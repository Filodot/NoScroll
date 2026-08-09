package com.filodot.noscroll.feature.learning

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.filodot.noscroll.core.learning.content.StaticLearningCatalog
import com.filodot.noscroll.core.learning.model.LearningCourseContent
import com.filodot.noscroll.core.testing.InMemoryLearningRepository
import com.filodot.noscroll.core.testing.InMemoryAiCredentialRepository
import com.filodot.noscroll.ui.theme.NoScrollTheme
import org.junit.Rule
import org.junit.Test

class LearningScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun correctAnswerIsRequiredToCompletePreviewLesson() {
        val repository = repository()
        composeRule.setContent {
            NoScrollTheme {
                LearningRoute(repository, InMemoryAiCredentialRepository())
            }
        }

        composeRule.onNodeWithText("↓ Офлайн").assertIsDisplayed()
        composeRule.onNodeWithText("Открыть курс").performClick()
        composeRule.onNodeWithText("Начать следующий урок").performClick()
        composeRule.onNodeWithText("Материал урока").assertIsDisplayed()
        composeRule.onNodeWithText("age = 18").assertDoesNotExist()
        composeRule.onNodeWithText("Перейти к заданиям").performScrollTo().performClick()
        composeRule.onNodeWithText("age = 18").performClick()
        composeRule.onNodeWithText("Проверить")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithText("Следующее задание").performScrollTo().performClick()
        composeRule.onNodeWithText("Проверить")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithText("Следующее задание").performScrollTo().performClick()
        composeRule.onNodeWithText("Что выведет код?")
            .performScrollTo()
            .performTextInput("8")
        composeRule.onNodeWithText("Проверить")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithText("Завершить урок").performScrollTo().performClick()

        composeRule.onNodeWithText("Урок завершён").assertIsDisplayed()
    }

    @Test
    fun suspiciousTaskCanBeReplacedWithoutSubmittingAnswer() {
        val repository = repository()
        composeRule.setContent {
            NoScrollTheme {
                LearningRoute(repository, InMemoryAiCredentialRepository())
            }
        }

        composeRule.onNodeWithText("Открыть курс").performClick()
        composeRule.onNodeWithText("Начать следующий урок").performClick()
        composeRule.onNodeWithText("Перейти к заданиям").performScrollTo().performClick()
        composeRule.onNodeWithText("Задание выглядит некорректным")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText("Подозрительное задание заменено без штрафа")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Расположите действия программы в порядке выполнения.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun repository() = InMemoryLearningRepository(
        initialContent = listOf(
            LearningCourseContent(
                course = StaticLearningCatalog.pythonCourse,
                sources = emptyList(),
                curriculumNodes = listOf(StaticLearningCatalog.firstTopic),
                concepts = listOf(
                    StaticLearningCatalog.variablesConcept,
                    StaticLearningCatalog.expressionsConcept,
                ),
            ),
        ),
        initialLessons = listOf(StaticLearningCatalog.firstLesson),
    )
}
