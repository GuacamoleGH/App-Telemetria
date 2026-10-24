package com.telemetria.app.core.preferences

import android.content.Context
import android.content.SharedPreferences
import com.telemetria.app.core.model.VehicleConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VehiclePreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _vehicleConfig = MutableStateFlow(loadConfig())
    val vehicleConfig: StateFlow<VehicleConfig> = _vehicleConfig.asStateFlow()

    private fun loadConfig(): VehicleConfig {
        val maxRpm = prefs.getInt(KEY_MAX_RPM, VehicleConfig.DEFAULT.maxRpm)
        val redlineRpm = prefs.getInt(KEY_REDLINE_RPM, VehicleConfig.DEFAULT.redlineRpm)
        // Aviso de cambio siempre a 500 RPM antes de la línea roja
        val shiftLightRpm = (redlineRpm - VehicleConfig.SHIFT_LIGHT_OFFSET_RPM).coerceAtLeast(1000)

        return try {
            VehicleConfig(
                maxRpm = maxRpm,
                redlineRpm = redlineRpm.coerceAtMost(maxRpm),
                shiftLightRpm = shiftLightRpm.coerceAtMost(maxRpm)
            )
        } catch (_: Exception) {
            VehicleConfig.DEFAULT
        }
    }

    fun saveConfig(config: VehicleConfig) {
        prefs.edit()
            .putInt(KEY_MAX_RPM, config.maxRpm)
            .putInt(KEY_REDLINE_RPM, config.redlineRpm)
            .putInt(KEY_SHIFT_LIGHT_RPM, config.shiftLightRpm)
            .apply()

        _vehicleConfig.value = config
    }

    companion object {
        private const val PREFS_NAME = "telemetria_vehicle_prefs"
        private const val KEY_MAX_RPM = "vehicle_max_rpm"
        private const val KEY_REDLINE_RPM = "vehicle_redline_rpm"
        private const val KEY_SHIFT_LIGHT_RPM = "vehicle_shift_light_rpm"

        @Volatile
        private var INSTANCE: VehiclePreferences? = null

        fun getInstance(context: Context): VehiclePreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VehiclePreferences(context).also { INSTANCE = it }
            }
        }
    }
}

