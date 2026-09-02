package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VisualizerSnapshot
import com.example.ui.theme.AncActiveGreen
import com.example.ui.theme.AncEmergencyRed
import com.example.ui.theme.AncWarningAmber
import com.example.ui.theme.WaveAntiNoise
import com.example.ui.theme.WaveMic
import com.example.ui.theme.WaveOutput

private val CrtBackground = Color(0xFF070B14)
private val CrtBorder = Color(0xFF1E2B45)
private val CrtGridColor = Color(0x1F00E5FF)

@Composable
fun RealTimeOscilloscope(
    snapshot: VisualizerSnapshot,
    showAntiNoise: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CrtBackground)
            .border(1.dp, CrtBorder, RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            // 1. Draw Phosphor CRT Grid Lines
            drawLine(CrtGridColor.copy(alpha = 0.35f), Offset(0f, centerY), Offset(width, centerY), strokeWidth = 1.dp.toPx())
            drawLine(CrtGridColor.copy(alpha = 0.18f), Offset(0f, centerY - height * 0.25f), Offset(width, centerY - height * 0.25f), strokeWidth = 0.5.dp.toPx())
            drawLine(CrtGridColor.copy(alpha = 0.18f), Offset(0f, centerY + height * 0.25f), Offset(width, centerY + height * 0.25f), strokeWidth = 0.5.dp.toPx())

            for (i in 1..9) {
                val x = width * (i / 10f)
                drawLine(CrtGridColor.copy(alpha = 0.15f), Offset(x, 0f), Offset(x, height), strokeWidth = 0.5.dp.toPx())
            }

            // 2. Draw Mic Reference Waveform (Cyan)
            val micSamples = snapshot.micWaveform
            if (micSamples.isNotEmpty()) {
                val micPath = Path()
                val step = width / (micSamples.size - 1).toFloat()
                for (i in micSamples.indices) {
                    val x = i * step
                    val y = centerY - (micSamples[i] * height * 0.44f)
                    if (i == 0) micPath.moveTo(x, y) else micPath.lineTo(x, y)
                }

                // Outer glow
                drawPath(
                    path = micPath,
                    color = WaveMic.copy(alpha = 0.3f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Core beam
                drawPath(
                    path = micPath,
                    color = WaveMic,
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // 3. Draw Anti-Noise Waveform (Pink) if enabled
            if (showAntiNoise) {
                val antiSamples = snapshot.antiNoiseWaveform
                if (antiSamples.isNotEmpty()) {
                    val antiPath = Path()
                    val step = width / (antiSamples.size - 1).toFloat()
                    for (i in antiSamples.indices) {
                        val x = i * step
                        val y = centerY - (antiSamples[i] * height * 0.44f)
                        if (i == 0) antiPath.moveTo(x, y) else antiPath.lineTo(x, y)
                    }

                    // Outer glow
                    drawPath(
                        path = antiPath,
                        color = WaveAntiNoise.copy(alpha = 0.3f),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // Core beam
                    drawPath(
                        path = antiPath,
                        color = WaveAntiNoise,
                        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            // 4. Draw Output Waveform (Mint Green)
            val outSamples = snapshot.outputWaveform
            if (outSamples.isNotEmpty()) {
                val outPath = Path()
                val step = width / (outSamples.size - 1).toFloat()
                for (i in outSamples.indices) {
                    val x = i * step
                    val y = centerY - (outSamples[i] * height * 0.44f)
                    if (i == 0) outPath.moveTo(x, y) else outPath.lineTo(x, y)
                }

                // Outer glow
                drawPath(
                    path = outPath,
                    color = WaveOutput.copy(alpha = 0.35f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Core beam
                drawPath(
                    path = outPath,
                    color = WaveOutput,
                    style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        // Top Status HUD
        Text(
            text = "CH1: MIC [CYAN]  •  CH2: ANTI [MAGENTA]  •  CH3: OUT [MINT]",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.TopStart)
        )

        // Legend indicator at bottom right
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color(0xCC050811), RoundedCornerShape(8.dp))
                .border(0.5.dp, Color(0x3300E5FF), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendIndicator(color = WaveMic, label = "MIC")
            if (showAntiNoise) {
                LegendIndicator(color = WaveAntiNoise, label = "ANTI-NOISE")
            }
            LegendIndicator(color = WaveOutput, label = "OUT")
        }
    }
}

@Composable
fun RealTimeSpectrum(
    snapshot: VisualizerSnapshot,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CrtBackground)
            .border(1.dp, CrtBorder, RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val mags = snapshot.spectrumMagnitudes
            val count = mags.size

            // Background frequency grid lines
            for (i in 1..5) {
                val y = height * (i / 6f)
                drawLine(CrtGridColor.copy(alpha = 0.15f), Offset(0f, y), Offset(width, y), strokeWidth = 0.5.dp.toPx())
            }

            if (count > 0) {
                val barWidth = width / count.toFloat()
                for (i in 0 until count) {
                    val normalizedMag = (mags[i] * 3.8f).coerceIn(0.015f, 1.0f)
                    val barHeight = normalizedMag * (height - 20.dp.toPx())
                    val left = i * barWidth
                    val top = height - barHeight

                    val gradient = Brush.verticalGradient(
                        colors = listOf(
                            WaveAntiNoise,
                            WaveMic,
                            WaveOutput
                        ),
                        startY = top,
                        endY = height
                    )

                    drawRect(
                        brush = gradient,
                        topLeft = Offset(left + (barWidth * 0.12f), top),
                        size = androidx.compose.ui.geometry.Size(barWidth * 0.76f, barHeight)
                    )
                }
            }
        }

        // FFT Top HUD
        Text(
            text = "64-BIN RADIX-2 FFT SPECTRUM (0 Hz - 8 kHz)",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.TopStart)
        )

        // Frequency range indicator at bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("80Hz", color = Color.White.copy(alpha = 0.4f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Text("500Hz", color = Color.White.copy(alpha = 0.4f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Text("1kHz", color = Color.White.copy(alpha = 0.4f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Text("2.5kHz", color = Color.White.copy(alpha = 0.4f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Text("8kHz", color = Color.White.copy(alpha = 0.4f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun PeakLevelMeter(
    label: String,
    dbLevel: Float,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    // Convert -60dB .. 0dB into 0.0 .. 1.0 fraction
    val fraction = ((dbLevel + 60f) / 60f).coerceIn(0.0f, 1.0f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0D1322))
            .border(1.dp, Color(0xFF1E2B45), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (dbLevel <= -60f) "-inf" else "${String.format("%.1f", dbLevel)} dB",
                color = if (dbLevel > -3f) AncEmergencyRed else if (dbLevel > -12f) AncWarningAmber else barColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Segmented LED-style bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x26FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                barColor.copy(alpha = 0.7f),
                                if (dbLevel > -12f) AncWarningAmber else barColor,
                                if (dbLevel > -3f) AncEmergencyRed else barColor
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun LegendIndicator(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
