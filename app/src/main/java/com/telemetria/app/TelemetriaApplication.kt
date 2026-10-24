package com.telemetria.app

import android.app.Application
import com.telemetria.app.core.database.TelemetryDatabase

class TelemetriaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicializar instancia de base de datos
        TelemetryDatabase.getInstance(this)
    }
}

