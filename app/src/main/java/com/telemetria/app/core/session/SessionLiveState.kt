package com.telemetria.app.core.session

import com.telemetria.app.core.model.GpsSnapshot
import com.telemetria.app.core.model.Session
import com.telemetria.app.core.model.SessionStatus

data class SessionLiveState(
    val currentSession: Session? = null,
    val status: SessionStatus = SessionStatus.COMPLETED,
    val currentSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val distanceMeters: Double = 0.0,
    val elapsedSeconds: Long = 0,
    val latestGps: GpsSnapshot? = null,
    val gpsFix: Boolean = false,
    val gpsAccuracyMeters: Float? = null,
    val sampleRateHz: Float = 0f,
    val pointCount: Long = 0,

    // IMU & Dynamics (Fase 3)
    val lateralG: Float = 0f,
    val longitudinalG: Float = 0f,
    val gpsDerivedLongitudinalG: Float? = null,
    val maxLateralG: Float = 0f,
    val maxLongitudinalG: Float = 0f,
    val isSensorCalibrated: Boolean = false,
    val isCalibrating: Boolean = false,

    // Vídeo & Sincronización (Fase 4)
    val isVideoEnabled: Boolean = false,
    val isVideoRecording: Boolean = false,
    val videoDurationSec: Long = 0L,
    val videoFilePath: String? = null,
    val videoStartMonotonicNanos: Long? = null,
    val videoStartEpochMillis: Long? = null,

    // OBD-II Veepeak BLE (Fase 6)
    val isObdConnected: Boolean = false,
    val engineRpm: Int? = null,
    val obdSpeedKmh: Float? = null,
    val throttlePercent: Float? = null,
    val coolantTempC: Int? = null,
    val engineLoadPercent: Float? = null,
    val obdRealHz: Float = 0f,
    val obdProtocolName: String? = null
)
