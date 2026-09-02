package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioDeviceInfoWrapper
import com.example.audio.oboe.HighSampleRate
import com.example.audio.oboe.OboeSharingMode
import com.example.data.AncMode
import com.example.data.DspMetrics
import com.example.data.DspParameters
import com.example.data.VisualizerSnapshot
import com.example.ui.components.PeakLevelMeter
import com.example.ui.components.RealTimeOscilloscope
import com.example.ui.components.RealTimeSpectrum
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.AncActiveGreen
import com.example.ui.theme.AncEmergencyRed
import com.example.ui.theme.AncWarningAmber
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.MintSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WaveAntiNoise
import com.example.ui.theme.WaveMic
import com.example.ui.theme.WaveOutput

private val ObsidianCardBg = Color(0xFF0D1322)
private val ObsidianCardBorder = Color(0xFF1E2B45)

@Composable
fun MainScreen(
    viewModel: AncViewModel,
    onNavigateToDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics by viewModel.metrics.collectAsState()
    val visualizerSnapshot by viewModel.visualizerSnapshot.collectAsState()
    val params by viewModel.dspParameters.collectAsState()
    val selectedInput by viewModel.selectedInput.collectAsState()
    val selectedOutput by viewModel.selectedOutput.collectAsState()
    val isOboeActive by viewModel.isOboeActive.collectAsState()
    val selectedHighSampleRate by viewModel.selectedHighSampleRate.collectAsState()
    val selectedSharingMode by viewModel.selectedSharingMode.collectAsState()
    val oboeTelemetry by viewModel.oboeTelemetry.collectAsState()

    var showSpectrum by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060913),
                        DarkBackground,
                        Color(0xFF04060C)
                    )
                )
            )
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 36.dp)
    ) {
        // 1. MASTER TACTILE IGNITION BUTTON
        item {
            TactileMasterEngineControl(
                isRunning = metrics.isRunning,
                activeMode = params.mode,
                pulseGlow = pulseGlow,
                onToggle = {
                    if (metrics.isRunning) viewModel.stopEngine() else viewModel.startEngine()
                }
            )
        }

        // 2. INTERACTIVE HARDWARE SIGNAL FLOW MATRIX (Input ➔ DSP Core ➔ Output)
        item {
            TactileAudioFlowMatrix(
                selectedInput = selectedInput,
                selectedOutput = selectedOutput,
                metrics = metrics,
                params = params,
                onConfigureRoutes = onNavigateToDevices
            )
        }

        // 2B. NATIVE OBOE & HIGH-SAMPLE RATE ENGINE CONFIGURATION
        item {
            TactileOboeEngineConfigCard(
                isOboeActive = isOboeActive,
                selectedSampleRate = selectedHighSampleRate,
                selectedSharingMode = selectedSharingMode,
                oboeTelemetry = oboeTelemetry,
                onToggleOboe = { viewModel.setEngineType(it) },
                onSelectSampleRate = { viewModel.setHighSampleRate(it) },
                onSelectSharingMode = { viewModel.setSharingMode(it) }
            )
        }

        // 3. DSP SAFEGUARD ALERT
        if (metrics.filterDiverged) {
            item {
                TactileSafeguardAlertCard(
                    onReset = { viewModel.resetFilterWeights() }
                )
            }
        }

        // 4. IMMERSIVE OSCILLOSCOPE & SPECTRAL CRT VISUALIZER
        item {
            TactileVisualizerSuite(
                snapshot = visualizerSnapshot,
                metrics = metrics,
                params = params,
                showSpectrum = showSpectrum,
                onToggleSpectrum = { showSpectrum = !showSpectrum }
            )
        }

        // 5. TACTILE ANC MODE SELECTOR MATRIX
        item {
            TactileModeSelectorMatrix(
                currentMode = params.mode,
                onSelectMode = { viewModel.setAncMode(it) }
            )
        }

        // 6. HIGH-PRECISION TACTILE DSP SLIDERS
        item {
            TactileDspControlsCard(
                params = params,
                onUpdateStrength = { viewModel.updateAncStrength(it) },
                onUpdateDelay = { viewModel.updateAudioDelayMs(it) }
            )
        }

        // 7. HARMONIC AUDIO TEST SOURCE MIXER
        item {
            TactileAudioSourceCard(
                params = params,
                onToggleAudioSource = { viewModel.toggleAudioSourceTrack(it) },
                onUpdateVolume = { viewModel.updateAudioSourceVolume(it) }
            )
        }

        // 8. REAL-TIME ENGINE TELEMETRY MATRIX HUD
        item {
            TactileEngineTelemetryHUD(metrics = metrics)
        }

        // 9. HIGH-CONTRAST EMERGENCY KILL SWITCH
        item {
            TactileEmergencyKillSwitch(
                onEmergencyStop = { viewModel.emergencyStop() }
            )
        }
    }
}

// -----------------------------------------------------------------------------
// COMPOSABLES: IMMERSIVE TACTILE COMPONENTS
// -----------------------------------------------------------------------------

@Composable
private fun TactileMasterEngineControl(
    isRunning: Boolean,
    activeMode: AncMode,
    pulseGlow: Float,
    onToggle: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isRunning) AncActiveGreen else CyanPrimary.copy(alpha = 0.5f),
        label = "master_border_color"
    )

    val gradientBrush = if (isRunning) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF00381F),
                Color(0xFF005A32),
                Color(0xFF00381F)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF0B1B2B),
                Color(0xFF132840),
                Color(0xFF0B1B2B)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isRunning) (8 * pulseGlow).dp else 2.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = if (isRunning) AncActiveGreen else Color.Black,
                spotColor = if (isRunning) AncActiveGreen else CyanPrimary
            )
            .clip(RoundedCornerShape(18.dp))
            .background(gradientBrush)
            .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = if (isRunning) AncEmergencyRed else AncActiveGreen),
                onClick = onToggle
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("engine_toggle_button")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRunning) AncActiveGreen.copy(alpha = 0.25f) else Color(0x2200E5FF)
                        )
                        .border(
                            1.5.dp,
                            if (isRunning) AncActiveGreen else CyanPrimary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PowerSettingsNew,
                        contentDescription = if (isRunning) "Stop Engine" else "Start Engine",
                        tint = if (isRunning) AncActiveGreen else CyanPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRunning) AncActiveGreen.copy(alpha = pulseGlow) else TextMuted
                                )
                        )
                        Text(
                            text = if (isRunning) "ENGINE ONLINE • REAL-TIME" else "ENGINE STANDBY",
                            color = if (isRunning) AncActiveGreen else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (isRunning) "TAP TO HALT AUDIO PIPELINE" else "TAP TO ENGAGE SOFTWARE ANC",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isRunning) Color(0x3300E676) else DarkSurfaceVariant)
                    .border(
                        1.dp,
                        if (isRunning) AncActiveGreen.copy(alpha = 0.6f) else ObsidianCardBorder,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isRunning) activeMode.displayName else "OFF",
                    color = if (isRunning) AncActiveGreen else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun TactileAudioFlowMatrix(
    selectedInput: AudioDeviceInfoWrapper?,
    selectedOutput: AudioDeviceInfoWrapper?,
    metrics: DspMetrics,
    params: DspParameters,
    onConfigureRoutes: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        WaveMic.copy(alpha = 0.4f),
                        WaveAntiNoise.copy(alpha = 0.4f),
                        WaveOutput.copy(alpha = 0.4f)
                    )
                ),
                RoundedCornerShape(18.dp)
            )
            .clickable { onConfigureRoutes() },
        colors = CardDefaults.cardColors(containerColor = ObsidianCardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary)
                    )
                    Text(
                        text = "DYNAMIC HARDWARE ROUTE MATRIX",
                        color = CyanPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "CONFIGURE",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Configure",
                        tint = CyanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TactileNode(
                    icon = Icons.Default.Mic,
                    nodeLabel = "REF MIC",
                    deviceName = selectedInput?.displayName ?: "Searching Mic...",
                    techDetail = "${metrics.sampleRate}Hz",
                    accentColor = WaveMic,
                    modifier = Modifier.weight(1f)
                )

                TactileFlowBeam(
                    color = WaveMic,
                    isActive = metrics.isRunning
                )

                TactileDspNode(
                    mode = params.mode,
                    latencyMs = metrics.totalEstimatedLatencyMs,
                    taps = params.filterTaps,
                    isActive = metrics.isRunning,
                    modifier = Modifier.weight(1.1f)
                )

                TactileFlowBeam(
                    color = WaveOutput,
                    isActive = metrics.isRunning
                )

                TactileNode(
                    icon = Icons.Default.Headphones,
                    nodeLabel = "TRANSDUCER",
                    deviceName = selectedOutput?.displayName ?: "Searching Out...",
                    techDetail = "Stereo Out",
                    accentColor = WaveOutput,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TactileNode(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    nodeLabel: String,
    deviceName: String,
    techDetail: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f))
                .border(1.dp, accentColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = nodeLabel,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = nodeLabel,
            color = accentColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = deviceName,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = techDetail,
            color = TextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun TactileDspNode(
    mode: AncMode,
    latencyMs: Float,
    taps: Int,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF10192C))
            .border(
                1.dp,
                if (isActive) AncActiveGreen else ObsidianCardBorder,
                RoundedCornerShape(12.dp)
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(WaveAntiNoise.copy(alpha = 0.2f))
                .border(1.dp, WaveAntiNoise, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Ø",
                color = WaveAntiNoise,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "FxLMS CORE",
            color = WaveAntiNoise,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "${taps} Taps FIR",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Text(
            text = "${String.format("%.1f", latencyMs)}ms delay",
            color = MintSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun TactileFlowBeam(
    color: Color,
    isActive: Boolean
) {
    Canvas(
        modifier = Modifier
            .width(18.dp)
            .height(2.dp)
    ) {
        drawLine(
            color = if (isActive) color else Color.White.copy(alpha = 0.15f),
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun TactileSafeguardAlertCard(onReset: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AncWarningAmber.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(AncWarningAmber, AncEmergencyRed))
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Alert",
                tint = AncWarningAmber,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DSP STABILITY SAFEGUARD ENGAGED",
                    color = AncWarningAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Adaptive weights clamped to prevent feedback howl. Leaky damping applied.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp
                )
            }
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = AncWarningAmber),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("RESET", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TactileVisualizerSuite(
    snapshot: VisualizerSnapshot,
    metrics: DspMetrics,
    params: DspParameters,
    showSpectrum: Boolean,
    onToggleSpectrum: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (showSpectrum) AmberTertiary else CyanPrimary)
                )
                Text(
                    text = if (showSpectrum) "CRT SPECTRAL FFT ANALYZER" else "CRT REAL-TIME OSCILLOSCOPE",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(8.dp))
                    .clickable { onToggleSpectrum() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Switch View",
                        tint = CyanPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (showSpectrum) "WAVEFORM" else "SPECTRUM",
                        color = CyanPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showSpectrum) {
            RealTimeSpectrum(
                snapshot = snapshot,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            RealTimeOscilloscope(
                snapshot = snapshot,
                showAntiNoise = (params.mode == AncMode.EXPERIMENTAL_ANC),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PeakLevelMeter(
                label = "MIC IN",
                dbLevel = metrics.inputPeakLevelDb,
                barColor = WaveMic,
                modifier = Modifier.weight(1f)
            )
            PeakLevelMeter(
                label = "ANTI-NOISE",
                dbLevel = metrics.antiNoisePeakLevelDb,
                barColor = WaveAntiNoise,
                modifier = Modifier.weight(1f)
            )
            PeakLevelMeter(
                label = "OUTPUT MIX",
                dbLevel = metrics.outputPeakLevelDb,
                barColor = WaveOutput,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TactileModeSelectorMatrix(
    currentMode: AncMode,
    onSelectMode: (AncMode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = ObsidianCardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "CANCELLATION & MONITORING MODES",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(AncMode.values()) { mode ->
                    val isSelected = (currentMode == mode)
                    val activeAccent = when (mode) {
                        AncMode.EXPERIMENTAL_ANC -> AncActiveGreen
                        AncMode.MONITOR -> CyanPrimary
                        AncMode.TEST_SIGNAL -> AmberTertiary
                        AncMode.BYPASS -> MintSecondary
                        AncMode.OFF -> Color.White.copy(alpha = 0.5f)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) activeAccent.copy(alpha = 0.2f) else DarkSurfaceVariant
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) activeAccent else ObsidianCardBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectMode(mode) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("mode_button_${mode.name}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) activeAccent else Color.White.copy(alpha = 0.2f))
                            )
                            Text(
                                text = mode.displayName,
                                color = if (isSelected) activeAccent else Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = currentMode.description,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun TactileDspControlsCard(
    params: DspParameters,
    onUpdateStrength: (Float) -> Unit,
    onUpdateDelay: (Float) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = ObsidianCardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ANC Strength Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Strength",
                        tint = AncActiveGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Anti-Noise Gain (Strength)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AncActiveGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${(params.ancStrength * 100).toInt()}%",
                        color = AncActiveGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Slider(
                value = params.ancStrength,
                onValueChange = onUpdateStrength,
                valueRange = 0.0f..1.2f,
                colors = SliderDefaults.colors(
                    thumbColor = AncActiveGreen,
                    activeTrackColor = AncActiveGreen,
                    inactiveTrackColor = DarkSurfaceVariant
                ),
                modifier = Modifier.testTag("anc_strength_slider")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Audio Delay Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Delay",
                        tint = CyanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Phase Alignment Delay",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyanPrimary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${String.format("%.1f", params.audioDelayMs)} ms",
                        color = CyanPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Slider(
                value = params.audioDelayMs,
                onValueChange = onUpdateDelay,
                valueRange = 0.0f..200.0f,
                colors = SliderDefaults.colors(
                    thumbColor = CyanPrimary,
                    activeTrackColor = CyanPrimary,
                    inactiveTrackColor = DarkSurfaceVariant
                ),
                modifier = Modifier.testTag("audio_delay_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = listOf(0f, 5f, 10f, 15f, 20f, 50f, 100f)
                for (p in presets) {
                    val isPreset = (params.audioDelayMs.toInt() == p.toInt())
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isPreset) CyanPrimary.copy(alpha = 0.3f) else DarkSurfaceVariant
                            )
                            .border(
                                1.dp,
                                if (isPreset) CyanPrimary else ObsidianCardBorder,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onUpdateDelay(p) }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${p.toInt()}ms",
                            color = if (isPreset) CyanPrimary else TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TactileAudioSourceCard(
    params: DspParameters,
    onToggleAudioSource: (Boolean) -> Unit,
    onUpdateVolume: (Float) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = ObsidianCardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MintSecondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Synth",
                        tint = MintSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Harmonic Audio Source Track",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Synthesizer stream mixed with anti-noise",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                Switch(
                    checked = params.playAudioSourceTrack,
                    onCheckedChange = onToggleAudioSource,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MintSecondary,
                        checkedTrackColor = MintSecondary.copy(alpha = 0.4f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.testTag("audio_source_switch")
                )
            }

            AnimatedVisibility(
                visible = params.playAudioSourceTrack,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Music Stream Gain",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${(params.audioSourceVolume * 100).toInt()}%",
                            color = MintSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Slider(
                        value = params.audioSourceVolume,
                        onValueChange = onUpdateVolume,
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = MintSecondary,
                            activeTrackColor = MintSecondary,
                            inactiveTrackColor = DarkSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TactileEngineTelemetryHUD(metrics: DspMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "HARDWARE ENGINE TELEMETRY",
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TactileTelemetryTile(
                title = "Round-Trip Delay",
                value = String.format("%.1f", metrics.totalEstimatedLatencyMs),
                unit = "ms",
                accentColor = CyanPrimary,
                modifier = Modifier.weight(1f)
            )
            TactileTelemetryTile(
                title = "Sample Rate",
                value = "${metrics.sampleRate}",
                unit = "Hz",
                accentColor = MintSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TactileTelemetryTile(
                title = "Buffer Frame Size",
                value = "${metrics.bufferSizeFrames}",
                unit = "frames",
                accentColor = AmberTertiary,
                modifier = Modifier.weight(1f)
            )
            TactileTelemetryTile(
                title = "DSP CPU Load",
                value = String.format("%.1f", metrics.dspLoadPercent),
                unit = "%",
                accentColor = if (metrics.dspLoadPercent > 75f) AncEmergencyRed else AncActiveGreen,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TactileTelemetryTile(
    title: String,
    value: String,
    unit: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCardBg)
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                text = title.uppercase(),
                color = TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = accentColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TactileEmergencyKillSwitch(onEmergencyStop: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF2A080C),
                        Color(0xFF3F0C12),
                        Color(0xFF2A080C)
                    )
                )
            )
            .border(
                1.5.dp,
                Brush.horizontalGradient(listOf(AncEmergencyRed, Color(0xFFFF5252))),
                RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = AncEmergencyRed),
                onClick = onEmergencyStop
            )
            .padding(vertical = 14.dp, horizontal = 16.dp)
            .testTag("emergency_stop_button")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = "Kill Switch",
                tint = AncEmergencyRed,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "EMERGENCY KILL SWITCH (INSTANT MUTE ANTI-NOISE)",
                color = AncEmergencyRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp
            )
        }
    }
}

@Composable
private fun TactileOboeEngineConfigCard(
    isOboeActive: Boolean,
    selectedSampleRate: HighSampleRate,
    selectedSharingMode: OboeSharingMode,
    oboeTelemetry: com.example.audio.oboe.OboeTelemetry,
    onToggleOboe: (Boolean) -> Unit,
    onSelectSampleRate: (HighSampleRate) -> Unit,
    onSelectSharingMode: (OboeSharingMode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("oboe_engine_config_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCardBg),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isOboeActive) CyanPrimary.copy(alpha = 0.5f) else ObsidianCardBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with Engine Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isOboeActive) CyanPrimary.copy(alpha = 0.15f) else DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Oboe Engine",
                            tint = if (isOboeActive) CyanPrimary else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "OBOE C++ AUDIO ENGINE",
                            color = if (isOboeActive) CyanPrimary else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (isOboeActive) oboeTelemetry.backendName else "Standard JVM Audio Pipeline",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Switch(
                    checked = isOboeActive,
                    onCheckedChange = onToggleOboe,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanPrimary,
                        checkedTrackColor = CyanPrimary.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.testTag("oboe_engine_toggle")
                )
            }

            AnimatedVisibility(visible = isOboeActive) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // High Sample Rate Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "SAMPLE RATE (HI-RES AUDIO PIPELINE)",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(HighSampleRate.values()) { rate ->
                                val isSelected = rate == selectedSampleRate
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) CyanPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) CyanPrimary else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onSelectSampleRate(rate) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${rate.sampleRateHz / 1000} kHz",
                                        color = if (isSelected) CyanPrimary else Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Hardware Sharing Mode
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "HARDWARE SHARING MODE",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OboeSharingMode.values().forEach { mode ->
                                val isSelected = mode == selectedSharingMode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MintSecondary.copy(alpha = 0.2f) else DarkSurfaceVariant
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) MintSecondary else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onSelectSharingMode(mode) }
                                        .padding(vertical = 8.dp, horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.label,
                                        color = if (isSelected) MintSecondary else Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Native Telemetry Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF090E1A))
                            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("BURST SIZE", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${oboeTelemetry.inputBufferSizeFrames} f", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("LATENCY", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${String.format("%.1f", oboeTelemetry.estimatedLatencyMs)} ms", color = AncActiveGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("xRUNS", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${oboeTelemetry.xRuns}", color = if (oboeTelemetry.xRuns > 0) AncWarningAmber else Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

