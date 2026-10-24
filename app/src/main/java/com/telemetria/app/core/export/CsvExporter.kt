package com.telemetria.app.core.export

import com.telemetria.app.core.database.entity.SessionEntity
import com.telemetria.app.core.database.entity.TelemetryPointEntity
import com.telemetria.app.core.util.FormatUtils
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object CsvExporter {

    private const val CSV_HEADER = "timestamp_utc_ms,iso_time,monotonic_nanos,latitude,longitude,altitude_m,gps_speed_kmh,bearing_deg,accuracy_m,engine_rpm,obd_speed_kmh,throttle_pct,coolant_temp_c,accel_x,accel_y,accel_z\n"

    fun exportToCsv(session: SessionEntity, points: List<TelemetryPointEntity>, outputFile: File): Boolean {
        return try {
            outputFile.parentFile?.mkdirs()
            OutputStreamWriter(FileOutputStream(outputFile), StandardCharsets.UTF_8).use { writer ->
                writer.write(CSV_HEADER)
                for (p in points) {
                    val line = buildString {
                        append(p.timestampMillis).append(",")
                        append(FormatUtils.formatIsoUtc(p.timestampMillis)).append(",")
                        append(p.monotonicNanos).append(",")
                        append(p.latitude?.toString() ?: "").append(",")
                        append(p.longitude?.toString() ?: "").append(",")
                        append(p.altitudeMeters?.toString() ?: "").append(",")
                        append(p.gpsSpeedKmh?.toString() ?: "").append(",")
                        append(p.bearingDegrees?.toString() ?: "").append(",")
                        append(p.accuracyMeters?.toString() ?: "").append(",")
                        append(p.engineRpm?.toString() ?: "").append(",")
                        append(p.obdSpeedKmh?.toString() ?: "").append(",")
                        append(p.throttlePercent?.toString() ?: "").append(",")
                        append(p.coolantTempC?.toString() ?: "").append(",")
                        append(p.accelX?.toString() ?: "").append(",")
                        append(p.accelY?.toString() ?: "").append(",")
                        append(p.accelZ?.toString() ?: "")
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

