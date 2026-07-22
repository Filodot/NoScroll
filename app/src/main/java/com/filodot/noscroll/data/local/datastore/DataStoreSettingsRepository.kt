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
                preferences[Keys.DIFFICULTY_MEDIUM_THRESHOLD_MINUTES] =
                    settings.difficultyMediumThresholdMinutes
                preferences[Keys.DIFFICULTY_HARD_THRESHOLD_MINUTES] =
                    settings.difficultyHardThresholdMinutes
                preferences[Keys.DIFFICULTY_DECAY_BREAK_MINUTES] =
                    settings.difficultyDecayBreakMinutes
                preferences[Keys.ENABLED_TASK_TYPES] = settings.enabledTaskTypes
                    .sortedBy(TaskType::ordinal)
                    .joinToString(",", transform = TaskType::name)
                preferences[Keys.PRESET] = settings.preset.name
                preferences[Keys.EMERGENCY_ACTIVE] = settings.emergencyActive
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
    val DIFFICULTY_MEDIUM_THRESHOLD_MINUTES =
        intPreferencesKey("difficulty_medium_threshold_minutes")
    val DIFFICULTY_HARD_THRESHOLD_MINUTES =
        intPreferencesKey("difficulty_hard_threshold_minutes")
    val DIFFICULTY_DECAY_BREAK_MINUTES = intPreferencesKey("difficulty_decay_break_minutes")
    val ENABLED_TASK_TYPES = stringPreferencesKey("enabled_task_types")
    val PRESET = stringPreferencesKey("preset")
    val EMERGENCY_ACTIVE = booleanPreferencesKey("emergency_active")
    val ACCESSIBILITY_DISCLOSURE_ACCEPTED_AT =
        longPreferencesKey("accessibility_disclosure_accepted_at_epoch_millis")
    val USAGE_DISCLOSURE_SEEN_AT =
        longPreferencesKey("usage_disclosure_seen_at_epoch_millis")
    val DETECTOR_RULES_VERSION = intPreferencesKey("detector_rules_version")
    val SETTINGS_SCHEMA_VERSION = intPreferencesKey("settings_schema_version")
}

private fun preferencesToSettings(preferences: Preferences): UserSettings {
    val defaults = UserSettings()
    return UserSettings(
        onboardingCompleted = preferences[Keys.ONBOARDING_COMPLETED]
            ?: defaults.onboardingCompleted,
        shortsGateEnabled = preferences[Keys.SHORTS_GATE_ENABLED] ?: defaults.shortsGateEnabled,
        shortsIntervalMinutes = preferences[Keys.SHORTS_INTERVAL_MINUTES]
            ?: defaults.shortsIntervalMinutes,
        dailyLimitEnabled = preferences[Keys.DAILY_LIMIT_ENABLED] ?: defaults.dailyLimitEnabled,
        dailyLimitMinutes = preferences[Keys.DAILY_LIMIT_MINUTES] ?: defaults.dailyLimitMinutes,
        instagramGateEnabled = preferences[Keys.INSTAGRAM_GATE_ENABLED]
            ?: defaults.instagramGateEnabled,
        instagramIntervalMinutes = preferences[Keys.INSTAGRAM_INTERVAL_MINUTES]
            ?: defaults.instagramIntervalMinutes,
        difficultyMediumThresholdMinutes =
            preferences[Keys.DIFFICULTY_MEDIUM_THRESHOLD_MINUTES]
                ?: defaults.difficultyMediumThresholdMinutes,
        difficultyHardThresholdMinutes = preferences[Keys.DIFFICULTY_HARD_THRESHOLD_MINUTES]
            ?: defaults.difficultyHardThresholdMinutes,
        difficultyDecayBreakMinutes = preferences[Keys.DIFFICULTY_DECAY_BREAK_MINUTES]
            ?: defaults.difficultyDecayBreakMinutes,
        enabledTaskTypes = preferences[Keys.ENABLED_TASK_TYPES]
            ?.split(',')
            ?.mapNotNull { stored -> TaskType.entries.firstOrNull { it.name == stored } }
            ?.toSet()
            ?.takeIf(Set<TaskType>::isNotEmpty)
            ?: defaults.enabledTaskTypes,
        preset = preferences[Keys.PRESET]
            ?.let { stored -> enumValues<LimitPreset>().firstOrNull { it.name == stored } }
            ?: defaults.preset,
        emergencyActive = preferences[Keys.EMERGENCY_ACTIVE] ?: defaults.emergencyActive,
        accessibilityDisclosureAcceptedAt =
            preferences[Keys.ACCESSIBILITY_DISCLOSURE_ACCEPTED_AT]?.let(Instant::ofEpochMilli),
        usageDisclosureSeenAt =
            preferences[Keys.USAGE_DISCLOSURE_SEEN_AT]?.let(Instant::ofEpochMilli),
        detectorRulesVersion = preferences[Keys.DETECTOR_RULES_VERSION]
            ?: defaults.detectorRulesVersion,
        settingsSchemaVersion = preferences[Keys.SETTINGS_SCHEMA_VERSION]
            ?: defaults.settingsSchemaVersion,
    )
}

private fun MutablePreferences.writeInstant(
    key: Preferences.Key<Long>,
    value: Instant?,
) {
    if (value == null) remove(key) else this[key] = value.toEpochMilli()
}
