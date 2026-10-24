package com.telemetria.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.telemetria.app.core.model.Session
import com.telemetria.app.core.model.SessionStatus

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long? = null,
    val startMonotonicNanos: Long,
    val status: String,
    val totalDistanceMeters: Double = 0.0,
    val maxSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val durationSeconds: Long = 0,
    val pointCount: Long = 0,
    val notes: String = "",

    // Vídeo y sincronización temporal (Fase 4)
    val videoFilePath: String? = null,
    val videoStartMonotonicNanos: Long? = null,
    val videoStartEpochMillis: Long? = null
) {
    fun toDomain(): Session = Session(
        id = id,
        title = title,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        startMonotonicNanos = startMonotonicNanos,
        status = try {
            SessionStatus.valueOf(status)
        } catch (_: Exception) {
            SessionStatus.COMPLETED
        },
        totalDistanceMeters = totalDistanceMeters,
        maxSpeedKmh = maxSpeedKmh,
        avgSpeedKmh = avgSpeedKmh,
        durationSeconds = durationSeconds,
        pointCount = pointCount,
        notes = notes,
        videoFilePath = videoFilePath,
        videoStartMonotonicNanos = videoStartMonotonicNanos,
        videoStartEpochMillis = videoStartEpochMillis
    )

    companion object {
        fun fromDomain(session: Session): SessionEntity = SessionEntity(
            id = session.id,
            title = session.title,
            startTimeMillis = session.startTimeMillis,
            endTimeMillis = session.endTimeMillis,
            startMonotonicNanos = session.startMonotonicNanos,
            status = session.status.name,
            totalDistanceMeters = session.totalDistanceMeters,
            maxSpeedKmh = session.maxSpeedKmh,
            avgSpeedKmh = session.avgSpeedKmh,
            durationSeconds = session.durationSeconds,
            pointCount = session.pointCount,
            notes = session.notes,
            videoFilePath = session.videoFilePath,
            videoStartMonotonicNanos = session.videoStartMonotonicNanos,
            videoStartEpochMillis = session.videoStartEpochMillis
        )
    }
}
