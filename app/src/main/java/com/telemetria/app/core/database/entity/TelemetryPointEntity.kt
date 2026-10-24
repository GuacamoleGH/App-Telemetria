package com.telemetria.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.telemetria.app.core.model.TelemetryPoint

@Entity(
    tableName = "telemetry_points",
    indices = [
        Index(value = ["sessionId", "timestampMillis"]),
        Index(value = ["sessionId", "monotonicNanos"])
    ]
)
data class TelemetryPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val timestampMillis: Long,
    val monotonicNanos: Long,

    // GPS
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val gpsSpeedKmh: Float? = null,
    val bearingDegrees: Float? = null,
    val accuracyMeters: Float? = null,
    val hasGpsFix: Boolean = false,

    // IMU
    val accelX: Float? = null,
    val accelY: Float? = null,
    val accelZ: Float? = null,
    val lateralG: Float? = null,
    val longitudinalG: Float? = null,

    // OBD
    val engineRpm: Int? = null,
    val obdSpeedKmh: Float? = null,
    val throttlePercent: Float? = null,
    val coolantTempC: Int? = null,
    val engineLoadPercent: Float? = null
) {
    fun toDomain(): TelemetryPoint = TelemetryPoint(
        id = id,
        sessionId = sessionId,
        timestampMillis = timestampMillis,
        monotonicNanos = monotonicNanos,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitudeMeters,
        gpsSpeedKmh = gpsSpeedKmh,
        bearingDegrees = bearingDegrees,
        accuracyMeters = accuracyMeters,
        hasGpsFix = hasGpsFix,
        accelX = accelX,
        accelY = accelY,
        accelZ = accelZ,
        lateralG = lateralG,
        longitudinalG = longitudinalG,
        engineRpm = engineRpm,
        obdSpeedKmh = obdSpeedKmh,
        throttlePercent = throttlePercent,
        coolantTempC = coolantTempC,
        engineLoadPercent = engineLoadPercent
    )

    companion object {
        fun fromDomain(point: TelemetryPoint): TelemetryPointEntity = TelemetryPointEntity(
            id = point.id,
            sessionId = point.sessionId,
            timestampMillis = point.timestampMillis,
            monotonicNanos = point.monotonicNanos,
            latitude = point.latitude,
            longitude = point.longitude,
            altitudeMeters = point.altitudeMeters,
            gpsSpeedKmh = point.gpsSpeedKmh,
            bearingDegrees = point.bearingDegrees,
            accuracyMeters = point.accuracyMeters,
            hasGpsFix = point.hasGpsFix,
            accelX = point.accelX,
            accelY = point.accelY,
            accelZ = point.accelZ,
            lateralG = point.lateralG,
            longitudinalG = point.longitudinalG,
            engineRpm = point.engineRpm,
            obdSpeedKmh = point.obdSpeedKmh,
            throttlePercent = point.throttlePercent,
            coolantTempC = point.coolantTempC,
            engineLoadPercent = point.engineLoadPercent
        )
    }
}

