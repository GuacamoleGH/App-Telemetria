package com.telemetria.app.ui.dashboard.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telemetria.app.core.util.FormatUtils
import com.telemetria.app.ui.theme.CockpitBorder
import com.telemetria.app.ui.theme.CockpitCardBg
import com.telemetria.app.ui.theme.NeonAmber
import com.telemetria.app.ui.theme.NeonCyan
import com.telemetria.app.ui.theme.NeonGreen
import com.telemetria.app.ui.theme.TextMuted
import com.telemetria.app.ui.theme.TextSecondary

@Composable
fun SpeedGauge(
    speedKmh: Float,
    isGpsValid: Boolean,
    gear: String? = null,
    modifier: Modifier = Modifier,
    speedFontSize: TextUnit = 68.sp,
    unitFontSize: TextUnit = 16.sp,
    verticalPadding: Dp = 10.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(CockpitCardBg, RoundedCornerShape(16.dp))
            .border(1.dp, CockpitBorder, RoundedCornerShape(16.dp))
            .padding(vertical = verticalPadding, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (gear != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 10.dp, top = 6.dp)
                    .background(Color(0xFF141922), RoundedCornerShape(8.dp))
                    .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "GEAR ",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Text(
                        text = gear,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = if (gear == "N") NeonAmber else NeonCyan
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                val speedDisplay = if (isGpsValid) FormatUtils.formatSpeed(speedKmh) else "--.-"
                val speedColor = if (isGpsValid) NeonGreen else TextMuted

                Text(
                    text = speedDisplay,
                    fontSize = speedFontSize,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = speedColor,
                    lineHeight = speedFontSize,
                    maxLines = 1,
                    softWrap = false
                )

                Text(
                    text = "KM/H",
                    fontSize = unitFontSize,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 5.dp, bottom = (unitFontSize.value * 0.45f).dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isGpsValid) "ORIGEN: GPS REAL" else "SIN SEÑAL GPS",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isGpsValid) NeonGreen.copy(alpha = 0.8f) else Color(0xFFFF5252),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
