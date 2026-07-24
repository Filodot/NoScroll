package com.filodot.noscroll.feature.learning

import com.filodot.noscroll.core.learning.content.StaticLearningCatalog
import com.filodot.noscroll.core.learning.importing.LearningMaterialDocument
import com.filodot.noscroll.core.learning.importing.LearningMaterialGateway
import com.filodot.noscroll.core.learning.importing.MaterialSection
import com.filodot.noscroll.core.learning.model.AttemptResult
import com.filodot.noscroll.core.learning.model.LearningCourseContent
import com.filodot.noscroll.core.learning.model.CourseOrigin
import com.filodot.noscroll.core.learning.model.GroundingMode
import com.filodot.noscroll.core.learning.model.LearningSourceType
import com.filodot.noscroll.core.testing.InMemoryLearningRepository
import java.time.Instant
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

    private suspend fun kotlinx.coroutines.test.TestScope.openFirstLesson(
        holder: LearningStateHolder,
    ) {
        holder.dispatch(LearningAction.OpenCourse(StaticLearningCatalog.pythonCourse.id))
        runCurrent()
        holder.dispatch(LearningAction.StartLesson)
        runCurrent()
    }

    private fun kotlinx.coroutines.test.TestScope.holder(
        repository: InMemoryLearningRepository,
        materialGateway: LearningMaterialGateway? = null,
    ) = LearningStateHolder(
        repository = repository,
        scope = backgroundScope,
        materialGateway = materialGateway,
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
