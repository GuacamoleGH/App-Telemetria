package com.telemetria.app.core.model

/**
 * Punto de telemetría consolidado.
 * Separa explícitamente las fuentes: datos GPS, datos inerciales y datos OBD-II.
 * NUNCA se rellenan valores de sensores no disponibles.
 */
data class TelemetryPoint(
    val id: Long = 0,
    val sessionId: String,
    val timestampMillis: Long,
    val monotonicNanos: Long,

    // GPS Data
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val gpsSpeedKmh: Float? = null,
    val bearingDegrees: Float? = null,
    val accuracyMeters: Float? = null,
    val hasGpsFix: Boolean = false,

    // IMU / Inertial Sensors (preparado para F3)
    val accelX: Float? = null,
    val accelY: Float? = null,
    val accelZ: Float? = null,
    val lateralG: Float? = null,
    val longitudinalG: Float? = null,

    // OBD-II (preparado para F6)
    val engineRpm: Int? = null,
    val obdSpeedKmh: Float? = null,
    val throttlePercent: Float? = null,
    val coolantTempC: Int? = null,
    val engineLoadPercent: Float? = null
)

