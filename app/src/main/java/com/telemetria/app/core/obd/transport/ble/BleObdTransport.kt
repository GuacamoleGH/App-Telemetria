package com.telemetria.app.core.obd.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.telemetria.app.core.obd.model.ObdConnectionState
import com.telemetria.app.core.obd.transport.ObdTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.charset.StandardCharsets
import java.util.UUID

@SuppressLint("MissingPermission")
class BleObdTransport(private val context: Context) : ObdTransport {

    companion object {
        // Descriptores estándar de notificación Bluetooth SIG
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Perfiles conocidos candidatos
        // 1. Veepeak OBDCheck BLE / Vgate iCar Pro BLE / IOS-VLINK
        val VEEPEAK_SERVICE_UUID: UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")
        val VEEPEAK_NOTIFY_CHAR_UUID: UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB")
        val VEEPEAK_WRITE_CHAR_UUID: UUID = UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB")

        // 2. Nordic UART Service
        val NORDIC_UART_SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val NORDIC_RX_WRITE_CHAR: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val NORDIC_TX_NOTIFY_CHAR: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow<ObdConnectionState>(ObdConnectionState.Disconnected)
    override val connectionState: StateFlow<ObdConnectionState> = _connectionState.asStateFlow()

    override val isConnected: Boolean
        get() = _connectionState.value is ObdConnectionState.Connected || _connectionState.value is ObdConnectionState.InitializingElm327

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    private val commandMutex = Mutex()
    private var pendingResponseDeferred: CompletableDeferred<String>? = null
    private val responseBuffer = StringBuilder()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ObdConnectionState.DiscoveringServices("Conectado a GATT. Descubriendo servicios...")
                    // Solicitar aumento de MTU para acelerar transferencias
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        gatt.requestMtu(256)
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ObdConnectionState.Disconnected
                    cleanup()
                }
                else -> {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        _connectionState.value = ObdConnectionState.Error("Error GATT de conexión: $status")
                        cleanup()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ObdConnectionState.Error("Fallo al descubrir servicios GATT: $status")
                return
            }

            // Descubrimiento dinámico de características Write y Notify
            val discovered = resolveGattCharacteristics(gatt)
            if (discovered) {
                _connectionState.value = ObdConnectionState.InitializingElm327
            } else {
                _connectionState.value = ObdConnectionState.Error("No se encontraron características GATT compatibles de transmisión OBD-II")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            onCharacteristicReceived(characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            onCharacteristicReceived(value)
        }

        private fun onCharacteristicReceived(value: ByteArray?) {
            if (value == null || value.isEmpty()) return
            val chunk = String(value, StandardCharsets.US_ASCII)

            synchronized(responseBuffer) {
                responseBuffer.append(chunk)
                // En protocolo ELM327, el carácter '>' indica fin de respuesta de la ECU
                if (responseBuffer.contains(">")) {
                    val fullResponse = responseBuffer.toString()
                    responseBuffer.setLength(0)
                    pendingResponseDeferred?.complete(fullResponse)
                }
            }
        }
    }

    /**
     * Detección dinámica de características GATT:
     * 1. Prueba los perfiles conocidos (Veepeak FFF0, Nordic UART).
     * 2. Si no coinciden, recorre dinámicamente todos los servicios y busca una con NOTIFY y otra con WRITE.
     */
    private fun resolveGattCharacteristics(gatt: BluetoothGatt): Boolean {
        // Opción 1: Comprobar servicio Veepeak FFF0
        val veepeakService = gatt.getService(VEEPEAK_SERVICE_UUID)
        if (veepeakService != null) {
            val notify = veepeakService.getCharacteristic(VEEPEAK_NOTIFY_CHAR_UUID)
            val write = veepeakService.getCharacteristic(VEEPEAK_WRITE_CHAR_UUID)
            if (notify != null && write != null) {
                return setupCharacteristics(gatt, notify, write)
            }
        }

        // Opción 2: Comprobar Nordic UART
        val nordicService = gatt.getService(NORDIC_UART_SERVICE)
        if (nordicService != null) {
            val notify = nordicService.getCharacteristic(NORDIC_TX_NOTIFY_CHAR)
            val write = nordicService.getCharacteristic(NORDIC_RX_WRITE_CHAR)
            if (notify != null && write != null) {
                return setupCharacteristics(gatt, notify, write)
            }
        }

        // Opción 3: Detección dinámica automática
        var candidateNotify: BluetoothGattCharacteristic? = null
        var candidateWrite: BluetoothGattCharacteristic? = null

        for (service in gatt.services) {
            for (char in service.characteristics) {
                val props = char.properties
                val hasNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                        (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                val hasWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                        (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

                if (hasNotify && candidateNotify == null) {
                    candidateNotify = char
                }
                if (hasWrite && candidateWrite == null) {
                    candidateWrite = char
                }
            }
            if (candidateNotify != null && candidateWrite != null) {
                break
            }
        }

        if (candidateNotify != null && candidateWrite != null) {
            return setupCharacteristics(gatt, candidateNotify, candidateWrite)
        }

        return false
    }

    private fun setupCharacteristics(
        gatt: BluetoothGatt,
        notify: BluetoothGattCharacteristic,
        write: BluetoothGattCharacteristic
    ): Boolean {
        notifyCharacteristic = notify
        writeCharacteristic = write

        // Habilitar notificaciones en el stack Bluetooth de Android
        val notificationSet = gatt.setCharacteristicNotification(notify, true)
        if (!notificationSet) return false

        // Habilitar CCCD en el descriptor del dispositivo remoto
        val descriptor = notify.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }

        return true
    }

    override suspend fun connect(target: String): Boolean = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter ?: run {
            _connectionState.value = ObdConnectionState.Error("Bluetooth no disponible en el dispositivo")
            return@withContext false
        }

        if (!adapter.isEnabled) {
            _connectionState.value = ObdConnectionState.Error("Bluetooth desactivado")
            return@withContext false
        }

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(target)
        } catch (e: Exception) {
            _connectionState.value = ObdConnectionState.Error("Dirección MAC inválida: $target")
            return@withContext false
        }

        _connectionState.value = ObdConnectionState.Connecting(device.name ?: "Veepeak BLE", device.address)

        cleanup()

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }

        true
    }

    override suspend fun sendCommand(command: String, timeoutMillis: Long): String = commandMutex.withLock {
        withContext(Dispatchers.IO) {
            val gatt = bluetoothGatt ?: throw IllegalStateException("GATT no conectado")
            val write = writeCharacteristic ?: throw IllegalStateException("Característica Write no configurada")

            synchronized(responseBuffer) {
                responseBuffer.setLength(0)
            }

            val deferred = CompletableDeferred<String>()
            pendingResponseDeferred = deferred

            val formattedCmd = if (command.endsWith("\r")) command else "$command\r"
            val bytes = formattedCmd.toByteArray(StandardCharsets.US_ASCII)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    write,
                    bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                @Suppress("DEPRECATION")
                write.value = bytes
                @Suppress("DEPRECATION")
                write.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(write)
            }

            val response = withTimeoutOrNull(timeoutMillis) {
                deferred.await()
            } ?: "TIMEOUT"

            pendingResponseDeferred = null
            response
        }
    }

    override fun disconnect() {
        _connectionState.value = ObdConnectionState.Disconnected
        cleanup()
    }

    private fun cleanup() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            bluetoothGatt = null
            writeCharacteristic = null
            notifyCharacteristic = null
            synchronized(responseBuffer) { responseBuffer.setLength(0) }
            pendingResponseDeferred?.cancel()
            pendingResponseDeferred = null
        }
    }
}

