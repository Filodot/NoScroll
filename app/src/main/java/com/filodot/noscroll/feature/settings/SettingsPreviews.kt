package com.filodot.noscroll.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.filodot.noscroll.ui.theme.NoScrollTheme

@Preview(name = "Settings ready", showBackground = true, locale = "ru")
@Composable
private fun ReadySettingsPreview() {
    SettingsPreviewFrame(settingsState())
}

@Preview(name = "Permissions missing dark", showBackground = true, locale = "ru", uiMode = 0x20)
@Composable
private fun MissingAccessSettingsPreview() {
    SettingsPreviewFrame(
        state = settingsState().copy(
            accessibilityStatus = SystemAccessUiStatus.NOT_ENABLED,
            usageAccessStatus = SystemAccessUiStatus.SKIPPED,
            youtubeVersionLabel = null,
        ),
        darkTheme = true,
    )
}

@Preview(name = "Redacted diagnostic error", showBackground = true, locale = "ru")
@Composable
private fun DiagnosticErrorPreview() {
    SettingsPreviewFrame(
        settingsState().copy(
            diagnostics = RedactedDiagnosticsUiState(
                detectorStatus = DetectorUiStatus.ERROR,
                lastResultCode = DiagnosticResultCode.ACCESS_UNAVAILABLE,
                unknownCount = 4,
                rulesVersion = 1,
            ),
        ),
    )
}

@Preview(
    name = "Settings 200%",
    showBackground = true,
    locale = "ru",
    fontScale = 2f,
)
@Composable
private fun LargeTextSettingsPreview() {
    SettingsPreviewFrame(settingsState())
}

@Composable
private fun SettingsPreviewFrame(
    state: SettingsUiState,
    darkTheme: Boolean = false,
) {
    NoScrollTheme(darkTheme = darkTheme) {
        SettingsScreen(state = state, onAction = {})
    }
}

private fun settingsState() = SettingsUiState(
    accessibilityStatus = SystemAccessUiStatus.ENABLED,
    usageAccessStatus = SystemAccessUiStatus.ENABLED,
    youtubeVersionLabel = "20.27.33",
    diagnostics = RedactedDiagnosticsUiState(
        detectorStatus = DetectorUiStatus.READY,
        lastRecognitionLabel = "Сегодня, 14:32",
        lastResultCode = DiagnosticResultCode.SHORTS_CONFIRMED,
        unknownCount = 0,
        rulesVersion = 1,
    ),
    appVersionLabel = "0.1.0",
)
