package com.example.audio

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.os.Build

data class AudioDeviceInfoWrapper(
    val id: Int,
    val productName: String,
    val type: Int,
    val typeName: String,
    val isSource: Boolean,
    val isSink: Boolean,
    val sampleRates: List<Int>,
    val channelCounts: List<Int>,
    val encodings: List<String>,
    val address: String,
    val isSelected: Boolean = false,
    val isCurrentRoute: Boolean = false
) {
    val displayName: String
        get() = if (productName.isNotBlank() && productName != "null") {
            productName
        } else {
            typeName
        }

    val technicalSummary: String
        get() = buildString {
            append("ID: ").append(id).append(" | Type: ").append(typeName)
            if (sampleRates.isNotEmpty()) {
                append(" | SR: ").append(sampleRates.joinToString(","))
            }
            if (channelCounts.isNotEmpty()) {
                append(" | Ch: ").append(channelCounts.joinToString(","))
            }
        }

    companion object {
        fun fromAudioDeviceInfo(
            info: AudioDeviceInfo,
            isSelected: Boolean = false,
            isCurrentRoute: Boolean = false
        ): AudioDeviceInfoWrapper {
            val sampleRates = info.sampleRates.toList()
            val channelCounts = info.channelCounts.toList()
            val encodings = info.encodings.map { getEncodingName(it) }

            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    info.address ?: ""
                } catch (_: Exception) {
                    ""
                }
            } else {
                ""
            }

            val productName = try {
                info.productName?.toString() ?: ""
            } catch (_: Exception) {
                ""
            }

            return AudioDeviceInfoWrapper(
                id = info.id,
                productName = productName,
                type = info.type,
                typeName = getDeviceTypeName(info.type),
                isSource = info.isSource,
                isSink = info.isSink,
                sampleRates = sampleRates,
                channelCounts = channelCounts,
                encodings = encodings,
                address = address,
                isSelected = isSelected,
                isCurrentRoute = isCurrentRoute
            )
        }

        fun getDeviceTypeName(type: Int): String {
            return when (type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in Earpiece"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset (Mic+Audio)"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
                AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line Analog"
                AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line Digital"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO Headset"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP Output"
                AudioDeviceInfo.TYPE_HDMI -> "HDMI Audio"
                AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Device"
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Accessory"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
                AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony Audio"
                AudioDeviceInfo.TYPE_AUX_LINE -> "AUX Line"
                AudioDeviceInfo.TYPE_IP -> "IP Audio"
                AudioDeviceInfo.TYPE_BUS -> "Bus Audio"
                AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing Aid"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "Built-in Speaker (Safe)"
                AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "Remote Submix"
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE Headset"
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE Speaker"
                AudioDeviceInfo.TYPE_BLE_BROADCAST -> "Bluetooth LE Broadcast"
                AudioDeviceInfo.TYPE_DOCK -> "Audio Dock"
                AudioDeviceInfo.TYPE_DOCK_ANALOG -> "Analog Audio Dock"
                AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI eARC"
                else -> "Audio Device (Type $type)"
            }
        }

        private fun getEncodingName(encoding: Int): String {
            return when (encoding) {
                AudioFormat.ENCODING_PCM_16BIT -> "PCM 16-bit"
                AudioFormat.ENCODING_PCM_8BIT -> "PCM 8-bit"
                AudioFormat.ENCODING_PCM_FLOAT -> "PCM Float"
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM 24-bit"
                AudioFormat.ENCODING_PCM_32BIT -> "PCM 32-bit"
                AudioFormat.ENCODING_DEFAULT -> "Default"
                else -> "Encoding $encoding"
            }
        }
    }
}
