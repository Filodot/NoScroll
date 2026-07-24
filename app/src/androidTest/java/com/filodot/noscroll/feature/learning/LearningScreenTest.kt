package com.filodot.noscroll.feature.learning

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.filodot.noscroll.ui.theme.NoScrollTheme
import org.junit.Rule
import org.junit.Test

class LearningScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun correctAnswerIsRequiredToCompletePreviewLesson() {
        composeRule.setContent {
            NoScrollTheme {
                LearningScreen()
            }
        }

        composeRule.onNodeWithText("Начать тестовый урок").performClick()
        composeRule.onNodeWithText("age = 18").performClick()
        composeRule.onNodeWithText("Проверить").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Завершить урок").performClick()

        composeRule.onNodeWithText("Урок завершён").assertIsDisplayed()
    }

    @Test
    fun suspiciousTaskCanBeReplacedWithoutSubmittingAnswer() {
        composeRule.setContent {
            NoScrollTheme {
                LearningScreen()
            }
        }

        composeRule.onNodeWithText("Начать тестовый урок").performClick()
        composeRule.onNodeWithText("Задание выглядит некорректным").performClick()

        composeRule.onNodeWithText(
            "Задание помечено для замены без штрафа. В статическом прототипе показан резервный " +
                "экземпляр.",
        ).assertIsDisplayed()
    }
}
