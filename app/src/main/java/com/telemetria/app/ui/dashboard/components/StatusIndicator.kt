package com.telemetria.app.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
fun StatusIndicatorRow(
    gpsFix: Boolean,
    gpsAccuracyMeters: Float?,
    sampleRateHz: Float,
    obdConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // GPS Indicator
        val gpsColor = if (gpsFix) NeonGreen else NeonRed
        val gpsLabel = if (gpsFix) {
            val acc = gpsAccuracyMeters?.let { "±${String.format(Locale.US, "%.1f", it)}m" } ?: "OK"
            "GPS $acc"
        } else {
            "GPS BUSCANDO"
        }
        StatusBadge(label = gpsLabel, activeColor = gpsColor, isActive = gpsFix)

        // Sample rate indicator
        if (gpsFix && sampleRateHz > 0f) {
            StatusBadge(
                label = "${String.format(Locale.US, "%.1f", sampleRateHz)} Hz",
                activeColor = NeonCyan,
                isActive = true
            )
        }

        // OBD Status
        StatusBadge(
            label = if (obdConnected) "OBD BLE" else "NO OBD",
            activeColor = if (obdConnected) NeonCyan else TextMuted,
            isActive = obdConnected
        )
    }
}

@Composable
fun StatusBadge(
    label: String,
    activeColor: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(CockpitCardBg, RoundedCornerShape(8.dp))
            .border(1.dp, CockpitBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(activeColor, CircleShape)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) activeColor else TextSecondary
            )
        }
    }
}

