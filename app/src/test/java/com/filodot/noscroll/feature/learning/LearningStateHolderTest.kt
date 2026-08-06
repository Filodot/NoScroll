package com.filodot.noscroll.feature.learning

import com.filodot.noscroll.core.learning.content.StaticLearningCatalog
import com.filodot.noscroll.core.learning.code.CodeEvaluation
import com.filodot.noscroll.core.learning.code.CodeEvaluationStatus
import com.filodot.noscroll.core.learning.code.CodeExerciseEvaluator
import com.filodot.noscroll.core.learning.ai.AiProviderId
import com.filodot.noscroll.core.learning.generation.CurriculumGenerator
import com.filodot.noscroll.core.learning.generation.GeneratedCurriculum
import com.filodot.noscroll.core.learning.generation.LessonGenerator
import com.filodot.noscroll.core.learning.generation.BufferedLessonGenerator
import com.filodot.noscroll.core.learning.importing.LearningMaterialDocument
import com.filodot.noscroll.core.learning.importing.LearningMaterialGateway
import com.filodot.noscroll.core.learning.importing.MaterialSection
import com.filodot.noscroll.core.learning.model.AttemptResult
import com.filodot.noscroll.core.learning.model.LearningCourseContent
import com.filodot.noscroll.core.learning.model.LearningAttempt
import com.filodot.noscroll.core.learning.model.CourseOrigin
import com.filodot.noscroll.core.learning.model.CourseStatus
import com.filodot.noscroll.core.learning.model.CurriculumNode
import com.filodot.noscroll.core.learning.model.CurriculumNodeType
import com.filodot.noscroll.core.learning.model.GroundingMode
import com.filodot.noscroll.core.learning.model.LearningSourceType
import com.filodot.noscroll.core.learning.model.LearningConcept
import com.filodot.noscroll.core.learning.model.CodeLanguage
import com.filodot.noscroll.core.learning.model.CodeTestCase
import com.filodot.noscroll.core.learning.model.MiniCodeContent
import com.filodot.noscroll.core.learning.model.LessonPackage
import com.filodot.noscroll.core.learning.model.SelfConfidence
import com.filodot.noscroll.core.testing.InMemoryAiCredentialRepository
import com.filodot.noscroll.core.testing.InMemoryLearningRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LearningStateHolderTest {
    private val now = Instant.parse("2026-07-24T10:00:00Z")

    @Test
    fun `correct answer is persisted and updates concept mastery`() = runTest {
        val repository = repository()
        val holder = holder(repository)
        runCurrent()
        openFirstLesson(holder)

        holder.dispatch(LearningAction.SelectOption("a", multiple = false))
        holder.dispatch(LearningAction.CheckAnswer)
        runCurrent()

        assertEquals(LearningAnswerStatus.CORRECT, holder.state.value.answerStatus)
        assertEquals(1, repository.getAttempts(StaticLearningCatalog.pythonCourse.id).size)
        assertEquals(
            14,
            repository.getMastery(StaticLearningCatalog.pythonCourse.id).single().score,
        )
    }

    @Test
    fun `lesson requires reading material before accepting an answer`() = runTest {
        val holder = holder(repository())
        runCurrent()
        holder.dispatch(LearningAction.OpenCourse(StaticLearningCatalog.pythonCourse.id))
        runCurrent()
        holder.dispatch(LearningAction.StartLesson)
        runCurrent()

        assertTrue(holder.state.value.showingLessonMaterial)
        holder.dispatch(LearningAction.SelectOption("a", multiple = false))
        assertTrue(holder.state.value.selectedOptionIds.isEmpty())

        holder.dispatch(LearningAction.OpenLessonQuestions)
        holder.dispatch(LearningAction.SelectOption("a", multiple = false))

        assertEquals(false, holder.state.value.showingLessonMaterial)
        assertEquals(setOf("a"), holder.state.value.selectedOptionIds)
    }

    @Test
    fun `reopening lesson resumes after activities completed before restart`() = runTest {
        val repository = repository()
        val completedActivity = StaticLearningCatalog.firstLesson.activities.first()
        repository.saveAttempt(
            LearningAttempt(
                id = "persisted-attempt",
                courseId = StaticLearningCatalog.pythonCourse.id,
                lessonId = StaticLearningCatalog.firstLesson.id,
                activityId = completedActivity.id,
                conceptIds = completedActivity.conceptIds,
                activityKind = completedActivity.content.kind,
                result = AttemptResult.CORRECT,
                hintsUsed = 0,
                durationSeconds = 20,
                confidence = SelfConfidence.MEDIUM,
                occurredAt = now.minusSeconds(60),
                localDate = LocalDate.of(2026, 7, 24),
            ),
        )
        val holder = holder(repository)
        runCurrent()
        holder.dispatch(LearningAction.OpenCourse(StaticLearningCatalog.pythonCourse.id))
        runCurrent()
        holder.dispatch(LearningAction.StartLesson)
        runCurrent()

        assertEquals(1, holder.state.value.activityIndex)
        assertEquals(setOf(completedActivity.id), holder.state.value.completedActivityIds)
        assertTrue(holder.state.value.message.orEmpty().contains("сохранённого места"))
    }

    @Test
    fun `wrong answer does not unlock continue action`() = runTest {
        val repository = repository()
        val holder = holder(repository)
        runCurrent()
        openFirstLesson(holder)

        holder.dispatch(LearningAction.SelectOption("b", multiple = false))
        holder.dispatch(LearningAction.CheckAnswer)
        runCurrent()
        holder.dispatch(LearningAction.ContinueLesson)
        runCurrent()

        assertEquals(LearningAnswerStatus.INCORRECT, holder.state.value.answerStatus)
        assertEquals(0, holder.state.value.activityIndex)
    }

    @Test
    fun `suspicious replacement is neutral and selects reserve activity`() = runTest {
        val repository = repository()
        val holder = holder(repository)
        runCurrent()
        openFirstLesson(holder)

        holder.dispatch(LearningAction.ReplaceSuspicious)
        runCurrent()

        val attempts = repository.getAttempts(StaticLearningCatalog.pythonCourse.id)
        assertEquals(AttemptResult.REPLACED_AS_SUSPICIOUS, attempts.single().result)
        assertTrue(repository.getMastery(StaticLearningCatalog.pythonCourse.id).isEmpty())
        assertEquals(1, holder.state.value.activityIndex)
    }

    @Test
    fun `topic course is saved as AI knowledge draft`() = runTest {
        val repository = InMemoryLearningRepository()
        val holder = holder(repository)
        runCurrent()

        holder.dispatch(LearningAction.StartCreateCourse)
        holder.dispatch(LearningAction.SetCreateTitle("Мой SQL"))
        holder.dispatch(LearningAction.SetCreateTopic("Основы SQL для аналитики данных"))
        holder.dispatch(LearningAction.CreateTopicCourse)
        runCurrent()

        val content = requireNotNull(holder.state.value.selectedCourse)
        assertEquals(CourseOrigin.TOPIC, content.course.origin)
        assertEquals(GroundingMode.AI_KNOWLEDGE, content.course.groundingMode)
        assertEquals("Мой SQL", content.course.title)
        assertEquals(LearningSourceType.TOPIC, content.sources.single().type)
        assertTrue(content.sourceChunks.isEmpty())
    }

    @Test
    fun `imported material is chunked and persisted as grounded course`() = runTest {
        val repository = InMemoryLearningRepository()
        val gateway = LearningMaterialGateway {
            LearningMaterialDocument(
                title = "notes.md",
                type = LearningSourceType.MARKDOWN,
                sections = listOf(MaterialSection("# Заголовок\n\nПолезный материал.")),
            )
        }
        val holder = holder(repository, gateway)
        runCurrent()

        holder.dispatch(LearningAction.StartCreateCourse)
        holder.dispatch(LearningAction.ImportMaterial("content://notes"))
        runCurrent()

        val content = requireNotNull(holder.state.value.selectedCourse)
        assertEquals(CourseOrigin.MATERIAL, content.course.origin)
        assertEquals(GroundingMode.SOURCE_REQUIRED, content.course.groundingMode)
        assertEquals("notes", content.course.title)
        assertEquals(LearningSourceType.MARKDOWN, content.sources.single().type)
        assertEquals("# Заголовок\n\nПолезный материал.", content.sourceChunks.single().text)
    }

    @Test
    fun `generated plan remains draft until user confirms it`() = runTest {
        val repository = InMemoryLearningRepository()
        val credentials = InMemoryAiCredentialRepository(
            initialKeys = mapOf(AiProviderId.GEMINI to "test-secret"),
        )
        val generator = CurriculumGenerator { content ->
            val concepts = (0..2).map { index ->
                LearningConcept(
                    id = "concept-$index",
                    courseId = content.course.id,
                    title = "Понятие $index",
                    summary = "Подробное описание понятия номер $index.",
                    position = index,
                )
            }
            GeneratedCurriculum(
                titleSuggestion = "План",
                descriptionSuggestion = "Описание сгенерированного учебного плана.",
                nodes = listOf(
                    CurriculumNode(
                        id = "node-1",
                        courseId = content.course.id,
                        parentId = null,
                        type = CurriculumNodeType.TOPIC,
                        title = "Первая тема",
                        description = "Подробное описание первой темы.",
                        position = 0,
                        estimatedMinutes = 15,
                        conceptIds = listOf("concept-0", "concept-1"),
                    ),
                    CurriculumNode(
                        id = "node-2",
                        courseId = content.course.id,
                        parentId = null,
                        type = CurriculumNodeType.TOPIC,
                        title = "Вторая тема",
                        description = "Подробное описание второй темы.",
                        position = 1,
                        estimatedMinutes = 15,
                        conceptIds = listOf("concept-2"),
                    ),
                ),
                concepts = concepts,
                providerId = AiProviderId.GEMINI,
                modelId = "test-model",
                qualityScore = 95,
                attempts = 1,
            )
        }
        val holder = holder(
            repository = repository,
            aiCredentials = credentials,
            curriculumGenerator = generator,
        )
        runCurrent()
        holder.dispatch(LearningAction.StartCreateCourse)
        holder.dispatch(LearningAction.SetCreateTopic("Основы SQL для аналитики"))
        holder.dispatch(LearningAction.CreateTopicCourse)
        runCurrent()

        holder.dispatch(LearningAction.GeneratePlan)
        runCurrent()
        assertEquals(CourseStatus.DRAFT, holder.state.value.selectedCourse?.course?.status)
        assertEquals(2, holder.state.value.selectedCourse?.curriculumNodes?.size)

        holder.dispatch(LearningAction.SetPlanNodeTitle("node-1", "Введение в SQL"))
        runCurrent()
        holder.dispatch(LearningAction.ConfirmPlan)
        runCurrent()

        val confirmed = requireNotNull(holder.state.value.selectedCourse)
        assertEquals(CourseStatus.READY, confirmed.course.status)
        assertEquals("Введение в SQL", confirmed.curriculumNodes.first().title)
    }

    @Test
    fun `validated generated lesson is saved and becomes ready offline`() = runTest {
        val readyContent = LearningCourseContent(
            course = StaticLearningCatalog.pythonCourse.copy(status = CourseStatus.READY),
            sources = emptyList(),
            curriculumNodes = listOf(StaticLearningCatalog.firstTopic),
            concepts = listOf(
                StaticLearningCatalog.variablesConcept,
                StaticLearningCatalog.expressionsConcept,
            ),
        )
        val repository = InMemoryLearningRepository(initialContent = listOf(readyContent))
        val credentials = InMemoryAiCredentialRepository(
            initialKeys = mapOf(AiProviderId.GEMINI to "test-secret"),
        )
        val generator = LessonGenerator { content, mastery ->
            assertEquals(readyContent.course.id, content.course.id)
            assertTrue(mastery.isEmpty())
            StaticLearningCatalog.firstLesson.copy(
                courseId = content.course.id,
                planVersion = content.course.planVersion,
            )
        }
        val holder = holder(
            repository = repository,
            aiCredentials = credentials,
            lessonGenerator = generator,
        )
        runCurrent()

        holder.dispatch(LearningAction.OpenCourse(readyContent.course.id))
        runCurrent()
        holder.dispatch(LearningAction.GenerateNextLesson)
        runCurrent()

        assertEquals(1, holder.state.value.readyLessons)
        assertEquals(
            StaticLearningCatalog.firstLesson.id,
            repository.peekNextLesson(readyContent.course.id)?.id,
        )
    }

    @Test
    fun `batch generation saves every lesson and reserves queued concepts`() = runTest {
        val readyContent = LearningCourseContent(
            course = StaticLearningCatalog.pythonCourse.copy(status = CourseStatus.READY),
            sources = emptyList(),
            curriculumNodes = listOf(StaticLearningCatalog.firstTopic),
            concepts = listOf(
                StaticLearningCatalog.variablesConcept,
                StaticLearningCatalog.expressionsConcept,
            ),
        )
        val repository = InMemoryLearningRepository(initialContent = listOf(readyContent))
        val credentials = InMemoryAiCredentialRepository(
            initialKeys = mapOf(AiProviderId.GEMINI to "test-secret"),
        )
        var generated = 0
        val reservations = mutableListOf<Set<String>>()
        val generator = object : BufferedLessonGenerator {
            override suspend fun generate(
                content: LearningCourseContent,
                mastery: List<com.filodot.noscroll.core.learning.model.ConceptMastery>,
            ): LessonPackage = generateNext(content, mastery, emptySet())

            override suspend fun generateNext(
                content: LearningCourseContent,
                mastery: List<com.filodot.noscroll.core.learning.model.ConceptMastery>,
                reservedConceptIds: Set<String>,
            ): LessonPackage {
                reservations += reservedConceptIds.toSet()
                generated += 1
                return StaticLearningCatalog.firstLesson.copy(
                    id = "batch-$generated",
                    courseId = content.course.id,
                    planVersion = content.course.planVersion,
                    activities = StaticLearningCatalog.firstLesson.activities.map {
                        it.copy(id = "batch-$generated-${it.id}")
                    },
                )
            }
        }
        val holder = holder(repository, aiCredentials = credentials, lessonGenerator = generator)
        runCurrent()
        holder.dispatch(LearningAction.OpenCourse(readyContent.course.id))
        runCurrent()
        holder.dispatch(LearningAction.SetLessonBatchSize(3))
        holder.dispatch(LearningAction.GenerateLessonBatch)
        runCurrent()

        assertEquals(3, repository.getValidatedLessons(readyContent.course.id).size)
        assertTrue(reservations.first().isEmpty())
        assertTrue(reservations.drop(1).all { it.isNotEmpty() })
        assertEquals(3, holder.state.value.readyLessons)
    }

    @Test
    fun `completing lesson automatically refills offline pool to three`() = runTest {
        val readyContent = LearningCourseContent(
            course = StaticLearningCatalog.pythonCourse.copy(status = CourseStatus.READY),
            sources = emptyList(),
            curriculumNodes = listOf(StaticLearningCatalog.firstTopic),
            concepts = listOf(
                StaticLearningCatalog.variablesConcept,
                StaticLearningCatalog.expressionsConcept,
            ),
        )
        val oneActivityLesson = StaticLearningCatalog.firstLesson.copy(
            activities = listOf(StaticLearningCatalog.firstLesson.activities.first()),
        )
        val repository = InMemoryLearningRepository(
            initialContent = listOf(readyContent),
            initialLessons = listOf(oneActivityLesson),
        )
        val credentials = InMemoryAiCredentialRepository(
            initialKeys = mapOf(AiProviderId.GEMINI to "test-secret"),
        )
        var generated = 0
        val generator = LessonGenerator { content, _ ->
            generated += 1
            oneActivityLesson.copy(
                id = "refill-$generated",
                courseId = content.course.id,
                planVersion = content.course.planVersion,
                activities = oneActivityLesson.activities.map {
                    it.copy(id = "refill-$generated-${it.id}")
                },
            )
        }
        val holder = holder(repository, aiCredentials = credentials, lessonGenerator = generator)
        runCurrent()
        holder.dispatch(LearningAction.OpenCourse(readyContent.course.id))
        runCurrent()
        holder.dispatch(LearningAction.StartLesson)
        runCurrent()
        holder.dispatch(LearningAction.OpenLessonQuestions)
        holder.dispatch(LearningAction.SelectOption("a", multiple = false))
        holder.dispatch(LearningAction.CheckAnswer)
        runCurrent()
        holder.dispatch(LearningAction.ContinueLesson)
        runCurrent()

        assertEquals(3, repository.getValidatedLessons(readyContent.course.id).size)
        assertEquals(3, generated)
        assertEquals(LearningPane.COMPLETED, holder.state.value.pane)
    }

    @Test
    fun `safe code evaluator can complete mini code activity`() = runTest {
        val codeActivity = StaticLearningCatalog.firstLesson.activities.first().copy(
            content = MiniCodeContent(
                language = CodeLanguage.PYTHON,
                starterCode = "def solve(x):\n    return x",
                tests = listOf(
                    CodeTestCase("public", """{"x":2}""", "4"),
                    CodeTestCase("hidden", """{"x":5}""", "10", hidden = true),
                ),
            ),
        )
        val lesson = StaticLearningCatalog.firstLesson.copy(activities = listOf(codeActivity))
        val repository = InMemoryLearningRepository(
            initialContent = listOf(
                LearningCourseContent(
                    course = StaticLearningCatalog.pythonCourse,
                    sources = emptyList(),
                    curriculumNodes = listOf(StaticLearningCatalog.firstTopic),
                    concepts = listOf(
                        StaticLearningCatalog.variablesConcept,
                        StaticLearningCatalog.expressionsConcept,
                    ),
                ),
            ),
            initialLessons = listOf(lesson),
        )
        val evaluator = CodeExerciseEvaluator { _, _ ->
            CodeEvaluation(CodeEvaluationStatus.CORRECT)
        }
        val holder = holder(repository, codeEvaluator = evaluator)
        runCurrent()
        openFirstLesson(holder)

        holder.dispatch(LearningAction.SetTextAnswer("def solve(x):\n    return x * 2"))
        holder.dispatch(LearningAction.CheckAnswer)
        runCurrent()

        assertEquals(LearningAnswerStatus.CORRECT, holder.state.value.answerStatus)
        assertEquals(
            AttemptResult.CORRECT,
            repository.getAttempts(StaticLearningCatalog.pythonCourse.id).single().result,
        )
    }

    @Test
    fun `confirmed course deletion removes all local learning data`() = runTest {
        val repository = repository()
        val holder = holder(repository)
        runCurrent()
        holder.dispatch(LearningAction.OpenCourse(StaticLearningCatalog.pythonCourse.id))
        runCurrent()

        holder.dispatch(LearningAction.RequestDeleteCourse)
        assertTrue(holder.state.value.deleteCourseConfirmation)
        holder.dispatch(LearningAction.ConfirmDeleteCourse)
        runCurrent()

        assertEquals(null, repository.getCourseContent(StaticLearningCatalog.pythonCourse.id))
        assertEquals(null, repository.getLesson(StaticLearningCatalog.firstLesson.id))
        assertEquals(LearningPane.COURSES, holder.state.value.pane)
        assertTrue(holder.state.value.message.orEmpty().contains("удалены"))
    }

    private suspend fun kotlinx.coroutines.test.TestScope.openFirstLesson(
        holder: LearningStateHolder,
    ) {
        holder.dispatch(LearningAction.OpenCourse(StaticLearningCatalog.pythonCourse.id))
        runCurrent()
        holder.dispatch(LearningAction.StartLesson)
        runCurrent()
        holder.dispatch(LearningAction.OpenLessonQuestions)
    }

    private fun kotlinx.coroutines.test.TestScope.holder(
        repository: InMemoryLearningRepository,
        materialGateway: LearningMaterialGateway? = null,
        aiCredentials: InMemoryAiCredentialRepository? = null,
        curriculumGenerator: CurriculumGenerator? = null,
        lessonGenerator: LessonGenerator? = null,
        codeEvaluator: CodeExerciseEvaluator? = null,
    ) = LearningStateHolder(
        repository = repository,
        scope = backgroundScope,
        materialGateway = materialGateway,
        aiCredentials = aiCredentials,
        curriculumGenerator = curriculumGenerator,
        lessonGenerator = lessonGenerator,
        codeEvaluator = codeEvaluator,
        now = { now },
        zoneId = ZoneId.of("UTC"),
        idGenerator = { "attempt-${repository.hashCode()}-${holderIds++}" },
    )

    private fun repository() = InMemoryLearningRepository(
        initialContent = listOf(
            LearningCourseContent(
                course = StaticLearningCatalog.pythonCourse,
                sources = emptyList(),
                curriculumNodes = listOf(StaticLearningCatalog.firstTopic),
                concepts = listOf(
                    StaticLearningCatalog.variablesConcept,
                    StaticLearningCatalog.expressionsConcept,
                ),
            ),
        ),
        initialLessons = listOf(StaticLearningCatalog.firstLesson),
    )

    companion object {
        private var holderIds = 0
    }
}
