package com.telemetria.app.core.model

data class Session(
    val id: String,
    val title: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long? = null,
    val startMonotonicNanos: Long,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val totalDistanceMeters: Double = 0.0,
    val maxSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val durationSeconds: Long = 0,
    val pointCount: Long = 0,
    val notes: String = "",

    // Vídeo y sincronización temporal (Fase 4)
    val videoFilePath: String? = null,
    val videoStartMonotonicNanos: Long? = null,
    val videoStartEpochMillis: Long? = null
)
