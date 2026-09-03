package com.example.dsp

import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.example.audio.AudioDeviceInfoWrapper
import com.example.audio.AudioDeviceManager
import com.example.audio.AudioInputEngine
import com.example.audio.AudioOutputEngine
import com.example.audio.DelayBuffer
import com.example.audio.FloatRingBuffer
import com.example.audio.LatencyMeasurer
import com.example.data.AncMode
import com.example.data.DspMetrics
import com.example.data.DspParameters
import com.example.data.TestSignalType
import com.example.data.VisualizerSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

class DspEngine(
    private val deviceManager: AudioDeviceManager
) {
    private val inputEngine = AudioInputEngine(deviceManager.context)
    private val outputEngine = AudioOutputEngine()
    private val dcBlocker = DcBlocker()
    private val bandpassFilter = BandpassFilter()
    private val fxlms = FilteredXLMS()
    private val equalizer = EqualizerFilter()
    private val limiter = Limiter()
    private val signalGenerator = SignalGenerator()
    private val audioSourcePlayer = AudioSourcePlayer()
    private val fftAnalyzer = FFTAnalyzer(128)
    private val latencyMeasurer = LatencyMeasurer()

    private var audioThread: Thread? = null
    private val isRunningFlag = AtomicBoolean(false)

    // DSP Parameters & State Flows
    var currentParams = DspParameters()
        private set

    private val _metrics = MutableStateFlow(DspMetrics())
    val metrics: StateFlow<DspMetrics> = _metrics.asStateFlow()

    private val _visualizerSnapshot = MutableStateFlow(VisualizerSnapshot())
    val visualizerSnapshot: StateFlow<VisualizerSnapshot> = _visualizerSnapshot.asStateFlow()

    // Pre-allocated Audio Buffers (Zero memory allocations on real-time thread)
    private var framesPerBuffer = 256
    private var sampleRate = 48000

    private var rawMicBuffer = FloatArray(512)
    private var conditionedMicBuffer = FloatArray(512)
    private var antiNoiseBuffer = FloatArray(512)
    private var testSignalBuffer = FloatArray(512)
    private var audioSourceBuffer = FloatArray(512)
    private var delayedAudioBuffer = FloatArray(512)
    private var mixedOutputBuffer = FloatArray(512)
    private var limitedOutputBuffer = FloatArray(512)

    private val ringBuffer = FloatRingBuffer(4096)
    private val delayBuffer = DelayBuffer(48000 * 2) // Up to 2 seconds max delay

    // Visualization buffers
    private val visMic = FloatArray(128)
    private val visAnti = FloatArray(128)
    private val visOut = FloatArray(128)
    private val visSpectrum = FloatArray(64)
    private val visFreqs = FloatArray(64)

    private var lastVisUpdateTime = 0L
    private var processedFramesCounter: Long = 0

    init {
        updateSampleRate(48000)
    }

    fun updateParameters(params: DspParameters) {
        this.currentParams = params
        val secDelaySamples = (params.secondaryPathDelayMs * 0.001f * sampleRate).toInt()
        fxlms.updateParameters(
            taps = params.filterTaps,
            mu = params.stepSizeMu,
            gamma = params.leakageGamma,
            secondaryDelaySamples = secDelaySamples
        )
        bandpassFilter.configure(
            sampleRate = sampleRate,
            lowCutoffHz = params.lowCutoffHz,
            highCutoffHz = params.highCutoffHz
        )
        limiter.threshold = params.limiterThreshold
    }

    fun setMode(mode: AncMode) {
        if (currentParams.mode != mode) {
            currentParams = currentParams.copy(mode = mode)
            if (mode == AncMode.OFF) {
                fxlms.reset()
                dcBlocker.reset()
                bandpassFilter.reset()
            }
        }
    }

    private fun updateSampleRate(sr: Int) {
        this.sampleRate = sr
        signalGenerator.updateSampleRate(sr)
        audioSourcePlayer.updateSampleRate(sr)
        limiter.updateSampleRate(sr)
        bandpassFilter.configure(sr, currentParams.lowCutoffHz, currentParams.highCutoffHz)

        // Frequency center bins for spectrum
        for (i in 0 until 64) {
            visFreqs[i] = (i * (sr / 2f) / 64f)
        }
    }

    @Synchronized
    fun start(): Boolean {
        if (isRunningFlag.get()) return true

        sampleRate = deviceManager.getPropertyOutputSampleRate()
        framesPerBuffer = deviceManager.getPropertyFramesPerBuffer().coerceIn(64, 512)

        // Reallocate internal buffers if buffer size changed
        if (rawMicBuffer.size < framesPerBuffer) {
            rawMicBuffer = FloatArray(framesPerBuffer)
            conditionedMicBuffer = FloatArray(framesPerBuffer)
            antiNoiseBuffer = FloatArray(framesPerBuffer)
            testSignalBuffer = FloatArray(framesPerBuffer)
            audioSourceBuffer = FloatArray(framesPerBuffer)
            delayedAudioBuffer = FloatArray(framesPerBuffer)
            mixedOutputBuffer = FloatArray(framesPerBuffer)
            limitedOutputBuffer = FloatArray(framesPerBuffer)
        }

        updateSampleRate(sampleRate)
        ringBuffer.clear()
        delayBuffer.clear()
        fxlms.reset()
        dcBlocker.reset()
        bandpassFilter.reset()
        limiter.reset()

        val inputDevInfo = deviceManager.selectedInput.value?.let { deviceManager.getAudioDeviceInfo(it.id) }
        val outputDevInfo = deviceManager.selectedOutput.value?.let { deviceManager.getAudioDeviceInfo(it.id) }

        val inStarted = inputEngine.start(sampleRate, framesPerBuffer, inputDevInfo)
        val outStarted = outputEngine.start(sampleRate, framesPerBuffer, outputDevInfo)

        if (!inStarted && !outStarted) {
            Log.e(TAG, "Failed to start both audio input and output engines")
            inputEngine.stop()
            outputEngine.stop()
            return false
        }

        isRunningFlag.set(true)

        audioThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            runRealTimeAudioLoop()
        }, "SoftwareAncDspThread").apply {
            start()
        }

        _metrics.value = _metrics.value.copy(
            isRunning = true,
            sampleRate = sampleRate,
            bufferSizeFrames = framesPerBuffer
        )
        return true
    }

    @Synchronized
    fun stop() {
        isRunningFlag.set(false)
        try {
            audioThread?.join(500)
        } catch (_: Exception) {}
        audioThread = null

        inputEngine.stop()
        outputEngine.stop()
        ringBuffer.clear()

        _metrics.value = _metrics.value.copy(
            isRunning = false,
            dspLoadPercent = 0f
        )
    }

    /**
     * Emergency Stop: Immediately disables anti-noise output and resets DSP filters.
     */
    fun emergencyStop() {
        setMode(AncMode.OFF)
        fxlms.reset()
        ringBuffer.clear()
        mixedOutputBuffer.fill(0f)
        limitedOutputBuffer.fill(0f)
    }

    private fun runRealTimeAudioLoop() {
        val bufferFrames = framesPerBuffer
        var inputPeak = 0.0f
        var antiPeak = 0.0f
        var outputPeak = 0.0f

        while (isRunningFlag.get()) {
            val loopStartNs = SystemClock.elapsedRealtimeNanos()

            // 1. Capture Microphone Audio
            val readCount = inputEngine.read(rawMicBuffer, bufferFrames)
            val framesToProcess = if (readCount > 0) readCount else bufferFrames

            if (readCount <= 0) {
                rawMicBuffer.fill(0f)
            }

            // 2. Stage 1: Input Conditioning (DC Blocker, Gain, Bandpass filter)
            val inputGainLinear = (10.0).pow((currentParams.inputGainDb / 20.0).toDouble()).toFloat()
            for (i in 0 until framesToProcess) {
                val micGain = rawMicBuffer[i] * inputGainLinear
                val noDc = dcBlocker.process(micGain)
                conditionedMicBuffer[i] = bandpassFilter.process(noDc)
                val absIn = abs(conditionedMicBuffer[i])
                if (absIn > inputPeak) inputPeak = absIn
            }

            // 3. Stage 2: ANC / DSP Processing based on Active Mode
            when (currentParams.mode) {
                AncMode.OFF -> {
                    antiNoiseBuffer.fill(0f)
                    mixedOutputBuffer.fill(0f)
                }
                AncMode.MONITOR -> {
                    antiNoiseBuffer.fill(0f)
                    for (i in 0 until framesToProcess) {
                        mixedOutputBuffer[i] = conditionedMicBuffer[i]
                    }
                }
                AncMode.EXPERIMENTAL_ANC -> {
                    fxlms.processBlock(
                        referenceIn = conditionedMicBuffer,
                        antiNoiseOut = antiNoiseBuffer,
                        count = framesToProcess,
                        ancStrength = currentParams.ancStrength
                    )
                    for (i in 0 until framesToProcess) {
                        mixedOutputBuffer[i] = antiNoiseBuffer[i]
                        val absAnti = abs(antiNoiseBuffer[i])
                        if (absAnti > antiPeak) antiPeak = absAnti
                    }
                }
                AncMode.BYPASS -> {
                    antiNoiseBuffer.fill(0f)
                    for (i in 0 until framesToProcess) {
                        mixedOutputBuffer[i] = rawMicBuffer[i]
                    }
                }
                AncMode.TEST_SIGNAL -> {
                    antiNoiseBuffer.fill(0f)
                    signalGenerator.generateSignal(
                        type = currentParams.testSignalType,
                        output = testSignalBuffer,
                        count = framesToProcess,
                        amplitude = currentParams.testSignalVolume
                    )
                    for (i in 0 until framesToProcess) {
                        mixedOutputBuffer[i] = testSignalBuffer[i]
                    }
                }
            }

            // 4. Stage 3: Audio Source + Delay Buffer Path
            if (currentParams.playAudioSourceTrack) {
                audioSourcePlayer.generateBlock(
                    output = audioSourceBuffer,
                    count = framesToProcess,
                    volume = currentParams.audioSourceVolume
                )
                val delaySamples = (currentParams.audioDelayMs * 0.001f * sampleRate)
                for (i in 0 until framesToProcess) {
                    delayBuffer.push(audioSourceBuffer[i])
                    delayedAudioBuffer[i] = delayBuffer.getDelayed(delaySamples)
                    mixedOutputBuffer[i] += delayedAudioBuffer[i]
                }
            }

            // 5. Stage 4: Equalizer & Safety Limiter
            for (i in 0 until framesToProcess) {
                mixedOutputBuffer[i] = equalizer.process(mixedOutputBuffer[i])
            }
            limiter.process(mixedOutputBuffer, limitedOutputBuffer, framesToProcess)

            for (i in 0 until framesToProcess) {
                val absOut = abs(limitedOutputBuffer[i])
                if (absOut > outputPeak) outputPeak = absOut
            }

            // 6. Write to Audio Output Engine
            val written = outputEngine.write(limitedOutputBuffer, framesToProcess)
            processedFramesCounter += framesToProcess

            val loopEndNs = SystemClock.elapsedRealtimeNanos()
            val loopDurationMs = (loopEndNs - loopStartNs) / 1_000_000.0f
            val bufferDurationMs = (bufferFrames.toFloat() / sampleRate) * 1000.0f
            val dspLoad = (loopDurationMs / bufferDurationMs) * 100.0f

            // Throttled UI & Visualizer Updates (~30 FPS)
            val now = SystemClock.uptimeMillis()
            if (now - lastVisUpdateTime > 33) {
                lastVisUpdateTime = now
                updateVisualizerAndMetrics(
                    inputPeak = inputPeak,
                    antiPeak = antiPeak,
                    outputPeak = outputPeak,
                    dspLoad = dspLoad.coerceIn(0f, 100f),
                    loopDurationMs = loopDurationMs
                )
                inputPeak = 0f
                antiPeak = 0f
                outputPeak = 0f
            }
        }
    }

    private fun updateVisualizerAndMetrics(
        inputPeak: Float,
        antiPeak: Float,
        outputPeak: Float,
        dspLoad: Float,
        loopDurationMs: Float
    ) {
        val visPoints = 128
        val step = max(1, framesPerBuffer / visPoints)

        for (i in 0 until visPoints) {
            val idx = (i * step).coerceIn(0, conditionedMicBuffer.size - 1)
            visMic[i] = conditionedMicBuffer[idx]
            visAnti[i] = antiNoiseBuffer[idx]
            visOut[i] = limitedOutputBuffer[idx]
        }

        // Compute FFT magnitude spectrum of output signal
        fftAnalyzer.computeMagnitudes(limitedOutputBuffer, 0, visSpectrum)

        val inputDb = if (inputPeak > 1e-5f) 20f * log10(inputPeak) else -96f
        val antiDb = if (antiPeak > 1e-5f) 20f * log10(antiPeak) else -96f
        val outputDb = if (outputPeak > 1e-5f) 20f * log10(outputPeak) else -96f

        val estimatedBufferLatencyMs = (framesPerBuffer.toFloat() / sampleRate) * 1000f * 2f // In + Out buffers

        _metrics.value = DspMetrics(
            isRunning = isRunningFlag.get(),
            mode = currentParams.mode,
            sampleRate = sampleRate,
            bufferSizeFrames = framesPerBuffer,
            inputLatencyMs = estimatedBufferLatencyMs / 2f,
            outputLatencyMs = estimatedBufferLatencyMs / 2f,
            processingLatencyMs = loopDurationMs,
            totalEstimatedLatencyMs = estimatedBufferLatencyMs + loopDurationMs,
            dspLoadPercent = dspLoad,
            inputPeakLevelDb = inputDb,
            antiNoisePeakLevelDb = antiDb,
            outputPeakLevelDb = outputDb,
            filterDiverged = fxlms.isDiverged,
            bufferUnderruns = outputEngine.getUnderrunCount().toLong(),
            bufferOverruns = ringBuffer.totalOverruns.get(),
            processedFrameCount = processedFramesCounter,
            lastError = if (fxlms.isDiverged) "Adaptive filter diverged - safeguard reset activated" else null
        )

        _visualizerSnapshot.value = VisualizerSnapshot(
            micWaveform = visMic.copyOf(),
            antiNoiseWaveform = visAnti.copyOf(),
            outputWaveform = visOut.copyOf(),
            spectrumMagnitudes = visSpectrum.copyOf(),
            spectrumFrequencies = visFreqs.copyOf(),
            timestamp = SystemClock.uptimeMillis()
        )
    }

    /**
     * Perform an Acoustic Latency Calibration Impulse test.
     */
    fun performLatencyCalibration(): com.example.data.LatencyCalibrationResult {
        val wasRunning = isRunningFlag.get()
        if (!wasRunning) {
            start()
        }

        val impulseBuffer = FloatArray(framesPerBuffer)
        val recordedBuffer = FloatArray(framesPerBuffer * 4)

        signalGenerator.generateCalibrationImpulse(impulseBuffer, framesPerBuffer)

        // Write impulse to output
        outputEngine.write(impulseBuffer, framesPerBuffer)

        // Record incoming echo
        var totalRead = 0
        val tempRead = FloatArray(framesPerBuffer)
        for (i in 0 until 4) {
            val r = inputEngine.read(tempRead, framesPerBuffer)
            if (r > 0) {
                System.arraycopy(tempRead, 0, recordedBuffer, totalRead, r)
                totalRead += r
            }
        }

        val result = latencyMeasurer.analyzeRoundTrip(
            referencePulse = impulseBuffer,
            recordedMicrophone = recordedBuffer,
            sampleRate = sampleRate,
            dspProcessingTimeMs = _metrics.value.processingLatencyMs
        )

        if (result.isSuccess) {
            // Apply recommended delay to audio delay parameter
            updateParameters(currentParams.copy(audioDelayMs = result.recommendedDelayMs))
        }

        return result
    }

    fun resetFilterWeights() {
        fxlms.reset()
        _metrics.value = _metrics.value.copy(filterDiverged = false)
    }

    fun setEqBandGain(bandIndex: Int, gainDb: Float) {
        equalizer.setBandGain(bandIndex, gainDb)
    }

    fun setEqBandGains(gains: FloatArray) {
        equalizer.setBandGains(gains)
    }

    fun setEqEnabled(enabled: Boolean) {
        equalizer.isEnabled = enabled
    }

    fun resetEqualizer() {
        equalizer.reset()
    }

    fun getInputState(): String = inputEngine.getStateSummary()
    fun getOutputState(): String = outputEngine.getStateSummary()

    companion object {
        private const val TAG = "DspEngine"
    }
}
