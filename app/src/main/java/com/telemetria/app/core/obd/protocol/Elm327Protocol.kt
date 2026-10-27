package com.telemetria.app.core.obd.protocol

import com.telemetria.app.core.obd.transport.ObdTransport
import kotlinx.coroutines.delay

class Elm327Protocol(private val transport: ObdTransport) {

    // Conjunto de PIDs soportados por la ECU del coche detectados en el escaneo inicial
    val supportedPids = mutableSetOf<String>()
    var detectedProtocol: String = "AUTO"
        private set

    /**
     * Inicializa el chip ELM327 y la conexión con el bus del vehículo.
     * Retorna true si el adaptador responde correctamente a los comandos AT.
     */
    suspend fun initialize(): Boolean {
        // 1. Reset chip
        transport.sendCommand("ATZ", timeoutMillis = 3000L)
        delay(800) // Tiempo de reinicio del microcontrolador

        // 2. Desactivar eco (Echo Off)
        transport.sendCommand("ATE0")

        // 3. Desactivar linefeeds (Linefeeds Off)
        transport.sendCommand("ATL0")

        // 4. Desactivar espacios para mayor tasa de transferencia (Spaces Off)
        transport.sendCommand("ATS0")

        // 5. Desactivar cabeceras CAN para recibir solo los bytes de datos (Headers Off)
        transport.sendCommand("ATH0")

        // 6. Selección automática de protocolo
        transport.sendCommand("ATSP0")

        // Optimización de velocidad y latencia ELM327:
        // - ATAT1: Adaptive Timing rápido (no espera timeouts fijos si la ECU responde rápido)
        // - ATST19: Reducción del timeout a 100 ms (25 x 4ms) para evitar congelaciones en PIDs no soportados
        // - ATCAF1: Formateo automático de tramas CAN
        transport.sendCommand("ATAT1")
        transport.sendCommand("ATST19")
        transport.sendCommand("ATCAF1")

        // 7. Test de conexión con la ECU mediante consulta de PIDs 01-20
        val pids0120 = transport.sendCommand("0100", timeoutMillis = 4000L)
        if (pids0120.contains("UNABLE") || pids0120.contains("ERROR") || pids0120.contains("TIMEOUT")) {
            return false
        }

        parseSupportedPids("0100", pids0120)

        // Comprobar si soporta el siguiente bloque 21-40
        if (isPidSupported("0120")) {
            val pids2140 = transport.sendCommand("0120")
            parseSupportedPids("0120", pids2140)
        }

        // Comprobar si soporta bloque 41-60 (pedal acelerador 0149/014A/014C)
        if (isPidSupported("0140")) {
            val pids4160 = transport.sendCommand("0140")
            parseSupportedPids("0140", pids4160)
        }

        // Obtener descripción del protocolo negociado
        val dp = transport.sendCommand("ATDP")
        detectedProtocol = cleanRawResponse(dp).ifBlank { "ISO/CAN" }

        return true
    }

    fun isPidSupported(pidHex: String): Boolean {
        return supportedPids.contains(pidHex.uppercase())
    }

    /**
     * Parsea la máscara de bits retornada por 0100, 0120, 0140...
     */
    fun parseSupportedPids(basePid: String, rawResponse: String) {
        val clean = cleanRawResponse(rawResponse)
        // Ejemplo respuesta: "4100BE3EB811"
        val expectedPrefix = "41${basePid.substring(2)}"
        val index = clean.indexOf(expectedPrefix)
        if (index == -1) return

        val hexPayload = clean.substring(index + expectedPrefix.length).take(8)
        if (hexPayload.length < 8) return

        try {
            val mask = hexPayload.toLong(16)
            val baseInt = basePid.substring(2).toInt(16)

            for (i in 0 until 32) {
                // Bit más significativo representa pid base + 1
                val isSupported = (mask and (1L shl (31 - i))) != 0L
                if (isSupported) {
                    val pidVal = baseInt + i + 1
                    val pidHex = String.format("01%02X", pidVal)
                    supportedPids.add(pidHex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- MÉTODOS DE PARSEO DE PIDS MÍNIMOS AUTOMOTRICES ---

    fun parseRpm(raw: String): Int? {
        val bytes = extractDataBytes("010C", raw, 2) ?: return null
        val a = bytes[0]
        val b = bytes[1]
        return ((a * 256) + b) / 4
    }

    fun parseSpeed(raw: String): Float? {
        val bytes = extractDataBytes("010D", raw, 1) ?: return null
        return bytes[0].toFloat()
    }

    fun parseThrottle(raw: String): Float? {
        // Soporta 0111 (mariposa clásica) o 0149 (posición pedal relativa)
        val bytes = extractDataBytes("0111", raw, 1) ?: extractDataBytes("0149", raw, 1) ?: return null
        return (bytes[0] * 100f) / 255f
    }

    fun parseCoolantTemp(raw: String): Int? {
        val bytes = extractDataBytes("0105", raw, 1) ?: return null
        return bytes[0] - 40
    }

    fun parseEngineLoad(raw: String): Float? {
        val bytes = extractDataBytes("0104", raw, 1) ?: return null
        return (bytes[0] * 100f) / 255f
    }

    /**
     * Presión Absoluta del Colector de Admisión (MAP) - PID 010B. Retorna kPa (0 a 255 kPa).
     */
    fun parseMap(raw: String): Float? {
        val bytes = extractDataBytes("010B", raw, 1) ?: return null
        return bytes[0].toFloat()
    }

    /**
     * Temperatura del aire de admisión (IAT) - PID 010F. Retorna °C (-40 a 215 °C).
     */
    fun parseIntakeAirTemp(raw: String): Int? {
        val bytes = extractDataBytes("010F", raw, 1) ?: return null
        return bytes[0] - 40
    }

    /**
     * Flujo de masa de aire (MAF) - PID 0110. Retorna g/s (0 a 655.35 g/s).
     */
    fun parseMaf(raw: String): Float? {
        val bytes = extractDataBytes("0110", raw, 2) ?: return null
        val a = bytes[0]
        val b = bytes[1]
        return ((a * 256) + b) / 100f
    }

    /**
     * Consumo directo de combustible (Engine Fuel Rate) - PID 015E. Retorna L/h.
     */
    fun parseFuelRate(raw: String): Float? {
        val bytes = extractDataBytes("015E", raw, 2) ?: return null
        val a = bytes[0]
        val b = bytes[1]
        return ((a * 256) + b) * 0.05f
    }

    /**
     * Presión barométrica ambiental - PID 0133. Retorna kPa (0 a 255 kPa).
     */
    fun parseBarometricPressure(raw: String): Float? {
        val bytes = extractDataBytes("0133", raw, 1) ?: return null
        return bytes[0].toFloat()
    }

    /**
     * Marcha real de la transmisión (Transmission Actual Gear) - PID 01A4 (SAE J1979-DA).
     * Retorna "R", "N", o marcha "1".."8", o null si no se pudo determinar.
     */
    fun parseTransmissionActualGear(raw: String): String? {
        val bytes = extractDataBytes("01A4", raw, 1) ?: return null
        val b = bytes[0]
        return when {
            b == 255 || b == 0xFF || b > 250 -> "R"
            b == 0 -> "N"
            b in 1..8 -> b.toString()
            else -> null
        }
    }

    companion object {
        /**
         * Calcula la marcha actual engranada (1ª a 7ª, N o R) según la relación entre RPM y velocidad km/h,
         * o según la dinámica inercial/aceleración cuando el vehículo se mueve hacia atrás bajo empuje (R).
         */
        fun calculateGear(
            speedKmh: Float?,
            rpm: Int?,
            longitudinalG: Float? = null,
            throttlePercent: Float? = null
        ): String {
            // Detección física inercial de marcha atrás (R):
            // Si el vehículo está en movimiento (> 1.5 km/h), el pedal del acelerador está activo (> 5%),
            // y la aceleración longitudinal IMU es negativa (< -0.10 G, vector hacia la trasera del coche),
            // el vehículo está acelerando hacia atrás en marcha atrás (R).
            if (speedKmh != null && speedKmh > 1.5f &&
                longitudinalG != null && longitudinalG < -0.10f &&
                throttlePercent != null && throttlePercent > 5.0f
            ) {
                return "R"
            }

            if (speedKmh == null || rpm == null || speedKmh < 4.0f || rpm < 450) {
                return "N"
            }
            val ratio = rpm / speedKmh
            return when {
                ratio > 98.0f -> "1"
                ratio > 60.0f -> "2"
                ratio > 41.0f -> "3"
                ratio > 30.0f -> "4"
                ratio > 22.0f -> "5"
                ratio > 16.0f -> "6"
                ratio > 11.0f -> "7"
                else -> "N"
            }
        }

        /**
         * Calcula la presión de sobrealimentación (Turbo / Boost) en bar restando la presión barométrica
         * (por defecto 101.3 kPa al nivel del mar). Valores positivos representan presión positiva del turbo.
         */
        fun calculateBoostBar(mapKpa: Float?, baroKpa: Float? = 101.3f): Float? {
            if (mapKpa == null) return null
            val ambient = baroKpa ?: 101.3f
            val boostKpa = mapKpa - ambient
            return boostKpa / 100.0f
        }

        /**
         * Calcula el consumo instantáneo de combustible en L/h y L/100 km a partir del sensor MAF
         * o del caudal directo de combustible (PID 015E).
         */
        fun calculateFuelConsumption(
            speedKmh: Float?,
            mafGps: Float?,
            fuelRateLh: Float?
        ): Pair<Float?, Float?> {
            val rateLh = fuelRateLh ?: mafGps?.let { (it * 3600f) / (14.7f * 740f) }
            val consumptionL100km = if (rateLh != null && speedKmh != null && speedKmh >= 5.0f) {
                (rateLh / speedKmh) * 100.0f
            } else null

            return Pair(rateLh, consumptionL100km)
        }
    }

    /**
     * Extrae los bytes de datos esperados para un PID del Modo 01.
     * Ejemplo para 010C: respuesta contiene "410C1A2F" -> retorna intArrayOf(0x1A, 0x2F)
     */
    fun extractDataBytes(pid: String, rawResponse: String, expectedByteCount: Int): IntArray? {
        val clean = cleanRawResponse(rawResponse)
        val pidNumber = pid.substring(2).uppercase()
        val matchPrefix = "41$pidNumber"

        val pos = clean.indexOf(matchPrefix)
        if (pos == -1) return null

        val dataHex = clean.substring(pos + matchPrefix.length)
        val neededHexChars = expectedByteCount * 2
        if (dataHex.length < neededHexChars) return null

        val result = IntArray(expectedByteCount)
        try {
            for (i in 0 until expectedByteCount) {
                val byteStr = dataHex.substring(i * 2, i * 2 + 2)
                result[i] = byteStr.toInt(16)
            }
            return result
        } catch (e: Exception) {
            return null
        }
    }

    fun cleanRawResponse(raw: String): String {
        return raw.replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(">", "")
            .trim()
            .uppercase()
    }
}

