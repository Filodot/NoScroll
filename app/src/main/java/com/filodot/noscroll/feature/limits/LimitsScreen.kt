package com.filodot.noscroll.feature.limits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.filodot.noscroll.core.settings.DAILY_LIMIT_RANGE
import com.filodot.noscroll.core.settings.DAILY_LIMIT_STEP_MINUTES
import com.filodot.noscroll.core.settings.LimitPresets
import com.filodot.noscroll.core.settings.SHORTS_INTERVAL_RANGE

@Composable
fun LimitsRoute(
    stateHolder: LimitsStateHolder,
    modifier: Modifier = Modifier,
) {
    val state by stateHolder.state.collectAsStateWithLifecycle()
    LimitsScreen(
        state = state,
        onAction = stateHolder::dispatch,
        modifier = modifier,
    )
}

@Composable
fun LimitsScreen(
    state: LimitsUiState,
    onAction: (LimitsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (state.hasUnsavedChanges) {
                UnsavedActions(onAction)
            }
        },
    ) { contentPadding ->
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(contentPadding)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "Ограничения",
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Настройте паузы Shorts и общий дневной предел отдельно.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    state.announcement?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = it,
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    SectionHeading("Режим")
                    Spacer(Modifier.height(12.dp))
                    PresetPicker(
                        selectedPreset = state.draft.preset,
                        onSelected = { onAction(LimitsAction.SelectPreset(it)) },
                    )
                    Spacer(Modifier.height(20.dp))
                    ShortsSettingsCard(state.draft, onAction)
                    Spacer(Modifier.height(16.dp))
                    DailySettingsCard(state.draft, onAction)
                    if (state.showDailyBeforeShortsWarning) {
                        Spacer(Modifier.height(16.dp))
                        InformationMessage(
                            "Дневной лимит может сработать раньше следующей паузы Shorts. " +
                                "Сохранение доступно.",
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    SummaryCard(state.summary)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun PresetPicker(
    selectedPreset: LimitPreset,
    onSelected: (LimitPreset) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LimitPresets.all.forEach { definition ->
            val label = when (definition.preset) {
                LimitPreset.GENTLE -> "Мягкий"
                LimitPreset.BALANCED -> "Сбалансированный"
                LimitPreset.STRICT -> "Строгий"
                LimitPreset.CUSTOM -> "Свой"
            }
            val values = if (definition.preset == LimitPreset.CUSTOM) {
                "Ручная настройка"
            } else {
                "${definition.shortsIntervalMinutes}/${definition.dailyLimitMinutes} мин"
            }
            PresetOption(
                label = label,
                values = values,
                recommended = definition.recommended,
                selected = selectedPreset == definition.preset,
                onClick = { onSelected(definition.preset) },
            )
        }
    }
}

@Composable
private fun PresetOption(
    label: String,
    values: String,
    recommended: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(176.dp)
            .heightIn(min = 108.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(16.dp),
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
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(text = values, style = MaterialTheme.typography.bodyMedium)
            if (recommended) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Рекомендуем",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ShortsSettingsCard(
    values: LimitsValues,
    onAction: (LimitsAction) -> Unit,
) {
    SettingsCard(
        title = "Пауза в Shorts",
        enabled = values.shortsEnabled,
        onEnabledChange = { onAction(LimitsAction.SetShortsEnabled(it)) },
    ) {
        ValueHeader(
            value = "${values.shortsMinutes} мин",
            recommended = values.shortsMinutes == 5,
        )
        Slider(
            value = values.shortsMinutes.toFloat(),
            onValueChange = {
                onAction(LimitsAction.SetShortsMinutes(it.toInt()))
            },
            valueRange = SHORTS_INTERVAL_RANGE.first.toFloat()..
                SHORTS_INTERVAL_RANGE.last.toFloat(),
            steps = SHORTS_INTERVAL_RANGE.count() - 2,
            enabled = values.shortsEnabled,
            modifier = Modifier.semantics {
                stateDescription = "${values.shortsMinutes} минут, диапазон от 1 до 30"
            },
        )
        Stepper(
            valueLabel = "${values.shortsMinutes} минут",
            decrementDescription = "Уменьшить паузу Shorts",
            incrementDescription = "Увеличить паузу Shorts",
            canDecrement = values.shortsEnabled &&
                values.shortsMinutes > SHORTS_INTERVAL_RANGE.first,
            canIncrement = values.shortsEnabled &&
                values.shortsMinutes < SHORTS_INTERVAL_RANGE.last,
            onDecrement = { onAction(LimitsAction.DecrementShorts) },
            onIncrement = { onAction(LimitsAction.IncrementShorts) },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Рекомендуем: 5 минут",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DailySettingsCard(
    values: LimitsValues,
    onAction: (LimitsAction) -> Unit,
) {
    SettingsCard(
        title = "Дневной YouTube",
        enabled = values.dailyEnabled,
        onEnabledChange = { onAction(LimitsAction.SetDailyEnabled(it)) },
    ) {
        ValueHeader(
            value = "${values.dailyMinutes} мин",
            recommended = values.dailyMinutes == 45,
        )
        Slider(
            value = values.dailyMinutes.toFloat(),
            onValueChange = {
                onAction(LimitsAction.SetDailyMinutes(it.toInt()))
            },
            valueRange = DAILY_LIMIT_RANGE.first.toFloat()..DAILY_LIMIT_RANGE.last.toFloat(),
            steps = DAILY_LIMIT_RANGE.count() / DAILY_LIMIT_STEP_MINUTES - 1,
            enabled = values.dailyEnabled,
            modifier = Modifier.semantics {
                stateDescription = "${values.dailyMinutes} минут, диапазон от 10 до 240"
            },
        )
        Stepper(
            valueLabel = "${values.dailyMinutes} минут",
            decrementDescription = "Уменьшить дневной лимит",
            incrementDescription = "Увеличить дневной лимит",
            canDecrement = values.dailyEnabled && values.dailyMinutes > DAILY_LIMIT_RANGE.first,
            canIncrement = values.dailyEnabled && values.dailyMinutes < DAILY_LIMIT_RANGE.last,
            onDecrement = { onAction(LimitsAction.DecrementDaily) },
            onIncrement = { onAction(LimitsAction.IncrementDaily) },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Рекомендуем: 45 минут",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.semantics {
                        stateDescription = if (enabled) "Включено" else "Выключено"
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun ValueHeader(
    value: String,
    recommended: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (recommended) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = "Рекомендуем",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun Stepper(
    valueLabel: String,
    decrementDescription: String,
    incrementDescription: String,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onDecrement,
            enabled = canDecrement,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = decrementDescription },
        ) {
            Text("−")
        }
        Text(
            text = valueLabel,
            modifier = Modifier
                .widthIn(min = 112.dp)
                .padding(horizontal = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedButton(
            onClick = onIncrement,
            enabled = canIncrement,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = incrementDescription },
        ) {
            Text("+")
        }
    }
}

@Composable
private fun SummaryCard(summary: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionHeading("Как это будет работать")
            Spacer(Modifier.height(10.dp))
            Text(
                text = summary,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun InformationMessage(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun UnsavedActions(onAction: (LimitsAction) -> Unit) {
    Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { onAction(LimitsAction.Cancel) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Text("Отменить")
            }
            Button(
                onClick = { onAction(LimitsAction.Save) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Text("Сохранить")
            }
        }
    }
}
