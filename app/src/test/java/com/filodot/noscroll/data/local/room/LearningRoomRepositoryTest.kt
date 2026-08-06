package com.filodot.noscroll.data.local.room

import androidx.room.Room
import com.filodot.noscroll.core.learning.content.StaticLearningCatalog
import com.filodot.noscroll.core.learning.model.ActivityKind
import com.filodot.noscroll.core.learning.model.AttemptResult
import com.filodot.noscroll.core.learning.model.ConceptMastery
import com.filodot.noscroll.core.learning.model.LearningAttempt
import com.filodot.noscroll.core.learning.model.LearningCourseContent
import com.filodot.noscroll.core.learning.model.LearningSource
import com.filodot.noscroll.core.learning.model.LearningSourceChunk
import com.filodot.noscroll.core.learning.model.LearningSourceType
import com.filodot.noscroll.core.learning.model.LessonPackageStatus
import com.filodot.noscroll.core.learning.model.SelfConfidence
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.model.TaskType
import com.filodot.noscroll.data.local.repository.RoomLearningRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearningRoomRepositoryTest {
    private lateinit var database: NoScrollDatabase
    private lateinit var repository: RoomLearningRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            NoScrollDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomLearningRepository(database.learningDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `course lesson attempt and mastery survive repository recreation`() = runBlocking {
        val source = LearningSource(
            id = "source-1",
            courseId = StaticLearningCatalog.pythonCourse.id,
            title = "Python notes",
            type = LearningSourceType.PLAIN_TEXT,
            contentHash = "abc",
            importedAt = Instant.parse("2026-07-24T09:00:00Z"),
        )
        val chunk = LearningSourceChunk(
            id = "chunk-1",
            sourceId = source.id,
            courseId = source.courseId,
            position = 0,
            text = "Переменная хранит значение.",
            characterStart = 0,
            characterEnd = 27,
            estimatedTokens = 7,
        )
        val content = LearningCourseContent(
            course = StaticLearningCatalog.pythonCourse,
            sources = listOf(source),
            sourceChunks = listOf(chunk),
            curriculumNodes = listOf(StaticLearningCatalog.firstTopic),
            concepts = listOf(
                StaticLearningCatalog.variablesConcept,
                StaticLearningCatalog.expressionsConcept,
            ),
        )
        repository.saveCourseContent(content)
        repository.saveLesson(StaticLearningCatalog.firstLesson)
        val attempt = attempt()
        val mastery = ConceptMastery(
            conceptId = StaticLearningCatalog.variablesConcept.id,
            score = 82,
            attemptCount = 3,
            correctCount = 3,
            consecutiveCorrect = 2,
            successfulFormats = setOf(ActivityKind.SINGLE_CHOICE, ActivityKind.CODE_OUTPUT),
            successfulDates = setOf(
                LocalDate.of(2026, 7, 23),
                LocalDate.of(2026, 7, 24),
            ),
            lastAttemptAt = attempt.occurredAt,
            nextReviewAt = attempt.occurredAt.plusSeconds(86_400),
        )
        repository.saveAttempt(attempt)
        repository.saveMastery(mastery)

        val recreated = RoomLearningRepository(database.learningDao())

        assertEquals(content, recreated.getCourseContent(content.course.id))
        assertEquals(StaticLearningCatalog.firstLesson, recreated.peekNextLesson(content.course.id))
        assertEquals(
            listOf(StaticLearningCatalog.firstLesson),
            recreated.getValidatedLessons(content.course.id),
        )
        assertEquals(listOf(attempt), recreated.getAttempts(content.course.id))
        assertEquals(listOf(mastery), recreated.getMastery(content.course.id))
        assertEquals(1, recreated.observeValidatedLessonCount(content.course.id).first())
    }

    @Test
    fun `taking lesson is atomic and removes it from ready queue`() = runBlocking {
        val content = courseContent()
        repository.saveCourseContent(content)
        repository.saveLesson(StaticLearningCatalog.firstLesson)

        val taken = repository.takeNextLesson(content.course.id)

        assertEquals(LessonPackageStatus.CONSUMED, taken?.status)
        assertNull(repository.takeNextLesson(content.course.id))
        assertNull(repository.peekNextLesson(content.course.id))
        assertEquals(0, repository.observeValidatedLessonCount(content.course.id).first())
    }

    @Test
    fun `deleting course removes every owned row`() = runBlocking {
        val content = courseContent()
        repository.saveCourseContent(content)
        repository.saveLesson(StaticLearningCatalog.firstLesson)
        repository.saveAttempt(attempt())
        repository.saveMastery(
            ConceptMastery(conceptId = StaticLearningCatalog.variablesConcept.id, score = 10),
        )

        repository.deleteCourse(content.course.id)

        assertNull(repository.getCourseContent(content.course.id))
        assertNull(database.learningDao().getLesson(StaticLearningCatalog.firstLesson.id))
        assertTrue(database.learningDao().getActivities(StaticLearningCatalog.firstLesson.id).isEmpty())
        assertTrue(repository.getAttempts(content.course.id).isEmpty())
        assertTrue(repository.getMastery(content.course.id).isEmpty())
    }

    @Test
    fun `editing plan preserves retained mastery and removes deleted concepts`() = runBlocking {
        val full = courseContent()
        repository.saveCourseContent(full)
        val retained = ConceptMastery(
            conceptId = StaticLearningCatalog.variablesConcept.id,
            score = 40,
        )
        val removed = ConceptMastery(
            conceptId = StaticLearningCatalog.expressionsConcept.id,
            score = 70,
        )
        repository.saveMastery(retained)
        repository.saveMastery(removed)

        repository.saveCourseContent(
            full.copy(
                curriculumNodes = listOf(
                    StaticLearningCatalog.firstTopic.copy(
                        conceptIds = listOf(StaticLearningCatalog.variablesConcept.id),
                    ),
                ),
                concepts = listOf(StaticLearningCatalog.variablesConcept),
            ),
        )
        repository.saveCourseContent(full)

        assertEquals(listOf(retained), repository.getMastery(full.course.id))
    }

    @Test
    fun `new plan quarantines old lessons and removes its pending gate atomically`() = runBlocking {
        val content = courseContent()
        repository.saveCourseContent(content)
        repository.saveLesson(StaticLearningCatalog.firstLesson)
        val pending = com.filodot.noscroll.core.model.PendingTask(
            id = "old-plan-task",
            operation = com.filodot.noscroll.core.model.ArithmeticOperation.ADD,
            leftOperand = 0,
            rightOperand = 0,
            expectedAnswer = 0,
            createdAt = Instant.parse("2026-07-24T11:00:00Z"),
            type = TaskType.LEARNING,
            learningCourseId = content.course.id,
            learningLessonId = StaticLearningCatalog.firstLesson.id,
            learningActivityId = StaticLearningCatalog.firstLesson.activities.first().id,
        )
        database.pendingTaskDao().upsert(pending.toEntity())
        database.gateCycleDao().upsert(
            GateCycle(
                localDate = LocalDate.of(2026, 7, 24),
                pendingTaskId = pending.id,
                updatedAt = pending.createdAt,
            ).toEntity(),
        )

        repository.saveCourseContent(
            content.copy(course = content.course.copy(planVersion = content.course.planVersion + 1)),
        )

        assertNull(repository.peekNextLesson(content.course.id))
        assertEquals(0, repository.observeValidatedLessonCount(content.course.id).first())
        assertEquals(
            LessonPackageStatus.QUARANTINED,
            repository.getLesson(StaticLearningCatalog.firstLesson.id)?.status,
        )
        assertNull(database.pendingTaskDao().get(pending.id))
        assertNull(
            database.gateCycleDao().get(GateCycle.CURRENT_GATE_CYCLE_ID)?.pendingTaskId,
        )
    }

    private fun courseContent() = LearningCourseContent(
        course = StaticLearningCatalog.pythonCourse,
        sources = emptyList(),
        curriculumNodes = listOf(StaticLearningCatalog.firstTopic),
        concepts = listOf(
            StaticLearningCatalog.variablesConcept,
            StaticLearningCatalog.expressionsConcept,
        ),
    )

    private fun attempt() = LearningAttempt(
        id = "attempt-1",
        courseId = StaticLearningCatalog.pythonCourse.id,
        lessonId = StaticLearningCatalog.firstLesson.id,
        activityId = StaticLearningCatalog.firstLesson.activities.first().id,
        conceptIds = setOf(StaticLearningCatalog.variablesConcept.id),
        activityKind = ActivityKind.SINGLE_CHOICE,
        result = AttemptResult.CORRECT,
        hintsUsed = 0,
        durationSeconds = 25,
        confidence = SelfConfidence.MEDIUM,
        occurredAt = Instant.parse("2026-07-24T10:00:00Z"),
        localDate = LocalDate.of(2026, 7, 24),
    )
}
