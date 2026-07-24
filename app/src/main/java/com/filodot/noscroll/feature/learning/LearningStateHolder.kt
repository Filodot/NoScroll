package com.filodot.noscroll.feature.learning

import com.filodot.noscroll.core.contracts.LearningRepository
import com.filodot.noscroll.core.learning.answer.AnswerEvaluation
import com.filodot.noscroll.core.learning.answer.LearningAnswer
import com.filodot.noscroll.core.learning.answer.LocalLearningAnswerChecker
import com.filodot.noscroll.core.learning.content.StaticLearningCatalog
import com.filodot.noscroll.core.learning.model.ActivityContent
import com.filodot.noscroll.core.learning.model.AttemptResult
import com.filodot.noscroll.core.learning.model.ConceptMastery
import com.filodot.noscroll.core.learning.model.EvidenceSelectionContent
import com.filodot.noscroll.core.learning.model.FillBlankContent
import com.filodot.noscroll.core.learning.model.LearningActivity
import com.filodot.noscroll.core.learning.model.LearningAttempt
import com.filodot.noscroll.core.learning.model.LearningCourse
import com.filodot.noscroll.core.learning.model.LearningCourseContent
import com.filodot.noscroll.core.learning.model.LessonPackage
import com.filodot.noscroll.core.learning.model.MatchingContent
import com.filodot.noscroll.core.learning.model.MultipleChoiceContent
import com.filodot.noscroll.core.learning.model.OrderingContent
import com.filodot.noscroll.core.learning.model.ScenarioContent
import com.filodot.noscroll.core.learning.model.SelfConfidence
import com.filodot.noscroll.core.learning.model.SingleChoiceContent
import com.filodot.noscroll.core.learning.model.TrueFalseContent
import com.filodot.noscroll.core.learning.progress.MasteryPolicy
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LearningPane {
    COURSES,
    COURSE,
    LESSON,
    COMPLETED,
}

enum class LearningAnswerStatus {
    UNCHECKED,
    INCORRECT,
    CORRECT,
    REQUIRES_EXTERNAL,
}

data class LearningCourseCardUi(
    val id: String,
    val title: String,
    val description: String,
    val masteryPercent: Int,
    val readyLessons: Int,
    val aiKnowledgeOnly: Boolean,
)

data class LearningUiState(
    val loading: Boolean = true,
    val pane: LearningPane = LearningPane.COURSES,
    val courses: List<LearningCourseCardUi> = emptyList(),
    val selectedCourse: LearningCourseContent? = null,
    val selectedCourseMasteryPercent: Int = 0,
    val readyLessons: Int = 0,
    val lesson: LessonPackage? = null,
    val activityIndex: Int = 0,
    val selectedOptionIds: Set<String> = emptySet(),
    val orderedItemIds: List<String> = emptyList(),
    val textAnswer: String = "",
    val booleanAnswer: Boolean? = null,
    val answerStatus: LearningAnswerStatus = LearningAnswerStatus.UNCHECKED,
    val replacedActivityIds: Set<String> = emptySet(),
    val message: String? = null,
) {
    val currentActivity: LearningActivity?
        get() = lesson?.activities?.getOrNull(activityIndex)
}

sealed interface LearningAction {
    data object CreateDemoCourse : LearningAction
    data class OpenCourse(val courseId: String) : LearningAction
    data object BackToCourses : LearningAction
    data object StartLesson : LearningAction
    data object BackToCourse : LearningAction
    data class SelectOption(val optionId: String, val multiple: Boolean) : LearningAction
    data class MoveOrderedItem(val itemId: String, val direction: Int) : LearningAction
    data class SetTextAnswer(val value: String) : LearningAction
    data class SetBooleanAnswer(val value: Boolean) : LearningAction
    data object CheckAnswer : LearningAction
    data object ContinueLesson : LearningAction
    data object ReplaceSuspicious : LearningAction
    data object DismissMessage : LearningAction
}

class LearningStateHolder(
    private val repository: LearningRepository,
    private val scope: CoroutineScope,
    private val answerChecker: LocalLearningAnswerChecker = LocalLearningAnswerChecker(),
    private val masteryPolicy: MasteryPolicy = MasteryPolicy(),
    private val now: () -> Instant = Instant::now,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutableState = MutableStateFlow(LearningUiState())
    val state: StateFlow<LearningUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            repository.courses.collectLatest { courses ->
                val cards = courses.map { course -> course.toCard() }
                mutableState.update { current ->
                    current.copy(
                        loading = false,
                        courses = cards,
                        pane = if (
                            current.selectedCourse != null &&
                            cards.none { it.id == current.selectedCourse.course.id }
                        ) {
                            LearningPane.COURSES
                        } else {
                            current.pane
                        },
                    )
                }
            }
        }
    }

    fun dispatch(action: LearningAction) {
        when (action) {
            LearningAction.CreateDemoCourse -> scope.launch { createDemoCourse() }
            is LearningAction.OpenCourse -> scope.launch { openCourse(action.courseId) }
            LearningAction.BackToCourses -> mutableState.update {
                it.copy(
                    pane = LearningPane.COURSES,
                    selectedCourse = null,
                    lesson = null,
                    message = null,
                )
            }

            LearningAction.StartLesson -> scope.launch { startLesson() }
            LearningAction.BackToCourse -> mutableState.update {
                it.copy(pane = LearningPane.COURSE, lesson = null, message = null)
            }

            is LearningAction.SelectOption -> selectOption(action)
            is LearningAction.MoveOrderedItem -> moveOrderedItem(action)
            is LearningAction.SetTextAnswer -> mutableState.update {
                it.copy(
                    textAnswer = action.value.take(MAX_TEXT_ANSWER_LENGTH),
                    answerStatus = LearningAnswerStatus.UNCHECKED,
                )
            }

            is LearningAction.SetBooleanAnswer -> mutableState.update {
                it.copy(
                    booleanAnswer = action.value,
                    answerStatus = LearningAnswerStatus.UNCHECKED,
                )
            }

            LearningAction.CheckAnswer -> scope.launch { checkAnswer() }
            LearningAction.ContinueLesson -> scope.launch { continueLesson() }
            LearningAction.ReplaceSuspicious -> scope.launch { replaceSuspicious() }
            LearningAction.DismissMessage -> mutableState.update { it.copy(message = null) }
        }
    }

    private suspend fun LearningCourse.toCard(): LearningCourseCardUi {
        val content = repository.getCourseContent(id)
        val mastery = repository.getMastery(id)
        return LearningCourseCardUi(
            id = id,
            title = title,
            description = description,
            masteryPercent = masteryPercent(content?.concepts.orEmpty(), mastery),
            readyLessons = repository.observeValidatedLessonCount(id).first(),
            aiKnowledgeOnly = groundingMode ==
                com.filodot.noscroll.core.learning.model.GroundingMode.AI_KNOWLEDGE,
        )
    }

    private suspend fun createDemoCourse() {
        mutableState.update { it.copy(loading = true, message = null) }
        val content = demoCourseContent()
        repository.saveCourseContent(content)
        repository.saveLesson(StaticLearningCatalog.firstLesson)
        openCourse(content.course.id)
    }

    private suspend fun openCourse(courseId: String) {
        val content = repository.getCourseContent(courseId)
        if (content == null) {
            mutableState.update {
                it.copy(loading = false, pane = LearningPane.COURSES, message = "Курс не найден")
            }
            return
        }
        val mastery = repository.getMastery(courseId)
        mutableState.update {
            it.copy(
                loading = false,
                pane = LearningPane.COURSE,
                selectedCourse = content,
                selectedCourseMasteryPercent = masteryPercent(content.concepts, mastery),
                readyLessons = repository.observeValidatedLessonCount(courseId).first(),
                lesson = null,
                message = null,
            )
        }
    }

    private suspend fun startLesson() {
        val courseId = mutableState.value.selectedCourse?.course?.id ?: return
        val lesson = repository.peekNextLesson(courseId)
        if (lesson == null) {
            mutableState.update {
                it.copy(message = "Нет готового офлайн-урока. Позже здесь запустится генерация.")
            }
            return
        }
        mutableState.update {
            initialActivityState(
                state = it.copy(
                    pane = LearningPane.LESSON,
                    lesson = lesson,
                    activityIndex = 0,
                    replacedActivityIds = emptySet(),
                    message = null,
                ),
                activity = lesson.activities.firstOrNull(),
            )
        }
    }

    private fun selectOption(action: LearningAction.SelectOption) {
        mutableState.update { state ->
            val selected = if (action.multiple) {
                if (action.optionId in state.selectedOptionIds) {
                    state.selectedOptionIds - action.optionId
                } else {
                    state.selectedOptionIds + action.optionId
                }
            } else {
                setOf(action.optionId)
            }
            state.copy(
                selectedOptionIds = selected,
                answerStatus = LearningAnswerStatus.UNCHECKED,
            )
        }
    }

    private fun moveOrderedItem(action: LearningAction.MoveOrderedItem) {
        mutableState.update { state ->
            val index = state.orderedItemIds.indexOf(action.itemId)
            if (index < 0) return@update state
            val target = (index + action.direction).coerceIn(0, state.orderedItemIds.lastIndex)
            if (target == index) return@update state
            val updated = state.orderedItemIds.toMutableList()
            val item = updated.removeAt(index)
            updated.add(target, item)
            state.copy(
                orderedItemIds = updated,
                answerStatus = LearningAnswerStatus.UNCHECKED,
            )
        }
    }

    private suspend fun checkAnswer() {
        val state = mutableState.value
        if (state.answerStatus == LearningAnswerStatus.CORRECT) return
        val activity = state.currentActivity ?: return
        val answer = state.answerFor(activity.content) ?: return
        when (answerChecker.evaluate(activity.content, answer)) {
            AnswerEvaluation.CORRECT -> {
                recordAttempt(activity, AttemptResult.CORRECT)
                mutableState.update {
                    it.copy(answerStatus = LearningAnswerStatus.CORRECT, message = null)
                }
            }

            AnswerEvaluation.INCORRECT -> {
                recordAttempt(activity, AttemptResult.INCORRECT)
                mutableState.update {
                    it.copy(answerStatus = LearningAnswerStatus.INCORRECT, message = null)
                }
            }

            AnswerEvaluation.REQUIRES_EXTERNAL_EVALUATION -> mutableState.update {
                it.copy(
                    answerStatus = LearningAnswerStatus.REQUIRES_EXTERNAL,
                    message = "Для этого формата нужна безопасная AI-проверка или code sandbox.",
                )
            }

            AnswerEvaluation.INCOMPATIBLE_ANSWER -> mutableState.update {
                it.copy(message = "Ответ заполнен не полностью")
            }
        }
    }

    private suspend fun recordAttempt(
        activity: LearningActivity,
        result: AttemptResult,
    ) {
        val state = mutableState.value
        val courseId = state.selectedCourse?.course?.id ?: return
        val lessonId = state.lesson?.id ?: return
        val timestamp = now()
        val attempt = LearningAttempt(
            id = idGenerator(),
            courseId = courseId,
            lessonId = lessonId,
            activityId = activity.id,
            conceptIds = activity.conceptIds,
            activityKind = activity.content.kind,
            result = result,
            hintsUsed = 0,
            durationSeconds = activity.estimatedSeconds,
            confidence = SelfConfidence.MEDIUM,
            occurredAt = timestamp,
            localDate = timestamp.atZone(zoneId).toLocalDate(),
        )
        repository.saveAttempt(attempt)
        if (result == AttemptResult.REPLACED_AS_SUSPICIOUS) return

        val mastery = repository.getMastery(courseId).associateBy(ConceptMastery::conceptId)
        activity.conceptIds.forEach { conceptId ->
            repository.saveMastery(
                masteryPolicy.update(
                    current = mastery[conceptId] ?: ConceptMastery(conceptId = conceptId),
                    attempt = attempt,
                ),
            )
        }
    }

    private suspend fun continueLesson() {
        val state = mutableState.value
        if (state.answerStatus != LearningAnswerStatus.CORRECT) return
        val lesson = state.lesson ?: return
        val nextIndex = (state.activityIndex + 1 until lesson.activities.size)
            .firstOrNull { lesson.activities[it].id !in state.replacedActivityIds }
        if (nextIndex != null) {
            mutableState.update {
                initialActivityState(
                    it.copy(activityIndex = nextIndex, message = null),
                    lesson.activities[nextIndex],
                )
            }
            return
        }
        val courseId = state.selectedCourse?.course?.id ?: return
        repository.takeNextLesson(courseId)
        val content = repository.getCourseContent(courseId)
        val mastery = repository.getMastery(courseId)
        mutableState.update {
            it.copy(
                pane = LearningPane.COMPLETED,
                selectedCourse = content,
                selectedCourseMasteryPercent = masteryPercent(content?.concepts.orEmpty(), mastery),
                readyLessons = repository.observeValidatedLessonCount(courseId).first(),
                lesson = null,
                message = null,
            )
        }
    }

    private suspend fun replaceSuspicious() {
        val state = mutableState.value
        val activity = state.currentActivity ?: return
        recordAttempt(activity, AttemptResult.REPLACED_AS_SUSPICIOUS)
        val lesson = state.lesson ?: return
        val replaced = state.replacedActivityIds + activity.id
        val replacementIndex = lesson.activities.indices.firstOrNull {
            lesson.activities[it].id !in replaced
        }
        if (replacementIndex == null) {
            mutableState.update {
                it.copy(
                    replacedActivityIds = replaced,
                    message = "Резервные задания закончились. Урок не списан и прогресс не изменён.",
                )
            }
            return
        }
        mutableState.update {
            initialActivityState(
                it.copy(
                    activityIndex = replacementIndex,
                    replacedActivityIds = replaced,
                    message = "Подозрительное задание заменено без штрафа",
                ),
                lesson.activities[replacementIndex],
            )
        }
    }

    private fun LearningUiState.answerFor(content: ActivityContent): LearningAnswer? = when (content) {
        is SingleChoiceContent,
        is MultipleChoiceContent,
        is EvidenceSelectionContent,
        is ScenarioContent,
        -> selectedOptionIds.takeIf { it.isNotEmpty() }?.let { LearningAnswer.Choices(it) }

        is TrueFalseContent -> booleanAnswer?.let { LearningAnswer.BooleanValue(it) }
        is OrderingContent -> orderedItemIds
            .takeIf { it.size == content.items.size }
            ?.let { LearningAnswer.Ordered(it) }

        is MatchingContent -> null
        is FillBlankContent,
        is com.filodot.noscroll.core.learning.model.ShortAnswerContent,
        is com.filodot.noscroll.core.learning.model.NumericAnswerContent,
        is com.filodot.noscroll.core.learning.model.CodeOutputContent,
        is com.filodot.noscroll.core.learning.model.CodeCompletionContent,
        is com.filodot.noscroll.core.learning.model.CodeFixContent,
        is com.filodot.noscroll.core.learning.model.MiniCodeContent,
        is com.filodot.noscroll.core.learning.model.TeachBackContent,
        -> textAnswer.takeIf(String::isNotBlank)?.let { LearningAnswer.Text(it) }

        is com.filodot.noscroll.core.learning.model.FlashcardContent ->
            LearningAnswer.ConfirmedRecall
    }

    private fun initialActivityState(
        state: LearningUiState,
        activity: LearningActivity?,
    ): LearningUiState = state.copy(
        selectedOptionIds = emptySet(),
        orderedItemIds = (activity?.content as? OrderingContent)?.items?.map { it.id }.orEmpty(),
        textAnswer = "",
        booleanAnswer = null,
        answerStatus = LearningAnswerStatus.UNCHECKED,
    )

    private fun masteryPercent(
        concepts: List<com.filodot.noscroll.core.learning.model.LearningConcept>,
        mastery: List<ConceptMastery>,
    ): Int {
        if (concepts.isEmpty()) return 0
        val scoreById = mastery.associateBy(ConceptMastery::conceptId)
        return concepts.sumOf { scoreById[it.id]?.score ?: 0 } / concepts.size
    }
}

private fun demoCourseContent(): LearningCourseContent = LearningCourseContent(
    course = StaticLearningCatalog.pythonCourse,
    sources = emptyList(),
    curriculumNodes = listOf(StaticLearningCatalog.firstTopic),
    concepts = listOf(
        StaticLearningCatalog.variablesConcept,
        StaticLearningCatalog.expressionsConcept,
    ),
)

private const val MAX_TEXT_ANSWER_LENGTH = 4_000
