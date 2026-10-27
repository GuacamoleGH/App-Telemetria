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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telemetria.app.ui.theme.CockpitBorder
import com.telemetria.app.ui.theme.CockpitCardBg
import com.telemetria.app.ui.theme.TextPrimary
import com.telemetria.app.ui.theme.TextSecondary

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String? = null,
    accentColor: Color = TextPrimary,
    titleFontSize: TextUnit = 12.sp,
    valueFontSize: TextUnit = 28.sp,
    unitFontSize: TextUnit = 13.sp,
    verticalPadding: Dp = 10.dp,
    horizontalPadding: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(CockpitCardBg, RoundedCornerShape(12.dp))
            .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
            .padding(vertical = verticalPadding, horizontal = horizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title.uppercase(),
                fontSize = titleFontSize,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    fontSize = valueFontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = accentColor,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                if (unit != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        fontSize = unitFontSize,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 2.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
