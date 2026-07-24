package com.filodot.noscroll.core.learning.progress

import com.filodot.noscroll.core.learning.model.ActivityKind
import com.filodot.noscroll.core.learning.model.AttemptResult
import com.filodot.noscroll.core.learning.model.ConceptMastery
import com.filodot.noscroll.core.learning.model.LearningAttempt
import com.filodot.noscroll.core.learning.model.SelfConfidence
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasteryPolicyTest {
    private val policy = MasteryPolicy()
    private val now = Instant.parse("2026-07-24T08:00:00Z")

    @Test
    fun `correct answer raises score and schedules ten minute retrieval`() {
        val updated = policy.update(
            ConceptMastery(conceptId = CONCEPT_ID),
            attempt(result = AttemptResult.CORRECT),
        )

        assertEquals(14, updated.score)
        assertEquals(1, updated.correctCount)
        assertEquals(now.plusSeconds(600), updated.nextReviewAt)
    }

    @Test
    fun `hints reduce but never erase progress`() {
        val updated = policy.update(
            ConceptMastery(conceptId = CONCEPT_ID),
            attempt(result = AttemptResult.CORRECT, hintsUsed = 10),
        )

        assertEquals(8, updated.score)
    }

    @Test
    fun `incorrect answer lowers score and resets streak`() {
        val current = ConceptMastery(
            conceptId = CONCEPT_ID,
            score = 40,
            consecutiveCorrect = 3,
        )

        val updated = policy.update(current, attempt(result = AttemptResult.INCORRECT))

        assertEquals(32, updated.score)
        assertEquals(0, updated.consecutiveCorrect)
        assertEquals(now.plusSeconds(600), updated.nextReviewAt)
    }

    @Test
    fun `suspicious replacement never changes mastery`() {
        val current = ConceptMastery(conceptId = CONCEPT_ID, score = 55, attemptCount = 7)

        val updated = policy.update(
            current,
            attempt(result = AttemptResult.REPLACED_AS_SUSPICIOUS),
        )

        assertEquals(current, updated)
    }

    @Test
    fun `mastery requires score attempts formats and separate days`() {
        var state = ConceptMastery(conceptId = CONCEPT_ID, score = 75)
        state = policy.update(
            state,
            attempt(
                result = AttemptResult.CORRECT,
                kind = ActivityKind.SINGLE_CHOICE,
                date = LocalDate.of(2026, 7, 24),
            ),
        )
        state = policy.update(
            state,
            attempt(
                result = AttemptResult.CORRECT,
                kind = ActivityKind.SINGLE_CHOICE,
                date = LocalDate.of(2026, 7, 24),
            ),
        )
        assertFalse(state.mastered)

        state = policy.update(
            state,
            attempt(
                result = AttemptResult.CORRECT,
                kind = ActivityKind.CODE_OUTPUT,
                date = LocalDate.of(2026, 7, 25),
            ),
        )

        assertTrue(state.mastered)
    }

    private fun attempt(
        result: AttemptResult,
        hintsUsed: Int = 0,
        kind: ActivityKind = ActivityKind.SINGLE_CHOICE,
        date: LocalDate = LocalDate.of(2026, 7, 24),
    ) = LearningAttempt(
        id = "attempt",
        courseId = "course",
        lessonId = "lesson",
        activityId = "activity",
        conceptIds = setOf(CONCEPT_ID),
        activityKind = kind,
        result = result,
        hintsUsed = hintsUsed,
        durationSeconds = 30,
        confidence = SelfConfidence.MEDIUM,
        occurredAt = now,
        localDate = date,
    )
}

private const val CONCEPT_ID = "concept"
