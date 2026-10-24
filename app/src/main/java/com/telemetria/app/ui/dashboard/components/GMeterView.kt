package com.telemetria.app.ui.dashboard.components

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telemetria.app.ui.theme.CockpitBorder
import com.telemetria.app.ui.theme.CockpitCardBg
import com.telemetria.app.ui.theme.NeonAmber
import com.telemetria.app.ui.theme.NeonCyan
import com.telemetria.app.ui.theme.NeonGreen
import com.telemetria.app.ui.theme.NeonRed
import com.telemetria.app.ui.theme.TextMuted
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
            .background(CockpitCardBg, RoundedCornerShape(12.dp))
            .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Diagrama circular de Kamm (Círculo de tracción / fuerzas G)
                Canvas(
                    modifier = Modifier.size(130.dp)
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.width / 2f - 6.dp.toPx()

                    // Anillos concéntricos: 0.5G, 1.0G, 1.5G
                    val r05 = maxRadius * (0.5f / maxScaleG)
                    val r10 = maxRadius * (1.0f / maxScaleG)
                    val r15 = maxRadius

                    drawCircle(color = Color(0xFF1E2833), radius = r05, center = center, style = Stroke(width = 1.dp.toPx()))
                    drawCircle(color = Color(0xFF263238), radius = r10, center = center, style = Stroke(width = 1.5.dp.toPx()))
                    drawCircle(color = Color(0xFF37474F), radius = r15, center = center, style = Stroke(width = 2.dp.toPx()))

                    // Ejes cruzados
                    drawLine(color = Color(0xFF263238), start = Offset(center.x, center.y - maxRadius), end = Offset(center.x, center.y + maxRadius), strokeWidth = 1.dp.toPx())
                    drawLine(color = Color(0xFF263238), start = Offset(center.x - maxRadius, center.y), end = Offset(center.x + maxRadius, center.y), strokeWidth = 1.dp.toPx())

                    // Posición actual del punto G
                    // En pantalla: X aumenta a la derecha (lateralG positivo), Y aumenta hacia abajo (frenada hacia abajo, aceleración hacia arriba)
                    val clampedLat = (lateralG / maxScaleG).coerceIn(-1f, 1f)
                    val clampedLong = (longitudinalG / maxScaleG).coerceIn(-1f, 1f)

                    val dotX = center.x + clampedLat * maxRadius
                    val dotY = center.y - clampedLong * maxRadius // Menos porque en pantalla Y crece hacia abajo

                    // Halo exterior y punto central
                    drawCircle(color = NeonCyan.copy(alpha = 0.35f), radius = 10.dp.toPx(), center = Offset(dotX, dotY))
                    drawCircle(color = NeonCyan, radius = 5.dp.toPx(), center = Offset(dotX, dotY))
                }

                // Lecturas numéricas
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        Text(
                            text = "G LATERAL (CURVA)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Text(
                            text = String.format(Locale.US, "%+.2f G", lateralG),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (lateralG >= 0f) NeonCyan else NeonAmber
                        )
                    }

                    Column {
                        Text(
                            text = "G LONGITUDINAL (ACEL/FRENO)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Text(
                            text = String.format(Locale.US, "%+.2f G", longitudinalG),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (longitudinalG >= 0f) NeonGreen else NeonRed
                        )
                    }

                    if (gpsDerivedLongG != null) {
                        Column {
                            Text(
                                text = "G LONG (DERIVADA GPS dv/dt)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Normal,
                                color = TextMuted
                            )
                            Text(
                                text = String.format(Locale.US, "%+.2f G", gpsDerivedLongG),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

