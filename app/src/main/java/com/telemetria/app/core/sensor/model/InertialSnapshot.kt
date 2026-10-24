package com.telemetria.app.core.sensor.model

/**
 * Muestra instantánea de sensores inerciales (IMU).
 * Contiene tanto los valores crudos del sensor como la aceleración lineal desprovista de gravedad.
 */
data class InertialSnapshot(
    val timestampMillis: Long,
    val monotonicNanos: Long,
    // Aceleración lineal del dispositivo (sin gravedad, marco del móvil) en m/s²
    val linearAccelX: Float,
    val linearAccelY: Float,
    val linearAccelZ: Float,
    // Aceleración bruta (incluyendo gravedad 9.81 m/s²) en m/s²
    val rawAccelX: Float,
    val rawAccelY: Float,
    val rawAccelZ: Float,
    // Velocidad angular (Giroscopio) en rad/s
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    // Vector de rotación (cuaternión x, y, z, w) si está disponible
    val rotationQuaternion: FloatArray? = null
)
