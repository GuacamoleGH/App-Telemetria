package com.telemetria.app.ui.dashboard.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@Composable
fun RpmBar(
    rpm: Int?,
    maxRpm: Int = 7500,
    redlineRpm: Int = 6500,
    shiftLightRpm: Int = 6200,
    modifier: Modifier = Modifier
) {
    val totalSegments = 18
    val currentRpm = rpm ?: 0
    val isConnected = rpm != null
    val effectiveMax = maxRpm.coerceAtLeast(1000)
    val effectiveRedline = redlineRpm.coerceIn(500, effectiveMax)
    val effectiveShift = shiftLightRpm.coerceIn(500, effectiveMax)

    val isShiftPoint = isConnected && currentRpm >= effectiveShift

    // Animación estroboscópica de luces de cambio de competición (Shift Lights Strobe)
    val infiniteTransition = rememberInfiniteTransition(label = "shiftLight")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(110, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flash"
    )

    val activeSegments = if (isConnected) {
        ((currentRpm.toFloat() / effectiveMax.toFloat()) * totalSegments).toInt().coerceIn(0, totalSegments)
    } else {
        0
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(CockpitCardBg, RoundedCornerShape(12.dp))
            .border(
                width = if (isShiftPoint) 1.5.dp else 1.dp,
                color = if (isShiftPoint) NeonRed.copy(alpha = flashAlpha) else CockpitBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TACÓMETRO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )

                    // Aviso estroboscópico de cambio de marcha tipo GT3 / F1
                    if (isShiftPoint) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    color = NeonRed.copy(alpha = flashAlpha),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "⚡ SHIFT",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }

                Text(
                    text = if (isConnected) "$currentRpm RPM" else "OBD-II NO CONECTADO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = when {
                        !isConnected -> TextMuted
                        isShiftPoint -> NeonRed
                        currentRpm >= effectiveRedline -> NeonRed
                        else -> NeonGreen
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Barra secuencial de LEDs estilo competición
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (i in 0 until totalSegments) {
                    val isActive = i < activeSegments
                    val segmentRpm = ((i + 1).toFloat() / totalSegments.toFloat()) * effectiveMax

                    val segmentColor = when {
                        !isConnected -> Color(0xFF1E2833)
                        isShiftPoint -> {
                            // Cuando se alcanza el régimen de cambio, los LEDs parpadean al unísono
                            if (isActive) {
                                if (i >= (totalSegments * 0.75f)) {
                                    NeonCyan.copy(alpha = flashAlpha)
                                } else {
                                    NeonRed.copy(alpha = flashAlpha)
                                }
                            } else {
                                Color(0xFF3B1419)
                            }
                        }
                        segmentRpm < (effectiveRedline * 0.75f) -> {
                            // Zona verde: régimen de potencia progresivo
                            if (isActive) NeonGreen else Color(0xFF122E1F)
                        }
                        segmentRpm < effectiveRedline -> {
                            // Zona ámbar: aproximándose a la zona de par máximo y corte
                            if (isActive) NeonAmber else Color(0xFF332910)
                        }
                        else -> {
                            // Zona roja (Redline): corte del motor
                            if (isActive) NeonRed else Color(0xFF3B1419)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(segmentColor)
                    )
                }
            }
        }
    }
}
