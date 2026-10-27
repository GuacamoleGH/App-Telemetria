package com.telemetria.app.ui.dashboard

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telemetria.app.core.model.EngineAspiration
import com.telemetria.app.core.model.SessionStatus
import com.telemetria.app.core.model.VehicleConfig
import com.telemetria.app.core.session.SessionLiveState
import com.telemetria.app.core.util.FormatUtils
import com.telemetria.app.ui.dashboard.components.CameraPreviewView
import com.telemetria.app.ui.dashboard.components.GMeterView
import com.telemetria.app.ui.dashboard.components.MetricCard
import com.telemetria.app.ui.dashboard.components.ObdConnectDialog
import com.telemetria.app.ui.dashboard.components.RpmBar
import com.telemetria.app.ui.dashboard.components.SpeedGauge
import com.telemetria.app.ui.dashboard.components.StatusIndicatorRow
import com.telemetria.app.ui.dashboard.components.VehicleSettingsDialog
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
    val vehicleConfig by viewModel.vehicleConfig.collectAsState()
    val isVideoEnabled by viewModel.isVideoEnabled.collectAsState()
    val isObdDialogVisible by viewModel.isObdDialogVisible.collectAsState()
    val isSettingsDialogVisible by viewModel.isSettingsDialogVisible.collectAsState()
    val obdConnectionState by viewModel.obdConnectionState.collectAsState()
    val discoveredBleDevices by viewModel.discoveredBleDevices.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Diálogo de ajustes de vehículo y tacómetro
    if (isSettingsDialogVisible) {
        VehicleSettingsDialog(
            currentConfig = vehicleConfig,
            onSaveConfig = { viewModel.saveVehicleConfig(it) },
            onDismiss = { viewModel.dismissSettingsDialog() }
        )
    }

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
            // En Portrait, la barra de control inferior se ancla abajo con navigationBarsPadding
            if (!isLandscape) {
                Surface(
                    color = CockpitBlack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        SessionControlButtons(
                            status = liveState.status,
                            isVideoEnabled = isVideoEnabled,
                            buttonHeight = 52.dp,
                            onStart = { viewModel.onStartClicked() },
                            onPause = { viewModel.onPauseClicked() },
                            onResume = { viewModel.onResumeClicked() },
                            onStop = {
                                viewModel.onStopClicked { saved ->
                                    onSessionFinished(saved.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isLandscape) {
            DashboardLandscapeContent(
                liveState = liveState,
                vehicleConfig = vehicleConfig,
                isVideoEnabled = isVideoEnabled,
                viewModel = viewModel,
                onNavigateToHistory = onNavigateToHistory,
                onSessionFinished = onSessionFinished
            )
        } else {
            DashboardPortraitContent(
                innerPadding = innerPadding,
                liveState = liveState,
                vehicleConfig = vehicleConfig,
                isVideoEnabled = isVideoEnabled,
                viewModel = viewModel,
                onNavigateToHistory = onNavigateToHistory
            )
        }
    }
}

@Composable
private fun DashboardPortraitContent(
    innerPadding: PaddingValues,
    liveState: SessionLiveState,
    vehicleConfig: VehicleConfig,
    isVideoEnabled: Boolean,
    viewModel: DashboardViewModel,
    onNavigateToHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        DashboardHeaderRow(
            liveState = liveState,
            isVideoEnabled = isVideoEnabled,
            onObdClick = { viewModel.showObdDialog() },
            onToggleVideo = { viewModel.toggleVideoEnabled() },
            onCalibrate = { viewModel.onCalibrateSensorsClicked() },
            onOpenSettings = { viewModel.showSettingsDialog() },
            onNavigateToHistory = onNavigateToHistory
        )

        // Pesos dinámicos: cuando la cámara está apagada, los bloques se expanden para ocupar todo el espacio inferior
        val speedGaugeWeight = if (isVideoEnabled) 0.85f else 1.10f
        val gMeterWeight = if (isVideoEnabled) 0.95f else 1.65f
        val metricRowWeight = if (isVideoEnabled) 0.55f else 0.75f

        // Visor de Cámara CameraX: cuando está activa toma su proporción y los demás bloques se adaptan
        AnimatedVisibility(
            visible = isVideoEnabled,
            modifier = if (isVideoEnabled) Modifier.weight(0.95f) else Modifier
        ) {
            CameraPreviewView(
                videoRecordingManager = viewModel.videoRecordingManager,
                isRecording = liveState.isVideoRecording,
                videoDurationSec = liveState.videoDurationSec,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            )
        }

        // Tacómetro estilo MyChron 5 con Shift Lights de carreras y escala configurada
        RpmBar(
            rpm = liveState.engineRpm,
            maxRpm = vehicleConfig.maxRpm,
            redlineRpm = vehicleConfig.redlineRpm,
            shiftLightRpm = vehicleConfig.shiftLightRpm
        )

        // Velocímetro Central (se expande cuando la cámara está desactivada)
        SpeedGauge(
            speedKmh = liveState.currentSpeedKmh,
            isGpsValid = liveState.gpsFix,
            obdSpeedKmh = liveState.obdSpeedKmh,
            gear = liveState.calculatedGear,
            speedFontSize = if (isVideoEnabled) 50.sp else 68.sp,
            unitFontSize = if (isVideoEnabled) 14.sp else 16.sp,
            verticalPadding = if (isVideoEnabled) 4.dp else 12.dp,
            modifier = Modifier.weight(speedGaugeWeight)
        )

        // G-Meter Dinámico (se expande cuando la cámara está desactivada)
        GMeterView(
            lateralG = liveState.lateralG,
            longitudinalG = liveState.longitudinalG,
            gpsDerivedLongG = liveState.gpsDerivedLongitudinalG,
            isCalibrated = liveState.isSensorCalibrated,
            isCalibrating = liveState.isCalibrating,
            autoCalibrationCountdownSec = liveState.autoCalibrationCountdownSec,
            modifier = Modifier.weight(gMeterWeight)
        )

        if (isVideoEnabled) {
            // Cuando la cámara está activa, consolidamos las 4 métricas estándar en 1 sola fila compacta
            // para garantizar que la fila de motor OBD NUNCA quede oculta o cortada por la barra inferior.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(metricRowWeight),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricCard(
                    title = "Distancia",
                    value = FormatUtils.formatDistanceValue(liveState.distanceMeters),
                    unit = "km",
                    accentColor = NeonCyan,
                    titleFontSize = 10.sp,
                    valueFontSize = 17.sp,
                    unitFontSize = 10.sp,
                    verticalPadding = 6.dp,
                    horizontalPadding = 3.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                MetricCard(
                    title = "Tiempo",
                    value = FormatUtils.formatDuration(liveState.elapsedSeconds),
                    accentColor = NeonGreen,
                    titleFontSize = 10.sp,
                    valueFontSize = 17.sp,
                    unitFontSize = 10.sp,
                    verticalPadding = 6.dp,
                    horizontalPadding = 3.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                MetricCard(
                    title = "Máxima",
                    value = FormatUtils.formatSpeed(liveState.maxSpeedKmh),
                    unit = "km/h",
                    accentColor = NeonRed,
                    titleFontSize = 10.sp,
                    valueFontSize = 17.sp,
                    unitFontSize = 10.sp,
                    verticalPadding = 6.dp,
                    horizontalPadding = 3.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                MetricCard(
                    title = "Media",
                    value = FormatUtils.formatSpeed(liveState.avgSpeedKmh),
                    unit = "km/h",
                    accentColor = NeonAmber,
                    titleFontSize = 10.sp,
                    valueFontSize = 17.sp,
                    unitFontSize = 10.sp,
                    verticalPadding = 6.dp,
                    horizontalPadding = 3.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            // Modo vertical con cámara apagada: 2 filas grandes de 2 métricas cada una
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(metricRowWeight),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Distancia",
                    value = FormatUtils.formatDistanceValue(liveState.distanceMeters),
                    unit = "km",
                    accentColor = NeonCyan,
                    titleFontSize = 13.sp,
                    valueFontSize = 30.sp,
                    unitFontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                MetricCard(
                    title = "Tiempo",
                    value = FormatUtils.formatDuration(liveState.elapsedSeconds),
                    accentColor = NeonGreen,
                    titleFontSize = 13.sp,
                    valueFontSize = 28.sp,
                    unitFontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(metricRowWeight),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Vel. Máxima",
                    value = FormatUtils.formatSpeed(liveState.maxSpeedKmh),
                    unit = "km/h",
                    accentColor = NeonRed,
                    titleFontSize = 13.sp,
                    valueFontSize = 30.sp,
                    unitFontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                MetricCard(
                    title = "Vel. Media",
                    value = FormatUtils.formatSpeed(liveState.avgSpeedKmh),
                    unit = "km/h",
                    accentColor = NeonAmber,
                    titleFontSize = 13.sp,
                    valueFontSize = 30.sp,
                    unitFontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        // Grid de Métricas de Motor OBD
        if (liveState.isObdConnected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (isVideoEnabled) 0.55f else 0.7f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Refrigerante",
                    value = liveState.coolantTempC?.let { "$it°C" } ?: "--",
                    accentColor = if ((liveState.coolantTempC ?: 0) > 105) NeonRed else NeonCyan,
                    titleFontSize = if (isVideoEnabled) 11.sp else 12.sp,
                    valueFontSize = if (isVideoEnabled) 20.sp else 28.sp,
                    verticalPadding = if (isVideoEnabled) 6.dp else 10.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                MetricCard(
                    title = "Pedal Acel.",
                    value = liveState.throttlePercent?.let { "${it.toInt()}%" } ?: "--",
                    accentColor = NeonGreen,
                    titleFontSize = if (isVideoEnabled) 11.sp else 12.sp,
                    valueFontSize = if (isVideoEnabled) 20.sp else 28.sp,
                    verticalPadding = if (isVideoEnabled) 6.dp else 10.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                val extraTitle: String?
                val extraValue: String?
                val extraUnit: String?
                if (vehicleConfig.aspiration == EngineAspiration.TURBOCHARGED && liveState.boostPressureBar != null) {
                    extraTitle = "Turbo"
                    val b = liveState.boostPressureBar
                    extraValue = if (b >= 0f) String.format(java.util.Locale.US, "+%.2f", b) else String.format(java.util.Locale.US, "%.2f", b)
                    extraUnit = "bar"
                } else if (liveState.engineLoadPercent != null) {
                    extraTitle = "Carga"
                    extraValue = "${liveState.engineLoadPercent.toInt()}%"
                    extraUnit = null
                } else if (liveState.fuelConsumptionL100km != null) {
                    extraTitle = "Consumo"
                    extraValue = String.format(java.util.Locale.US, "%.1f", liveState.fuelConsumptionL100km)
                    extraUnit = "L/100"
                } else {
                    extraTitle = null
                    extraValue = null
                    extraUnit = null
                }

                if (extraTitle != null && extraValue != null) {
                    MetricCard(
                        title = extraTitle,
                        value = extraValue,
                        unit = extraUnit,
                        accentColor = NeonAmber,
                        titleFontSize = if (isVideoEnabled) 11.sp else 12.sp,
                        valueFontSize = if (isVideoEnabled) 20.sp else 28.sp,
                        verticalPadding = if (isVideoEnabled) 6.dp else 10.dp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardLandscapeContent(
    liveState: SessionLiveState,
    vehicleConfig: VehicleConfig,
    isVideoEnabled: Boolean,
    viewModel: DashboardViewModel,
    onNavigateToHistory: () -> Unit,
    onSessionFinished: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Cabecera superior completa a todo lo ancho
        DashboardHeaderRow(
            liveState = liveState,
            isVideoEnabled = isVideoEnabled,
            onObdClick = { viewModel.showObdDialog() },
            onToggleVideo = { viewModel.toggleVideoEnabled() },
            onCalibrate = { viewModel.onCalibrateSensorsClicked() },
            onOpenSettings = { viewModel.showSettingsDialog() },
            onNavigateToHistory = onNavigateToHistory
        )

        // Tres columnas optimizadas para orientación horizontal (Cockpit Mode)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Columna 1 (Izquierda): Tacómetro + Velocímetro principal (+ datos OBD)
            Column(
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RpmBar(
                    rpm = liveState.engineRpm,
                    maxRpm = vehicleConfig.maxRpm,
                    redlineRpm = vehicleConfig.redlineRpm,
                    shiftLightRpm = vehicleConfig.shiftLightRpm
                )

                SpeedGauge(
                    speedKmh = liveState.currentSpeedKmh,
                    isGpsValid = liveState.gpsFix,
                    obdSpeedKmh = liveState.obdSpeedKmh,
                    gear = liveState.calculatedGear,
                    speedFontSize = 50.sp,
                    unitFontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )

                if (liveState.isObdConnected) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MetricCard(
                            title = "Agua",
                            value = liveState.coolantTempC?.let { "$it°C" } ?: "--",
                            accentColor = if ((liveState.coolantTempC ?: 0) > 105) NeonRed else NeonCyan,
                            titleFontSize = 9.sp,
                            valueFontSize = 16.sp,
                            unitFontSize = 10.sp,
                            verticalPadding = 4.dp,
                            horizontalPadding = 4.dp,
                            modifier = Modifier.weight(1f)
                        )

                        MetricCard(
                            title = "Pedal",
                            value = liveState.throttlePercent?.let { "${it.toInt()}%" } ?: "--",
                            accentColor = NeonGreen,
                            titleFontSize = 9.sp,
                            valueFontSize = 16.sp,
                            unitFontSize = 10.sp,
                            verticalPadding = 4.dp,
                            horizontalPadding = 4.dp,
                            modifier = Modifier.weight(1f)
                        )

                        val extraLandscapeTitle: String?
                        val extraLandscapeValue: String?
                        val extraLandscapeUnit: String?
                        if (vehicleConfig.aspiration == EngineAspiration.TURBOCHARGED && liveState.boostPressureBar != null) {
                            extraLandscapeTitle = "Turbo"
                            val b = liveState.boostPressureBar
                            extraLandscapeValue = if (b >= 0f) String.format(java.util.Locale.US, "+%.2f", b) else String.format(java.util.Locale.US, "%.2f", b)
                            extraLandscapeUnit = "bar"
                        } else if (liveState.engineLoadPercent != null) {
                            extraLandscapeTitle = "Carga"
                            extraLandscapeValue = "${liveState.engineLoadPercent.toInt()}%"
                            extraLandscapeUnit = null
                        } else if (liveState.fuelConsumptionL100km != null) {
                            extraLandscapeTitle = "Consumo"
                            extraLandscapeValue = String.format(java.util.Locale.US, "%.1f", liveState.fuelConsumptionL100km)
                            extraLandscapeUnit = "L/100"
                        } else {
                            extraLandscapeTitle = null
                            extraLandscapeValue = null
                            extraLandscapeUnit = null
                        }

                        if (extraLandscapeTitle != null && extraLandscapeValue != null) {
                            MetricCard(
                                title = extraLandscapeTitle,
                                value = extraLandscapeValue,
                                unit = extraLandscapeUnit,
                                accentColor = NeonAmber,
                                titleFontSize = 9.sp,
                                valueFontSize = 16.sp,
                                unitFontSize = 10.sp,
                                verticalPadding = 4.dp,
                                horizontalPadding = 4.dp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Columna 2 (Centro): Cámara (si está activa) + G-Meter (círculo de tracción Kamm)
            Column(
                modifier = Modifier
                    .weight(1.10f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isVideoEnabled) {
                    CameraPreviewView(
                        videoRecordingManager = viewModel.videoRecordingManager,
                        isRecording = liveState.isVideoRecording,
                        videoDurationSec = liveState.videoDurationSec,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                    )
                }

                GMeterView(
                    lateralG = liveState.lateralG,
                    longitudinalG = liveState.longitudinalG,
                    gpsDerivedLongG = liveState.gpsDerivedLongitudinalG,
                    isCalibrated = liveState.isSensorCalibrated,
                    isCalibrating = liveState.isCalibrating,
                    autoCalibrationCountdownSec = liveState.autoCalibrationCountdownSec,
                    modifier = Modifier.weight(1f)
                )
            }

            // Columna 3 (Derecha): 4 Tarjetas de telemetría expandidas verticalmente + Botón de Iniciar/Detener
            Column(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricCard(
                            title = "Distancia",
                            value = FormatUtils.formatDistanceValue(liveState.distanceMeters),
                            unit = "km",
                            accentColor = NeonCyan,
                            titleFontSize = 11.sp,
                            valueFontSize = 24.sp,
                            unitFontSize = 12.sp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        MetricCard(
                            title = "Tiempo",
                            value = FormatUtils.formatDuration(liveState.elapsedSeconds),
                            accentColor = NeonGreen,
                            titleFontSize = 11.sp,
                            valueFontSize = 24.sp,
                            unitFontSize = 12.sp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricCard(
                            title = "Vel. Máx",
                            value = FormatUtils.formatSpeed(liveState.maxSpeedKmh),
                            unit = "km/h",
                            accentColor = NeonRed,
                            titleFontSize = 11.sp,
                            valueFontSize = 24.sp,
                            unitFontSize = 12.sp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        MetricCard(
                            title = "Vel. Med",
                            value = FormatUtils.formatSpeed(liveState.avgSpeedKmh),
                            unit = "km/h",
                            accentColor = NeonAmber,
                            titleFontSize = 11.sp,
                            valueFontSize = 24.sp,
                            unitFontSize = 12.sp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }

                SessionControlButtons(
                    status = liveState.status,
                    isVideoEnabled = isVideoEnabled,
                    buttonHeight = 44.dp,
                    fontSize = 13.sp,
                    onStart = { viewModel.onStartClicked() },
                    onPause = { viewModel.onPauseClicked() },
                    onResume = { viewModel.onResumeClicked() },
                    onStop = {
                        viewModel.onStopClicked { saved ->
                            onSessionFinished(saved.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DashboardHeaderRow(
    liveState: SessionLiveState,
    isVideoEnabled: Boolean,
    onObdClick: () -> Unit,
    onToggleVideo: () -> Unit,
    onCalibrate: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            StatusIndicatorRow(
                gpsFix = liveState.gpsFix,
                gpsAccuracyMeters = liveState.gpsAccuracyMeters,
                sampleRateHz = liveState.sampleRateHz,
                obdConnected = liveState.isObdConnected,
                obdHz = liveState.obdRealHz,
                onObdClick = onObdClick
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleVideo,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    contentDescription = "Activar / Desactivar Grabación de Vídeo",
                    tint = if (isVideoEnabled) NeonCyan else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onCalibrate,
                enabled = !liveState.isCalibrating,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ScreenRotation,
                    contentDescription = "Calibrar posición de sensores",
                    tint = if (liveState.isCalibrating) NeonAmber else if (liveState.isSensorCalibrated) NeonGreen else TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Ajustes del Vehículo y Tacómetro",
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onNavigateToHistory,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Historial de Sesiones",
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SessionControlButtons(
    status: SessionStatus,
    isVideoEnabled: Boolean,
    buttonHeight: Dp = 52.dp,
    fontSize: TextUnit = 15.sp,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    when (status) {
        SessionStatus.COMPLETED -> {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight),
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
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
        SessionStatus.ACTIVE -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPause,
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonAmber,
                        contentColor = CockpitBlack
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PAUSAR", fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("FINALIZAR", fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        SessionStatus.PAUSED -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = CockpitBlack
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("REANUDAR", fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("FINALIZAR", fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}
