package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2nd-order Biquad Bandpass Filter (Constant 0 dB peak gain).
 * Restricts ANC frequency range to physical acoustic sweet spot (typically 80 Hz - 2000 Hz).
 */
class BandpassFilter(
    private var sampleRate: Int = 48000,
    private var centerFreqHz: Float = 500f,
    private var q: Float = 1.0f
) {
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    init {
        recalculateCoefficients()
    }

    fun configure(sampleRate: Int, lowCutoffHz: Float, highCutoffHz: Float) {
        this.sampleRate = sampleRate
        val low = lowCutoffHz.coerceAtLeast(20f)
        val high = highCutoffHz.coerceAtMost((sampleRate / 2f) - 100f).coerceAtLeast(low + 10f)

        // Center frequency is geometric mean
        this.centerFreqHz = sqrt(low * high)
        val bandwidth = (high - low).coerceAtLeast(10f)
        this.q = (centerFreqHz / bandwidth).coerceIn(0.1f, 10f)

        recalculateCoefficients()
    }

    private fun recalculateCoefficients() {
        val w0 = (2.0 * PI * centerFreqHz / sampleRate).toFloat()
        val alpha = (sin(w0.toDouble()) / (2.0 * q)).toFloat()
        val cosW0 = cos(w0.toDouble()).toFloat()

        val a0 = 1.0f + alpha
        b0 = alpha / a0
        b1 = 0.0f
        b2 = -alpha / a0
        a1 = (-2.0f * cosW0) / a0
        a2 = (1.0f - alpha) / a0
    }

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = if (y.isNaN() || y.isInfinite()) 0f else y
        return y1
    }

    fun process(input: FloatArray, output: FloatArray, count: Int) {
        for (i in 0 until count) {
            val x = input[i]
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = if (y.isNaN() || y.isInfinite()) 0f else y
            output[i] = y1
        }
    }

    fun reset() {
        x1 = 0f
        x2 = 0f
        y1 = 0f
        y2 = 0f
    }
}
