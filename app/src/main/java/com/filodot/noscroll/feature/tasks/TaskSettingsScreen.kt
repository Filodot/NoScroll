package com.filodot.noscroll.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.filodot.noscroll.core.model.CustomTaskPreset
import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskTarget
import com.filodot.noscroll.core.model.TaskType

data class TaskSettingsUiState(
    val loadMinutes: Int,
    val currentDifficulty: TaskDifficulty,
    val mediumThresholdMinutes: Int,
    val hardThresholdMinutes: Int,
    val decayBreakMinutes: Int,
    val enabledTypes: Set<TaskType>,
    val presets: List<CustomTaskPreset>,
    val instagramEnabled: Boolean,
)

sealed interface TaskSettingsAction {
    data class SetMediumThreshold(val minutes: Int) : TaskSettingsAction
    data class SetHardThreshold(val minutes: Int) : TaskSettingsAction
    data class SetDecayBreakMinutes(val minutes: Int) : TaskSettingsAction
    data class SetTaskTypeEnabled(val type: TaskType, val enabled: Boolean) : TaskSettingsAction
    data class CreatePreset(val title: String, val instruction: String) : TaskSettingsAction
    data class SetPresetEnabled(val preset: CustomTaskPreset, val enabled: Boolean) :
        TaskSettingsAction

    data class DeletePreset(val presetId: String) : TaskSettingsAction
    data class PrepareAccess(val target: TaskTarget) : TaskSettingsAction
}

@Composable
fun TaskSettingsScreen(
    state: TaskSettingsUiState,
    onAction: (TaskSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Задания",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = "Сложность зависит от накопленной нагрузки Shorts, а тип задания выбирается " +
                "из включённых вариантов.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        LoadCard(state)
        PrepareAccessCard(state.instagramEnabled, onAction)
        DifficultyCard(state, onAction)
        TaskTypesCard(state, onAction)
        CustomPresetsCard(state.presets, onAction)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LoadCard(state: TaskSettingsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Текущая нагрузка", style = MaterialTheme.typography.labelLarge)
            Text("${state.loadMinutes} мин", style = MaterialTheme.typography.headlineMedium)
            Text(
                when (state.currentDifficulty) {
                    TaskDifficulty.EASY -> "Следующая задача: лёгкая"
                    TaskDifficulty.MEDIUM -> "Следующая задача: средняя"
                    TaskDifficulty.HARD -> "Следующая задача: сложная"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Нагрузка не сбрасывается в полночь и уменьшается только во время перерыва.",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PrepareAccessCard(
    instagramEnabled: Boolean,
    onAction: (TaskSettingsAction) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Подготовить доступ")
            Text("Решите входное задание до открытия отвлекающего приложения.")
            Button(
                onClick = { onAction(TaskSettingsAction.PrepareAccess(TaskTarget.YOUTUBE_SHORTS)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Разрешить YouTube Shorts") }
            OutlinedButton(
                onClick = { onAction(TaskSettingsAction.PrepareAccess(TaskTarget.INSTAGRAM)) },
                enabled = instagramEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Разрешить Instagram") }
        }
    }
}

@Composable
private fun DifficultyCard(
    state: TaskSettingsUiState,
    onAction: (TaskSettingsAction) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle("Шкала сложности")
            NumberSetting(
                title = "Средняя с",
                value = state.mediumThresholdMinutes,
                suffix = "мин нагрузки",
                onDecrease = {
                    onAction(TaskSettingsAction.SetMediumThreshold(state.mediumThresholdMinutes - 1))
                },
                onIncrease = {
                    onAction(TaskSettingsAction.SetMediumThreshold(state.mediumThresholdMinutes + 1))
                },
            )
            NumberSetting(
                title = "Сложная с",
                value = state.hardThresholdMinutes,
                suffix = "мин нагрузки",
                onDecrease = {
                    onAction(TaskSettingsAction.SetHardThreshold(state.hardThresholdMinutes - 1))
                },
                onIncrease = {
                    onAction(TaskSettingsAction.SetHardThreshold(state.hardThresholdMinutes + 1))
                },
            )
            NumberSetting(
                title = "Скорость восстановления",
                value = state.decayBreakMinutes,
                suffix = "мин перерыва за −1 мин",
                onDecrease = {
                    onAction(TaskSettingsAction.SetDecayBreakMinutes(state.decayBreakMinutes - 1))
                },
                onIncrease = {
                    onAction(TaskSettingsAction.SetDecayBreakMinutes(state.decayBreakMinutes + 1))
                },
            )
            Text(
                "Рекомендуем: средняя 10, сложная 25, восстановление 5 минут.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NumberSetting(
    title: String,
    value: Int,
    suffix: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onDecrease, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("−")
            }
            Text("$value $suffix", modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onIncrease, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("+")
            }
        }
    }
}

@Composable
private fun TaskTypesCard(
    state: TaskSettingsUiState,
    onAction: (TaskSettingsAction) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Типы заданий")
            TaskType.entries.forEach { type ->
                val enabled = type in state.enabledTypes
                val hasCustom = state.presets.any(CustomTaskPreset::enabled)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(taskTypeLabel(type), style = MaterialTheme.typography.titleMedium)
                        Text(taskTypeDescription(type), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = enabled,
                        enabled = type != TaskType.CUSTOM || hasCustom || enabled,
                        onCheckedChange = { checked ->
                            onAction(TaskSettingsAction.SetTaskTypeEnabled(type, checked))
                        },
                    )
                }
            }
            Text(
                "Если включено несколько типов, NoScroll чередует их.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CustomPresetsCard(
    presets: List<CustomTaskPreset>,
    onAction: (TaskSettingsAction) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var instruction by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Свои задания")
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(60) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название пресета") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = instruction,
                onValueChange = { instruction = it.take(240) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Что нужно сделать") },
                minLines = 2,
                maxLines = 4,
            )
            Button(
                onClick = {
                    onAction(TaskSettingsAction.CreatePreset(title.trim(), instruction.trim()))
                    title = ""
                    instruction = ""
                },
                enabled = title.isNotBlank() && instruction.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Сохранить пресет") }
            presets.forEach { preset ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(preset.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = preset.enabled,
                                onCheckedChange = { enabled ->
                                    onAction(TaskSettingsAction.SetPresetEnabled(preset, enabled))
                                },
                            )
                        }
                        Text(preset.instruction)
                        TextButton(onClick = { onAction(TaskSettingsAction.DeletePreset(preset.id)) }) {
                            Text("Удалить")
                        }
                    }
                }
            }
            if (presets.isEmpty()) {
                Text("Пока нет сохранённых пресетов", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}

private fun taskTypeLabel(type: TaskType): String = when (type) {
    TaskType.ARITHMETIC -> "Арифметика"
    TaskType.PUSH_UPS -> "Отжимания"
    TaskType.CUSTOM -> "Мои пресеты"
}

private fun taskTypeDescription(type: TaskType): String = when (type) {
    TaskType.ARITHMETIC -> "Ответ проверяется автоматически"
    TaskType.PUSH_UPS -> "5 / 10 / 20 повторений по сложности"
    TaskType.CUSTOM -> "Одно из сохранённых вами заданий"
}
