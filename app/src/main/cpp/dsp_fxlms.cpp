#include "dsp_fxlms.h"

NativeFxLMS::NativeFxLMS(int filterTaps, float stepSize, float leakFactor)
    : numTaps_(filterTaps),
      stepSize_(stepSize),
      leakFactor_(leakFactor),
      historyIndex_(0),
      filteredXIndex_(0),
      diverged_(false),
      stabilityCheckCounter_(0) {
    weights_.assign(numTaps_, 0.0f);
    historyBuffer_.assign(numTaps_ * 2, 0.0f);
    initSecondaryPathModel();
}

void NativeFxLMS::initSecondaryPathModel() {
    // Standard bandpass acoustic secondary path estimate (transducer + acoustic delay)
    secondaryPathWeights_ = {
        0.02f, 0.08f, 0.25f, 0.50f, 0.35f, 0.15f, 0.05f, 0.01f,
        -0.02f, -0.05f, -0.08f, -0.04f, -0.02f, 0.0f, 0.01f, 0.02f
    };
    filteredXHistory_.assign(secondaryPathWeights_.size() * 2, 0.0f);
}

void NativeFxLMS::setFilterTaps(int taps) {
    if (taps < 8) taps = 8;
    if (taps > 256) taps = 256;
    if (numTaps_ == taps) return;

    numTaps_ = taps;
    weights_.assign(numTaps_, 0.0f);
    historyBuffer_.assign(numTaps_ * 2, 0.0f);
    historyIndex_ = 0;
}

void NativeFxLMS::setStepSize(float mu) {
    stepSize_ = std::clamp(mu, 0.00001f, 0.1f);
}

void NativeFxLMS::setLeakFactor(float leak) {
    leakFactor_ = std::clamp(leak, 0.90f, 1.0f);
}

void NativeFxLMS::reset() {
    std::fill(weights_.begin(), weights_.end(), 0.0f);
    std::fill(historyBuffer_.begin(), historyBuffer_.end(), 0.0f);
    std::fill(filteredXHistory_.begin(), filteredXHistory_.end(), 0.0f);
    historyIndex_ = 0;
    filteredXIndex_ = 0;
    diverged_ = false;
    stabilityCheckCounter_ = 0;
}

float NativeFxLMS::processSample(float micInput, float desiredCancellationGain) {
    // 1. Store mic input into circular history buffer
    historyBuffer_[historyIndex_] = micInput;
    historyBuffer_[historyIndex_ + numTaps_] = micInput; // Duplicate for linear convolution without modulo

    // 2. Compute anti-noise FIR filter output y(n) = sum(w_k * x(n-k))
    const float* histPtr = &historyBuffer_[historyIndex_];
    float y = 0.0f;
    for (int k = 0; k < numTaps_; ++k) {
        y += weights_[k] * histPtr[numTaps_ - 1 - k];
    }

    // 3. Compute filtered-X sample: x_hat(n) = S_hat(z) * x(n)
    int sSize = static_cast<int>(secondaryPathWeights_.size());
    filteredXHistory_[filteredXIndex_] = micInput;
    filteredXHistory_[filteredXIndex_ + sSize] = micInput;
    const float* sHistPtr = &filteredXHistory_[filteredXIndex_];

    float filteredX = 0.0f;
    for (int k = 0; k < sSize; ++k) {
        filteredX += secondaryPathWeights_[k] * sHistPtr[sSize - 1 - k];
    }

    // Advance circular indices
    historyIndex_ = (historyIndex_ + 1) % numTaps_;
    filteredXIndex_ = (filteredXIndex_ + 1) % sSize;

    // Apply active cancellation inversion and gain scaling
    float antiNoise = -y * desiredCancellationGain;

    // Stability clamp
    if (antiNoise > 1.0f) antiNoise = 1.0f;
    else if (antiNoise < -1.0f) antiNoise = -1.0f;

    return antiNoise;
}

void NativeFxLMS::updateWeights(float errorSignal) {
    // Normalized leaky LMS update: w(n+1) = leak * w(n) + mu * e(n) * x_hat(n)
    float effectiveMu = stepSize_;
    const float* histPtr = &historyBuffer_[historyIndex_];

    float weightPower = 0.0f;
    for (int k = 0; k < numTaps_; ++k) {
        float grad = errorSignal * histPtr[numTaps_ - 1 - k];
        weights_[k] = leakFactor_ * weights_[k] + effectiveMu * grad;
        weightPower += weights_[k] * weights_[k];
    }

    // Periodic divergence safeguard
    if (++stabilityCheckCounter_ > 128) {
        stabilityCheckCounter_ = 0;
        if (weightPower > 25.0f || std::isnan(weightPower)) {
            diverged_ = true;
            for (int k = 0; k < numTaps_; ++k) {
                weights_[k] *= 0.5f;
            }
        }
    }
}

void NativeFxLMS::getWeights(float* outBuffer, int maxCount) const {
    if (!outBuffer || maxCount <= 0) return;
    int count = std::min(maxCount, numTaps_);
    std::memcpy(outBuffer, weights_.data(), count * sizeof(float));
}
