package com.telemetria.app.core.sensor.model

data class CalibratedGForces(
    val lateralG: Float = 0f,
    val longitudinalG: Float = 0f,
    val verticalG: Float = 0f,
    val gpsDerivedLongitudinalG: Float? = null,
    val isCalibrated: Boolean = false
)

