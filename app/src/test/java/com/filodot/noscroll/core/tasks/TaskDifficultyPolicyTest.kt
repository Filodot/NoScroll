package com.filodot.noscroll.core.tasks

import com.filodot.noscroll.core.model.TaskDifficulty
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskDifficultyPolicyTest {
    private val policy = TaskDifficultyPolicy()
    private val config = TaskDifficultyConfig(
        mediumThresholdMinutes = 10,
        hardThresholdMinutes = 25,
        decayBreakMinutesPerLoadMinute = 5,
    )
    private val now = Instant.parse("2026-07-16T10:00:00Z")

    @Test
    fun `fresh load is easy`() {
        assertEquals(TaskDifficulty.EASY, policy.difficulty(TaskDifficultyState(), config))
    }

    @Test
    fun `ten watched minutes reach medium`() {
        val updated = policy.update(
            state = TaskDifficultyState(updatedAt = now.minusSeconds(600)),
            now = now,
            shortsActive = true,
            config = config,
            observedActiveSeconds = 600,
        )

        assertEquals(600L, updated.loadSeconds)
        assertEquals(TaskDifficulty.MEDIUM, policy.difficulty(updated, config))
    }

    @Test
    fun `first active sample is not lost before timestamp is initialized`() {
        val updated = policy.update(
            state = TaskDifficultyState(),
            now = now,
            shortsActive = true,
            config = config,
            observedActiveSeconds = 1,
        )

        assertEquals(1L, updated.loadSeconds)
        assertEquals(now, updated.updatedAt)
    }

    @Test
    fun `a full day away recovers accumulated load without periodic writes`() {
        val state = TaskDifficultyState(
            loadSeconds = 25 * 60L,
            updatedAt = now,
        )

        val recovered = policy.update(
            state = state,
            now = now.plusSeconds(24 * 60 * 60L),
            shortsActive = false,
            config = config,
        )

        assertEquals(0L, recovered.loadSeconds)
        assertEquals(0L, recovered.recoverySeconds)
    }

    @Test
    fun `twenty five watched minutes reach hard regardless of trigger`() {
        val state = TaskDifficultyState(loadSeconds = 25 * 60L, updatedAt = now)

        assertEquals(TaskDifficulty.HARD, policy.difficulty(state, config))
    }

    @Test
    fun `five break minutes remove one load minute`() {
        val state = TaskDifficultyState(loadSeconds = 10 * 60L, updatedAt = now)

        val recovered = policy.update(
            state = state,
            now = now.plusSeconds(5 * 60L),
            shortsActive = false,
            config = config,
        )

        assertEquals(9 * 60L, recovered.loadSeconds)
        assertEquals(TaskDifficulty.EASY, policy.difficulty(recovered, config))
    }

    @Test
    fun `frequent heartbeats preserve fractional recovery`() {
        var state = TaskDifficultyState(loadSeconds = 60L, updatedAt = now)
        repeat(5) {
            state = policy.update(
                state = state,
                now = now.plusSeconds((it + 1).toLong()),
                shortsActive = false,
                config = config,
            )
        }

        assertEquals(59L, state.loadSeconds)
        assertEquals(0L, state.recoverySeconds)
    }

    @Test
    fun `offline time is recovery even when first observed screen is Shorts`() {
        val state = TaskDifficultyState(loadSeconds = 120L, updatedAt = now.minusSeconds(60))

        val recovered = policy.update(
            state = state,
            now = now,
            shortsActive = true,
            config = config,
            observedActiveSeconds = 0,
        )

        assertEquals(108L, recovered.loadSeconds)
    }

    @Test
    fun `wall clock rollback leaves load unchanged`() {
        val state = TaskDifficultyState(loadSeconds = 123L, updatedAt = now.plusSeconds(1))

        val updated = policy.update(state, now, shortsActive = true, config = config)

        assertEquals(123L, updated.loadSeconds)
        assertEquals(now, updated.updatedAt)
    }
}
