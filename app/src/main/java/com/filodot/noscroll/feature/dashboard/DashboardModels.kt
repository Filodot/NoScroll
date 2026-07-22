package com.filodot.noscroll.feature.dashboard

import kotlin.math.max

sealed interface ShortsLimitUiState {
    data class Enabled(
        val cycleUsedSeconds: Long,
        val intervalSeconds: Long,
        val todaySeconds: Long,
        val seenToday: Boolean = true,
        val accessLocked: Boolean = false,
        val unlockedUntilLabel: String? = null,
    ) : ShortsLimitUiState

    data object Disabled : ShortsLimitUiState
}

sealed interface DailyLimitUiState {
    data class Enabled(
        val usedSeconds: Long,
        val limitSeconds: Long,
    ) : DailyLimitUiState

    data object Disabled : DailyLimitUiState
    data object Unavailable : DailyLimitUiState
}

sealed interface InstagramLimitUiState {
    data class Enabled(
        val cycleUsedSeconds: Long,
        val intervalSeconds: Long,
        val todaySeconds: Long,
        val accessLocked: Boolean = false,
        val unlockedUntilLabel: String? = null,
    ) : InstagramLimitUiState

    data object Disabled : InstagramLimitUiState
}

data class EmergencyUiState(
    val active: Boolean = false,
    val activeSinceLabel: String? = null,
)

data class DashboardUiState(
    val dateLabel: String,
    val accessibilityEnabled: Boolean = true,
    val monitoringHealthy: Boolean = true,
    val shorts: ShortsLimitUiState = ShortsLimitUiState.Enabled(
        cycleUsedSeconds = 78,
        intervalSeconds = 300,
        todaySeconds = 720,
    ),
    val daily: DailyLimitUiState = DailyLimitUiState.Enabled(
        usedSeconds = 18 * 60,
        limitSeconds = 45 * 60,
    ),
    val instagram: InstagramLimitUiState = InstagramLimitUiState.Enabled(
        cycleUsedSeconds = 0,
        intervalSeconds = 600,
        todaySeconds = 0,
    ),
    val emergency: EmergencyUiState = EmergencyUiState(),
) {
    val protectionStatus: DashboardProtectionStatus
        get() = when {
            emergency.active -> DashboardProtectionStatus.EMERGENCY_BYPASS
            !accessibilityEnabled || !monitoringHealthy ->
                DashboardProtectionStatus.ACCESSIBILITY_ERROR
            else -> DashboardProtectionStatus.WORKING
        }

    val emergencyAvailable: Boolean
        get() = shorts !is ShortsLimitUiState.Disabled ||
            instagram !is InstagramLimitUiState.Disabled ||
            daily !is DailyLimitUiState.Disabled

    val hasUsageAccessProblem: Boolean
        get() = accessibilityEnabled && monitoringHealthy &&
            daily is DailyLimitUiState.Unavailable
}

enum class DashboardProtectionStatus {
    WORKING,
    EMERGENCY_BYPASS,
    ACCESSIBILITY_ERROR,
}

sealed interface DashboardAction {
    data object ShowHelp : DashboardAction
    data object OpenAccessibilitySettings : DashboardAction
    data object OpenUsageAccessSettings : DashboardAction
    data object OpenChallenge : DashboardAction
    data object OpenInstagramChallenge : DashboardAction
    data class SetEmergencyEnabled(val enabled: Boolean) : DashboardAction
}

data class DashboardProgress(
    val fraction: Float,
    val usedSeconds: Long,
    val limitSeconds: Long,
) {
    val remainingSeconds: Long
        get() = max(0, limitSeconds - usedSeconds)
}

internal fun progress(
    usedSeconds: Long,
    limitSeconds: Long,
): DashboardProgress {
    val safeUsed = max(0, usedSeconds)
    val safeLimit = max(1, limitSeconds)
    return DashboardProgress(
        fraction = (safeUsed.toDouble() / safeLimit.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat(),
        usedSeconds = safeUsed,
        limitSeconds = safeLimit,
    )
}

internal fun formatCountdown(seconds: Long): String {
    val safeSeconds = max(0, seconds)
    val minutes = safeSeconds / 60
    val remainder = safeSeconds % 60
    return minutes.toString().padStart(2, '0') + ":" +
        remainder.toString().padStart(2, '0')
}

internal fun wholeMinutes(seconds: Long): Long = max(0, seconds) / 60
