package com.telemetria.app.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.telemetria.app.core.model.VehicleConfig
import com.telemetria.app.ui.theme.CockpitBorder
import com.telemetria.app.ui.theme.CockpitCardBg
import com.telemetria.app.ui.theme.NeonAmber
import com.telemetria.app.ui.theme.NeonCyan
import com.telemetria.app.ui.theme.NeonGreen
import com.telemetria.app.ui.theme.NeonRed
import com.telemetria.app.ui.theme.TextMuted
import com.telemetria.app.ui.theme.TextPrimary
import com.telemetria.app.ui.theme.TextSecondary

private data class VehiclePreset(
    val name: String,
    val maxRpm: Int,
    val redlineRpm: Int,
    val shiftRpm: Int
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleSettingsDialog(
    currentConfig: VehicleConfig,
    onSaveConfig: (VehicleConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var maxRpm by remember { mutableIntStateOf(currentConfig.maxRpm) }
    var redlineRpm by remember { mutableIntStateOf(currentConfig.redlineRpm) }
    var shiftLightRpm by remember { mutableIntStateOf(currentConfig.shiftLightRpm) }

    // Slider interactivo para previsualizar el tacómetro y las luces de cambio
    var previewRpm by remember { mutableFloatStateOf(shiftLightRpm.toFloat()) }

    val presets = remember {
        listOf(
            VehiclePreset("Diésel (5.5k)", 5500, 4800, 4500),
            VehiclePreset("Estándar (7.0k)", 7000, 6200, 6000),
            VehiclePreset("Gasolina (8.0k)", 8000, 7200, 7000),
            VehiclePreset("VTEC / GT (9.0k)", 9000, 8400, 8200),
            VehiclePreset("Moto / Kart (11k)", 11000, 10000, 9700)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CockpitCardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, CockpitBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Cabecera con título e icono de cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AJUSTES DEL VEHÍCULO",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                Text(
                    text = "Configura la escala del tacómetro, línea roja y aviso de cambio estilo competición para la app y el vídeo.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                // Presets rápidos de vehículos
                Text(
                    text = "PERFILES RÁPIDOS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEach { preset ->
                        val isSelected = maxRpm == preset.maxRpm && redlineRpm == preset.redlineRpm
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                maxRpm = preset.maxRpm
                                redlineRpm = preset.redlineRpm
                                shiftLightRpm = preset.shiftRpm
                                previewRpm = preset.shiftRpm.toFloat()
                            },
                            label = { Text(preset.name, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan,
                                containerColor = Color(0xFF141A22),
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1. RPM Máximas de escala
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("RPM MÁXIMAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("$maxRpm RPM", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = NeonCyan)
                }
                Slider(
                    value = maxRpm.toFloat(),
                    onValueChange = { newVal ->
                        val rounded = (newVal / 250f).toInt() * 250
                        maxRpm = rounded
                        if (redlineRpm > maxRpm) redlineRpm = maxRpm
                        if (shiftLightRpm > maxRpm) shiftLightRpm = maxRpm
                        if (previewRpm > maxRpm) previewRpm = maxRpm.toFloat()
                    },
                    valueRange = 4000f..12000f,
                    steps = 31,
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 2. Inicio de Línea Roja (Redline)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("LÍNEA ROJA (CORTE)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("$redlineRpm RPM", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = NeonRed)
                }
                Slider(
                    value = redlineRpm.toFloat(),
                    onValueChange = { newVal ->
                        val rounded = (newVal / 100f).toInt() * 100
                        redlineRpm = rounded.coerceAtMost(maxRpm)
                        if (shiftLightRpm > redlineRpm) shiftLightRpm = redlineRpm
                    },
                    valueRange = 3000f..maxRpm.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = NeonRed, activeTrackColor = NeonRed)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 3. Aviso de Cambio (Shift Light)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("AVISO DE CAMBIO (SHIFT LIGHT)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("$shiftLightRpm RPM", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = NeonAmber)
                }
                Slider(
                    value = shiftLightRpm.toFloat(),
                    onValueChange = { newVal ->
                        val rounded = (newVal / 100f).toInt() * 100
                        shiftLightRpm = rounded.coerceAtMost(maxRpm)
                    },
                    valueRange = 2500f..maxRpm.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = NeonAmber, activeTrackColor = NeonAmber)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Vista Previa Interactiva en tiempo real del Tacómetro
                Text(
                    text = "VISTA PREVIA EN VIVO (PRUEBA DE ACELERACIÓN)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                RpmBar(
                    rpm = previewRpm.toInt(),
                    maxRpm = maxRpm,
                    redlineRpm = redlineRpm,
                    shiftLightRpm = shiftLightRpm
                )

                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = previewRpm.coerceIn(0f, maxRpm.toFloat()),
                    onValueChange = { previewRpm = it },
                    valueRange = 0f..maxRpm.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = NeonGreen, activeTrackColor = NeonGreen)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Botón de Guardar
                Button(
                    onClick = {
                        val config = VehicleConfig(
                            maxRpm = maxRpm,
                            redlineRpm = redlineRpm.coerceAtMost(maxRpm),
                            shiftLightRpm = shiftLightRpm.coerceAtMost(maxRpm)
                        )
                        onSaveConfig(config)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "GUARDAR AJUSTES",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

