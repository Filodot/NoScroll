package com.filodot.noscroll.core.policy

import com.filodot.noscroll.core.model.PolicyDecision
import com.filodot.noscroll.core.model.PolicyInput
import com.filodot.noscroll.core.model.ShortsDetectionState
import com.filodot.noscroll.core.model.TaskTrigger

/**
 * Produces the single enforcement decision for the current immutable snapshot.
 *
 * The engine is intentionally pure: it does not mutate counters, create tasks, control overlays,
 * or call Android APIs. Callers serialize input updates and translate the returned decision into
 * side effects.
 *
 * Priority is fixed as Emergency -> required permissions -> foreground -> daily -> task -> allow.
 * Usage Access is required only for daily enforcement; when it is missing, the daily rule is
 * skipped while the Shorts gate remains available. Non-positive persisted limits fail open until
 * the settings domain repairs them.
 */
class PolicyEngine {
    fun decide(input: PolicyInput): PolicyDecision {
        if (input.settings.emergencyActive || input.emergencyState.isActive) {
            return PolicyDecision.EmergencyBypass
        }

        if (!input.permissions.accessibilityGranted) {
            return PolicyDecision.RequirementsMissing(
                accessibilityMissing = true,
                usageAccessMissing = input.settings.dailyLimitEnabled &&
                    !input.permissions.usageAccessGranted,
            )
        }

        if (!input.youtubeForeground) {
            return PolicyDecision.Allow
        }

        val dailyLimitSeconds = input.settings.dailyLimitMinutes.toPositiveSeconds()
        if (
            input.settings.dailyLimitEnabled &&
            input.permissions.usageAccessGranted &&
            dailyLimitSeconds != null &&
            input.dailyUsage.youtubeSeconds >= dailyLimitSeconds
        ) {
            return PolicyDecision.DailyLimitReached(
                usedSeconds = input.dailyUsage.youtubeSeconds,
                limitSeconds = dailyLimitSeconds,
            )
        }

        if (
            input.settings.shortsGateEnabled &&
            input.detectorState == ShortsDetectionState.SHORTS_CONFIRMED
        ) {
            val pendingTaskId = input.pendingTask?.id ?: input.gateCycle.pendingTaskId
            if (pendingTaskId != null) {
                return PolicyDecision.TaskGateRequired(
                    pendingTaskId = pendingTaskId,
                    trigger = input.pendingTask?.trigger ?: TaskTrigger.INTERVAL,
                )
            }
            if (!input.entryGatePaid) {
                return PolicyDecision.TaskGateRequired(
                    pendingTaskId = null,
                    trigger = TaskTrigger.ENTRY,
                )
            }
            val shortsLimitSeconds = input.settings.shortsIntervalMinutes.toPositiveSeconds()
            val intervalReached = shortsLimitSeconds != null &&
                input.gateCycle.usedSeconds >= shortsLimitSeconds

            if (intervalReached) {
                return PolicyDecision.TaskGateRequired(
                    pendingTaskId = null,
                    trigger = TaskTrigger.INTERVAL,
                )
            }
        }

        return PolicyDecision.Allow
    }
}

private fun Int.toPositiveSeconds(): Long? =
    takeIf { it > 0 }?.let { minutes -> minutes.toLong() * SECONDS_PER_MINUTE }

private const val SECONDS_PER_MINUTE = 60L
