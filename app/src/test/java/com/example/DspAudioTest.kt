package com.example

import com.example.audio.DelayBuffer
import com.example.audio.FloatRingBuffer
import com.example.dsp.BandpassFilter
import com.example.dsp.DcBlocker
import com.example.dsp.FFTAnalyzer
import com.example.dsp.FilteredXLMS
import com.example.dsp.Limiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DspAudioTest {

    @Test
    fun testFloatRingBuffer_WriteRead() {
        val buffer = FloatRingBuffer(16)
        val input = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
        val written = buffer.write(input, 0, input.size)
        assertEquals(5, written)
        assertEquals(5, buffer.availableRead())

        val output = FloatArray(5)
        val read = buffer.read(output, 0, 5)
        assertEquals(5, read)
        for (i in input.indices) {
            assertEquals(input[i], output[i], 0.0001f)
        }
        assertEquals(0, buffer.availableRead())
    }

    @Test
    fun testDelayBuffer() {
        val delay = DelayBuffer(100)
        delay.push(10.0f)
        delay.push(20.0f)
        delay.push(30.0f)
        delay.push(40.0f)

        // Delay of 0 samples gives most recent (40.0f)
        val d0 = delay.getDelayed(0.0f)
        assertEquals(40.0f, d0, 0.001f)

        // Delay of 1 sample gives previous (30.0f)
        val d1 = delay.getDelayed(1.0f)
        assertEquals(30.0f, d1, 0.001f)

        // Delay of 1.5 samples interpolates between 30.0f and 20.0f -> 25.0f
        val d1_5 = delay.getDelayed(1.5f)
        assertEquals(25.0f, d1_5, 0.001f)
    }

    @Test
    fun testDcBlocker() {
        val dcBlocker = DcBlocker(0.995f)
        var out = 0.0f
        // Pass a constant DC offset of 2.0f
        for (i in 0 until 2000) {
            out = dcBlocker.process(2.0f)
        }
        // DC offset should decay towards 0
        assertTrue(abs(out) < 0.05f)
    }

    @Test
    fun testBandpassFilter() {
        val bp = BandpassFilter()
        bp.configure(sampleRate = 48000, lowCutoffHz = 100f, highCutoffHz = 1000f)

        // Zero signal should output zero
        val out0 = bp.process(0.0f)
        assertEquals(0.0f, out0, 0.0001f)
    }

    @Test
    fun testFilteredXLMS_Stability() {
        val fxlms = FilteredXLMS(filterTaps = 32, stepSizeMu = 0.01f)
        assertFalse(fxlms.isDiverged)

        // Process quiet noise
        for (i in 0 until 100) {
            val sample = ((i % 10) - 5) * 0.01f
            fxlms.processSample(sample, sample * 0.1f, adapt = true)
        }
        assertFalse(fxlms.isDiverged)
    }

    @Test
    fun testLimiter_PeakClamping() {
        val limiter = Limiter(threshold = 0.95f)
        // Feed extreme hot signal
        val limited = limiter.process(5.0f)
        assertTrue(abs(limited) <= 1.0f)

        // Test NaN protection
        val nanOut = limiter.process(Float.NaN)
        assertEquals(0.0f, nanOut, 0.0001f)
    }

    @Test
    fun testFFTAnalyzer() {
        val fft = FFTAnalyzer(128)
        val sineWave = FloatArray(128)
        for (i in 0 until 128) {
            sineWave[i] = kotlin.math.sin(2.0 * Math.PI * i / 16.0).toFloat()
        }
        val magnitudes = FloatArray(64)
        fft.computeMagnitudes(sineWave, 0, magnitudes)

        // Expect peak around bin 8 (128 / 16)
        var maxBin = 0
        var maxMag = 0f
        for (i in magnitudes.indices) {
            if (magnitudes[i] > maxMag) {
                maxMag = magnitudes[i]
                maxBin = i
            }
        }
        assertEquals(8, maxBin)
    }
}
