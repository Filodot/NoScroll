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
        LearningCourseEntity::class,
        LearningSourceEntity::class,
        CurriculumNodeEntity::class,
        LearningConceptEntity::class,
        LessonPackageEntity::class,
        LearningActivityEntity::class,
        LearningAttemptEntity::class,
        ConceptMasteryEntity::class,
    ],
    version = NoScrollDatabase.VERSION,
    exportSchema = true,
)
abstract class NoScrollDatabase : RoomDatabase() {
    abstract fun dailyUsageDao(): DailyUsageDao

    abstract fun gateCycleDao(): GateCycleDao

    abstract fun pendingTaskDao(): PendingTaskDao

    abstract fun customTaskPresetDao(): CustomTaskPresetDao

    abstract fun learningDao(): LearningDao

    abstract fun emergencyEventDao(): EmergencyEventDao

    abstract fun taskGrantDao(): TaskGrantDao

    abstract fun retentionDao(): RetentionDao

    companion object {
        const val DATABASE_NAME = "noscroll.db"
        const val VERSION = 6

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

        val MIGRATION_5_6 = Migration(5, 6) { database ->
            LEARNING_TABLE_STATEMENTS.forEach(database::execSQL)
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
        )

        fun build(context: Context, name: String = DATABASE_NAME): NoScrollDatabase =
            Room.databaseBuilder(context, NoScrollDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                .build()

        private val LEARNING_TABLE_STATEMENTS = listOf(
            "CREATE TABLE IF NOT EXISTS learning_courses (" +
                "id TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, " +
                "language_tag TEXT NOT NULL, origin TEXT NOT NULL, grounding_mode TEXT NOT NULL, " +
                "status TEXT NOT NULL, plan_version INTEGER NOT NULL, " +
                "created_at_epoch_millis INTEGER NOT NULL, updated_at_epoch_millis INTEGER NOT NULL, " +
                "PRIMARY KEY(id))",
            "CREATE TABLE IF NOT EXISTS learning_sources (" +
                "id TEXT NOT NULL, course_id TEXT NOT NULL, title TEXT NOT NULL, " +
                "source_type TEXT NOT NULL, content_hash TEXT, " +
                "imported_at_epoch_millis INTEGER NOT NULL, PRIMARY KEY(id))",
            "CREATE TABLE IF NOT EXISTS curriculum_nodes (" +
                "id TEXT NOT NULL, course_id TEXT NOT NULL, parent_id TEXT, node_type TEXT NOT NULL, " +
                "title TEXT NOT NULL, description TEXT NOT NULL, position INTEGER NOT NULL, " +
                "estimated_minutes INTEGER NOT NULL, optional INTEGER NOT NULL, " +
                "concept_ids_json TEXT NOT NULL, PRIMARY KEY(id))",
            "CREATE TABLE IF NOT EXISTS learning_concepts (" +
                "id TEXT NOT NULL, course_id TEXT NOT NULL, title TEXT NOT NULL, " +
                "summary TEXT NOT NULL, position INTEGER NOT NULL, " +
                "prerequisite_ids_json TEXT NOT NULL, citations_json TEXT NOT NULL, " +
                "PRIMARY KEY(id))",
            "CREATE TABLE IF NOT EXISTS lesson_packages (" +
                "id TEXT NOT NULL, course_id TEXT NOT NULL, curriculum_node_id TEXT NOT NULL, " +
                "plan_version INTEGER NOT NULL, title TEXT NOT NULL, introduction TEXT NOT NULL, " +
                "grounding_mode TEXT NOT NULL, status TEXT NOT NULL, " +
                "generated_at_epoch_millis INTEGER NOT NULL, PRIMARY KEY(id))",
            "CREATE TABLE IF NOT EXISTS learning_activities (" +
                "id TEXT NOT NULL, lesson_id TEXT NOT NULL, position INTEGER NOT NULL, " +
                "concept_ids_json TEXT NOT NULL, prompt TEXT NOT NULL, explanation TEXT NOT NULL, " +
                "difficulty TEXT NOT NULL, estimated_seconds INTEGER NOT NULL, " +
                "activity_kind TEXT NOT NULL, content_json TEXT NOT NULL, citations_json TEXT NOT NULL, " +
                "provider_id TEXT, model_id TEXT, prompt_version TEXT, " +
                "generated_at_epoch_millis INTEGER, correctness INTEGER NOT NULL, " +
                "grounding INTEGER NOT NULL, clarity INTEGER NOT NULL, pedagogy INTEGER NOT NULL, " +
                "format_validity INTEGER NOT NULL, reviewed_at_epoch_millis INTEGER NOT NULL, " +
                "PRIMARY KEY(id))",
            "CREATE TABLE IF NOT EXISTS learning_attempts (" +
                "id TEXT NOT NULL, course_id TEXT NOT NULL, lesson_id TEXT NOT NULL, " +
                "activity_id TEXT NOT NULL, concept_ids_json TEXT NOT NULL, " +
                "activity_kind TEXT NOT NULL, result TEXT NOT NULL, hints_used INTEGER NOT NULL, " +
                "duration_seconds INTEGER NOT NULL, confidence TEXT NOT NULL, " +
                "occurred_at_epoch_millis INTEGER NOT NULL, local_date TEXT NOT NULL, PRIMARY KEY(id))",
            "CREATE TABLE IF NOT EXISTS concept_mastery (" +
                "concept_id TEXT NOT NULL, score INTEGER NOT NULL, attempt_count INTEGER NOT NULL, " +
                "correct_count INTEGER NOT NULL, consecutive_correct INTEGER NOT NULL, " +
                "successful_formats_json TEXT NOT NULL, successful_dates_json TEXT NOT NULL, " +
                "last_attempt_at_epoch_millis INTEGER, next_review_at_epoch_millis INTEGER, " +
                "PRIMARY KEY(concept_id))",
        )
    }
}
