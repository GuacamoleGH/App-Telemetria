package com.telemetria.app.core.video

import java.io.File

object StorageSpaceHelper {

    const val MINIMUM_FREE_SPACE_BYTES = 500L * 1024L * 1024L // 500 MB mínimo
    const val CRITICAL_LOW_SPACE_BYTES = 150L * 1024L * 1024L // 150 MB límite crítico

    /**
     * Devuelve los bytes libres disponibles en el directorio donde se almacenarán los vídeos.
     * Utiliza usableSpace de java.io.File que opera limpiamente tanto en runtime de Android
     * como en pruebas unitarias JVM de escritorio.
     */
    fun getAvailableSpaceBytes(directory: File): Long {
        return try {
            val space = directory.usableSpace
            if (space > 0) space else directory.freeSpace
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    /**
     * Verifica si hay suficiente espacio para comenzar a grabar vídeo en alta resolución.
     */
    fun hasSufficientSpaceForRecording(directory: File): Boolean {
        return getAvailableSpaceBytes(directory) >= MINIMUM_FREE_SPACE_BYTES
    }
}

