#include <jni.h>
#include <string>
#include <android/log.h>
#include "oboe_engine.h"

#define JNI_METHOD(return_type, method_name) \
    JNIEXPORT return_type JNICALL Java_com_example_audio_oboe_OboeAudioEngineNative_##method_name

extern "C" {

JNI_METHOD(jlong, nativeCreateEngine)(JNIEnv* env, jobject thiz) {
    auto* engine = new (std::nothrow) OboeAudioEngine();
    return reinterpret_cast<jlong>(engine);
}

JNI_METHOD(void, nativeDestroyEngine)(JNIEnv* env, jobject thiz, jlong enginePtr) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (engine) {
        delete engine;
    }
}

JNI_METHOD(jboolean, nativeStart)(
    JNIEnv* env,
    jobject thiz,
    jlong enginePtr,
    jint sampleRate,
    jint bufferSizeFrames,
    jint inputDeviceId,
    jint outputDeviceId,
    jboolean exclusiveMode
) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (!engine) return JNI_FALSE;
    bool success = engine->start(sampleRate, bufferSizeFrames, inputDeviceId, outputDeviceId, exclusiveMode);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNI_METHOD(void, nativeStop)(JNIEnv* env, jobject thiz, jlong enginePtr) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (engine) {
        engine->stop();
    }
}

JNI_METHOD(jboolean, nativeIsRunning)(JNIEnv* env, jobject thiz, jlong enginePtr) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (!engine) return JNI_FALSE;
    return engine->isRunning() ? JNI_TRUE : JNI_FALSE;
}

JNI_METHOD(void, nativeSetAncMode)(JNIEnv* env, jobject thiz, jlong enginePtr, jint modeOrdinal) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (engine) {
        engine->setAncMode(static_cast<NativeAncMode>(modeOrdinal));
    }
}

JNI_METHOD(void, nativeSetAncStrength)(JNIEnv* env, jobject thiz, jlong enginePtr, jfloat strength) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (engine) {
        engine->setAncStrength(strength);
    }
}

JNI_METHOD(void, nativeSetAudioDelayMs)(JNIEnv* env, jobject thiz, jlong enginePtr, jfloat delayMs) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (engine) {
        engine->setAudioDelayMs(delayMs);
    }
}

JNI_METHOD(void, nativeSetFilterTaps)(JNIEnv* env, jobject thiz, jlong enginePtr, jint taps) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (engine) {
        engine->setFilterTaps(taps);
    }
}

JNI_METHOD(void, nativeSetStepSize)(JNIEnv* env, jobject thiz, jlong enginePtr, jfloat stepSize) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (engine) {
        engine->setStepSize(stepSize);
    }
}

JNI_METHOD(void, nativeSetLeakFactor)(JNIEnv* env, jobject thiz, jlong enginePtr, jfloat leakFactor) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (engine) {
        engine->setLeakFactor(leakFactor);
    }
}

JNI_METHOD(void, nativeResetFilter)(JNIEnv* env, jobject thiz, jlong enginePtr) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (engine) {
        engine->resetFilter();
    }
}

JNI_METHOD(void, nativeSetAudioSourceVolume)(JNIEnv* env, jobject thiz, jlong enginePtr, jfloat volume) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (engine) {
        engine->setAudioSourceVolume(volume);
    }
}

JNI_METHOD(void, nativeSetPlayAudioSource)(JNIEnv* env, jobject thiz, jlong enginePtr, jboolean play) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (engine) {
        engine->setPlayAudioSource(play);
    }
}

JNI_METHOD(void, nativeSetEqBandGain)(JNIEnv* env, jobject thiz, jlong enginePtr, jint bandIndex, jfloat gainDb) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (engine) {
        engine->setEqBandGain(bandIndex, gainDb);
    }
}

JNI_METHOD(void, nativeSetEqBandGains)(JNIEnv* env, jobject thiz, jlong enginePtr, jfloatArray gains) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (engine && gains) {
        jsize len = env->GetArrayLength(gains);
        jfloat* buf = env->GetFloatArrayElements(gains, nullptr);
        if (buf) {
            engine->setEqBandGains(buf, len);
            env->ReleaseFloatArrayElements(gains, buf, JNI_ABORT);
        }
    }
}

JNI_METHOD(void, nativeSetEqEnabled)(JNIEnv* env, jobject thiz, jlong enginePtr, jboolean enabled) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (engine) {
        engine->setEqEnabled(enabled == JNI_TRUE);
    }
}

JNI_METHOD(void, nativeResetEqualizer)(JNIEnv* env, jobject thiz, jlong enginePtr) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (engine) {
        engine->resetEqualizer();
    }
}

JNI_METHOD(jboolean, nativeGetMetrics)(
    JNIEnv* env,
    jobject thiz,
    jlong enginePtr,
    jfloatArray outMetricsArray
) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (!engine || !outMetricsArray) return JNI_FALSE;

    EngineMetricsSnapshot snapshot{};
    engine->getMetrics(snapshot);

    jsize len = env->GetArrayLength(outMetricsArray);
    if (len < 13) return JNI_FALSE;

    jfloat metricsBuf[13];
    metricsBuf[0] = snapshot.isRunning ? 1.0f : 0.0f;
    metricsBuf[1] = static_cast<jfloat>(snapshot.sampleRate);
    metricsBuf[2] = static_cast<jfloat>(snapshot.inputBufferSizeFrames);
    metricsBuf[3] = static_cast<jfloat>(snapshot.outputBufferSizeFrames);
    metricsBuf[4] = static_cast<jfloat>(snapshot.xRuns);
    metricsBuf[5] = snapshot.estimatedLatencyMs;
    metricsBuf[6] = snapshot.dspCpuLoadPercent;
    metricsBuf[7] = snapshot.inputPeakDb;
    metricsBuf[8] = snapshot.antiNoisePeakDb;
    metricsBuf[9] = snapshot.outputPeakDb;
    metricsBuf[10] = snapshot.filterDiverged ? 1.0f : 0.0f;
    metricsBuf[11] = static_cast<jfloat>(snapshot.activeMode);
    metricsBuf[12] = static_cast<jfloat>(snapshot.backendType);

    env->SetFloatArrayRegion(outMetricsArray, 0, 13, metricsBuf);
    return JNI_TRUE;
}

JNI_METHOD(jboolean, nativeGetVisualizerSnapshot)(
    JNIEnv* env,
    jobject thiz,
    jlong enginePtr,
    jfloatArray micOut,
    jfloatArray antiNoiseOut,
    jfloatArray mixOut
) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (!engine || !micOut || !antiNoiseOut || !mixOut) return JNI_FALSE;

    jsize count = env->GetArrayLength(micOut);
    std::vector<float> tempMic(count);
    std::vector<float> tempAnti(count);
    std::vector<float> tempMix(count);

    engine->getVisualizerSnapshot(tempMic.data(), tempAnti.data(), tempMix.data(), count);

    env->SetFloatArrayRegion(micOut, 0, count, tempMic.data());
    env->SetFloatArrayRegion(antiNoiseOut, 0, count, tempAnti.data());
    env->SetFloatArrayRegion(mixOut, 0, count, tempMix.data());
    return JNI_TRUE;
}

JNI_METHOD(jboolean, nativeGetFrequencySpectrum)(
    JNIEnv* env,
    jobject thiz,
    jlong enginePtr,
    jfloatArray spectrumOut
) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    if (!engine || !spectrumOut) return JNI_FALSE;

    jsize numBins = env->GetArrayLength(spectrumOut);
    std::vector<float> tempBins(numBins);
    engine->getSpectrum(tempBins.data(), numBins);

    env->SetFloatArrayRegion(spectrumOut, 0, numBins, tempBins.data());
    return JNI_TRUE;
}

JNI_METHOD(jstring, nativeGetBackendName)(JNIEnv* env, jobject thiz, jlong enginePtr) {
    auto* engine = reinterpret_cast<OboeAudioEngine*>(enginePtr);
    const char* name = engine ? engine->getBackendName() : "Unknown";
    return env->NewStringUTF(name);
}

JNI_METHOD(jboolean, nativeIsAAudioSupported)(JNIEnv* env, jclass clazz) {
    return OboeAudioEngine::isAAudioSupported() ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
