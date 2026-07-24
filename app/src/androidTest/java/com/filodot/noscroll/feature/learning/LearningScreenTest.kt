package com.filodot.noscroll.feature.learning

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.filodot.noscroll.core.learning.content.StaticLearningCatalog
import com.filodot.noscroll.core.learning.model.LearningCourseContent
import com.filodot.noscroll.core.testing.InMemoryLearningRepository
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
                LearningRoute(repository)
            }
        }

        composeRule.onNodeWithText("Открыть курс").performClick()
        composeRule.onNodeWithText("Начать следующий урок").performClick()
        composeRule.onNodeWithText("age = 18").performClick()
        composeRule.onNodeWithText("Проверить").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Следующее задание").performClick()
        composeRule.onNodeWithText("Проверить").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Следующее задание").performClick()
        composeRule.onNodeWithText("Что выведет код?").performTextInput("8")
        composeRule.onNodeWithText("Проверить").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Завершить урок").performClick()

        composeRule.onNodeWithText("Урок завершён").assertIsDisplayed()
    }

    @Test
    fun suspiciousTaskCanBeReplacedWithoutSubmittingAnswer() {
        val repository = repository()
        composeRule.setContent {
            NoScrollTheme {
                LearningRoute(repository)
            }
        }

        composeRule.onNodeWithText("Открыть курс").performClick()
        composeRule.onNodeWithText("Начать следующий урок").performClick()
        composeRule.onNodeWithText("Задание выглядит некорректным").performClick()

        composeRule.onNodeWithText("Подозрительное задание заменено без штрафа")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Расположите действия программы в порядке выполнения.")
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
