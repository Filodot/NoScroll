package com.filodot.noscroll.data.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_usage")
data class DailyUsageEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_date")
    val localDate: String,
    @ColumnInfo(name = "youtube_seconds")
    val youtubeSeconds: Long,
    @ColumnInfo(name = "shorts_seconds")
    val shortsSeconds: Long,
    @ColumnInfo(name = "instagram_seconds", defaultValue = "0")
    val instagramSeconds: Long = 0,
    @ColumnInfo(name = "emergency_youtube_seconds", defaultValue = "0")
    val emergencyYoutubeSeconds: Long,
    @ColumnInfo(name = "gates_shown")
    val gatesShown: Int,
    @ColumnInfo(name = "tasks_solved")
    val tasksSolved: Int,
    @ColumnInfo(name = "task_exits")
    val taskExits: Int,
    @ColumnInfo(name = "last_updated_elapsed_millis")
    val lastUpdatedElapsedMillis: Long?,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "gate_cycles")
data class GateCycleEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "local_date")
    val localDate: String,
    @ColumnInfo(name = "used_seconds")
    val usedSeconds: Long,
    @ColumnInfo(name = "pending_task_id")
    val pendingTaskId: String?,
    @ColumnInfo(name = "interval_block_streak", defaultValue = "0")
    val intervalBlockStreak: Int,
    @ColumnInfo(name = "last_interval_block_at_epoch_millis")
    val lastIntervalBlockAtEpochMillis: Long?,
    @ColumnInfo(name = "entry_cooldown_until_epoch_millis")
    val entryCooldownUntilEpochMillis: Long?,
    @ColumnInfo(name = "instagram_used_seconds", defaultValue = "0")
    val instagramUsedSeconds: Long = 0,
    @ColumnInfo(name = "instagram_entry_cooldown_until_epoch_millis")
    val instagramEntryCooldownUntilEpochMillis: Long? = null,
    @ColumnInfo(name = "difficulty_load_seconds", defaultValue = "0")
    val difficultyLoadSeconds: Long = 0,
    @ColumnInfo(name = "difficulty_load_updated_at_epoch_millis")
    val difficultyLoadUpdatedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "difficulty_recovery_seconds", defaultValue = "0")
    val difficultyRecoverySeconds: Long = 0,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "pending_tasks")
data class PendingTaskEntity(
    @PrimaryKey
    val id: String,
    val operation: String,
    @ColumnInfo(name = "left_operand")
    val leftOperand: Int,
    @ColumnInfo(name = "right_operand")
    val rightOperand: Int,
    @ColumnInfo(name = "expected_answer")
    val expectedAnswer: Int,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "wrong_attempts")
    val wrongAttempts: Int,
    val solved: Boolean,
    @ColumnInfo(defaultValue = "'MEDIUM'")
    val difficulty: String,
    @ColumnInfo(defaultValue = "'INTERVAL'")
    val trigger: String,
    @ColumnInfo(defaultValue = "'YOUTUBE_SHORTS'")
    val target: String = "YOUTUBE_SHORTS",
    @ColumnInfo(name = "task_type", defaultValue = "'ARITHMETIC'")
    val taskType: String = "ARITHMETIC",
    @ColumnInfo(name = "completion_mode", defaultValue = "'CHECKED_ANSWER'")
    val completionMode: String = "CHECKED_ANSWER",
    @ColumnInfo(defaultValue = "''")
    val prompt: String = "",
    @ColumnInfo(name = "custom_preset_id")
    val customPresetId: String? = null,
)

@Entity(tableName = "custom_task_presets")
data class CustomTaskPresetEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val instruction: String,
    val enabled: Boolean,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "emergency_events")
data class EmergencyEventEntity(
    @PrimaryKey
    val id: String,
    val reason: String,
    @ColumnInfo(name = "activated_at_epoch_millis")
    val activatedAtEpochMillis: Long,
    @ColumnInfo(name = "deactivated_at_epoch_millis")
    val deactivatedAtEpochMillis: Long?,
    @ColumnInfo(name = "activation_source")
    val activationSource: String,
    @ColumnInfo(name = "youtube_seconds_during")
    val youtubeSecondsDuring: Long,
)

@Entity(tableName = "learning_courses")
data class LearningCourseEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    @ColumnInfo(name = "language_tag")
    val languageTag: String,
    val origin: String,
    @ColumnInfo(name = "grounding_mode")
    val groundingMode: String,
    val status: String,
    @ColumnInfo(name = "plan_version")
    val planVersion: Int,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "learning_sources")
data class LearningSourceEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "course_id")
    val courseId: String,
    val title: String,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "content_hash")
    val contentHash: String?,
    @ColumnInfo(name = "imported_at_epoch_millis")
    val importedAtEpochMillis: Long,
)

@Entity(tableName = "curriculum_nodes")
data class CurriculumNodeEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "course_id")
    val courseId: String,
    @ColumnInfo(name = "parent_id")
    val parentId: String?,
    @ColumnInfo(name = "node_type")
    val nodeType: String,
    val title: String,
    val description: String,
    val position: Int,
    @ColumnInfo(name = "estimated_minutes")
    val estimatedMinutes: Int,
    val optional: Boolean,
    @ColumnInfo(name = "concept_ids_json")
    val conceptIdsJson: String,
)

@Entity(tableName = "learning_concepts")
data class LearningConceptEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "course_id")
    val courseId: String,
    val title: String,
    val summary: String,
    val position: Int,
    @ColumnInfo(name = "prerequisite_ids_json")
    val prerequisiteIdsJson: String,
    @ColumnInfo(name = "citations_json")
    val citationsJson: String,
)

@Entity(tableName = "lesson_packages")
data class LessonPackageEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "course_id")
    val courseId: String,
    @ColumnInfo(name = "curriculum_node_id")
    val curriculumNodeId: String,
    @ColumnInfo(name = "plan_version")
    val planVersion: Int,
    val title: String,
    val introduction: String,
    @ColumnInfo(name = "grounding_mode")
    val groundingMode: String,
    val status: String,
    @ColumnInfo(name = "generated_at_epoch_millis")
    val generatedAtEpochMillis: Long,
)

@Entity(tableName = "learning_activities")
data class LearningActivityEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "lesson_id")
    val lessonId: String,
    val position: Int,
    @ColumnInfo(name = "concept_ids_json")
    val conceptIdsJson: String,
    val prompt: String,
    val explanation: String,
    val difficulty: String,
    @ColumnInfo(name = "estimated_seconds")
    val estimatedSeconds: Int,
    @ColumnInfo(name = "activity_kind")
    val activityKind: String,
    @ColumnInfo(name = "content_json")
    val contentJson: String,
    @ColumnInfo(name = "citations_json")
    val citationsJson: String,
    @ColumnInfo(name = "provider_id")
    val providerId: String?,
    @ColumnInfo(name = "model_id")
    val modelId: String?,
    @ColumnInfo(name = "prompt_version")
    val promptVersion: String?,
    @ColumnInfo(name = "generated_at_epoch_millis")
    val generatedAtEpochMillis: Long?,
    val correctness: Int,
    val grounding: Int,
    val clarity: Int,
    val pedagogy: Int,
    @ColumnInfo(name = "format_validity")
    val formatValidity: Int,
    @ColumnInfo(name = "reviewed_at_epoch_millis")
    val reviewedAtEpochMillis: Long,
)

@Entity(tableName = "learning_attempts")
data class LearningAttemptEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "course_id")
    val courseId: String,
    @ColumnInfo(name = "lesson_id")
    val lessonId: String,
    @ColumnInfo(name = "activity_id")
    val activityId: String,
    @ColumnInfo(name = "concept_ids_json")
    val conceptIdsJson: String,
    @ColumnInfo(name = "activity_kind")
    val activityKind: String,
    val result: String,
    @ColumnInfo(name = "hints_used")
    val hintsUsed: Int,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int,
    val confidence: String,
    @ColumnInfo(name = "occurred_at_epoch_millis")
    val occurredAtEpochMillis: Long,
    @ColumnInfo(name = "local_date")
    val localDate: String,
)

@Entity(tableName = "concept_mastery")
data class ConceptMasteryEntity(
    @PrimaryKey
    @ColumnInfo(name = "concept_id")
    val conceptId: String,
    val score: Int,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int,
    @ColumnInfo(name = "correct_count")
    val correctCount: Int,
    @ColumnInfo(name = "consecutive_correct")
    val consecutiveCorrect: Int,
    @ColumnInfo(name = "successful_formats_json")
    val successfulFormatsJson: String,
    @ColumnInfo(name = "successful_dates_json")
    val successfulDatesJson: String,
    @ColumnInfo(name = "last_attempt_at_epoch_millis")
    val lastAttemptAtEpochMillis: Long?,
    @ColumnInfo(name = "next_review_at_epoch_millis")
    val nextReviewAtEpochMillis: Long?,
)
