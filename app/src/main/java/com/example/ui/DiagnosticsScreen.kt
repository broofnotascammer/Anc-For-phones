package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MetricTile
import com.example.ui.theme.AncActiveGreen
import com.example.ui.theme.AncEmergencyRed
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextMuted

@Composable
fun DiagnosticsScreen(
    viewModel: AncViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val metrics by viewModel.metrics.collectAsState()
    val diagnosticReport by viewModel.diagnosticReport.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.generateDiagnosticReport()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp)
    ) {
        // 1. Buffer Health & Underrun Counters
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "REAL-TIME STREAM HEALTH",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricTile(
                            title = "Buffer Underruns",
                            value = "${metrics.bufferUnderruns}",
                            accentColor = if (metrics.bufferUnderruns > 0) AncEmergencyRed else AncActiveGreen,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = "Buffer Overruns",
                            value = "${metrics.bufferOverruns}",
                            accentColor = if (metrics.bufferOverruns > 0) AncEmergencyRed else AncActiveGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricTile(
                            title = "Processed Frames",
                            value = "${metrics.processedFrameCount}",
                            accentColor = CyanPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = "Filter Divergence",
                            value = if (metrics.filterDiverged) "YES" else "NO",
                            accentColor = if (metrics.filterDiverged) AncEmergencyRed else AncActiveGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Hardware Specs & Native Audio Properties
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ANDROID AUDIO PLATFORM SPECIFICATIONS",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    diagnosticReport?.let { report ->
                        DiagnosticField(label = "Device", value = "${report.deviceManufacturer} ${report.deviceModel}")
                        DiagnosticField(label = "Android Version", value = "Android ${report.androidVersion} (API ${report.apiLevel})")
                        DiagnosticField(label = "Low-Latency Audio", value = if (report.lowLatencySupported) "SUPPORTED (FLAG_LOW_LATENCY)" else "UNSUPPORTED")
                        DiagnosticField(label = "Pro Audio", value = if (report.proAudioSupported) "SUPPORTED" else "UNSUPPORTED")
                        DiagnosticField(label = "Input AudioRecord", value = report.audioRecordState)
                        DiagnosticField(label = "Output AudioTrack", value = report.audioTrackState)

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AudioManager Properties:",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        report.audioManagerProperties.forEach { (k, v) ->
                            DiagnosticField(label = k, value = v)
                        }
                    }
                }
            }
        }

        // 3. COPY DEBUG REPORT BUTTON
        item {
            Button(
                onClick = {
                    viewModel.generateDiagnosticReport()
                    val report = viewModel.diagnosticReport.value
                    if (report != null) {
                        val reportText = buildString {
                            appendLine("=== SOFTWARE ANC HARDWARE DEBUG REPORT ===")
                            appendLine("Device: ${report.deviceManufacturer} ${report.deviceModel} (Android ${report.androidVersion}, API ${report.apiLevel})")
                            appendLine("Low Latency: ${report.lowLatencySupported} | Pro Audio: ${report.proAudioSupported}")
                            appendLine("Input Device: ${report.currentInputRoute}")
                            appendLine("Output Device: ${report.currentOutputRoute}")
                            appendLine("AudioRecord State: ${report.audioRecordState}")
                            appendLine("AudioTrack State: ${report.audioTrackState}")
                            appendLine("DSP State: ${report.dspState}")
                            appendLine("Metrics: ${report.metricsSummary}")
                            appendLine("AudioManager Props: ${report.audioManagerProperties}")
                            appendLine("Available Inputs: ${report.availableInputs}")
                            appendLine("Available Outputs: ${report.availableOutputs}")
                            appendLine("Timestamp: ${report.timestamp}")
                        }

                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Software ANC Debug Report", reportText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Debug Report copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("copy_debug_report_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Report",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "COPY DEBUG REPORT TO CLIPBOARD",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun DiagnosticField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = CyanPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
    }
}
