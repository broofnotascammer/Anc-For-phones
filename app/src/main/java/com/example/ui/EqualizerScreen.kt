package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.EqualizerPreset
import com.example.ui.components.EqualizerCurveCanvas
import com.example.ui.theme.AncActiveGreen
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.MintSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private val BAND_INFO = listOf(
    Triple("60 Hz", "Sub-Bass", "Deep rumble, vibrations, structural drone"),
    Triple("250 Hz", "Bass", "Vehicle engines, HVAC hum, tire noise"),
    Triple("1 kHz", "Midrange", "Vocal core, fundamental ambient chatter"),
    Triple("4 kHz", "High-Mid", "Vocal clarity, presence, friction"),
    Triple("12 kHz", "Treble", "High-pitch fan hiss, whistle, air")
)

@Composable
fun EqualizerScreen(
    viewModel: AncViewModel,
    modifier: Modifier = Modifier
) {
    val eqState by viewModel.equalizerState.collectAsState()
    val isOboeActive by viewModel.isOboeActive.collectAsState()
    val metrics by viewModel.metrics.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060913),
                        DarkBackground,
                        DarkSurface
                    )
                )
            ),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Card with Toggle & Reset
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eq_header_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        listOf(CyanPrimary.copy(alpha = 0.4f), MintSecondary.copy(alpha = 0.2f))
                    )
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(CyanPrimary.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, CyanPrimary.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "NATIVE 5-BAND EQUALIZER",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(
                                                if (eqState.isEnabled) AncActiveGreen else Color.Gray,
                                                CircleShape
                                            )
                                    )
                                    Text(
                                        text = if (isOboeActive) "C++ High-Precision Biquad Engine" else "JVM Fallback EQ",
                                        color = MintSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.resetEqualizer() },
                                modifier = Modifier.testTag("eq_reset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset EQ",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Switch(
                                checked = eqState.isEnabled,
                                onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = CyanPrimary,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = DarkSurfaceVariant
                                ),
                                modifier = Modifier.testTag("eq_master_switch")
                            )
                        }
                    }
                }
            }
        }

        // 2. Interactive Frequency Response Curve
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eq_curve_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FREQUENCY RESPONSE CURVE",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (eqState.isEnabled) "${eqState.selectedPreset.displayName} (${if (metrics.isRunning) "ACTIVE" else "READY"})" else "BYPASSED",
                            color = if (eqState.isEnabled) CyanPrimary else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    EqualizerCurveCanvas(
                        bandGains = eqState.bandGains,
                        isEnabled = eqState.isEnabled
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Labels below curve
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        BAND_INFO.forEach { (freq, _, _) ->
                            Text(
                                text = freq,
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 3. Quick Presets Selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "EQ PROFILES & PRESETS",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(EqualizerPreset.values()) { preset ->
                        val isSelected = eqState.selectedPreset == preset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) CyanPrimary.copy(alpha = 0.2f) else DarkSurface
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) CyanPrimary else DarkSurfaceBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.setEqualizerPreset(preset)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("eq_preset_${preset.name}")
                        ) {
                            Column {
                                Text(
                                    text = preset.displayName,
                                    color = if (isSelected) CyanPrimary else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                )
                                Text(
                                    text = preset.description,
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Individual 5 Band Gain Sliders
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "BAND GAIN CONTROLS (-15 dB to +15 dB)",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )

                BAND_INFO.forEachIndexed { index, (freq, label, desc) ->
                    val gain = eqState.bandGains.getOrElse(index) { 0f }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eq_band_card_$index"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(CyanPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = freq,
                                            color = CyanPrimary,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = label,
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = desc,
                                            color = TextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                // DB readout box
                                Box(
                                    modifier = Modifier
                                        .background(
                                            when {
                                                gain > 0.05f -> MintSecondary.copy(alpha = 0.15f)
                                                gain < -0.05f -> Color(0xFFFF9100).copy(alpha = 0.15f)
                                                else -> DarkSurfaceVariant
                                            },
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${if (gain > 0f) "+" else ""}${String.format("%.1f", gain)} dB",
                                        color = when {
                                            gain > 0.05f -> MintSecondary
                                            gain < -0.05f -> Color(0xFFFF9100)
                                            else -> TextMuted
                                        },
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Slider(
                                value = gain,
                                onValueChange = { newGain ->
                                    viewModel.setEqualizerBandGain(index, newGain)
                                },
                                valueRange = -15f..15f,
                                steps = 29, // 1 dB increments
                                colors = SliderDefaults.colors(
                                    thumbColor = CyanPrimary,
                                    activeTrackColor = CyanPrimary,
                                    inactiveTrackColor = DarkSurfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("eq_slider_band_$index")
                            )

                            // Quick Nudge Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "-15 dB",
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = "0 dB",
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    modifier = Modifier.clickable {
                                        viewModel.setEqualizerBandGain(index, 0f)
                                    }
                                )
                                Text(
                                    text = "+15 dB",
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
