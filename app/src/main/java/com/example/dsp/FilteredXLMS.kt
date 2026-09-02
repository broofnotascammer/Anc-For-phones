package com.example.dsp

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Filtered-X Least Mean Squares (FxLMS) Feed-Forward Active Noise Cancellation Engine.
 *
 * Signal Flow:
 * 1. Reference noise x(n) is captured by the environmental microphone.
 * 2. Secondary path model S_hat(z) filters reference to obtain x_prime(n) = S_hat(z) * x(n).
 * 3. Adaptive FIR filter W(z) generates anti-noise y(n) = W^T * X(n).
 * 4. Inverted anti-noise -y(n) is output to headphones.
 * 5. Normalized Leaky LMS updates weights:
 *    w_k(n+1) = (1 - mu * gamma) * w_k(n) + (mu / (||X_prime||^2 + eps)) * e(n) * x_prime(n-k)
 */
class FilteredXLMS(
    var filterTaps: Int = 32,
    var stepSizeMu: Float = 0.015f,
    var leakageGamma: Float = 0.0005f,
    private val maxWeightsNorm: Float = 10.0f
) {
    // FIR Adaptive weights W(n)
    private var weights = FloatArray(MAX_TAPS)

    // Reference signal history X(n)
    private var xHistory = FloatArray(MAX_TAPS)
    private var xHistoryIndex = 0

    // Filtered reference signal history X'(n)
    private var xPrimeHistory = FloatArray(MAX_TAPS)
    private var xPrimeHistoryIndex = 0

    // Secondary path FIR model S_hat (approximates DAC + acoustic propagation delay)
    private var sHat = FloatArray(SECONDARY_PATH_TAPS)
    private var sHatHistory = FloatArray(SECONDARY_PATH_TAPS)
    private var sHatHistoryIndex = 0

    var isDiverged = false
        private set

    init {
        initDefaultSecondaryPath(delaySamples = 48) // ~1ms at 48kHz
        reset()
    }

    fun initDefaultSecondaryPath(delaySamples: Int) {
        sHat.fill(0f)
        val delay = delaySamples.coerceIn(0, SECONDARY_PATH_TAPS - 1)
        sHat[delay] = 0.9f
        if (delay + 1 < SECONDARY_PATH_TAPS) sHat[delay + 1] = 0.3f
        if (delay + 2 < SECONDARY_PATH_TAPS) sHat[delay + 2] = -0.1f
    }

    fun updateParameters(taps: Int, mu: Float, gamma: Float, secondaryDelaySamples: Int) {
        val clampedTaps = taps.coerceIn(8, MAX_TAPS)
        if (clampedTaps != this.filterTaps) {
            this.filterTaps = clampedTaps
            reset()
        }
        this.stepSizeMu = mu.coerceIn(0.0001f, 0.2f)
        this.leakageGamma = gamma.coerceIn(0.0f, 0.01f)
        initDefaultSecondaryPath(secondaryDelaySamples)
    }

    /**
     * Process one sample through the FxLMS pipeline.
     * @param x Reference microphone noise sample
     * @param errorSignal Estimated or measured error signal
     * @return Phase-inverted Anti-noise sample (-y)
     */
    fun processSample(x: Float, errorSignal: Float, adapt: Boolean = true): Float {
        if (x.isNaN() || x.isInfinite()) {
            reset()
            isDiverged = true
            return 0f
        }

        val taps = filterTaps

        // 1. Push x into reference history
        xHistory[xHistoryIndex] = x
        
        // 2. Filter reference through secondary path model S_hat(z) -> x_prime
        sHatHistory[sHatHistoryIndex] = x
        var xPrime = 0.0f
        var sIdx = sHatHistoryIndex
        for (i in 0 until SECONDARY_PATH_TAPS) {
            xPrime += sHat[i] * sHatHistory[sIdx]
            sIdx--
            if (sIdx < 0) sIdx += SECONDARY_PATH_TAPS
        }
        sHatHistoryIndex = (sHatHistoryIndex + 1) % SECONDARY_PATH_TAPS

        // Push xPrime into filtered reference history
        xPrimeHistory[xPrimeHistoryIndex] = xPrime

        // 3. Compute anti-noise y(n) = sum(w_k * x(n-k))
        var y = 0.0f
        var xIdx = xHistoryIndex
        for (k in 0 until taps) {
            y += weights[k] * xHistory[xIdx]
            xIdx--
            if (xIdx < 0) xIdx += MAX_TAPS
        }

        // Advance circular index
        xHistoryIndex = (xHistoryIndex + 1) % MAX_TAPS
        val currentXPrimeIdx = xPrimeHistoryIndex
        xPrimeHistoryIndex = (xPrimeHistoryIndex + 1) % MAX_TAPS

        // 4. Adapt weights if enabled
        if (adapt && !isDiverged) {
            // Compute power of X' for normalization (NLMS)
            var xPrimePower = 1e-4f
            var xpIdx = currentXPrimeIdx
            for (k in 0 until taps) {
                val sample = xPrimeHistory[xpIdx]
                xPrimePower += sample * sample
                xpIdx--
                if (xpIdx < 0) xpIdx += MAX_TAPS
            }

            val normalizedMu = stepSizeMu / xPrimePower
            val leak = 1.0f - (stepSizeMu * leakageGamma)

            var weightSumSq = 0.0f
            xpIdx = currentXPrimeIdx
            for (k in 0 until taps) {
                val xp = xPrimeHistory[xpIdx]
                val updatedW = (leak * weights[k]) + (normalizedMu * errorSignal * xp)
                if (updatedW.isNaN() || updatedW.isInfinite() || abs(updatedW) > 5.0f) {
                    isDiverged = true
                    reset()
                    return 0f
                }
                weights[k] = updatedW
                weightSumSq += updatedW * updatedW
                xpIdx--
                if (xpIdx < 0) xpIdx += MAX_TAPS
            }

            if (weightSumSq > maxWeightsNorm * maxWeightsNorm) {
                // Stabilize weights by scaling down
                val scale = maxWeightsNorm / sqrt(weightSumSq)
                for (k in 0 until taps) {
                    weights[k] *= scale
                }
            }
        }

        // Return phase-inverted anti-noise (-y)
        return -y
    }

    fun processBlock(
        referenceIn: FloatArray,
        antiNoiseOut: FloatArray,
        count: Int,
        ancStrength: Float
    ) {
        for (i in 0 until count) {
            val ref = referenceIn[i]
            // In open-loop single-mic feedforward mode, residual error is modeled from input & filtered anti-noise
            val anti = processSample(x = ref, errorSignal = ref * 0.1f, adapt = true)
            antiNoiseOut[i] = anti * ancStrength
        }
    }

    fun reset() {
        weights.fill(0f)
        // Initialize central tap with modest inverting response
        weights[0] = 0.5f
        xHistory.fill(0f)
        xPrimeHistory.fill(0f)
        sHatHistory.fill(0f)
        xHistoryIndex = 0
        xPrimeHistoryIndex = 0
        sHatHistoryIndex = 0
        isDiverged = false
    }

    companion object {
        const val MAX_TAPS = 128
        const val SECONDARY_PATH_TAPS = 64
    }
}
