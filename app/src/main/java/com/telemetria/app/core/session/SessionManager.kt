package com.telemetria.app.core.session

import android.os.SystemClock
import com.telemetria.app.core.database.TelemetryDatabase
import com.telemetria.app.core.database.entity.SessionEntity
import com.telemetria.app.core.database.entity.TelemetryPointEntity
import com.telemetria.app.core.model.GpsSnapshot
import com.telemetria.app.core.model.Session
import com.telemetria.app.core.model.SessionStatus
import com.telemetria.app.core.sensor.OrientationCalibrator
import com.telemetria.app.core.sensor.model.InertialSnapshot
import com.telemetria.app.core.util.CalculationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

class SessionManager private constructor(
    private val database: TelemetryDatabase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var batchWriter: TelemetryBatchWriter? = null
    val orientationCalibrator = OrientationCalibrator()

    private val _liveState = MutableStateFlow(SessionLiveState())
    val liveState: StateFlow<SessionLiveState> = _liveState.asStateFlow()

    private var activeSession: Session? = null
    private var lastRecordedLocation: GpsSnapshot? = null
    private var sampleCountWindow: Int = 0
    private var sampleWindowStart: Long = 0

    // Última aceleración lineal y G-forces para enriquecer los puntos grabados
    private var latestInertial: InertialSnapshot? = null
    private var currentLateralG: Float = 0f
    private var currentLongitudinalG: Float = 0f

    // Últimos datos OBD-II (Fase 6)
    private var latestObd: com.telemetria.app.core.obd.model.ObdData? = null

    // Ventana para reducción de tasa de persistencia inercial (ej. persistir a 10Hz en base de datos)
    private var lastInertialPersistMillis: Long = 0L

    // Variables de detección de inmovilidad para auto-calibración previa a la grabación
    private var stillStartTimeMillis: Long = 0L
    private var hasAutoCalibratedForCurrentStillness: Boolean = false

    init {
        scope.launch(Dispatchers.IO) {
            val uncompleted = database.sessionDao().getActiveOrPausedSession()
            if (uncompleted != null) {
                val restored = uncompleted.toDomain()
                activeSession = restored
                _liveState.update {
                    it.copy(
                        currentSession = restored,
                        status = restored.status,
                        distanceMeters = restored.totalDistanceMeters,
                        maxSpeedKmh = restored.maxSpeedKmh,
                        avgSpeedKmh = restored.avgSpeedKmh,
                        elapsedSeconds = restored.durationSeconds,
                        pointCount = restored.pointCount
                    )
                }
                batchWriter = TelemetryBatchWriter(database.telemetryPointDao(), scope)
            }
        }
    }

    fun startOrientationCalibration() {
        orientationCalibrator.startCalibration()
        _liveState.update { it.copy(isCalibrating = true, isSensorCalibrated = false) }
    }

    fun attachVideoRecording(filePath: String, startMonoNanos: Long, startEpochMillis: Long) {
        val current = activeSession ?: return
        val updated = current.copy(
            videoFilePath = filePath,
            videoStartMonotonicNanos = startMonoNanos,
            videoStartEpochMillis = startEpochMillis
        )
        activeSession = updated

        scope.launch(Dispatchers.IO) {
            database.sessionDao().updateVideoInfo(
                sessionId = current.id,
                filePath = filePath,
                startMonoNanos = startMonoNanos,
                startEpochMillis = startEpochMillis
            )
        }

        _liveState.update {
            it.copy(
                isVideoRecording = true,
                videoFilePath = filePath,
                videoStartMonotonicNanos = startMonoNanos,
                videoStartEpochMillis = startEpochMillis
            )
        }
    }

    fun updateVideoStatus(isRecording: Boolean, durationSec: Long) {
        _liveState.update {
            it.copy(
                isVideoRecording = isRecording,
                videoDurationSec = durationSec
            )
        }
    }

    suspend fun startNewSession(title: String = ""): Session {
        val nowMillis = System.currentTimeMillis()
        val monoNanos = SystemClock.elapsedRealtimeNanos()
        val sessionTitle = if (title.isBlank()) {
            "Sesión ${com.telemetria.app.core.util.FormatUtils.formatDate(nowMillis)}"
        } else title

        val session = Session(
            id = UUID.randomUUID().toString(),
            title = sessionTitle,
            startTimeMillis = nowMillis,
            startMonotonicNanos = monoNanos,
            status = SessionStatus.ACTIVE
        )

        activeSession = session
        lastRecordedLocation = null
        sampleCountWindow = 0
        sampleWindowStart = nowMillis

        batchWriter?.close()
        batchWriter = TelemetryBatchWriter(database.telemetryPointDao(), scope)

        database.sessionDao().insertSession(SessionEntity.fromDomain(session))

        _liveState.update {
            SessionLiveState(
                currentSession = session,
                status = SessionStatus.ACTIVE,
                isSensorCalibrated = orientationCalibrator.isCalibrated
            )
        }

        return session
    }

    fun pauseSession() {
        val current = activeSession ?: return
        val paused = current.copy(status = SessionStatus.PAUSED)
        activeSession = paused

        scope.launch(Dispatchers.IO) {
            batchWriter?.flush()
            database.sessionDao().updateSession(SessionEntity.fromDomain(paused))
        }

        _liveState.update { it.copy(status = SessionStatus.PAUSED) }
    }

    fun resumeSession() {
        val current = activeSession ?: return
        val resumed = current.copy(status = SessionStatus.ACTIVE)
        activeSession = resumed

        scope.launch(Dispatchers.IO) {
            database.sessionDao().updateSession(SessionEntity.fromDomain(resumed))
        }

        _liveState.update { it.copy(status = SessionStatus.ACTIVE) }
    }

    suspend fun stopSession(): Session? {
        val current = activeSession ?: return null
        val nowMillis = System.currentTimeMillis()
        val totalSec = ((nowMillis - current.startTimeMillis) / 1000).coerceAtLeast(0)

        val completed = current.copy(
            endTimeMillis = nowMillis,
            status = SessionStatus.COMPLETED,
            durationSeconds = totalSec,
            totalDistanceMeters = _liveState.value.distanceMeters,
            maxSpeedKmh = _liveState.value.maxSpeedKmh,
            avgSpeedKmh = _liveState.value.avgSpeedKmh,
            pointCount = _liveState.value.pointCount
        )

        batchWriter?.flush()
        batchWriter?.close()
        batchWriter = null

        database.sessionDao().updateSession(SessionEntity.fromDomain(completed))

        activeSession = null
        lastRecordedLocation = null

        _liveState.update {
            it.copy(
                currentSession = null,
                status = SessionStatus.COMPLETED,
                currentSpeedKmh = 0f
            )
        }

        return completed
    }

    fun onInertialReceived(snapshot: InertialSnapshot) {
        latestInertial = snapshot

        // Si se encuentra en proceso de calibración, alimentar muestras de gravedad
        if (_liveState.value.isCalibrating) {
            val finished = orientationCalibrator.addCalibrationSample(
                snapshot.rawAccelX,
                snapshot.rawAccelY,
                snapshot.rawAccelZ
            )
            if (finished) {
                _liveState.update {
                    it.copy(
                        isCalibrating = false,
                        isSensorCalibrated = true,
                        autoCalibrationCountdownSec = null
                    )
                }
            }
            return
        }

        // Auto-calibración: si el móvil lleva quieto más de 5 segundos antes de grabar
        val isRecording = activeSession?.status == SessionStatus.ACTIVE
        if (!isRecording) {
            val linMag = sqrt(
                snapshot.linearAccelX * snapshot.linearAccelX +
                snapshot.linearAccelY * snapshot.linearAccelY +
                snapshot.linearAccelZ * snapshot.linearAccelZ
            )
            val gyroMag = sqrt(
                snapshot.gyroX * snapshot.gyroX +
                snapshot.gyroY * snapshot.gyroY +
                snapshot.gyroZ * snapshot.gyroZ
            )

            // Umbrales de inmovilidad estricta (móvil quieto en el soporte del coche)
            val isStill = linMag < 0.25f && gyroMag < 0.12f

            if (isStill) {
                val now = snapshot.timestampMillis
                if (stillStartTimeMillis == 0L) {
                    stillStartTimeMillis = now
                }
                val elapsedStillMs = now - stillStartTimeMillis
                if (elapsedStillMs >= 5000L && !hasAutoCalibratedForCurrentStillness) {
                    hasAutoCalibratedForCurrentStillness = true
                    stillStartTimeMillis = 0L
                    startOrientationCalibration()
                } else if (!hasAutoCalibratedForCurrentStillness && !_liveState.value.isSensorCalibrated) {
                    val remainingSec = ((5000L - elapsedStillMs + 999L) / 1000L).toInt().coerceIn(1, 5)
                    if (_liveState.value.autoCalibrationCountdownSec != remainingSec) {
                        _liveState.update { it.copy(autoCalibrationCountdownSec = remainingSec) }
                    }
                }
            } else {
                // Se detectó movimiento: resetear temporizador de quietud
                stillStartTimeMillis = 0L
                hasAutoCalibratedForCurrentStillness = false
                if (_liveState.value.autoCalibrationCountdownSec != null) {
                    _liveState.update { it.copy(autoCalibrationCountdownSec = null) }
                }
            }
        } else {
            stillStartTimeMillis = 0L
            hasAutoCalibratedForCurrentStillness = false
            if (_liveState.value.autoCalibrationCountdownSec != null) {
                _liveState.update { it.copy(autoCalibrationCountdownSec = null) }
            }
        }

        val gForces = orientationCalibrator.transformToVehicleG(snapshot)
        currentLateralG = gForces.lateralG
        currentLongitudinalG = gForces.longitudinalG

        var newMaxLat = _liveState.value.maxLateralG
        var newMaxLong = _liveState.value.maxLongitudinalG

        if (abs(currentLateralG) > newMaxLat) {
            newMaxLat = abs(currentLateralG)
        }
        if (abs(currentLongitudinalG) > newMaxLong) {
            newMaxLong = abs(currentLongitudinalG)
        }

        val session = activeSession
        val now = snapshot.timestampMillis

        // Persistir puntos intermedios de alta frecuencia (cada ~100 ms / 10 Hz) cuando la sesión está activa
        if (session != null && session.status == SessionStatus.ACTIVE && (now - lastInertialPersistMillis >= 100L)) {
            lastInertialPersistMillis = now
            val entity = TelemetryPointEntity(
                sessionId = session.id,
                timestampMillis = snapshot.timestampMillis,
                monotonicNanos = snapshot.monotonicNanos,
                latitude = lastRecordedLocation?.latitude,
                longitude = lastRecordedLocation?.longitude,
                altitudeMeters = lastRecordedLocation?.altitudeMeters,
                gpsSpeedKmh = lastRecordedLocation?.speedKmh,
                bearingDegrees = lastRecordedLocation?.bearingDegrees,
                accuracyMeters = lastRecordedLocation?.accuracyMeters,
                hasGpsFix = lastRecordedLocation?.isRealFix ?: false,
                accelX = snapshot.linearAccelX,
                accelY = snapshot.linearAccelY,
                accelZ = snapshot.linearAccelZ,
                lateralG = currentLateralG,
                longitudinalG = currentLongitudinalG,
                engineRpm = latestObd?.engineRpm,
                obdSpeedKmh = latestObd?.obdSpeedKmh,
                throttlePercent = latestObd?.throttlePercent,
                coolantTempC = latestObd?.coolantTempC,
                engineLoadPercent = latestObd?.engineLoadPercent,
                intakeAirTempC = latestObd?.intakeAirTempC,
                boostPressureBar = latestObd?.boostPressureBar,
                fuelConsumptionL100km = latestObd?.fuelConsumptionL100km,
                gear = latestObd?.calculatedGear
            )
            batchWriter?.enqueue(entity)
        }

        _liveState.update {
            it.copy(
                lateralG = currentLateralG,
                longitudinalG = currentLongitudinalG,
                maxLateralG = newMaxLat,
                maxLongitudinalG = newMaxLong,
                isSensorCalibrated = orientationCalibrator.isCalibrated
            )
        }
    }

    fun onObdReceived(data: com.telemetria.app.core.obd.model.ObdData) {
        latestObd = data
        _liveState.update {
            it.copy(
                isObdConnected = data.isConnected,
                engineRpm = data.engineRpm,
                obdSpeedKmh = data.obdSpeedKmh,
                throttlePercent = data.throttlePercent,
                coolantTempC = data.coolantTempC,
                engineLoadPercent = data.engineLoadPercent,
                intakeAirTempC = data.intakeAirTempC,
                boostPressureBar = data.boostPressureBar,
                fuelRateLh = data.fuelRateLh,
                fuelConsumptionL100km = data.fuelConsumptionL100km,
                calculatedGear = data.calculatedGear,
                obdRealHz = data.realHz
            )
        }
    }

    fun onGpsReceived(gps: GpsSnapshot) {
        val session = activeSession
        val now = System.currentTimeMillis()

        // Calcular frecuencia real en ventana de 2 segundos
        if (sampleWindowStart == 0L) sampleWindowStart = now
        sampleCountWindow++
        val windowDuration = (now - sampleWindowStart) / 1000f
        val currentHz = if (windowDuration >= 2.0f) {
            val hz = sampleCountWindow / windowDuration
            sampleCountWindow = 0
            sampleWindowStart = now
            hz
        } else {
            _liveState.value.sampleRateHz
        }

        var newDistance = _liveState.value.distanceMeters
        val currentSpeed = gps.speedKmh ?: 0f
        var newMaxSpeed = _liveState.value.maxSpeedKmh

        // Calcular aceleración longitudinal derivada de la velocidad GPS (dv / dt)
        var gpsDerivedG: Float? = null
        val prevLoc = lastRecordedLocation
        if (prevLoc != null && prevLoc.speedKmh != null && gps.speedKmh != null) {
            val dtSec = (gps.timestampMillis - prevLoc.timestampMillis) / 1000.0f
            if (dtSec in 0.2f..2.5f) {
                val dvMps = ((gps.speedKmh - prevLoc.speedKmh) / 3.6f)
                val accMps2 = dvMps / dtSec
                gpsDerivedG = accMps2 / OrientationCalibrator.STANDARD_GRAVITY
            }
        }

        if (session != null && session.status == SessionStatus.ACTIVE) {
            if (prevLoc != null && (gps.accuracyMeters ?: 100f) < 30f) {
                val d = CalculationUtils.haversineDistanceMeters(
                    lat1 = prevLoc.latitude,
                    lon1 = prevLoc.longitude,
                    lat2 = gps.latitude,
                    lon2 = gps.longitude
                )
                if (d > 0.5 && d < 200.0) {
                    newDistance += d
                }
            }
            lastRecordedLocation = gps

            if (currentSpeed > newMaxSpeed) {
                newMaxSpeed = currentSpeed
            }

            val newCount = _liveState.value.pointCount + 1
            val elapsedSec = ((now - session.startTimeMillis) / 1000).coerceAtLeast(1)
            val avgSpeed = ((newDistance / 1000.0) / (elapsedSec / 3600.0)).toFloat()

            val imu = latestInertial
            val entity = TelemetryPointEntity(
                sessionId = session.id,
                timestampMillis = gps.timestampMillis,
                monotonicNanos = gps.monotonicNanos,
                latitude = gps.latitude,
                longitude = gps.longitude,
                altitudeMeters = gps.altitudeMeters,
                gpsSpeedKmh = gps.speedKmh,
                bearingDegrees = gps.bearingDegrees,
                accuracyMeters = gps.accuracyMeters,
                hasGpsFix = gps.isRealFix,
                accelX = imu?.linearAccelX,
                accelY = imu?.linearAccelY,
                accelZ = imu?.linearAccelZ,
                lateralG = currentLateralG,
                longitudinalG = currentLongitudinalG,
                engineRpm = latestObd?.engineRpm,
                obdSpeedKmh = latestObd?.obdSpeedKmh,
                throttlePercent = latestObd?.throttlePercent,
                coolantTempC = latestObd?.coolantTempC,
                engineLoadPercent = latestObd?.engineLoadPercent,
                intakeAirTempC = latestObd?.intakeAirTempC,
                boostPressureBar = latestObd?.boostPressureBar,
                fuelConsumptionL100km = latestObd?.fuelConsumptionL100km,
                gear = latestObd?.calculatedGear
            )
            batchWriter?.enqueue(entity)

            if (newCount % 10L == 0L) {
                scope.launch(Dispatchers.IO) {
                    database.sessionDao().updateLiveStats(
                        sessionId = session.id,
                        distanceMeters = newDistance,
                        maxSpeed = newMaxSpeed,
                        avgSpeed = avgSpeed,
                        durationSec = elapsedSec,
                        count = newCount
                    )
                }
            }

            _liveState.update {
                it.copy(
                    currentSpeedKmh = currentSpeed,
                    maxSpeedKmh = newMaxSpeed,
                    avgSpeedKmh = avgSpeed,
                    distanceMeters = newDistance,
                    elapsedSeconds = elapsedSec,
                    latestGps = gps,
                    gpsFix = gps.isRealFix,
                    gpsAccuracyMeters = gps.accuracyMeters,
                    sampleRateHz = currentHz,
                    pointCount = newCount,
                    gpsDerivedLongitudinalG = gpsDerivedG
                )
            }
        } else {
            lastRecordedLocation = gps
            _liveState.update {
                it.copy(
                    currentSpeedKmh = currentSpeed,
                    latestGps = gps,
                    gpsFix = gps.isRealFix,
                    gpsAccuracyMeters = gps.accuracyMeters,
                    sampleRateHz = currentHz,
                    gpsDerivedLongitudinalG = gpsDerivedG
                )
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(database: TelemetryDatabase): SessionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SessionManager(database)
                INSTANCE = instance
                instance
            }
        }
    }
}
