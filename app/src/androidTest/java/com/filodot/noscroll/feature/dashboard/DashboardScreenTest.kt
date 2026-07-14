package com.filodot.noscroll.feature.dashboard

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.filodot.noscroll.ui.theme.NoScrollTheme
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalDashboard_exposesIndependentTextProgressValues() {
        composeRule.setDashboard(normalState())

        composeRule.onNodeWithText("До паузы: 03:42").assertIsDisplayed()
        composeRule.onNodeWithText("Shorts сегодня: 12 мин").assertIsDisplayed()
        composeRule.onNodeWithText("Использовано 18 из 45 мин").assertIsDisplayed()
        composeRule.onNode(
            hasContentDescription("Интервал Shorts: использовано 01:18 из 05:00"),
        ).assertIsDisplayed()
    }

    @Test
    fun emergencyDashboard_prioritizesBannerAndRestoresProtectionAction() {
        var lastAction: DashboardAction? = null
        composeRule.setDashboard(
            state = normalState().copy(
                emergency = EmergencyUiState(active = true, activeSinceLabel = "14:32"),
            ),
            onAction = { lastAction = it },
        )

        composeRule.onNodeWithText("С 14:32. Учёт времени продолжается").assertIsDisplayed()
        composeRule.onAllNodesWithText("Блокировка приостановлена")
            .onFirst()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Включить блокировку").performClick()

        composeRule.runOnIdle {
            check(lastAction == DashboardAction.SetEmergencyEnabled(enabled = false))
        }
    }

    @Test
    fun missingAccessibility_exposesRequiredRecoveryAction() {
        var lastAction: DashboardAction? = null
        composeRule.setDashboard(
            state = normalState().copy(accessibilityEnabled = false),
            onAction = { lastAction = it },
        )

        composeRule.onNodeWithText("Защита не работает").assertIsDisplayed()
        composeRule.onNodeWithText("Включить Accessibility").performClick()

        composeRule.runOnIdle {
            check(lastAction == DashboardAction.OpenAccessibilitySettings)
        }
    }

    @Test
    fun unavailableDaily_keepsShortsWorkingAndOffersUsageAccess() {
        composeRule.setDashboard(normalState().copy(daily = DailyLimitUiState.Unavailable))

        composeRule.onNodeWithText("Дневной лимит приостановлен").assertIsDisplayed()
        composeRule.onNodeWithText("Паузы в Shorts продолжают работать").assertIsDisplayed()
        composeRule.onNodeWithText("Недоступно без доступа к статистике").assertIsDisplayed()
        composeRule.onNodeWithText("До паузы: 03:42").assertIsDisplayed()
    }

    @Test
    fun allLimitsDisabled_disablesEmergencySwitchWithExplanation() {
        composeRule.setDashboard(
            normalState().copy(
                shorts = ShortsLimitUiState.Disabled,
                daily = DailyLimitUiState.Disabled,
            ),
        )

        composeRule.onNodeWithText("Ограничения уже выключены").assertIsDisplayed()
        composeRule.onNode(isToggleable()).assertIsNotEnabled()
        composeRule.onNodeWithText("Защита не работает").assertDoesNotExist()
    }

    @Test
    fun recoveryActionsRemainReachableAtTwoHundredPercentFontScale() {
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density,
                    fontScale = 2f,
                ),
            ) {
                NoScrollTheme {
                    DashboardScreen(
                        state = normalState().copy(daily = DailyLimitUiState.Unavailable),
                        onAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Разрешить").performScrollTo().assertIsEnabled()
        composeRule.onNode(isToggleable()).performScrollTo().assertIsEnabled()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setDashboard(
        state: DashboardUiState,
        onAction: (DashboardAction) -> Unit = {},
    ) {
        setContent {
            NoScrollTheme {
                DashboardScreen(state = state, onAction = onAction)
            }
        }
    }

    private fun normalState() = DashboardUiState(dateLabel = "14 июля")
}
