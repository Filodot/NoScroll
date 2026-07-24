package com.filodot.noscroll.core.testing

import com.filodot.noscroll.core.contracts.EmergencyRepository
import com.filodot.noscroll.core.contracts.SettingsRepository
import com.filodot.noscroll.core.contracts.LearningRepository
import com.filodot.noscroll.core.contracts.TaskRepository
import com.filodot.noscroll.core.contracts.TaskPresetRepository
import com.filodot.noscroll.core.contracts.UsageRepository
import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.CustomTaskPreset
import com.filodot.noscroll.core.model.EmergencyEvent
import com.filodot.noscroll.core.model.EmergencyState
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.model.UserSettings
import com.filodot.noscroll.core.learning.model.ConceptMastery
import com.filodot.noscroll.core.learning.model.LearningAttempt
import com.filodot.noscroll.core.learning.model.LearningCourse
import com.filodot.noscroll.core.learning.model.LearningCourseContent
import com.filodot.noscroll.core.learning.model.LessonPackage
import com.filodot.noscroll.core.learning.model.LessonPackageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class InMemorySettingsRepository(
    initialSettings: UserSettings = UserSettings(),
) : SettingsRepository {
    private val mutableSettings = MutableStateFlow(initialSettings)

    override val settings: StateFlow<UserSettings> = mutableSettings

    override suspend fun save(settings: UserSettings) {
        mutableSettings.value = settings
    }
}

class InMemoryUsageRepository(
    initialDailyUsage: DailyUsage,
    initialGateCycle: GateCycle,
) : UsageRepository {
    private val mutableDailyUsage = MutableStateFlow(initialDailyUsage)
    private val mutableGateCycle = MutableStateFlow(initialGateCycle)

    override val dailyUsage: StateFlow<DailyUsage> = mutableDailyUsage
    override val gateCycle: StateFlow<GateCycle> = mutableGateCycle

    override suspend fun saveDailyUsage(usage: DailyUsage) {
        mutableDailyUsage.value = usage
    }

    override suspend fun saveGateCycle(cycle: GateCycle) {
        mutableGateCycle.value = cycle
    }
}

class InMemoryTaskRepository(
    initialTask: PendingTask? = null,
) : TaskRepository {
    private val mutablePendingTask = MutableStateFlow(initialTask)

    override val pendingTask: StateFlow<PendingTask?> = mutablePendingTask

    override suspend fun save(task: PendingTask) {
        mutablePendingTask.value = task
    }

    override suspend fun clear(taskId: String) {
        if (mutablePendingTask.value?.id == taskId) {
            mutablePendingTask.value = null
        }
    }
}

class InMemoryTaskPresetRepository(
    initialPresets: List<CustomTaskPreset> = emptyList(),
) : TaskPresetRepository {
    private val mutablePresets = MutableStateFlow(initialPresets)
    override val presets: StateFlow<List<CustomTaskPreset>> = mutablePresets

    override suspend fun save(preset: CustomTaskPreset) {
        mutablePresets.value = mutablePresets.value.filterNot { it.id == preset.id } + preset
    }

    override suspend fun delete(presetId: String) {
        mutablePresets.value = mutablePresets.value.filterNot { it.id == presetId }
    }
}

class InMemoryLearningRepository(
    initialContent: List<LearningCourseContent> = emptyList(),
    initialLessons: List<LessonPackage> = emptyList(),
) : LearningRepository {
    private val contentById = initialContent.associateBy { it.course.id }.toMutableMap()
    private val lessonsById = initialLessons.associateBy(LessonPackage::id).toMutableMap()
    private val mutableLessons = MutableStateFlow(initialLessons)
    private val attemptsById = mutableMapOf<String, LearningAttempt>()
    private val masteryByConceptId = mutableMapOf<String, ConceptMastery>()
    private val mutableCourses = MutableStateFlow(
        initialContent.map(LearningCourseContent::course),
    )

    override val courses: Flow<List<LearningCourse>> = mutableCourses

    override suspend fun saveCourseContent(content: LearningCourseContent) {
        contentById[content.course.id] = content
        mutableCourses.value = contentById.values
            .map(LearningCourseContent::course)
            .sortedByDescending(LearningCourse::updatedAt)
    }

    override suspend fun getCourseContent(courseId: String): LearningCourseContent? =
        contentById[courseId]

    override suspend fun saveLesson(lesson: LessonPackage) {
        lessonsById[lesson.id] = lesson
        publishLessons()
    }

    override suspend fun peekNextLesson(courseId: String): LessonPackage? =
        validatedLessons(courseId).firstOrNull()

    override suspend fun takeNextLesson(courseId: String): LessonPackage? {
        val lesson = validatedLessons(courseId).firstOrNull() ?: return null
        val consumed = lesson.copy(status = LessonPackageStatus.CONSUMED)
        lessonsById[lesson.id] = consumed
        publishLessons()
        return consumed
    }

    override fun observeValidatedLessonCount(courseId: String): Flow<Int> =
        mutableLessons.map { lessons ->
            lessons.count {
                it.courseId == courseId && it.status == LessonPackageStatus.VALIDATED
            }
        }

    override suspend fun saveAttempt(attempt: LearningAttempt) {
        attemptsById[attempt.id] = attempt
    }

    override suspend fun getAttempts(courseId: String): List<LearningAttempt> =
        attemptsById.values.filter { it.courseId == courseId }.sortedBy(LearningAttempt::occurredAt)

    override suspend fun saveMastery(mastery: ConceptMastery) {
        masteryByConceptId[mastery.conceptId] = mastery
    }

    override suspend fun getMastery(courseId: String): List<ConceptMastery> {
        val conceptIds = contentById[courseId]?.concepts?.mapTo(mutableSetOf()) { it.id }
            ?: emptySet()
        return masteryByConceptId.values.filter { it.conceptId in conceptIds }
    }

    override suspend fun deleteCourse(courseId: String) {
        val conceptIds = contentById.remove(courseId)?.concepts
            ?.mapTo(mutableSetOf()) { it.id }
            .orEmpty()
        lessonsById.entries.removeAll { it.value.courseId == courseId }
        publishLessons()
        attemptsById.entries.removeAll { it.value.courseId == courseId }
        masteryByConceptId.keys.removeAll(conceptIds)
        mutableCourses.value = contentById.values.map(LearningCourseContent::course)
    }

    private fun validatedLessons(courseId: String): List<LessonPackage> =
        lessonsById.values
            .filter { it.courseId == courseId && it.status == LessonPackageStatus.VALIDATED }
            .sortedWith(compareBy(LessonPackage::generatedAt, LessonPackage::id))

    private fun publishLessons() {
        mutableLessons.value = lessonsById.values.toList()
    }
}

class InMemoryEmergencyRepository(
    initialHistory: List<EmergencyEvent> = emptyList(),
) : EmergencyRepository {
    private val mutableHistory = MutableStateFlow(initialHistory)
    private val mutableState = MutableStateFlow(
        EmergencyState(initialHistory.firstOrNull { it.deactivatedAt == null }),
    )

    override val state: StateFlow<EmergencyState> = mutableState
    override val history: Flow<List<EmergencyEvent>> = mutableHistory

    override suspend fun activate(event: EmergencyEvent) {
        mutableHistory.value = mutableHistory.value.filterNot { it.id == event.id } + event
        mutableState.value = EmergencyState(event)
    }

    override suspend fun deactivate(event: EmergencyEvent) {
        mutableHistory.value = mutableHistory.value.map { stored ->
            if (stored.id == event.id) event else stored
        }
        mutableState.value = EmergencyState()
    }

    override suspend fun deleteHistory() {
        val activeEvent = mutableState.value.activeEvent
        mutableHistory.value = listOfNotNull(activeEvent)
    }
}
