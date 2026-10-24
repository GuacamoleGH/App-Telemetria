package com.telemetria.app.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.telemetria.app.core.sensor.model.InertialSnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class InertialSensorManager(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val linearAccelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rawAccelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    val isAvailable: Boolean
        get() = sensorManager != null && (linearAccelSensor != null || rawAccelSensor != null)

    fun observeInertialSensors(samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_GAME): Flow<InertialSnapshot> = callbackFlow {
        val manager = sensorManager
        if (manager == null) {
            close()
            return@callbackFlow
        }

        // Almacenamiento volátil de los últimos valores para correlacionar la muestra
        var latestLinearX = 0f
        var latestLinearY = 0f
        var latestLinearZ = 0f

        var latestRawX = 0f
        var latestRawY = 0f
        var latestRawZ = 0f

        var latestGyroX = 0f
        var latestGyroY = 0f
        var latestGyroZ = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val nowMonoNanos = event.timestamp.takeIf { it > 0 } ?: SystemClock.elapsedRealtimeNanos()
                val nowMillis = System.currentTimeMillis()

                when (event.sensor.type) {
                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        latestLinearX = event.values[0]
                        latestLinearY = event.values[1]
                        latestLinearZ = event.values[2]

                        // Emitir en cada ciclo de aceleración lineal
                        val snapshot = InertialSnapshot(
                            timestampMillis = nowMillis,
                            monotonicNanos = nowMonoNanos,
                            linearAccelX = latestLinearX,
                            linearAccelY = latestLinearY,
                            linearAccelZ = latestLinearZ,
                            rawAccelX = latestRawX,
                            rawAccelY = latestRawY,
                            rawAccelZ = latestRawZ,
                            gyroX = latestGyroX,
                            gyroY = latestGyroY,
                            gyroZ = latestGyroZ
                        )
                        trySend(snapshot)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        latestRawX = event.values[0]
                        latestRawY = event.values[1]
                        latestRawZ = event.values[2]

                        // Si el dispositivo no posee sensor TYPE_LINEAR_ACCELERATION, emitir desde raw
                        if (linearAccelSensor == null) {
                            val snapshot = InertialSnapshot(
                                timestampMillis = nowMillis,
                                monotonicNanos = nowMonoNanos,
                                linearAccelX = latestRawX,
                                linearAccelY = latestRawY,
                                linearAccelZ = latestRawZ,
                                rawAccelX = latestRawX,
                                rawAccelY = latestRawY,
                                rawAccelZ = latestRawZ,
                                gyroX = latestGyroX,
                                gyroY = latestGyroY,
                                gyroZ = latestGyroZ
                            )
                            trySend(snapshot)
                        }
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        latestGyroX = event.values[0]
                        latestGyroY = event.values[1]
                        latestGyroZ = event.values[2]
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        linearAccelSensor?.let { manager.registerListener(listener, it, samplingPeriodUs) }
        rawAccelSensor?.let { manager.registerListener(listener, it, samplingPeriodUs) }
        gyroSensor?.let { manager.registerListener(listener, it, samplingPeriodUs) }

        awaitClose {
            manager.unregisterListener(listener)
        }
    }
}

