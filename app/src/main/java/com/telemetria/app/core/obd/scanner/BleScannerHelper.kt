package com.telemetria.app.core.obd.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BleScannerHelper(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun scanForObdDevices(scanDurationMillis: Long = 10000L): Flow<List<DiscoveredBleDevice>> = callbackFlow {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null || !isBluetoothEnabled) {
            close(IllegalStateException("Bluetooth LE Scanner no disponible"))
            return@callbackFlow
        }

        val discoveredMap = mutableMapOf<String, DiscoveredBleDevice>()

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
                close(RuntimeException("Escaneo BLE fallido: $errorCode"))
            }
        }

        scanner.startScan(callback)

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {}
        }, scanDurationMillis)

        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {}
        }
    }
}

