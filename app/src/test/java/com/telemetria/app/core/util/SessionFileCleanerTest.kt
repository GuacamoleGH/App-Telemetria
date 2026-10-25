package com.telemetria.app.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionFileCleanerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun deleteMatchingFiles_deletesOnlyMatchingSessionFiles() {
        val testDir = tempFolder.newFolder("Telemetria")

        val targetSessionId = "fafeedb7-1234-5678-90ab-cdef12345678"
        val targetShortId = targetSessionId.take(8) // "fafeedb7"

        val otherSessionId = "eab9d7c3-9876-5432-10fe-dcba87654321"
        val otherShortId = otherSessionId.take(8) // "eab9d7c3"

        // Archivos asociados a la sesión objetivo
        val zipFile = File(testDir, "sesion_${targetShortId}_bundle.zip").apply { createNewFile() }
        val csvFile = File(testDir, "telemetria_${targetShortId}.csv").apply { createNewFile() }
        val gpxFile = File(testDir, "sesion_${targetShortId}.gpx").apply { createNewFile() }

        // Archivos pertenecientes a OTRA sesión
        val otherZip = File(testDir, "sesion_${otherShortId}_bundle.zip").apply { createNewFile() }
        val otherCsv = File(testDir, "telemetria_${otherShortId}.csv").apply { createNewFile() }

        assertTrue(zipFile.exists())
        assertTrue(csvFile.exists())
        assertTrue(gpxFile.exists())
        assertTrue(otherZip.exists())
        assertTrue(otherCsv.exists())

        val deleted = SessionFileCleaner.deleteMatchingFiles(testDir, targetShortId, targetSessionId)

        // Verificamos que se borraron los archivos de la sesión objetivo
        assertFalse("El zip debe haberse eliminado", zipFile.exists())
        assertFalse("El csv debe haberse eliminado", csvFile.exists())
        assertFalse("El gpx debe haberse eliminado", gpxFile.exists())
        assertTrue("3 archivos deben haber sido reportados como eliminados", deleted.size == 3)

        // Verificamos que los archivos de la otra sesión se preservaron
        assertTrue("El zip de la otra sesión NO debe borrarse", otherZip.exists())
        assertTrue("El csv de la otra sesión NO debe borrarse", otherCsv.exists())
    }

    @Test
    fun deleteVideoFile_deletesDirectPath() {
        val videoDir = tempFolder.newFolder("telemetria_videos")
        val targetShortId = "fafeedb7"
        val videoFile = File(videoDir, "video_$targetShortId.mp4").apply { createNewFile() }
        val otherVideo = File(videoDir, "video_other99.mp4").apply { createNewFile() }

        assertTrue(videoFile.exists())
        assertTrue(otherVideo.exists())

        val deleted = SessionFileCleaner.deleteMatchingFiles(videoDir, targetShortId, "fafeedb7-uuid")

        assertFalse(videoFile.exists())
        assertTrue(otherVideo.exists())
        assertTrue(deleted.contains(videoFile))
    }
}
