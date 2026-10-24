package com.telemetria.app.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(CockpitCardBg, RoundedCornerShape(12.dp))
            .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Column {
            Text(
                text = title.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = accentColor,
                    maxLines = 1
                )
                if (unit != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}
