package com.telemetria.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculationUtilsTest {

    @Test
    fun haversineDistanceMeters_knownLocations_calculatesCorrectDistance() {
        // Circuito del Jarama (Madrid) recta de meta: 2 puntos conocidos separados ~350 metros
        val lat1 = 40.617150
        val lon1 = -3.585250
        val lat2 = 40.620250
        val lon2 = -3.584750

        val distance = CalculationUtils.haversineDistanceMeters(lat1, lon1, lat2, lon2)

        // Distancia esperada aproximada ~347 metros (+- 10m de margen)
        assertTrue("Distance $distance should be between 340 and 360 meters", distance in 340.0..360.0)
    }

    @Test
    fun haversineDistanceMeters_samePoint_returnsZero() {
        val lat = 40.4168
        val lon = -3.7038
        val distance = CalculationUtils.haversineDistanceMeters(lat, lon, lat, lon)
        assertEquals(0.0, distance, 0.0001)
    }
}

