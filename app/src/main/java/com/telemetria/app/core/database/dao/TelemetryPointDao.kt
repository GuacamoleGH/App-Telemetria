package com.telemetria.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telemetria.app.core.database.entity.TelemetryPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<TelemetryPointEntity>)

    @Query("SELECT * FROM telemetry_points WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    suspend fun getPointsForSession(sessionId: String): List<TelemetryPointEntity>

    @Query("SELECT * FROM telemetry_points WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    fun observePointsForSession(sessionId: String): Flow<List<TelemetryPointEntity>>

    @Query("""
        SELECT * FROM telemetry_points 
        WHERE sessionId = :sessionId AND latitude IS NOT NULL AND longitude IS NOT NULL 
        ORDER BY timestampMillis ASC
    """)
    suspend fun getGpsTrackForSession(sessionId: String): List<TelemetryPointEntity>

    @Query("SELECT COUNT(*) FROM telemetry_points WHERE sessionId = :sessionId")
    suspend fun countPointsForSession(sessionId: String): Long

    @Query("DELETE FROM telemetry_points WHERE sessionId = :sessionId")
    suspend fun deletePointsForSession(sessionId: String)
}

