package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioDeviceInfoWrapper
import com.example.audio.AudioDeviceManager
import com.example.audio.oboe.HighSampleRate
import com.example.audio.oboe.OboeAudioEngineWrapper
import com.example.audio.oboe.OboeSharingMode
import com.example.audio.oboe.OboeTelemetry
import com.example.data.AncFeasibilityReport
import com.example.data.AncMode
import com.example.data.DeviceSelectionMode
import com.example.data.DspMetrics
import com.example.data.DspParameters
import com.example.data.HardwareDiagnosticReport
import com.example.data.LatencyCalibrationResult
import com.example.data.TestSignalType
import com.example.data.VisualizerSnapshot
import com.example.detection.CapabilityChecker
import com.example.detection.DeviceMonitor
import com.example.dsp.DspEngine
import com.example.service.AncForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AncViewModel(application: Application) : AndroidViewModel(application) {

    val deviceManager: AudioDeviceManager = AncForegroundService.deviceManagerInstance ?: AudioDeviceManager(application).also {
        // Shared instance
    }

    val dspEngine: DspEngine = AncForegroundService.dspEngineInstance ?: DspEngine(deviceManager).also {
        // Shared instance
    }

    val oboeEngine = OboeAudioEngineWrapper(application, viewModelScope)

    private val capabilityChecker = CapabilityChecker(application, deviceManager)
    private var deviceMonitor: DeviceMonitor? = null

    private val _isOboeActive = MutableStateFlow(true)
    val isOboeActive: StateFlow<Boolean> = _isOboeActive.asStateFlow()

    private val _selectedHighSampleRate = MutableStateFlow(HighSampleRate.STANDARD_48K)
    val selectedHighSampleRate: StateFlow<HighSampleRate> = _selectedHighSampleRate.asStateFlow()

    private val _selectedSharingMode = MutableStateFlow(OboeSharingMode.EXCLUSIVE)
    val selectedSharingMode: StateFlow<OboeSharingMode> = _selectedSharingMode.asStateFlow()

    val oboeTelemetry: StateFlow<OboeTelemetry> = oboeEngine.telemetry

    val metrics: StateFlow<DspMetrics> = combine(
        _isOboeActive,
        dspEngine.metrics,
        oboeEngine.telemetry
    ) { isOboe, jvmMetrics, oboeTelem ->
        if (isOboe) {
            DspMetrics(
                isRunning = oboeTelem.isRunning,
                mode = oboeTelem.activeMode,
                sampleRate = oboeTelem.sampleRate,
                bufferSizeFrames = oboeTelem.inputBufferSizeFrames,
                inputLatencyMs = oboeTelem.estimatedLatencyMs * 0.4f,
                outputLatencyMs = oboeTelem.estimatedLatencyMs * 0.6f,
                processingLatencyMs = (oboeTelem.dspCpuLoadPercent * 0.05f),
                totalEstimatedLatencyMs = oboeTelem.estimatedLatencyMs,
                dspLoadPercent = oboeTelem.dspCpuLoadPercent,
                inputPeakLevelDb = oboeTelem.inputPeakDb,
                antiNoisePeakLevelDb = oboeTelem.antiNoisePeakDb,
                outputPeakLevelDb = oboeTelem.outputPeakDb,
                filterDiverged = oboeTelem.filterDiverged,
                bufferUnderruns = oboeTelem.xRuns.toLong(),
                lastError = if (oboeTelem.filterDiverged) "Adaptive Filter Divergence Detected" else null
            )
        } else {
            jvmMetrics
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        dspEngine.metrics.value
    )

    val visualizerSnapshot: StateFlow<VisualizerSnapshot> = combine(
        _isOboeActive,
        dspEngine.visualizerSnapshot,
        oboeEngine.visualizerSnapshot
    ) { isOboe, jvmVis, oboeVis ->
        if (isOboe) oboeVis else jvmVis
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        dspEngine.visualizerSnapshot.value
    )

    val availableInputs: StateFlow<List<AudioDeviceInfoWrapper>> = deviceManager.availableInputs
    val availableOutputs: StateFlow<List<AudioDeviceInfoWrapper>> = deviceManager.availableOutputs
    val selectedInput: StateFlow<AudioDeviceInfoWrapper?> = deviceManager.selectedInput
    val selectedOutput: StateFlow<AudioDeviceInfoWrapper?> = deviceManager.selectedOutput
    val inputSelectionMode: StateFlow<DeviceSelectionMode> = deviceManager.inputSelectionMode
    val outputSelectionMode: StateFlow<DeviceSelectionMode> = deviceManager.outputSelectionMode

    private val _dspParameters = MutableStateFlow(dspEngine.currentParams)
    val dspParameters: StateFlow<DspParameters> = _dspParameters.asStateFlow()

    private val _feasibilityReport = MutableStateFlow<AncFeasibilityReport?>(null)
    val feasibilityReport: StateFlow<AncFeasibilityReport?> = _feasibilityReport.asStateFlow()

    private val _calibrationResult = MutableStateFlow<LatencyCalibrationResult?>(null)
    val calibrationResult: StateFlow<LatencyCalibrationResult?> = _calibrationResult.asStateFlow()

    private val _diagnosticReport = MutableStateFlow<HardwareDiagnosticReport?>(null)
    val diagnosticReport: StateFlow<HardwareDiagnosticReport?> = _diagnosticReport.asStateFlow()

    private val _isCheckingCapability = MutableStateFlow(false)
    val isCheckingCapability: StateFlow<Boolean> = _isCheckingCapability.asStateFlow()

    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    init {
        deviceMonitor = DeviceMonitor(application, deviceManager) {
            if (metrics.value.isRunning) {
                dspEngine.stop()
                dspEngine.start()
            }
        }.also { it.start() }

        runCapabilityCheck()
    }

    fun setEngineType(useOboe: Boolean) {
        if (_isOboeActive.value == useOboe) return
        val wasRunning = metrics.value.isRunning
        if (wasRunning) {
            stopEngine()
        }
        _isOboeActive.value = useOboe
        if (wasRunning) {
            startEngine()
        }
    }

    fun setHighSampleRate(rate: HighSampleRate) {
        _selectedHighSampleRate.value = rate
        if (metrics.value.isRunning && _isOboeActive.value) {
            stopEngine()
            startEngine()
        }
    }

    fun setSharingMode(mode: OboeSharingMode) {
        _selectedSharingMode.value = mode
        if (metrics.value.isRunning && _isOboeActive.value) {
            stopEngine()
            startEngine()
        }
    }

    fun startEngine() {
        AncForegroundService.startService(getApplication())
        if (_isOboeActive.value) {
            val params = _dspParameters.value
            oboeEngine.setAncMode(params.mode)
            oboeEngine.setAncStrength(params.ancStrength)
            oboeEngine.setAudioDelayMs(params.audioDelayMs)
            oboeEngine.setFilterTaps(params.filterTaps)
            oboeEngine.setStepSize(params.stepSizeMu)
            oboeEngine.setLeakFactor(1.0f - params.leakageGamma)
            oboeEngine.setAudioSourceVolume(params.audioSourceVolume)
            oboeEngine.setPlayAudioSource(params.playAudioSourceTrack)

            oboeEngine.start(
                sampleRate = _selectedHighSampleRate.value,
                sharingMode = _selectedSharingMode.value,
                inputDevice = selectedInput.value,
                outputDevice = selectedOutput.value
            )
        } else {
            dspEngine.start()
        }
    }

    fun stopEngine() {
        if (_isOboeActive.value) {
            oboeEngine.stop()
        } else {
            dspEngine.stop()
        }
        AncForegroundService.stopService(getApplication())
    }

    fun emergencyStop() {
        if (_isOboeActive.value) {
            oboeEngine.stop()
            oboeEngine.setAncMode(AncMode.OFF)
        } else {
            dspEngine.emergencyStop()
        }
        _dspParameters.value = _dspParameters.value.copy(mode = AncMode.OFF)
    }

    fun resetFilterWeights() {
        if (_isOboeActive.value) {
            oboeEngine.resetFilterWeights()
        } else {
            dspEngine.resetFilterWeights()
        }
    }

    fun setAncMode(mode: AncMode) {
        if (_isOboeActive.value) {
            oboeEngine.setAncMode(mode)
        } else {
            dspEngine.setMode(mode)
        }
        _dspParameters.value = _dspParameters.value.copy(mode = mode)
    }

    fun updateAncStrength(strength: Float) {
        val updated = _dspParameters.value.copy(ancStrength = strength)
        _dspParameters.value = updated
        if (_isOboeActive.value) {
            oboeEngine.setAncStrength(strength)
        } else {
            dspEngine.updateParameters(updated)
        }
    }

    fun updateAudioDelayMs(delayMs: Float) {
        val updated = _dspParameters.value.copy(audioDelayMs = delayMs)
        _dspParameters.value = updated
        if (_isOboeActive.value) {
            oboeEngine.setAudioDelayMs(delayMs)
        } else {
            dspEngine.updateParameters(updated)
        }
    }

    fun updateFilterTaps(taps: Int) {
        val updated = _dspParameters.value.copy(filterTaps = taps)
        _dspParameters.value = updated
        if (_isOboeActive.value) {
            oboeEngine.setFilterTaps(taps)
        } else {
            dspEngine.updateParameters(updated)
        }
    }

    fun updateStepSizeMu(mu: Float) {
        val updated = _dspParameters.value.copy(stepSizeMu = mu)
        _dspParameters.value = updated
        if (_isOboeActive.value) {
            oboeEngine.setStepSize(mu)
        } else {
            dspEngine.updateParameters(updated)
        }
    }

    fun updateLeakageGamma(gamma: Float) {
        val updated = _dspParameters.value.copy(leakageGamma = gamma)
        _dspParameters.value = updated
        if (_isOboeActive.value) {
            oboeEngine.setLeakFactor(1.0f - gamma)
        } else {
            dspEngine.updateParameters(updated)
        }
    }

    fun updateSecondaryPathDelayMs(delayMs: Float) {
        val updated = _dspParameters.value.copy(secondaryPathDelayMs = delayMs)
        _dspParameters.value = updated
        dspEngine.updateParameters(updated)
    }

    fun updateBandpassCutoffs(lowHz: Float, highHz: Float) {
        val updated = _dspParameters.value.copy(lowCutoffHz = lowHz, highCutoffHz = highHz)
        _dspParameters.value = updated
        dspEngine.updateParameters(updated)
    }

    fun updateLimiterThreshold(threshold: Float) {
        val updated = _dspParameters.value.copy(limiterThreshold = threshold)
        _dspParameters.value = updated
        dspEngine.updateParameters(updated)
    }

    fun updateTestSignal(type: TestSignalType, volume: Float) {
        val updated = _dspParameters.value.copy(testSignalType = type, testSignalVolume = volume)
        _dspParameters.value = updated
        dspEngine.updateParameters(updated)
    }

    fun toggleAudioSourceTrack(enabled: Boolean) {
        val updated = _dspParameters.value.copy(playAudioSourceTrack = enabled)
        _dspParameters.value = updated
        if (_isOboeActive.value) {
            oboeEngine.setPlayAudioSource(enabled)
        } else {
            dspEngine.updateParameters(updated)
        }
    }

    fun updateAudioSourceVolume(volume: Float) {
        val updated = _dspParameters.value.copy(audioSourceVolume = volume)
        _dspParameters.value = updated
        if (_isOboeActive.value) {
            oboeEngine.setAudioSourceVolume(volume)
        } else {
            dspEngine.updateParameters(updated)
        }
    }

    fun selectManualInput(device: AudioDeviceInfoWrapper?) {
        deviceManager.selectManualInput(device)
        if (metrics.value.isRunning) {
            stopEngine()
            startEngine()
        }
    }

    fun selectManualOutput(device: AudioDeviceInfoWrapper?) {
        deviceManager.selectManualOutput(device)
        if (metrics.value.isRunning) {
            stopEngine()
            startEngine()
        }
    }

    fun refreshDevices() {
        deviceManager.refreshDevices()
    }

    fun runCapabilityCheck() {
        viewModelScope.launch {
            _isCheckingCapability.value = true
            val report = capabilityChecker.runFullCapabilityCheck()
            _feasibilityReport.value = report
            _isCheckingCapability.value = false
        }
    }

    fun runLatencyCalibration() {
        viewModelScope.launch {
            _isCalibrating.value = true
            val result = dspEngine.performLatencyCalibration()
            _calibrationResult.value = result
            if (result.isSuccess) {
                _dspParameters.value = dspEngine.currentParams
            }
            _isCalibrating.value = false
        }
    }

    fun generateDiagnosticReport() {
        val m = metrics.value
        val metricsStr = "Running: ${m.isRunning}, Mode: ${m.mode.displayName}, SR: ${m.sampleRate} Hz, Buffer: ${m.bufferSizeFrames}, Load: ${String.format("%.1f", m.dspLoadPercent)}%, Total Latency: ${String.format("%.1f", m.totalEstimatedLatencyMs)} ms, Underruns: ${m.bufferUnderruns}"
        _diagnosticReport.value = capabilityChecker.generateDiagnosticReport(
            audioRecordState = dspEngine.getInputState(),
            audioTrackState = dspEngine.getOutputState(),
            dspState = "Taps: ${_dspParameters.value.filterTaps}, Mu: ${_dspParameters.value.stepSizeMu}, Delay: ${_dspParameters.value.audioDelayMs}ms, Diverged: ${m.filterDiverged}",
            metricsSummary = metricsStr
        )
    }

    override fun onCleared() {
        super.onCleared()
        deviceMonitor?.stop()
    }
}
