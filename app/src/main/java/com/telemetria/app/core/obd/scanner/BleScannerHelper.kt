package com.telemetria.app.core.obd.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BleScannerHelper(private val context: Context) {

    private val bluetoothManager: BluetoothManager?
        get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun scanForObdDevices(scanDurationMillis: Long = 10000L): Flow<List<DiscoveredBleDevice>> = callbackFlow {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            close(IllegalStateException("El Bluetooth del móvil está desactivado. Actívalo en los ajustes rápidos."))
            return@callbackFlow
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Escáner Bluetooth LE no disponible en este dispositivo."))
            return@callbackFlow
        }

        val discoveredMap = mutableMapOf<String, DiscoveredBleDevice>()

        // 1. Mostrar dispositivos ya emparejados en el sistema (ej. si el Veepeak ya fue vinculado)
        try {
            adapter.bondedDevices?.forEach { device ->
                val name = device.name ?: "Dispositivo Vinculado"
                discoveredMap[device.address] = DiscoveredBleDevice(
                    name = "$name (Emparejado)",
                    address = device.address,
                    rssi = -50
                )
            }
            if (discoveredMap.isNotEmpty()) {
                trySend(discoveredMap.values.toList())
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: result.scanRecord?.deviceName ?: "Dispositivo BLE"
                val address = device.address

                val discovered = DiscoveredBleDevice(
                    name = name,
                    address = address,
                    rssi = result.rssi
                )

                discoveredMap[address] = discovered
                trySend(discoveredMap.values.sortedByDescending { it.rssi })
            }

            override fun onScanFailed(errorCode: Int) {
                val message = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "El escaneo Bluetooth ya está en curso."
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "El subsistema Bluetooth del teléfono está ocupado. Prueba a desactivar y reactivar el Bluetooth en tu móvil o vuelve a pulsar Escanear."
                    SCAN_FAILED_INTERNAL_ERROR -> "Error interno del chip Bluetooth."
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE no soportado por este dispositivo."
                    SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Sin recursos de hardware Bluetooth disponibles."
                    else -> "Fallo de escaneo Bluetooth (código $errorCode)"
                }
                close(RuntimeException(message))
            }
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, scanSettings, callback)
        } catch (e: SecurityException) {
            close(IllegalStateException("Permiso de Bluetooth no concedido"))
            return@callbackFlow
        } catch (e: Exception) {
            close(RuntimeException("No se pudo iniciar el escaneo BLE: ${e.message}"))
            return@callbackFlow
        }

        val handler = Handler(Looper.getMainLooper())
        val stopRunnable = Runnable {
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {}
        }
        handler.postDelayed(stopRunnable, scanDurationMillis)

        awaitClose {
            handler.removeCallbacks(stopRunnable)
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {}
        }
    }
}
