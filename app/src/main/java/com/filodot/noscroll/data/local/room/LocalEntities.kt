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
