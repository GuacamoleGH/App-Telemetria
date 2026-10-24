package com.telemetria.app.core.obd.polling

import android.os.SystemClock
import com.telemetria.app.core.obd.model.ObdData
import com.telemetria.app.core.obd.protocol.Elm327Protocol
import com.telemetria.app.core.obd.transport.ObdTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ObdPollingScheduler(
    private val transport: ObdTransport,
    private val protocol: Elm327Protocol,
    private val scope: CoroutineScope
) {
    private val _obdData = MutableStateFlow(ObdData())
    val obdData: StateFlow<ObdData> = _obdData.asStateFlow()

    private var pollingJob: Job? = null

    // Variables de medición de frecuencia real
    private var queryCountWindow = 0
    private var windowStartMillis = 0L

    fun startPolling() {
        pollingJob?.cancel()
        queryCountWindow = 0
        windowStartMillis = System.currentTimeMillis()

        pollingJob = scope.launch(Dispatchers.IO) {
            var loopCycle = 0

            var latestRpm: Int? = null
            var latestSpeed: Float? = null
            var latestThrottle: Float? = null
            var latestCoolant: Int? = null
            var latestLoad: Float? = null

            while (isActive && transport.isConnected) {
                val cycleStart = SystemClock.elapsedRealtimeNanos()
                loopCycle++

                // 1. ALTA PRIORIDAD: RPM (siempre en cada ciclo impar)
                try {
                    val rawRpm = transport.sendCommand("010C", timeoutMillis = 1500L)
                    protocol.parseRpm(rawRpm)?.let { latestRpm = it }
                    recordQuerySuccess()
                } catch (e: Exception) {
                    // Timeout o desconexión
                }

                // 2. ALTA PRIORIDAD: Pedal / Mariposa del acelerador
                try {
                    val throttlePid = if (protocol.isPidSupported("0149")) "0149" else "0111"
                    val rawThrottle = transport.sendCommand(throttlePid, timeoutMillis = 1500L)
                    protocol.parseThrottle(rawThrottle)?.let { latestThrottle = it }
                    recordQuerySuccess()
                } catch (e: Exception) {
                    // Ignorar fallo puntual
                }

                // 3. MEDIA PRIORIDAD: Velocidad OBD (cada 2 ciclos)
                if (loopCycle % 2 == 0) {
                    try {
                        val rawSpeed = transport.sendCommand("010D", timeoutMillis = 1500L)
                        protocol.parseSpeed(rawSpeed)?.let { latestSpeed = it }
                        recordQuerySuccess()
                    } catch (_: Exception) {}
                }

                // 4. BAJA PRIORIDAD: Temperatura refrigerante y carga motor (cada 15 ciclos ~ 3-5s)
                if (loopCycle % 15 == 0) {
                    try {
                        val rawCoolant = transport.sendCommand("0105", timeoutMillis = 2000L)
                        protocol.parseCoolantTemp(rawCoolant)?.let { latestCoolant = it }
                        recordQuerySuccess()

                        val rawLoad = transport.sendCommand("0104", timeoutMillis = 2000L)
                        protocol.parseEngineLoad(rawLoad)?.let { latestLoad = it }
                        recordQuerySuccess()
                    } catch (_: Exception) {}
                }

                // Calcular frecuencia real en ventana de 2 segundos
                val now = System.currentTimeMillis()
                val windowDurationSec = (now - windowStartMillis) / 1000f
                val realHz = if (windowDurationSec >= 2.0f) {
                    val hz = queryCountWindow / windowDurationSec
                    queryCountWindow = 0
                    windowStartMillis = now
                    hz
                } else {
                    _obdData.value.realHz
                }

                _obdData.update {
                    ObdData(
                        engineRpm = latestRpm,
                        obdSpeedKmh = latestSpeed,
                        throttlePercent = latestThrottle,
                        coolantTempC = latestCoolant,
                        engineLoadPercent = latestLoad,
                        realHz = realHz,
                        lastUpdateMonoNanos = cycleStart,
                        isConnected = true
                    )
                }
            }

            _obdData.update { it.copy(isConnected = false) }
        }
    }

    private fun recordQuerySuccess() {
        queryCountWindow++
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _obdData.update { it.copy(isConnected = false) }
    }
}

