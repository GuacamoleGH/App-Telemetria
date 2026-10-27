package com.telemetria.app.ui.history

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.telemetria.app.core.database.entity.SessionEntity
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
    val zipProgress by viewModel.zipExportProgress.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var postZipPromptSession by remember { mutableStateOf<SessionEntity?>(null) }
    var showManualDeleteVideoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        viewModel.loadSessionDetails(sessionId)
    }

    LaunchedEffect(Unit) {
        viewModel.exportMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.promptDeleteRawVideoAfterZip.collectLatest { exportedSession ->
            postZipPromptSession = exportedSession
        }
    }

    if (zipProgress.isExporting) {
        Dialog(
            onDismissRequest = { /* Bloqueado hasta terminar la exportación */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, CockpitBorder, RoundedCornerShape(16.dp)),
                color = CockpitDarkSurface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "EMPAQUETANDO SESIÓN",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = zipProgress.sessionTitle.ifBlank { "Exportando paquete ZIP completo..." },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    LinearProgressIndicator(
                        progress = { zipProgress.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = NeonGreen,
                        trackColor = CockpitCardBg
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = zipProgress.statusText,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${(zipProgress.progress * 100).toInt()}%",
                            color = NeonGreen,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "No cierres la aplicación durante el proceso.",
                        color = TextMuted,
                        fontSize = 10.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }

    if (postZipPromptSession != null) {
        val target = postZipPromptSession!!
        val rawSizeMb = viewModel.getRawVideoSizeMb(target)
        AlertDialog(
            onDismissRequest = { postZipPromptSession = null },
            title = {
                Text(
                    text = "¡Paquete ZIP exportado!",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "El vídeo y toda la telemetría se han empaquetado de forma segura en:\n\n" +
                            "📁 Download/Telemetria/sesion_${target.id.take(8)}_bundle.zip\n\n" +
                            "¿Deseas borrar el archivo de vídeo original de la memoria interna (Movies/telemetria_videos) para liberar ${String.format(java.util.Locale.US, "%.1f MB", rawSizeMb)} manteniendo el archivo .zip?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        postZipPromptSession = null
                        viewModel.deleteRawVideo(target)
                    }
                ) {
                    Text("BORRAR VÍDEO Y LIBERAR ESPACIO", color = NeonGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { postZipPromptSession = null }) {
                    Text("CONSERVAR AMBOS", color = TextSecondary)
                }
            },
            containerColor = CockpitDarkSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }

    if (showManualDeleteVideoDialog && session != null) {
        val target = session!!
        val rawSizeMb = viewModel.getRawVideoSizeMb(target)
        AlertDialog(
            onDismissRequest = { showManualDeleteVideoDialog = false },
            title = {
                Text(
                    text = "¿Borrar vídeo original?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "El archivo ZIP ya existe en 'Download/Telemetria' con la copia completa del vídeo.\n\n" +
                            "Al borrar el vídeo original de 'telemetria_videos' liberarás ${String.format(java.util.Locale.US, "%.1f MB", rawSizeMb)} de almacenamiento interno. El ZIP permanecerá intacto.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showManualDeleteVideoDialog = false
                        viewModel.deleteRawVideo(target)
                    }
                ) {
                    Text("LIBERAR ESPACIO", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDeleteVideoDialog = false }) {
                    Text("CANCELAR", color = TextSecondary)
                }
            },
            containerColor = CockpitDarkSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }

    if (showDeleteDialog && session != null) {
        val target = session!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "¿Eliminar sesión?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "¿Deseas eliminar '${target.title}'? Se borrarán sus datos de telemetría y los archivos generados (vídeo, paquete .zip, CSV y GPX si existen).",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteSession(target.id) {
                            onBack()
                        }
                    }
                ) {
                    Text("ELIMINAR", color = NeonRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("CANCELAR", color = TextSecondary)
                }
            },
            containerColor = CockpitDarkSurface,
            shape = RoundedCornerShape(12.dp)
        )
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
                actions = {
                    if (session != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar sesión",
                                tint = TextMuted
                            )
                        }
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
                val hasRawVideo = viewModel.doesRawVideoExist(currentSession)
                val hasZip = viewModel.isZipExported(currentSession)
                val zipFile = viewModel.getZipFileForSession(currentSession)
                val rawVideoSizeMb = viewModel.getRawVideoSizeMb(currentSession)
                val zipSizeMb = if (hasZip) zipFile.length() / (1024f * 1024f) else 0f
                val syncOffsetMs = if (currentSession.videoStartMonotonicNanos != null && currentSession.startMonotonicNanos > 0) {
                    (currentSession.videoStartMonotonicNanos - currentSession.startMonotonicNanos) / 1_000_000L
                } else 0L

                if (hasRawVideo || hasZip || currentSession.videoFilePath != null) {
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
                                    text = "VÍDEO DE SESIÓN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary
                                )
                                Text(
                                    text = when {
                                        hasRawVideo && hasZip -> "VÍDEO + COPIA EN ZIP"
                                        hasZip && !hasRawVideo -> "CONSERVADO EN ZIP (LIBERADO)"
                                        hasRawVideo -> String.format(java.util.Locale.US, "%.1f MB", rawVideoSizeMb)
                                        else -> "NO ENCONTRADO"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = when {
                                        hasZip && !hasRawVideo -> NeonGreen
                                        hasRawVideo && hasZip -> NeonCyan
                                        hasRawVideo -> NeonCyan
                                        else -> NeonRed
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (hasZip && !hasRawVideo) {
                                Text(
                                    text = zipFile.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "El archivo de vídeo original ha sido eliminado de 'telemetria_videos' para ahorrar espacio en la memoria interna. El metraje permanece seguro dentro del archivo ZIP en Download/Telemetria (${String.format(java.util.Locale.US, "%.1f MB", zipSizeMb)}).",
                                    fontSize = 12.sp,
                                    color = NeonGreen.copy(alpha = 0.9f)
                                )
                            } else if (hasRawVideo) {
                                val videoName = currentSession.videoFilePath?.let { java.io.File(it).name } ?: "video_${currentSession.id.take(8)}.mp4"
                                Text(
                                    text = videoName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )

                                if (hasZip) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "El vídeo también se encuentra empaquetado de forma segura en Download/Telemetria/${zipFile.name}.",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { showManualDeleteVideoDialog = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CockpitDarkSurface,
                                            contentColor = NeonCyan
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = NeonCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.padding(start = 6.dp))
                                        Text(
                                            text = "BORRAR VÍDEO ORIGINAL (LIBERAR ${String.format(java.util.Locale.US, "%.1f MB", rawVideoSizeMb)})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Para liberar el espacio de este vídeo manteniendo una copia de seguridad segura, expórtalo primero en paquete ZIP.",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

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
                    enabled = !zipProgress.isExporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = CockpitBlack,
                        disabledContainerColor = CockpitCardBg,
                        disabledContentColor = TextMuted
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
                        enabled = !zipProgress.isExporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CockpitDarkSurface,
                            contentColor = NeonCyan,
                            disabledContainerColor = CockpitCardBg,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 4.dp))
                        Text("GPX (Strava)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { viewModel.exportCsv(currentSession) },
                        enabled = !zipProgress.isExporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CockpitDarkSurface,
                            contentColor = NeonGreen,
                            disabledContainerColor = CockpitCardBg,
                            disabledContentColor = TextMuted
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
