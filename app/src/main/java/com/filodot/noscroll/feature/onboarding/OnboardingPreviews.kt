package com.filodot.noscroll.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.filodot.noscroll.ui.theme.NoScrollTheme

@Preview(name = "01 Welcome", showBackground = true, locale = "ru")
@Composable
private fun WelcomePreview() {
    PreviewFrame(OnboardingUiState())
}

@Preview(name = "02 Preset dark", showBackground = true, locale = "ru", uiMode = 0x20)
@Composable
private fun PresetDarkPreview() {
    PreviewFrame(OnboardingUiState(step = OnboardingStep.PRESET), darkTheme = true)
}

@Preview(
    name = "03 Disclosure at 200%",
    showBackground = true,
    locale = "ru",
    fontScale = 2f,
)
@Composable
private fun AccessibilityDisclosureLargeTextPreview() {
    PreviewFrame(OnboardingUiState(step = OnboardingStep.ACCESSIBILITY_DISCLOSURE))
}

@Preview(name = "04 Usage access", showBackground = true, locale = "ru")
@Composable
private fun UsageAccessPreview() {
    PreviewFrame(
        OnboardingUiState(
            step = OnboardingStep.USAGE_ACCESS,
            accessibilityStatus = PermissionUiStatus.ENABLED,
        ),
    )
}

@Preview(name = "05 Ready without YouTube", showBackground = true, locale = "ru")
@Composable
private fun ReadyWithoutYoutubePreview() {
    PreviewFrame(
        OnboardingUiState(
            step = OnboardingStep.READINESS,
            accessibilityStatus = PermissionUiStatus.ENABLED,
            usageAccessStatus = PermissionUiStatus.SKIPPED,
            youtubeInstalled = false,
        ),
    )
}

@Composable
private fun PreviewFrame(
    state: OnboardingUiState,
    darkTheme: Boolean = false,
) {
    NoScrollTheme(darkTheme = darkTheme) {
        OnboardingScreen(state = state, onAction = {})
    }
}
