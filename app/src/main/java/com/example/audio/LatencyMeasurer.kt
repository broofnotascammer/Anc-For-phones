package com.example.audio

import android.os.SystemClock
import com.example.data.LatencyCalibrationResult
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Latency Measurer and Acoustic Calibration Processor.
 * Uses cross-correlation of transmitted test chirp vs received microphone samples
 * to measure true end-to-end hardware+software acoustic delay.
 */
class LatencyMeasurer {

    /**
     * Cross-correlate reference test signal with recorded microphone buffer to find peak lag.
     */
    fun analyzeRoundTrip(
        referencePulse: FloatArray,
        recordedMicrophone: FloatArray,
        sampleRate: Int,
        dspProcessingTimeMs: Float
    ): LatencyCalibrationResult {
        val refLen = referencePulse.size
        val recLen = recordedMicrophone.size

        if (refLen == 0 || recLen == 0 || recLen < refLen) {
            return LatencyCalibrationResult(
                isSuccess = false,
                statusMessage = "Buffer too short for acoustic cross-correlation"
            )
        }

        // Calculate cross-correlation for positive lags
        val maxLag = recLen - refLen
        var peakCorrelation = 0.0f
        var peakLag = 0

        // Compute energy of reference
        var refEnergy = 0.0f
        for (i in 0 until refLen) {
            refEnergy += referencePulse[i] * referencePulse[i]
        }
        if (refEnergy < 1e-6f) refEnergy = 1.0f

        for (lag in 0 until maxLag) {
            var corr = 0.0f
            var recEnergy = 0.0f
            for (i in 0 until refLen) {
                val r = recordedMicrophone[lag + i]
                corr += referencePulse[i] * r
                recEnergy += r * r
            }

            val normalizedCorr = if (recEnergy > 1e-6f) {
                corr / sqrt(refEnergy * recEnergy)
            } else {
                0.0f
            }

            if (abs(normalizedCorr) > peakCorrelation) {
                peakCorrelation = abs(normalizedCorr)
                peakLag = lag
            }
        }

        val latencyMs = (peakLag.toFloat() / sampleRate) * 1000.0f
        val isConfident = peakCorrelation > 0.25f

        // Recommended delay compensation for music path alignment
        val recommendedDelayMs = latencyMs.coerceIn(0.0f, 200.0f)
        val totalPathMs = latencyMs + dspProcessingTimeMs

        return LatencyCalibrationResult(
            isSuccess = isConfident,
            measuredRoundTripMs = latencyMs,
            measuredRoundTripSamples = peakLag,
            confidence = peakCorrelation,
            recommendedDelayMs = recommendedDelayMs,
            dspTimeMs = dspProcessingTimeMs,
            totalPathDelayMs = totalPathMs,
            statusMessage = if (isConfident) {
                "Measured ${String.format("%.1f", latencyMs)} ms (Confidence: ${(peakCorrelation * 100).toInt()}%)"
            } else {
                "Low correlation signal (${(peakCorrelation * 100).toInt()}%). Check volume or mic position."
            }
        )
    }

    /**
     * Measure DSP block compute execution duration.
     */
    inline fun measureDspTime(block: () -> Unit): Float {
        val startNs = SystemClock.elapsedRealtimeNanos()
        block()
        val endNs = SystemClock.elapsedRealtimeNanos()
        return (endNs - startNs) / 1_000_000.0f
    }
}
