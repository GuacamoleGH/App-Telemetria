package com.telemetria.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telemetria.app.core.model.SessionStatus
import com.telemetria.app.core.util.FormatUtils
import com.telemetria.app.ui.dashboard.components.CameraPreviewView
import com.telemetria.app.ui.dashboard.components.GMeterView
import com.telemetria.app.ui.dashboard.components.MetricCard
import com.telemetria.app.ui.dashboard.components.ObdConnectDialog
import com.telemetria.app.ui.dashboard.components.RpmBar
import com.telemetria.app.ui.dashboard.components.SpeedGauge
import com.telemetria.app.ui.dashboard.components.StatusIndicatorRow
import com.telemetria.app.ui.theme.CockpitBlack
import com.telemetria.app.ui.theme.NeonAmber
import com.telemetria.app.ui.theme.NeonCyan
import com.telemetria.app.ui.theme.NeonGreen
import com.telemetria.app.ui.theme.NeonRed
import com.telemetria.app.ui.theme.TextMuted
import com.telemetria.app.ui.theme.TextPrimary

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToHistory: () -> Unit,
    onSessionFinished: (String) -> Unit
) {
    val liveState by viewModel.liveState.collectAsState()
    val isVideoEnabled by viewModel.isVideoEnabled.collectAsState()
    val isObdDialogVisible by viewModel.isObdDialogVisible.collectAsState()
    val obdConnectionState by viewModel.obdConnectionState.collectAsState()
    val discoveredBleDevices by viewModel.discoveredBleDevices.collectAsState()
    val scrollState = rememberScrollState()

    // Diálogo de conexión OBD-II BLE
    if (isObdDialogVisible) {
        ObdConnectDialog(
            connectionState = obdConnectionState,
            discoveredDevices = discoveredBleDevices,
            onStartScan = { viewModel.startObdScan() },
            onStopScan = { viewModel.stopObdScan() },
            onConnectDevice = { addr -> viewModel.connectObd(addr) },
            onDisconnect = { viewModel.disconnectObd() },
            onDismiss = { viewModel.dismissObdDialog() }
        )
    }

    Scaffold(
        containerColor = CockpitBlack,
        bottomBar = {
            Surface(
                color = CockpitBlack,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    when (liveState.status) {
                        SessionStatus.COMPLETED -> {
                            Button(
                                onClick = { viewModel.onStartClicked() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonGreen,
                                    contentColor = CockpitBlack
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isVideoEnabled) "INICIAR VÍDEO + TELEMETRÍA" else "INICIAR TELEMETRÍA",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        SessionStatus.ACTIVE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.onPauseClicked() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonAmber,
                                        contentColor = CockpitBlack
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("PAUSAR", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        viewModel.onStopClicked { saved ->
                                            onSessionFinished(saved.id)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonRed,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("FINALIZAR", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        SessionStatus.PAUSED -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.onResumeClicked() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonGreen,
                                        contentColor = CockpitBlack
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("REANUDAR", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        viewModel.onStopClicked { saved ->
                                            onSessionFinished(saved.id)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonRed,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("FINALIZAR", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Status indicators + Controles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIndicatorRow(
                    gpsFix = liveState.gpsFix,
                    gpsAccuracyMeters = liveState.gpsAccuracyMeters,
                    sampleRateHz = liveState.sampleRateHz,
                    obdConnected = liveState.isObdConnected,
                    obdHz = liveState.obdRealHz,
                    onObdClick = { viewModel.showObdDialog() }
                )

                Row {
                    // Botón OBD Bluetooth
                    IconButton(onClick = { viewModel.showObdDialog() }) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Conectar Veepeak OBD-II",
                            tint = if (liveState.isObdConnected) NeonCyan else TextMuted
                        )
                    }

                    // Toggle Vídeo CameraX
                    IconButton(onClick = { viewModel.toggleVideoEnabled() }) {
                        Icon(
                            imageVector = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Activar / Desactivar Grabación de Vídeo",
                            tint = if (isVideoEnabled) NeonCyan else TextMuted
                        )
                    }

                    // Calibración de orientación en soporte
                    IconButton(
                        onClick = { viewModel.onCalibrateSensorsClicked() },
                        enabled = !liveState.isCalibrating
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "Calibrar posición de sensores",
                            tint = if (liveState.isCalibrating) NeonAmber else if (liveState.isSensorCalibrated) NeonGreen else TextPrimary
                        )
                    }

                    // Historial
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Historial de Sesiones",
                            tint = NeonCyan
                        )
                    }
                }
            }

            // Visor de Cámara CameraX
            AnimatedVisibility(visible = isVideoEnabled) {
                CameraPreviewView(
                    videoRecordingManager = viewModel.videoRecordingManager,
                    isRecording = liveState.isVideoRecording,
                    videoDurationSec = liveState.videoDurationSec,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }

            // Tacómetro estilo MyChron 5 (conectado a RPM reales de OBD)
            RpmBar(rpm = liveState.engineRpm)

            // Velocímetro Central
            SpeedGauge(
                speedKmh = liveState.currentSpeedKmh,
                isGpsValid = liveState.gpsFix
            )

            // G-Meter Dinámico
            GMeterView(
                lateralG = liveState.lateralG,
                longitudinalG = liveState.longitudinalG,
                gpsDerivedLongG = liveState.gpsDerivedLongitudinalG,
                isCalibrated = liveState.isSensorCalibrated
            )

            // Grid de Métricas Primarias
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Distancia",
                    value = FormatUtils.formatDistanceKm(liveState.distanceMeters),
                    accentColor = NeonCyan,
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    title = "Tiempo",
                    value = FormatUtils.formatDuration(liveState.elapsedSeconds),
                    accentColor = NeonGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Vel. Máxima",
                    value = FormatUtils.formatSpeed(liveState.maxSpeedKmh),
                    unit = "km/h",
                    accentColor = NeonRed,
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    title = "Vel. Media",
                    value = FormatUtils.formatSpeed(liveState.avgSpeedKmh),
                    unit = "km/h",
                    accentColor = NeonAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            // Grid de Métricas de Motor OBD (si hay conexión con la ECU)
            if (liveState.isObdConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Refrigerante",
                        value = liveState.coolantTempC?.let { "$it°C" } ?: "--",
                        accentColor = if ((liveState.coolantTempC ?: 0) > 105) NeonRed else NeonCyan,
                        modifier = Modifier.weight(1f)
                    )

                    MetricCard(
                        title = "Pedal Acel.",
                        value = liveState.throttlePercent?.let { "${it.toInt()}%" } ?: "--",
                        accentColor = NeonGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
