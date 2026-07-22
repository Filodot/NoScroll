package com.filodot.noscroll.feature.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskTrigger
import com.filodot.noscroll.core.model.TaskCompletionMode
import com.filodot.noscroll.core.model.TaskTarget
import com.filodot.noscroll.core.model.TaskType

@Composable
fun BlockingOverlayHost(
    stateHolder: BlockingOverlayStateHolder,
    modifier: Modifier = Modifier,
) {
    val state by stateHolder.state.collectAsStateWithLifecycle()
    BlockingOverlayScreen(
        state = state,
        onAction = stateHolder::dispatch,
        modifier = modifier,
    )
}

@Composable
fun BlockingOverlayScreen(
    state: BlockingOverlayUiState,
    onAction: (BlockingOverlayAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onAction(BlockingOverlayAction.SystemBack) }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        val emergencyForm = state.emergencyForm
        if (emergencyForm != null) {
            EmergencyFormScreen(form = emergencyForm, onAction = onAction)
        } else {
            EnforcementScreen(enforcement = state.enforcement, onAction = onAction)
        }
    }
}

@Composable
private fun EnforcementScreen(
    enforcement: EnforcementUiState,
    onAction: (BlockingOverlayAction) -> Unit,
) {
    Scaffold(
        bottomBar = {
            EscapeActions(
                enforcement = enforcement,
                onExit = {
                    val solvedTask = (enforcement as? EnforcementUiState.TaskGate)
                        ?.answerStatus == TaskAnswerStatus.CORRECT
                    onAction(
                        if (solvedTask) {
                            BlockingOverlayAction.OpenYouTube
                        } else {
                            BlockingOverlayAction.ExitYouTube
                        },
                    )
                },
                onEmergency = { onAction(BlockingOverlayAction.OpenEmergencyForm) },
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(contentPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NoScrollMark()
                Spacer(Modifier.height(28.dp))
                when (enforcement) {
                    is EnforcementUiState.TaskGate -> TaskGateContent(enforcement, onAction)
                    is EnforcementUiState.DailyLimit -> DailyLimitContent(enforcement)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TaskGateContent(
    task: EnforcementUiState.TaskGate,
    onAction: (BlockingOverlayAction) -> Unit,
) {
    val appLabel = if (task.target == TaskTarget.INSTAGRAM) "Instagram" else "Shorts"
    OverlayTitle(
        if (task.trigger == TaskTrigger.ENTRY) "Вход в $appLabel" else "Пора сделать паузу",
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = when (task.difficulty) {
            TaskDifficulty.EASY -> "Лёгкая задача"
            TaskDifficulty.MEDIUM -> "Средняя задача"
            TaskDifficulty.HARD -> "Сложная задача"
        },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = if (task.trigger == TaskTrigger.ENTRY) {
            "Сначала выполните задание здесь. После этого $appLabel откроется на " +
                "${task.grantMinutes} минут"
        } else if (task.target == TaskTarget.INSTAGRAM) {
            "Интервал использования закончился. Выполните задание, чтобы снова открыть " +
                "Instagram на ${task.grantMinutes} минут"
        } else {
            "Пауза началась после вашего следующего свайпа. Выполните задание, чтобы снова " +
                "открыть Shorts на ${task.grantMinutes} минут"
        },
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(32.dp))
    if (task.answerStatus == TaskAnswerStatus.CORRECT) {
        SuccessMessage("Готово — $appLabel открыт на ${task.grantMinutes} минут")
    } else if (task.completionMode == TaskCompletionMode.CHECKED_ANSWER) {
        Text(
            text = task.visualExpression,
            modifier = Modifier.semantics { contentDescription = task.spokenExpression },
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = task.answer,
            onValueChange = { onAction(BlockingOverlayAction.UpdateAnswer(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("${task.visualExpression} =") },
            singleLine = true,
            enabled = task.answerStatus != TaskAnswerStatus.CHECKING,
            isError = task.answerStatus == TaskAnswerStatus.INCORRECT,
            supportingText = {
                if (task.answerStatus == TaskAnswerStatus.INCORRECT) {
                    Text(
                        text = "Не получилось. Попробуйте ещё раз",
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onAction(BlockingOverlayAction.SubmitAnswer) },
            ),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onAction(BlockingOverlayAction.SubmitAnswer) },
            enabled = task.answer.isNotBlank() &&
                task.answerStatus != TaskAnswerStatus.CHECKING,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(
                if (task.answerStatus == TaskAnswerStatus.CHECKING) {
                    "Проверяем…"
                } else {
                    "Проверить"
                },
            )
        }
        if (task.wrongAttempts >= 3) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { onAction(BlockingOverlayAction.RequestAnotherTask) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Другой пример")
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = when (task.type) {
                        TaskType.PUSH_UPS -> "Физическая пауза"
                        TaskType.CUSTOM -> "Ваше задание"
                        TaskType.ARITHMETIC -> "Задание"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = task.visualExpression,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onAction(BlockingOverlayAction.SubmitAnswer) },
            enabled = task.answerStatus != TaskAnswerStatus.CHECKING,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(if (task.answerStatus == TaskAnswerStatus.CHECKING) "Сохраняем…" else "Выполнено")
        }
    }
}

@Composable
private fun DailyLimitContent(daily: EnforcementUiState.DailyLimit) {
    OverlayTitle("Дневной лимит YouTube исчерпан")
    Spacer(Modifier.height(24.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Сегодня: ${daily.usedMinutes} из ${daily.limitMinutes} минут",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Лимит обновится ${daily.resetLabel}",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    Text(
        text = "Задание не продлевает дневной лимит.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun EscapeActions(
    enforcement: EnforcementUiState,
    onExit: () -> Unit,
    onEmergency: () -> Unit,
) {
    Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val task = enforcement as? EnforcementUiState.TaskGate
            if (enforcement is EnforcementUiState.DailyLimit ||
                task?.answerStatus == TaskAnswerStatus.CORRECT
            ) {
                Button(
                    onClick = onExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        if (task?.answerStatus == TaskAnswerStatus.CORRECT) {
                            if (task.target == TaskTarget.INSTAGRAM) {
                                "Открыть Instagram"
                            } else {
                                "Открыть YouTube"
                            }
                        } else {
                            "На главный экран"
                        },
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Решить позже")
                }
            }
            TextButton(
                onClick = onEmergency,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Emergency Stop")
            }
        }
    }
}

@Composable
private fun EmergencyFormScreen(
    form: EmergencyFormUiState,
    onAction: (BlockingOverlayAction) -> Unit,
) {
    Scaffold(
        bottomBar = {
            EmergencyFormActions(form = form, onAction = onAction)
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(contentPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            ) {
                OverlayTitle("Отключить все ограничения?")
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Задания и дневной лимит не будут появляться, пока вы вручную не " +
                        "включите блокировку снова. Учёт времени продолжится",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = form.reason,
                    onValueChange = {
                        onAction(BlockingOverlayAction.UpdateEmergencyReason(it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Почему вам нужно отключить блокировку?") },
                    placeholder = { Text("Например: смотрю учебную трансляцию") },
                    enabled = !form.submitting,
                    minLines = 3,
                    maxLines = 6,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                if (form.reason.isNotEmpty() &&
                                    form.normalizedReason.length < 5
                                ) {
                                    "Минимум 5 символов"
                                } else {
                                    ""
                                },
                            )
                            Text("${form.reason.length} / 300")
                        }
                    },
                )
                form.submissionError?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = it,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EmergencyFormActions(
    form: EmergencyFormUiState,
    onAction: (BlockingOverlayAction) -> Unit,
) {
    Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = { onAction(BlockingOverlayAction.ConfirmEmergency) },
                enabled = form.canConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(
                    if (form.submitting) {
                        "Отключаем…"
                    } else {
                        "Отключить блокировку"
                    },
                )
            }
            TextButton(
                onClick = { onAction(BlockingOverlayAction.CancelEmergency) },
                enabled = !form.submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Отмена")
            }
        }
    }
}

@Composable
private fun NoScrollMark() {
    Surface(
        modifier = Modifier
            .size(56.dp)
            .semantics { contentDescription = "NoScroll" },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "N",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

@Composable
private fun OverlayTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
        style = MaterialTheme.typography.headlineMedium,
    )
}

@Composable
private fun SuccessMessage(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
