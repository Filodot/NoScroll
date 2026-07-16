package com.filodot.noscroll.core.tasks

import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskTrigger
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskDifficultyPolicyTest {
    private val policy = TaskDifficultyPolicy()
    private val now = Instant.parse("2026-07-16T10:00:00Z")

    @Test
    fun `entry task is always easy and does not alter interval series`() {
        val state = TaskDifficultyState(2, now.minusSeconds(60))

        val assignment = policy.assign(TaskTrigger.ENTRY, state, now)

        assertEquals(TaskDifficulty.EASY, assignment.difficulty)
        assertEquals(state, assignment.nextState)
    }

    @Test
    fun `first interval block is medium`() {
        val assignment = policy.assign(TaskTrigger.INTERVAL, TaskDifficultyState(), now)

        assertEquals(TaskDifficulty.MEDIUM, assignment.difficulty)
        assertEquals(TaskDifficultyState(1, now), assignment.nextState)
    }

    @Test
    fun `second interval block inside thirty minutes is hard`() {
        val state = TaskDifficultyState(1, now.minus(Duration.ofMinutes(29)))

        val assignment = policy.assign(TaskTrigger.INTERVAL, state, now)

        assertEquals(TaskDifficulty.HARD, assignment.difficulty)
        assertEquals(TaskDifficultyState(2, now), assignment.nextState)
    }

    @Test
    fun `series decays to medium exactly after thirty minutes`() {
        val state = TaskDifficultyState(4, now.minus(Duration.ofMinutes(30)))

        val assignment = policy.assign(TaskTrigger.INTERVAL, state, now)

        assertEquals(TaskDifficulty.MEDIUM, assignment.difficulty)
        assertEquals(TaskDifficultyState(1, now), assignment.nextState)
    }

    @Test
    fun `wall clock rollback safely starts a new medium series`() {
        val state = TaskDifficultyState(2, now.plusSeconds(1))

        val assignment = policy.assign(TaskTrigger.INTERVAL, state, now)

        assertEquals(TaskDifficulty.MEDIUM, assignment.difficulty)
        assertEquals(TaskDifficultyState(1, now), assignment.nextState)
    }

    @Test
    fun `interval streak saturates instead of overflowing`() {
        val state = TaskDifficultyState(Int.MAX_VALUE, now.minusSeconds(1))

        val assignment = policy.assign(TaskTrigger.INTERVAL, state, now)

        assertEquals(TaskDifficulty.HARD, assignment.difficulty)
        assertEquals(TaskDifficultyState(Int.MAX_VALUE, now), assignment.nextState)
    }
}
