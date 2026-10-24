package com.telemetria.app.core.obd

import com.telemetria.app.core.obd.model.ObdConnectionState
import com.telemetria.app.core.obd.protocol.Elm327Protocol
import com.telemetria.app.core.obd.transport.ObdTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Elm327ProtocolTest {

    // Mock simple de transporte para tests de protocolo
    private val mockTransport = object : ObdTransport {
        override val connectionState: StateFlow<ObdConnectionState> = MutableStateFlow(ObdConnectionState.Connected("CAN", 10))
        override val isConnected: Boolean = true
        override suspend fun connect(target: String): Boolean = true
        override suspend fun sendCommand(command: String, timeoutMillis: Long): String = "OK"
        override fun disconnect() {}
    }

    private val protocol = Elm327Protocol(mockTransport)

    @Test
    fun parseRpm_standardCanResponse_decodesCorrectRpm() {
        // En OBD-II: 41 0C XX YY -> ((XX * 256) + YY) / 4
        // 0x1A = 26, 0xF8 = 248 -> (26 * 256 + 248) / 4 = 6904 / 4 = 1726 RPM
        val rawResponseWithSpacesAndPrompt = "41 0C 1A F8 \r\r>"
        val rpm = protocol.parseRpm(rawResponseWithSpacesAndPrompt)

        assertEquals(1726, rpm)
    }

    @Test
    fun parseRpm_noSpacesAndCarriageReturns_decodesCorrectly() {
        val raw = "410C2710\r>" // 0x2710 = 10000 -> 10000 / 4 = 2500 RPM
        val rpm = protocol.parseRpm(raw)

        assertEquals(2500, rpm)
    }

    @Test
    fun parseSpeed_standardResponse_decodesKmh() {
        // 41 0D XX -> XX km/h
        // 0x55 = 85 km/h
        val raw = "41 0D 55\r>"
        val speed = protocol.parseSpeed(raw)

        assertEquals(85.0f, speed ?: 0f, 0.01f)
    }

    @Test
    fun parseThrottle_standardResponse_decodesPercentage() {
        // 41 11 XX -> (XX * 100) / 255 %
        // 0x80 = 128 -> (128 * 100) / 255 = 50.19%
        val raw = "41 11 80\r>"
        val throttle = protocol.parseThrottle(raw)

        assertTrue("Throttle must be around 50.2%", (throttle ?: 0f) in 50.0f..50.5f)
    }

    @Test
    fun parseCoolantTemp_standardResponse_decodesDegreesCelsius() {
        // 41 05 XX -> XX - 40 deg C
        // 0x7B = 123 -> 123 - 40 = 83 deg C
        val raw = "41 05 7B\r>"
        val temp = protocol.parseCoolantTemp(raw)

        assertEquals(83, temp)
    }

    @Test
    fun parseEngineLoad_standardResponse_decodesPercentage() {
        // 41 04 XX -> (XX * 100) / 255 %
        // 0x40 = 64 -> (64 * 100) / 255 = 25.09%
        val raw = "41 04 40\r>"
        val load = protocol.parseEngineLoad(raw)

        assertTrue("Load must be around 25.1%", (load ?: 0f) in 25.0f..25.2f)
    }

    @Test
    fun parseSupportedPids_decodesBitmaskCorrectly() {
        // Respuesta típica: "41 00 BE 3E B8 11"
        // Byte 1: 0xBE (10111110): PIDs 01, 03, 04, 05, 06, 07 soportados
        // Byte 2: 0x3E (00111110): PIDs 0B, 0C, 0D, 0E, 0F soportados (incluye RPM 0C y Velocidad 0D)
        val raw = "41 00 BE 3E B8 11\r>"
        protocol.parseSupportedPids("0100", raw)

        assertTrue("Should support 010C (RPM)", protocol.isPidSupported("010C"))
        assertTrue("Should support 010D (Speed)", protocol.isPidSupported("010D"))
        assertTrue("Should support 0104 (Engine Load)", protocol.isPidSupported("0104"))
        assertTrue("Should support 0105 (Coolant Temp)", protocol.isPidSupported("0105"))
    }

    @Test
    fun parseRpm_noDataOrSearching_returnsNull() {
        assertNull(protocol.parseRpm("NO DATA\r>"))
        assertNull(protocol.parseRpm("SEARCHING...\rUNABLE TO CONNECT\r>"))
        assertNull(protocol.parseRpm("CAN ERROR\r>"))
    }
}

