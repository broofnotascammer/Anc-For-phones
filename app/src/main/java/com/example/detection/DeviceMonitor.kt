package com.example.detection

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import com.example.audio.AudioDeviceManager

class DeviceMonitor(
    private val context: Context,
    private val deviceManager: AudioDeviceManager,
    private val onDeviceChange: () -> Unit
) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d(TAG, "Received audio event: $action")
            when (action) {
                Intent.ACTION_HEADSET_PLUG,
                AudioManager.ACTION_AUDIO_BECOMING_NOISY,
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    deviceManager.refreshDevices()
                    onDeviceChange()
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        try {
            context.registerReceiver(receiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register DeviceMonitor receiver: ${e.message}")
        }
    }

    fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister DeviceMonitor receiver: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "DeviceMonitor"
    }
}
