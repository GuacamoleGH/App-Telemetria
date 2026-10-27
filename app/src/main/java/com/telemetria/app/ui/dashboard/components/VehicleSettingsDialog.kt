package com.telemetria.app.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.window.DialogProperties
import com.telemetria.app.core.model.EngineAspiration
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

@Composable
fun VehicleSettingsDialog(
    currentConfig: VehicleConfig,
    onSaveConfig: (VehicleConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var maxRpm by remember { mutableIntStateOf(currentConfig.maxRpm) }
    var redlineRpm by remember { mutableIntStateOf(currentConfig.redlineRpm) }
    var aspiration by remember { mutableStateOf(currentConfig.aspiration) }
    // Aviso de cambio siempre fijado a 500 RPM antes de la línea roja
    val shiftLightRpm = (redlineRpm - VehicleConfig.SHIFT_LIGHT_OFFSET_RPM).coerceAtLeast(1000)

    // Slider interactivo para previsualizar el tacómetro y las luces de cambio
    var previewRpm by remember { mutableFloatStateOf(shiftLightRpm.toFloat()) }

    val presets = remember {
        listOf(
            VehiclePreset("Diésel (5.5k)", 5500, 4800, 4300),
            VehiclePreset("Estándar (7.0k)", 7000, 6200, 5700),
            VehiclePreset("Gasolina (8.0k)", 8000, 7200, 6700),
            VehiclePreset("VTEC / GT (9.0k)", 9000, 8400, 7900),
            VehiclePreset("Moto / Kart (11k)", 11000, 10000, 9500)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CockpitCardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, CockpitBorder),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Cabecera compacta
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
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AJUSTES DEL VEHÍCULO",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "Configura tacómetro, corte y shift lights de carreras.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                // Perfiles rápidos en fila horizontal deslizable
                Text(
                    text = "PERFILES RÁPIDOS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        val isSelected = maxRpm == preset.maxRpm && redlineRpm == preset.redlineRpm
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                maxRpm = preset.maxRpm
                                redlineRpm = preset.redlineRpm
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

                Spacer(modifier = Modifier.height(2.dp))

                // 1. RPM Máximas de escala
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
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
                        if (previewRpm > maxRpm) previewRpm = maxRpm.toFloat()
                    },
                    valueRange = 4000f..12000f,
                    steps = 31,
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
                    modifier = Modifier.height(34.dp)
                )

                // 2. Inicio de Línea Roja (Redline)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LÍNEA ROJA (CORTE)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("$redlineRpm RPM", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = NeonRed)
                }
                Slider(
                    value = redlineRpm.toFloat(),
                    onValueChange = { newVal ->
                        val rounded = (newVal / 100f).toInt() * 100
                        redlineRpm = rounded.coerceAtMost(maxRpm)
                    },
                    valueRange = 3000f..maxRpm.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = NeonRed, activeTrackColor = NeonRed),
                    modifier = Modifier.height(34.dp)
                )

                // 3. Aviso de Cambio (Shift Light) - Siempre 500 RPM antes de la línea roja
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141A22), RoundedCornerShape(8.dp))
                        .border(1.dp, CockpitBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AVISO DE CAMBIO (SHIFT LIGHT)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Automático (500 RPM antes del corte)",
                            fontSize = 9.sp,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = "$shiftLightRpm RPM",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = NeonAmber
                    )
                }

                // Vista Previa Interactiva en tiempo real del Tacómetro
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VISTA PREVIA EN VIVO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Text(
                        text = "${previewRpm.toInt()} RPM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = NeonGreen
                    )
                }

                RpmBar(
                    rpm = previewRpm.toInt(),
                    maxRpm = maxRpm,
                    redlineRpm = redlineRpm,
                    shiftLightRpm = shiftLightRpm
                )

                Slider(
                    value = previewRpm.coerceIn(0f, maxRpm.toFloat()),
                    onValueChange = { previewRpm = it },
                    valueRange = 0f..maxRpm.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = NeonGreen, activeTrackColor = NeonGreen),
                    modifier = Modifier.height(34.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Sección: Tipo de Motor (Atmosférico vs Turbo)
                Text(
                    text = "TIPO DE MOTOR / ADMISIÓN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = aspiration == EngineAspiration.NATURALLY_ASPIRATED,
                        onClick = { aspiration = EngineAspiration.NATURALLY_ASPIRATED },
                        label = { Text("🍃 Atmosférico (NA)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = aspiration == EngineAspiration.NATURALLY_ASPIRATED,
                            borderColor = if (aspiration == EngineAspiration.NATURALLY_ASPIRATED) NeonCyan else CockpitBorder
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = aspiration == EngineAspiration.TURBOCHARGED,
                        onClick = { aspiration = EngineAspiration.TURBOCHARGED },
                        label = { Text("⚡ Turbo / Soplado", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonAmber.copy(alpha = 0.2f),
                            selectedLabelColor = NeonAmber
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = aspiration == EngineAspiration.TURBOCHARGED,
                            borderColor = if (aspiration == EngineAspiration.TURBOCHARGED) NeonAmber else CockpitBorder
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = if (aspiration == EngineAspiration.NATURALLY_ASPIRATED) {
                        "En coches atmosféricos se oculta el manómetro de turbo (que marca valores negativos de vacío) y se muestra Carga o Consumo de motor."
                    } else {
                        "Muestra la presión manométrica de soplado de turbo en bar (sobrepresión sobre la atmosférica)."
                    },
                    fontSize = 9.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Botón de Guardar
                Button(
                    onClick = {
                        val config = VehicleConfig(
                            maxRpm = maxRpm,
                            redlineRpm = redlineRpm.coerceAtMost(maxRpm),
                            shiftLightRpm = shiftLightRpm.coerceAtMost(maxRpm),
                            aspiration = aspiration
                        )
                        onSaveConfig(config)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
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

