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
        const val VERSION = 3

        val MIGRATION_1_2 = Migration(1, 2) { database ->
            database.execSQL(
                "ALTER TABLE daily_usage " +
                    "ADD COLUMN emergency_youtube_seconds INTEGER NOT NULL DEFAULT 0",
            )
        }

        val MIGRATION_2_3 = Migration(2, 3) { database ->
            database.execSQL(
                "ALTER TABLE gate_cycles " +
                    "ADD COLUMN interval_block_streak INTEGER NOT NULL DEFAULT 0",
            )
            database.execSQL(
                "ALTER TABLE gate_cycles " +
                    "ADD COLUMN last_interval_block_at_epoch_millis INTEGER",
            )
            database.execSQL(
                "ALTER TABLE pending_tasks " +
                    "ADD COLUMN difficulty TEXT NOT NULL DEFAULT 'MEDIUM'",
            )
            database.execSQL(
                "ALTER TABLE pending_tasks " +
                    "ADD COLUMN trigger TEXT NOT NULL DEFAULT 'INTERVAL'",
            )
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun build(context: Context, name: String = DATABASE_NAME): NoScrollDatabase =
            Room.databaseBuilder(context, NoScrollDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
