package com.telemetria.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.telemetria.app.core.database.dao.SessionDao
import com.telemetria.app.core.database.dao.TelemetryPointDao
import com.telemetria.app.core.database.entity.SessionEntity
import com.telemetria.app.core.database.entity.TelemetryPointEntity

@Database(
    entities = [SessionEntity::class, TelemetryPointEntity::class],
    version = 3,
    exportSchema = false
)
abstract class TelemetryDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun telemetryPointDao(): TelemetryPointDao

    companion object {
        @Volatile
        private var INSTANCE: TelemetryDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE telemetry_points ADD COLUMN intakeAirTempC INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE telemetry_points ADD COLUMN boostPressureBar REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE telemetry_points ADD COLUMN fuelConsumptionL100km REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE telemetry_points ADD COLUMN gear TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): TelemetryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TelemetryDatabase::class.java,
                    "telemetry_master.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

