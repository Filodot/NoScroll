package com.filodot.noscroll.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.filodot.noscroll.ui.theme.NoScrollTheme
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingSystemAccessAndYouTubeHaveExplicitTextStates() {
        composeRule.setSettings(
            state().copy(
                accessibilityStatus = SystemAccessUiStatus.NOT_ENABLED,
                usageAccessStatus = SystemAccessUiStatus.SKIPPED,
                youtubeVersionLabel = null,
            ),
        )

        composeRule.onNodeWithText("Не включён").assertIsDisplayed()
        composeRule.onNodeWithText("Пропущен").assertIsDisplayed()
        composeRule.onNodeWithText("Не найден").assertIsDisplayed()
        composeRule.onAllNodesWithText("Настроить").assertCountEquals(2)
    }

    @Test
    fun diagnosticsRendersOnlyCodesCountersAndRulesVersion() {
        composeRule.setSettings(state())

        composeRule.onNodeWithText("Shorts подтверждён").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Диагностика содержит только коды и счётчики — без текста и элементов экрана YouTube.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun privacyActionRemainsReachableAtTwoHundredPercentFontScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
            ) {
                NoScrollTheme {
                    SettingsScreen(state = state(), onAction = {})
                }
            }
        }

        composeRule.onNodeWithText("Политика приватности")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setSettings(
        state: SettingsUiState,
    ) {
        setContent {
            NoScrollTheme {
                SettingsScreen(state = state, onAction = {})
            }
        }
    }

    private fun state() = SettingsUiState(
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
}
