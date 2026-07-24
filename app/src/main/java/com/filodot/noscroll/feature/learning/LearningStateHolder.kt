package com.filodot.noscroll.feature.learning

import com.filodot.noscroll.core.contracts.LearningRepository
import com.filodot.noscroll.core.learning.answer.AnswerEvaluation
import com.filodot.noscroll.core.learning.answer.LearningAnswer
import com.filodot.noscroll.core.learning.answer.LocalLearningAnswerChecker
import com.filodot.noscroll.core.learning.ai.AiCredentialRepository
import com.filodot.noscroll.core.learning.ai.AiProviderId
import com.filodot.noscroll.core.learning.ai.AiProviderSettings
import com.filodot.noscroll.core.learning.content.StaticLearningCatalog
import com.filodot.noscroll.core.learning.code.CodeEvaluationStatus
import com.filodot.noscroll.core.learning.code.CodeExerciseEvaluator
import com.filodot.noscroll.core.learning.generation.CurriculumGenerationException
import com.filodot.noscroll.core.learning.generation.CurriculumGenerator
import com.filodot.noscroll.core.learning.generation.LessonGenerationException
import com.filodot.noscroll.core.learning.generation.LessonGenerator
import com.filodot.noscroll.core.learning.importing.LearningMaterialGateway
import com.filodot.noscroll.core.learning.importing.LearningMaterialImportException
import com.filodot.noscroll.core.learning.importing.LearningMaterialProcessor
import com.filodot.noscroll.core.learning.model.ActivityContent
import com.filodot.noscroll.core.learning.model.AttemptResult
import com.filodot.noscroll.core.learning.model.ConceptMastery
import com.filodot.noscroll.core.learning.model.CourseOrigin
import com.filodot.noscroll.core.learning.model.CourseStatus
import com.filodot.noscroll.core.learning.model.CurriculumNode
import com.filodot.noscroll.core.learning.model.CurriculumNodeType
import com.filodot.noscroll.core.learning.model.EvidenceSelectionContent
import com.filodot.noscroll.core.learning.model.FillBlankContent
import com.filodot.noscroll.core.learning.model.LearningActivity
import com.filodot.noscroll.core.learning.model.LearningAttempt
import com.filodot.noscroll.core.learning.model.LearningCourse
import com.filodot.noscroll.core.learning.model.LearningCourseContent
import com.filodot.noscroll.core.learning.model.LearningConcept
import com.filodot.noscroll.core.learning.model.LearningSource
import com.filodot.noscroll.core.learning.model.LearningSourceType
import com.filodot.noscroll.core.learning.model.GroundingMode
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LearningPane {
    COURSES,
    CREATE,
    AI_SETTINGS,
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
    val createTitle: String = "",
    val createTopic: String = "",
    val importingMaterial: Boolean = false,
    val aiProviders: List<AiProviderSettings> = emptyList(),
    val editingAiProvider: AiProviderId? = null,
    val aiApiKeyDraft: String = "",
    val aiModelDraft: String = "",
    val generatingPlan: Boolean = false,
    val planDirty: Boolean = false,
    val lastPlanProviderLabel: String? = null,
    val generatingLesson: Boolean = false,
    val checkingAnswer: Boolean = false,
    val deleteCourseConfirmation: Boolean = false,
    val deletingCourse: Boolean = false,
    val selectedCourse: LearningCourseContent? = null,
    val selectedCourseMasteryPercent: Int = 0,
    val readyLessons: Int = 0,
    val lesson: LessonPackage? = null,
    val activityIndex: Int = 0,
    val selectedOptionIds: Set<String> = emptySet(),
    val orderedItemIds: List<String> = emptyList(),
    val matchingRightIdByLeftId: Map<String, String> = emptyMap(),
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
    data object StartCreateCourse : LearningAction
    data object CancelCreateCourse : LearningAction
    data class SetCreateTitle(val value: String) : LearningAction
    data class SetCreateTopic(val value: String) : LearningAction
    data object CreateTopicCourse : LearningAction
    data class ImportMaterial(val reference: String) : LearningAction
    data object OpenAiSettings : LearningAction
    data object CloseAiSettings : LearningAction
    data class EditAiProvider(val providerId: AiProviderId) : LearningAction
    data class SetAiApiKeyDraft(val value: String) : LearningAction
    data class SetAiModelDraft(val value: String) : LearningAction
    data object SaveAiProvider : LearningAction
    data class ClearAiProviderKey(val providerId: AiProviderId) : LearningAction
    data class ToggleAiProvider(val providerId: AiProviderId, val enabled: Boolean) : LearningAction
    data object GeneratePlan : LearningAction
    data object BeginPlanEdit : LearningAction
    data class SetPlanNodeTitle(val nodeId: String, val value: String) : LearningAction
    data class SetPlanNodeDescription(val nodeId: String, val value: String) : LearningAction
    data class MovePlanNode(val nodeId: String, val direction: Int) : LearningAction
    data class DeletePlanNode(val nodeId: String) : LearningAction
    data object AddPlanNode : LearningAction
    data object ConfirmPlan : LearningAction
    data object GenerateNextLesson : LearningAction
    data object RequestDeleteCourse : LearningAction
    data object CancelDeleteCourse : LearningAction
    data object ConfirmDeleteCourse : LearningAction
    data object CreateDemoCourse : LearningAction
    data class OpenCourse(val courseId: String) : LearningAction
    data object BackToCourses : LearningAction
    data object StartLesson : LearningAction
    data object BackToCourse : LearningAction
    data class SelectOption(val optionId: String, val multiple: Boolean) : LearningAction
    data class MoveOrderedItem(val itemId: String, val direction: Int) : LearningAction
    data class SetMatch(val leftId: String, val rightId: String) : LearningAction
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
    private val materialGateway: LearningMaterialGateway? = null,
    private val aiCredentials: AiCredentialRepository? = null,
    private val curriculumGenerator: CurriculumGenerator? = null,
    private val lessonGenerator: LessonGenerator? = null,
    private val codeEvaluator: CodeExerciseEvaluator? = null,
    private val materialProcessor: LearningMaterialProcessor = LearningMaterialProcessor(),
    private val answerChecker: LocalLearningAnswerChecker = LocalLearningAnswerChecker(),
    private val masteryPolicy: MasteryPolicy = MasteryPolicy(),
    private val now: () -> Instant = Instant::now,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutableState = MutableStateFlow(LearningUiState())
    val state: StateFlow<LearningUiState> = mutableState.asStateFlow()
    private var planSaveJob: Job? = null

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
        aiCredentials?.let { credentials ->
            scope.launch {
                credentials.settings.collectLatest { providers ->
                    mutableState.update { it.copy(aiProviders = providers) }
                }
            }
        }
    }

    fun dispatch(action: LearningAction) {
        when (action) {
            LearningAction.StartCreateCourse -> mutableState.update {
                it.copy(
                    pane = LearningPane.CREATE,
                    createTitle = "",
                    createTopic = "",
                    importingMaterial = false,
                    message = null,
                )
            }

            LearningAction.CancelCreateCourse -> mutableState.update {
                it.copy(
                    pane = LearningPane.COURSES,
                    importingMaterial = false,
                    message = null,
                )
            }

            is LearningAction.SetCreateTitle -> mutableState.update {
                it.copy(createTitle = action.value.take(MAX_COURSE_TITLE_LENGTH))
            }

            is LearningAction.SetCreateTopic -> mutableState.update {
                it.copy(createTopic = action.value.take(MAX_TOPIC_LENGTH))
            }

            LearningAction.CreateTopicCourse -> scope.launch { createTopicCourse() }
            is LearningAction.ImportMaterial -> scope.launch { importMaterial(action.reference) }
            LearningAction.OpenAiSettings -> mutableState.update {
                it.copy(
                    pane = LearningPane.AI_SETTINGS,
                    editingAiProvider = null,
                    aiApiKeyDraft = "",
                    aiModelDraft = "",
                    message = null,
                )
            }

            LearningAction.CloseAiSettings -> mutableState.update {
                it.copy(
                    pane = LearningPane.CREATE,
                    editingAiProvider = null,
                    aiApiKeyDraft = "",
                    aiModelDraft = "",
                    message = null,
                )
            }

            is LearningAction.EditAiProvider -> editAiProvider(action.providerId)
            is LearningAction.SetAiApiKeyDraft -> mutableState.update {
                it.copy(aiApiKeyDraft = action.value.take(MAX_API_KEY_LENGTH))
            }

            is LearningAction.SetAiModelDraft -> mutableState.update {
                it.copy(aiModelDraft = action.value.take(MAX_MODEL_ID_LENGTH))
            }

            LearningAction.SaveAiProvider -> scope.launch { saveAiProvider() }
            is LearningAction.ClearAiProviderKey -> scope.launch {
                aiCredentials?.clearApiKey(action.providerId)
                mutableState.update {
                    it.copy(
                        editingAiProvider = null,
                        aiApiKeyDraft = "",
                        message = "API-ключ удалён",
                    )
                }
            }

            is LearningAction.ToggleAiProvider -> scope.launch {
                aiCredentials?.setEnabled(action.providerId, action.enabled)
            }

            LearningAction.GeneratePlan -> scope.launch { generatePlan() }
            LearningAction.BeginPlanEdit -> scope.launch { beginPlanEdit() }
            is LearningAction.SetPlanNodeTitle -> {
                updatePlanNode(action.nodeId) {
                    it.copy(title = action.value.take(MAX_PLAN_TITLE_LENGTH))
                }
            }

            is LearningAction.SetPlanNodeDescription -> {
                updatePlanNode(action.nodeId) {
                    it.copy(description = action.value.take(MAX_PLAN_DESCRIPTION_LENGTH))
                }
            }

            is LearningAction.MovePlanNode -> scope.launch {
                movePlanNode(action.nodeId, action.direction)
            }

            is LearningAction.DeletePlanNode -> scope.launch { deletePlanNode(action.nodeId) }
            LearningAction.AddPlanNode -> scope.launch { addPlanNode() }
            LearningAction.ConfirmPlan -> scope.launch { confirmPlan() }
            LearningAction.GenerateNextLesson -> scope.launch { generateNextLesson() }
            LearningAction.RequestDeleteCourse -> mutableState.update {
                it.copy(deleteCourseConfirmation = true, message = null)
            }

            LearningAction.CancelDeleteCourse -> mutableState.update {
                it.copy(deleteCourseConfirmation = false)
            }

            LearningAction.ConfirmDeleteCourse -> scope.launch { deleteSelectedCourse() }
            LearningAction.CreateDemoCourse -> scope.launch { createDemoCourse() }
            is LearningAction.OpenCourse -> scope.launch { openCourse(action.courseId) }
            LearningAction.BackToCourses -> mutableState.update {
                it.copy(
                    pane = LearningPane.COURSES,
                    selectedCourse = null,
                    lesson = null,
                    planDirty = false,
                    lastPlanProviderLabel = null,
                    deleteCourseConfirmation = false,
                    message = null,
                )
            }

            LearningAction.StartLesson -> scope.launch { startLesson() }
            LearningAction.BackToCourse -> mutableState.update {
                it.copy(pane = LearningPane.COURSE, lesson = null, message = null)
            }

            is LearningAction.SelectOption -> selectOption(action)
            is LearningAction.MoveOrderedItem -> moveOrderedItem(action)
            is LearningAction.SetMatch -> mutableState.update {
                it.copy(
                    matchingRightIdByLeftId =
                        it.matchingRightIdByLeftId + (action.leftId to action.rightId),
                    answerStatus = LearningAnswerStatus.UNCHECKED,
                )
            }
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

    private fun editAiProvider(providerId: AiProviderId) {
        val settings = mutableState.value.aiProviders.firstOrNull { it.id == providerId } ?: return
        mutableState.update {
            it.copy(
                editingAiProvider = providerId,
                aiApiKeyDraft = "",
                aiModelDraft = settings.modelId,
                message = null,
            )
        }
    }

    private suspend fun saveAiProvider() {
        val credentials = aiCredentials ?: return
        val state = mutableState.value
        val providerId = state.editingAiProvider ?: return
        try {
            credentials.setModel(providerId, state.aiModelDraft)
            if (state.aiApiKeyDraft.isNotBlank()) {
                credentials.saveApiKey(providerId, state.aiApiKeyDraft)
            }
            mutableState.update {
                it.copy(
                    editingAiProvider = null,
                    aiApiKeyDraft = "",
                    aiModelDraft = "",
                    message = "Настройки ${providerId.displayName()} сохранены",
                )
            }
        } catch (error: IllegalArgumentException) {
            mutableState.update {
                it.copy(message = error.message ?: "Проверьте модель и API-ключ")
            }
        }
    }

    private suspend fun generatePlan() {
        val generator = curriculumGenerator
        val content = mutableState.value.selectedCourse
        if (generator == null || content == null) {
            mutableState.update {
                it.copy(message = "Генератор учебной программы пока недоступен")
            }
            return
        }
        if (mutableState.value.aiProviders.none { it.enabled && it.hasApiKey }) {
            mutableState.update {
                it.copy(message = "Добавьте API-ключ хотя бы одного AI-провайдера")
            }
            return
        }
        mutableState.update { it.copy(generatingPlan = true, message = null) }
        try {
            val generated = generator.generate(content)
            val updated = content.copy(
                course = content.course.copy(
                    status = CourseStatus.DRAFT,
                    updatedAt = now(),
                ),
                curriculumNodes = generated.nodes,
                concepts = generated.concepts,
            )
            repository.saveCourseContent(updated)
            openCourse(updated.course.id)
            mutableState.update {
                it.copy(
                    generatingPlan = false,
                    planDirty = true,
                    lastPlanProviderLabel = "${generated.providerId.displayName()} · " +
                        "${generated.modelId} · качество ${generated.qualityScore}/100",
                    message = if (generated.attempts > 1) {
                        "План принят после ${generated.attempts} попыток проверки"
                    } else {
                        "Черновик плана готов. Проверьте и подтвердите его."
                    },
                )
            }
        } catch (error: CurriculumGenerationException) {
            mutableState.update {
                it.copy(
                    generatingPlan = false,
                    message = error.issues.firstOrNull()
                        ?: error.message
                        ?: "Не удалось получить качественный план",
                )
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.update {
                it.copy(
                    generatingPlan = false,
                    message = error.message?.take(400) ?: "Провайдеры ИИ недоступны",
                )
            }
        }
    }

    private suspend fun beginPlanEdit() {
        val content = mutableState.value.selectedCourse ?: return
        val updated = content.copy(
            course = content.course.copy(status = CourseStatus.DRAFT, updatedAt = now()),
        )
        repository.saveCourseContent(updated)
        openCourse(updated.course.id)
        mutableState.update { it.copy(planDirty = true) }
    }

    private fun updatePlanNode(
        nodeId: String,
        transform: (CurriculumNode) -> CurriculumNode,
    ) {
        val content = mutableState.value.selectedCourse ?: return
        if (content.course.status != CourseStatus.DRAFT) return
        val updated = content.copy(
            course = content.course.copy(updatedAt = now()),
            curriculumNodes = content.curriculumNodes.map {
                if (it.id == nodeId) transform(it) else it
            },
        )
        mutableState.update { it.copy(selectedCourse = updated, planDirty = true) }
        planSaveJob?.cancel()
        planSaveJob = scope.launch {
            delay(PLAN_SAVE_DEBOUNCE_MILLIS)
            repository.saveCourseContent(updated)
        }
    }

    private suspend fun movePlanNode(nodeId: String, direction: Int) {
        val content = mutableState.value.selectedCourse ?: return
        if (content.course.status != CourseStatus.DRAFT) return
        val nodes = content.curriculumNodes.sortedBy(CurriculumNode::position).toMutableList()
        val from = nodes.indexOfFirst { it.id == nodeId }
        if (from < 0) return
        val to = (from + direction).coerceIn(0, nodes.lastIndex)
        if (from == to) return
        val moved = nodes.removeAt(from)
        nodes.add(to, moved)
        saveEditedPlan(
            content.copy(
                curriculumNodes = nodes.mapIndexed { index, node -> node.copy(position = index) },
            ),
        )
    }

    private suspend fun deletePlanNode(nodeId: String) {
        val content = mutableState.value.selectedCourse ?: return
        if (content.course.status != CourseStatus.DRAFT) return
        val removedConceptIds = content.curriculumNodes.firstOrNull { it.id == nodeId }
            ?.conceptIds
            ?.toSet()
            .orEmpty()
        saveEditedPlan(
            content.copy(
                curriculumNodes = content.curriculumNodes
                    .filterNot { it.id == nodeId }
                    .mapIndexed { index, node -> node.copy(position = index) },
                concepts = content.concepts
                    .filterNot { it.id in removedConceptIds }
                    .map { it.copy(prerequisiteIds = it.prerequisiteIds - removedConceptIds) },
            ),
        )
    }

    private suspend fun addPlanNode() {
        val content = mutableState.value.selectedCourse ?: return
        if (content.course.status != CourseStatus.DRAFT) return
        val conceptId = idGenerator()
        val position = content.curriculumNodes.size
        saveEditedPlan(
            content.copy(
                curriculumNodes = content.curriculumNodes + CurriculumNode(
                    id = idGenerator(),
                    courseId = content.course.id,
                    parentId = null,
                    type = CurriculumNodeType.TOPIC,
                    title = "Новая тема",
                    description = "Опишите, чему должен научиться пользователь.",
                    position = position,
                    estimatedMinutes = 15,
                    conceptIds = listOf(conceptId),
                ),
                concepts = content.concepts + LearningConcept(
                    id = conceptId,
                    courseId = content.course.id,
                    title = "Пользовательская тема ${position + 1}",
                    summary = "Понятие добавлено пользователем при редактировании плана.",
                    position = content.concepts.size,
                ),
            ),
        )
    }

    private suspend fun saveEditedPlan(content: LearningCourseContent) {
        planSaveJob?.cancel()
        val updated = content.copy(course = content.course.copy(updatedAt = now()))
        repository.saveCourseContent(updated)
        openCourse(updated.course.id)
        mutableState.update { it.copy(planDirty = true) }
    }

    private suspend fun confirmPlan() {
        planSaveJob?.cancel()
        val content = mutableState.value.selectedCourse ?: return
        if (content.curriculumNodes.size < 2 ||
            content.curriculumNodes.any {
                it.title.trim().length < 3 || it.description.trim().length < 10
            }
        ) {
            mutableState.update {
                it.copy(message = "Оставьте минимум две заполненные темы с описаниями")
            }
            return
        }
        val updated = content.copy(
            course = content.course.copy(
                status = CourseStatus.READY,
                planVersion = content.course.planVersion + 1,
                updatedAt = now(),
            ),
            curriculumNodes = content.curriculumNodes.map {
                it.copy(title = it.title.trim(), description = it.description.trim())
            },
        )
        repository.saveCourseContent(updated)
        openCourse(updated.course.id)
        mutableState.update {
            it.copy(planDirty = false, message = "План подтверждён и готов к созданию уроков")
        }
    }

    private suspend fun generateNextLesson() {
        val generator = lessonGenerator
        val content = mutableState.value.selectedCourse
        if (generator == null || content == null) {
            mutableState.update { it.copy(message = "Генератор уроков пока недоступен") }
            return
        }
        if (content.course.status != CourseStatus.READY) {
            mutableState.update { it.copy(message = "Сначала подтвердите план курса") }
            return
        }
        if (mutableState.value.readyLessons > 0) {
            mutableState.update { it.copy(message = "Следующий урок уже готов офлайн") }
            return
        }
        if (mutableState.value.generatingLesson) return
        if (mutableState.value.aiProviders.none { it.enabled && it.hasApiKey }) {
            mutableState.update { it.copy(message = "Добавьте API-ключ AI-провайдера") }
            return
        }
        mutableState.update { it.copy(generatingLesson = true, message = null) }
        try {
            val lesson = generator.generate(content, repository.getMastery(content.course.id))
            repository.saveLesson(lesson)
            openCourse(content.course.id)
            val generation = lesson.activities.firstOrNull()?.generation
            mutableState.update {
                it.copy(
                    generatingLesson = false,
                    message = if (generation == null) {
                        "Следующий урок готов офлайн"
                    } else {
                        "Урок готов: ${generation.providerId} · ${generation.modelId}"
                    },
                )
            }
        } catch (error: LessonGenerationException) {
            mutableState.update {
                it.copy(
                    generatingLesson = false,
                    message = error.issues.firstOrNull()
                        ?: error.message
                        ?: "Не удалось создать качественный урок",
                )
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.update {
                it.copy(
                    generatingLesson = false,
                    message = error.message?.take(400) ?: "Не удалось создать урок",
                )
            }
        }
    }

    private suspend fun createTopicCourse() {
        val state = mutableState.value
        val topic = state.createTopic.trim()
        if (topic.length < MIN_TOPIC_LENGTH) {
            mutableState.update { it.copy(message = "Опишите тему хотя бы в нескольких словах") }
            return
        }
        val timestamp = now()
        val courseId = idGenerator()
        val title = state.createTitle.trim().ifBlank { topic.take(60) }
        val source = LearningSource(
            id = idGenerator(),
            courseId = courseId,
            title = topic,
            type = LearningSourceType.TOPIC,
            contentHash = null,
            importedAt = timestamp,
        )
        repository.saveCourseContent(
            LearningCourseContent(
                course = LearningCourse(
                    id = courseId,
                    title = title,
                    description = "Курс по теме: $topic",
                    origin = CourseOrigin.TOPIC,
                    groundingMode = GroundingMode.AI_KNOWLEDGE,
                    status = CourseStatus.DRAFT,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                ),
                sources = listOf(source),
                curriculumNodes = emptyList(),
                concepts = emptyList(),
            ),
        )
        openCourse(courseId)
    }

    private suspend fun deleteSelectedCourse() {
        val courseId = mutableState.value.selectedCourse?.course?.id ?: return
        if (mutableState.value.deletingCourse) return
        planSaveJob?.cancel()
        mutableState.update {
            it.copy(deletingCourse = true, deleteCourseConfirmation = false, message = null)
        }
        try {
            repository.deleteCourse(courseId)
            mutableState.update {
                it.copy(
                    deletingCourse = false,
                    pane = LearningPane.COURSES,
                    selectedCourse = null,
                    lesson = null,
                    readyLessons = 0,
                    selectedCourseMasteryPercent = 0,
                    planDirty = false,
                    message = "Курс и все его локальные материалы удалены",
                )
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.update {
                it.copy(
                    deletingCourse = false,
                    message = error.message?.take(300) ?: "Не удалось удалить курс",
                )
            }
        }
    }

    private suspend fun importMaterial(reference: String) {
        val gateway = materialGateway
        if (gateway == null) {
            mutableState.update { it.copy(message = "Импорт файлов недоступен в этом окружении") }
            return
        }
        mutableState.update { it.copy(importingMaterial = true, message = null) }
        try {
            val document = gateway.read(reference)
            val timestamp = now()
            val courseId = idGenerator()
            val sourceId = idGenerator()
            val prepared = materialProcessor.prepare(
                courseId = courseId,
                sourceId = sourceId,
                document = document,
                importedAt = timestamp,
            )
            val title = mutableState.value.createTitle.trim()
                .ifBlank { document.title.substringBeforeLast('.').ifBlank { document.title } }
            repository.saveCourseContent(
                LearningCourseContent(
                    course = LearningCourse(
                        id = courseId,
                        title = title,
                        description = "${prepared.characterCount} знаков · " +
                            "${prepared.chunks.size} фрагментов",
                        origin = CourseOrigin.MATERIAL,
                        groundingMode = GroundingMode.SOURCE_REQUIRED,
                        status = CourseStatus.DRAFT,
                        createdAt = timestamp,
                        updatedAt = timestamp,
                    ),
                    sources = listOf(prepared.source),
                    sourceChunks = prepared.chunks,
                    curriculumNodes = emptyList(),
                    concepts = emptyList(),
                ),
            )
            openCourse(courseId)
        } catch (error: LearningMaterialImportException) {
            mutableState.update {
                it.copy(importingMaterial = false, message = error.message ?: "Не удалось импортировать")
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.update {
                it.copy(
                    importingMaterial = false,
                    message = "Импорт не завершён. Проверьте файл и попробуйте снова.",
                )
            }
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
                importingMaterial = false,
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
        if (state.answerStatus == LearningAnswerStatus.CORRECT || state.checkingAnswer) return
        val activity = state.currentActivity ?: return
        val answer = state.answerFor(activity.content) ?: return
        mutableState.update { it.copy(checkingAnswer = true, message = null) }
        try {
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

                AnswerEvaluation.REQUIRES_EXTERNAL_EVALUATION ->
                    evaluateExternalFormat(activity, answer)

                AnswerEvaluation.INCOMPATIBLE_ANSWER -> mutableState.update {
                    it.copy(message = "Ответ заполнен не полностью")
                }
            }
        } finally {
            mutableState.update { it.copy(checkingAnswer = false) }
        }
    }

    private suspend fun evaluateExternalFormat(
        activity: LearningActivity,
        answer: LearningAnswer,
    ) {
        val submission = (answer as? LearningAnswer.Text)?.value
        val evaluator = codeEvaluator
        if (submission == null || evaluator == null) {
            mutableState.update {
                it.copy(
                    answerStatus = LearningAnswerStatus.REQUIRES_EXTERNAL,
                    message = "Для этого формата пока нет безопасной локальной проверки.",
                )
            }
            return
        }
        val evaluation = evaluator.evaluate(activity.content, submission)
        when (evaluation.status) {
            CodeEvaluationStatus.CORRECT -> {
                recordAttempt(activity, AttemptResult.CORRECT)
                mutableState.update {
                    it.copy(answerStatus = LearningAnswerStatus.CORRECT, message = null)
                }
            }

            CodeEvaluationStatus.INCORRECT -> {
                recordAttempt(activity, AttemptResult.INCORRECT)
                mutableState.update {
                    it.copy(
                        answerStatus = LearningAnswerStatus.INCORRECT,
                        message = evaluation.message,
                    )
                }
            }

            CodeEvaluationStatus.INVALID_SUBMISSION -> mutableState.update {
                it.copy(
                    answerStatus = LearningAnswerStatus.INCORRECT,
                    message = evaluation.message ?: "Код не удалось выполнить",
                )
            }

            CodeEvaluationStatus.UNSUPPORTED -> mutableState.update {
                it.copy(
                    answerStatus = LearningAnswerStatus.REQUIRES_EXTERNAL,
                    message = evaluation.message ?: "Формат пока не поддерживается локально",
                )
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

        is MatchingContent -> matchingRightIdByLeftId
            .takeIf { matches ->
                matches.keys == content.left.mapTo(mutableSetOf()) { it.id } &&
                    matches.values.all { rightId -> content.right.any { it.id == rightId } }
            }
            ?.let { LearningAnswer.Matches(it) }
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
        matchingRightIdByLeftId = emptyMap(),
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
private const val MAX_COURSE_TITLE_LENGTH = 100
private const val MAX_TOPIC_LENGTH = 2_000
private const val MIN_TOPIC_LENGTH = 8
private const val MAX_API_KEY_LENGTH = 512
private const val MAX_MODEL_ID_LENGTH = 120
private const val MAX_PLAN_TITLE_LENGTH = 100
private const val MAX_PLAN_DESCRIPTION_LENGTH = 500
private const val PLAN_SAVE_DEBOUNCE_MILLIS = 500L

internal fun AiProviderId.displayName(): String = when (this) {
    AiProviderId.GEMINI -> "Google Gemini"
    AiProviderId.GROQ -> "Groq"
    AiProviderId.OPENROUTER -> "OpenRouter"
}
