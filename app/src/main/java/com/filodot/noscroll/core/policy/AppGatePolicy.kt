package com.filodot.noscroll.core.policy

import com.filodot.noscroll.core.model.TaskTrigger
import java.time.Instant

data class AppGateInput(
    val enabled: Boolean,
    val accessibilityGranted: Boolean,
    val bypassActive: Boolean,
    val intervalMinutes: Int,
    val usedSeconds: Long,
    val cooldownUntil: Instant?,
    val pendingTrigger: TaskTrigger? = null,
)

sealed interface AppGateDecision {
    data object Allow : AppGateDecision
    data class RequireTask(val trigger: TaskTrigger) : AppGateDecision
}

/** Pure gate shared by whole-app restrictions for YouTube, Instagram, Pinterest and Chrome. */
class AppGatePolicy {
    fun decide(input: AppGateInput, now: Instant): AppGateDecision {
        if (!input.enabled || !input.accessibilityGranted || input.bypassActive) {
            return AppGateDecision.Allow
        }
        input.pendingTrigger?.let { return AppGateDecision.RequireTask(it) }
        val intervalSeconds = input.intervalMinutes
            .takeIf { it > 0 }
            ?.toLong()
            ?.times(SECONDS_PER_MINUTE)
            ?: return AppGateDecision.Allow
        if (input.usedSeconds.coerceAtLeast(0) >= intervalSeconds) {
            return AppGateDecision.RequireTask(TaskTrigger.INTERVAL)
        }
        if (input.cooldownUntil?.isAfter(now) != true) {
            return AppGateDecision.RequireTask(TaskTrigger.ENTRY)
        }
        return AppGateDecision.Allow
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
