package com.telemetria.app.core.model

enum class EngineAspiration(val displayName: String) {
    NATURALLY_ASPIRATED("Atmosférico (NA)"),
    TURBOCHARGED("Turbo / Sobrealimentado")
}

/**
 * Configuración del vehículo del usuario: tacómetro, régimen de corte,
 * luces de cambio y tipo de aspiración del motor (atmosférico o turbo).
 */
data class VehicleConfig(
    val maxRpm: Int = 7500,
    val redlineRpm: Int = 6500,
    val shiftLightRpm: Int = (redlineRpm - 500).coerceAtLeast(1000),
    val aspiration: EngineAspiration = EngineAspiration.NATURALLY_ASPIRATED
) {
    init {
        require(maxRpm > 0) { "maxRpm debe ser mayor a 0" }
        require(redlineRpm in 1..maxRpm) { "redlineRpm debe estar entre 1 y maxRpm" }
        require(shiftLightRpm in 1..maxRpm) { "shiftLightRpm debe estar entre 1 y maxRpm" }
    }

    companion object {
        const val SHIFT_LIGHT_OFFSET_RPM = 500

        val DEFAULT = VehicleConfig(
            maxRpm = 7500,
            redlineRpm = 6500,
            shiftLightRpm = 6000,
            aspiration = EngineAspiration.NATURALLY_ASPIRATED
        )
    }
}

