package com.filodot.noscroll.feature.learning

import com.filodot.noscroll.core.learning.content.StaticLearningCatalog
import com.filodot.noscroll.core.learning.model.AttemptResult
import com.filodot.noscroll.core.learning.model.LearningCourseContent
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
    ) = LearningStateHolder(
        repository = repository,
        scope = backgroundScope,
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
