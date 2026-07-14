package com.filodot.noscroll.feature.onboarding

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.filodot.noscroll.ui.theme.NoScrollTheme
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun accessibilityDisclosure_requiresConsentAndKeepsRefusalVisible() {
        var lastAction: OnboardingAction? = null
        composeRule.setContent {
            NoScrollTheme {
                OnboardingScreen(
                    state = OnboardingUiState(
                        step = OnboardingStep.ACCESSIBILITY_DISCLOSURE,
                    ),
                    onAction = { lastAction = it },
                )
            }
        }

        composeRule.onNodeWithText("Перейти в настройки Android").assertIsNotEnabled()
        composeRule.onNodeWithText("Не сейчас").assertIsDisplayed()
        composeRule.onNodeWithText("Я понимаю, зачем нужен доступ").performClick()

        composeRule.runOnIdle {
            check(lastAction == OnboardingAction.SetAccessibilityConsent(true))
        }
    }

    @Test
    fun accessibilityDisclosure_enablesPrimaryActionAfterConsent() {
        composeRule.setContent {
            NoScrollTheme {
                OnboardingScreen(
                    state = OnboardingUiState(
                        step = OnboardingStep.ACCESSIBILITY_DISCLOSURE,
                        accessibilityConsentChecked = true,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Перейти в настройки Android").assertIsEnabled()
    }

    @Test
    fun balancedPreset_isExposedAsSelectedRecommendedRadioOption() {
        composeRule.setContent {
            NoScrollTheme {
                OnboardingScreen(
                    state = OnboardingUiState(step = OnboardingStep.PRESET),
                    onAction = {},
                )
            }
        }

        composeRule.onNode(hasText("Сбалансированный") and hasClickAction()).assertIsSelected()
        composeRule.onNodeWithText("Рекомендуем").assertIsDisplayed()
    }

    @Test
    fun readiness_requiresAccessibilityButNotInstalledYouTube() {
        composeRule.setContent {
            NoScrollTheme {
                OnboardingScreen(
                    state = OnboardingUiState(
                        step = OnboardingStep.READINESS,
                        accessibilityStatus = PermissionUiStatus.ENABLED,
                        usageAccessStatus = PermissionUiStatus.SKIPPED,
                        youtubeInstalled = false,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("YouTube не найден. Защита начнёт работать после установки приложения.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Начать").performScrollTo().assertIsEnabled()
        composeRule.onNode(hasStateDescription("Включён")).assertIsDisplayed()
    }

    @Test
    fun disclosureActionsRemainReachableAtTwoHundredPercentFontScale() {
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density,
                    fontScale = 2f,
                ),
            ) {
                NoScrollTheme {
                    OnboardingScreen(
                        state = OnboardingUiState(
                            step = OnboardingStep.ACCESSIBILITY_DISCLOSURE,
                        ),
                        onAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Не сейчас").performScrollTo().assertIsDisplayed()
    }
}
