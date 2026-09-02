package com.example.audio.oboe

import com.example.data.AncMode

/**
 * Supported sample rates including high-sample-rate recording and playback options.
 */
enum class HighSampleRate(val sampleRateHz: Int, val label: String) {
    STANDARD_44K(44100, "44.1 kHz (CD Audio)"),
    STANDARD_48K(48000, "48.0 kHz (Studio Pro)"),
    HIGH_RES_96K(96000, "96.0 kHz (Hi-Res Audio)"),
    ULTRA_RES_192K(192000, "192.0 kHz (Ultra Hi-Res)")
}

/**
 * Hardware sharing mode for Oboe low-latency stream.
 */
enum class OboeSharingMode(val isExclusive: Boolean, val label: String) {
    EXCLUSIVE(true, "Exclusive (Ultra Low Latency)"),
    SHARED(false, "Shared (System Compatible)")
}

/**
 * Native telemetry metrics snapshot provided by C++ Oboe engine.
 */
data class OboeTelemetry(
    val isRunning: Boolean = false,
    val sampleRate: Int = 48000,
    val inputBufferSizeFrames: Int = 192,
    val outputBufferSizeFrames: Int = 192,
    val xRuns: Int = 0,
    val estimatedLatencyMs: Float = 0f,
    val dspCpuLoadPercent: Float = 0f,
    val inputPeakDb: Float = -90f,
    val antiNoisePeakDb: Float = -90f,
    val outputPeakDb: Float = -90f,
    val filterDiverged: Boolean = false,
    val activeMode: AncMode = AncMode.EXPERIMENTAL_ANC,
    val backendName: String = "Oboe AAudio"
)
