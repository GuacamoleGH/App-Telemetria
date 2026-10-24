package com.telemetria.app.ui.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telemetria.app.ui.theme.CockpitBorder
import com.telemetria.app.ui.theme.CockpitCardBg
import com.telemetria.app.ui.theme.NeonAmber
import com.telemetria.app.ui.theme.NeonCyan
import com.telemetria.app.ui.theme.NeonGreen
import com.telemetria.app.ui.theme.NeonRed
import com.telemetria.app.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun GMeterView(
    lateralG: Float,
    longitudinalG: Float,
    gpsDerivedLongG: Float?,
    isCalibrated: Boolean,
    modifier: Modifier = Modifier
) {
    val maxScaleG = 1.5f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(CockpitCardBg, RoundedCornerShape(14.dp))
            .border(1.dp, CockpitBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "G-METER (DINÁMICA)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                Text(
                    text = if (isCalibrated) "CALIBRADO" else "AUTO-ESTIMADO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCalibrated) NeonGreen else NeonAmber
                )
            }

            // Diagrama circular de Kamm adaptativo al espacio disponible
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val diameter = minOf(maxWidth, maxHeight).coerceAtLeast(20.dp)

                Canvas(
                    modifier = Modifier.size(diameter)
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = (size.width / 2f - 4.dp.toPx()).coerceAtLeast(4f)

                    val r05 = maxRadius * (0.5f / maxScaleG)
                    val r10 = maxRadius * (1.0f / maxScaleG)
                    val r15 = maxRadius

                    drawCircle(color = Color(0xFF1E2833), radius = r05, center = center, style = Stroke(width = 1.dp.toPx()))
                    drawCircle(color = Color(0xFF263238), radius = r10, center = center, style = Stroke(width = 1.5.dp.toPx()))
                    drawCircle(color = Color(0xFF37474F), radius = r15, center = center, style = Stroke(width = 2.dp.toPx()))

                    drawLine(color = Color(0xFF263238), start = Offset(center.x, center.y - maxRadius), end = Offset(center.x, center.y + maxRadius), strokeWidth = 1.dp.toPx())
                    drawLine(color = Color(0xFF263238), start = Offset(center.x - maxRadius, center.y), end = Offset(center.x + maxRadius, center.y), strokeWidth = 1.dp.toPx())

                    val clampedLat = (lateralG / maxScaleG).coerceIn(-1f, 1f)
                    val clampedLong = (longitudinalG / maxScaleG).coerceIn(-1f, 1f)

                    val dotX = center.x + clampedLat * maxRadius
                    val dotY = center.y - clampedLong * maxRadius

                    val dotRadius = (diameter.toPx() * 0.05f).coerceIn(3.dp.toPx(), 7.dp.toPx())
                    val haloRadius = dotRadius * 2f

                    drawCircle(color = NeonCyan.copy(alpha = 0.35f), radius = haloRadius, center = Offset(dotX, dotY))
                    drawCircle(color = NeonCyan, radius = dotRadius, center = Offset(dotX, dotY))
                }
            }

            // Lecturas numéricas horizontales debajo del círculo: G Lateral a la izq, G Longitudinal a la der
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "G LATERAL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Text(
                        text = String.format(Locale.US, "%+.2f G", lateralG),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (lateralG >= 0f) NeonCyan else NeonAmber
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "G LONGITUDINAL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Text(
                        text = String.format(Locale.US, "%+.2f G", longitudinalG),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (longitudinalG >= 0f) NeonGreen else NeonRed
                    )
                }
            }
        }
    }
}
