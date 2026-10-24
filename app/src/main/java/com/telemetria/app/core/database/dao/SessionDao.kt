package com.telemetria.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.telemetria.app.core.database.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    fun observeSessionById(sessionId: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions ORDER BY startTimeMillis DESC")
    fun observeAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE status = 'ACTIVE' OR status = 'PAUSED' ORDER BY startTimeMillis DESC LIMIT 1")
    suspend fun getActiveOrPausedSession(): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("""
        UPDATE sessions 
        SET totalDistanceMeters = :distanceMeters,
            maxSpeedKmh = :maxSpeed,
            avgSpeedKmh = :avgSpeed,
            durationSeconds = :durationSec,
            pointCount = :count
        WHERE id = :sessionId
    """)
    suspend fun updateLiveStats(
        sessionId: String,
        distanceMeters: Double,
        maxSpeed: Float,
        avgSpeed: Float,
        durationSec: Long,
        count: Long
    )

    @Query("""
        UPDATE sessions 
        SET videoFilePath = :filePath,
            videoStartMonotonicNanos = :startMonoNanos,
            videoStartEpochMillis = :startEpochMillis
        WHERE id = :sessionId
    """)
    suspend fun updateVideoInfo(
        sessionId: String,
        filePath: String,
        startMonoNanos: Long,
        startEpochMillis: Long
    )
}

