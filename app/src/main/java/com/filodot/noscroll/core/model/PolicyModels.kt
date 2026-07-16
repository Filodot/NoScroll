package com.filodot.noscroll.core.model

data class PermissionState(
    val accessibilityGranted: Boolean,
    val usageAccessGranted: Boolean,
)

data class PolicyInput(
    val settings: UserSettings,
    val permissions: PermissionState,
    val dailyUsage: DailyUsage,
    val gateCycle: GateCycle,
    val pendingTask: PendingTask?,
    val emergencyState: EmergencyState,
    val detectorState: ShortsDetectionState,
    val youtubeForeground: Boolean,
    val entryGatePaid: Boolean = true,
)

sealed interface PolicyDecision {
    data object Allow : PolicyDecision

    data object EmergencyBypass : PolicyDecision

    data class RequirementsMissing(
        val accessibilityMissing: Boolean,
        val usageAccessMissing: Boolean,
    ) : PolicyDecision

    data class DailyLimitReached(
        val usedSeconds: Long,
        val limitSeconds: Long,
    ) : PolicyDecision

    data class TaskGateRequired(
        val pendingTaskId: String?,
        val trigger: TaskTrigger = TaskTrigger.INTERVAL,
    ) : PolicyDecision
}

sealed interface OverlayState {
    data object Hidden : OverlayState

    data class TaskGate(val taskId: String) : OverlayState

    data class DailyLimit(val usedSeconds: Long, val limitSeconds: Long) : OverlayState

    data class EmergencyForm(val source: EmergencyActivationSource) : OverlayState
}
