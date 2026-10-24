package com.telemetria.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.telemetria.app.core.location.BatteryOptimizationHelper
import com.telemetria.app.core.location.LocationPermissionHelper
import com.telemetria.app.ui.navigation.AppNavigation
import com.telemetria.app.ui.theme.CockpitBlack
import com.telemetria.app.ui.theme.TelemetriaTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permisos procesados
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mantener la pantalla encendida de forma segura durante la conducción
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Comprobar y solicitar permisos de ubicación y notificación
        if (!LocationPermissionHelper.hasLocationPermissions(this) ||
            !LocationPermissionHelper.hasNotificationPermission(this)
        ) {
            permissionLauncher.launch(LocationPermissionHelper.getRequiredPermissions())
        }

        // Sugerir exclusión de optimización de batería para evitar que el fabricante mate el GPS
        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
            try {
                startActivity(BatteryOptimizationHelper.createRequestIgnoreBatteryOptimizationsIntent(this))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setContent {
            TelemetriaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CockpitBlack
                ) {
                    val navController = rememberNavController()
                    AppNavigation(navController = navController)
                }
            }
        }
    }
}

