package com.example.dsp

import kotlin.math.PI
import kotlin.math.sin

/**
 * Synthetic audio source providing melody/tones to simulate music/voice stream
 * that gets delayed, mixed with anti-noise, and output to headphones.
 */
class AudioSourcePlayer(private var sampleRate: Int = 48000) {
    private var phase = 0.0
    private var noteIndex = 0
    private var sampleCount = 0

    // Harmonic pentatonic chords progression to sound pleasant
    private val frequencies = floatArrayOf(
        261.63f, 329.63f, 392.00f, 523.25f, // C4, E4, G4, C5
        220.00f, 261.63f, 329.63f, 440.00f, // A3, C4, E4, A4
        174.61f, 220.00f, 261.63f, 349.23f, // F3, A3, C4, F4
        196.00f, 246.94f, 293.66f, 392.00f  // G3, B3, D4, G4
    )

    fun updateSampleRate(sr: Int) {
        this.sampleRate = sr
    }

    fun generateBlock(output: FloatArray, count: Int, volume: Float) {
        val samplesPerNote = (sampleRate * 0.35f).toInt() // Change note every 350ms
        val vol = volume.coerceIn(0f, 1f)

        for (i in 0 until count) {
            val currentFreq = frequencies[noteIndex % frequencies.size]
            val phaseInc = 2.0 * PI * currentFreq / sampleRate

            // Rich warm synth tone (fundamental + 2nd harmonic)
            val s1 = sin(phase)
            val s2 = sin(phase * 2.0) * 0.3
            output[i] = ((s1 + s2) * 0.5 * vol).toFloat()

            phase += phaseInc
            if (phase >= 2.0 * PI) phase -= 2.0 * PI

            sampleCount++
            if (sampleCount >= samplesPerNote) {
                sampleCount = 0
                noteIndex = (noteIndex + 1) % frequencies.size
            }
        }
    }

    fun reset() {
        phase = 0.0
        noteIndex = 0
        sampleCount = 0
    }
}
