package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class AudioInputEngine(private val context: Context? = null) {

    private var audioRecord: AudioRecord? = null
    var actualSampleRate: Int = 48000
        private set
    var actualBufferSizeFrames: Int = 256
        private set
    var isRecording: Boolean = false
        private set

    private var isFloatEncodingSupported: Boolean = false
    private var shortBuffer: ShortArray? = null

    @SuppressLint("MissingPermission")
    fun start(
        targetSampleRate: Int,
        framesPerBuffer: Int,
        preferredDevice: AudioDeviceInfo? = null
    ): Boolean {
        stop()

        // Guard against missing RECORD_AUDIO runtime permission
        if (context != null && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Cannot start AudioInputEngine: RECORD_AUDIO permission not granted.")
            return false
        }

        this.actualSampleRate = targetSampleRate
        this.actualBufferSizeFrames = framesPerBuffer

        val sampleRatesToTry = listOf(targetSampleRate, 48000, 44100, 16000, 8000).distinct()
        val audioSourcesToTry = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.UNPROCESSED
        )

        var initialized = false

        for (sr in sampleRatesToTry) {
            if (initialized) break
            for (source in audioSourcesToTry) {
                // 1. Try Float encoding
                if (tryInitFloat(sr, framesPerBuffer, source, preferredDevice)) {
                    this.actualSampleRate = sr
                    this.isFloatEncodingSupported = true
                    initialized = true
                    break
                }
                // 2. Fallback to PCM 16-bit
                if (tryInitPcm16(sr, framesPerBuffer, source, preferredDevice)) {
                    this.actualSampleRate = sr
                    this.isFloatEncodingSupported = false
                    this.shortBuffer = ShortArray(framesPerBuffer)
                    initialized = true
                    break
                }
            }
        }

        val record = audioRecord ?: run {
            Log.e(TAG, "AudioRecord could not be initialized with any configuration.")
            return false
        }

        if (preferredDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                record.setPreferredDevice(preferredDevice)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set preferred input device: ${e.message}")
            }
        }

        return try {
            record.startRecording()
            isRecording = (record.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            isRecording
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord: ${e.message}")
            stop()
            false
        }
    }

    private fun tryInitFloat(
        sampleRate: Int,
        framesPerBuffer: Int,
        audioSource: Int,
        device: AudioDeviceInfo?
    ): Boolean {
        try {
            val minBufSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            if (minBufSize <= 0) return false

            val bufSizeBytes = maxOf(minBufSize, framesPerBuffer * 4 * 4)

            // Try Builder (API 23+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val format = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()

                    val record = AudioRecord.Builder()
                        .setAudioSource(audioSource)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(bufSizeBytes)
                        .build()

                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        this.audioRecord = record
                        return true
                    } else {
                        record.release()
                    }
                } catch (e: Throwable) {
                    // Builder failed, continue to fallback
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Float AudioRecord init failed for $sampleRate Hz, source $audioSource: ${e.message}")
        }
        return false
    }

    private fun tryInitPcm16(
        sampleRate: Int,
        framesPerBuffer: Int,
        audioSource: Int,
        device: AudioDeviceInfo?
    ): Boolean {
        try {
            val minBufSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufSize <= 0) return false

            val bufSizeBytes = maxOf(minBufSize, framesPerBuffer * 2 * 4)

            // Try Builder (API 23+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val format = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()

                    val record = AudioRecord.Builder()
                        .setAudioSource(audioSource)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(bufSizeBytes)
                        .build()

                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        this.audioRecord = record
                        return true
                    } else {
                        record.release()
                    }
                } catch (e: Throwable) {
                    // Continue to legacy constructor
                }
            }

            // Legacy direct constructor fallback
            try {
                @Suppress("DEPRECATION")
                val legacyRecord = AudioRecord(
                    audioSource,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSizeBytes
                )
                if (legacyRecord.state == AudioRecord.STATE_INITIALIZED) {
                    this.audioRecord = legacyRecord
                    return true
                } else {
                    legacyRecord.release()
                }
            } catch (e: Throwable) {
                // Handled
            }
        } catch (e: Throwable) {
            Log.d(TAG, "PCM16 AudioRecord init failed for $sampleRate Hz, source $audioSource: ${e.message}")
        }
        return false
    }

    fun read(destination: FloatArray, count: Int): Int {
        val record = audioRecord ?: return 0
        if (!isRecording || record.recordingState != AudioRecord.RECORDSTATE_RECORDING) return 0

        return try {
            if (isFloatEncodingSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                record.read(destination, 0, count, AudioRecord.READ_NON_BLOCKING)
            } else {
                val sBuf = shortBuffer ?: ShortArray(count).also { shortBuffer = it }
                val readCount = record.read(sBuf, 0, count)
                if (readCount > 0) {
                    val invShortMax = 1.0f / 32768.0f
                    for (i in 0 until readCount) {
                        destination[i] = sBuf[i] * invShortMax
                    }
                }
                readCount
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from AudioRecord: ${e.message}")
            0
        }
    }

    fun stop() {
        isRecording = false
        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }
    }

    fun getStateSummary(): String {
        val record = audioRecord ?: return "Uninitialized (null)"
        val stateStr = when (record.state) {
            AudioRecord.STATE_INITIALIZED -> "INITIALIZED"
            AudioRecord.STATE_UNINITIALIZED -> "UNINITIALIZED"
            else -> "UNKNOWN"
        }
        val recStateStr = when (record.recordingState) {
            AudioRecord.RECORDSTATE_RECORDING -> "RECORDING"
            AudioRecord.RECORDSTATE_STOPPED -> "STOPPED"
            else -> "UNKNOWN"
        }
        return "$stateStr | $recStateStr | ${record.sampleRate} Hz | ${if (isFloatEncodingSupported) "FLOAT" else "PCM16"}"
    }

    companion object {
        private const val TAG = "AudioInputEngine"
    }
}
