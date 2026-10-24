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
}

