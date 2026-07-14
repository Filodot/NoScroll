package com.filodot.noscroll.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.filodot.noscroll.ui.theme.NoScrollTheme

@Preview(name = "Normal light", showBackground = true, locale = "ru")
@Composable
private fun NormalDashboardPreview() {
    DashboardPreviewFrame(normalState())
}

@Preview(name = "Emergency dark", showBackground = true, locale = "ru", uiMode = 0x20)
@Composable
private fun EmergencyDashboardPreview() {
    DashboardPreviewFrame(
        state = normalState().copy(
            emergency = EmergencyUiState(
                active = true,
                activeSinceLabel = "14:32",
            ),
        ),
        darkTheme = true,
    )
}

@Preview(name = "Accessibility error", showBackground = true, locale = "ru")
@Composable
private fun AccessibilityErrorDashboardPreview() {
    DashboardPreviewFrame(normalState().copy(accessibilityEnabled = false))
}

@Preview(
    name = "Daily unavailable 200%",
    showBackground = true,
    locale = "ru",
    fontScale = 2f,
)
@Composable
private fun DailyUnavailableLargeTextPreview() {
    DashboardPreviewFrame(normalState().copy(daily = DailyLimitUiState.Unavailable))
}

@Preview(name = "Limits disabled", showBackground = true, locale = "ru")
@Composable
private fun DisabledLimitsDashboardPreview() {
    DashboardPreviewFrame(
        normalState().copy(
            shorts = ShortsLimitUiState.Disabled,
            daily = DailyLimitUiState.Disabled,
        ),
    )
}

@Composable
private fun DashboardPreviewFrame(
    state: DashboardUiState,
    darkTheme: Boolean = false,
) {
    NoScrollTheme(darkTheme = darkTheme) {
        DashboardScreen(state = state, onAction = {})
    }
}

private fun normalState() = DashboardUiState(dateLabel = "14 июля")
