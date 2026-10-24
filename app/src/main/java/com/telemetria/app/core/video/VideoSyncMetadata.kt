package com.telemetria.app.core.video

import java.io.File

/**
 * Metadatos de sincronización temporal absoluta y monotónica de una pista de vídeo.
 * Permite enlazar cualquier instante t del archivo de vídeo con la muestra exacta de telemetría:
 * offsetMillis = (sampleMonotonicNanos - videoStartMonotonicNanos) / 1_000_000
 */
data class VideoSyncMetadata(
    val videoFile: File,
    val videoStartMonotonicNanos: Long,
    val videoStartEpochMillis: Long,
    val isRecording: Boolean = false,
    val durationSeconds: Long = 0L
)

