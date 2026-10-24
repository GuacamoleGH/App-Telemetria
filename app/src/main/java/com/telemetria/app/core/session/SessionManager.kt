package com.telemetria.app.core.session

import android.os.SystemClock
import com.telemetria.app.core.database.TelemetryDatabase
import com.telemetria.app.core.database.entity.SessionEntity
import com.telemetria.app.core.database.entity.TelemetryPointEntity
import com.telemetria.app.core.model.GpsSnapshot
import com.telemetria.app.core.model.Session
import com.telemetria.app.core.model.SessionStatus
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

class SessionManager private constructor(
    private val database: TelemetryDatabase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var batchWriter: TelemetryBatchWriter? = null

    private val _liveState = MutableStateFlow(SessionLiveState())
    val liveState: StateFlow<SessionLiveState> = _liveState.asStateFlow()

    private var activeSession: Session? = null
    private var lastRecordedLocation: GpsSnapshot? = null
    private var lastSampleTimeMillis: Long = 0
    private var sampleCountWindow: Int = 0
    private var sampleWindowStart: Long = 0

    init {
        // Al arrancar, verificar si existe una sesión previa interrumpida (recuperación de cierre inesperado)
        scope.launch(Dispatchers.IO) {
            val uncompleted = database.sessionDao().getActiveOrPausedSession()
            if (uncompleted != null) {
                // Recuperar la sesión activa o marcarla como pausada para que el usuario pueda reanudarla o cerrarla
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
                status = SessionStatus.ACTIVE
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

        if (session != null && session.status == SessionStatus.ACTIVE) {
            // Calcular incremento de distancia si la precisión es aceptable
            val lastLoc = lastRecordedLocation
            if (lastLoc != null && (gps.accuracyMeters ?: 100f) < 30f) {
                val d = CalculationUtils.haversineDistanceMeters(
                    lat1 = lastLoc.latitude,
                    lon1 = lastLoc.longitude,
                    lat2 = gps.latitude,
                    lon2 = gps.longitude
                )
                // Filtrar saltos de ruido GPS si la velocidad es muy baja
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

            // Crear y encolar el punto para escritura en lote
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
                hasGpsFix = gps.isRealFix
            )
            batchWriter?.enqueue(entity)

            // Actualizar periódicamente en la BD para tolerar cierres de la app
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
                    pointCount = newCount
                )
            }
        } else {
            // Solo monitorización sin grabación activa
            _liveState.update {
                it.copy(
                    currentSpeedKmh = currentSpeed,
                    latestGps = gps,
                    gpsFix = gps.isRealFix,
                    gpsAccuracyMeters = gps.accuracyMeters,
                    sampleRateHz = currentHz
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

