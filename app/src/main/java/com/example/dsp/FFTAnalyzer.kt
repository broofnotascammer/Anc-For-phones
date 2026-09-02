package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-speed, zero-allocation Radix-2 Cooley-Tukey FFT Analyzer for real-time spectrum visualization.
 */
class FFTAnalyzer(val fftSize: Int = 128) {
    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)
    private val window = FloatArray(fftSize)
    private val cosTable = FloatArray(fftSize / 2)
    private val sinTable = FloatArray(fftSize / 2)

    init {
        require(fftSize > 0 && (fftSize and (fftSize - 1)) == 0) { "FFT size must be a power of 2" }
        // Precompute Hann Window
        for (i in 0 until fftSize) {
            window[i] = (0.5f * (1.0f - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
        }
        // Precompute trig tables
        for (i in 0 until fftSize / 2) {
            cosTable[i] = cos(-2.0 * PI * i / fftSize).toFloat()
            sinTable[i] = sin(-2.0 * PI * i / fftSize).toFloat()
        }
    }

    /**
     * Compute FFT magnitude spectrum into the provided outMagnitudes array (size fftSize / 2).
     */
    fun computeMagnitudes(samples: FloatArray, offset: Int, outMagnitudes: FloatArray) {
        val numSamples = minOf(samples.size - offset, fftSize)

        // Apply Hann window and copy into real, zero imag
        for (i in 0 until numSamples) {
            real[i] = samples[offset + i] * window[i]
            imag[i] = 0f
        }
        for (i in numSamples until fftSize) {
            real[i] = 0f
            imag[i] = 0f
        }

        // Bit-reversal permutation
        var j = 0
        val n = fftSize
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR
                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Cooley-Tukey butterfly computations
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val tableStep = n / len
            var i = 0
            while (i < n) {
                for (k in 0 until halfLen) {
                    val tableIdx = k * tableStep
                    val uR = cosTable[tableIdx]
                    val uI = sinTable[tableIdx]

                    val vR = real[i + k + halfLen] * uR - imag[i + k + halfLen] * uI
                    val vI = real[i + k + halfLen] * uI + imag[i + k + halfLen] * uR

                    real[i + k + halfLen] = real[i + k] - vR
                    imag[i + k + halfLen] = imag[i + k] - vI
                    real[i + k] += vR
                    imag[i + k] += vI
                }
                i += len
            }
            len = len shl 1
        }

        // Calculate normalized magnitude
        val outLen = minOf(outMagnitudes.size, fftSize / 2)
        val norm = 2.0f / fftSize
        for (k in 0 until outLen) {
            val mag = sqrt(real[k] * real[k] + imag[k] * imag[k]) * norm
            outMagnitudes[k] = mag
        }
    }
}
