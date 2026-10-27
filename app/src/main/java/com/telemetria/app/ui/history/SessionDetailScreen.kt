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
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.telemetria.app.core.database.entity.SessionEntity
import com.telemetria.app.core.util.FormatUtils
import java.util.Locale
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

    val detailedStats = remember(points) {
        if (points.isEmpty()) null
        else {
            var maxLatG = 0f
            var maxLatGLeft = 0f
            var maxLatGRight = 0f
            var maxBrakingG = 0f
            var maxAccelG = 0f
            var maxCombinedG = 0f

            var maxRpm: Int? = null
            var totalRpm = 0L
            var rpmCount = 0

            var maxCoolantC: Int? = null
            var avgCoolantSum = 0L
            var coolantCount = 0

            var fullThrottleCount = 0
            var throttleCount = 0
            var maxThrottle = 0f

            var maxBoost: Float? = null
            var maxLoad: Float? = null

            var fuelSum = 0f
            var fuelCount = 0

            var minAlt: Double? = null
            var maxAlt: Double? = null
            var elevationGain = 0.0
            var elevationLoss = 0.0
            var lastValidAlt: Double? = null

            var movingTimeSec = 0L
            var stoppedTimeSec = 0L
            var lastTimeMs: Long? = null

            for (p in points) {
                val lat = p.lateralG
                if (lat != null) {
                    val absLat = kotlin.math.abs(lat)
                    if (absLat > maxLatG) maxLatG = absLat
                    if (lat < 0 && absLat > maxLatGLeft) maxLatGLeft = absLat
                    if (lat > 0 && lat > maxLatGRight) maxLatGRight = lat
                }
                val longG = p.longitudinalG
                if (longG != null) {
                    if (longG < 0 && kotlin.math.abs(longG) > maxBrakingG) maxBrakingG = kotlin.math.abs(longG)
                    if (longG > 0 && longG > maxAccelG) maxAccelG = longG
                }
                if (lat != null && longG != null) {
                    val comb = kotlin.math.sqrt(lat * lat + longG * longG)
                    if (comb > maxCombinedG) maxCombinedG = comb
                }

                val rpm = p.engineRpm
                if (rpm != null && rpm > 0) {
                    if (maxRpm == null || rpm > maxRpm) maxRpm = rpm
                    totalRpm += rpm
                    rpmCount++
                }

                val coolant = p.coolantTempC
                if (coolant != null) {
                    if (maxCoolantC == null || coolant > maxCoolantC) maxCoolantC = coolant
                    avgCoolantSum += coolant
                    coolantCount++
                }

                val th = p.throttlePercent
                if (th != null) {
                    throttleCount++
                    if (th > maxThrottle) maxThrottle = th
                    if (th >= 85f) fullThrottleCount++
                }

                val boost = p.boostPressureBar
                if (boost != null) {
                    if (maxBoost == null || boost > maxBoost) maxBoost = boost
                }

                val load = p.engineLoadPercent
                if (load != null) {
                    if (maxLoad == null || load > maxLoad) maxLoad = load
                }

                val fuel = p.fuelConsumptionL100km
                if (fuel != null && fuel > 0 && fuel < 100f) {
                    fuelSum += fuel
                    fuelCount++
                }

                val alt = p.altitudeMeters
                if (alt != null && alt > -500.0 && alt < 9000.0) {
                    if (minAlt == null || alt < minAlt) minAlt = alt
                    if (maxAlt == null || alt > maxAlt) maxAlt = alt
                    if (lastValidAlt != null) {
                        val dAlt = alt - lastValidAlt
                        if (dAlt > 0.4 && dAlt < 50.0) elevationGain += dAlt
                        else if (dAlt < -0.4 && dAlt > -50.0) elevationLoss += kotlin.math.abs(dAlt)
                    }
                    lastValidAlt = alt
                }

                val curTimeMs = p.timestampMillis
                val prevTime = lastTimeMs
                if (prevTime != null && curTimeMs > prevTime) {
                    val dtSec: Long = (curTimeMs - prevTime) / 1000L
                    if (dtSec in 1L..5L) {
                        val spd = p.gpsSpeedKmh ?: p.obdSpeedKmh ?: 0f
                        if (spd >= 3.0f) {
                            movingTimeSec += dtSec
                        } else {
                            stoppedTimeSec += dtSec
                        }
                    }
                }
                lastTimeMs = curTimeMs
            }

            SessionDetailStats(
                maxLatG = maxLatG,
                maxLatGLeft = maxLatGLeft,
                maxLatGRight = maxLatGRight,
                maxBrakingG = maxBrakingG,
                maxAccelG = maxAccelG,
                maxCombinedG = maxCombinedG,
                maxRpm = maxRpm,
                avgRpm = if (rpmCount > 0) (totalRpm / rpmCount).toInt() else null,
                maxCoolantC = maxCoolantC,
                avgCoolantC = if (coolantCount > 0) (avgCoolantSum / coolantCount).toInt() else null,
                maxThrottlePct = if (throttleCount > 0) maxThrottle else null,
                fullThrottlePctTime = if (throttleCount > 0) (fullThrottleCount * 100f / throttleCount) else null,
                maxBoostBar = maxBoost,
                maxLoadPct = maxLoad,
                avgFuelL100km = if (fuelCount > 0) (fuelSum / fuelCount) else null,
                minAltitudeM = minAlt,
                maxAltitudeM = maxAlt,
                elevationGainM = if (elevationGain > 0.0) elevationGain else null,
                elevationLossM = if (elevationLoss > 0.0) elevationLoss else null,
                movingTimeSec = movingTimeSec,
                stoppedTimeSec = stoppedTimeSec,
                hasObdData = rpmCount > 0 || coolantCount > 0,
                totalPoints = points.size
            )
        }
    }

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

                // Fila de desglose de tiempo y muestras
                if (detailedStats != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            title = "En Marcha",
                            value = FormatUtils.formatDuration(detailedStats.movingTimeSec),
                            accentColor = NeonGreen,
                            titleFontSize = 10.sp,
                            valueFontSize = 18.sp,
                            unitFontSize = 11.sp,
                            verticalPadding = 6.dp,
                            horizontalPadding = 4.dp,
                            modifier = Modifier.weight(1f)
                        )

                        MetricCard(
                            title = "Detenido",
                            value = FormatUtils.formatDuration(detailedStats.stoppedTimeSec),
                            accentColor = TextMuted,
                            titleFontSize = 10.sp,
                            valueFontSize = 18.sp,
                            unitFontSize = 11.sp,
                            verticalPadding = 6.dp,
                            horizontalPadding = 4.dp,
                            modifier = Modifier.weight(1f)
                        )

                        MetricCard(
                            title = "Muestras",
                            value = "${detailedStats.totalPoints}",
                            unit = "pts",
                            accentColor = NeonCyan,
                            titleFontSize = 10.sp,
                            valueFontSize = 18.sp,
                            unitFontSize = 11.sp,
                            verticalPadding = 6.dp,
                            horizontalPadding = 4.dp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Panel: Dinámica de Fuerzas G (Chasis)
                if (detailedStats != null && (detailedStats.maxLatG > 0f || detailedStats.maxBrakingG > 0f)) {
                    DetailSectionCard(
                        title = "Dinámica de Fuerzas G (Chasis)",
                        icon = Icons.Default.Navigation,
                        iconColor = NeonCyan
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricCard(
                                title = "G Lateral Máx",
                                value = String.format(Locale.US, "%.2f", detailedStats.maxLatG),
                                unit = "G",
                                accentColor = NeonCyan,
                                titleFontSize = 10.sp,
                                valueFontSize = 22.sp,
                                unitFontSize = 11.sp,
                                verticalPadding = 6.dp,
                                horizontalPadding = 6.dp,
                                modifier = Modifier.weight(1f)
                            )

                            MetricCard(
                                title = "G Combinada",
                                value = String.format(Locale.US, "%.2f", detailedStats.maxCombinedG),
                                unit = "G",
                                accentColor = NeonAmber,
                                titleFontSize = 10.sp,
                                valueFontSize = 22.sp,
                                unitFontSize = 11.sp,
                                verticalPadding = 6.dp,
                                horizontalPadding = 6.dp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricCard(
                                title = "Frenada Máx",
                                value = String.format(Locale.US, "-%.2f", detailedStats.maxBrakingG),
                                unit = "G",
                                accentColor = NeonRed,
                                titleFontSize = 10.sp,
                                valueFontSize = 22.sp,
                                unitFontSize = 11.sp,
                                verticalPadding = 6.dp,
                                horizontalPadding = 6.dp,
                                modifier = Modifier.weight(1f)
                            )

                            MetricCard(
                                title = "Aceleración",
                                value = String.format(Locale.US, "+%.2f", detailedStats.maxAccelG),
                                unit = "G",
                                accentColor = NeonGreen,
                                titleFontSize = 10.sp,
                                valueFontSize = 22.sp,
                                unitFontSize = 11.sp,
                                verticalPadding = 6.dp,
                                horizontalPadding = 6.dp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (detailedStats.maxLatGLeft > 0f || detailedStats.maxLatGRight > 0f) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = String.format(Locale.US, "◀ Curva Izq: %.2f G", detailedStats.maxLatGLeft),
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = String.format(Locale.US, "Curva Der: %.2f G ▶", detailedStats.maxLatGRight),
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Panel: Rendimiento de Motor y Transmisión OBD
                if (detailedStats != null && detailedStats.hasObdData) {
                    DetailSectionCard(
                        title = "Rendimiento de Motor y Transmisión (OBD)",
                        icon = Icons.Default.Speed,
                        iconColor = NeonAmber
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricCard(
                                title = "RPM Máximas",
                                value = detailedStats.maxRpm?.toString() ?: "--",
                                unit = "rpm",
                                accentColor = NeonRed,
                                titleFontSize = 10.sp,
                                valueFontSize = 22.sp,
                                unitFontSize = 11.sp,
                                verticalPadding = 6.dp,
                                horizontalPadding = 6.dp,
                                modifier = Modifier.weight(1f)
                            )

                            MetricCard(
                                title = "RPM Medias",
                                value = detailedStats.avgRpm?.toString() ?: "--",
                                unit = "rpm",
                                accentColor = TextPrimary,
                                titleFontSize = 10.sp,
                                valueFontSize = 22.sp,
                                unitFontSize = 11.sp,
                                verticalPadding = 6.dp,
                                horizontalPadding = 6.dp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricCard(
                                title = "Temp. Máx Agua",
                                value = detailedStats.maxCoolantC?.let { "$it°C" } ?: "--",
                                accentColor = if ((detailedStats.maxCoolantC ?: 0) > 105) NeonRed else NeonCyan,
                                titleFontSize = 10.sp,
                                valueFontSize = 22.sp,
                                verticalPadding = 6.dp,
                                horizontalPadding = 6.dp,
                                modifier = Modifier.weight(1f)
                            )

                            MetricCard(
                                title = "Temp. Media Agua",
                                value = detailedStats.avgCoolantC?.let { "$it°C" } ?: "--",
                                accentColor = TextPrimary,
                                titleFontSize = 10.sp,
                                valueFontSize = 22.sp,
                                verticalPadding = 6.dp,
                                horizontalPadding = 6.dp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricCard(
                                title = "Pedal a Fondo",
                                value = detailedStats.fullThrottlePctTime?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                                unit = "% tiempo",
                                accentColor = NeonGreen,
                                titleFontSize = 10.sp,
                                valueFontSize = 22.sp,
                                unitFontSize = 11.sp,
                                verticalPadding = 6.dp,
                                horizontalPadding = 6.dp,
                                modifier = Modifier.weight(1f)
                            )

                            val aspirationTitle = if (detailedStats.maxBoostBar != null && detailedStats.maxBoostBar > 0f) "Turbo Pico" else "Carga Máx"
                            val aspirationVal = if (detailedStats.maxBoostBar != null && detailedStats.maxBoostBar > 0f) {
                                String.format(Locale.US, "+%.2f", detailedStats.maxBoostBar)
                            } else {
                                detailedStats.maxLoadPct?.let { "${it.toInt()}%" } ?: "--"
                            }
                            val aspirationUnit = if (detailedStats.maxBoostBar != null && detailedStats.maxBoostBar > 0f) "bar" else null

                            MetricCard(
                                title = aspirationTitle,
                                value = aspirationVal,
                                unit = aspirationUnit,
                                accentColor = NeonAmber,
                                titleFontSize = 10.sp,
                                valueFontSize = 22.sp,
                                unitFontSize = 11.sp,
                                verticalPadding = 6.dp,
                                horizontalPadding = 6.dp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (detailedStats.avgFuelL100km != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricCard(
                                    title = "Consumo Medio",
                                    value = String.format(Locale.US, "%.1f", detailedStats.avgFuelL100km),
                                    unit = "L/100",
                                    accentColor = NeonCyan,
                                    titleFontSize = 10.sp,
                                    valueFontSize = 22.sp,
                                    unitFontSize = 11.sp,
                                    verticalPadding = 6.dp,
                                    horizontalPadding = 6.dp,
                                    modifier = Modifier.weight(1f)
                                )

                                MetricCard(
                                    title = "Pedal Máximo",
                                    value = detailedStats.maxThrottlePct?.let { "${it.toInt()}%" } ?: "--",
                                    accentColor = TextSecondary,
                                    titleFontSize = 10.sp,
                                    valueFontSize = 22.sp,
                                    verticalPadding = 6.dp,
                                    horizontalPadding = 6.dp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Panel: Perfil Altimétrico y Terreno
                if (detailedStats != null && detailedStats.maxAltitudeM != null) {
                    DetailSectionCard(
                        title = "Perfil Altimétrico y Terreno",
                        icon = Icons.Default.Terrain,
                        iconColor = NeonGreen
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricCard(
                                title = "Desnivel +",
                                value = "+${detailedStats.elevationGainM?.toInt() ?: 0}",
                                unit = "m",
                                accentColor = NeonGreen,
                                titleFontSize = 10.sp,
                                valueFontSize = 22.sp,
                                unitFontSize = 11.sp,
                                verticalPadding = 6.dp,
                                horizontalPadding = 6.dp,
                                modifier = Modifier.weight(1f)
                            )

                            MetricCard(
                                title = "Desnivel -",
                                value = "-${detailedStats.elevationLossM?.toInt() ?: 0}",
                                unit = "m",
                                accentColor = NeonRed,
                                titleFontSize = 10.sp,
                                valueFontSize = 22.sp,
                                unitFontSize = 11.sp,
                                verticalPadding = 6.dp,
                                horizontalPadding = 6.dp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricCard(
                                title = "Altitud Máx",
                                value = "${detailedStats.maxAltitudeM.toInt()}",
                                unit = "m",
                                accentColor = NeonCyan,
                                titleFontSize = 10.sp,
                                valueFontSize = 22.sp,
                                unitFontSize = 11.sp,
                                verticalPadding = 6.dp,
                                horizontalPadding = 6.dp,
                                modifier = Modifier.weight(1f)
                            )

                            MetricCard(
                                title = "Altitud Mín",
                                value = "${detailedStats.minAltitudeM?.toInt() ?: 0}",
                                unit = "m",
                                accentColor = TextSecondary,
                                titleFontSize = 10.sp,
                                valueFontSize = 22.sp,
                                unitFontSize = 11.sp,
                                verticalPadding = 6.dp,
                                horizontalPadding = 6.dp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

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

@Composable
private fun DetailSectionCard(
    title: String,
    icon: ImageVector? = null,
    iconColor: Color = NeonCyan,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CockpitCardBg, RoundedCornerShape(12.dp))
            .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = title.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                )
            }
            content()
        }
    }
}

private data class SessionDetailStats(
    val maxLatG: Float = 0f,
    val maxLatGLeft: Float = 0f,
    val maxLatGRight: Float = 0f,
    val maxBrakingG: Float = 0f,
    val maxAccelG: Float = 0f,
    val maxCombinedG: Float = 0f,
    val maxRpm: Int? = null,
    val avgRpm: Int? = null,
    val maxCoolantC: Int? = null,
    val avgCoolantC: Int? = null,
    val maxThrottlePct: Float? = null,
    val fullThrottlePctTime: Float? = null,
    val maxBoostBar: Float? = null,
    val maxLoadPct: Float? = null,
    val avgFuelL100km: Float? = null,
    val minAltitudeM: Double? = null,
    val maxAltitudeM: Double? = null,
    val elevationGainM: Double? = null,
    val elevationLossM: Double? = null,
    val movingTimeSec: Long = 0L,
    val stoppedTimeSec: Long = 0L,
    val hasObdData: Boolean = false,
    val totalPoints: Int = 0
)
