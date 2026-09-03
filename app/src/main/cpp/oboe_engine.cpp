#include "oboe_engine.h"
#include <android/log.h>
#include <cmath>
#include <algorithm>

#define TAG "OboeAncEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// -----------------------------------------------------------------------------
// LockFreeAudioRingBuffer Implementation
// -----------------------------------------------------------------------------

LockFreeAudioRingBuffer::LockFreeAudioRingBuffer(size_t capacity)
    : capacity_(capacity), writeIndex_(0), readIndex_(0) {
    buffer_.assign(capacity_, 0.0f);
}

void LockFreeAudioRingBuffer::reset() {
    writeIndex_.store(0, std::memory_order_relaxed);
    readIndex_.store(0, std::memory_order_relaxed);
    std::fill(buffer_.begin(), buffer_.end(), 0.0f);
}

size_t LockFreeAudioRingBuffer::availableWrite() const {
    size_t w = writeIndex_.load(std::memory_order_relaxed);
    size_t r = readIndex_.load(std::memory_order_acquire);
    return capacity_ - 1 - ((w - r) & (capacity_ - 1));
}

size_t LockFreeAudioRingBuffer::availableRead() const {
    size_t w = writeIndex_.load(std::memory_order_acquire);
    size_t r = readIndex_.load(std::memory_order_relaxed);
    return (w - r) & (capacity_ - 1);
}

size_t LockFreeAudioRingBuffer::write(const float* data, size_t count) {
    if (!data || count == 0) return 0;
    size_t avail = availableWrite();
    size_t toWrite = std::min(count, avail);
    size_t w = writeIndex_.load(std::memory_order_relaxed);

    for (size_t i = 0; i < toWrite; ++i) {
        buffer_[(w + i) & (capacity_ - 1)] = data[i];
    }
    writeIndex_.store(w + toWrite, std::memory_order_release);
    return toWrite;
}

size_t LockFreeAudioRingBuffer::read(float* data, size_t count) {
    if (!data || count == 0) return 0;
    size_t avail = availableRead();
    size_t toRead = std::min(count, avail);
    size_t r = readIndex_.load(std::memory_order_relaxed);

    for (size_t i = 0; i < toRead; ++i) {
        data[i] = buffer_[(r + i) & (capacity_ - 1)];
    }
    readIndex_.store(r + toRead, std::memory_order_release);
    return toRead;
}

// -----------------------------------------------------------------------------
// OboeAudioEngine Implementation
// -----------------------------------------------------------------------------

OboeAudioEngine::OboeAudioEngine()
    : inputRingBuffer_(32768) {
    delayLineBuffer_.assign(96000, 0.0f); // Max ~500ms delay at 192kHz
    std::fill(visMicBuffer_, visMicBuffer_ + VIS_BUFFER_SIZE, 0.0f);
    std::fill(visAntiNoiseBuffer_, visAntiNoiseBuffer_ + VIS_BUFFER_SIZE, 0.0f);
    std::fill(visMixBuffer_, visMixBuffer_ + VIS_BUFFER_SIZE, 0.0f);
}

OboeAudioEngine::~OboeAudioEngine() {
    stop();
}

bool OboeAudioEngine::isAAudioSupported() {
    return oboe::AudioStreamBuilder::isAAudioSupported();
}

const char* OboeAudioEngine::getBackendName() const {
    if (playbackStream_) {
        return (playbackStream_->getAudioApi() == oboe::AudioApi::AAudio) ? "AAudio (Native Low Latency)" : "OpenSL ES";
    }
    return isAAudioSupported() ? "AAudio (Ready)" : "OpenSL ES (Ready)";
}

bool OboeAudioEngine::start(int targetSampleRate, int framesPerBuffer, int inputDeviceId, int outputDeviceId, bool exclusiveMode) {
    if (isRunning_.load()) {
        stop();
    }

    sampleRate_ = targetSampleRate > 0 ? targetSampleRate : 48000;
    burstSizeFrames_ = framesPerBuffer > 0 ? framesPerBuffer : 192;
    inputDeviceId_ = inputDeviceId;
    outputDeviceId_ = outputDeviceId;
    exclusiveMode_ = exclusiveMode;

    inputRingBuffer_.reset();
    fxlms_.reset();
    std::fill(delayLineBuffer_.begin(), delayLineBuffer_.end(), 0.0f);
    delayLineWriteIdx_ = 0;
    xRunCount_.store(0);

    LOGI("Starting OboeAudioEngine: Target SR=%d Hz, BufferSize=%d, Exclusive=%d",
         sampleRate_, burstSizeFrames_, exclusiveMode ? 1 : 0);

    if (!openPlaybackStream()) {
        LOGE("Failed to open Oboe playback stream");
        return false;
    }

    if (!openRecordingStream()) {
        LOGW("Failed to open Oboe recording stream - running in playback only mode");
    }

    // Start playback stream
    oboe::Result result = playbackStream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Error starting playback stream: %s", oboe::convertToText(result));
        closeStreams();
        return false;
    }

    // Start recording stream if available
    if (recordingStream_) {
        result = recordingStream_->requestStart();
        if (result != oboe::Result::OK) {
            LOGW("Warning starting recording stream: %s", oboe::convertToText(result));
        }
    }

    isRunning_.store(true);
    LOGI("OboeAudioEngine started successfully with backend: %s", getBackendName());
    return true;
}

void OboeAudioEngine::stop() {
    if (!isRunning_.exchange(false)) {
        return;
    }
    LOGI("Stopping OboeAudioEngine");
    closeStreams();
}

bool OboeAudioEngine::openPlaybackStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(exclusiveMode_ ? oboe::SharingMode::Exclusive : oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(sampleRate_)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    if (outputDeviceId_ > 0) {
        builder.setDeviceId(outputDeviceId_);
    }

    oboe::Result result = builder.openStream(playbackStream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open playback stream: %s. Retrying with Shared mode...", oboe::convertToText(result));
        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(playbackStream_);
        if (result != oboe::Result::OK) {
            LOGE("Failed retry open playback stream: %s", oboe::convertToText(result));
            return false;
        }
    }

    // Set buffer capacity to 2 bursts for minimal latency
    int32_t burst = playbackStream_->getFramesPerBurst();
    if (burst > 0) {
        playbackStream_->setBufferSizeInFrames(burst * 2);
    }
    sampleRate_ = playbackStream_->getSampleRate();
    equalizer_.setSampleRate(sampleRate_);

    LOGI("Playback stream opened: SR=%d, Burst=%d, BufferSize=%d, Format=%s, API=%s",
         sampleRate_, burst, playbackStream_->getBufferSizeInFrames(),
         oboe::convertToText(playbackStream_->getFormat()),
         (playbackStream_->getAudioApi() == oboe::AudioApi::AAudio) ? "AAudio" : "OpenSLES");
    return true;
}

bool OboeAudioEngine::openRecordingStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(exclusiveMode_ ? oboe::SharingMode::Exclusive : oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setSampleRate(sampleRate_)
        ->setInputPreset(oboe::InputPreset::VoicePerformance)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    if (inputDeviceId_ > 0) {
        builder.setDeviceId(inputDeviceId_);
    }

    oboe::Result result = builder.openStream(recordingStream_);
    if (result != oboe::Result::OK) {
        LOGW("Failed to open recording stream in Exclusive: %s. Trying Shared mode...", oboe::convertToText(result));
        builder.setSharingMode(oboe::SharingMode::Shared);
        builder.setInputPreset(oboe::InputPreset::Generic);
        result = builder.openStream(recordingStream_);
        if (result != oboe::Result::OK) {
            LOGW("Could not open input recording stream: %s", oboe::convertToText(result));
            return false;
        }
    }

    LOGI("Recording stream opened: SR=%d, Channels=1, API=%s",
         recordingStream_->getSampleRate(),
         (recordingStream_->getAudioApi() == oboe::AudioApi::AAudio) ? "AAudio" : "OpenSLES");
    return true;
}

void OboeAudioEngine::closeStreams() {
    if (recordingStream_) {
        recordingStream_->stop();
        recordingStream_->close();
        recordingStream_.reset();
    }
    if (playbackStream_) {
        playbackStream_->stop();
        playbackStream_->close();
        playbackStream_.reset();
    }
}

// -----------------------------------------------------------------------------
// Real-time Audio Callback (High Priority Audio Thread)
// -----------------------------------------------------------------------------

oboe::DataCallbackResult OboeAudioEngine::onAudioReady(
    oboe::AudioStream* audioStream,
    void* audioData,
    int32_t numFrames
) {
    if (!isRunning_.load(std::memory_order_relaxed)) {
        return oboe::DataCallbackResult::Stop;
    }

    auto startTime = std::chrono::high_resolution_clock::now();

    // 1. INPUT STREAM: Collect incoming mic samples into ring buffer
    if (audioStream == recordingStream_.get()) {
        const float* inData = static_cast<const float*>(audioData);
        inputRingBuffer_.write(inData, numFrames);

        // Peak detector for mic input
        float maxMic = 0.0f;
        for (int i = 0; i < numFrames; ++i) {
            float absVal = std::abs(inData[i]);
            if (absVal > maxMic) maxMic = absVal;
        }
        float prevMic = inputPeakLinear_.load(std::memory_order_relaxed);
        inputPeakLinear_.store(std::max(maxMic, prevMic * 0.95f), std::memory_order_relaxed);

        return oboe::DataCallbackResult::Continue;
    }

    // 2. OUTPUT STREAM: Process DSP and fill stereo buffer
    if (audioStream == playbackStream_.get()) {
        float* outData = static_cast<float*>(audioData);
        processAudioBlock(outData, numFrames);

        // Track xRuns if available
        auto xruns = playbackStream_->getXRunCount();
        if (xruns) {
            xRunCount_.store(xruns.value(), std::memory_order_relaxed);
        }

        // Calculate estimated latency
        auto latencyResult = playbackStream_->calculateLatencyMillis();
        if (latencyResult) {
            estimatedLatencyMs_.store(static_cast<float>(latencyResult.value()), std::memory_order_relaxed);
        } else {
            // Approximation: buffer size / sample rate * 1000 + hardware base (10ms)
            float est = (static_cast<float>(numFrames * 2) / static_cast<float>(sampleRate_)) * 1000.0f + 8.0f;
            estimatedLatencyMs_.store(est, std::memory_order_relaxed);
        }

        // CPU load calculation
        auto endTime = std::chrono::high_resolution_clock::now();
        std::chrono::duration<double, std::micro> elapsed = endTime - startTime;
        double framePeriodUs = (static_cast<double>(numFrames) / sampleRate_) * 1000000.0;
        float currentLoad = static_cast<float>((elapsed.count() / framePeriodUs) * 100.0);
        float prevLoad = dspCpuLoad_.load(std::memory_order_relaxed);
        dspCpuLoad_.store(prevLoad * 0.9f + currentLoad * 0.1f, std::memory_order_relaxed);

        return oboe::DataCallbackResult::Continue;
    }

    return oboe::DataCallbackResult::Continue;
}

void OboeAudioEngine::processAudioBlock(float* outData, int numFrames) {
    NativeAncMode mode = currentMode_.load(std::memory_order_relaxed);
    float strength = ancStrength_.load(std::memory_order_relaxed);
    float delayMs = audioDelayMs_.load(std::memory_order_relaxed);
    float synthVol = audioSourceVolume_.load(std::memory_order_relaxed);
    bool playSynth = playAudioSource_.load(std::memory_order_relaxed);

    int delaySamples = static_cast<int>((delayMs / 1000.0f) * sampleRate_);
    int delayCap = static_cast<int>(delayLineBuffer_.size());
    if (delaySamples >= delayCap) delaySamples = delayCap - 1;

    // Read mic frames from ring buffer
    std::vector<float> micChunk(numFrames, 0.0f);
    size_t readCount = inputRingBuffer_.read(micChunk.data(), numFrames);

    float maxAntiNoise = 0.0f;
    float maxOutput = 0.0f;

    double twoPi = 2.0 * M_PI;
    double synthStep1 = (440.0 / sampleRate_) * twoPi;  // A4
    double synthStep2 = (554.37 / sampleRate_) * twoPi; // C#5
    double testSignalStep = (1000.0 / sampleRate_) * twoPi; // 1kHz test tone

    for (int i = 0; i < numFrames; ++i) {
        float micSample = micChunk[i];

        // Store into delay line
        delayLineBuffer_[delayLineWriteIdx_] = micSample;
        size_t readDelayIdx = (delayLineWriteIdx_ + delayCap - delaySamples) % delayCap;
        float delayedMic = delayLineBuffer_[readDelayIdx];
        delayLineWriteIdx_ = (delayLineWriteIdx_ + 1) % delayCap;

        float antiNoiseSample = 0.0f;
        float outputMono = 0.0f;

        switch (mode) {
            case NativeAncMode::EXPERIMENTAL_ANC: {
                antiNoiseSample = fxlms_.processSample(delayedMic, strength);
                // In full-duplex ANC, output the generated anti-noise
                outputMono = antiNoiseSample;
                // LMS weight update
                float residualError = delayedMic + antiNoiseSample;
                fxlms_.updateWeights(residualError);
                break;
            }
            case NativeAncMode::MONITOR: {
                outputMono = delayedMic * strength;
                antiNoiseSample = 0.0f;
                break;
            }
            case NativeAncMode::TEST_SIGNAL: {
                testSignalPhase_ += testSignalStep;
                if (testSignalPhase_ > twoPi) testSignalPhase_ -= twoPi;
                float tone = static_cast<float>(std::sin(testSignalPhase_)) * 0.4f * strength;
                outputMono = tone;
                antiNoiseSample = 0.0f;
                break;
            }
            case NativeAncMode::BYPASS: {
                outputMono = delayedMic;
                antiNoiseSample = 0.0f;
                break;
            }
            case NativeAncMode::OFF:
            default: {
                outputMono = 0.0f;
                antiNoiseSample = 0.0f;
                break;
            }
        }

        // Add background harmonic synthesizer if enabled
        if (playSynth) {
            synthPhase1_ += synthStep1;
            synthPhase2_ += synthStep2;
            if (synthPhase1_ > twoPi) synthPhase1_ -= twoPi;
            if (synthPhase2_ > twoPi) synthPhase2_ -= twoPi;
            float synthSample = static_cast<float>(
                (std::sin(synthPhase1_) * 0.6 + std::sin(synthPhase2_) * 0.4) * 0.3 * synthVol
            );
            outputMono += synthSample;
        }

        // Apply Native 5-Band Equalizer (Biquad Peak/Shelf Filters)
        outputMono = equalizer_.processSample(outputMono);

        // Hard limiter safeguard to prevent digital clipping / ear fatigue
        outputMono = std::clamp(outputMono, -0.98f, 0.98f);

        // Interleaved Stereo Out
        outData[i * 2] = outputMono;     // Left
        outData[i * 2 + 1] = outputMono; // Right

        // Peak tracking
        float absAnti = std::abs(antiNoiseSample);
        if (absAnti > maxAntiNoise) maxAntiNoise = absAnti;
        float absOut = std::abs(outputMono);
        if (absOut > maxOutput) maxOutput = absOut;

        // Visualizer decimation update
        if (i % 4 == 0) {
            updateVisualizer(delayedMic, antiNoiseSample, outputMono);
        }
    }

    // Decay peak trackers
    float prevAnti = antiNoisePeakLinear_.load(std::memory_order_relaxed);
    antiNoisePeakLinear_.store(std::max(maxAntiNoise, prevAnti * 0.95f), std::memory_order_relaxed);

    float prevOut = outputPeakLinear_.load(std::memory_order_relaxed);
    outputPeakLinear_.store(std::max(maxOutput, prevOut * 0.95f), std::memory_order_relaxed);
}

void OboeAudioEngine::updateVisualizer(float mic, float antiNoise, float outMix) {
    int idx = visWriteIndex_.load(std::memory_order_relaxed);
    visMicBuffer_[idx] = mic;
    visAntiNoiseBuffer_[idx] = antiNoise;
    visMixBuffer_[idx] = outMix;
    visWriteIndex_.store((idx + 1) % VIS_BUFFER_SIZE, std::memory_order_relaxed);
}

void OboeAudioEngine::getVisualizerSnapshot(float* micOut, float* antiNoiseOut, float* mixOut, int count) {
    if (!micOut || !antiNoiseOut || !mixOut || count <= 0) return;
    int limit = std::min(count, VIS_BUFFER_SIZE);
    int start = visWriteIndex_.load(std::memory_order_relaxed);

    for (int i = 0; i < limit; ++i) {
        int rIdx = (start + i) % VIS_BUFFER_SIZE;
        micOut[i] = visMicBuffer_[rIdx];
        antiNoiseOut[i] = visAntiNoiseBuffer_[rIdx];
        mixOut[i] = visMixBuffer_[rIdx];
    }
}

void OboeAudioEngine::getSpectrum(float* spectrumOut, int numBins) {
    if (!spectrumOut || numBins <= 0) return;
    int limit = std::min(numBins, 64);
    int start = visWriteIndex_.load(std::memory_order_relaxed);

    // Fast discrete energy binning over recent mix samples
    for (int b = 0; b < limit; ++b) {
        float real = 0.0f;
        float imag = 0.0f;
        float omega = static_cast<float>(2.0 * M_PI * (b + 1) / VIS_BUFFER_SIZE);
        for (int n = 0; n < VIS_BUFFER_SIZE; n += 4) {
            float s = visMixBuffer_[(start + n) % VIS_BUFFER_SIZE];
            real += s * std::cos(omega * n);
            imag -= s * std::sin(omega * n);
        }
        float mag = std::sqrt(real * real + imag * imag) / (VIS_BUFFER_SIZE / 4.0f);
        spectrumOut[b] = std::clamp(mag * 2.5f, 0.0f, 1.0f);
    }
}

float OboeAudioEngine::linearToDb(float linear) const {
    if (linear <= 0.00001f) return -90.0f;
    return 20.0f * std::log10(linear);
}

void OboeAudioEngine::getMetrics(EngineMetricsSnapshot& outMetrics) {
    outMetrics.isRunning = isRunning_.load(std::memory_order_relaxed);
    outMetrics.sampleRate = sampleRate_;
    outMetrics.inputBufferSizeFrames = burstSizeFrames_;
    outMetrics.outputBufferSizeFrames = playbackStream_ ? playbackStream_->getBufferSizeInFrames() : burstSizeFrames_;
    outMetrics.xRuns = xRunCount_.load(std::memory_order_relaxed);
    outMetrics.estimatedLatencyMs = estimatedLatencyMs_.load(std::memory_order_relaxed);
    outMetrics.dspCpuLoadPercent = dspCpuLoad_.load(std::memory_order_relaxed);
    outMetrics.inputPeakDb = linearToDb(inputPeakLinear_.load(std::memory_order_relaxed));
    outMetrics.antiNoisePeakDb = linearToDb(antiNoisePeakLinear_.load(std::memory_order_relaxed));
    outMetrics.outputPeakDb = linearToDb(outputPeakLinear_.load(std::memory_order_relaxed));
    outMetrics.filterDiverged = fxlms_.hasDiverged();
    outMetrics.activeMode = static_cast<int>(currentMode_.load(std::memory_order_relaxed));
    outMetrics.backendType = (playbackStream_ && playbackStream_->getAudioApi() == oboe::AudioApi::AAudio) ? 1 : 2;
}

// -----------------------------------------------------------------------------
// Control Mutators
// -----------------------------------------------------------------------------

void OboeAudioEngine::setAncMode(NativeAncMode mode) {
    currentMode_.store(mode, std::memory_order_relaxed);
}

void OboeAudioEngine::setAncStrength(float strength) {
    ancStrength_.store(std::clamp(strength, 0.0f, 2.0f), std::memory_order_relaxed);
}

void OboeAudioEngine::setAudioDelayMs(float delayMs) {
    audioDelayMs_.store(std::clamp(delayMs, 0.0f, 500.0f), std::memory_order_relaxed);
}

void OboeAudioEngine::setFilterTaps(int taps) {
    fxlms_.setFilterTaps(taps);
}

void OboeAudioEngine::setStepSize(float mu) {
    fxlms_.setStepSize(mu);
}

void OboeAudioEngine::setLeakFactor(float leak) {
    fxlms_.setLeakFactor(leak);
}

void OboeAudioEngine::resetFilter() {
    fxlms_.reset();
}

void OboeAudioEngine::setAudioSourceVolume(float volume) {
    audioSourceVolume_.store(std::clamp(volume, 0.0f, 1.0f), std::memory_order_relaxed);
}

void OboeAudioEngine::setPlayAudioSource(bool play) {
    playAudioSource_.store(play, std::memory_order_relaxed);
}

void OboeAudioEngine::setEqBandGain(int bandIndex, float gainDb) {
    equalizer_.setBandGain(bandIndex, gainDb);
}

void OboeAudioEngine::setEqBandGains(const float* gains, int count) {
    equalizer_.setBandGains(gains, count);
}

void OboeAudioEngine::setEqEnabled(bool enabled) {
    equalizer_.setEnabled(enabled);
}

void OboeAudioEngine::resetEqualizer() {
    equalizer_.reset();
}

void OboeAudioEngine::onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result error) {
    LOGW("Oboe ErrorBeforeClose: %s", oboe::convertToText(error));
}

void OboeAudioEngine::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) {
    LOGW("Oboe ErrorAfterClose: %s. Attempting reconnection...", oboe::convertToText(error));
    if (isRunning_.load()) {
        restartStreams();
    }
}

void OboeAudioEngine::restartStreams() {
    closeStreams();
    openPlaybackStream();
    openRecordingStream();
    if (playbackStream_) playbackStream_->requestStart();
    if (recordingStream_) recordingStream_->requestStart();
}
