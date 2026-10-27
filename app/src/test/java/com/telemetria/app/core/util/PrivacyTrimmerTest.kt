package com.telemetria.app.core.util

import com.telemetria.app.core.database.entity.TelemetryPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyTrimmerTest {

    @Test
    fun trimPointsByDistance_trimsStartAndEnd() {
        // Creamos una serie de 10 puntos a lo largo de una línea norte-sur
        // 0.001 grados de latitud son aprox. 111 metros
        val points = (0..10).map { i ->
            TelemetryPointEntity(
                id = i.toLong(),
                sessionId = "test-session",
                timestampMillis = 1000L * i,
                monotonicNanos = 1_000_000_000L * i,
                latitude = 40.0 + (i * 0.001), // ~111m por punto
                longitude = -3.0
            )
        }

        // Recortamos 200 metros (debería omitir ~2 puntos iniciales y ~2 puntos finales)
        val trimmed = PrivacyTrimmer.trimPointsByDistance(points, 200.0)

        assertTrue(trimmed.size < points.size)
        // El primer punto recortado debe ser el índice 2 (~222m desde inicio)
        assertEquals(2L, trimmed.first().id)
        // El último punto recortado debe ser el índice 8 (~222m desde fin)
        assertEquals(8L, trimmed.last().id)
    }

    @Test
    fun trimPointsByDistance_whenTrimZero_returnsOriginalList() {
        val points = listOf(
            TelemetryPointEntity(id = 1, sessionId = "s", timestampMillis = 1, monotonicNanos = 1, latitude = 40.0, longitude = -3.0),
            TelemetryPointEntity(id = 2, sessionId = "s", timestampMillis = 2, monotonicNanos = 2, latitude = 40.1, longitude = -3.0)
        )
        val result = PrivacyTrimmer.trimPointsByDistance(points, 0.0)
        assertEquals(points.size, result.size)
    }
}
