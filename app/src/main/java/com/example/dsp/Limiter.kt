package com.example.dsp

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.tanh

/**
 * Soft-knee audio limiter and safety envelope guard.
 * Prevents speaker blowout, clipping distortion, and protects user hearing.
 */
class Limiter(
    var threshold: Float = 0.95f,
    private val attackTimeMs: Float = 1.0f,
    private val releaseTimeMs: Float = 50.0f,
    sampleRate: Int = 48000
) {
    private var envelope = 0.0f
    private var attackCoeff = 0.0f
    private var releaseCoeff = 0.0f

    init {
        updateSampleRate(sampleRate)
    }

    fun updateSampleRate(sampleRate: Int) {
        val attackSamples = max(1f, (attackTimeMs * 0.001f * sampleRate))
        val releaseSamples = max(1f, (releaseTimeMs * 0.001f * sampleRate))
        attackCoeff = (-1.0f / attackSamples).let { kotlin.math.exp(it.toDouble()).toFloat() }
        releaseCoeff = (-1.0f / releaseSamples).let { kotlin.math.exp(it.toDouble()).toFloat() }
    }

    fun process(sample: Float): Float {
        if (sample.isNaN() || sample.isInfinite()) {
            return 0.0f
        }

        val absSample = abs(sample)
        envelope = if (absSample > envelope) {
            attackCoeff * envelope + (1.0f - attackCoeff) * absSample
        } else {
            releaseCoeff * envelope + (1.0f - releaseCoeff) * absSample
        }

        var gain = 1.0f
        if (envelope > threshold) {
            gain = threshold / envelope
        }

        val limited = sample * gain

        // Soft-knee tanh saturation ceiling
        return if (abs(limited) > 0.98f) {
            tanh(limited.toDouble()).toFloat() * 0.98f
        } else {
            limited
        }
    }

    fun process(input: FloatArray, output: FloatArray, count: Int) {
        for (i in 0 until count) {
            output[i] = process(input[i])
        }
    }

    fun reset() {
        envelope = 0.0f
    }
}
