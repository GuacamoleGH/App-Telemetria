package com.telemetria.app.core.obd

import android.content.Context
import com.telemetria.app.core.obd.model.ObdConnectionState
import com.telemetria.app.core.obd.model.ObdData
import com.telemetria.app.core.obd.polling.ObdPollingScheduler
import com.telemetria.app.core.obd.protocol.Elm327Protocol
import com.telemetria.app.core.obd.scanner.BleScannerHelper
import com.telemetria.app.core.obd.scanner.DiscoveredBleDevice
import com.telemetria.app.core.obd.transport.ble.BleObdTransport
import com.telemetria.app.core.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ObdManager private constructor(
    private val context: Context,
    private val sessionManager: SessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val bleTransport = BleObdTransport(context)
    val elm327Protocol = Elm327Protocol(bleTransport)
    val pollingScheduler = ObdPollingScheduler(bleTransport, elm327Protocol, scope)
    val bleScannerHelper = BleScannerHelper(context)

    private val _connectionState = MutableStateFlow<ObdConnectionState>(ObdConnectionState.Disconnected)
    val connectionState: StateFlow<ObdConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices.asStateFlow()

    private var scanJob: Job? = null
    private var dataObserverJob: Job? = null

    init {
        // Observar estado del transporte y arrancar inicialización / bucle de consulta
        scope.launch {
            bleTransport.connectionState.collectLatest { state ->
                _connectionState.value = state
                when (state) {
                    is ObdConnectionState.InitializingElm327 -> {
                        val initSuccess = elm327Protocol.initialize()
                        if (initSuccess) {
                            _connectionState.value = ObdConnectionState.Connected(
                                protocolName = elm327Protocol.detectedProtocol,
                                supportedPidsCount = elm327Protocol.supportedPids.size
                            )
                            startPollingLoop()
                        } else {
                            _connectionState.value = ObdConnectionState.Error("Fallo de inicialización ELM327 / Bus no responde")
                            bleTransport.disconnect()
                        }
                    }
                    is ObdConnectionState.Disconnected -> {
                        pollingScheduler.stopPolling()
                        dataObserverJob?.cancel()
                    }
                    else -> {}
                }
            }
        }
    }

    fun startBleScan(durationMillis: Long = 10000L) {
        scanJob?.cancel()
        _connectionState.value = ObdConnectionState.Scanning
        scanJob = scope.launch {
            try {
                bleScannerHelper.scanForObdDevices(durationMillis).collect { list ->
                    _discoveredDevices.value = list
                }
            } catch (e: Exception) {
                _connectionState.value = ObdConnectionState.Error("Error escaneo BLE: ${e.message}")
            }
        }
    }

    fun stopBleScan() {
        scanJob?.cancel()
        scanJob = null
        if (_connectionState.value is ObdConnectionState.Scanning) {
            _connectionState.value = ObdConnectionState.Disconnected
        }
    }

    fun connectDevice(deviceAddress: String) {
        stopBleScan()
        scope.launch {
            bleTransport.connect(deviceAddress)
        }
    }

    private fun startPollingLoop() {
        pollingScheduler.startPolling()
        dataObserverJob?.cancel()
        dataObserverJob = scope.launch {
            pollingScheduler.obdData.collectLatest { data ->
                sessionManager.onObdReceived(data)
            }
        }
    }

    fun disconnect() {
        pollingScheduler.stopPolling()
        dataObserverJob?.cancel()
        bleTransport.disconnect()
        _connectionState.value = ObdConnectionState.Disconnected
    }

    companion object {
        @Volatile
        private var INSTANCE: ObdManager? = null

        fun getInstance(context: Context, sessionManager: SessionManager): ObdManager {
            return INSTANCE ?: synchronized(this) {
                val instance = ObdManager(context.applicationContext, sessionManager)
                INSTANCE = instance
                instance
            }
        }
    }
}

