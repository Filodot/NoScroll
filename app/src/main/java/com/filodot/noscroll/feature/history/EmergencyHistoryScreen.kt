package com.filodot.noscroll.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun EmergencyHistoryRoute(
    stateHolder: EmergencyHistoryStateHolder,
    modifier: Modifier = Modifier,
) {
    val state by stateHolder.state.collectAsStateWithLifecycle()
    EmergencyHistoryScreen(
        state = state,
        onAction = stateHolder::dispatch,
        modifier = modifier,
    )
}

@Composable
fun EmergencyHistoryScreen(
    state: EmergencyHistoryUiState,
    onAction: (EmergencyHistoryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "История Emergency Stop",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Причины видны только здесь и не покидают устройство.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(24.dp))
                when {
                    state.loading -> LoadingState()
                    state.loadError != null -> ErrorState(
                        message = state.loadError,
                        onRetry = { onAction(EmergencyHistoryAction.RetryLoad) },
                    )

                    state.items.isEmpty() -> EmptyState()
                    else -> state.items.forEach { item ->
                        EmergencyHistoryCard(item)
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onAction(EmergencyHistoryAction.RequestDeleteHistory) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Удалить локальную историю")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (state.deleteConfirmationVisible) {
        DeleteHistoryDialog(state = state, onAction = onAction)
    }
}

@Composable
private fun EmergencyHistoryCard(item: EmergencyHistoryItemUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isActive) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (item.isActive) "Активен" else item.activatedAtLabel,
                    modifier = Modifier.weight(1f),
                    color = if (item.isActive) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(text = item.sourceLabel, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = item.reason,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (item.isActive) {
                    "Включён ${item.activatedAtLabel}. Учёт времени продолжается."
                } else {
                    "${item.activatedAtLabel} — ${item.deactivatedAtLabel}"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Длительность: ${item.durationMinutes} мин · " +
                    "YouTube: ${item.youtubeMinutesDuring} мин",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Загружаем локальную историю")
    }
}

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text = "Здесь появятся ваши Emergency Stop",
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Не удалось загрузить историю",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Повторить")
            }
        }
    }
}

@Composable
private fun DeleteHistoryDialog(
    state: EmergencyHistoryUiState,
    onAction: (EmergencyHistoryAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!state.deleting) onAction(EmergencyHistoryAction.CancelDeleteHistory)
        },
        title = { Text("Удалить локальную историю") },
        text = {
            Column {
                Text(
                    "Будут удалены дневная статистика, результаты заданий и причины " +
                        "завершённых Emergency Stop.",
                )
                Spacer(Modifier.height(8.dp))
                Text("Настройки лимитов и системные разрешения не изменятся.")
                if (state.items.any(EmergencyHistoryItemUi::isActive)) {
                    Spacer(Modifier.height(8.dp))
                    Text("Активный Emergency Stop останется включён.")
                }
                state.deleteError?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = it,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAction(EmergencyHistoryAction.ConfirmDeleteHistory) },
                enabled = !state.deleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(if (state.deleting) "Удаляем…" else "Удалить историю")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onAction(EmergencyHistoryAction.CancelDeleteHistory) },
                enabled = !state.deleting,
            ) {
                Text("Отмена")
            }
        },
    )
}
