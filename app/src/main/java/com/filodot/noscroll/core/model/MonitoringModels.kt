package com.filodot.noscroll.core.model

enum class ShortsDetectionState {
    UNKNOWN,
    NOT_SHORTS,
    SHORTS_CONFIRMED,
}

enum class KnownTextHint {
    SHORTS_LABEL,
    COMMENTS_ACTION,
    SHARE_ACTION,
    SUBSCRIBE_ACTION,
}

data class AccessibilityWindowEvent(
    val packageName: String,
    val eventType: Int,
    val elapsedRealtimeMillis: Long,
)

data class WindowNodeSignal(
    val viewIdResourceName: String? = null,
    val className: String? = null,
    val roleName: String? = null,
    val knownTextHints: Set<KnownTextHint> = emptySet(),
    val isScrollable: Boolean = false,
    val actionIds: Set<Int> = emptySet(),
)

data class WindowSnapshot(
    val packageName: String,
    val eventType: Int,
    val capturedAtElapsedMillis: Long,
    val rootClassName: String? = null,
    val windowWidthPx: Int? = null,
    val windowHeightPx: Int? = null,
    val nodes: List<WindowNodeSignal> = emptyList(),
)

data class DetectionResult(
    val state: ShortsDetectionState,
    val confidence: Float,
    val matchedSignalCodes: Set<String> = emptySet(),
    val rulesVersion: Int,
)

data class DeviceState(
    val screenInteractive: Boolean,
    val deviceUnlocked: Boolean,
    val foregroundPackage: String?,
)
