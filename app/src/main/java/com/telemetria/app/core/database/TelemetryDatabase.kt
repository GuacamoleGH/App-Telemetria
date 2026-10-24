package com.telemetria.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.telemetria.app.core.database.dao.SessionDao
import com.telemetria.app.core.database.dao.TelemetryPointDao
import com.telemetria.app.core.database.entity.SessionEntity
import com.telemetria.app.core.database.entity.TelemetryPointEntity

@Database(
    entities = [SessionEntity::class, TelemetryPointEntity::class],
    version = 2,
    exportSchema = false
)
abstract class TelemetryDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun telemetryPointDao(): TelemetryPointDao

    companion object {
        @Volatile
        private var INSTANCE: TelemetryDatabase? = null

        fun getInstance(context: Context): TelemetryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TelemetryDatabase::class.java,
                    "telemetry_master.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

