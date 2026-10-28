package com.telemetria.app.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

    fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }

    fun formatDistanceKm(meters: Double): String {
        val km = meters / 1000.0
        return String.format(Locale.US, "%.2f km", km)
    }

    fun formatDistanceValue(meters: Double): String {
        val km = meters / 1000.0
        return String.format(Locale.US, "%.2f", km)
    }

    fun formatSpeed(speedKmh: Float): String {
        return String.format(Locale.US, "%.1f", speedKmh)
    }

    fun formatDate(timestampMillis: Long): String {
        return dateFormat.format(Date(timestampMillis))
    }

    fun formatIsoUtc(timestampMillis: Long): String {
        return isoFormat.format(Date(timestampMillis))
    }

    /**
     * Genera un nombre de archivo descriptivo y seguro para el paquete ZIP,
     * incorporando la fecha y hora de la ruta (YYYY-MM-DD_HH-mm) y el ID corto de la sesión.
     * Sanitiza y sustituye de forma estricta cualquier carácter prohibido en Windows (\ / : * ? " < > |).
     */
    fun formatZipFilename(startTimeMillis: Long, sessionId: String): String {
        val dateFormatted = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
            sdf.format(Date(startTimeMillis))
        } catch (_: Exception) {
            "sesion"
        }
        val safeDate = dateFormatted.replace(Regex("""[\\/:*?"<>|\s]"""), "-")
        val shortId = sessionId.take(8)
        return "Tachyon_${safeDate}_${shortId}.zip"
    }
}

