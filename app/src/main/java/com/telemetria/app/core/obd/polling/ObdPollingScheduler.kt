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
            var latestIat: Int? = null
            var latestMap: Float? = null
            var latestMaf: Float? = null
            var latestFuelRate: Float? = null
            var latestBaro: Float? = 101.3f

            while (isActive && transport.isConnected) {
                val cycleStart = SystemClock.elapsedRealtimeNanos()
                loopCycle++

                // 1. ALTA PRIORIDAD: RPM (en cada ciclo para máxima fidelidad en tacómetro y shift lights)
                try {
                    val rawRpm = transport.sendCommand("010C", timeoutMillis = 1000L)
                    protocol.parseRpm(rawRpm)?.let { latestRpm = it }
                    recordQuerySuccess()
                } catch (_: Exception) {}

                // 2. ALTA PRIORIDAD: Pedal / Mariposa de aceleración
                try {
                    val throttlePid = if (protocol.isPidSupported("0149")) "0149" else "0111"
                    val rawThrottle = transport.sendCommand(throttlePid, timeoutMillis = 1000L)
                    protocol.parseThrottle(rawThrottle)?.let { latestThrottle = it }
                    recordQuerySuccess()
                } catch (_: Exception) {}

                // 3. MEDIA PRIORIDAD: Velocidad OBD y Presión MAP / Turbo (cada 2 ciclos)
                if (loopCycle % 2 == 0) {
                    try {
                        val rawSpeed = transport.sendCommand("010D", timeoutMillis = 1000L)
                        protocol.parseSpeed(rawSpeed)?.let { latestSpeed = it }
                        recordQuerySuccess()
                    } catch (_: Exception) {}

                    if (protocol.isPidSupported("010B")) {
                        try {
                            val rawMap = transport.sendCommand("010B", timeoutMillis = 1000L)
                            protocol.parseMap(rawMap)?.let { latestMap = it }
                            recordQuerySuccess()
                        } catch (_: Exception) {}
                    }
                }

                // 4. PRIORIDAD REGULAR: Consumo de combustible en tiempo real (cada 4 ciclos)
                if (loopCycle % 4 == 0) {
                    if (protocol.isPidSupported("015E")) {
                        try {
                            val rawFuelRate = transport.sendCommand("015E", timeoutMillis = 1000L)
                            protocol.parseFuelRate(rawFuelRate)?.let { latestFuelRate = it }
                            recordQuerySuccess()
                        } catch (_: Exception) {}
                    } else if (protocol.isPidSupported("0110")) {
                        try {
                            val rawMaf = transport.sendCommand("0110", timeoutMillis = 1000L)
                            protocol.parseMaf(rawMaf)?.let { latestMaf = it }
                            recordQuerySuccess()
                        } catch (_: Exception) {}
                    }
                }

                // 5. BAJA PRIORIDAD AMORTIZADA (consultas distribuidas cada 15 ciclos para no generar picos de latencia)
                when (loopCycle % 15) {
                    0 -> {
                        try {
                            val rawCoolant = transport.sendCommand("0105", timeoutMillis = 1500L)
                            protocol.parseCoolantTemp(rawCoolant)?.let { latestCoolant = it }
                            recordQuerySuccess()
                        } catch (_: Exception) {}
                    }
                    3 -> {
                        try {
                            val rawLoad = transport.sendCommand("0104", timeoutMillis = 1500L)
                            protocol.parseEngineLoad(rawLoad)?.let { latestLoad = it }
                            recordQuerySuccess()
                        } catch (_: Exception) {}
                    }
                    6 -> {
                        if (protocol.isPidSupported("010F")) {
                            try {
                                val rawIat = transport.sendCommand("010F", timeoutMillis = 1500L)
                                protocol.parseIntakeAirTemp(rawIat)?.let { latestIat = it }
                                recordQuerySuccess()
                            } catch (_: Exception) {}
                        }
                    }
                    9 -> {
                        if (protocol.isPidSupported("0133")) {
                            try {
                                val rawBaro = transport.sendCommand("0133", timeoutMillis = 1500L)
                                protocol.parseBarometricPressure(rawBaro)?.let { latestBaro = it }
                                recordQuerySuccess()
                            } catch (_: Exception) {}
                        }
                    }
                }

                // Cálculos sintéticos instantáneos (coste 0 en bus CAN):
                val gear = Elm327Protocol.calculateGear(latestSpeed, latestRpm)
                val boostBar = Elm327Protocol.calculateBoostBar(latestMap, latestBaro)
                val (fuelLh, fuelL100km) = Elm327Protocol.calculateFuelConsumption(latestSpeed, latestMaf, latestFuelRate)

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
                        intakeAirTempC = latestIat,
                        mapKpa = latestMap,
                        boostPressureBar = boostBar,
                        fuelRateLh = fuelLh,
                        fuelConsumptionL100km = fuelL100km,
                        calculatedGear = gear,
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

