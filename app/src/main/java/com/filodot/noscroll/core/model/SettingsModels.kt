package com.filodot.noscroll.core.model

import java.time.Instant

enum class LimitPreset {
    GENTLE,
    BALANCED,
    STRICT,
    CUSTOM,
}

data class UserSettings(
    val onboardingCompleted: Boolean = false,
    val shortsGateEnabled: Boolean = true,
    val shortsIntervalMinutes: Int = DEFAULT_SHORTS_INTERVAL_MINUTES,
    val dailyLimitEnabled: Boolean = true,
    val dailyLimitMinutes: Int = DEFAULT_DAILY_LIMIT_MINUTES,
    val preset: LimitPreset = LimitPreset.BALANCED,
    val emergencyActive: Boolean = false,
    val accessibilityDisclosureAcceptedAt: Instant? = null,
    val usageDisclosureSeenAt: Instant? = null,
    val detectorRulesVersion: Int = DEFAULT_DETECTOR_RULES_VERSION,
    val settingsSchemaVersion: Int = SETTINGS_SCHEMA_VERSION,
) {
    companion object {
        const val DEFAULT_SHORTS_INTERVAL_MINUTES = 5
        const val DEFAULT_DAILY_LIMIT_MINUTES = 45
        const val DEFAULT_DETECTOR_RULES_VERSION = 1
        const val SETTINGS_SCHEMA_VERSION = 1
    }
}
