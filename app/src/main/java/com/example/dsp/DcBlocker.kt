package com.example.dsp

/**
 * 1st-order IIR DC Blocker Filter.
 * Transfer function: H(z) = (1 - z^-1) / (1 - R * z^-1)
 * Difference equation: y[n] = x[n] - x[n-1] + R * y[n-1]
 */
class DcBlocker(private val r: Float = 0.995f) {
    private var x1: Float = 0f
    private var y1: Float = 0f

    fun process(x: Float): Float {
        val y = x - x1 + r * y1
        x1 = x
        y1 = if (y.isNaN() || y.isInfinite()) 0f else y
        return y1
    }

    fun process(input: FloatArray, output: FloatArray, count: Int) {
        for (i in 0 until count) {
            val x = input[i]
            val y = x - x1 + r * y1
            x1 = x
            y1 = if (y.isNaN() || y.isInfinite()) 0f else y
            output[i] = y1
        }
    }

    fun reset() {
        x1 = 0f
        y1 = 0f
    }
}
