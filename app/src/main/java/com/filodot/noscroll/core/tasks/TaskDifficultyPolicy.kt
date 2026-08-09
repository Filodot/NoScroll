package com.filodot.noscroll.core.tasks

import com.filodot.noscroll.core.model.TaskDifficulty
import java.time.Duration
import java.time.Instant

data class TaskDifficultyConfig(
    val mediumThresholdMinutes: Int = 10,
    val hardThresholdMinutes: Int = 25,
    /** Real break minutes required to remove one load minute. */
    val decayBreakMinutesPerLoadMinute: Int = 5,
) {
    fun normalized(): TaskDifficultyConfig {
        val medium = mediumThresholdMinutes.coerceIn(1, 240)
        return copy(
            mediumThresholdMinutes = medium,
            hardThresholdMinutes = hardThresholdMinutes.coerceIn(medium + 1, 480),
            decayBreakMinutesPerLoadMinute = decayBreakMinutesPerLoadMinute.coerceIn(1, 60),
        )
    }
}

data class TaskDifficultyState(
    val loadSeconds: Long = 0,
    val updatedAt: Instant? = null,
    /** Preserves partial recovery when updates arrive more often than the decay ratio. */
    val recoverySeconds: Long = 0,
)

/**
 * Persistent, task-type agnostic cognitive-load scale.
 *
 * Time in controlled distracting apps adds load in real time. Time away gradually removes it;
 * the scale is not tied to a calendar day. Wall-clock rollback never changes the load.
 */
class TaskDifficultyPolicy {
    fun update(
        state: TaskDifficultyState,
        now: Instant,
        distractingAppActive: Boolean,
        config: TaskDifficultyConfig,
        observedActiveSeconds: Long? = null,
    ): TaskDifficultyState {
        val normalized = normalize(state)
        val previous = normalized.updatedAt
        if (previous == null) {
            val initialActiveSeconds = if (distractingAppActive) {
                observedActiveSeconds.orZero().coerceAtLeast(0)
            } else {
                0
            }
            return normalized.copy(
                loadSeconds = normalized.loadSeconds.saturatingAdd(initialActiveSeconds),
                updatedAt = now,
                recoverySeconds = if (initialActiveSeconds > 0) 0 else normalized.recoverySeconds,
            )
        }
        if (now.isBefore(previous)) return normalized.copy(updatedAt = now)

        val elapsedSeconds = Duration.between(previous, now).seconds.coerceAtLeast(0)
        val activeSeconds = if (distractingAppActive) {
            // MonitoringCoordinator measures active time with elapsedRealtime. Do not clamp that
            // trusted monotonic sample to wall-clock seconds: Duration.seconds rounds down and a
            // heartbeat arriving at ~999 ms would otherwise silently lose one watched second.
            (observedActiveSeconds ?: elapsedSeconds).coerceAtLeast(0)
        } else {
            0
        }
        val breakSeconds = (elapsedSeconds - activeSeconds).coerceAtLeast(0)
        if (activeSeconds == 0L && breakSeconds == 0L) {
            return normalized.copy(updatedAt = now)
        }
        val recovered = recover(normalized, breakSeconds, config)
        return if (activeSeconds > 0) {
            recovered.copy(
                loadSeconds = recovered.loadSeconds.saturatingAdd(activeSeconds),
                updatedAt = now,
                recoverySeconds = 0,
            )
        } else {
            recovered.copy(updatedAt = now)
        }
    }

    private fun recover(
        state: TaskDifficultyState,
        breakSeconds: Long,
        config: TaskDifficultyConfig,
    ): TaskDifficultyState {
        if (breakSeconds <= 0) return state
        val ratio = config.normalized().decayBreakMinutesPerLoadMinute.toLong()
        val recovery = state.recoverySeconds.saturatingAdd(breakSeconds)
        val loadDecrease = recovery / ratio
        val actualDecrease = loadDecrease.coerceAtMost(state.loadSeconds)
        return state.copy(
            loadSeconds = state.loadSeconds - actualDecrease,
            recoverySeconds = if (actualDecrease < loadDecrease) 0 else recovery % ratio,
        )
    }

    fun difficulty(
        state: TaskDifficultyState,
        config: TaskDifficultyConfig,
    ): TaskDifficulty {
        val normalizedConfig = config.normalized()
        val loadMinutes = normalize(state).loadSeconds / SECONDS_PER_MINUTE
        return when {
            loadMinutes >= normalizedConfig.hardThresholdMinutes -> TaskDifficulty.HARD
            loadMinutes >= normalizedConfig.mediumThresholdMinutes -> TaskDifficulty.MEDIUM
            else -> TaskDifficulty.EASY
        }
    }

    private fun normalize(state: TaskDifficultyState): TaskDifficultyState = state.copy(
        loadSeconds = state.loadSeconds.coerceAtLeast(0),
        recoverySeconds = state.recoverySeconds.coerceAtLeast(0),
    )
}

private fun Long.saturatingAdd(other: Long): Long =
    if (other > Long.MAX_VALUE - this) Long.MAX_VALUE else this + other

private const val SECONDS_PER_MINUTE = 60L

private fun Long?.orZero(): Long = this ?: 0L
