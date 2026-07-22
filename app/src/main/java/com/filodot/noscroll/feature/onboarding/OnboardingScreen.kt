package com.filodot.noscroll.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filodot.noscroll.core.model.LimitPreset
import com.filodot.noscroll.core.settings.LimitPresets

private const val AccessibilityDisclosure =
    "Чтобы распознавать Shorts, учитывать время в Instagram и вовремя останавливать ленту, " +
        "NoScroll получает события интерфейса этих приложений. Сервис может проверять элементы " +
        "активного экрана и выполнить системное действие «Назад», когда доступ закончился. " +
        "Задание показывается только внутри приложения NoScroll. Данные обрабатываются на " +
        "устройстве: NoScroll не " +
        "сохраняет содержимое экрана, названия видео, историю просмотров или введённый в " +
        "YouTube текст и никуда их не отправляет."

private const val UsageAccessDisclosure =
    "Для общего дневного лимита NoScroll читает длительность использования YouTube. Доступ не " +
        "раскрывает содержимое видео или действия внутри YouTube. Без него задания в Shorts " +
        "продолжат работать, но общий дневной лимит будет недоступен."

@Composable
fun OnboardingRoute(
    stateHolder: OnboardingStateHolder,
    modifier: Modifier = Modifier,
) {
    val state by stateHolder.state.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
        onAction = stateHolder::dispatch,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        when (state.step) {
            OnboardingStep.WELCOME -> WelcomeScreen(onAction)
            OnboardingStep.PRESET -> PresetScreen(state, onAction)
            OnboardingStep.ACCESSIBILITY_DISCLOSURE ->
                AccessibilityDisclosureScreen(state, onAction)

            OnboardingStep.USAGE_ACCESS -> UsageAccessScreen(state, onAction)
            OnboardingStep.READINESS -> ReadinessScreen(state, onAction)
        }
    }
}

@Composable
private fun WelcomeScreen(onAction: (OnboardingAction) -> Unit) {
    OnboardingPage(step = 1) {
        Surface(
            modifier = Modifier
                .size(64.dp)
                .semantics { contentDescription = "Логотип NoScroll" },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "N",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        ScreenTitle("Остановите автоматический скролл")
        Spacer(Modifier.height(12.dp))
        Text(
            text = "NoScroll делает короткие осознанные паузы в Shorts и Instagram и помогает " +
                "соблюдать общий лимит YouTube",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Bullet("Без аккаунта")
        Bullet("Всё хранится на телефоне")
        Bullet("Ограничения можно отключить")
        Spacer(Modifier.height(32.dp))
        PrimaryAction("Настроить") { onAction(OnboardingAction.Configure) }
        TextButton(
            onClick = { onAction(OnboardingAction.ShowHowItWorks) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Как это работает")
        }
    }
}

@Composable
private fun PresetScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
) {
    OnboardingPage(step = 2) {
        ScreenTitle("Выберите комфортный режим")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Первое значение — минуты Shorts до задания. Второе — общий лимит " +
                "YouTube в сутки.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        LimitPresets.all.forEach { definition ->
            val label = when (definition.preset) {
                LimitPreset.GENTLE -> "Мягкий"
                LimitPreset.BALANCED -> "Сбалансированный"
                LimitPreset.STRICT -> "Строгий"
                LimitPreset.CUSTOM -> "Настроить вручную"
            }
            val detail = if (definition.preset == LimitPreset.CUSTOM) {
                "Текущие значения: ${state.shortsIntervalMinutes} / ${state.dailyLimitMinutes} мин"
            } else {
                "${definition.shortsIntervalMinutes} / ${definition.dailyLimitMinutes} мин"
            }
            PresetCard(
                label = label,
                detail = detail,
                recommended = definition.recommended,
                selected = state.selectedPreset == definition.preset,
                onClick = { onAction(OnboardingAction.SelectPreset(definition.preset)) },
            )
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(12.dp))
        PrimaryAction("Продолжить") { onAction(OnboardingAction.ContinueFromPreset) }
    }
}

@Composable
private fun AccessibilityDisclosureScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
) {
    OnboardingPage(step = 3) {
        ScreenTitle("Доступ к YouTube и Instagram")
        Spacer(Modifier.height(12.dp))
        Text(
            text = AccessibilityDisclosure,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        ConsentRow(
            checked = state.accessibilityConsentChecked,
            onCheckedChange = {
                onAction(OnboardingAction.SetAccessibilityConsent(it))
            },
        )
        if (state.accessibilityReturnFailed) {
            Spacer(Modifier.height(12.dp))
            InlineMessage(
                text = "Доступ пока не включён. На Android 13+ после установки APK откройте " +
                    "карточку приложения, выберите меню ⋮ → «Разрешить ограниченные настройки», " +
                    "затем снова включите службу Accessibility.",
                error = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onAction(OnboardingAction.OpenAppDetailsSettings) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Открыть карточку приложения")
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryAction(
            label = if (state.waitingForAccessibilityReturn) {
                "Проверить доступ"
            } else {
                "Перейти в настройки Android"
            },
            enabled = state.accessibilityConsentChecked,
        ) { onAction(OnboardingAction.RequestAccessibilitySettings) }
        TextButton(
            onClick = { onAction(OnboardingAction.OpenPrivacyPolicy) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Политика приватности")
        }
        TextButton(
            onClick = { onAction(OnboardingAction.SkipAccessibility) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Не сейчас")
        }
    }
}

@Composable
private fun UsageAccessScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
) {
    OnboardingPage(step = 4) {
        ScreenTitle("Дневной лимит YouTube")
        Spacer(Modifier.height(12.dp))
        Text(
            text = UsageAccessDisclosure,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (state.usageReturnFailed) {
            Spacer(Modifier.height(12.dp))
            InlineMessage(
                text = "Доступ к статистике пока не включён. Дневной лимит останется " +
                    "недоступен.",
                error = true,
            )
        }
        Spacer(Modifier.height(28.dp))
        PrimaryAction(
            label = if (state.waitingForUsageReturn) {
                "Проверить доступ"
            } else {
                "Разрешить доступ к статистике"
            },
        ) { onAction(OnboardingAction.RequestUsageAccessSettings) }
        TextButton(
            onClick = { onAction(OnboardingAction.SkipUsageAccess) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Продолжить без дневного лимита")
        }
    }
}

@Composable
private fun ReadinessScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
) {
    OnboardingPage(step = 5) {
        ScreenTitle("Проверим готовность")
        Spacer(Modifier.height(20.dp))
        PermissionStatusRow(
            label = "Доступ к YouTube и Instagram",
            status = state.accessibilityStatus,
        )
        HorizontalDivider()
        PermissionStatusRow(
            label = "Доступ к статистике",
            status = state.usageAccessStatus,
        )
        HorizontalDivider()
        SummaryRow(
            label = "Режим",
            value = presetSummary(state),
        )
        HorizontalDivider()
        SummaryRow(
            label = "Приложение YouTube",
            value = if (state.youtubeInstalled) "Установлено" else "Не найдено",
        )
        HorizontalDivider()
        SummaryRow(
            label = "Приложение Instagram",
            value = if (state.instagramInstalled) "Установлено" else "Не найдено",
        )
        if (!state.youtubeInstalled) {
            Spacer(Modifier.height(16.dp))
            InlineMessage(
                text = "YouTube не найден. Защита начнёт работать после установки приложения.",
                error = false,
            )
        }
        if (!state.instagramInstalled) {
            Spacer(Modifier.height(16.dp))
            InlineMessage(
                text = "Instagram не найден. Его ограничение начнёт работать после установки.",
                error = false,
            )
        }
        if (state.accessibilityStatus != PermissionUiStatus.ENABLED) {
            Spacer(Modifier.height(16.dp))
            InlineMessage(
                text = "Чтобы запустить защиту, включите доступ к YouTube и Instagram.",
                error = true,
            )
        }
        Spacer(Modifier.height(28.dp))
        PrimaryAction(
            label = "Начать",
            enabled = state.canStart,
        ) { onAction(OnboardingAction.Start) }
        if (
            state.accessibilityStatus != PermissionUiStatus.ENABLED ||
            state.usageAccessStatus != PermissionUiStatus.ENABLED
        ) {
            OutlinedButton(
                onClick = { onAction(OnboardingAction.ReviewMissingPermissions) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Проверить доступы")
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    step: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(
            text = "Шаг $step из 5",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ScreenTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineMedium,
    )
}

@Composable
private fun Bullet(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "•",
            modifier = Modifier.padding(end = 12.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PresetCard(
    label: String,
    detail: String,
    recommended: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = label, style = MaterialTheme.typography.titleMedium)
                    if (recommended) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = "Рекомендуем",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(text = detail, style = MaterialTheme.typography.bodyMedium)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun ConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = "Я понимаю, зачем нужен доступ",
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    status: PermissionUiStatus,
) {
    val statusText = when (status) {
        PermissionUiStatus.NOT_GRANTED -> "Не включён"
        PermissionUiStatus.ENABLED -> "Включён"
        PermissionUiStatus.SKIPPED -> "Пропущен"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { stateDescription = statusText }
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = statusText,
            color = if (status == PermissionUiStatus.ENABLED) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(text = value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun InlineMessage(
    text: String,
    error: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(12.dp),
        color = if (error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            color = if (error) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PrimaryAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(label)
    }
}

private fun presetSummary(state: OnboardingUiState): String {
    val label = when (state.selectedPreset) {
        LimitPreset.GENTLE -> "Мягкий"
        LimitPreset.BALANCED -> "Сбалансированный"
        LimitPreset.STRICT -> "Строгий"
        LimitPreset.CUSTOM -> "Свой"
    }
    return "$label · ${state.shortsIntervalMinutes}/${state.dailyLimitMinutes} мин"
}
