package com.filodot.noscroll.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.filodot.noscroll.ui.theme.NoScrollTheme
import org.junit.Rule
import org.junit.Test

class NoScrollAppNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun newUserStartsInOnboarding() {
        composeRule.setApp(onboardingCompleted = false)

        composeRule.onNodeWithText("Остановите автоматический скролл").assertIsDisplayed()
        composeRule.onNodeWithText("Настроить").assertIsDisplayed()
    }

    @Test
    fun completedUserCanMoveAcrossAllTopLevelDestinations() {
        composeRule.setApp(onboardingCompleted = true)

        composeRule.onAllNodesWithText("Сегодня").assertCountEquals(2)
        composeRule.onNodeWithText("Ограничения").performClick()
        composeRule.onAllNodesWithText("Ограничения").assertCountEquals(2)
        composeRule.onNodeWithText("Учёба").performClick()
        composeRule.onNodeWithText("Обучение").assertIsDisplayed()
        composeRule.onNodeWithText("Настройки").performClick()
        composeRule.onAllNodesWithText("Настройки").assertCountEquals(2)
    }

    @Test
    fun settingsOpensStandaloneEmergencyHistory() {
        composeRule.setApp(onboardingCompleted = true)
        composeRule.onNodeWithText("Настройки").performClick()

        composeRule.onNodeWithText("Открыть историю")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText("История Emergency Stop").assertIsDisplayed()
        composeRule.onNodeWithText("Здесь появятся ваши Emergency Stop").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setApp(
        onboardingCompleted: Boolean,
    ) {
        setContent {
            NoScrollTheme {
                NoScrollApp(
                    graph = NoScrollAppGraph.fake(
                        onboardingCompleted = onboardingCompleted,
                    ),
                )
            }
        }
    }
}
