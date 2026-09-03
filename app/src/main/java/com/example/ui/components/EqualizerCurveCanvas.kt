package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.MintSecondary

@Composable
fun EqualizerCurveCanvas(
    bandGains: List<Float>,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        // Draw grid lines: +12dB, 0dB, -12dB
        val gridColor = DarkSurfaceBorder.copy(alpha = 0.5f)
        val line0DbColor = Color.White.copy(alpha = 0.2f)

        val yPlus12 = midY - (12f / 15f) * (midY - 10f)
        val yMinus12 = midY + (12f / 15f) * (midY - 10f)

        // +12 dB
        drawLine(
            color = gridColor,
            start = Offset(0f, yPlus12),
            end = Offset(w, yPlus12),
            strokeWidth = 1f
        )

        // 0 dB reference center
        drawLine(
            color = line0DbColor,
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.5f
        )

        // -12 dB
        drawLine(
            color = gridColor,
            start = Offset(0f, yMinus12),
            end = Offset(w, yMinus12),
            strokeWidth = 1f
        )

        // Frequency vertical grid guides (60Hz, 250Hz, 1kHz, 4kHz, 12kHz)
        val numBands = bandGains.size.coerceAtLeast(1)
        val bandPoints = mutableListOf<Offset>()

        for (i in 0 until numBands) {
            val fractionX = (i + 0.5f) / numBands
            val px = fractionX * w
            val gain = if (isEnabled) bandGains.getOrElse(i) { 0f } else 0f
            val py = midY - (gain / 15f) * (midY - 14f)
            bandPoints.add(Offset(px, py))

            // Draw vertical band guide
            drawLine(
                color = gridColor.copy(alpha = 0.3f),
                start = Offset(px, 0f),
                end = Offset(px, h),
                strokeWidth = 1f
            )
        }

        // Generate smooth curve through points
        val path = Path()
        val fillPath = Path()

        val startY = if (isEnabled) {
            midY - ((bandGains.firstOrNull() ?: 0f) / 15f) * (midY - 14f)
        } else midY

        path.moveTo(0f, startY)
        fillPath.moveTo(0f, midY)
        fillPath.lineTo(0f, startY)

        for (i in 0 until bandPoints.size) {
            val p = bandPoints[i]
            if (i == 0) {
                path.cubicTo(
                    bandPoints[0].x * 0.4f, startY,
                    bandPoints[0].x * 0.6f, bandPoints[0].y,
                    bandPoints[0].x, bandPoints[0].y
                )
            } else {
                val prev = bandPoints[i - 1]
                val cx1 = (prev.x + p.x) / 2f
                val cy1 = prev.y
                val cx2 = (prev.x + p.x) / 2f
                val cy2 = p.y
                path.cubicTo(cx1, cy1, cx2, cy2, p.x, p.y)
            }
        }

        val lastP = bandPoints.last()
        path.cubicTo(
            (lastP.x + w) / 2f, lastP.y,
            (lastP.x + w) / 2f, lastP.y,
            w, lastP.y
        )

        fillPath.addPath(path)
        fillPath.lineTo(w, midY)
        fillPath.close()

        val activeGradient = Brush.verticalGradient(
            colors = listOf(
                CyanPrimary.copy(alpha = if (isEnabled) 0.35f else 0.05f),
                MintSecondary.copy(alpha = if (isEnabled) 0.1f else 0.02f),
                Color.Transparent
            )
        )

        // Draw fill area below curve
        drawPath(
            path = fillPath,
            brush = activeGradient
        )

        // Draw main curve stroke
        val strokeColor = if (isEnabled) CyanPrimary else Color.Gray.copy(alpha = 0.5f)
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw band control point circles
        for (i in 0 until bandPoints.size) {
            val p = bandPoints[i]
            // Outer glow ring
            drawCircle(
                color = if (isEnabled) MintSecondary.copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.2f),
                radius = 7.dp.toPx(),
                center = p
            )
            // Inner core dot
            drawCircle(
                color = if (isEnabled) Color.White else Color.LightGray,
                radius = 4.dp.toPx(),
                center = p
            )
        }
    }
}
