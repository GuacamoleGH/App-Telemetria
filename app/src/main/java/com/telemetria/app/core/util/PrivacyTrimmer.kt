package com.telemetria.app.core.util

import com.telemetria.app.core.database.entity.TelemetryPointEntity

object PrivacyTrimmer {

    /**
     * Recorta los primeros y últimos N metros de una lista de puntos de telemetría GPS,
     * ocultando las ubicaciones exactas de origen y destino (garaje, domicilio, trabajo)
     * en los archivos de exportación de datos (CSV, GPX, session.json).
     *
     * @param points Lista cronológica de muestras de telemetría.
     * @param trimMeters Distancia en metros a omitir al inicio y al final de la ruta.
     * @return Sublista con los puntos recortados, o la lista original si no hay suficientes puntos.
     */
    fun trimPointsByDistance(
        points: List<TelemetryPointEntity>,
        trimMeters: Double
    ): List<TelemetryPointEntity> {
        if (trimMeters <= 0.0 || points.size < 4) return points

        var startDist = 0.0
        var startIndex = 0
        var lastLat: Double? = null
        var lastLon: Double? = null

        // Encontrar índice de inicio que supera el margen de privacidad
        for (i in points.indices) {
            val p = points[i]
            if (p.latitude != null && p.longitude != null) {
                if (lastLat != null && lastLon != null) {
                    val d = CalculationUtils.haversineDistanceMeters(lastLat, lastLon, p.latitude, p.longitude)
                    startDist += d
                    if (startDist >= trimMeters) {
                        startIndex = i
                        break
                    }
                }
                lastLat = p.latitude
                lastLon = p.longitude
            }
        }

        var endDist = 0.0
        var endIndex = points.lastIndex
        lastLat = null
        lastLon = null

        // Encontrar índice de fin que supera el margen de privacidad desde el destino
        for (i in points.indices.reversed()) {
            val p = points[i]
            if (p.latitude != null && p.longitude != null) {
                if (lastLat != null && lastLon != null) {
                    val d = CalculationUtils.haversineDistanceMeters(p.latitude, p.longitude, lastLat, lastLon)
                    endDist += d
                    if (endDist >= trimMeters) {
                        endIndex = i
                        break
                    }
                }
                lastLat = p.latitude
                lastLon = p.longitude
            }
        }

        return if (startIndex < endIndex) {
            points.subList(startIndex, endIndex + 1)
        } else {
            // Si la ruta total es más corta que 2 * trimMeters, devolver la lista completa
            points
        }
    }
}
