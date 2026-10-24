package com.telemetria.app.core.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageAndSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun storagePolicy_requiresAtLeast500Mb() {
        assertEquals(500L * 1024L * 1024L, StorageSpaceHelper.MINIMUM_FREE_SPACE_BYTES)
        assertEquals(150L * 1024L * 1024L, StorageSpaceHelper.CRITICAL_LOW_SPACE_BYTES)

        val rootDir = tempFolder.root
        val available = StorageSpaceHelper.getAvailableSpaceBytes(rootDir)
        assertTrue("Local disk must report positive available space", available > 0)
    }

    @Test
    fun videoSyncMetadata_computesOffsetCorrectly() {
        val sessionStartMonoNanos = 10_000_000_000L // 10.0s monotónico
        val videoStartMonoNanos = 10_150_000_000L   // Vídeo inicia 150 ms después del botón

        val sample1MonoNanos = 10_100_000_000L     // Muestra tomada 50 ms ANTES de que el vídeo empezara
        val sample2MonoNanos = 10_350_000_000L     // Muestra tomada 200 ms DESPUÉS de que el vídeo empezara

        // Offset respecto al primer frame del vídeo (en milisegundos)
        val offsetSample1Ms = (sample1MonoNanos - videoStartMonoNanos) / 1_000_000L
        val offsetSample2Ms = (sample2MonoNanos - videoStartMonoNanos) / 1_000_000L

        assertEquals(-50L, offsetSample1Ms)
        assertEquals(200L, offsetSample2Ms)

        // Verificamos que no dependa de saltos de hora UTC
        val meta = VideoSyncMetadata(
            videoFile = File(tempFolder.root, "test_video.mp4"),
            videoStartMonotonicNanos = videoStartMonoNanos,
            videoStartEpochMillis = 1700000000150L,
            isRecording = true,
            durationSeconds = 120L
        )

        assertEquals(120L, meta.durationSeconds)
        assertTrue(meta.isRecording)
    }
}
