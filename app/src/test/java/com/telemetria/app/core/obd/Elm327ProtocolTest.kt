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

    @Test
    fun parseMap_decodesKpa() {
        // 41 0B C8 -> 0xC8 = 200 kPa (aprox 2.0 bar absoluto)
        val raw = "41 0B C8\r>"
        val map = protocol.parseMap(raw)
        assertEquals(200.0f, map ?: 0f, 0.01f)
    }

    @Test
    fun parseIntakeAirTemp_decodesDegreesCelsius() {
        // 41 0F 46 -> 0x46 = 70 -> 70 - 40 = 30 °C
        val raw = "41 0F 46\r>"
        val iat = protocol.parseIntakeAirTemp(raw)
        assertEquals(30, iat)
    }

    @Test
    fun parseMaf_decodesGramsPerSec() {
        // 41 10 08 FC -> ((8 * 256) + 252) / 100 = 23.00 g/s
        val raw = "41 10 08 FC\r>"
        val maf = protocol.parseMaf(raw)
        assertEquals(23.00f, maf ?: 0f, 0.01f)
    }

    @Test
    fun parseFuelRate_decodesLitersPerHour() {
        // 41 5E 00 64 -> (0 + 100) * 0.05 = 5.0 L/h
        val raw = "41 5E 00 64\r>"
        val rate = protocol.parseFuelRate(raw)
        assertEquals(5.0f, rate ?: 0f, 0.01f)
    }

    @Test
    fun calculateGear_estimatesRatiosCorrectly() {
        // 2000 RPM a 20 km/h -> ratio = 100 -> 1ª marcha
        assertEquals("1", Elm327Protocol.calculateGear(20.0f, 2000))

        // 3000 RPM a 40 km/h -> ratio = 75 -> 2ª marcha
        assertEquals("2", Elm327Protocol.calculateGear(40.0f, 3000))

        // 3000 RPM a 60 km/h -> ratio = 50 -> 3ª marcha
        assertEquals("3", Elm327Protocol.calculateGear(60.0f, 3000))

        // 3000 RPM a 85 km/h -> ratio = 35.29 -> 4ª marcha
        assertEquals("4", Elm327Protocol.calculateGear(85.0f, 3000))

        // 2500 RPM a 100 km/h -> ratio = 25 -> 5ª marcha
        assertEquals("5", Elm327Protocol.calculateGear(100.0f, 2500))

        // 2400 RPM a 130 km/h -> ratio = 18.46 -> 6ª marcha
        assertEquals("6", Elm327Protocol.calculateGear(130.0f, 2400))

        // Coche parado / ralentí -> N
        assertEquals("N", Elm327Protocol.calculateGear(0.0f, 800))
    }

    @Test
    fun calculateBoostBar_estimatesPressureRelativeToAtmosphere() {
        // 200 kPa MAP con 101.3 kPa barométrica = ~ +0.987 bar de turbo
        val boost = Elm327Protocol.calculateBoostBar(200.0f, 101.3f)
        assertTrue("Boost should be around +0.99 bar", (boost ?: 0f) in 0.98f..0.99f)

        // En atmosférico (101.3 kPa) -> 0.0 bar
        val boostAtm = Elm327Protocol.calculateBoostBar(101.3f, 101.3f)
        assertEquals(0.0f, boostAtm ?: 0f, 0.01f)
    }

    @Test
    fun calculateFuelConsumption_estimatesL100kmFromMaf() {
        // 25 g/s MAF a 100 km/h:
        // rateLh = (25 * 3600) / (14.7 * 740) = 90000 / 10878 = 8.27 L/h
        // consumptionL100km = (8.27 / 100) * 100 = 8.27 L/100km
        val (_, consumption) = Elm327Protocol.calculateFuelConsumption(100.0f, 25.0f, null)
        assertTrue("Consumption should be around 8.27 L/100km", (consumption ?: 0f) in 8.2f..8.4f)
    }
}

