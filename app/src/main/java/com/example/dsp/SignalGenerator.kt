package com.example.dsp

import com.example.data.TestSignalType
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Real-time synthesis generator for test signals, calibration chirps, and diagnostic audio.
 */
class SignalGenerator(private var sampleRate: Int = 48000) {
    private var phase = 0.0
    private var b0 = 0.0f
    private var b1 = 0.0f
    private var b2 = 0.0f
    private var b3 = 0.0f
    private var b4 = 0.0f
    private var b5 = 0.0f
    private var b6 = 0.0f

    fun updateSampleRate(sr: Int) {
        this.sampleRate = sr
    }

    fun generateSignal(
        type: TestSignalType,
        output: FloatArray,
        count: Int,
        amplitude: Float = 0.25f,
        customFrequencyHz: Float = 440f
    ) {
        val amp = amplitude.coerceIn(0.0f, 1.0f)
        val sr = sampleRate.toDouble()

        when (type) {
            TestSignalType.SINE_440 -> {
                val freq = 440.0
                val phaseInc = 2.0 * PI * freq / sr
                for (i in 0 until count) {
                    output[i] = (sin(phase) * amp).toFloat()
                    phase += phaseInc
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                }
            }
            TestSignalType.SINE_1000 -> {
                val freq = 1000.0
                val phaseInc = 2.0 * PI * freq / sr
                for (i in 0 until count) {
                    output[i] = (sin(phase) * amp).toFloat()
                    phase += phaseInc
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                }
            }
            TestSignalType.CHIRP_SWEEP -> {
                // Logarithmic frequency sweep from 100 Hz to 4000 Hz over ~1 second loop
                val sweepDurationSec = 1.0
                val fStart = 100.0
                val fEnd = 4000.0
                for (i in 0 until count) {
                    val t = (phase / (2.0 * PI * 100.0)) % sweepDurationSec
                    val instFreq = fStart + (fEnd - fStart) * (t / sweepDurationSec)
                    val phaseInc = 2.0 * PI * instFreq / sr
                    output[i] = (sin(phase) * amp).toFloat()
                    phase += phaseInc
                    if (phase >= 2.0 * PI * 10000) phase = 0.0
                }
            }
            TestSignalType.WHITE_NOISE -> {
                for (i in 0 until count) {
                    val rnd = (Random.nextFloat() * 2.0f - 1.0f)
                    output[i] = rnd * amp
                }
            }
            TestSignalType.PINK_NOISE -> {
                // Paul Kellet's filtered pink noise generator
                for (i in 0 until count) {
                    val white = Random.nextFloat() * 2.0f - 1.0f
                    b0 = 0.99886f * b0 + white * 0.0555179f
                    b1 = 0.99332f * b1 + white * 0.0750759f
                    b2 = 0.96900f * b2 + white * 0.1538520f
                    b3 = 0.86650f * b3 + white * 0.3104856f
                    b4 = 0.55000f * b4 + white * 0.5329522f
                    b5 = -0.7616f * b5 - white * 0.0168980f
                    val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f) * 0.11f
                    b6 = white * 0.115926f
                    output[i] = pink.coerceIn(-1.0f, 1.0f) * amp
                }
            }
        }
    }

    /**
     * Generate an acoustic test impulse / short windowed burst for round-trip latency calibration.
     */
    fun generateCalibrationImpulse(output: FloatArray, count: Int, pulseFrequencyHz: Float = 1500f) {
        val sr = sampleRate.toDouble()
        val pulseLength = minOf(count, (sr * 0.01).toInt()) // 10ms pulse
        val phaseInc = 2.0 * PI * pulseFrequencyHz / sr
        var p = 0.0

        for (i in 0 until pulseLength) {
            val window = (0.5f * (1.0f - kotlin.math.cos(2.0 * PI * i / pulseLength))).toFloat()
            output[i] = (sin(p) * window * 0.7f).toFloat()
            p += phaseInc
        }
        for (i in pulseLength until count) {
            output[i] = 0.0f
        }
    }

    fun reset() {
        phase = 0.0
        b0 = 0f
        b1 = 0f
        b2 = 0f
        b3 = 0f
        b4 = 0f
        b5 = 0f
        b6 = 0f
    }
}
