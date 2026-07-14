package com.filodot.noscroll.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        DailyUsageEntity::class,
        GateCycleEntity::class,
        PendingTaskEntity::class,
        EmergencyEventEntity::class,
    ],
    version = NoScrollDatabase.VERSION,
    exportSchema = true,
)
abstract class NoScrollDatabase : RoomDatabase() {
    abstract fun dailyUsageDao(): DailyUsageDao

    abstract fun gateCycleDao(): GateCycleDao

    abstract fun pendingTaskDao(): PendingTaskDao

    abstract fun emergencyEventDao(): EmergencyEventDao

    abstract fun taskGrantDao(): TaskGrantDao

    abstract fun retentionDao(): RetentionDao

    companion object {
        const val DATABASE_NAME = "noscroll.db"
        const val VERSION = 2

        val MIGRATION_1_2 = Migration(1, 2) { database ->
            database.execSQL(
                "ALTER TABLE daily_usage " +
                    "ADD COLUMN emergency_youtube_seconds INTEGER NOT NULL DEFAULT 0",
            )
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)

        fun build(context: Context, name: String = DATABASE_NAME): NoScrollDatabase =
            Room.databaseBuilder(context, NoScrollDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
