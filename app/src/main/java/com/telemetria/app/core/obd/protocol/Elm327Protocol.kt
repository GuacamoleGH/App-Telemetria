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

