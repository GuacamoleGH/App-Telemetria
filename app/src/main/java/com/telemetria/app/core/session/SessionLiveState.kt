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
    val pointCount: Long = 0
)

