package com.example.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 5-Band Parametric/Peaking Biquad Equalizer for real-time acoustic shaping.
 * Bands:
 * 0: 60 Hz (Sub-Bass)
 * 1: 250 Hz (Bass)
 * 2: 1000 Hz (Midrange)
 * 3: 4000 Hz (High-Mid / Presence)
 * 4: 12000 Hz (Treble / Air)
 */
class EqualizerFilter(
    private var sampleRate: Int = 48000
) {
    companion object {
        const val NUM_BANDS = 5
        val DEFAULT_FREQUENCIES = floatArrayOf(60f, 250f, 1000f, 4000f, 12000f)
        val DEFAULT_Q = floatArrayOf(0.707f, 1.0f, 1.0f, 1.0f, 0.707f)
    }

    private class BiquadSection {
        var b0 = 1.0f
        var b1 = 0.0f
        var b2 = 0.0f
        var a1 = 0.0f
        var a2 = 0.0f

        var z1 = 0.0f
        var z2 = 0.0f

        fun reset() {
            z1 = 0.0f
            z2 = 0.0f
        }

        inline fun process(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    var isEnabled: Boolean = true
    private val gainsDb = FloatArray(NUM_BANDS) { 0.0f }
    private val filters = Array(NUM_BANDS) { BiquadSection() }

    init {
        for (i in 0 until NUM_BANDS) {
            updateCoefficients(i)
        }
    }

    fun setSampleRate(sampleRate: Int) {
        if (sampleRate <= 0 || sampleRate == this.sampleRate) return
        this.sampleRate = sampleRate
        for (i in 0 until NUM_BANDS) {
            updateCoefficients(i)
        }
    }

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0 until NUM_BANDS) return
        val clamped = gainDb.coerceIn(-15.0f, 15.0f)
        gainsDb[bandIndex] = clamped
        updateCoefficients(bandIndex)
    }

    fun setBandGains(gains: FloatArray) {
        val limit = minOf(gains.size, NUM_BANDS)
        for (i in 0 until limit) {
            val clamped = gains[i].coerceIn(-15.0f, 15.0f)
            gainsDb[i] = clamped
            updateCoefficients(i)
        }
    }

    fun getBandGain(bandIndex: Int): Float {
        if (bandIndex !in 0 until NUM_BANDS) return 0f
        return gainsDb[bandIndex]
    }

    fun reset() {
        for (f in filters) {
            f.reset()
        }
    }

    fun process(sample: Float): Float {
        if (!isEnabled) return sample
        var out = sample
        for (i in 0 until NUM_BANDS) {
            out = filters[i].process(out)
        }
        return out
    }

    private fun updateCoefficients(bandIndex: Int) {
        if (bandIndex !in 0 until NUM_BANDS) return
        val gainDb = gainsDb[bandIndex]
        val filter = filters[bandIndex]

        if (abs(gainDb) < 0.05f) {
            filter.b0 = 1.0f
            filter.b1 = 0.0f
            filter.b2 = 0.0f
            filter.a1 = 0.0f
            filter.a2 = 0.0f
            return
        }

        val f0 = DEFAULT_FREQUENCIES[bandIndex]
        val q = DEFAULT_Q[bandIndex]

        val a = 10.0.pow((gainDb / 40.0)).toFloat()
        val w0 = (2.0 * PI * f0 / sampleRate).toFloat()
        val alpha = (sin(w0.toDouble()) / (2.0 * q)).toFloat()
        val cosW0 = cos(w0.toDouble()).toFloat()

        val b0 = 1.0f + alpha * a
        val b1 = -2.0f * cosW0
        val b2 = 1.0f - alpha * a
        val a0 = 1.0f + alpha / a
        val a1 = -2.0f * cosW0
        val a2 = 1.0f - alpha / a

        filter.b0 = b0 / a0
        filter.b1 = b1 / a0
        filter.b2 = b2 / a0
        filter.a1 = a1 / a0
        filter.a2 = a2 / a0
    }
}
