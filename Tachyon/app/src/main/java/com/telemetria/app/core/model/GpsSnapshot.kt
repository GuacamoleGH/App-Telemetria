package com.telemetria.app.core.model

/**
 * Representa una captura instantánea de ubicación recibida directamente del GPS / FusedLocationProvider.
 * Se guardan marcas de tiempo duales (UTC y monotónica) para sincronización determinista con vídeo y OBD.
 */
data class GpsSnapshot(
    val timestampMillis: Long,
    val monotonicNanos: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val speedKmh: Float?,
    val bearingDegrees: Float?,
    val accuracyMeters: Float?,
    val isRealFix: Boolean
)

