package com.telemetria.app.core.export

import com.telemetria.app.core.database.entity.SessionEntity
import com.telemetria.app.core.database.entity.TelemetryPointEntity
import com.telemetria.app.core.util.FormatUtils
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object GpxExporter {

    fun exportToGpx(session: SessionEntity, points: List<TelemetryPointEntity>, outputFile: File): Boolean {
        return try {
            outputFile.parentFile?.mkdirs()
            OutputStreamWriter(FileOutputStream(outputFile), StandardCharsets.UTF_8).use { writer ->
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                writer.write("<gpx version=\"1.1\" creator=\"Telemetria App\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
                writer.write("  <metadata>\n")
                writer.write("    <name>${escapeXml(session.title)}</name>\n")
                writer.write("    <time>${FormatUtils.formatIsoUtc(session.startTimeMillis)}</time>\n")
                writer.write("  </metadata>\n")
                writer.write("  <trk>\n")
                writer.write("    <name>${escapeXml(session.title)}</name>\n")
                writer.write("    <trkseg>\n")

                for (p in points) {
                    val lat = p.latitude
                    val lon = p.longitude
                    if (lat != null && lon != null) {
                        writer.write("      <trkpt lat=\"$lat\" lon=\"$lon\">\n")
                        p.altitudeMeters?.let {
                            writer.write("        <ele>$it</ele>\n")
                        }
                        writer.write("        <time>${FormatUtils.formatIsoUtc(p.timestampMillis)}</time>\n")
                        p.gpsSpeedKmh?.let {
                            val speedMs = it / 3.6
                            writer.write("        <speed>$speedMs</speed>\n")
                        }
                        writer.write("      </trkpt>\n")
                    }
                }

                writer.write("    </trkseg>\n")
                writer.write("  </trk>\n")
                writer.write("</gpx>\n")
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

