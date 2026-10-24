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
import com.telemetria.app.ui.theme.NeonGreen
import com.telemetria.app.ui.theme.TextMuted
import com.telemetria.app.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun SpeedElevationGraph(
    points: List<TelemetryPointEntity>,
    modifier: Modifier = Modifier
) {
    val speedValues = points.mapNotNull { it.gpsSpeedKmh }
    val maxSpeed = (speedValues.maxOrNull() ?: 100f).coerceAtLeast(20f)
    val minSpeed = 0f

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
                    text = "PERFIL DE VELOCIDAD (KM/H)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Text(
                    text = "MÁX: ${String.format(Locale.US, "%.1f", maxSpeed)} km/h",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = NeonGreen
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (speedValues.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
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

                    // Build path
                    val path = Path()
                    val stepX = width / (speedValues.size - 1).coerceAtLeast(1)

                    speedValues.forEachIndexed { index, speed ->
                        val x = index * stepX
                        val normalizedY = 1f - ((speed - minSpeed) / (maxSpeed - minSpeed)).coerceIn(0f, 1f)
                        val y = normalizedY * (height - 10f) + 5f

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = NeonGreen,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
        }
    }
}

