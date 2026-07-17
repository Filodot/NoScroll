package com.filodot.noscroll.data.local.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.filodot.noscroll.core.model.TaskTrigger
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyUsageDao {
    @Query("SELECT * FROM daily_usage ORDER BY local_date DESC LIMIT 1")
    fun observeLatest(): Flow<DailyUsageEntity?>

    @Query("SELECT * FROM daily_usage WHERE local_date = :localDate")
    suspend fun get(localDate: String): DailyUsageEntity?

    @Upsert
    suspend fun upsert(entity: DailyUsageEntity)
}

@Dao
interface GateCycleDao {
    @Query("SELECT * FROM gate_cycles WHERE id = :id")
    fun observe(id: String): Flow<GateCycleEntity?>

    @Query("SELECT * FROM gate_cycles WHERE id = :id")
    suspend fun get(id: String): GateCycleEntity?

    @Upsert
    suspend fun upsert(entity: GateCycleEntity)
}

@Dao
interface PendingTaskDao {
    @Query(
        "SELECT * FROM pending_tasks " +
            "WHERE solved = 0 ORDER BY created_at_epoch_millis DESC LIMIT 1",
    )
    fun observePending(): Flow<PendingTaskEntity?>

    @Query("SELECT * FROM pending_tasks WHERE id = :id")
    suspend fun get(id: String): PendingTaskEntity?

    @Upsert
    suspend fun upsert(entity: PendingTaskEntity)

    @Query("DELETE FROM pending_tasks WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface CustomTaskPresetDao {
    @Query("SELECT * FROM custom_task_presets ORDER BY created_at_epoch_millis, id")
    fun observeAll(): Flow<List<CustomTaskPresetEntity>>

    @Upsert
    suspend fun upsert(entity: CustomTaskPresetEntity)

    @Query("DELETE FROM custom_task_presets WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface EmergencyEventDao {
    @Query(
        "SELECT * FROM emergency_events WHERE deactivated_at_epoch_millis IS NULL " +
            "ORDER BY activated_at_epoch_millis DESC LIMIT 1",
    )
    fun observeActive(): Flow<EmergencyEventEntity?>

    @Query("SELECT * FROM emergency_events ORDER BY activated_at_epoch_millis DESC")
    fun observeHistory(): Flow<List<EmergencyEventEntity>>

    @Query("SELECT * FROM emergency_events WHERE id = :id")
    suspend fun get(id: String): EmergencyEventEntity?

    @Upsert
    suspend fun upsert(entity: EmergencyEventEntity)

    @Query("DELETE FROM emergency_events WHERE deactivated_at_epoch_millis IS NOT NULL")
    suspend fun deleteClosedHistory(): Int
}

@Dao
abstract class TaskGrantDao {
    @Query("SELECT * FROM pending_tasks WHERE id = :taskId AND solved = 0")
    protected abstract suspend fun getUnsolvedTask(taskId: String): PendingTaskEntity?

    @Query("SELECT * FROM gate_cycles WHERE id = :cycleId")
    protected abstract suspend fun getCycle(cycleId: String): GateCycleEntity?

    @Query("SELECT COUNT(*) FROM daily_usage WHERE local_date = :localDate")
    protected abstract suspend fun dailyUsageCount(localDate: String): Int

    @Query("UPDATE pending_tasks SET solved = 1 WHERE id = :taskId AND solved = 0")
    protected abstract suspend fun markSolved(taskId: String): Int

    @Query(
        "UPDATE daily_usage SET tasks_solved = tasks_solved + 1, " +
            "updated_at_epoch_millis = :updatedAtEpochMillis WHERE local_date = :localDate",
    )
    protected abstract suspend fun incrementTasksSolved(
        localDate: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        "UPDATE gate_cycles SET used_seconds = 0, pending_task_id = NULL, " +
            "entry_cooldown_until_epoch_millis = :entryCooldownUntilEpochMillis, " +
            "updated_at_epoch_millis = :updatedAtEpochMillis " +
            "WHERE id = :cycleId AND pending_task_id = :taskId",
    )
    protected abstract suspend fun resetYoutubeCycle(
        cycleId: String,
        taskId: String,
        entryCooldownUntilEpochMillis: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        "UPDATE gate_cycles SET instagram_used_seconds = 0, pending_task_id = NULL, " +
            "instagram_entry_cooldown_until_epoch_millis = :entryCooldownUntilEpochMillis, " +
            "updated_at_epoch_millis = :updatedAtEpochMillis " +
            "WHERE id = :cycleId AND pending_task_id = :taskId",
    )
    protected abstract suspend fun resetInstagramCycle(
        cycleId: String,
        taskId: String,
        entryCooldownUntilEpochMillis: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("DELETE FROM pending_tasks WHERE id = :taskId AND solved = 1")
    protected abstract suspend fun deleteSolved(taskId: String): Int

    @Transaction
    open suspend fun grant(
        taskId: String,
        localDate: String,
        cycleId: String,
        updatedAtEpochMillis: Long,
        entryCooldownUntilEpochMillis: Long,
    ): Boolean {
        val task = getUnsolvedTask(taskId) ?: return false
        val cycle = getCycle(cycleId) ?: return false
        if (cycle.pendingTaskId != task.id || dailyUsageCount(localDate) != 1) return false

        check(markSolved(taskId) == 1) { "Task grant lost the pending task" }
        check(incrementTasksSolved(localDate, updatedAtEpochMillis) == 1) {
            "Task grant lost the daily aggregate"
        }
        val cycleUpdated = if (task.target == "INSTAGRAM") {
            resetInstagramCycle(
                cycleId = cycleId,
                taskId = taskId,
                entryCooldownUntilEpochMillis = entryCooldownUntilEpochMillis,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
        } else {
            resetYoutubeCycle(
                cycleId = cycleId,
                taskId = taskId,
                entryCooldownUntilEpochMillis = entryCooldownUntilEpochMillis,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
        }
        check(cycleUpdated == 1) { "Task grant lost the gate cycle" }
        check(deleteSolved(taskId) == 1) { "Task grant could not remove the solved task" }
        return true
    }
}

data class RetentionResult(
    val dailyRowsDeleted: Int,
    val emergencyRowsDeleted: Int,
    val solvedTaskRowsDeleted: Int,
)

@Dao
abstract class RetentionDao {
    @Query("DELETE FROM daily_usage WHERE local_date < :cutoffLocalDate")
    protected abstract suspend fun deleteOldDailyUsage(cutoffLocalDate: String): Int

    @Query(
        "DELETE FROM emergency_events WHERE deactivated_at_epoch_millis IS NOT NULL " +
            "AND deactivated_at_epoch_millis < :cutoffEpochMillis",
    )
    protected abstract suspend fun deleteOldClosedEmergencyEvents(cutoffEpochMillis: Long): Int

    @Query(
        "DELETE FROM pending_tasks WHERE solved = 1 " +
            "AND created_at_epoch_millis < :cutoffEpochMillis",
    )
    protected abstract suspend fun deleteOldSolvedTasks(cutoffEpochMillis: Long): Int

    @Transaction
    open suspend fun deleteOlderThan(
        cutoffLocalDate: String,
        cutoffEpochMillis: Long,
    ): RetentionResult = RetentionResult(
        dailyRowsDeleted = deleteOldDailyUsage(cutoffLocalDate),
        emergencyRowsDeleted = deleteOldClosedEmergencyEvents(cutoffEpochMillis),
        solvedTaskRowsDeleted = deleteOldSolvedTasks(cutoffEpochMillis),
    )
}
