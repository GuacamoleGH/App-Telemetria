package com.telemetria.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun formatDuration_underOneHour_formatsMmSs() {
        assertEquals("00:45", FormatUtils.formatDuration(45))
        assertEquals("12:30", FormatUtils.formatDuration(750))
    }

    @Test
    fun formatDuration_overOneHour_formatsHhMmSs() {
        assertEquals("01:05:20", FormatUtils.formatDuration(3920))
    }

    @Test
    fun formatDistanceKm_convertsMetersToKm() {
        assertEquals("0.00 km", FormatUtils.formatDistanceKm(0.0))
        assertEquals("1.50 km", FormatUtils.formatDistanceKm(1500.0))
        assertEquals("12.35 km", FormatUtils.formatDistanceKm(12345.6))
    }

    @Test
    fun formatSpeed_oneDecimal() {
        assertEquals("0.0", FormatUtils.formatSpeed(0f))
        assertEquals("124.5", FormatUtils.formatSpeed(124.54f))
    }

    @Test
    fun formatZipFilename_containsDateAndShortIdAndZipExtension() {
        // Timestamp 1793187345970L (2026-10-28 12:35 UTC aprox)
        val timestamp = 1793187345970L
        val sessionId = "db8f7b5a-e793-42ee-9708-dbc000122e6b"
        val filename = FormatUtils.formatZipFilename(timestamp, sessionId)

        org.junit.Assert.assertTrue("Debe empezar con Tachyon_", filename.startsWith("Tachyon_"))
        org.junit.Assert.assertTrue("Debe terminar con .zip", filename.endsWith(".zip"))
        org.junit.Assert.assertTrue("Debe contener el short ID", filename.contains("db8f7b5a"))
    }

    @Test
    fun formatZipFilename_containsNoForbiddenWindowsCharacters() {
        val timestamp = System.currentTimeMillis()
        val sessionId = "abcdef12-3456-7890-abcd-ef1234567890"
        val filename = FormatUtils.formatZipFilename(timestamp, sessionId)

        val forbiddenChars = listOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
        for (char in forbiddenChars) {
            org.junit.Assert.assertFalse("El nombre de archivo no debe contener '$char'", filename.contains(char))
        }
    }
}

