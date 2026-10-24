package com.telemetria.app.core.export

import com.telemetria.app.core.database.entity.SessionEntity
import com.telemetria.app.core.database.entity.TelemetryPointEntity
import com.telemetria.app.core.util.FormatUtils
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale

object CsvExporter {

    private const val CSV_HEADER = "time_offset_video_sec,time_offset_session_sec,timestamp_utc_ms,iso_time,monotonic_nanos,latitude,longitude,altitude_m,gps_speed_kmh,bearing_deg,accuracy_m,lateral_g,longitudinal_g,engine_rpm,obd_speed_kmh,throttle_pct,coolant_temp_c,accel_x,accel_y,accel_z\n"

    fun exportToCsv(session: SessionEntity, points: List<TelemetryPointEntity>, outputFile: File): Boolean {
        return try {
            outputFile.parentFile?.mkdirs()
            OutputStreamWriter(FileOutputStream(outputFile), StandardCharsets.UTF_8).use { writer ->
                writer.write(CSV_HEADER)

                val videoStartMono = session.videoStartMonotonicNanos
                val sessionStartMono = session.startMonotonicNanos

                for (p in points) {
                    val offsetSessionSec = (p.monotonicNanos - sessionStartMono) / 1_000_000_000.0
                    val offsetVideoSec = if (videoStartMono != null) {
                        (p.monotonicNanos - videoStartMono) / 1_000_000_000.0
                    } else {
                        offsetSessionSec
                    }

                    val line = buildString {
                        append(String.format(Locale.US, "%.3f", offsetVideoSec)).append(",")
                        append(String.format(Locale.US, "%.3f", offsetSessionSec)).append(",")
                        append(p.timestampMillis).append(",")
                        append(FormatUtils.formatIsoUtc(p.timestampMillis)).append(",")
                        append(p.monotonicNanos).append(",")
                        append(p.latitude?.let { String.format(Locale.US, "%.7f", it) } ?: "").append(",")
                        append(p.longitude?.let { String.format(Locale.US, "%.7f", it) } ?: "").append(",")
                        append(p.altitudeMeters?.let { String.format(Locale.US, "%.1f", it) } ?: "").append(",")
                        append(p.gpsSpeedKmh?.let { String.format(Locale.US, "%.2f", it) } ?: "").append(",")
                        append(p.bearingDegrees?.let { String.format(Locale.US, "%.1f", it) } ?: "").append(",")
                        append(p.accuracyMeters?.let { String.format(Locale.US, "%.1f", it) } ?: "").append(",")
                        append(p.lateralG?.let { String.format(Locale.US, "%.3f", it) } ?: "").append(",")
                        append(p.longitudinalG?.let { String.format(Locale.US, "%.3f", it) } ?: "").append(",")
                        append(p.engineRpm?.toString() ?: "").append(",")
                        append(p.obdSpeedKmh?.let { String.format(Locale.US, "%.1f", it) } ?: "").append(",")
                        append(p.throttlePercent?.let { String.format(Locale.US, "%.1f", it) } ?: "").append(",")
                        append(p.coolantTempC?.toString() ?: "").append(",")
                        append(p.accelX?.let { String.format(Locale.US, "%.3f", it) } ?: "").append(",")
                        append(p.accelY?.let { String.format(Locale.US, "%.3f", it) } ?: "").append(",")
                        append(p.accelZ?.let { String.format(Locale.US, "%.3f", it) } ?: "")
                        append("\n")
                    }
                    writer.write(line)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
