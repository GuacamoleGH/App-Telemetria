package com.telemetria.app.core.export

import com.telemetria.app.core.database.entity.SessionEntity
import com.telemetria.app.core.database.entity.TelemetryPointEntity
import com.telemetria.app.core.model.VehicleConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipSessionExporter {

    private const val README_CONTENT = """========================================================================
PAQUETE DE TELEMETRÍA Y VÍDEO SINCRONIZADO - APP TELEMETRÍA AUTOMOVILÍSTICA
========================================================================

Este archivo ZIP contiene la exportación completa de la sesión de conducción
con telemetría y vídeo sincronizados a nivel de reloj monotónico de nanosegundos.

------------------------------------------------------------------------
CONTENIDO DEL PAQUETE
------------------------------------------------------------------------
1. video.mp4: Vídeo original limpio grabado por CameraX (H.264 / AAC).
2. telemetria.csv: Tabla de telemetría completa.
3. ruta.gpx: Archivo GPX 1.1 compatible con Strava, Google Earth y Garmin.
4. session.json: Metadatos de la sesión, unidades, fuentes de datos y sincronización.
5. LEEME_SINCRONIZACION.txt: Este documento.

------------------------------------------------------------------------
SINCRONIZACIÓN DETERMINISTA CON RACERENDER / PC
------------------------------------------------------------------------
El instante exacto de inicio del vídeo se registró en el mismo reloj monotónico
que las muestras de telemetría.

En 'telemetria.csv', la columna 'time_offset_video_sec' indica los segundos
exactos respecto al primer frame de vídeo:
- time_offset_video_sec = 0.000 -> Inicio exacto del vídeo.
- Valores negativos (< 0.0) -> Muestras tomadas antes del inicio del vídeo.
- Valores positivos (> 0.0) -> Muestras durante la reproducción del vídeo.

INSTRUCCIONES PARA RACERENDER:
1. Abrir RaceRender y crear un nuevo proyecto.
2. Añadir 'video.mp4' como vídeo principal.
3. Añadir 'telemetria.csv' como archivo de datos (Input Data File).
4. En el asistente de sincronización, seleccionar 'time_offset_video_sec' como
   columna de tiempo (Time / Seconds). El offset de sincronización en RaceRender
   debe fijarse en 0.00 segundos.
5. Mapear las señales:
   - Velocidad: 'gps_speed_kmh' (Kilometers Per Hour)
   - Tacómetro: 'engine_rpm' (RPM)
   - Acelerador / Mariposa: 'throttle_pct' (%)
   - Fuerza G Lateral: 'lateral_g' (G's)
   - Fuerza G Longitudinal: 'longitudinal_g' (G's)
   - Coordenadas: 'latitude' y 'longitude'

------------------------------------------------------------------------
RENDERIZADO AUTOMÁTICO CON PYTHON + FFMPEG
------------------------------------------------------------------------
Si prefieres no usar software de terceros, puedes usar el script incluido
en el repositorio 'pc_tools/render_telemetry_overlay.py':

python render_telemetry_overlay.py --zip paquete_sesion.zip --out video_telemetria_final.mp4
========================================================================
"""

    fun exportSessionToZip(
        session: SessionEntity,
        points: List<TelemetryPointEntity>,
        outputZipFile: File,
        tempDir: File,
        vehicleConfig: VehicleConfig? = null,
        onProgress: ((progress: Float, statusText: String) -> Unit)? = null
    ): Boolean {
        return try {
            onProgress?.invoke(0.05f, "Generando archivos de telemetría...")
            outputZipFile.parentFile?.mkdirs()
            tempDir.mkdirs()

            val csvFile = File(tempDir, "telemetria.csv")
            val gpxFile = File(tempDir, "ruta.gpx")
            val jsonFile = File(tempDir, "session.json")
            val readmeFile = File(tempDir, "LEEME_SINCRONIZACION.txt")

            // Generar archivos auxiliares
            CsvExporter.exportToCsv(session, points, csvFile)
            onProgress?.invoke(0.12f, "Generando mapa GPX...")
            GpxExporter.exportToGpx(session, points, gpxFile)
            onProgress?.invoke(0.18f, "Generando metadatos...")
            jsonFile.writeText(SessionMetadataExporter.generateSessionJson(session, points.size.toLong(), vehicleConfig), StandardCharsets.UTF_8)
            readmeFile.writeText(README_CONTENT, StandardCharsets.UTF_8)

            onProgress?.invoke(0.22f, "Iniciando compresión ZIP...")

            val videoFile = session.videoFilePath?.let { File(it) }?.takeIf { it.exists() }
            val hasVideo = videoFile != null

            // Empaquetar todo en el ZIP
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
                addFileToZip(zos, csvFile, "telemetria.csv")
                addFileToZip(zos, gpxFile, "ruta.gpx")
                addFileToZip(zos, jsonFile, "session.json")
                addFileToZip(zos, readmeFile, "LEEME_SINCRONIZACION.txt")

                onProgress?.invoke(0.25f, if (hasVideo) "Empaquetando vídeo HD..." else "Finalizando paquete...")

                // Si hay vídeo grabado, incluirlo en la raíz del ZIP con progreso real
                if (videoFile != null) {
                    addFileToZip(zos, videoFile, "video.mp4") { copiedBytes, totalBytes ->
                        val videoFraction = (copiedBytes.toFloat() / totalBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)
                        val overallProgress = 0.25f + (videoFraction * 0.70f)
                        val mbCopied = copiedBytes / (1024 * 1024)
                        val mbTotal = totalBytes / (1024 * 1024)
                        onProgress?.invoke(overallProgress, "Empaquetando vídeo: $mbCopied / $mbTotal MB (${(videoFraction * 100).toInt()}%)")
                    }
                }
            }

            onProgress?.invoke(0.98f, "Limpiando archivos temporales...")
            // Limpiar archivos temporales
            csvFile.delete()
            gpxFile.delete()
            jsonFile.delete()
            readmeFile.delete()

            onProgress?.invoke(1.0f, "¡Paquete ZIP completado!")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun addFileToZip(
        zos: ZipOutputStream,
        file: File,
        entryName: String,
        onBytesCopied: ((copied: Long, total: Long) -> Unit)? = null
    ) {
        if (!file.exists()) return
        val total = file.length()
        var copied = 0L
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        BufferedInputStream(FileInputStream(file)).use { bis ->
            val buffer = ByteArray(64 * 1024)
            var count: Int
            var lastReported = 0L
            while (bis.read(buffer).also { count = it } != -1) {
                zos.write(buffer, 0, count)
                copied += count
                if (copied - lastReported > 256 * 1024 || copied == total) {
                    lastReported = copied
                    onBytesCopied?.invoke(copied, total)
                }
            }
        }
        zos.closeEntry()
    }
}

