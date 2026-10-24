package com.telemetria.app.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.telemetria.app.core.obd.model.ObdConnectionState
import com.telemetria.app.core.obd.scanner.DiscoveredBleDevice
import com.telemetria.app.ui.theme.CockpitBorder
import com.telemetria.app.ui.theme.CockpitCardBg
import com.telemetria.app.ui.theme.CockpitDarkSurface
import com.telemetria.app.ui.theme.NeonAmber
import com.telemetria.app.ui.theme.NeonCyan
import com.telemetria.app.ui.theme.NeonGreen
import com.telemetria.app.ui.theme.NeonRed
import com.telemetria.app.ui.theme.TextMuted
import com.telemetria.app.ui.theme.TextPrimary
import com.telemetria.app.ui.theme.TextSecondary

@Composable
fun ObdConnectDialog(
    connectionState: ObdConnectionState,
    discoveredDevices: List<DiscoveredBleDevice>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CockpitDarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CockpitBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "CONEXIÓN OBD-II BLE",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary)
                    }
                }

                // Estado actual
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CockpitCardBg, RoundedCornerShape(10.dp))
                        .border(1.dp, CockpitBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    when (connectionState) {
                        is ObdConnectionState.Disconnected -> {
                            Text("Desconectado. Pulsa 'Escanear' para buscar tu Veepeak BLE.", fontSize = 12.sp, color = TextMuted)
                        }
                        is ObdConnectionState.Scanning -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonCyan, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.size(10.dp))
                                Text("Buscando dispositivos BLE...", fontSize = 12.sp, color = NeonCyan)
                            }
                        }
                        is ObdConnectionState.Connecting -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonAmber, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.size(10.dp))
                                Text("Conectando con ${connectionState.deviceName}...", fontSize = 12.sp, color = NeonAmber)
                            }
                        }
                        is ObdConnectionState.DiscoveringServices -> {
                            Text(connectionState.info, fontSize = 12.sp, color = NeonAmber)
                        }
                        is ObdConnectionState.InitializingElm327 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonGreen, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.size(10.dp))
                                Text("Inicializando comandos AT y detectando ECU...", fontSize = 12.sp, color = NeonGreen)
                            }
                        }
                        is ObdConnectionState.Connected -> {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.BluetoothConnected, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("CONECTADO A LA ECU", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                                }
                                Text("Protocolo: ${connectionState.protocolName}", fontSize = 11.sp, color = TextSecondary)
                                Text("PIDs descubiertos: ${connectionState.supportedPidsCount}", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                        is ObdConnectionState.Error -> {
                            Text("Error: ${connectionState.message}", fontSize = 12.sp, color = NeonRed)
                        }
                    }
                }

                // Lista de dispositivos encontrados
                Text(
                    text = "DISPOSITIVOS DISPONIBLES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                if (discoveredDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No se han detectado dispositivos aún.", fontSize = 12.sp, color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(discoveredDevices, key = { it.address }) { dev ->
                            val isVeepeak = dev.name.contains("Veepeak", ignoreCase = true) ||
                                    dev.name.contains("OBD", ignoreCase = true) ||
                                    dev.name.contains("IOS-VLINK", ignoreCase = true)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isVeepeak) Color(0xFF142C33) else CockpitCardBg, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isVeepeak) NeonCyan.copy(alpha = 0.6f) else CockpitBorder, RoundedCornerShape(8.dp))
                                    .clickable { onConnectDevice(dev.address) }
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = dev.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isVeepeak) NeonCyan else TextPrimary
                                        )
                                        Text(
                                            text = dev.address,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextSecondary
                                        )
                                    }
                                    Text(
                                        text = "${dev.rssi} dBm",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (connectionState is ObdConnectionState.Connected) {
                        Button(
                            onClick = onDisconnect,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("DESCONECTAR", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        if (connectionState is ObdConnectionState.Scanning) {
                            OutlinedButton(
                                onClick = onStopScan,
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("DETENER", fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        } else {
                            Button(
                                onClick = onStartScan,
                                modifier = Modifier.weight(1f).height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("ESCANEAR", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
