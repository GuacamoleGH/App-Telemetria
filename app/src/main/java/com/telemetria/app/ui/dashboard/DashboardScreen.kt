package com.telemetria.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.telemetria.app.ui.dashboard.components.MetricCard
import com.telemetria.app.ui.dashboard.components.RpmBar
import com.telemetria.app.ui.dashboard.components.SpeedGauge
import com.telemetria.app.ui.dashboard.components.StatusIndicatorRow
import com.telemetria.app.ui.theme.CockpitBlack
import com.telemetria.app.ui.theme.NeonAmber
import com.telemetria.app.ui.theme.NeonCyan
import com.telemetria.app.ui.theme.NeonGreen
import com.telemetria.app.ui.theme.NeonRed
import com.telemetria.app.ui.theme.TextPrimary

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToHistory: () -> Unit,
    onSessionFinished: (String) -> Unit
) {
    val liveState by viewModel.liveState.collectAsState()
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Status indicators + History Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIndicatorRow(
                    gpsFix = liveState.gpsFix,
                    gpsAccuracyMeters = liveState.gpsAccuracyMeters,
                    sampleRateHz = liveState.sampleRateHz,
                    obdConnected = false // En F6 se conectará con BLE
                )

                IconButton(onClick = onNavigateToHistory) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Historial de Sesiones",
                        tint = NeonCyan
                    )
                }
            }

            // Tacómetro estilo MyChron
            RpmBar(rpm = null)

            // Velocímetro Central
            SpeedGauge(
                speedKmh = liveState.currentSpeedKmh,
                isGpsValid = liveState.gpsFix
            )

            // Grid de Métricas (2 x 2)
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

            Spacer(modifier = Modifier.height(10.dp))

            // Botones de Control de Sesión
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
                            text = "INICIAR GRABACIÓN",
                            fontSize = 16.sp,
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

