package com.filodot.noscroll.core.model

import java.time.Instant
import java.time.LocalDate

data class DailyUsage(
    val localDate: LocalDate,
    val youtubeSeconds: Long = 0,
    val shortsSeconds: Long = 0,
    val emergencyYoutubeSeconds: Long = 0,
    val gatesShown: Int = 0,
    val tasksSolved: Int = 0,
    val taskExits: Int = 0,
    val lastUpdatedElapsedMillis: Long? = null,
    val updatedAt: Instant,
)

data class GateCycle(
    val id: String = CURRENT_GATE_CYCLE_ID,
    val localDate: LocalDate,
    val usedSeconds: Long = 0,
    val pendingTaskId: String? = null,
    val updatedAt: Instant,
    val intervalBlockStreak: Int = 0,
    val lastIntervalBlockAt: Instant? = null,
) {
    companion object {
        const val CURRENT_GATE_CYCLE_ID = "current"
    }
}

enum class UsageEventType {
    ACTIVITY_RESUMED,
    ACTIVITY_PAUSED,
}

data class NormalizedUsageEvent(
    val packageName: String,
    val type: UsageEventType,
    val timestamp: Instant,
)
