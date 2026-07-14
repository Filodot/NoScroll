package com.filodot.noscroll.feature.limits

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.filodot.noscroll.core.model.LimitPreset
import com.filodot.noscroll.ui.theme.NoScrollTheme

@Preview(name = "Balanced light", showBackground = true, locale = "ru")
@Composable
private fun BalancedLimitsPreview() {
    LimitsPreviewFrame(LimitsUiState())
}

@Preview(name = "Unsaved custom", showBackground = true, locale = "ru")
@Composable
private fun UnsavedCustomLimitsPreview() {
    LimitsPreviewFrame(
        LimitsUiState(
            draft = LimitsValues(
                preset = LimitPreset.CUSTOM,
                shortsMinutes = 8,
                dailyMinutes = 60,
            ),
        ),
    )
}

@Preview(name = "Daily before Shorts", showBackground = true, locale = "ru")
@Composable
private fun DailyBeforeShortsPreview() {
    LimitsPreviewFrame(
        LimitsUiState(
            draft = LimitsValues(
                preset = LimitPreset.CUSTOM,
                shortsMinutes = 20,
                dailyMinutes = 10,
            ),
        ),
    )
}

@Preview(name = "Both disabled dark", showBackground = true, locale = "ru", uiMode = 0x20)
@Composable
private fun DisabledLimitsDarkPreview() {
    val values = LimitsValues(shortsEnabled = false, dailyEnabled = false)
    LimitsPreviewFrame(
        state = LimitsUiState(saved = values, draft = values),
        darkTheme = true,
    )
}

@Preview(
    name = "Unsaved 200%",
    showBackground = true,
    locale = "ru",
    fontScale = 2f,
)
@Composable
private fun LargeTextLimitsPreview() {
    LimitsPreviewFrame(
        LimitsUiState(
            draft = LimitsValues(
                preset = LimitPreset.CUSTOM,
                shortsMinutes = 6,
                dailyMinutes = 50,
            ),
        ),
    )
}

@Composable
private fun LimitsPreviewFrame(
    state: LimitsUiState,
    darkTheme: Boolean = false,
) {
    NoScrollTheme(darkTheme = darkTheme) {
        LimitsScreen(state = state, onAction = {})
    }
}
