package com.example.audio.oboe

import android.util.Log

/**
 * Direct JNI bindings for the C++ Oboe Audio Engine.
 */
object OboeAudioEngineNative {

    private const val TAG = "OboeAudioEngineNative"
    private var isLibraryLoaded = false

    init {
        try {
            System.loadLibrary("oboe_anc_engine")
            isLibraryLoaded = true
            Log.i(TAG, "Native Oboe audio engine library loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native oboe_anc_engine could not be loaded: ${e.message}")
            isLibraryLoaded = false
        } catch (e: Exception) {
            Log.w(TAG, "Exception loading native oboe_anc_engine: ${e.message}")
            isLibraryLoaded = false
        }
    }

    fun isNativeAvailable(): Boolean = isLibraryLoaded

    external fun nativeCreateEngine(): Long
    external fun nativeDestroyEngine(enginePtr: Long)
    external fun nativeStart(
        enginePtr: Long,
        sampleRate: Int,
        bufferSizeFrames: Int,
        inputDeviceId: Int,
        outputDeviceId: Int,
        exclusiveMode: Boolean
    ): Boolean

    external fun nativeStop(enginePtr: Long)
    external fun nativeIsRunning(enginePtr: Long): Boolean
    external fun nativeSetAncMode(enginePtr: Long, modeOrdinal: Int)
    external fun nativeSetAncStrength(enginePtr: Long, strength: Float)
    external fun nativeSetAudioDelayMs(enginePtr: Long, delayMs: Float)
    external fun nativeSetFilterTaps(enginePtr: Long, taps: Int)
    external fun nativeSetStepSize(enginePtr: Long, stepSize: Float)
    external fun nativeSetLeakFactor(enginePtr: Long, leakFactor: Float)
    external fun nativeResetFilter(enginePtr: Long)
    external fun nativeSetAudioSourceVolume(enginePtr: Long, volume: Float)
    external fun nativeSetPlayAudioSource(enginePtr: Long, play: Boolean)
    external fun nativeSetEqBandGain(enginePtr: Long, bandIndex: Int, gainDb: Float)
    external fun nativeSetEqBandGains(enginePtr: Long, gains: FloatArray)
    external fun nativeSetEqEnabled(enginePtr: Long, enabled: Boolean)
    external fun nativeResetEqualizer(enginePtr: Long)

    external fun nativeGetMetrics(enginePtr: Long, outMetricsArray: FloatArray): Boolean
    external fun nativeGetVisualizerSnapshot(
        enginePtr: Long,
        micOut: FloatArray,
        antiNoiseOut: FloatArray,
        mixOut: FloatArray
    ): Boolean

    external fun nativeGetFrequencySpectrum(enginePtr: Long, spectrumOut: FloatArray): Boolean
    external fun nativeGetBackendName(enginePtr: Long): String
    external fun nativeIsAAudioSupported(): Boolean
}
