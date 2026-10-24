package com.telemetria.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telemetria.app.core.util.FormatUtils
import com.telemetria.app.ui.dashboard.components.MetricCard
import com.telemetria.app.ui.history.components.GForceGraph
import com.telemetria.app.ui.history.components.MapViewOsm
import com.telemetria.app.ui.history.components.SpeedElevationGraph
import com.telemetria.app.ui.theme.CockpitBlack
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
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    viewModel: HistoryViewModel,
    onBack: () -> Unit
) {
    val session by viewModel.selectedSession.collectAsState()
    val points by viewModel.selectedPoints.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    LaunchedEffect(sessionId) {
        viewModel.loadSessionDetails(sessionId)
    }

    LaunchedEffect(Unit) {
        viewModel.exportMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = session?.title ?: "DETALLE DE SESIÓN",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CockpitDarkSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = CockpitBlack
    ) { innerPadding ->
        val currentSession = session
        if (currentSession == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Cargando sesión...", color = TextPrimary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Mapa interactivo Osmdroid con el track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    MapViewOsm(points = points)
                }

                // Grid de métricas consolidadas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Distancia",
                        value = FormatUtils.formatDistanceValue(currentSession.totalDistanceMeters),
                        unit = "km",
                        accentColor = NeonCyan,
                        modifier = Modifier.weight(1f)
                    )

                    MetricCard(
                        title = "Duración",
                        value = FormatUtils.formatDuration(currentSession.durationSeconds),
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
                        value = FormatUtils.formatSpeed(currentSession.maxSpeedKmh),
                        unit = "km/h",
                        accentColor = NeonRed,
                        modifier = Modifier.weight(1f)
                    )

                    MetricCard(
                        title = "Vel. Media",
                        value = FormatUtils.formatSpeed(currentSession.avgSpeedKmh),
                        unit = "km/h",
                        accentColor = NeonAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Gráfica de perfil de velocidad (Canvas nativo)
                SpeedElevationGraph(points = points)

                // Gráfica de Fuerzas G dinámicas (Fase 3: Lateral y Longitudinal)
                GForceGraph(points = points)

                // Tarjeta de Vídeo y Sincronización (Fase 4)
                if (currentSession.videoFilePath != null) {
                    val videoFile = java.io.File(currentSession.videoFilePath)
                    val exists = videoFile.exists()
                    val sizeMb = if (exists) videoFile.length() / (1024f * 1024f) else 0f
                    val syncOffsetMs = if (currentSession.videoStartMonotonicNanos != null && currentSession.startMonotonicNanos > 0) {
                        (currentSession.videoStartMonotonicNanos - currentSession.startMonotonicNanos) / 1_000_000L
                    } else 0L

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CockpitCardBg, RoundedCornerShape(12.dp))
                            .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "VÍDEO ASOCIADO (CAMERAX)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary
                                )
                                Text(
                                    text = if (exists) String.format(java.util.Locale.US, "%.1f MB", sizeMb) else "NO ENCONTRADO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (exists) NeonCyan else NeonRed
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = videoFile.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Sincronización monotónica: offset $syncOffsetMs ms respecto a muestra inicial",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Botón principal de exportación en paquete ZIP (Vídeo + CSV + GPX + JSON + Doc)
                Button(
                    onClick = { viewModel.exportZip(currentSession) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = CockpitBlack
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.padding(start = 6.dp))
                    Text("EXPORTAR PAQUETE ZIP (VÍDEO + DATOS)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // Botones de Exportación individual
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.exportGpx(currentSession) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CockpitDarkSurface,
                            contentColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 4.dp))
                        Text("GPX (Strava)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { viewModel.exportCsv(currentSession) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CockpitDarkSurface,
                            contentColor = NeonGreen
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 4.dp))
                        Text("CSV (RaceRender)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
