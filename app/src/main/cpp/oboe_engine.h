#ifndef OBOE_ENGINE_H
#define OBOE_ENGINE_H

#include <oboe/Oboe.h>
#include <memory>
#include <atomic>
#include <vector>
#include <mutex>
#include <chrono>
#include "dsp_fxlms.h"
#include "dsp_equalizer.h"

enum class NativeAncMode : int {
    EXPERIMENTAL_ANC = 0,
    MONITOR = 1,
    TEST_SIGNAL = 2,
    BYPASS = 3,
    OFF = 4
};

struct EngineMetricsSnapshot {
    bool isRunning;
    int sampleRate;
    int inputBufferSizeFrames;
    int outputBufferSizeFrames;
    int xRuns;
    float estimatedLatencyMs;
    float dspCpuLoadPercent;
    float inputPeakDb;
    float antiNoisePeakDb;
    float outputPeakDb;
    bool filterDiverged;
    int activeMode;
    int backendType; // 0 = Unknown, 1 = AAudio, 2 = OpenSLES
};

class LockFreeAudioRingBuffer {
public:
    LockFreeAudioRingBuffer(size_t capacity = 16384);
    void reset();
    size_t write(const float* data, size_t count);
    size_t read(float* data, size_t count);
    size_t availableRead() const;
    size_t availableWrite() const;

private:
    std::vector<float> buffer_;
    size_t capacity_;
    std::atomic<size_t> writeIndex_{0};
    std::atomic<size_t> readIndex_{0};
};

class OboeAudioEngine : public oboe::AudioStreamDataCallback,
                         public oboe::AudioStreamErrorCallback {
public:
    OboeAudioEngine();
    virtual ~OboeAudioEngine();

    bool start(int targetSampleRate, int framesPerBuffer, int inputDeviceId, int outputDeviceId, bool exclusiveMode);
    void stop();
    bool isRunning() const { return isRunning_.load(); }

    void setAncMode(NativeAncMode mode);
    void setAncStrength(float strength);
    void setAudioDelayMs(float delayMs);
    void setFilterTaps(int taps);
    void setStepSize(float mu);
    void setLeakFactor(float leak);
    void resetFilter();
    void setAudioSourceVolume(float volume);
    void setPlayAudioSource(bool play);

    // Native Integrated Equalizer Controls
    void setEqBandGain(int bandIndex, float gainDb);
    void setEqBandGains(const float* gains, int count);
    void setEqEnabled(bool enabled);
    void resetEqualizer();

    void getMetrics(EngineMetricsSnapshot& outMetrics);
    void getVisualizerSnapshot(float* micOut, float* antiNoiseOut, float* mixOut, int count);
    void getSpectrum(float* spectrumOut, int numBins);

    const char* getBackendName() const;
    static bool isAAudioSupported();

    // Oboe AudioStreamDataCallback
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* audioStream,
        void* audioData,
        int32_t numFrames
    ) override;

    // Oboe AudioStreamErrorCallback
    void onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> recordingStream_;
    std::shared_ptr<oboe::AudioStream> playbackStream_;

    std::atomic<bool> isRunning_{false};
    std::atomic<NativeAncMode> currentMode_{NativeAncMode::EXPERIMENTAL_ANC};
    std::atomic<float> ancStrength_{1.0f};
    std::atomic<float> audioDelayMs_{0.0f};
    std::atomic<float> audioSourceVolume_{0.5f};
    std::atomic<bool> playAudioSource_{false};

    int sampleRate_{48000};
    int burstSizeFrames_{192};
    int inputDeviceId_{0};
    int outputDeviceId_{0};
    bool exclusiveMode_{true};

    NativeFxLMS fxlms_;
    NativeEqualizer equalizer_;
    LockFreeAudioRingBuffer inputRingBuffer_;

    // Delay line for phase alignment
    std::vector<float> delayLineBuffer_;
    size_t delayLineWriteIdx_{0};

    // Synthesizer state
    double synthPhase1_{0.0};
    double synthPhase2_{0.0};
    double testSignalPhase_{0.0};

    // Telemetry & Metrics
    std::atomic<int> xRunCount_{0};
    std::atomic<float> inputPeakLinear_{0.0f};
    std::atomic<float> antiNoisePeakLinear_{0.0f};
    std::atomic<float> outputPeakLinear_{0.0f};
    std::atomic<float> dspCpuLoad_{0.0f};
    std::atomic<float> estimatedLatencyMs_{0.0f};

    // Waveform visualization ring buffer
    static constexpr int VIS_BUFFER_SIZE = 512;
    float visMicBuffer_[VIS_BUFFER_SIZE]{};
    float visAntiNoiseBuffer_[VIS_BUFFER_SIZE]{};
    float visMixBuffer_[VIS_BUFFER_SIZE]{};
    std::atomic<int> visWriteIndex_{0};

    // Processing helpers
    void processAudioBlock(float* outData, int numFrames);
    void updateVisualizer(float mic, float antiNoise, float outMix);
    float linearToDb(float linear) const;

    bool openRecordingStream();
    bool openPlaybackStream();
    void closeStreams();
    void restartStreams();
};

#endif // OBOE_ENGINE_H
