package com.filodot.noscroll.feature.dashboard

import com.filodot.noscroll.core.model.TaskTarget
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

sealed interface AppLimitUiState {
    data class Enabled(
        val cycleUsedSeconds: Long,
        val intervalSeconds: Long,
        val todaySeconds: Long,
        val accessLocked: Boolean = false,
        val unlockedUntilLabel: String? = null,
    ) : AppLimitUiState

    data object Disabled : AppLimitUiState
}

data class EmergencyUiState(
    val active: Boolean = false,
    val activeSinceLabel: String? = null,
)

data class FocusAppUi(
    val packageName: String,
    val label: String,
)

data class FocusModeUiState(
    val active: Boolean = false,
    val endsAtLabel: String? = null,
    val remainingMinutes: Long = 0,
    val durationMinutes: Int = 30,
    val selectedPackages: Set<String> = emptySet(),
    val blockedAppLabels: List<String> = emptyList(),
    val availableApps: List<FocusAppUi> = emptyList(),
)

enum class DashboardMonitoringState {
    RUNNING,
    STARTING,
    RECOVERING,
    DISCONNECTED,
}

data class DashboardUiState(
    val dateLabel: String,
    val accessibilityEnabled: Boolean = true,
    val monitoringState: DashboardMonitoringState = DashboardMonitoringState.RUNNING,
    val shorts: ShortsLimitUiState = ShortsLimitUiState.Enabled(
        cycleUsedSeconds = 78,
        intervalSeconds = 300,
        todaySeconds = 720,
    ),
    val daily: DailyLimitUiState = DailyLimitUiState.Enabled(
        usedSeconds = 18 * 60,
        limitSeconds = 45 * 60,
    ),
    val youtube: AppLimitUiState = AppLimitUiState.Disabled,
    val instagram: AppLimitUiState = AppLimitUiState.Enabled(
        cycleUsedSeconds = 0,
        intervalSeconds = 600,
        todaySeconds = 0,
    ),
    val pinterest: AppLimitUiState = AppLimitUiState.Disabled,
    val chrome: AppLimitUiState = AppLimitUiState.Disabled,
    val emergency: EmergencyUiState = EmergencyUiState(),
    val focusMode: FocusModeUiState = FocusModeUiState(),
) {
    val protectionStatus: DashboardProtectionStatus
        get() = when {
            emergency.active -> DashboardProtectionStatus.EMERGENCY_BYPASS
            !accessibilityEnabled || monitoringState == DashboardMonitoringState.DISCONNECTED ->
                DashboardProtectionStatus.ACCESSIBILITY_ERROR
            monitoringState == DashboardMonitoringState.STARTING ||
                monitoringState == DashboardMonitoringState.RECOVERING ->
                DashboardProtectionStatus.MONITORING_RECOVERING
            else -> DashboardProtectionStatus.WORKING
        }

    val emergencyAvailable: Boolean
        get() = shorts !is ShortsLimitUiState.Disabled ||
            youtube !is AppLimitUiState.Disabled ||
            instagram !is AppLimitUiState.Disabled ||
            pinterest !is AppLimitUiState.Disabled ||
            chrome !is AppLimitUiState.Disabled ||
            daily !is DailyLimitUiState.Disabled ||
            focusMode.active

    val hasUsageAccessProblem: Boolean
        get() = accessibilityEnabled && monitoringState == DashboardMonitoringState.RUNNING &&
            daily is DailyLimitUiState.Unavailable
}

enum class DashboardProtectionStatus {
    WORKING,
    EMERGENCY_BYPASS,
    MONITORING_RECOVERING,
    ACCESSIBILITY_ERROR,
}

sealed interface DashboardAction {
    data object ShowHelp : DashboardAction
    data object OpenAccessibilitySettings : DashboardAction
    data object OpenUsageAccessSettings : DashboardAction
    data object OpenDiagnostics : DashboardAction
    data object OpenChallenge : DashboardAction
    data class OpenAppChallenge(val target: TaskTarget) : DashboardAction
    data class StartFocusMode(
        val durationMinutes: Int,
        val packageNames: Set<String>,
    ) : DashboardAction
    data object OpenFocusEmergency : DashboardAction
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
