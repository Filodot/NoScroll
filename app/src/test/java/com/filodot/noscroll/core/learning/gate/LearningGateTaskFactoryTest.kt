package com.filodot.noscroll.core.learning.gate

import com.filodot.noscroll.core.contracts.WallClock
import com.filodot.noscroll.core.learning.content.StaticLearningCatalog
import com.filodot.noscroll.core.learning.model.AttemptResult
import com.filodot.noscroll.core.learning.model.CourseStatus
import com.filodot.noscroll.core.learning.model.LearningCourseContent
import com.filodot.noscroll.core.model.TaskCompletionMode
import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskTarget
import com.filodot.noscroll.core.model.TaskTrigger
import com.filodot.noscroll.core.model.TaskType
import com.filodot.noscroll.core.testing.InMemoryLearningRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningGateTaskFactoryTest {
    private val now = Instant.parse("2026-07-24T12:00:00Z")

    @Test
    fun `validated single choice activity becomes persistent gate task`() = runTest {
        val repository = repository()
        val factory = factory(repository)

        val task = requireNotNull(
            factory.create(
                difficulty = TaskDifficulty.HARD,
                trigger = TaskTrigger.INTERVAL,
                target = TaskTarget.YOUTUBE_SHORTS,
                selectedCourseIds = setOf(StaticLearningCatalog.pythonCourse.id),
                sequence = 0,
            ),
        )

        assertEquals(TaskType.LEARNING, task.type)
        assertEquals(TaskCompletionMode.SINGLE_CHOICE, task.completionMode)
        assertEquals("a", task.expectedChoiceId)
        assertEquals(3, task.choices.size)
        assertEquals(StaticLearningCatalog.firstLesson.id, task.learningLessonId)
        assertEquals("python-variable-choice", task.learningActivityId)
        assertEquals(TaskDifficulty.HARD, task.difficulty)
    }

    @Test
    fun `draft and unselected courses never create gate task`() = runTest {
        val draft = content().copy(
            course = StaticLearningCatalog.pythonCourse.copy(status = CourseStatus.DRAFT),
        )
        val repository = InMemoryLearningRepository(
            initialContent = listOf(draft),
            initialLessons = listOf(StaticLearningCatalog.firstLesson),
        )
        val factory = factory(repository)

        assertNull(
            factory.create(
                TaskDifficulty.EASY,
                TaskTrigger.ENTRY,
                TaskTarget.INSTAGRAM,
                selectedCourseIds = setOf(draft.course.id),
                sequence = 0,
            ),
        )
        assertNull(
            factory.create(
                TaskDifficulty.EASY,
                TaskTrigger.ENTRY,
                TaskTarget.INSTAGRAM,
                selectedCourseIds = emptySet(),
                sequence = 0,
            ),
        )
    }

    @Test
    fun `results update mastery and completed gate lesson leaves queue`() = runTest {
        val repository = repository()
        val factory = factory(repository)
        val task = requireNotNull(
            factory.create(
                TaskDifficulty.EASY,
                TaskTrigger.ENTRY,
                TaskTarget.YOUTUBE_SHORTS,
                selectedCourseIds = setOf(StaticLearningCatalog.pythonCourse.id),
                sequence = 0,
            ),
        )

        factory.recordResult(task, AttemptResult.INCORRECT)
        factory.recordResult(task, AttemptResult.CORRECT)

        val attempts = repository.getAttempts(StaticLearningCatalog.pythonCourse.id)
        assertEquals(
            listOf(AttemptResult.INCORRECT, AttemptResult.CORRECT),
            attempts.map { it.result },
        )
        val mastery = repository.getMastery(StaticLearningCatalog.pythonCourse.id)
            .single { it.conceptId == StaticLearningCatalog.variablesConcept.id }
        assertEquals(2, mastery.attemptCount)
        assertEquals(1, mastery.correctCount)
        assertEquals(14, mastery.score)
        assertNull(repository.peekNextLesson(StaticLearningCatalog.pythonCourse.id))
    }

    @Test
    fun `suspicious replacement is recorded without changing mastery`() = runTest {
        val repository = repository()
        val factory = factory(repository)
        val task = requireNotNull(
            factory.create(
                TaskDifficulty.MEDIUM,
                TaskTrigger.INTERVAL,
                TaskTarget.INSTAGRAM,
                selectedCourseIds = setOf(StaticLearningCatalog.pythonCourse.id),
                sequence = 0,
            ),
        )

        factory.recordResult(task, AttemptResult.REPLACED_AS_SUSPICIOUS)

        assertEquals(
            AttemptResult.REPLACED_AS_SUSPICIOUS,
            repository.getAttempts(StaticLearningCatalog.pythonCourse.id).single().result,
        )
        assertTrue(repository.getMastery(StaticLearningCatalog.pythonCourse.id).isEmpty())
    }

    private fun factory(repository: InMemoryLearningRepository): LearningGateTaskFactory {
        var nextId = 0
        return LearningGateTaskFactory(
            repository = repository,
            wallClock = WallClock { now },
            zoneId = ZoneId.of("UTC"),
            idGenerator = { "generated-${nextId++}" },
        )
    }

    private fun repository() = InMemoryLearningRepository(
        initialContent = listOf(content()),
        initialLessons = listOf(StaticLearningCatalog.firstLesson),
    )

    private fun content() = LearningCourseContent(
        course = StaticLearningCatalog.pythonCourse.copy(status = CourseStatus.READY),
        sources = emptyList(),
        curriculumNodes = listOf(StaticLearningCatalog.firstTopic),
        concepts = listOf(
            StaticLearningCatalog.variablesConcept,
            StaticLearningCatalog.expressionsConcept,
        ),
    )
}
