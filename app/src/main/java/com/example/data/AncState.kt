package com.example.data

enum class AncMode(val displayName: String, val description: String) {
    OFF("OFF", "No audio processing active"),
    MONITOR("MONITOR", "Microphone audio pass-through for diagnostics"),
    EXPERIMENTAL_ANC("ANC", "Real-time feed-forward anti-noise cancellation"),
    BYPASS("BYPASS", "Direct audio stream without ANC filter"),
    TEST_SIGNAL("TEST SIGNAL", "Synthetic signal generator for audio diagnostics")
}

enum class TestSignalType(val displayName: String) {
    SINE_440("Sine 440 Hz (A4)"),
    SINE_1000("Sine 1000 Hz"),
    CHIRP_SWEEP("Chirp Sweep (100-4000 Hz)"),
    WHITE_NOISE("White Noise"),
    PINK_NOISE("Pink Noise")
}

enum class DeviceSelectionMode {
    AUTOMATIC,
    MANUAL
}

data class DspParameters(
    val mode: AncMode = AncMode.OFF,
    val ancStrength: Float = 0.8f,          // 0.0f to 1.0f (or up to 1.5f)
    val audioDelayMs: Float = 0.0f,         // 0ms to 200ms
    val filterTaps: Int = 32,               // Number of FIR taps for adaptive filter (16 to 128)
    val stepSizeMu: Float = 0.015f,         // FxLMS adaptation step size mu
    val leakageGamma: Float = 0.0005f,      // Leakage factor to avoid weight drift
    val secondaryPathDelayMs: Float = 1.2f, // Estimated acoustic secondary path delay
    val lowCutoffHz: Float = 80.0f,         // High-pass cutoff for band-limiting
    val highCutoffHz: Float = 2000.0f,      // Low-pass cutoff for band-limiting (ANC sweet spot)
    val inputGainDb: Float = 0.0f,          // -12dB to +12dB
    val limiterThreshold: Float = 0.95f,    // Soft-knee safety limiter ceiling
    val testSignalType: TestSignalType = TestSignalType.SINE_440,
    val testSignalVolume: Float = 0.25f,
    val playAudioSourceTrack: Boolean = false,
    val audioSourceFrequencyHz: Float = 220.0f,
    val audioSourceVolume: Float = 0.3f
)

data class DspMetrics(
    val isRunning: Boolean = false,
    val mode: AncMode = AncMode.OFF,
    val sampleRate: Int = 48000,
    val bufferSizeFrames: Int = 256,
    val inputLatencyMs: Float = 0f,
    val outputLatencyMs: Float = 0f,
    val processingLatencyMs: Float = 0f,
    val totalEstimatedLatencyMs: Float = 0f,
    val dspLoadPercent: Float = 0f,
    val inputPeakLevelDb: Float = -96f,
    val antiNoisePeakLevelDb: Float = -96f,
    val outputPeakLevelDb: Float = -96f,
    val filterDiverged: Boolean = false,
    val bufferUnderruns: Long = 0,
    val bufferOverruns: Long = 0,
    val processedFrameCount: Long = 0,
    val lastError: String? = null
)

data class VisualizerSnapshot(
    val micWaveform: FloatArray = FloatArray(128),
    val antiNoiseWaveform: FloatArray = FloatArray(128),
    val outputWaveform: FloatArray = FloatArray(128),
    val spectrumMagnitudes: FloatArray = FloatArray(64),
    val spectrumFrequencies: FloatArray = FloatArray(64),
    val timestamp: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VisualizerSnapshot
        return timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        return timestamp.hashCode()
    }
}

data class AncFeasibilityReport(
    val inputAvailable: Boolean = false,
    val inputDeviceName: String = "None",
    val outputAvailable: Boolean = false,
    val outputDeviceName: String = "None",
    val simultaneousIoSupported: Boolean = false,
    val lowLatencySupported: Boolean = false,
    val nativeSampleRate: Int = 0,
    val nativeFramesPerBuffer: Int = 0,
    val estimatedRoundTripLatencyMs: Float = 0f,
    val isFeasibleForAnc: Boolean = false,
    val assessmentSummary: String = "",
    val details: List<String> = emptyList()
)

data class LatencyCalibrationResult(
    val isSuccess: Boolean = false,
    val measuredRoundTripMs: Float = 0f,
    val measuredRoundTripSamples: Int = 0,
    val confidence: Float = 0f,
    val recommendedDelayMs: Float = 0f,
    val dspTimeMs: Float = 0f,
    val totalPathDelayMs: Float = 0f,
    val statusMessage: String = "Not calibrated"
)

data class HardwareDiagnosticReport(
    val androidVersion: String,
    val apiLevel: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val audioManagerProperties: Map<String, String>,
    val availableInputs: List<String>,
    val availableOutputs: List<String>,
    val currentInputRoute: String,
    val currentOutputRoute: String,
    val lowLatencySupported: Boolean,
    val proAudioSupported: Boolean,
    val audioRecordState: String,
    val audioTrackState: String,
    val dspState: String,
    val metricsSummary: String,
    val timestamp: Long = System.currentTimeMillis()
)
