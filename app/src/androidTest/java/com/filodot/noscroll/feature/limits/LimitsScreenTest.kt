package com.filodot.noscroll.feature.limits

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.filodot.noscroll.core.model.LimitPreset
import com.filodot.noscroll.ui.theme.NoScrollTheme
import org.junit.Rule
import org.junit.Test

class LimitsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun balancedPresetIsSelectedAndValuesHaveContextualTalkBackText() {
        composeRule.setLimits(LimitsUiState())

        composeRule.onNode(hasText("Сбалансированный") and hasClickAction()).assertIsSelected()
        composeRule.onAllNodesWithText("Рекомендуем").assertCountEquals(3)
        composeRule.onNode(hasStateDescription("5 минут, диапазон от 1 до 30"))
            .assertIsDisplayed()
        composeRule.onNode(hasStateDescription("45 минут, диапазон от 10 до 240"))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun unsavedStateShowsStickySaveAndCancelActions() {
        val state = LimitsUiState(
            draft = LimitsValues(
                preset = LimitPreset.CUSTOM,
                shortsMinutes = 6,
            ),
        )

        composeRule.setLimits(state)

        composeRule.onNodeWithText("Сохранить").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("Отменить").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun saveAndCancelEmitTypedActions() {
        val actions = mutableListOf<LimitsAction>()
        val state = LimitsUiState(
            draft = LimitsValues(
                preset = LimitPreset.CUSTOM,
                dailyMinutes = 50,
            ),
        )
        composeRule.setLimits(state, actions::add)

        composeRule.onNodeWithText("Отменить").performClick()
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.runOnIdle {
            check(actions == listOf(LimitsAction.Cancel, LimitsAction.Save))
        }
    }

    @Test
    fun dailyBeforeShortsIsInformationalAndDoesNotDisableSave() {
        composeRule.setLimits(
            LimitsUiState(
                draft = LimitsValues(
                    preset = LimitPreset.CUSTOM,
                    shortsMinutes = 20,
                    dailyMinutes = 10,
                ),
            ),
        )

        composeRule.onNodeWithText(
            "Дневной лимит может сработать раньше следующей паузы Shorts. " +
                "Сохранение доступно.",
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsEnabled()
    }

    @Test
    fun steppersDisableOnlyTheDirectionsBeyondDocumentedBounds() {
        val values = LimitsValues(
            preset = LimitPreset.CUSTOM,
            shortsMinutes = 1,
            dailyMinutes = 240,
        )
        composeRule.setLimits(LimitsUiState(saved = values, draft = values))

        composeRule.onNodeWithContentDescription("Уменьшить паузу Shorts")
            .assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Увеличить паузу Shorts")
            .assertIsEnabled()
        composeRule.onNodeWithContentDescription("Уменьшить дневной лимит")
            .assertIsEnabled()
        composeRule.onNodeWithContentDescription("Увеличить дневной лимит")
            .assertIsNotEnabled()
    }

    @Test
    fun stickyActionsRemainVisibleAtTwoHundredPercentFontScale() {
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density,
                    fontScale = 2f,
                ),
            ) {
                NoScrollTheme {
                    LimitsScreen(
                        state = LimitsUiState(
                            draft = LimitsValues(
                                preset = LimitPreset.CUSTOM,
                                shortsMinutes = 6,
                            ),
                        ),
                        onAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        composeRule.onNodeWithText("Отменить").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setLimits(
        state: LimitsUiState,
        onAction: (LimitsAction) -> Unit = {},
    ) {
        setContent {
            NoScrollTheme {
                LimitsScreen(state = state, onAction = onAction)
            }
        }
    }
}
