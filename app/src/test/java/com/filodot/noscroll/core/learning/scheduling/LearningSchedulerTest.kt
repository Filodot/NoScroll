package com.filodot.noscroll.core.learning.scheduling

import com.filodot.noscroll.core.learning.model.ConceptMastery
import com.filodot.noscroll.core.learning.model.LearningConcept
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningSchedulerTest {
    private val now = Instant.parse("2026-07-24T08:00:00Z")
    private val scheduler = LearningScheduler()

    @Test
    fun `new concept stays locked until all prerequisites are mastered`() {
        val prerequisite = concept("first", position = 0)
        val dependent = concept(
            id = "second",
            position = 1,
            prerequisiteIds = setOf(prerequisite.id),
        )

        val selected = scheduler.select(
            concepts = listOf(prerequisite, dependent),
            masteryByConceptId = emptyMap(),
            now = now,
            maxConcepts = 3,
        )

        assertEquals(listOf("first"), selected.map { it.concept.id })
    }

    @Test
    fun `mastered prerequisite unlocks next concept`() {
        val prerequisite = concept("first", position = 0)
        val dependent = concept(
            id = "second",
            position = 1,
            prerequisiteIds = setOf(prerequisite.id),
        )
        val mastery = ConceptMastery(
            conceptId = prerequisite.id,
            score = 90,
            attemptCount = 4,
            correctCount = 4,
            successfulFormats = setOf(
                com.filodot.noscroll.core.learning.model.ActivityKind.SINGLE_CHOICE,
                com.filodot.noscroll.core.learning.model.ActivityKind.FLASHCARD,
            ),
            successfulDates = setOf(
                java.time.LocalDate.of(2026, 7, 23),
                java.time.LocalDate.of(2026, 7, 24),
            ),
            nextReviewAt = now.plusSeconds(3600),
        )

        val selected = scheduler.select(
            listOf(prerequisite, dependent),
            mapOf(prerequisite.id to mastery),
            now,
            maxConcepts = 2,
        )

        assertTrue(selected.any {
            it.concept.id == dependent.id && it.reason == ScheduleReason.NEW_MATERIAL
        })
    }

    @Test
    fun `overdue concept is selected as review`() {
        val concept = concept("review", position = 0)
        val state = ConceptMastery(
            conceptId = concept.id,
            score = 30,
            attemptCount = 1,
            nextReviewAt = now.minusSeconds(1),
        )

        val selected = scheduler.select(
            listOf(concept),
            mapOf(concept.id to state),
            now,
            maxConcepts = 1,
        )

        assertEquals(1, selected.size)
        assertEquals(ScheduleReason.DUE_REVIEW, selected.single().reason)
    }

    @Test
    fun `selection never exceeds requested size`() {
        val concepts = (0..9).map { concept("concept-$it", position = it) }

        val selected = scheduler.select(
            concepts = concepts,
            masteryByConceptId = emptyMap(),
            now = now,
            maxConcepts = 1,
        )

        assertEquals(1, selected.size)
    }

    private fun concept(
        id: String,
        position: Int,
        prerequisiteIds: Set<String> = emptySet(),
    ) = LearningConcept(
        id = id,
        courseId = "course",
        title = id,
        summary = "summary",
        position = position,
        prerequisiteIds = prerequisiteIds,
    )
}
