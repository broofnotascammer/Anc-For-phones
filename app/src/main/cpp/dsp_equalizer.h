#ifndef DSP_EQUALIZER_H
#define DSP_EQUALIZER_H

#include <vector>
#include <cmath>
#include <algorithm>
#include <array>
#include <atomic>

struct BiquadFilter {
    float b0 = 1.0f;
    float b1 = 0.0f;
    float b2 = 0.0f;
    float a1 = 0.0f;
    float a2 = 0.0f;

    // Filter state
    float z1 = 0.0f;
    float z2 = 0.0f;

    inline void reset() {
        z1 = 0.0f;
        z2 = 0.0f;
    }

    // Direct Form II Transposed for optimal numerical stability and performance
    inline float process(float x) {
        float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }
};

class NativeEqualizer {
public:
    static constexpr int NUM_BANDS = 5;
    static constexpr std::array<float, NUM_BANDS> DEFAULT_FREQUENCIES = {60.0f, 250.0f, 1000.0f, 4000.0f, 12000.0f};
    static constexpr std::array<float, NUM_BANDS> DEFAULT_Q = {0.707f, 1.0f, 1.0f, 1.0f, 0.707f};

    NativeEqualizer(int sampleRate = 48000);
    ~NativeEqualizer() = default;

    void setSampleRate(int sampleRate);
    void setBandGain(int bandIndex, float gainDb);
    void setBandGains(const float* gains, int count);
    void setEnabled(bool enabled);
    bool isEnabled() const { return enabled_.load(std::memory_order_relaxed); }
    void reset();

    // Process a single audio sample in-place
    inline float processSample(float inSample) {
        if (!enabled_.load(std::memory_order_relaxed)) {
            return inSample;
        }
        float out = inSample;
        for (int i = 0; i < NUM_BANDS; ++i) {
            out = filters_[i].process(out);
        }
        return out;
    }

    // Process a buffer of samples
    void processBuffer(float* buffer, int numSamples);

    float getBandGain(int bandIndex) const;

private:
    int sampleRate_{48000};
    std::atomic<bool> enabled_{true};
    std::array<std::atomic<float>, NUM_BANDS> gainsDb_{};
    std::array<BiquadFilter, NUM_BANDS> filters_{};

    void calculateBiquadCoefficients(int bandIndex);
};

#endif // DSP_EQUALIZER_H
