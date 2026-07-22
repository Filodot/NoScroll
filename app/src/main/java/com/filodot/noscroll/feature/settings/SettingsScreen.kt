package com.filodot.noscroll.feature.settings

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
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
                    text = "Настройки",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(24.dp))
                SectionTitle("Системный доступ")
                Spacer(Modifier.height(12.dp))
                SystemAccessCard(state, onAction)
                Spacer(Modifier.height(24.dp))
                SectionTitle("Данные")
                Spacer(Modifier.height(12.dp))
                ActionCard(
                    title = "История Emergency Stop",
                    body = "Причины и длительность отключений хранятся только на этом устройстве.",
                    actionLabel = "Открыть историю",
                    onClick = { onAction(SettingsAction.OpenEmergencyHistory) },
                )
                Spacer(Modifier.height(24.dp))
                SectionTitle("Приватность")
                Spacer(Modifier.height(12.dp))
                ActionCard(
                    title = "Данные остаются на телефоне",
                    body = "NoScroll хранит настройки, агрегированное время, результаты заданий и " +
                        "причины Emergency Stop только на устройстве. Содержимое экранов YouTube " +
                        "и Instagram, названия видео, историю просмотров и введённый текст " +
                        "приложение не сохраняет.",
                    actionLabel = "Политика приватности",
                    onClick = { onAction(SettingsAction.OpenPrivacyDocument) },
                )
                Spacer(Modifier.height(24.dp))
                SectionTitle("Диагностика")
                Spacer(Modifier.height(12.dp))
                DiagnosticsCard(state)
                Spacer(Modifier.height(24.dp))
                SectionTitle("О приложении")
                Spacer(Modifier.height(12.dp))
                ActionCard(
                    title = "NoScroll ${state.appVersionLabel}",
                    body = "Инструмент осознанной паузы, а не медицинское средство. " +
                        "Абсолютная блокировка на всех версиях YouTube не гарантируется.",
                    actionLabel = "Лицензии",
                    onClick = { onAction(SettingsAction.OpenLicenses) },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SystemAccessCard(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            AccessRow(
                label = "Accessibility Service",
                status = state.accessibilityStatus,
                actionLabel = if (state.accessibilityStatus == SystemAccessUiStatus.ENABLED) {
                    null
                } else {
                    "Настроить"
                },
                onAction = { onAction(SettingsAction.OpenAccessibilitySettings) },
            )
            HorizontalDivider()
            AccessRow(
                label = "Usage Access",
                status = state.usageAccessStatus,
                actionLabel = if (state.usageAccessStatus == SystemAccessUiStatus.ENABLED) {
                    null
                } else {
                    "Настроить"
                },
                onAction = { onAction(SettingsAction.OpenUsageAccessSettings) },
            )
            HorizontalDivider()
            KeyValueRow(
                label = "Версия YouTube",
                value = state.youtubeVersionLabel ?: "Не найден",
            )
            HorizontalDivider()
            KeyValueRow(
                label = "Версия Instagram",
                value = state.instagramVersionLabel ?: "Не найден",
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Если Android 13+ не разрешает включить Accessibility для скачанного " +
                    "APK: откройте карточку приложения, нажмите меню ⋮ и выберите " +
                    "«Разрешить ограниченные настройки».",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = { onAction(SettingsAction.OpenAppDetailsSettings) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Открыть карточку приложения")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { onAction(SettingsAction.RefreshSystemAccess) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Проверить снова")
            }
        }
    }
}

@Composable
private fun AccessRow(
    label: String,
    status: SystemAccessUiStatus,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    val statusLabel = when (status) {
        SystemAccessUiStatus.ENABLED -> "Включён"
        SystemAccessUiStatus.NOT_ENABLED -> "Не включён"
        SystemAccessUiStatus.SKIPPED -> "Пропущен"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { stateDescription = statusLabel }
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = statusLabel,
                color = if (status == SystemAccessUiStatus.ENABLED) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
        actionLabel?.let {
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(it)
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(state: SettingsUiState) {
    val diagnostics = state.diagnostics
    val statusLabel = when (diagnostics.detectorStatus) {
        DetectorUiStatus.READY -> "Готов"
        DetectorUiStatus.INACTIVE -> "Неактивен"
        DetectorUiStatus.UNKNOWN_LAYOUT -> "Неизвестная версия интерфейса"
        DetectorUiStatus.ERROR -> "Ошибка"
    }
    val resultLabel = when (diagnostics.lastResultCode) {
        DiagnosticResultCode.SHORTS_CONFIRMED -> "Shorts подтверждён"
        DiagnosticResultCode.NON_SHORTS_CONFIRMED -> "Не Shorts"
        DiagnosticResultCode.UNKNOWN -> "Неопределённо"
        DiagnosticResultCode.ACCESS_UNAVAILABLE -> "Доступ недоступен"
        null -> "Нет данных"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (
                diagnostics.detectorStatus == DetectorUiStatus.ERROR || !state.monitoringHealthy
            ) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            KeyValueRow("Мониторинг", state.monitoringHealthLabel)
            HorizontalDivider()
            KeyValueRow("Последний heartbeat", state.lastHeartbeatLabel ?: "Нет")
            HorizontalDivider()
            KeyValueRow("Последнее событие Instagram", state.lastInstagramEventLabel ?: "Нет")
            HorizontalDivider()
            KeyValueRow("Автовосстановлений", state.recoveryCount.toString())
            state.lastFailureCode?.let { failureCode ->
                HorizontalDivider()
                KeyValueRow("Последний код восстановления", failureCode)
            }
            HorizontalDivider()
            KeyValueRow("Статус детектора", statusLabel)
            HorizontalDivider()
            KeyValueRow("Последний результат", resultLabel)
            HorizontalDivider()
            KeyValueRow(
                "Последнее распознавание",
                diagnostics.lastRecognitionLabel ?: "Ещё не выполнялось",
            )
            HorizontalDivider()
            KeyValueRow("Неопределённых результатов", diagnostics.unknownCount.toString())
            HorizontalDivider()
            KeyValueRow("Версия правил", diagnostics.rulesVersion.toString())
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Диагностика содержит только коды и счётчики — без текста и элементов " +
                    "экрана YouTube или Instagram.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    body: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(text = body, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onClick,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun KeyValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
}
