package com.filodot.noscroll.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.filodot.noscroll.ui.theme.NoScrollTheme
import org.junit.Rule
import org.junit.Test

class EmergencyHistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateUsesApprovedCopyWithoutMandatoryCta() {
        composeRule.setHistory(EmergencyHistoryUiState())

        composeRule.onNodeWithText("Здесь появятся ваши Emergency Stop").assertIsDisplayed()
    }

    @Test
    fun loadErrorOffersTypedRetryAction() {
        var lastAction: EmergencyHistoryAction? = null
        composeRule.setHistory(
            EmergencyHistoryUiState(loadError = "Локальная база недоступна"),
            onAction = { lastAction = it },
        )

        composeRule.onNodeWithText("Повторить").performClick()
        composeRule.runOnIdle {
            check(lastAction == EmergencyHistoryAction.RetryLoad)
        }
    }

    @Test
    fun activeEventShowsReasonAndOngoingUsageState() {
        composeRule.setHistory(EmergencyHistoryUiState(items = listOf(activeItem())))

        composeRule.onNodeWithText("Активен").assertIsDisplayed()
        composeRule.onNodeWithText("Учебная трансляция").assertIsDisplayed()
        composeRule.onNodeWithText("Включён Сегодня, 14:32. Учёт времени продолжается.")
            .assertIsDisplayed()
    }

    @Test
    fun deleteConfirmationListsDataAndPreservesActiveEmergency() {
        composeRule.setHistory(
            EmergencyHistoryUiState(
                items = listOf(activeItem()),
                deleteConfirmationVisible = true,
            ),
        )

        composeRule.onAllNodesWithText("Удалить локальную историю").assertCountEquals(2)
        composeRule.onNodeWithText("Активный Emergency Stop останется включён.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Настройки лимитов и системные разрешения не изменятся.")
            .assertIsDisplayed()
    }

    @Test
    fun completedHistoryAndDeleteControlRemainReachable() {
        composeRule.setHistory(
            EmergencyHistoryUiState(items = listOf(completedItem())),
        )

        composeRule.onNodeWithText("Рабочая инструкция").assertIsDisplayed()
        composeRule.onNodeWithText("Удалить локальную историю")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setHistory(
        state: EmergencyHistoryUiState,
        onAction: (EmergencyHistoryAction) -> Unit = {},
    ) {
        setContent {
            NoScrollTheme {
                EmergencyHistoryScreen(state = state, onAction = onAction)
            }
        }
    }

    private fun activeItem() = EmergencyHistoryItemUi(
        id = "active",
        reason = "Учебная трансляция",
        activatedAtLabel = "Сегодня, 14:32",
        durationMinutes = 18,
        youtubeMinutesDuring = 12,
        sourceLabel = "Сегодня",
    )

    private fun completedItem() = EmergencyHistoryItemUi(
        id = "completed",
        reason = "Рабочая инструкция",
        activatedAtLabel = "Вчера, 10:15",
        deactivatedAtLabel = "10:42",
        durationMinutes = 27,
        youtubeMinutesDuring = 19,
        sourceLabel = "Задание",
    )
}
