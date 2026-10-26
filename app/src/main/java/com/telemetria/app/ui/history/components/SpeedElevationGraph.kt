package com.telemetria.app.ui.history.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
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
fun SpeedElevationGraph(
    points: List<TelemetryPointEntity>,
    modifier: Modifier = Modifier
) {
    val gpsPoints = points.filter { it.gpsSpeedKmh != null }
    val obdPoints = points.filter { it.obdSpeedKmh != null }

    val allSpeeds = buildList {
        gpsPoints.forEach { it.gpsSpeedKmh?.let { s -> add(s) } }
        obdPoints.forEach { it.obdSpeedKmh?.let { s -> add(s) } }
    }

    val maxSpeed = (allSpeeds.maxOrNull() ?: 100f).coerceAtLeast(20f)
    val minSpeed = 0f
    val hasGps = gpsPoints.size >= 2
    val hasObd = obdPoints.size >= 2

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "PERFIL VELOCIDAD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    if (hasGps && hasObd) {
                        Text(text = "● GPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                        Text(text = "● OBD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                    } else if (hasObd) {
                        Text(text = "● OBD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                    } else if (hasGps) {
                        Text(text = "● GPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                    }
                }
                Text(
                    text = "MÁX: ${String.format(Locale.US, "%.1f", maxSpeed)} km/h",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = NeonGreen
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!hasGps && !hasObd) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Datos insuficientes para la gráfica",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val width = size.width
                    val height = size.height

                    // Grid lines (25%, 50%, 75%)
                    val gridColor = Color(0xFF263238)
                    for (i in 1..3) {
                        val y = height * (i / 4f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )
                    }

                    // 1. Trazado OBD Speed (si existe)
                    if (hasObd) {
                        val obdPath = Path()
                        val stepX = width / (obdPoints.size - 1).coerceAtLeast(1)
                        obdPoints.forEachIndexed { index, point ->
                            val speed = point.obdSpeedKmh ?: 0f
                            val x = index * stepX
                            val normalizedY = 1f - ((speed - minSpeed) / (maxSpeed - minSpeed)).coerceIn(0f, 1f)
                            val y = normalizedY * (height - 10f) + 5f

                            if (index == 0) obdPath.moveTo(x, y) else obdPath.lineTo(x, y)
                        }
                        drawPath(
                            path = obdPath,
                            color = NeonCyan.copy(alpha = 0.85f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    // 2. Trazado GPS Speed (si existe)
                    if (hasGps) {
                        val gpsPath = Path()
                        val stepX = width / (gpsPoints.size - 1).coerceAtLeast(1)
                        gpsPoints.forEachIndexed { index, point ->
                            val speed = point.gpsSpeedKmh ?: 0f
                            val x = index * stepX
                            val normalizedY = 1f - ((speed - minSpeed) / (maxSpeed - minSpeed)).coerceIn(0f, 1f)
                            val y = normalizedY * (height - 10f) + 5f

                            if (index == 0) gpsPath.moveTo(x, y) else gpsPath.lineTo(x, y)
                        }
                        drawPath(
                            path = gpsPath,
                            color = NeonGreen,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

