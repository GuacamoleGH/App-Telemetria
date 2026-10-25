package com.telemetria.app.core.util

import android.content.Context
import android.os.Environment
import android.util.Log
import com.telemetria.app.core.database.entity.SessionEntity
import java.io.File

object SessionFileCleaner {

    private const val TAG = "SessionFileCleaner"

    /**
     * Elimina todos los archivos físicos generados asociados a una sesión:
     * - Vídeo MP4 (ruta guardada en la entidad de sesión y en la carpeta telemetria_videos)
     * - Paquete ZIP de exportación
     * - Exportaciones individuales CSV y GPX
     * - Archivos de metadatos o temporales de empaquetado en caché
     *
     * @return Lista de archivos eliminados con éxito.
     */
    fun deleteSessionFiles(context: Context, session: SessionEntity?, sessionId: String): List<File> {
        val deletedFiles = mutableListOf<File>()
        val shortId = sessionId.take(8)

        // 1. Eliminar archivo de vídeo si existe
        session?.videoFilePath?.let { path ->
            val videoFile = File(path)
            if (videoFile.exists()) {
                if (videoFile.delete()) {
                    logInfo("Vídeo de sesión eliminado: ${videoFile.absolutePath}")
                    deletedFiles.add(videoFile)
                } else {
                    logWarn("No se pudo eliminar el archivo de vídeo: ${videoFile.absolutePath}")
                }
            }
        }

        // Eliminar posibles vídeos en carpeta telemetria_videos con el id o prefijo de sesión
        val videoDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "telemetria_videos")
        deleteMatchingFiles(videoDir, shortId, sessionId, deletedFiles)

        // 2. Eliminar exportaciones en carpeta pública de Downloads/Telemetria y almacenamientos locales
        val exportDirs = listOfNotNull(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Telemetria"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            context.getExternalFilesDir(null),
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            File(context.getExternalFilesDir(null) ?: context.filesDir, "Telemetria"),
            context.filesDir,
            File(context.filesDir, "Telemetria")
        ).distinct()

        exportDirs.forEach { dir ->
            deleteMatchingFiles(dir, shortId, sessionId, deletedFiles)
        }

        // 3. Limpiar directorio temporal o archivos en caché
        val cacheDir = context.cacheDir
        if (cacheDir.exists() && cacheDir.isDirectory) {
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.contains(shortId) || file.name.contains(sessionId)) {
                    if (file.deleteRecursively()) {
                        logInfo("Directorio/archivo temporal de caché eliminado: ${file.name}")
                        deletedFiles.add(file)
                    }
                }
            }
        }

        logInfo("Limpieza de sesión $sessionId completada. Archivos eliminados: ${deletedFiles.size}")
        return deletedFiles
    }

    /**
     * Elimina archivos en un directorio cuyo nombre contenga el prefijo corto (8 chars) o el ID completo de la sesión.
     */
    fun deleteMatchingFiles(
        dir: File,
        shortId: String,
        sessionId: String,
        deletedList: MutableList<File> = mutableListOf()
    ): List<File> {
        if (!dir.exists() || !dir.isDirectory) return deletedList

        dir.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.contains(shortId) || file.name.contains(sessionId))) {
                val absolutePath = file.absolutePath
                if (file.delete()) {
                    logInfo("Archivo asociado eliminado: $absolutePath")
                    deletedList.add(file)
                } else {
                    logWarn("No se pudo eliminar el archivo: $absolutePath")
                }
            }
        }
        return deletedList
    }

    private fun logInfo(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG] $msg")
        }
    }

    private fun logWarn(msg: String) {
        try {
            Log.w(TAG, msg)
        } catch (_: Throwable) {
            System.err.println("[$TAG] $msg")
        }
    }
}
