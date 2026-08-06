package com.filodot.noscroll.core.model

import com.filodot.noscroll.core.focus.FocusAppCatalog
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
    val instagramGateEnabled: Boolean = true,
    val instagramIntervalMinutes: Int = DEFAULT_INSTAGRAM_INTERVAL_MINUTES,
    val youtubeGateEnabled: Boolean = false,
    val youtubeIntervalMinutes: Int = DEFAULT_YOUTUBE_INTERVAL_MINUTES,
    val pinterestGateEnabled: Boolean = false,
    val pinterestIntervalMinutes: Int = DEFAULT_PINTEREST_INTERVAL_MINUTES,
    val chromeGateEnabled: Boolean = false,
    val chromeIntervalMinutes: Int = DEFAULT_CHROME_INTERVAL_MINUTES,
    val difficultyMediumThresholdMinutes: Int = DEFAULT_MEDIUM_THRESHOLD_MINUTES,
    val difficultyHardThresholdMinutes: Int = DEFAULT_HARD_THRESHOLD_MINUTES,
    val difficultyDecayBreakMinutes: Int = DEFAULT_DIFFICULTY_DECAY_BREAK_MINUTES,
    val enabledTaskTypes: Set<TaskType> = setOf(TaskType.ARITHMETIC),
    val selectedLearningCourseIds: Set<String> = emptySet(),
    val preset: LimitPreset = LimitPreset.BALANCED,
    val emergencyActive: Boolean = false,
    val focusDurationMinutes: Int = DEFAULT_FOCUS_DURATION_MINUTES,
    val focusBlockedPackages: Set<String> = FocusAppCatalog.defaultPackages,
    val focusStartedAt: Instant? = null,
    val focusEndsAt: Instant? = null,
    val accessibilityDisclosureAcceptedAt: Instant? = null,
    val usageDisclosureSeenAt: Instant? = null,
    val detectorRulesVersion: Int = DEFAULT_DETECTOR_RULES_VERSION,
    val settingsSchemaVersion: Int = SETTINGS_SCHEMA_VERSION,
) {
    companion object {
        const val DEFAULT_SHORTS_INTERVAL_MINUTES = 5
        const val DEFAULT_DAILY_LIMIT_MINUTES = 45
        const val DEFAULT_INSTAGRAM_INTERVAL_MINUTES = 10
        const val DEFAULT_YOUTUBE_INTERVAL_MINUTES = 15
        const val DEFAULT_PINTEREST_INTERVAL_MINUTES = 10
        const val DEFAULT_CHROME_INTERVAL_MINUTES = 20
        const val DEFAULT_MEDIUM_THRESHOLD_MINUTES = 10
        const val DEFAULT_HARD_THRESHOLD_MINUTES = 25
        const val DEFAULT_DIFFICULTY_DECAY_BREAK_MINUTES = 5
        const val DEFAULT_FOCUS_DURATION_MINUTES = 30
        const val DEFAULT_DETECTOR_RULES_VERSION = 1
        const val SETTINGS_SCHEMA_VERSION = 5
    }
}
