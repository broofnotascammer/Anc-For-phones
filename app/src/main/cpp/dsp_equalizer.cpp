#include "dsp_equalizer.h"

NativeEqualizer::NativeEqualizer(int sampleRate)
    : sampleRate_(sampleRate) {
    for (int i = 0; i < NUM_BANDS; ++i) {
        gainsDb_[i].store(0.0f, std::memory_order_relaxed);
        calculateBiquadCoefficients(i);
    }
}

void NativeEqualizer::setSampleRate(int sampleRate) {
    if (sampleRate <= 0 || sampleRate == sampleRate_) return;
    sampleRate_ = sampleRate;
    for (int i = 0; i < NUM_BANDS; ++i) {
        calculateBiquadCoefficients(i);
    }
}

void NativeEqualizer::setBandGain(int bandIndex, float gainDb) {
    if (bandIndex < 0 || bandIndex >= NUM_BANDS) return;
    gainDb = std::clamp(gainDb, -15.0f, 15.0f);
    gainsDb_[bandIndex].store(gainDb, std::memory_order_relaxed);
    calculateBiquadCoefficients(bandIndex);
}

void NativeEqualizer::setBandGains(const float* gains, int count) {
    if (!gains) return;
    int limit = std::min(count, NUM_BANDS);
    for (int i = 0; i < limit; ++i) {
        float g = std::clamp(gains[i], -15.0f, 15.0f);
        gainsDb_[i].store(g, std::memory_order_relaxed);
        calculateBiquadCoefficients(i);
    }
}

void NativeEqualizer::setEnabled(bool enabled) {
    enabled_.store(enabled, std::memory_order_relaxed);
}

void NativeEqualizer::reset() {
    for (int i = 0; i < NUM_BANDS; ++i) {
        filters_[i].reset();
    }
}

float NativeEqualizer::getBandGain(int bandIndex) const {
    if (bandIndex < 0 || bandIndex >= NUM_BANDS) return 0.0f;
    return gainsDb_[bandIndex].load(std::memory_order_relaxed);
}

void NativeEqualizer::processBuffer(float* buffer, int numSamples) {
    if (!enabled_.load(std::memory_order_relaxed) || !buffer) return;
    for (int s = 0; s < numSamples; ++s) {
        float sample = buffer[s];
        for (int i = 0; i < NUM_BANDS; ++i) {
            sample = filters_[i].process(sample);
        }
        buffer[s] = sample;
    }
}

void NativeEqualizer::calculateBiquadCoefficients(int bandIndex) {
    if (bandIndex < 0 || bandIndex >= NUM_BANDS) return;

    float gainDb = gainsDb_[bandIndex].load(std::memory_order_relaxed);
    float f0 = DEFAULT_FREQUENCIES[bandIndex];
    float Q = DEFAULT_Q[bandIndex];

    // If gain is essentially 0 dB, set passthrough biquad
    if (std::abs(gainDb) < 0.05f) {
        filters_[bandIndex].b0 = 1.0f;
        filters_[bandIndex].b1 = 0.0f;
        filters_[bandIndex].b2 = 0.0f;
        filters_[bandIndex].a1 = 0.0f;
        filters_[bandIndex].a2 = 0.0f;
        return;
    }

    // Standard Robert Bristow-Johnson (RBJ) Audio EQ Cookbook peaking EQ
    float A = std::pow(10.0f, gainDb / 40.0f);
    float w0 = 2.0f * static_cast<float>(M_PI) * f0 / static_cast<float>(sampleRate_);
    float alpha = std::sin(w0) / (2.0f * Q);
    float cosw0 = std::cos(w0);

    float b0 = 1.0f + alpha * A;
    float b1 = -2.0f * cosw0;
    float b2 = 1.0f - alpha * A;
    float a0 = 1.0f + alpha / A;
    float a1 = -2.0f * cosw0;
    float a2 = 1.0f - alpha / A;

    // Normalize coefficients by a0
    filters_[bandIndex].b0 = b0 / a0;
    filters_[bandIndex].b1 = b1 / a0;
    filters_[bandIndex].b2 = b2 / a0;
    filters_[bandIndex].a1 = a1 / a0;
    filters_[bandIndex].a2 = a2 / a0;
}
