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

    /**
     * Umbral máximo seguro para incluir vídeo en el contenedor ZIP en Android.
     * Si el archivo de vídeo supera los 2 GB (2.147.483.648 bytes), se exporta como archivo suelto
     * junto al ZIP para evitar problemas con implementaciones de ZipOutputStream / ZIP64 en Android.
     */
    const val DEFAULT_MAX_ZIP_VIDEO_BYTES: Long = 2L * 1024 * 1024 * 1024

    private const val README_CONTENT = """========================================================================
PAQUETE DE TELEMETRÍA Y VÍDEO SINCRONIZADO - APP TELEMETRÍA AUTOMOVILÍSTICA
========================================================================

Este archivo ZIP contiene la exportación completa de la sesión de conducción
con telemetría y vídeo sincronizados a nivel de reloj monotónico de nanosegundos.

------------------------------------------------------------------------
CONTENIDO DEL PAQUETE
------------------------------------------------------------------------
1. video.mp4: Vídeo original limpio grabado por CameraX (H.264 / AAC, modo STORED sin recompresión).
2. telemetria.csv: Tabla de telemetría completa (compresión DEFLATE).
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
RENDERIZADO AUTOMÁTICO CON TACHYON STUDIO / PYTHON
------------------------------------------------------------------------
Si prefieres análisis visual en PC, puedes usar Tachyon Studio (Rust/Tauri)
o el script incluido en 'pc_tools/render_telemetry_overlay.py':

python render_telemetry_overlay.py --zip paquete_sesion.zip --out video_telemetria_final.mp4
========================================================================
"""

    fun exportSessionToZip(
        session: SessionEntity,
        points: List<TelemetryPointEntity>,
        outputZipFile: File,
        tempDir: File,
        vehicleConfig: VehicleConfig? = null,
        maxZipVideoBytes: Long = DEFAULT_MAX_ZIP_VIDEO_BYTES,
        onProgress: ((progress: Float, statusText: String) -> Unit)? = null
    ): Boolean {
        return try {
            onProgress?.invoke(0.05f, "Generando archivos de telemetría...")
            outputZipFile.parentFile?.mkdirs()
            tempDir.mkdirs()

            val videoFile = session.videoFilePath?.let { File(it) }?.takeIf { it.exists() }
            val hasVideo = videoFile != null
            val isVideoTooLarge = hasVideo && (videoFile!!.length() > maxZipVideoBytes)

            val videoStorageMode = if (hasVideo && isVideoTooLarge) "external" else if (hasVideo) "embedded" else "none"
            val externalVideoFilename = if (hasVideo && isVideoTooLarge) "${outputZipFile.nameWithoutExtension}_video.mp4" else null

            val csvFile = File(tempDir, "telemetria.csv")
            val gpxFile = File(tempDir, "ruta.gpx")
            val jsonFile = File(tempDir, "session.json")
            val readmeFile = File(tempDir, "LEEME_SINCRONIZACION.txt")

            // Generar archivos auxiliares
            CsvExporter.exportToCsv(session, points, csvFile)
            onProgress?.invoke(0.12f, "Generando mapa GPX...")
            GpxExporter.exportToGpx(session, points, gpxFile)
            onProgress?.invoke(0.18f, "Generando metadatos...")
            jsonFile.writeText(
                SessionMetadataExporter.generateSessionJson(
                    session = session,
                    pointCount = points.size.toLong(),
                    vehicleConfig = vehicleConfig,
                    videoStorageMode = videoStorageMode,
                    externalVideoFilename = externalVideoFilename
                ),
                StandardCharsets.UTF_8
            )
            readmeFile.writeText(README_CONTENT, StandardCharsets.UTF_8)

            onProgress?.invoke(0.22f, "Iniciando compresión ZIP...")

            // Empaquetar entradas en el ZIP
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
                // Las entradas de texto se comprimen con DEFLATE
                addCompressedFileToZip(zos, csvFile, "telemetria.csv")
                addCompressedFileToZip(zos, gpxFile, "ruta.gpx")
                addCompressedFileToZip(zos, jsonFile, "session.json")
                addCompressedFileToZip(zos, readmeFile, "LEEME_SINCRONIZACION.txt")

                // Si hay vídeo y no supera el umbral, se añade con método STORED (sin recompresión)
                if (hasVideo && !isVideoTooLarge && videoFile != null) {
                    onProgress?.invoke(0.25f, "Calculando CRC32 e integrando vídeo STORED...")
                    addStoredFileToZip(zos, videoFile, "video.mp4") { copiedBytes, totalBytes ->
                        val videoFraction = (copiedBytes.toFloat() / totalBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)
                        val overallProgress = 0.25f + (videoFraction * 0.70f)
                        val mbCopied = copiedBytes / (1024 * 1024)
                        val mbTotal = totalBytes / (1024 * 1024)
                        onProgress?.invoke(overallProgress, "Empaquetando vídeo (STORED): $mbCopied / $mbTotal MB (${(videoFraction * 100).toInt()}%)")
                    }
                }
            }

            // Si el vídeo supera el umbral de 2 GB, se copia como archivo suelto junto al ZIP
            if (hasVideo && isVideoTooLarge && videoFile != null) {
                val externalVideoFile = File(outputZipFile.parentFile, externalVideoFilename!!)
                onProgress?.invoke(0.30f, "Vídeo > 2 GB: Exportando vídeo suelto...")
                copyFileWithProgress(videoFile, externalVideoFile) { copiedBytes, totalBytes ->
                    val videoFraction = (copiedBytes.toFloat() / totalBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    val overallProgress = 0.30f + (videoFraction * 0.65f)
                    val mbCopied = copiedBytes / (1024 * 1024)
                    val mbTotal = totalBytes / (1024 * 1024)
                    onProgress?.invoke(overallProgress, "Exportando vídeo suelto: $mbCopied / $mbTotal MB (${(videoFraction * 100).toInt()}%)")
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

    /**
     * Añade una entrada comprimida usando el algoritmo estándar DEFLATED.
     */
    private fun addCompressedFileToZip(
        zos: ZipOutputStream,
        file: File,
        entryName: String
    ) {
        if (!file.exists()) return
        val entry = ZipEntry(entryName).apply {
            method = ZipEntry.DEFLATED
        }
        zos.putNextEntry(entry)
        BufferedInputStream(FileInputStream(file)).use { bis ->
            val buffer = ByteArray(64 * 1024)
            var count: Int
            while (bis.read(buffer).also { count = it } != -1) {
                zos.write(buffer, 0, count)
            }
        }
        zos.closeEntry()
    }

    /**
     * Añade una entrada sin compresión (STORED).
     * java.util.zip exige especificar size, compressedSize y crc-32 ANTES de putNextEntry().
     * El cálculo del CRC32 se realiza por bloques (64 KB) sin cargar el archivo en RAM.
     */
    private fun addStoredFileToZip(
        zos: ZipOutputStream,
        file: File,
        entryName: String,
        onBytesCopied: ((copied: Long, total: Long) -> Unit)? = null
    ) {
        if (!file.exists()) return
        val total = file.length()
        val crcValue = calculateCrc32(file)

        val entry = ZipEntry(entryName).apply {
            method = ZipEntry.STORED
            size = total
            compressedSize = total
            crc = crcValue
        }
        zos.putNextEntry(entry)

        var copied = 0L
        var lastReported = 0L
        BufferedInputStream(FileInputStream(file)).use { bis ->
            val buffer = ByteArray(64 * 1024)
            var count: Int
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

    /**
     * Calcula el CRC32 leyendo el archivo secuencialmente en bloques de 64 KB sin consumos de RAM.
     */
    fun calculateCrc32(file: File): Long {
        val crc = java.util.zip.CRC32()
        BufferedInputStream(FileInputStream(file)).use { bis ->
            val buffer = ByteArray(64 * 1024)
            var count: Int
            while (bis.read(buffer).also { count = it } != -1) {
                crc.update(buffer, 0, count)
            }
        }
        return crc.value
    }

    /**
     * Copia un archivo de gran tamaño por streaming con progreso porcentual.
     */
    private fun copyFileWithProgress(
        source: File,
        dest: File,
        onBytesCopied: ((copied: Long, total: Long) -> Unit)? = null
    ) {
        val total = source.length()
        var copied = 0L
        var lastReported = 0L
        BufferedInputStream(FileInputStream(source)).use { bis ->
            BufferedOutputStream(FileOutputStream(dest)).use { bos ->
                val buffer = ByteArray(64 * 1024)
                var count: Int
                while (bis.read(buffer).also { count = it } != -1) {
                    bos.write(buffer, 0, count)
                    copied += count
                    if (copied - lastReported > 256 * 1024 || copied == total) {
                        lastReported = copied
                        onBytesCopied?.invoke(copied, total)
                    }
                }
            }
        }
    }
}

