package com.filodot.noscroll.core.tasks

import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskTrigger
import java.time.Duration
import java.time.Instant

data class TaskDifficultyState(
    val intervalBlockStreak: Int = 0,
    val lastIntervalBlockAt: Instant? = null,
)

data class TaskDifficultyAssignment(
    val difficulty: TaskDifficulty,
    val nextState: TaskDifficultyState,
)

/** Reusable trigger-to-difficulty policy shared by current and future task content types. */
class TaskDifficultyPolicy(
    private val hardDifficultyDecay: Duration = DEFAULT_HARD_DIFFICULTY_DECAY,
) {
    init {
        require(!hardDifficultyDecay.isNegative && !hardDifficultyDecay.isZero)
    }

    fun assign(
        trigger: TaskTrigger,
        state: TaskDifficultyState,
        now: Instant,
    ): TaskDifficultyAssignment = when (trigger) {
        TaskTrigger.ENTRY -> TaskDifficultyAssignment(
            difficulty = TaskDifficulty.EASY,
            nextState = state.normalized(),
        )

        TaskTrigger.INTERVAL -> assignInterval(state.normalized(), now)
    }

    private fun assignInterval(
        state: TaskDifficultyState,
        now: Instant,
    ): TaskDifficultyAssignment {
        val lastBlock = state.lastIntervalBlockAt
        val continuesSeries = lastBlock != null &&
            !now.isBefore(lastBlock) &&
            Duration.between(lastBlock, now) < hardDifficultyDecay
        val nextStreak = if (continuesSeries) {
            state.intervalBlockStreak.saturatingIncrement()
        } else {
            1
        }
        return TaskDifficultyAssignment(
            difficulty = if (continuesSeries && state.intervalBlockStreak >= 1) {
                TaskDifficulty.HARD
            } else {
                TaskDifficulty.MEDIUM
            },
            nextState = TaskDifficultyState(
                intervalBlockStreak = nextStreak,
                lastIntervalBlockAt = now,
            ),
        )
    }

    private fun TaskDifficultyState.normalized(): TaskDifficultyState = copy(
        intervalBlockStreak = intervalBlockStreak.coerceAtLeast(0),
        lastIntervalBlockAt = lastIntervalBlockAt.takeIf { intervalBlockStreak > 0 },
    )

    private fun Int.saturatingIncrement(): Int = if (this == Int.MAX_VALUE) this else this + 1

    companion object {
        val DEFAULT_HARD_DIFFICULTY_DECAY: Duration = Duration.ofMinutes(30)
    }
}
