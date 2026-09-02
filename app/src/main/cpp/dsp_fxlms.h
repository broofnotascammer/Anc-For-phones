#ifndef DSP_FXLMS_H
#define DSP_FXLMS_H

#include <vector>
#include <cmath>
#include <algorithm>
#include <cstring>

class NativeFxLMS {
public:
    NativeFxLMS(int filterTaps = 64, float stepSize = 0.005f, float leakFactor = 0.9995f);
    ~NativeFxLMS() = default;

    void setFilterTaps(int taps);
    void setStepSize(float mu);
    void setLeakFactor(float leak);
    void reset();

    // Process a single sample through the adaptive filter
    // micInput: reference noise sample x(n)
    // Returns: anti-noise sample y(n)
    float processSample(float micInput, float desiredCancellationGain);

    // Update weights using error signal e(n)
    void updateWeights(float errorSignal);

    // Get filter weights snapshot for visualization/analysis
    void getWeights(float* outBuffer, int maxCount) const;

    bool hasDiverged() const { return diverged_; }
    void clearDivergence() { diverged_ = false; }

private:
    int numTaps_;
    float stepSize_;
    float leakFactor_;

    std::vector<float> weights_;
    std::vector<float> historyBuffer_;
    int historyIndex_;

    // Secondary path modeled FIR filter (estimate S_hat(z))
    std::vector<float> secondaryPathWeights_;
    std::vector<float> filteredXHistory_;
    int filteredXIndex_;

    bool diverged_;
    int stabilityCheckCounter_;

    void initSecondaryPathModel();
};

#endif // DSP_FXLMS_H
