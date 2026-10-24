package com.telemetria.app.ui.history.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telemetria.app.core.database.entity.TelemetryPointEntity
import com.telemetria.app.ui.theme.CockpitBorder
import com.telemetria.app.ui.theme.CockpitCardBg
import com.telemetria.app.ui.theme.NeonCyan
import com.telemetria.app.ui.theme.NeonGreen
import com.telemetria.app.ui.theme.TextMuted
import com.telemetria.app.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun GForceGraph(
    points: List<TelemetryPointEntity>,
    modifier: Modifier = Modifier
) {
    val latGList = points.mapNotNull { it.lateralG }
    val longGList = points.mapNotNull { it.longitudinalG }

    val maxLat = (latGList.map { kotlin.math.abs(it) }.maxOrNull() ?: 1.0f).coerceAtLeast(0.5f)
    val maxLong = (longGList.map { kotlin.math.abs(it) }.maxOrNull() ?: 1.0f).coerceAtLeast(0.5f)
    val scaleG = maxOf(maxLat, maxLong, 1.0f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(CockpitCardBg, RoundedCornerShape(12.dp))
            .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Text(
                    text = "DINÁMICA G-FORCE (LATERAL / LONGITUDINAL)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Text(
                    text = "ESCALA: ±${String.format(Locale.US, "%.1f", scaleG)} G",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = NeonCyan
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(modifier = Modifier.padding(end = 4.dp).background(NeonCyan).height(3.dp).fillMaxWidth(0.05f))
                    Text("G Lateral (Paso curva)", fontSize = 10.sp, color = NeonCyan)
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(modifier = Modifier.padding(end = 4.dp).background(NeonGreen).height(3.dp).fillMaxWidth(0.05f))
                    Text("G Long (Acel/Freno)", fontSize = 10.sp, color = NeonGreen)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (latGList.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = "Datos inerciales insuficientes",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f

                    // Línea central (0 G)
                    drawLine(
                        color = Color(0xFF37474F),
                        start = Offset(0f, centerY),
                        end = Offset(width, centerY),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Líneas de límite (+/- G)
                    drawLine(color = Color(0xFF1E2833), start = Offset(0f, 0f), end = Offset(width, 0f), strokeWidth = 1f)
                    drawLine(color = Color(0xFF1E2833), start = Offset(0f, height), end = Offset(width, height), strokeWidth = 1f)

                    val stepX = width / (latGList.size - 1).coerceAtLeast(1)

                    // Traza Lateral G (Cyan)
                    val latPath = Path()
                    latGList.forEachIndexed { i, g ->
                        val x = i * stepX
                        val normalized = (g / scaleG).coerceIn(-1f, 1f)
                        val y = centerY - normalized * (centerY - 5f)
                        if (i == 0) latPath.moveTo(x, y) else latPath.lineTo(x, y)
                    }
                    drawPath(path = latPath, color = NeonCyan, style = Stroke(width = 2.dp.toPx()))

                    // Traza Longitudinal G (Verde)
                    if (longGList.size == latGList.size) {
                        val longPath = Path()
                        longGList.forEachIndexed { i, g ->
                            val x = i * stepX
                            val normalized = (g / scaleG).coerceIn(-1f, 1f)
                            val y = centerY - normalized * (centerY - 5f)
                            if (i == 0) longPath.moveTo(x, y) else longPath.lineTo(x, y)
                        }
                        drawPath(path = longPath, color = NeonGreen, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
        }
    }
}

