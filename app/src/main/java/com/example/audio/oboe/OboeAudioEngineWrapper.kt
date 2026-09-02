package com.example.audio.oboe

import android.content.Context
import android.util.Log
import com.example.audio.AudioDeviceInfoWrapper
import com.example.data.AncMode
import com.example.data.VisualizerSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * High-level Kotlin wrapper around the C++ Oboe Audio Engine.
 * Supports low-latency AAudio / OpenSL ES, high sample-rate recording and playback (44.1k to 192k),
 * thread-safe real-time DSP control, and continuous telemetry collection.
 */
class OboeAudioEngineWrapper(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "OboeEngineWrapper"
        private const val METRICS_SIZE = 13
        private const val VISUALIZER_WAVE_SIZE = 256
        private const val SPECTRUM_BINS = 64
    }

    private var enginePtr: Long = 0L
    private val isNativeAvailable = OboeAudioEngineNative.isNativeAvailable()

    private val _telemetry = MutableStateFlow(OboeTelemetry())
    val telemetry: StateFlow<OboeTelemetry> = _telemetry.asStateFlow()

    private val _visualizerSnapshot = MutableStateFlow(
        VisualizerSnapshot(
            micWaveform = FloatArray(VISUALIZER_WAVE_SIZE),
            antiNoiseWaveform = FloatArray(VISUALIZER_WAVE_SIZE),
            outputWaveform = FloatArray(VISUALIZER_WAVE_SIZE),
            spectrumMagnitudes = FloatArray(SPECTRUM_BINS),
            spectrumFrequencies = FloatArray(SPECTRUM_BINS)
        )
    )
    val visualizerSnapshot: StateFlow<VisualizerSnapshot> = _visualizerSnapshot.asStateFlow()

    private var pollingJob: Job? = null
    private val rawMetricsBuffer = FloatArray(METRICS_SIZE)
    private val micWaveBuf = FloatArray(VISUALIZER_WAVE_SIZE)
    private val antiNoiseWaveBuf = FloatArray(VISUALIZER_WAVE_SIZE)
    private val mixWaveBuf = FloatArray(VISUALIZER_WAVE_SIZE)
    private val spectrumBuf = FloatArray(SPECTRUM_BINS)

    // Current configuration parameters
    var selectedSampleRate: HighSampleRate = HighSampleRate.STANDARD_48K
        private set
    var selectedSharingMode: OboeSharingMode = OboeSharingMode.EXCLUSIVE
        private set

    init {
        if (isNativeAvailable) {
            try {
                enginePtr = OboeAudioEngineNative.nativeCreateEngine()
                Log.i(TAG, "Native Oboe engine instance created: 0x${java.lang.Long.toHexString(enginePtr)}")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to instantiate native Oboe engine: ${e.message}")
                enginePtr = 0L
            }
        } else {
            Log.w(TAG, "Oboe native library not loaded; operating in fallback mock mode.")
        }
    }

    fun isAAudioSupported(): Boolean {
        return if (isNativeAvailable) {
            try {
                OboeAudioEngineNative.nativeIsAAudioSupported()
            } catch (e: Throwable) {
                false
            }
        } else {
            false
        }
    }

    fun getBackendName(): String {
        return if (isNativeAvailable && enginePtr != 0L) {
            try {
                OboeAudioEngineNative.nativeGetBackendName(enginePtr)
            } catch (e: Throwable) {
                "Oboe (Native)"
            }
        } else {
            "JVM Fallback Engine"
        }
    }

    /**
     * Start the low-latency Oboe full-duplex stream at high sample rates (e.g. 48kHz, 96kHz, 192kHz).
     */
    fun start(
        sampleRate: HighSampleRate = HighSampleRate.STANDARD_48K,
        sharingMode: OboeSharingMode = OboeSharingMode.EXCLUSIVE,
        framesPerBuffer: Int = 192,
        inputDevice: AudioDeviceInfoWrapper? = null,
        outputDevice: AudioDeviceInfoWrapper? = null
    ): Boolean {
        selectedSampleRate = sampleRate
        selectedSharingMode = sharingMode

        val inputId = inputDevice?.id ?: 0
        val outputId = outputDevice?.id ?: 0

        Log.i(TAG, "Starting Oboe wrapper: SR=${sampleRate.sampleRateHz}Hz, Mode=${sharingMode.label}, In=$inputId, Out=$outputId")

        if (isNativeAvailable && enginePtr != 0L) {
            val started = try {
                OboeAudioEngineNative.nativeStart(
                    enginePtr,
                    sampleRate.sampleRateHz,
                    framesPerBuffer,
                    inputId,
                    outputId,
                    sharingMode.isExclusive
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Native start failed: ${e.message}")
                false
            }

            if (started) {
                startPolling()
                return true
            }
        }

        // Fallback simulation mode for testing or systems without hardware native support
        _telemetry.value = _telemetry.value.copy(
            isRunning = true,
            sampleRate = sampleRate.sampleRateHz,
            inputBufferSizeFrames = framesPerBuffer,
            outputBufferSizeFrames = framesPerBuffer,
            backendName = getBackendName()
        )
        startPolling()
        return true
    }

    fun stop() {
        if (isNativeAvailable && enginePtr != 0L) {
            try {
                OboeAudioEngineNative.nativeStop(enginePtr)
            } catch (e: Throwable) {
                Log.e(TAG, "Native stop failed: ${e.message}")
            }
        }
        stopPolling()
        _telemetry.value = _telemetry.value.copy(isRunning = false)
    }

    fun isRunning(): Boolean {
        return if (isNativeAvailable && enginePtr != 0L) {
            try {
                OboeAudioEngineNative.nativeIsRunning(enginePtr)
            } catch (e: Throwable) {
                false
            }
        } else {
            _telemetry.value.isRunning
        }
    }

    fun setAncMode(mode: AncMode) {
        if (isNativeAvailable && enginePtr != 0L) {
            try {
                OboeAudioEngineNative.nativeSetAncMode(enginePtr, mode.ordinal)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed setting mode: ${e.message}")
            }
        }
        _telemetry.value = _telemetry.value.copy(activeMode = mode)
    }

    fun setAncStrength(strength: Float) {
        if (isNativeAvailable && enginePtr != 0L) {
            try {
                OboeAudioEngineNative.nativeSetAncStrength(enginePtr, strength)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed setting strength: ${e.message}")
            }
        }
    }

    fun setAudioDelayMs(delayMs: Float) {
        if (isNativeAvailable && enginePtr != 0L) {
            try {
                OboeAudioEngineNative.nativeSetAudioDelayMs(enginePtr, delayMs)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed setting delay: ${e.message}")
            }
        }
    }

    fun setFilterTaps(taps: Int) {
        if (isNativeAvailable && enginePtr != 0L) {
            try {
                OboeAudioEngineNative.nativeSetFilterTaps(enginePtr, taps)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed setting filter taps: ${e.message}")
            }
        }
    }

    fun setStepSize(stepSize: Float) {
        if (isNativeAvailable && enginePtr != 0L) {
            try {
                OboeAudioEngineNative.nativeSetStepSize(enginePtr, stepSize)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed setting step size: ${e.message}")
            }
        }
    }

    fun setLeakFactor(leakFactor: Float) {
        if (isNativeAvailable && enginePtr != 0L) {
            try {
                OboeAudioEngineNative.nativeSetLeakFactor(enginePtr, leakFactor)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed setting leak factor: ${e.message}")
            }
        }
    }

    fun resetFilterWeights() {
        if (isNativeAvailable && enginePtr != 0L) {
            try {
                OboeAudioEngineNative.nativeResetFilter(enginePtr)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed resetting filter: ${e.message}")
            }
        }
        _telemetry.value = _telemetry.value.copy(filterDiverged = false)
    }

    fun setAudioSourceVolume(volume: Float) {
        if (isNativeAvailable && enginePtr != 0L) {
            try {
                OboeAudioEngineNative.nativeSetAudioSourceVolume(enginePtr, volume)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed setting audio source volume: ${e.message}")
            }
        }
    }

    fun setPlayAudioSource(play: Boolean) {
        if (isNativeAvailable && enginePtr != 0L) {
            try {
                OboeAudioEngineNative.nativeSetPlayAudioSource(enginePtr, play)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed setting audio source play state: ${e.message}")
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                updateTelemetryAndVisualizer()
                delay(33) // ~30 FPS telemetry refresh
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun updateTelemetryAndVisualizer() {
        if (isNativeAvailable && enginePtr != 0L) {
            val ok = try {
                OboeAudioEngineNative.nativeGetMetrics(enginePtr, rawMetricsBuffer)
            } catch (e: Throwable) {
                false
            }

            if (ok) {
                val isRun = rawMetricsBuffer[0] > 0.5f
                val sr = rawMetricsBuffer[1].toInt()
                val inBuf = rawMetricsBuffer[2].toInt()
                val outBuf = rawMetricsBuffer[3].toInt()
                val xRuns = rawMetricsBuffer[4].toInt()
                val latency = rawMetricsBuffer[5]
                val cpuLoad = rawMetricsBuffer[6]
                val inDb = rawMetricsBuffer[7]
                val antiDb = rawMetricsBuffer[8]
                val outDb = rawMetricsBuffer[9]
                val diverged = rawMetricsBuffer[10] > 0.5f
                val modeOrd = rawMetricsBuffer[11].toInt()
                val mode = AncMode.values().getOrElse(modeOrd) { AncMode.EXPERIMENTAL_ANC }
                val backendCode = rawMetricsBuffer[12].toInt()
                val backendName = if (backendCode == 1) "Oboe (AAudio Low Latency)" else "Oboe (OpenSL ES)"

                _telemetry.value = OboeTelemetry(
                    isRunning = isRun,
                    sampleRate = sr,
                    inputBufferSizeFrames = inBuf,
                    outputBufferSizeFrames = outBuf,
                    xRuns = xRuns,
                    estimatedLatencyMs = latency,
                    dspCpuLoadPercent = cpuLoad,
                    inputPeakDb = inDb,
                    antiNoisePeakDb = antiDb,
                    outputPeakDb = outDb,
                    filterDiverged = diverged,
                    activeMode = mode,
                    backendName = backendName
                )

                // Visualizer snapshot
                val visOk = OboeAudioEngineNative.nativeGetVisualizerSnapshot(
                    enginePtr,
                    micWaveBuf,
                    antiNoiseWaveBuf,
                    mixWaveBuf
                )
                val specOk = OboeAudioEngineNative.nativeGetFrequencySpectrum(
                    enginePtr,
                    spectrumBuf
                )

                if (visOk || specOk) {
                    _visualizerSnapshot.value = VisualizerSnapshot(
                        micWaveform = micWaveBuf.clone(),
                        antiNoiseWaveform = antiNoiseWaveBuf.clone(),
                        outputWaveform = mixWaveBuf.clone(),
                        spectrumMagnitudes = spectrumBuf.clone(),
                        spectrumFrequencies = FloatArray(SPECTRUM_BINS) { it * (selectedSampleRate.sampleRateHz / (2f * SPECTRUM_BINS)) },
                        timestamp = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    fun release() {
        stop()
        if (isNativeAvailable && enginePtr != 0L) {
            try {
                OboeAudioEngineNative.nativeDestroyEngine(enginePtr)
                enginePtr = 0L
                Log.i(TAG, "Native Oboe audio engine destroyed successfully.")
            } catch (e: Throwable) {
                Log.e(TAG, "Exception releasing Oboe engine: ${e.message}")
            }
        }
    }
}
