package com.telemetria.app.core.export

import com.telemetria.app.core.database.entity.SessionEntity
import com.telemetria.app.core.database.entity.TelemetryPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ZipExportTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun zipExporter_createsCompletePackage_withStoredVideo() {
        // Crear un archivo de vídeo simulado en disco
        val dummyVideoFile = File(tempFolder.root, "mock_video.mp4")
        val mockData = "FAKE_VIDEO_STREAM_DATA_TEST_BYTES_FOR_CRC".toByteArray()
        dummyVideoFile.writeBytes(mockData)

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

        // Inspeccionar el contenido del ZIP con el lector estándar de Java
        ZipFile(outputZip).use { zip ->
            val entryNames = zip.entries().asSequence().map { it.name }.toSet()

            assertTrue("Must contain telemetria.csv", entryNames.contains("telemetria.csv"))
            assertTrue("Must contain ruta.gpx", entryNames.contains("ruta.gpx"))
            assertTrue("Must contain session.json", entryNames.contains("session.json"))
            assertTrue("Must contain LEEME_SINCRONIZACION.txt", entryNames.contains("LEEME_SINCRONIZACION.txt"))
            assertTrue("Must contain video.mp4", entryNames.contains("video.mp4"))

            // Verificar que telemetria.csv está comprimido con DEFLATED (8)
            val csvEntry = zip.getEntry("telemetria.csv")
            assertEquals("telemetria.csv must be DEFLATED", ZipEntry.DEFLATED, csvEntry.method)

            // Verificar que video.mp4 está guardado con STORED (0) y su CRC32 es íntegro
            val videoEntry = zip.getEntry("video.mp4")
            assertEquals("video.mp4 must be STORED (method 0)", ZipEntry.STORED, videoEntry.method)
            assertEquals("Video uncompressed size must match", mockData.size.toLong(), videoEntry.size)
            assertEquals("Video compressed size must match uncompressed", mockData.size.toLong(), videoEntry.compressedSize)
            val expectedCrc = ZipSessionExporter.calculateCrc32(dummyVideoFile)
            assertEquals("Video CRC-32 must match calculated checksum", expectedCrc, videoEntry.crc)

            // Comprobar lectura de los bytes exactos sin descompresión
            val readBytes = zip.getInputStream(videoEntry).readBytes()
            assertEquals("Read bytes must match original mock data", String(mockData), String(readBytes))

            // Verificar contenido de session.json (versión 1.1.0 y modo embedded)
            val jsonEntry = zip.getEntry("session.json")
            val jsonContent = zip.getInputStream(jsonEntry).bufferedReader().readText()
            assertTrue(jsonContent.contains("\"schema_version\": \"1.1.0\""))
            assertTrue(jsonContent.contains("\"has_video\": true"))
            assertTrue(jsonContent.contains("\"video_storage_mode\": \"embedded\""))
            assertTrue(jsonContent.contains("\"external_video_filename\": null"))
            assertTrue(jsonContent.contains("\"telemetry_to_video_offset_ms\": 200"))
        }
    }

    @Test
    fun zipExporter_largeVideo_exportsAsExternalFile() {
        val dummyVideoFile = File(tempFolder.root, "large_mock_video.mp4")
        val largeMockData = ByteArray(2048) { (it % 256).toByte() }
        dummyVideoFile.writeBytes(largeMockData)

        val session = SessionEntity(
            id = "sess-large-video-test",
            title = "Prueba de Vídeo Extenso",
            startTimeMillis = 1700000000000L,
            endTimeMillis = 1700000060000L,
            startMonotonicNanos = 5_000_000_000L,
            status = "COMPLETED",
            durationSeconds = 60,
            videoFilePath = dummyVideoFile.absolutePath,
            videoStartMonotonicNanos = 5_000_000_000L,
            videoStartEpochMillis = 1700000000000L
        )

        val outputZip = File(tempFolder.root, "export_large.zip")
        val stagingDir = File(tempFolder.root, "staging_large")

        // Umbral forzado a 1 KB para disparar la bifurcación de archivo suelto
        val success = ZipSessionExporter.exportSessionToZip(
            session = session,
            points = emptyList(),
            outputZipFile = outputZip,
            tempDir = stagingDir,
            maxZipVideoBytes = 1024L
        )

        assertTrue("Export must succeed", success)
        assertTrue("ZIP file must exist", outputZip.exists())

        val externalVideo = File(tempFolder.root, "export_large_video.mp4")
        assertTrue("External video file must exist next to ZIP", externalVideo.exists())
        assertEquals("External video size must match source", largeMockData.size.toLong(), externalVideo.length())

        ZipFile(outputZip).use { zip ->
            val entryNames = zip.entries().asSequence().map { it.name }.toSet()
            assertFalse("video.mp4 must NOT be inside ZIP when exceeding threshold", entryNames.contains("video.mp4"))
            assertTrue("Must contain session.json", entryNames.contains("session.json"))

            val jsonEntry = zip.getEntry("session.json")
            val jsonContent = zip.getInputStream(jsonEntry).bufferedReader().readText()
            assertTrue(jsonContent.contains("\"schema_version\": \"1.1.0\""))
            assertTrue(jsonContent.contains("\"has_video\": true"))
            assertTrue(jsonContent.contains("\"video_storage_mode\": \"external\""))
            assertTrue(jsonContent.contains("\"external_video_filename\": \"export_large_video.mp4\""))
        }
    }
}

