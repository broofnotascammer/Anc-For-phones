package com.example.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.data.DeviceSelectionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioDeviceManager(val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _availableInputs = MutableStateFlow<List<AudioDeviceInfoWrapper>>(emptyList())
    val availableInputs: StateFlow<List<AudioDeviceInfoWrapper>> = _availableInputs.asStateFlow()

    private val _availableOutputs = MutableStateFlow<List<AudioDeviceInfoWrapper>>(emptyList())
    val availableOutputs: StateFlow<List<AudioDeviceInfoWrapper>> = _availableOutputs.asStateFlow()

    private val _selectedInput = MutableStateFlow<AudioDeviceInfoWrapper?>(null)
    val selectedInput: StateFlow<AudioDeviceInfoWrapper?> = _selectedInput.asStateFlow()

    private val _selectedOutput = MutableStateFlow<AudioDeviceInfoWrapper?>(null)
    val selectedOutput: StateFlow<AudioDeviceInfoWrapper?> = _selectedOutput.asStateFlow()

    private val _inputSelectionMode = MutableStateFlow(DeviceSelectionMode.AUTOMATIC)
    val inputSelectionMode: StateFlow<DeviceSelectionMode> = _inputSelectionMode.asStateFlow()

    private val _outputSelectionMode = MutableStateFlow(DeviceSelectionMode.AUTOMATIC)
    val outputSelectionMode: StateFlow<DeviceSelectionMode> = _outputSelectionMode.asStateFlow()

    private var onDeviceChangeListener: (() -> Unit)? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices added: ${addedDevices?.size ?: 0}")
            refreshDevices()
            onDeviceChangeListener?.invoke()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices removed: ${removedDevices?.size ?: 0}")
            refreshDevices()
            onDeviceChangeListener?.invoke()
        }
    }

    init {
        registerDeviceCallback()
        refreshDevices()
    }

    fun setOnDeviceChangeListener(listener: () -> Unit) {
        this.onDeviceChangeListener = listener
    }

    private fun registerDeviceCallback() {
        try {
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register AudioDeviceCallback: ${e.message}")
        }
    }

    fun unregister() {
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister AudioDeviceCallback: ${e.message}")
        }
    }

    fun refreshDevices() {
        try {
            val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

            val wrappedInputs = inputDevices.map { dev ->
                AudioDeviceInfoWrapper.fromAudioDeviceInfo(
                    info = dev,
                    isSelected = (_selectedInput.value?.id == dev.id),
                    isCurrentRoute = isCurrentCommunicationDevice(dev)
                )
            }

            val wrappedOutputs = outputDevices.map { dev ->
                AudioDeviceInfoWrapper.fromAudioDeviceInfo(
                    info = dev,
                    isSelected = (_selectedOutput.value?.id == dev.id),
                    isCurrentRoute = isCurrentCommunicationDevice(dev)
                )
            }

            _availableInputs.value = wrappedInputs
            _availableOutputs.value = wrappedOutputs

            // Re-evaluate automatic device selections
            if (_inputSelectionMode.value == DeviceSelectionMode.AUTOMATIC || _selectedInput.value == null || wrappedInputs.none { it.id == _selectedInput.value?.id }) {
                _selectedInput.value = pickBestInput(wrappedInputs)
            }

            if (_outputSelectionMode.value == DeviceSelectionMode.AUTOMATIC || _selectedOutput.value == null || wrappedOutputs.none { it.id == _selectedOutput.value?.id }) {
                _selectedOutput.value = pickBestOutput(wrappedOutputs)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing devices: ${e.message}")
        }
    }

    fun selectManualInput(device: AudioDeviceInfoWrapper?) {
        if (device == null) {
            _inputSelectionMode.value = DeviceSelectionMode.AUTOMATIC
            _selectedInput.value = pickBestInput(_availableInputs.value)
        } else {
            _inputSelectionMode.value = DeviceSelectionMode.MANUAL
            _selectedInput.value = device
        }
        onDeviceChangeListener?.invoke()
    }

    fun selectManualOutput(device: AudioDeviceInfoWrapper?) {
        if (device == null) {
            _outputSelectionMode.value = DeviceSelectionMode.AUTOMATIC
            _selectedOutput.value = pickBestOutput(_availableOutputs.value)
        } else {
            _outputSelectionMode.value = DeviceSelectionMode.MANUAL
            _selectedOutput.value = device
        }
        onDeviceChangeListener?.invoke()
    }

    fun getAudioDeviceInfo(id: Int): AudioDeviceInfo? {
        val allDevices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
        return allDevices.firstOrNull { it.id == id }
    }

    /**
     * Priority for Output:
     * Wired Headset/Headphones > USB Headset/Device > Bluetooth LE/A2DP/SCO > Built-in Speaker
     */
    fun pickBestOutput(devices: List<AudioDeviceInfoWrapper>): AudioDeviceInfoWrapper? {
        if (devices.isEmpty()) return null

        val wired = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
            it.type == AudioDeviceInfo.TYPE_LINE_DIGITAL
        }
        if (wired != null) return wired

        val usb = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        if (usb != null) return usb

        val bluetooth = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
        if (bluetooth != null) return bluetooth

        val speaker = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE
        }
        if (speaker != null) return speaker

        return devices.firstOrNull()
    }

    /**
     * Priority for Input:
     * Wired Headset Mic > USB Microphone/Headset > Bluetooth Headset Mic > Built-in Mic
     */
    fun pickBestInput(devices: List<AudioDeviceInfoWrapper>): AudioDeviceInfoWrapper? {
        if (devices.isEmpty()) return null

        val wired = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }
        if (wired != null) return wired

        val usb = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        if (usb != null) return usb

        val bluetooth = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        if (bluetooth != null) return bluetooth

        val builtin = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
        }
        if (builtin != null) return builtin

        return devices.firstOrNull()
    }

    private fun isCurrentCommunicationDevice(device: AudioDeviceInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.communicationDevice?.id == device.id
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
    }

    fun getPropertyOutputSampleRate(): Int {
        val srStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        return srStr?.toIntOrNull() ?: 48000
    }

    fun getPropertyFramesPerBuffer(): Int {
        val fpbStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        return fpbStr?.toIntOrNull() ?: 256
    }

    companion object {
        private const val TAG = "AudioDeviceManager"
    }
}
