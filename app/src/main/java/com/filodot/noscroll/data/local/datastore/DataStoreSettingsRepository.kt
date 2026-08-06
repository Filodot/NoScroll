package com.filodot.noscroll.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.filodot.noscroll.core.contracts.SettingsRepository
import com.filodot.noscroll.core.focus.FocusAppCatalog
import com.filodot.noscroll.core.model.LimitPreset
import com.filodot.noscroll.core.model.TaskType
import com.filodot.noscroll.core.model.UserSettings
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : SettingsRepository {
    private val mutableInitialized = MutableStateFlow(false)
    private val mutableSettings = MutableStateFlow(UserSettings())
    private val saveMutex = Mutex()
    val initialized: StateFlow<Boolean> = mutableInitialized.asStateFlow()

    override val settings: StateFlow<UserSettings> = mutableSettings.asStateFlow()

    init {
        scope.launch {
            dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }
                .map(::preferencesToSettings)
                .onEach { mutableInitialized.value = true }
                .collect(mutableSettings::emit)
        }
    }

    override suspend fun save(settings: UserSettings) = saveMutex.withLock {
        val previous = mutableSettings.value
        mutableSettings.value = settings
        try {
            dataStore.edit { preferences ->
                preferences[Keys.ONBOARDING_COMPLETED] = settings.onboardingCompleted
                preferences[Keys.SHORTS_GATE_ENABLED] = settings.shortsGateEnabled
                preferences[Keys.SHORTS_INTERVAL_MINUTES] = settings.shortsIntervalMinutes
                preferences[Keys.DAILY_LIMIT_ENABLED] = settings.dailyLimitEnabled
                preferences[Keys.DAILY_LIMIT_MINUTES] = settings.dailyLimitMinutes
                preferences[Keys.INSTAGRAM_GATE_ENABLED] = settings.instagramGateEnabled
                preferences[Keys.INSTAGRAM_INTERVAL_MINUTES] = settings.instagramIntervalMinutes
                preferences[Keys.YOUTUBE_GATE_ENABLED] = settings.youtubeGateEnabled
                preferences[Keys.YOUTUBE_INTERVAL_MINUTES] = settings.youtubeIntervalMinutes
                preferences[Keys.PINTEREST_GATE_ENABLED] = settings.pinterestGateEnabled
                preferences[Keys.PINTEREST_INTERVAL_MINUTES] = settings.pinterestIntervalMinutes
                preferences[Keys.CHROME_GATE_ENABLED] = settings.chromeGateEnabled
                preferences[Keys.CHROME_INTERVAL_MINUTES] = settings.chromeIntervalMinutes
                preferences[Keys.DIFFICULTY_MEDIUM_THRESHOLD_MINUTES] =
                    settings.difficultyMediumThresholdMinutes
                preferences[Keys.DIFFICULTY_HARD_THRESHOLD_MINUTES] =
                    settings.difficultyHardThresholdMinutes
                preferences[Keys.DIFFICULTY_DECAY_BREAK_MINUTES] =
                    settings.difficultyDecayBreakMinutes
                preferences[Keys.ENABLED_TASK_TYPES] = settings.enabledTaskTypes
                    .sortedBy(TaskType::ordinal)
                    .joinToString(",", transform = TaskType::name)
                preferences[Keys.SELECTED_LEARNING_COURSE_IDS] =
                    settings.selectedLearningCourseIds.sorted().joinToString(",")
                preferences[Keys.PRESET] = settings.preset.name
                preferences[Keys.EMERGENCY_ACTIVE] = settings.emergencyActive
                preferences[Keys.FOCUS_DURATION_MINUTES] = settings.focusDurationMinutes
                preferences[Keys.FOCUS_BLOCKED_PACKAGES] = FocusAppCatalog
                    .sanitize(settings.focusBlockedPackages)
                    .sorted()
                    .joinToString(",")
                preferences.writeInstant(Keys.FOCUS_STARTED_AT, settings.focusStartedAt)
                preferences.writeInstant(Keys.FOCUS_ENDS_AT, settings.focusEndsAt)
                preferences[Keys.DETECTOR_RULES_VERSION] = settings.detectorRulesVersion
                preferences[Keys.SETTINGS_SCHEMA_VERSION] = settings.settingsSchemaVersion
                preferences.writeInstant(
                    Keys.ACCESSIBILITY_DISCLOSURE_ACCEPTED_AT,
                    settings.accessibilityDisclosureAcceptedAt,
                )
                preferences.writeInstant(
                    Keys.USAGE_DISCLOSURE_SEEN_AT,
                    settings.usageDisclosureSeenAt,
                )
            }
        } catch (error: Exception) {
            mutableSettings.value = previous
            throw error
        }
        Unit
    }

    companion object {
        const val FILE_NAME = "user_settings.preferences_pb"

        fun createDataStore(context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { context.preferencesDataStoreFile(FILE_NAME) },
            )
    }
}

private object Keys {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val SHORTS_GATE_ENABLED = booleanPreferencesKey("shorts_gate_enabled")
    val SHORTS_INTERVAL_MINUTES = intPreferencesKey("shorts_interval_minutes")
    val DAILY_LIMIT_ENABLED = booleanPreferencesKey("daily_limit_enabled")
    val DAILY_LIMIT_MINUTES = intPreferencesKey("daily_limit_minutes")
    val INSTAGRAM_GATE_ENABLED = booleanPreferencesKey("instagram_gate_enabled")
    val INSTAGRAM_INTERVAL_MINUTES = intPreferencesKey("instagram_interval_minutes")
    val YOUTUBE_GATE_ENABLED = booleanPreferencesKey("youtube_gate_enabled")
    val YOUTUBE_INTERVAL_MINUTES = intPreferencesKey("youtube_interval_minutes")
    val PINTEREST_GATE_ENABLED = booleanPreferencesKey("pinterest_gate_enabled")
    val PINTEREST_INTERVAL_MINUTES = intPreferencesKey("pinterest_interval_minutes")
    val CHROME_GATE_ENABLED = booleanPreferencesKey("chrome_gate_enabled")
    val CHROME_INTERVAL_MINUTES = intPreferencesKey("chrome_interval_minutes")
    val DIFFICULTY_MEDIUM_THRESHOLD_MINUTES =
        intPreferencesKey("difficulty_medium_threshold_minutes")
    val DIFFICULTY_HARD_THRESHOLD_MINUTES =
        intPreferencesKey("difficulty_hard_threshold_minutes")
    val DIFFICULTY_DECAY_BREAK_MINUTES = intPreferencesKey("difficulty_decay_break_minutes")
    val ENABLED_TASK_TYPES = stringPreferencesKey("enabled_task_types")
    val SELECTED_LEARNING_COURSE_IDS = stringPreferencesKey("selected_learning_course_ids")
    val PRESET = stringPreferencesKey("preset")
    val EMERGENCY_ACTIVE = booleanPreferencesKey("emergency_active")
    val FOCUS_DURATION_MINUTES = intPreferencesKey("focus_duration_minutes")
    val FOCUS_BLOCKED_PACKAGES = stringPreferencesKey("focus_blocked_packages")
    val FOCUS_STARTED_AT = longPreferencesKey("focus_started_at_epoch_millis")
    val FOCUS_ENDS_AT = longPreferencesKey("focus_ends_at_epoch_millis")
    val ACCESSIBILITY_DISCLOSURE_ACCEPTED_AT =
        longPreferencesKey("accessibility_disclosure_accepted_at_epoch_millis")
    val USAGE_DISCLOSURE_SEEN_AT =
        longPreferencesKey("usage_disclosure_seen_at_epoch_millis")
    val DETECTOR_RULES_VERSION = intPreferencesKey("detector_rules_version")
    val SETTINGS_SCHEMA_VERSION = intPreferencesKey("settings_schema_version")
}

private fun preferencesToSettings(preferences: Preferences): UserSettings {
    val defaults = UserSettings()
    val shortsInterval = preferences[Keys.SHORTS_INTERVAL_MINUTES]
        ?.takeIf { it in 1..30 }
        ?: defaults.shortsIntervalMinutes
    val dailyLimit = preferences[Keys.DAILY_LIMIT_MINUTES]
        ?.takeIf { it in 10..240 && it % 5 == 0 }
        ?: defaults.dailyLimitMinutes
    val instagramInterval = preferences[Keys.INSTAGRAM_INTERVAL_MINUTES]
        ?.takeIf { it in 1..30 }
        ?: defaults.instagramIntervalMinutes
    val youtubeInterval = preferences[Keys.YOUTUBE_INTERVAL_MINUTES]
        ?.takeIf { it in 1..30 }
        ?: defaults.youtubeIntervalMinutes
    val pinterestInterval = preferences[Keys.PINTEREST_INTERVAL_MINUTES]
        ?.takeIf { it in 1..30 }
        ?: defaults.pinterestIntervalMinutes
    val chromeInterval = preferences[Keys.CHROME_INTERVAL_MINUTES]
        ?.takeIf { it in 1..30 }
        ?: defaults.chromeIntervalMinutes
    val mediumThreshold = preferences[Keys.DIFFICULTY_MEDIUM_THRESHOLD_MINUTES]
        ?.takeIf { it in 1..120 }
        ?: defaults.difficultyMediumThresholdMinutes
    val hardThreshold = preferences[Keys.DIFFICULTY_HARD_THRESHOLD_MINUTES]
        ?.takeIf { it in (mediumThreshold + 1)..240 }
        ?: defaults.difficultyHardThresholdMinutes
            .coerceAtLeast(mediumThreshold + 1)
            .coerceAtMost(240)
    val storedFocusStartedAt = preferences.readInstant(Keys.FOCUS_STARTED_AT)
    val storedFocusEndsAt = preferences.readInstant(Keys.FOCUS_ENDS_AT)
    val validFocusWindow = storedFocusStartedAt != null && storedFocusEndsAt != null &&
        storedFocusEndsAt.isAfter(storedFocusStartedAt) &&
        java.time.Duration.between(storedFocusStartedAt, storedFocusEndsAt).toMinutes() in 5..720
    return UserSettings(
        onboardingCompleted = preferences[Keys.ONBOARDING_COMPLETED]
            ?: defaults.onboardingCompleted,
        shortsGateEnabled = preferences[Keys.SHORTS_GATE_ENABLED] ?: defaults.shortsGateEnabled,
        shortsIntervalMinutes = shortsInterval,
        dailyLimitEnabled = preferences[Keys.DAILY_LIMIT_ENABLED] ?: defaults.dailyLimitEnabled,
        dailyLimitMinutes = dailyLimit,
        instagramGateEnabled = preferences[Keys.INSTAGRAM_GATE_ENABLED]
            ?: defaults.instagramGateEnabled,
        instagramIntervalMinutes = instagramInterval,
        youtubeGateEnabled = preferences[Keys.YOUTUBE_GATE_ENABLED]
            ?: defaults.youtubeGateEnabled,
        youtubeIntervalMinutes = youtubeInterval,
        pinterestGateEnabled = preferences[Keys.PINTEREST_GATE_ENABLED]
            ?: defaults.pinterestGateEnabled,
        pinterestIntervalMinutes = pinterestInterval,
        chromeGateEnabled = preferences[Keys.CHROME_GATE_ENABLED]
            ?: defaults.chromeGateEnabled,
        chromeIntervalMinutes = chromeInterval,
        difficultyMediumThresholdMinutes = mediumThreshold,
        difficultyHardThresholdMinutes = hardThreshold,
        difficultyDecayBreakMinutes = preferences[Keys.DIFFICULTY_DECAY_BREAK_MINUTES]
            ?.takeIf { it in 1..30 }
            ?: defaults.difficultyDecayBreakMinutes,
        enabledTaskTypes = preferences[Keys.ENABLED_TASK_TYPES]
            ?.split(',')
            ?.mapNotNull { stored -> TaskType.entries.firstOrNull { it.name == stored } }
            ?.toSet()
            ?.takeIf(Set<TaskType>::isNotEmpty)
            ?: defaults.enabledTaskTypes,
        selectedLearningCourseIds = preferences[Keys.SELECTED_LEARNING_COURSE_IDS]
            ?.split(',')
            ?.map(String::trim)
            ?.filter { it.length in 1..200 }
            ?.take(100)
            ?.toSet()
            ?: defaults.selectedLearningCourseIds,
        preset = preferences[Keys.PRESET]
            ?.let { stored -> enumValues<LimitPreset>().firstOrNull { it.name == stored } }
            ?: defaults.preset,
        emergencyActive = preferences[Keys.EMERGENCY_ACTIVE] ?: defaults.emergencyActive,
        focusDurationMinutes = preferences[Keys.FOCUS_DURATION_MINUTES]
            ?.takeIf { it in 5..720 && it % 5 == 0 }
            ?: defaults.focusDurationMinutes,
        focusBlockedPackages = preferences[Keys.FOCUS_BLOCKED_PACKAGES]
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toSet()
            ?.let(FocusAppCatalog::sanitize)
            ?.takeIf(Set<String>::isNotEmpty)
            ?: defaults.focusBlockedPackages,
        focusStartedAt = storedFocusStartedAt.takeIf { validFocusWindow },
        focusEndsAt = storedFocusEndsAt.takeIf { validFocusWindow },
        accessibilityDisclosureAcceptedAt =
            preferences.readInstant(Keys.ACCESSIBILITY_DISCLOSURE_ACCEPTED_AT),
        usageDisclosureSeenAt =
            preferences.readInstant(Keys.USAGE_DISCLOSURE_SEEN_AT),
        detectorRulesVersion = preferences[Keys.DETECTOR_RULES_VERSION]
            ?.takeIf { it > 0 }
            ?: defaults.detectorRulesVersion,
        settingsSchemaVersion = preferences[Keys.SETTINGS_SCHEMA_VERSION]
            ?.takeIf { it > 0 }
            ?.coerceAtLeast(defaults.settingsSchemaVersion)
            ?: defaults.settingsSchemaVersion,
    )
}

private fun Preferences.readInstant(key: Preferences.Key<Long>): Instant? =
    get(key)?.let { epochMillis -> runCatching { Instant.ofEpochMilli(epochMillis) }.getOrNull() }

private fun MutablePreferences.writeInstant(
    key: Preferences.Key<Long>,
    value: Instant?,
) {
    if (value == null) remove(key) else this[key] = value.toEpochMilli()
}
