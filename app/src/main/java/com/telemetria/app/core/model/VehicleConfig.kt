package com.telemetria.app.core.model

/**
 * Configuración de tacómetro y régimen de revoluciones específico del vehículo del usuario.
 * Permite calibrar la escala del tacómetro, el corte/zona roja y las luces de cambio tipo competición.
 */
data class VehicleConfig(
    val maxRpm: Int = 7500,
    val redlineRpm: Int = 6500,
    val shiftLightRpm: Int = (redlineRpm - 500).coerceAtLeast(1000)
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
            shiftLightRpm = 6000
        )
    }
}

