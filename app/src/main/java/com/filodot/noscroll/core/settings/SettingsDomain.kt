package com.filodot.noscroll.core.settings

import com.filodot.noscroll.core.contracts.SettingsRepository
import com.filodot.noscroll.core.model.LimitPreset
import com.filodot.noscroll.core.model.UserSettings

data class LimitPresetDefinition(
    val preset: LimitPreset,
    val shortsIntervalMinutes: Int?,
    val dailyLimitMinutes: Int?,
    val recommended: Boolean = false,
)

object LimitPresets {
    val gentle = LimitPresetDefinition(
        preset = LimitPreset.GENTLE,
        shortsIntervalMinutes = 10,
        dailyLimitMinutes = 90,
    )
    val balanced = LimitPresetDefinition(
        preset = LimitPreset.BALANCED,
        shortsIntervalMinutes = 5,
        dailyLimitMinutes = 45,
        recommended = true,
    )
    val strict = LimitPresetDefinition(
        preset = LimitPreset.STRICT,
        shortsIntervalMinutes = 2,
        dailyLimitMinutes = 20,
    )
    val custom = LimitPresetDefinition(
        preset = LimitPreset.CUSTOM,
        shortsIntervalMinutes = null,
        dailyLimitMinutes = null,
    )

    val all: List<LimitPresetDefinition> = listOf(gentle, balanced, strict, custom)

    fun definition(preset: LimitPreset): LimitPresetDefinition =
        all.first { it.preset == preset }
}

sealed interface SettingsValidationError {
    data class ShortsIntervalOutOfRange(val value: Int) : SettingsValidationError

    data class DailyLimitOutOfRange(val value: Int) : SettingsValidationError

    data class DailyLimitStepMismatch(val value: Int) : SettingsValidationError
}

enum class SettingsWarning {
    DAILY_LIMIT_BELOW_SHORTS_INTERVAL,
}

data class SettingsValidation(
    val errors: List<SettingsValidationError> = emptyList(),
    val warnings: Set<SettingsWarning> = emptySet(),
) {
    val isValid: Boolean get() = errors.isEmpty()
}

enum class SettingsSummaryVariant {
    BOTH_ENABLED,
    SHORTS_ONLY,
    DAILY_ONLY,
    ALL_DISABLED,
}

/** Semantic copy model rendered into localized summary text by the UI layer. */
data class SettingsSummaryTextModel(
    val variant: SettingsSummaryVariant,
    val shortsIntervalMinutes: Int?,
    val dailyLimitMinutes: Int?,
    val warnings: Set<SettingsWarning>,
)

enum class SettingsCommand {
    RECALCULATE_SHORTS_CYCLE,
    RECALCULATE_DAILY_LIMIT,
    REEVALUATE_POLICY,
}

data class SettingsEvaluation(
    val settings: UserSettings,
    val validation: SettingsValidation,
    val summary: SettingsSummaryTextModel,
)

data class SettingsChangeResult(
    val evaluation: SettingsEvaluation,
    val persisted: Boolean,
    val commands: Set<SettingsCommand> = emptySet(),
)

object SettingsValidator {
    fun evaluate(settings: UserSettings): SettingsEvaluation {
        val errors = buildList {
            if (settings.shortsIntervalMinutes !in SHORTS_INTERVAL_RANGE) {
                add(SettingsValidationError.ShortsIntervalOutOfRange(settings.shortsIntervalMinutes))
            }
            if (settings.dailyLimitMinutes !in DAILY_LIMIT_RANGE) {
                add(SettingsValidationError.DailyLimitOutOfRange(settings.dailyLimitMinutes))
            } else if (settings.dailyLimitMinutes % DAILY_LIMIT_STEP_MINUTES != 0) {
                add(SettingsValidationError.DailyLimitStepMismatch(settings.dailyLimitMinutes))
            }
        }
        val warnings = buildSet {
            if (
                settings.shortsGateEnabled &&
                settings.dailyLimitEnabled &&
                settings.dailyLimitMinutes < settings.shortsIntervalMinutes
            ) {
                add(SettingsWarning.DAILY_LIMIT_BELOW_SHORTS_INTERVAL)
            }
        }
        val variant = when {
            settings.shortsGateEnabled && settings.dailyLimitEnabled ->
                SettingsSummaryVariant.BOTH_ENABLED

            settings.shortsGateEnabled -> SettingsSummaryVariant.SHORTS_ONLY
            settings.dailyLimitEnabled -> SettingsSummaryVariant.DAILY_ONLY
            else -> SettingsSummaryVariant.ALL_DISABLED
        }

        return SettingsEvaluation(
            settings = settings,
            validation = SettingsValidation(errors = errors, warnings = warnings),
            summary = SettingsSummaryTextModel(
                variant = variant,
                shortsIntervalMinutes = settings.shortsIntervalMinutes
                    .takeIf { settings.shortsGateEnabled },
                dailyLimitMinutes = settings.dailyLimitMinutes
                    .takeIf { settings.dailyLimitEnabled },
                warnings = warnings,
            ),
        )
    }
}

/** Validates and persists settings edits while emitting immediate recalculation commands. */
class SettingsDomain(
    private val repository: SettingsRepository,
) {
    fun current(): SettingsEvaluation = SettingsValidator.evaluate(repository.settings.value)

    suspend fun applyPreset(preset: LimitPreset): SettingsChangeResult =
        change { current ->
            val definition = LimitPresets.definition(preset)
            if (preset == LimitPreset.CUSTOM) {
                current.copy(preset = LimitPreset.CUSTOM)
            } else {
                current.copy(
                    shortsIntervalMinutes = requireNotNull(definition.shortsIntervalMinutes),
                    dailyLimitMinutes = requireNotNull(definition.dailyLimitMinutes),
                    preset = preset,
                )
            }
        }

    suspend fun setShortsInterval(minutes: Int): SettingsChangeResult =
        change { current ->
            if (current.shortsIntervalMinutes == minutes) current else {
                current.copy(
                    shortsIntervalMinutes = minutes,
                    preset = LimitPreset.CUSTOM,
                )
            }
        }

    suspend fun setDailyLimit(minutes: Int): SettingsChangeResult =
        change { current ->
            if (current.dailyLimitMinutes == minutes) current else {
                current.copy(
                    dailyLimitMinutes = minutes,
                    preset = LimitPreset.CUSTOM,
                )
            }
        }

    suspend fun setShortsEnabled(enabled: Boolean): SettingsChangeResult =
        change { it.copy(shortsGateEnabled = enabled) }

    suspend fun setDailyEnabled(enabled: Boolean): SettingsChangeResult =
        change { it.copy(dailyLimitEnabled = enabled) }

    suspend fun save(settings: UserSettings): SettingsChangeResult = change { settings }

    private suspend fun change(
        transform: (UserSettings) -> UserSettings,
    ): SettingsChangeResult {
        val previous = repository.settings.value
        val candidate = transform(previous)
        val evaluation = SettingsValidator.evaluate(candidate)
        if (!evaluation.validation.isValid) {
            return SettingsChangeResult(
                evaluation = evaluation,
                persisted = false,
            )
        }

        val changed = candidate != previous
        if (changed) repository.save(candidate)
        return SettingsChangeResult(
            evaluation = evaluation,
            persisted = changed,
            commands = if (changed) commandsFor(previous, candidate) else emptySet(),
        )
    }
}

private fun commandsFor(
    previous: UserSettings,
    candidate: UserSettings,
): Set<SettingsCommand> = buildSet {
    if (previous.shortsIntervalMinutes != candidate.shortsIntervalMinutes) {
        add(SettingsCommand.RECALCULATE_SHORTS_CYCLE)
    }
    if (previous.dailyLimitMinutes != candidate.dailyLimitMinutes) {
        add(SettingsCommand.RECALCULATE_DAILY_LIMIT)
    }
    if (
        previous.shortsGateEnabled != candidate.shortsGateEnabled ||
        previous.dailyLimitEnabled != candidate.dailyLimitEnabled ||
        previous.shortsIntervalMinutes != candidate.shortsIntervalMinutes ||
        previous.dailyLimitMinutes != candidate.dailyLimitMinutes
    ) {
        add(SettingsCommand.REEVALUATE_POLICY)
    }
}

val SHORTS_INTERVAL_RANGE: IntRange = 1..30
val DAILY_LIMIT_RANGE: IntRange = 10..240
const val DAILY_LIMIT_STEP_MINUTES = 5
