package com.filodot.noscroll.feature.onboarding

import com.filodot.noscroll.core.model.LimitPreset
import com.filodot.noscroll.core.model.UserSettings
import com.filodot.noscroll.core.settings.LimitPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OnboardingStep {
    WELCOME,
    PRESET,
    ACCESSIBILITY_DISCLOSURE,
    USAGE_ACCESS,
    READINESS,
}

enum class PermissionUiStatus {
    NOT_GRANTED,
    ENABLED,
    SKIPPED,
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val selectedPreset: LimitPreset = LimitPreset.BALANCED,
    val shortsIntervalMinutes: Int = UserSettings.DEFAULT_SHORTS_INTERVAL_MINUTES,
    val dailyLimitMinutes: Int = UserSettings.DEFAULT_DAILY_LIMIT_MINUTES,
    val accessibilityConsentChecked: Boolean = false,
    val accessibilityStatus: PermissionUiStatus = PermissionUiStatus.NOT_GRANTED,
    val usageAccessStatus: PermissionUiStatus = PermissionUiStatus.NOT_GRANTED,
    val youtubeInstalled: Boolean = true,
    val waitingForAccessibilityReturn: Boolean = false,
    val waitingForUsageReturn: Boolean = false,
    val accessibilityReturnFailed: Boolean = false,
    val usageReturnFailed: Boolean = false,
    val instagramInstalled: Boolean = true,
) {
    val canStart: Boolean
        get() = step == OnboardingStep.READINESS &&
            accessibilityStatus == PermissionUiStatus.ENABLED
}

sealed interface OnboardingAction {
    data object Configure : OnboardingAction
    data object ShowHowItWorks : OnboardingAction
    data class SelectPreset(val preset: LimitPreset) : OnboardingAction
    data object ContinueFromPreset : OnboardingAction
    data class SetAccessibilityConsent(val checked: Boolean) : OnboardingAction
    data object RequestAccessibilitySettings : OnboardingAction
    data object OpenPrivacyPolicy : OnboardingAction
    data object OpenAppDetailsSettings : OnboardingAction
    data object SkipAccessibility : OnboardingAction
    data class AccessibilitySettingsReturned(val enabled: Boolean) : OnboardingAction
    data object RequestUsageAccessSettings : OnboardingAction
    data object SkipUsageAccess : OnboardingAction
    data class UsageAccessSettingsReturned(val enabled: Boolean) : OnboardingAction
    data class RefreshReadiness(
        val accessibilityEnabled: Boolean,
        val usageAccessEnabled: Boolean,
        val youtubeInstalled: Boolean,
        val instagramInstalled: Boolean = true,
    ) : OnboardingAction

    data object ReviewMissingPermissions : OnboardingAction
    data object Start : OnboardingAction
}

sealed interface OnboardingEffect {
    data object ShowHowItWorks : OnboardingEffect
    data object OpenAccessibilitySettings : OnboardingEffect
    data object OpenUsageAccessSettings : OnboardingEffect
    data object OpenPrivacyPolicy : OnboardingEffect
    data object OpenAppDetailsSettings : OnboardingEffect
    data object RefreshPermissionStates : OnboardingEffect
    data class Completed(val selection: OnboardingSelection) : OnboardingEffect
}

data class OnboardingSelection(
    val preset: LimitPreset,
    val shortsIntervalMinutes: Int,
    val dailyLimitMinutes: Int,
    val dailyLimitEnabled: Boolean,
)

class OnboardingStateHolder(
    initialState: OnboardingUiState = OnboardingUiState(),
    private val emitEffect: (OnboardingEffect) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    fun dispatch(action: OnboardingAction) {
        when (action) {
            OnboardingAction.Configure -> updateForStep(OnboardingStep.WELCOME) {
                it.copy(step = OnboardingStep.PRESET)
            }

            OnboardingAction.ShowHowItWorks -> emitEffect(OnboardingEffect.ShowHowItWorks)
            is OnboardingAction.SelectPreset -> selectPreset(action.preset)
            OnboardingAction.ContinueFromPreset -> updateForStep(OnboardingStep.PRESET) {
                it.copy(step = OnboardingStep.ACCESSIBILITY_DISCLOSURE)
            }

            is OnboardingAction.SetAccessibilityConsent ->
                updateForStep(OnboardingStep.ACCESSIBILITY_DISCLOSURE) {
                    it.copy(accessibilityConsentChecked = action.checked)
                }

            OnboardingAction.RequestAccessibilitySettings -> requestAccessibilitySettings()
            OnboardingAction.OpenPrivacyPolicy -> emitEffect(OnboardingEffect.OpenPrivacyPolicy)
            OnboardingAction.OpenAppDetailsSettings ->
                emitEffect(OnboardingEffect.OpenAppDetailsSettings)
            OnboardingAction.SkipAccessibility -> skipAccessibility()
            is OnboardingAction.AccessibilitySettingsReturned ->
                handleAccessibilityReturn(action.enabled)

            OnboardingAction.RequestUsageAccessSettings -> requestUsageAccessSettings()
            OnboardingAction.SkipUsageAccess -> skipUsageAccess()
            is OnboardingAction.UsageAccessSettingsReturned -> handleUsageReturn(action.enabled)
            is OnboardingAction.RefreshReadiness -> refreshReadiness(action)
            OnboardingAction.ReviewMissingPermissions -> reviewMissingPermissions()
            OnboardingAction.Start -> completeIfReady()
        }
    }

    private fun selectPreset(preset: LimitPreset) {
        updateForStep(OnboardingStep.PRESET) { current ->
            val definition = LimitPresets.definition(preset)
            current.copy(
                selectedPreset = preset,
                shortsIntervalMinutes = definition.shortsIntervalMinutes
                    ?: current.shortsIntervalMinutes,
                dailyLimitMinutes = definition.dailyLimitMinutes
                    ?: current.dailyLimitMinutes,
            )
        }
    }

    private fun requestAccessibilitySettings() {
        val current = mutableState.value
        if (
            current.step != OnboardingStep.ACCESSIBILITY_DISCLOSURE ||
            !current.accessibilityConsentChecked
        ) {
            return
        }
        mutableState.value = current.copy(
            waitingForAccessibilityReturn = true,
            accessibilityReturnFailed = false,
        )
        emitEffect(OnboardingEffect.OpenAccessibilitySettings)
    }

    private fun skipAccessibility() {
        updateForStep(OnboardingStep.ACCESSIBILITY_DISCLOSURE) {
            it.copy(
                step = OnboardingStep.READINESS,
                accessibilityStatus = PermissionUiStatus.NOT_GRANTED,
                usageAccessStatus = PermissionUiStatus.SKIPPED,
                waitingForAccessibilityReturn = false,
            )
        }
    }

    private fun handleAccessibilityReturn(enabled: Boolean) {
        val current = mutableState.value
        if (current.step != OnboardingStep.ACCESSIBILITY_DISCLOSURE) return
        mutableState.value = current.copy(
            step = if (enabled) OnboardingStep.USAGE_ACCESS else current.step,
            accessibilityStatus = if (enabled) {
                PermissionUiStatus.ENABLED
            } else {
                PermissionUiStatus.NOT_GRANTED
            },
            waitingForAccessibilityReturn = false,
            accessibilityReturnFailed = !enabled,
        )
    }

    private fun requestUsageAccessSettings() {
        val current = mutableState.value
        if (current.step != OnboardingStep.USAGE_ACCESS) return
        mutableState.value = current.copy(
            waitingForUsageReturn = true,
            usageReturnFailed = false,
        )
        emitEffect(OnboardingEffect.OpenUsageAccessSettings)
    }

    private fun skipUsageAccess() {
        updateForStep(OnboardingStep.USAGE_ACCESS) {
            it.copy(
                step = OnboardingStep.READINESS,
                usageAccessStatus = PermissionUiStatus.SKIPPED,
                waitingForUsageReturn = false,
            )
        }
    }

    private fun handleUsageReturn(enabled: Boolean) {
        val current = mutableState.value
        if (current.step != OnboardingStep.USAGE_ACCESS) return
        mutableState.value = current.copy(
            step = if (enabled) OnboardingStep.READINESS else current.step,
            usageAccessStatus = if (enabled) {
                PermissionUiStatus.ENABLED
            } else {
                PermissionUiStatus.NOT_GRANTED
            },
            waitingForUsageReturn = false,
            usageReturnFailed = !enabled,
        )
    }

    private fun refreshReadiness(action: OnboardingAction.RefreshReadiness) {
        val current = mutableState.value
        if (current.step != OnboardingStep.READINESS) return
        mutableState.value = current.copy(
            accessibilityStatus = if (action.accessibilityEnabled) {
                PermissionUiStatus.ENABLED
            } else {
                PermissionUiStatus.NOT_GRANTED
            },
            usageAccessStatus = when {
                action.usageAccessEnabled -> PermissionUiStatus.ENABLED
                current.usageAccessStatus == PermissionUiStatus.SKIPPED ->
                    PermissionUiStatus.SKIPPED

                else -> PermissionUiStatus.NOT_GRANTED
            },
            youtubeInstalled = action.youtubeInstalled,
            instagramInstalled = action.instagramInstalled,
        )
    }

    private fun reviewMissingPermissions() {
        val current = mutableState.value
        if (current.step != OnboardingStep.READINESS) return
        mutableState.value = current.copy(
            step = if (current.accessibilityStatus != PermissionUiStatus.ENABLED) {
                OnboardingStep.ACCESSIBILITY_DISCLOSURE
            } else {
                OnboardingStep.USAGE_ACCESS
            },
            accessibilityReturnFailed = false,
            usageReturnFailed = false,
        )
        emitEffect(OnboardingEffect.RefreshPermissionStates)
    }

    private fun completeIfReady() {
        val current = mutableState.value
        if (!current.canStart) return
        emitEffect(
            OnboardingEffect.Completed(
                OnboardingSelection(
                    preset = current.selectedPreset,
                    shortsIntervalMinutes = current.shortsIntervalMinutes,
                    dailyLimitMinutes = current.dailyLimitMinutes,
                    dailyLimitEnabled = current.usageAccessStatus == PermissionUiStatus.ENABLED,
                ),
            ),
        )
    }

    private inline fun updateForStep(
        requiredStep: OnboardingStep,
        transform: (OnboardingUiState) -> OnboardingUiState,
    ) {
        val current = mutableState.value
        if (current.step == requiredStep) mutableState.value = transform(current)
    }
}
