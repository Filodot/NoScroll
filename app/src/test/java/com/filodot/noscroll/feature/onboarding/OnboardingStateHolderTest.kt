package com.filodot.noscroll.feature.onboarding

import com.filodot.noscroll.core.model.LimitPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateHolderTest {
    @Test
    fun `welcome starts with recommended values and configure opens presets`() {
        val holder = OnboardingStateHolder()

        assertEquals(LimitPreset.BALANCED, holder.state.value.selectedPreset)
        assertEquals(5, holder.state.value.shortsIntervalMinutes)
        assertEquals(45, holder.state.value.dailyLimitMinutes)

        holder.dispatch(OnboardingAction.Configure)

        assertEquals(OnboardingStep.PRESET, holder.state.value.step)
    }

    @Test
    fun `how it works is exposed as a navigation effect`() {
        val effects = mutableListOf<OnboardingEffect>()
        val holder = OnboardingStateHolder(emitEffect = effects::add)

        holder.dispatch(OnboardingAction.ShowHowItWorks)

        assertEquals(listOf(OnboardingEffect.ShowHowItWorks), effects)
    }

    @Test
    fun `preset selection applies approved values and custom preserves them`() {
        val holder = presetHolder()

        holder.dispatch(OnboardingAction.SelectPreset(LimitPreset.GENTLE))
        assertEquals(10, holder.state.value.shortsIntervalMinutes)
        assertEquals(90, holder.state.value.dailyLimitMinutes)

        holder.dispatch(OnboardingAction.SelectPreset(LimitPreset.CUSTOM))

        assertEquals(LimitPreset.CUSTOM, holder.state.value.selectedPreset)
        assertEquals(10, holder.state.value.shortsIntervalMinutes)
        assertEquals(90, holder.state.value.dailyLimitMinutes)
    }

    @Test
    fun `accessibility settings cannot open before explicit consent`() {
        val effects = mutableListOf<OnboardingEffect>()
        val holder = disclosureHolder(effects)

        holder.dispatch(OnboardingAction.RequestAccessibilitySettings)
        assertTrue(effects.isEmpty())
        assertFalse(holder.state.value.waitingForAccessibilityReturn)

        holder.dispatch(OnboardingAction.SetAccessibilityConsent(true))
        holder.dispatch(OnboardingAction.RequestAccessibilitySettings)

        assertEquals(listOf(OnboardingEffect.OpenAccessibilitySettings), effects)
        assertTrue(holder.state.value.waitingForAccessibilityReturn)
    }

    @Test
    fun `accessibility refusal stays on disclosure and exposes retry feedback`() {
        val holder = disclosureHolder()
        holder.dispatch(OnboardingAction.SetAccessibilityConsent(true))
        holder.dispatch(OnboardingAction.RequestAccessibilitySettings)

        holder.dispatch(OnboardingAction.AccessibilitySettingsReturned(enabled = false))

        assertEquals(OnboardingStep.ACCESSIBILITY_DISCLOSURE, holder.state.value.step)
        assertEquals(PermissionUiStatus.NOT_GRANTED, holder.state.value.accessibilityStatus)
        assertFalse(holder.state.value.waitingForAccessibilityReturn)
        assertTrue(holder.state.value.accessibilityReturnFailed)
    }

    @Test
    fun `enabled accessibility advances to optional usage access`() {
        val holder = disclosureHolder()

        holder.dispatch(OnboardingAction.AccessibilitySettingsReturned(enabled = true))

        assertEquals(OnboardingStep.USAGE_ACCESS, holder.state.value.step)
        assertEquals(PermissionUiStatus.ENABLED, holder.state.value.accessibilityStatus)
        assertFalse(holder.state.value.accessibilityReturnFailed)
    }

    @Test
    fun `skipping accessibility reaches readiness but cannot start`() {
        val effects = mutableListOf<OnboardingEffect>()
        val holder = disclosureHolder(effects)

        holder.dispatch(OnboardingAction.SkipAccessibility)
        holder.dispatch(OnboardingAction.Start)

        assertEquals(OnboardingStep.READINESS, holder.state.value.step)
        assertEquals(PermissionUiStatus.SKIPPED, holder.state.value.usageAccessStatus)
        assertFalse(holder.state.value.canStart)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `usage access request and refusal keep optional screen open`() {
        val effects = mutableListOf<OnboardingEffect>()
        val holder = usageHolder(effects)

        holder.dispatch(OnboardingAction.RequestUsageAccessSettings)
        assertEquals(listOf(OnboardingEffect.OpenUsageAccessSettings), effects)
        assertTrue(holder.state.value.waitingForUsageReturn)

        holder.dispatch(OnboardingAction.UsageAccessSettingsReturned(enabled = false))

        assertEquals(OnboardingStep.USAGE_ACCESS, holder.state.value.step)
        assertEquals(PermissionUiStatus.NOT_GRANTED, holder.state.value.usageAccessStatus)
        assertTrue(holder.state.value.usageReturnFailed)
    }

    @Test
    fun `usage access can be skipped without disabling Shorts protection`() {
        val holder = usageHolder()

        holder.dispatch(OnboardingAction.SkipUsageAccess)

        assertEquals(OnboardingStep.READINESS, holder.state.value.step)
        assertEquals(PermissionUiStatus.SKIPPED, holder.state.value.usageAccessStatus)
        assertTrue(holder.state.value.canStart)
    }

    @Test
    fun `successful usage return enables daily limit and completes selected setup`() {
        val effects = mutableListOf<OnboardingEffect>()
        val holder = usageHolder(effects)

        holder.dispatch(OnboardingAction.UsageAccessSettingsReturned(enabled = true))
        holder.dispatch(OnboardingAction.Start)

        assertEquals(PermissionUiStatus.ENABLED, holder.state.value.usageAccessStatus)
        assertEquals(
            OnboardingEffect.Completed(
                OnboardingSelection(
                    preset = LimitPreset.BALANCED,
                    shortsIntervalMinutes = 5,
                    dailyLimitMinutes = 45,
                    dailyLimitEnabled = true,
                ),
            ),
            effects.single(),
        )
    }

    @Test
    fun `skipped usage completes with daily limit disabled`() {
        val effects = mutableListOf<OnboardingEffect>()
        val holder = usageHolder(effects)

        holder.dispatch(OnboardingAction.SkipUsageAccess)
        holder.dispatch(OnboardingAction.Start)

        val completed = effects.single() as OnboardingEffect.Completed
        assertFalse(completed.selection.dailyLimitEnabled)
    }

    @Test
    fun `missing YouTube warns but does not disable start when accessibility is enabled`() {
        val holder = readinessHolder()

        holder.dispatch(
            OnboardingAction.RefreshReadiness(
                accessibilityEnabled = true,
                usageAccessEnabled = false,
                youtubeInstalled = false,
            ),
        )

        assertFalse(holder.state.value.youtubeInstalled)
        assertTrue(holder.state.value.canStart)
    }

    @Test
    fun `readiness refresh revokes required access immediately and preserves usage skip`() {
        val holder = readinessHolder()

        holder.dispatch(
            OnboardingAction.RefreshReadiness(
                accessibilityEnabled = false,
                usageAccessEnabled = false,
                youtubeInstalled = true,
            ),
        )

        assertEquals(PermissionUiStatus.NOT_GRANTED, holder.state.value.accessibilityStatus)
        assertEquals(PermissionUiStatus.SKIPPED, holder.state.value.usageAccessStatus)
        assertFalse(holder.state.value.canStart)
    }

    @Test
    fun `review opens the first missing required permission and asks for refresh`() {
        val effects = mutableListOf<OnboardingEffect>()
        val holder = OnboardingStateHolder(
            initialState = OnboardingUiState(
                step = OnboardingStep.READINESS,
                accessibilityStatus = PermissionUiStatus.NOT_GRANTED,
                usageAccessStatus = PermissionUiStatus.SKIPPED,
            ),
            emitEffect = effects::add,
        )

        holder.dispatch(OnboardingAction.ReviewMissingPermissions)

        assertEquals(OnboardingStep.ACCESSIBILITY_DISCLOSURE, holder.state.value.step)
        assertEquals(listOf(OnboardingEffect.RefreshPermissionStates), effects)
    }

    private fun presetHolder(): OnboardingStateHolder = OnboardingStateHolder(
        initialState = OnboardingUiState(step = OnboardingStep.PRESET),
    )

    private fun disclosureHolder(
        effects: MutableList<OnboardingEffect> = mutableListOf(),
    ): OnboardingStateHolder = OnboardingStateHolder(
        initialState = OnboardingUiState(step = OnboardingStep.ACCESSIBILITY_DISCLOSURE),
        emitEffect = effects::add,
    )

    private fun usageHolder(
        effects: MutableList<OnboardingEffect> = mutableListOf(),
    ): OnboardingStateHolder = OnboardingStateHolder(
        initialState = OnboardingUiState(
            step = OnboardingStep.USAGE_ACCESS,
            accessibilityStatus = PermissionUiStatus.ENABLED,
        ),
        emitEffect = effects::add,
    )

    private fun readinessHolder(): OnboardingStateHolder = OnboardingStateHolder(
        initialState = OnboardingUiState(
            step = OnboardingStep.READINESS,
            accessibilityStatus = PermissionUiStatus.ENABLED,
            usageAccessStatus = PermissionUiStatus.SKIPPED,
        ),
    )
}
