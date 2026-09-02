package com.example.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log

class AudioOutputEngine {

    private var audioTrack: AudioTrack? = null
    var actualSampleRate: Int = 48000
        private set
    var isPlaying: Boolean = false
        private set

    private var isFloatEncodingSupported: Boolean = false
    private var shortBuffer: ShortArray? = null

    fun start(
        targetSampleRate: Int,
        framesPerBuffer: Int,
        preferredDevice: AudioDeviceInfo? = null
    ): Boolean {
        stop()

        this.actualSampleRate = targetSampleRate

        val sampleRatesToTry = listOf(targetSampleRate, 48000, 44100)

        for (sr in sampleRatesToTry) {
            if (tryInitFloat(sr, framesPerBuffer, preferredDevice)) {
                this.actualSampleRate = sr
                this.isFloatEncodingSupported = true
                break
            }
            if (tryInitPcm16(sr, framesPerBuffer, preferredDevice)) {
                this.actualSampleRate = sr
                this.isFloatEncodingSupported = false
                this.shortBuffer = ShortArray(framesPerBuffer)
                break
            }
        }

        val track = audioTrack ?: return false

        if (preferredDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                track.setPreferredDevice(preferredDevice)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set preferred output device: ${e.message}")
            }
        }

        return try {
            track.play()
            isPlaying = (track.playState == AudioTrack.PLAYSTATE_PLAYING)
            isPlaying
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack play: ${e.message}")
            stop()
            false
        }
    }

    private fun tryInitFloat(sampleRate: Int, framesPerBuffer: Int, device: AudioDeviceInfo?): Boolean {
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            if (minBufSize <= 0) return false

            val bufSizeBytes = maxOf(minBufSize, framesPerBuffer * 4 * 4)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
                    }
                }
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val builder = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufSizeBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            val track = builder.build()

            if (track.state == AudioTrack.STATE_INITIALIZED) {
                this.audioTrack = track
                return true
            } else {
                track.release()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Float AudioTrack init failed: ${e.message}")
        }
        return false
    }

    private fun tryInitPcm16(sampleRate: Int, framesPerBuffer: Int, device: AudioDeviceInfo?): Boolean {
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufSize <= 0) return false

            val bufSizeBytes = maxOf(minBufSize, framesPerBuffer * 2 * 4)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val builder = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufSizeBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            val track = builder.build()

            if (track.state == AudioTrack.STATE_INITIALIZED) {
                this.audioTrack = track
                return true
            } else {
                track.release()
            }
        } catch (e: Exception) {
            Log.d(TAG, "PCM16 AudioTrack init failed: ${e.message}")
        }
        return false
    }

    fun write(source: FloatArray, count: Int): Int {
        val track = audioTrack ?: return 0
        if (!isPlaying || track.playState != AudioTrack.PLAYSTATE_PLAYING) return 0

        return if (isFloatEncodingSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            track.write(source, 0, count, AudioTrack.WRITE_NON_BLOCKING)
        } else {
            val sBuf = shortBuffer ?: ShortArray(count).also { shortBuffer = it }
            for (i in 0 until count) {
                val clamped = source[i].coerceIn(-1.0f, 1.0f)
                sBuf[i] = (clamped * 32767.0f).toInt().toShort()
            }
            track.write(sBuf, 0, count, AudioTrack.WRITE_NON_BLOCKING)
        }
    }

    fun getUnderrunCount(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                audioTrack?.underrunCount ?: 0
            } catch (_: Exception) {
                0
            }
        } else {
            0
        }
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioTrack: ${e.message}")
        } finally {
            audioTrack = null
        }
    }

    fun getStateSummary(): String {
        val track = audioTrack ?: return "Uninitialized (null)"
        val stateStr = when (track.state) {
            AudioTrack.STATE_INITIALIZED -> "INITIALIZED"
            AudioTrack.STATE_UNINITIALIZED -> "UNINITIALIZED"
            else -> "UNKNOWN"
        }
        val playStateStr = when (track.playState) {
            AudioTrack.PLAYSTATE_PLAYING -> "PLAYING"
            AudioTrack.PLAYSTATE_STOPPED -> "STOPPED"
            AudioTrack.PLAYSTATE_PAUSED -> "PAUSED"
            else -> "UNKNOWN"
        }
        return "$stateStr | $playStateStr | ${track.sampleRate} Hz | ${if (isFloatEncodingSupported) "FLOAT" else "PCM16"}"
    }

    companion object {
        private const val TAG = "AudioOutputEngine"
    }
}
