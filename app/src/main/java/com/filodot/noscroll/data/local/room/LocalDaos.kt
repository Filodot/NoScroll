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
abstract class LearningDao {
    @Query("SELECT * FROM learning_courses ORDER BY updated_at_epoch_millis DESC, id")
    abstract fun observeCourses(): Flow<List<LearningCourseEntity>>

    @Query("SELECT * FROM learning_courses WHERE id = :courseId")
    abstract suspend fun getCourse(courseId: String): LearningCourseEntity?

    @Query("SELECT * FROM learning_sources WHERE course_id = :courseId ORDER BY imported_at_epoch_millis, id")
    abstract suspend fun getSources(courseId: String): List<LearningSourceEntity>

    @Query("SELECT * FROM curriculum_nodes WHERE course_id = :courseId ORDER BY position, id")
    abstract suspend fun getCurriculumNodes(courseId: String): List<CurriculumNodeEntity>

    @Query("SELECT * FROM learning_concepts WHERE course_id = :courseId ORDER BY position, id")
    abstract suspend fun getConcepts(courseId: String): List<LearningConceptEntity>

    @Query("SELECT * FROM lesson_packages WHERE id = :lessonId")
    abstract suspend fun getLesson(lessonId: String): LessonPackageEntity?

    @Query("SELECT * FROM learning_activities WHERE lesson_id = :lessonId ORDER BY position, id")
    abstract suspend fun getActivities(lessonId: String): List<LearningActivityEntity>

    @Query(
        "SELECT * FROM lesson_packages WHERE course_id = :courseId AND status = 'VALIDATED' " +
            "ORDER BY generated_at_epoch_millis, id LIMIT 1",
    )
    abstract suspend fun getNextValidatedLesson(courseId: String): LessonPackageEntity?

    @Query("SELECT COUNT(*) FROM lesson_packages WHERE course_id = :courseId AND status = 'VALIDATED'")
    abstract fun observeValidatedLessonCount(courseId: String): Flow<Int>

    @Query("SELECT * FROM learning_attempts WHERE course_id = :courseId ORDER BY occurred_at_epoch_millis, id")
    abstract suspend fun getAttempts(courseId: String): List<LearningAttemptEntity>

    @Query(
        "SELECT concept_mastery.* FROM concept_mastery " +
            "INNER JOIN learning_concepts ON learning_concepts.id = concept_mastery.concept_id " +
            "WHERE learning_concepts.course_id = :courseId ORDER BY learning_concepts.position",
    )
    abstract suspend fun getMastery(courseId: String): List<ConceptMasteryEntity>

    @Upsert
    protected abstract suspend fun upsertCourse(entity: LearningCourseEntity)

    @Upsert
    protected abstract suspend fun upsertSources(entities: List<LearningSourceEntity>)

    @Upsert
    protected abstract suspend fun upsertCurriculumNodes(entities: List<CurriculumNodeEntity>)

    @Upsert
    protected abstract suspend fun upsertConcepts(entities: List<LearningConceptEntity>)

    @Upsert
    protected abstract suspend fun upsertLesson(entity: LessonPackageEntity)

    @Upsert
    protected abstract suspend fun upsertActivities(entities: List<LearningActivityEntity>)

    @Upsert
    abstract suspend fun upsertAttempt(entity: LearningAttemptEntity)

    @Upsert
    abstract suspend fun upsertMastery(entity: ConceptMasteryEntity)

    @Query("DELETE FROM learning_sources WHERE course_id = :courseId")
    protected abstract suspend fun deleteSources(courseId: String)

    @Query("DELETE FROM curriculum_nodes WHERE course_id = :courseId")
    protected abstract suspend fun deleteCurriculumNodes(courseId: String)

    @Query("DELETE FROM learning_concepts WHERE course_id = :courseId")
    protected abstract suspend fun deleteConcepts(courseId: String)

    @Query("DELETE FROM concept_mastery WHERE concept_id NOT IN (SELECT id FROM learning_concepts)")
    protected abstract suspend fun deleteOrphanMastery()

    @Query("DELETE FROM learning_activities WHERE lesson_id = :lessonId")
    protected abstract suspend fun deleteActivities(lessonId: String)

    @Query(
        "UPDATE lesson_packages SET status = 'CONSUMED' " +
            "WHERE id = :lessonId AND status = 'VALIDATED'",
    )
    protected abstract suspend fun consumeLesson(lessonId: String): Int

    @Query(
        "DELETE FROM concept_mastery WHERE concept_id IN " +
            "(SELECT id FROM learning_concepts WHERE course_id = :courseId)",
    )
    protected abstract suspend fun deleteCourseMastery(courseId: String)

    @Query("DELETE FROM learning_attempts WHERE course_id = :courseId")
    protected abstract suspend fun deleteCourseAttempts(courseId: String)

    @Query(
        "DELETE FROM learning_activities WHERE lesson_id IN " +
            "(SELECT id FROM lesson_packages WHERE course_id = :courseId)",
    )
    protected abstract suspend fun deleteCourseActivities(courseId: String)

    @Query("DELETE FROM lesson_packages WHERE course_id = :courseId")
    protected abstract suspend fun deleteCourseLessons(courseId: String)

    @Query("DELETE FROM learning_courses WHERE id = :courseId")
    protected abstract suspend fun deleteCourseRow(courseId: String)

    @Transaction
    open suspend fun saveCourseContent(
        course: LearningCourseEntity,
        sources: List<LearningSourceEntity>,
        nodes: List<CurriculumNodeEntity>,
        concepts: List<LearningConceptEntity>,
    ) {
        upsertCourse(course)
        deleteSources(course.id)
        deleteCurriculumNodes(course.id)
        deleteConcepts(course.id)
        if (sources.isNotEmpty()) upsertSources(sources)
        if (nodes.isNotEmpty()) upsertCurriculumNodes(nodes)
        if (concepts.isNotEmpty()) upsertConcepts(concepts)
        deleteOrphanMastery()
    }

    @Transaction
    open suspend fun saveLesson(
        lesson: LessonPackageEntity,
        activities: List<LearningActivityEntity>,
    ) {
        upsertLesson(lesson)
        deleteActivities(lesson.id)
        if (activities.isNotEmpty()) upsertActivities(activities)
    }

    @Transaction
    open suspend fun takeNextValidatedLesson(courseId: String): LessonPackageEntity? {
        val lesson = getNextValidatedLesson(courseId) ?: return null
        return if (consumeLesson(lesson.id) == 1) lesson.copy(status = "CONSUMED") else null
    }

    @Transaction
    open suspend fun deleteCourse(courseId: String) {
        deleteCourseMastery(courseId)
        deleteCourseAttempts(courseId)
        deleteCourseActivities(courseId)
        deleteCourseLessons(courseId)
        deleteSources(courseId)
        deleteCurriculumNodes(courseId)
        deleteConcepts(courseId)
        deleteCourseRow(courseId)
    }
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
