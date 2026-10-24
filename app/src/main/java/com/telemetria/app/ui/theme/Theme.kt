package com.telemetria.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    secondary = NeonCyan,
    tertiary = NeonAmber,
    background = CockpitBlack,
    surface = CockpitDarkSurface,
    onPrimary = CockpitBlack,
    onSecondary = CockpitBlack,
    onTertiary = CockpitBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun TelemetriaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

