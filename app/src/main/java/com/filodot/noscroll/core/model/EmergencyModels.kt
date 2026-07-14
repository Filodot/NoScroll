package com.filodot.noscroll.core.model

import java.time.Instant

enum class EmergencyActivationSource {
    DASHBOARD,
    TASK_GATE,
    DAILY_LIMIT,
}

data class EmergencyEvent(
    val id: String,
    val reason: String,
    val activatedAt: Instant,
    val deactivatedAt: Instant? = null,
    val activationSource: EmergencyActivationSource,
    val youtubeSecondsDuring: Long = 0,
)

data class EmergencyState(
    val activeEvent: EmergencyEvent? = null,
) {
    val isActive: Boolean = activeEvent != null && activeEvent.deactivatedAt == null
}
