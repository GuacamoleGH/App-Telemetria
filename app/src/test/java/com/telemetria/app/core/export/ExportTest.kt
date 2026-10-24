package com.telemetria.app.core.export

import com.telemetria.app.core.database.entity.SessionEntity
import com.telemetria.app.core.database.entity.TelemetryPointEntity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExportTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val sampleSession = SessionEntity(
        id = "test-session-1234",
        title = "Prueba de Circuito",
        startTimeMillis = 1700000000000L,
        endTimeMillis = 1700000100000L,
        startMonotonicNanos = 1000000000L,
        status = "COMPLETED",
        totalDistanceMeters = 3450.5,
        maxSpeedKmh = 145.2f,
        avgSpeedKmh = 82.4f,
        durationSeconds = 100,
        pointCount = 2
    )

    private val samplePoints = listOf(
        TelemetryPointEntity(
            id = 1,
            sessionId = "test-session-1234",
            timestampMillis = 1700000000000L,
            monotonicNanos = 1000000000L,
            latitude = 40.617150,
            longitude = -3.585250,
            altitudeMeters = 620.0,
            gpsSpeedKmh = 95.0f,
            bearingDegrees = 180f,
            accuracyMeters = 1.8f,
            hasGpsFix = true
        ),
        TelemetryPointEntity(
            id = 2,
            sessionId = "test-session-1234",
            timestampMillis = 1700000001000L,
            monotonicNanos = 2000000000L,
            latitude = 40.618150,
            longitude = -3.585200,
            altitudeMeters = 621.5,
            gpsSpeedKmh = 102.5f,
            bearingDegrees = 180f,
            accuracyMeters = 1.5f,
            hasGpsFix = true
        )
    )

    @Test
    fun gpxExporter_createsValidGpxStructure() {
        val targetFile = File(tempFolder.root, "test.gpx")
        val success = GpxExporter.exportToGpx(sampleSession, samplePoints, targetFile)

        assertTrue(success)
        assertTrue(targetFile.exists())
        val content = targetFile.readText()
        assertTrue(content.contains("<gpx version=\"1.1\""))
        assertTrue(content.contains("<trkpt lat=\"40.61715\" lon=\"-3.58525\">"))
        assertTrue(content.contains("<ele>620.0</ele>"))
        assertTrue(content.contains("<time>"))
        assertTrue(content.contains("</trkpt>"))
    }

    @Test
    fun csvExporter_createsValidCsvStructure() {
        val targetFile = File(tempFolder.root, "test.csv")
        val success = CsvExporter.exportToCsv(sampleSession, samplePoints, targetFile)

        assertTrue(success)
        assertTrue(targetFile.exists())
        val content = targetFile.readText()
        assertTrue(content.startsWith("timestamp_utc_ms,iso_time,monotonic_nanos,latitude,longitude,altitude_m,gps_speed_kmh"))
        val lines = content.lines()
        assertTrue("Lines count should be header + 2 points + empty line", lines.size >= 3)
        assertTrue(lines[1].contains("1700000000000"))
        assertTrue(lines[1].contains("1000000000"))
        assertTrue(lines[1].contains("40.61715"))
    }
}

