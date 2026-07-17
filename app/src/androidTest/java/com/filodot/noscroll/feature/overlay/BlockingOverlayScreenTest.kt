package com.filodot.noscroll.feature.overlay

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.filodot.noscroll.ui.theme.NoScrollTheme
import com.filodot.noscroll.core.model.TaskCompletionMode
import com.filodot.noscroll.core.model.TaskTarget
import com.filodot.noscroll.core.model.TaskType
import org.junit.Rule
import org.junit.Test

class BlockingOverlayScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun taskGateKeepsSolveLaterVisibleAndCheckDisabledForBlankAnswer() {
        var lastAction: BlockingOverlayAction? = null
        composeRule.setOverlay(taskOverlay(), onAction = { lastAction = it })

        composeRule.onNodeWithText("Проверить").assertIsNotEnabled()
        composeRule.onNodeWithText("Решить позже").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Emergency Stop").assertIsDisplayed()
        composeRule.runOnIdle {
            check(lastAction == BlockingOverlayAction.ExitYouTube)
        }
    }

    @Test
    fun expressionHasMeaningfulTalkBackDescription() {
        composeRule.setOverlay(taskOverlay())

        composeRule.onNodeWithContentDescription("семнадцать плюс двадцать шесть")
            .assertIsDisplayed()
    }

    @Test
    fun thirdErrorShowsCalmInlineMessageAndAnotherTaskAction() {
        composeRule.setOverlay(
            taskOverlay().copy(
                enforcement = task().copy(
                    wrongAttempts = 3,
                    answerStatus = TaskAnswerStatus.INCORRECT,
                ),
            ),
        )

        composeRule.onNodeWithText("Не получилось. Попробуйте ещё раз").assertIsDisplayed()
        composeRule.onNodeWithText("Другой пример").assertIsDisplayed()
    }

    @Test
    fun dailyLimitContainsNoTaskOrExtraTimeAction() {
        composeRule.setOverlay(
            BlockingOverlayUiState(
                enforcement = EnforcementUiState.DailyLimit(
                    usedMinutes = 45,
                    limitMinutes = 45,
                ),
            ),
        )

        composeRule.onNodeWithText("Дневной лимит YouTube исчерпан").assertIsDisplayed()
        composeRule.onNodeWithText("Сегодня: 45 из 45 минут").assertIsDisplayed()
        composeRule.onNodeWithText("На главный экран").assertIsDisplayed()
        composeRule.onNodeWithText("Проверить").assertDoesNotExist()
        composeRule.onNodeWithText("Другой пример").assertDoesNotExist()
    }

    @Test
    fun emergencyConfirmRejectsFourTrimmedCharacters() {
        composeRule.setOverlay(
            taskOverlay().copy(
                emergencyForm = EmergencyFormUiState(reason = " 1234 "),
            ),
        )
        composeRule.onNodeWithText("Отключить блокировку").assertIsNotEnabled()
    }

    @Test
    fun emergencyConfirmAcceptsFiveTrimmedCharacters() {
        composeRule.setOverlay(
            taskOverlay().copy(
                emergencyForm = EmergencyFormUiState(reason = " 12345 "),
            ),
        )
        composeRule.onNodeWithText("Отключить блокировку").assertIsEnabled()
        composeRule.onNodeWithText("7 / 300").assertIsDisplayed()
    }

    @Test
    fun solveLaterAndEmergencyRemainReachableAtTwoHundredPercentFontScale() {
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density,
                    fontScale = 2f,
                ),
            ) {
                NoScrollTheme {
                    BlockingOverlayScreen(state = taskOverlay(), onAction = {})
                }
            }
        }

        composeRule.onNodeWithText("Решить позже").assertIsDisplayed()
        composeRule.onNodeWithText("Emergency Stop").assertIsDisplayed()
    }

    @Test
    fun solvedTaskOffersExplicitYouTubeLaunch() {
        var lastAction: BlockingOverlayAction? = null
        composeRule.setOverlay(
            taskOverlay().copy(
                enforcement = task().copy(answerStatus = TaskAnswerStatus.CORRECT),
            ),
            onAction = { lastAction = it },
        )

        composeRule.onNodeWithText("Открыть YouTube").performClick()

        composeRule.runOnIdle {
            check(lastAction == BlockingOverlayAction.OpenYouTube)
        }
    }

    @Test
    fun manualInstagramTaskShowsInstructionWithoutAnswerKeyboard() {
        var lastAction: BlockingOverlayAction? = null
        composeRule.setOverlay(
            BlockingOverlayUiState(
                enforcement = task().copy(
                    visualExpression = "Сделайте 10 отжиманий в комфортном темпе",
                    target = TaskTarget.INSTAGRAM,
                    type = TaskType.PUSH_UPS,
                    completionMode = TaskCompletionMode.MANUAL_CONFIRMATION,
                ),
            ),
            onAction = { lastAction = it },
        )

        composeRule.onNodeWithText("Сделайте 10 отжиманий в комфортном темпе")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Выполнено").performClick()
        composeRule.onNodeWithText("Ответ").assertDoesNotExist()
        composeRule.runOnIdle { check(lastAction == BlockingOverlayAction.SubmitAnswer) }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setOverlay(
        state: BlockingOverlayUiState,
        onAction: (BlockingOverlayAction) -> Unit = {},
    ) {
        setContent {
            NoScrollTheme {
                BlockingOverlayScreen(state = state, onAction = onAction)
            }
        }
    }

    private fun taskOverlay() = BlockingOverlayUiState(enforcement = task())

    private fun task() = EnforcementUiState.TaskGate(
        taskId = "task-1",
        visualExpression = "17 + 26",
        spokenExpression = "семнадцать плюс двадцать шесть",
        grantMinutes = 5,
    )
}
