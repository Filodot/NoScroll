package com.filodot.noscroll.core.settings

import com.filodot.noscroll.core.model.LimitPreset
import com.filodot.noscroll.core.model.UserSettings
import com.filodot.noscroll.core.testing.InMemorySettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDomainTest {
    @Test
    fun `preset catalogue contains the approved values and recommendation`() {
        assertEquals(
            listOf(LimitPreset.GENTLE, LimitPreset.BALANCED, LimitPreset.STRICT, LimitPreset.CUSTOM),
            LimitPresets.all.map { it.preset },
        )
        assertEquals(10, LimitPresets.gentle.shortsIntervalMinutes)
        assertEquals(90, LimitPresets.gentle.dailyLimitMinutes)
        assertEquals(5, LimitPresets.balanced.shortsIntervalMinutes)
        assertEquals(45, LimitPresets.balanced.dailyLimitMinutes)
        assertTrue(LimitPresets.balanced.recommended)
        assertEquals(2, LimitPresets.strict.shortsIntervalMinutes)
        assertEquals(20, LimitPresets.strict.dailyLimitMinutes)
        assertEquals(null, LimitPresets.custom.shortsIntervalMinutes)
    }

    @Test
    fun `balanced defaults produce the both-enabled summary`() {
        val evaluation = domain().current()

        assertTrue(evaluation.validation.isValid)
        assertEquals(SettingsSummaryVariant.BOTH_ENABLED, evaluation.summary.variant)
        assertEquals(5, evaluation.summary.shortsIntervalMinutes)
        assertEquals(45, evaluation.summary.dailyLimitMinutes)
        assertTrue(evaluation.summary.warnings.isEmpty())
    }

    @Test
    fun `applying preset persists values and emits immediate recalculation commands`() = runTest {
        val repository = InMemorySettingsRepository()
        val result = SettingsDomain(repository).applyPreset(LimitPreset.GENTLE)

        assertTrue(result.persisted)
        assertEquals(10, repository.settings.value.shortsIntervalMinutes)
        assertEquals(90, repository.settings.value.dailyLimitMinutes)
        assertEquals(LimitPreset.GENTLE, repository.settings.value.preset)
        assertEquals(
            setOf(
                SettingsCommand.RECALCULATE_SHORTS_CYCLE,
                SettingsCommand.RECALCULATE_DAILY_LIMIT,
                SettingsCommand.REEVALUATE_POLICY,
            ),
            result.commands,
        )
    }

    @Test
    fun `editing one numeric value switches preset to custom`() = runTest {
        val repository = InMemorySettingsRepository()
        val result = SettingsDomain(repository).setShortsInterval(6)

        assertTrue(result.persisted)
        assertEquals(LimitPreset.CUSTOM, repository.settings.value.preset)
        assertEquals(
            setOf(
                SettingsCommand.RECALCULATE_SHORTS_CYCLE,
                SettingsCommand.REEVALUATE_POLICY,
            ),
            result.commands,
        )
    }

    @Test
    fun `minimum and maximum boundaries are accepted exactly`() = runTest {
        val repository = InMemorySettingsRepository()
        val domain = SettingsDomain(repository)

        assertTrue(domain.save(settings(shorts = 1, daily = 10)).evaluation.validation.isValid)
        val maximum = domain.save(settings(shorts = 30, daily = 240))

        assertTrue(maximum.persisted)
        assertEquals(30, repository.settings.value.shortsIntervalMinutes)
        assertEquals(240, repository.settings.value.dailyLimitMinutes)
    }

    @Test
    fun `out of range or off-step values are rejected without persistence`() = runTest {
        val repository = InMemorySettingsRepository()
        val domain = SettingsDomain(repository)
        val original = repository.settings.value

        val shorts = domain.setShortsInterval(31)
        val dailyRange = domain.setDailyLimit(5)
        val dailyStep = domain.setDailyLimit(42)

        assertFalse(shorts.persisted)
        assertTrue(
            shorts.evaluation.validation.errors.single() is
                SettingsValidationError.ShortsIntervalOutOfRange,
        )
        assertTrue(
            dailyRange.evaluation.validation.errors.single() is
                SettingsValidationError.DailyLimitOutOfRange,
        )
        assertTrue(
            dailyStep.evaluation.validation.errors.single() is
                SettingsValidationError.DailyLimitStepMismatch,
        )
        assertEquals(original, repository.settings.value)
    }

    @Test
    fun `daily off keeps stored value and removes warning from summary`() = runTest {
        val repository = InMemorySettingsRepository(settings(shorts = 20, daily = 10))
        val result = SettingsDomain(repository).setDailyEnabled(false)

        assertTrue(result.persisted)
        assertFalse(repository.settings.value.dailyLimitEnabled)
        assertEquals(10, repository.settings.value.dailyLimitMinutes)
        assertEquals(SettingsSummaryVariant.SHORTS_ONLY, result.evaluation.summary.variant)
        assertEquals(null, result.evaluation.summary.dailyLimitMinutes)
        assertTrue(result.evaluation.validation.warnings.isEmpty())
        assertEquals(setOf(SettingsCommand.REEVALUATE_POLICY), result.commands)
    }

    @Test
    fun `daily below Shorts is allowed and produces informational warning`() = runTest {
        val repository = InMemorySettingsRepository()
        val result = SettingsDomain(repository).save(settings(shorts = 20, daily = 10))

        assertTrue(result.persisted)
        assertTrue(result.evaluation.validation.isValid)
        assertEquals(
            setOf(SettingsWarning.DAILY_LIMIT_BELOW_SHORTS_INTERVAL),
            result.evaluation.validation.warnings,
        )
        assertEquals(result.evaluation.validation.warnings, result.evaluation.summary.warnings)
    }

    @Test
    fun `disabling both limits retains configured values and yields passive summary`() = runTest {
        val repository = InMemorySettingsRepository(settings(shorts = 10, daily = 90))
        val domain = SettingsDomain(repository)

        domain.setShortsEnabled(false)
        val result = domain.setDailyEnabled(false)

        assertEquals(SettingsSummaryVariant.ALL_DISABLED, result.evaluation.summary.variant)
        assertEquals(null, result.evaluation.summary.shortsIntervalMinutes)
        assertEquals(null, result.evaluation.summary.dailyLimitMinutes)
        assertEquals(10, repository.settings.value.shortsIntervalMinutes)
        assertEquals(90, repository.settings.value.dailyLimitMinutes)
    }

    @Test
    fun `shorts off leaves daily enforcement and values independent`() = runTest {
        val repository = InMemorySettingsRepository(settings(shorts = 5, daily = 45))
        val result = SettingsDomain(repository).setShortsEnabled(false)

        assertEquals(SettingsSummaryVariant.DAILY_ONLY, result.evaluation.summary.variant)
        assertEquals(45, result.evaluation.summary.dailyLimitMinutes)
        assertEquals(null, result.evaluation.summary.shortsIntervalMinutes)
        assertEquals(LimitPreset.BALANCED, repository.settings.value.preset)
    }

    @Test
    fun `selecting custom preserves numeric values and emits no enforcement command`() = runTest {
        val repository = InMemorySettingsRepository(settings(shorts = 10, daily = 90))
        val result = SettingsDomain(repository).applyPreset(LimitPreset.CUSTOM)

        assertTrue(result.persisted)
        assertEquals(10, repository.settings.value.shortsIntervalMinutes)
        assertEquals(90, repository.settings.value.dailyLimitMinutes)
        assertEquals(LimitPreset.CUSTOM, repository.settings.value.preset)
        assertTrue(result.commands.isEmpty())
    }

    @Test
    fun `idempotent edit does not change preset persist or recalculate`() = runTest {
        val repository = InMemorySettingsRepository()
        val result = SettingsDomain(repository).setShortsInterval(5)

        assertFalse(result.persisted)
        assertEquals(LimitPreset.BALANCED, repository.settings.value.preset)
        assertTrue(result.commands.isEmpty())
    }

    private fun domain(settings: UserSettings = UserSettings()) =
        SettingsDomain(InMemorySettingsRepository(settings))

    private fun settings(
        shorts: Int,
        daily: Int,
    ) = UserSettings(
        shortsIntervalMinutes = shorts,
        dailyLimitMinutes = daily,
        preset = LimitPreset.BALANCED,
    )
}
