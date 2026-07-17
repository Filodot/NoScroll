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
        CustomTaskPresetEntity::class,
    ],
    version = NoScrollDatabase.VERSION,
    exportSchema = true,
)
abstract class NoScrollDatabase : RoomDatabase() {
    abstract fun dailyUsageDao(): DailyUsageDao

    abstract fun gateCycleDao(): GateCycleDao

    abstract fun pendingTaskDao(): PendingTaskDao

    abstract fun customTaskPresetDao(): CustomTaskPresetDao

    abstract fun emergencyEventDao(): EmergencyEventDao

    abstract fun taskGrantDao(): TaskGrantDao

    abstract fun retentionDao(): RetentionDao

    companion object {
        const val DATABASE_NAME = "noscroll.db"
        const val VERSION = 5

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

        val MIGRATION_3_4 = Migration(3, 4) { database ->
            database.execSQL(
                "ALTER TABLE gate_cycles " +
                    "ADD COLUMN entry_cooldown_until_epoch_millis INTEGER",
            )
        }

        val MIGRATION_4_5 = Migration(4, 5) { database ->
            database.execSQL(
                "ALTER TABLE daily_usage ADD COLUMN instagram_seconds INTEGER NOT NULL DEFAULT 0",
            )
            database.execSQL(
                "ALTER TABLE gate_cycles ADD COLUMN instagram_used_seconds INTEGER NOT NULL DEFAULT 0",
            )
            database.execSQL(
                "ALTER TABLE gate_cycles ADD COLUMN instagram_entry_cooldown_until_epoch_millis INTEGER",
            )
            database.execSQL(
                "ALTER TABLE gate_cycles ADD COLUMN difficulty_load_seconds INTEGER NOT NULL DEFAULT 0",
            )
            database.execSQL(
                "ALTER TABLE gate_cycles ADD COLUMN difficulty_load_updated_at_epoch_millis INTEGER",
            )
            database.execSQL(
                "ALTER TABLE gate_cycles ADD COLUMN difficulty_recovery_seconds INTEGER NOT NULL DEFAULT 0",
            )
            database.execSQL(
                "ALTER TABLE pending_tasks ADD COLUMN target TEXT NOT NULL DEFAULT 'YOUTUBE_SHORTS'",
            )
            database.execSQL(
                "ALTER TABLE pending_tasks ADD COLUMN task_type TEXT NOT NULL DEFAULT 'ARITHMETIC'",
            )
            database.execSQL(
                "ALTER TABLE pending_tasks ADD COLUMN completion_mode TEXT NOT NULL DEFAULT 'CHECKED_ANSWER'",
            )
            database.execSQL(
                "ALTER TABLE pending_tasks ADD COLUMN prompt TEXT NOT NULL DEFAULT ''",
            )
            database.execSQL("ALTER TABLE pending_tasks ADD COLUMN custom_preset_id TEXT")
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS custom_task_presets (" +
                    "id TEXT NOT NULL, title TEXT NOT NULL, instruction TEXT NOT NULL, " +
                    "enabled INTEGER NOT NULL, created_at_epoch_millis INTEGER NOT NULL, " +
                    "PRIMARY KEY(id))",
            )
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
        )

        fun build(context: Context, name: String = DATABASE_NAME): NoScrollDatabase =
            Room.databaseBuilder(context, NoScrollDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
