package com.telemetria.app.core.obd.model

data class ObdData(
    val engineRpm: Int? = null,
    val obdSpeedKmh: Float? = null,
    val throttlePercent: Float? = null,
    val coolantTempC: Int? = null,
    val engineLoadPercent: Float? = null,
    val realHz: Float = 0f,
    val lastUpdateMonoNanos: Long = 0L,
    val isConnected: Boolean = false
)

