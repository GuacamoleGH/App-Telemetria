package com.telemetria.app.core.export

import com.telemetria.app.core.database.entity.SessionEntity
import com.telemetria.app.core.database.entity.TelemetryPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class ZipExportTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun zipExporter_createsCompletePackage() {
        // Crear un archivo de vídeo simulado en disco
        val dummyVideoFile = File(tempFolder.root, "mock_video.mp4")
        dummyVideoFile.writeBytes(ByteArray(1024)) // 1 KB mock MP4

        val session = SessionEntity(
            id = "sess-zip-test-99",
            title = "Prueba de Exportación ZIP",
            startTimeMillis = 1700000000000L,
            endTimeMillis = 1700000060000L,
            startMonotonicNanos = 5_000_000_000L,
            status = "COMPLETED",
            totalDistanceMeters = 1200.0,
            maxSpeedKmh = 130f,
            avgSpeedKmh = 72f,
            durationSeconds = 60,
            pointCount = 2,
            videoFilePath = dummyVideoFile.absolutePath,
            videoStartMonotonicNanos = 5_200_000_000L, // Vídeo arrancó 200 ms después
            videoStartEpochMillis = 1700000000200L
        )

        val points = listOf(
            TelemetryPointEntity(
                id = 1,
                sessionId = session.id,
                timestampMillis = 1700000000000L,
                monotonicNanos = 5_000_000_000L,
                latitude = 40.617,
                longitude = -3.585,
                gpsSpeedKmh = 80f,
                lateralG = 0.45f,
                longitudinalG = 0.30f
            ),
            TelemetryPointEntity(
                id = 2,
                sessionId = session.id,
                timestampMillis = 1700000001000L,
                monotonicNanos = 6_000_000_000L,
                latitude = 40.618,
                longitude = -3.586,
                gpsSpeedKmh = 95f,
                lateralG = 0.60f,
                longitudinalG = -0.20f
            )
        )

        val outputZip = File(tempFolder.root, "export_bundle.zip")
        val stagingDir = File(tempFolder.root, "staging")

        val success = ZipSessionExporter.exportSessionToZip(session, points, outputZip, stagingDir)

        assertTrue("ZIP export must succeed", success)
        assertTrue("ZIP file must exist on disk", outputZip.exists())
        assertTrue("ZIP file must have non-zero size", outputZip.length() > 0)

        // Inspeccionar el contenido del ZIP
        ZipFile(outputZip).use { zip ->
            val entryNames = zip.entries().asSequence().map { it.name }.toSet()

            assertTrue("Must contain telemetria.csv", entryNames.contains("telemetria.csv"))
            assertTrue("Must contain ruta.gpx", entryNames.contains("ruta.gpx"))
            assertTrue("Must contain session.json", entryNames.contains("session.json"))
            assertTrue("Must contain LEEME_SINCRONIZACION.txt", entryNames.contains("LEEME_SINCRONIZACION.txt"))
            assertTrue("Must contain video.mp4", entryNames.contains("video.mp4"))

            // Verificar contenido de session.json
            val jsonEntry = zip.getEntry("session.json")
            val jsonContent = zip.getInputStream(jsonEntry).bufferedReader().readText()
            assertTrue(jsonContent.contains("\"schema_version\": \"1.0.0\""))
            assertTrue(jsonContent.contains("\"has_video\": true"))
            assertTrue(jsonContent.contains("\"telemetry_to_video_offset_ms\": 200"))
        }
    }
}

