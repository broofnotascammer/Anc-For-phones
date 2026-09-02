package com.example.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DeviceSelectionMode
import com.example.ui.components.AudioDeviceCard
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.MintSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WaveMic
import com.example.ui.theme.WaveOutput

@Composable
fun DevicesScreen(
    viewModel: AncViewModel,
    modifier: Modifier = Modifier
) {
    val inputs by viewModel.availableInputs.collectAsState()
    val outputs by viewModel.availableOutputs.collectAsState()
    val selectedInput by viewModel.selectedInput.collectAsState()
    val selectedOutput by viewModel.selectedOutput.collectAsState()
    val inputMode by viewModel.inputSelectionMode.collectAsState()
    val outputMode by viewModel.outputSelectionMode.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp)
    ) {
        // 1. Discovery Architecture Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = CyanPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DYNAMIC HARDWARE DISCOVERY",
                            color = CyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "This engine uses Android AudioManager to query attached wired headsets, USB DACs, Bluetooth LE/A2DP devices, and internal transducers without hard-coded assumptions.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // 2. Scan / Refresh Action
        item {
            Button(
                onClick = { viewModel.refreshDevices() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("refresh_devices_button"),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Scan",
                    tint = CyanPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RE-SCAN AUDIO HARDWARE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // 3. INPUT DEVICES SECTION
        item {
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
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Input",
                            tint = WaveMic,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "INPUT DEVICES (${inputs.size})",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Auto Mode Toggle Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (inputMode == DeviceSelectionMode.AUTOMATIC) WaveMic.copy(alpha = 0.2f) else DarkSurfaceVariant
                            )
                            .border(
                                1.dp,
                                if (inputMode == DeviceSelectionMode.AUTOMATIC) WaveMic else DarkSurfaceBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.selectManualInput(null) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("input_auto_toggle")
                    ) {
                        Text(
                            text = if (inputMode == DeviceSelectionMode.AUTOMATIC) "MODE: AUTOMATIC" else "SWITCH TO AUTO",
                            color = if (inputMode == DeviceSelectionMode.AUTOMATIC) WaveMic else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (inputs.isEmpty()) {
            item {
                Text(
                    text = "No audio input devices exposed by Android OS.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(inputs) { dev ->
                val isSelected = (selectedInput?.id == dev.id)
                AudioDeviceCard(
                    device = dev,
                    isSelected = isSelected,
                    onSelect = { viewModel.selectManualInput(dev) },
                    modifier = Modifier.testTag("input_device_${dev.id}")
                )
            }
        }

        // 4. OUTPUT DEVICES SECTION
        item {
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
                        Icon(
                            imageVector = Icons.Default.Speaker,
                            contentDescription = "Output",
                            tint = WaveOutput,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "OUTPUT DEVICES (${outputs.size})",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Auto Mode Toggle Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (outputMode == DeviceSelectionMode.AUTOMATIC) WaveOutput.copy(alpha = 0.2f) else DarkSurfaceVariant
                            )
                            .border(
                                1.dp,
                                if (outputMode == DeviceSelectionMode.AUTOMATIC) WaveOutput else DarkSurfaceBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.selectManualOutput(null) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("output_auto_toggle")
                    ) {
                        Text(
                            text = if (outputMode == DeviceSelectionMode.AUTOMATIC) "MODE: AUTOMATIC" else "SWITCH TO AUTO",
                            color = if (outputMode == DeviceSelectionMode.AUTOMATIC) WaveOutput else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (outputs.isEmpty()) {
            item {
                Text(
                    text = "No audio output devices exposed by Android OS.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(outputs) { dev ->
                val isSelected = (selectedOutput?.id == dev.id)
                AudioDeviceCard(
                    device = dev,
                    isSelected = isSelected,
                    onSelect = { viewModel.selectManualOutput(dev) },
                    modifier = Modifier.testTag("output_device_${dev.id}")
                )
            }
        }
    }
}
