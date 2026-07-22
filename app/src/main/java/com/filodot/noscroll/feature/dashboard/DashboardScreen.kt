package com.filodot.noscroll.feature.dashboard

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
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
                DashboardHeader(state.dateLabel, onAction)
                Spacer(Modifier.height(12.dp))
                ProtectionStatusChip(state.protectionStatus)
                PriorityStateBanner(state, onAction)
                Spacer(Modifier.height(16.dp))
                ShortsCard(
                    shorts = state.shorts,
                    paused = state.emergency.active,
                    onAction = onAction,
                )
                Spacer(Modifier.height(16.dp))
                InstagramCard(
                    instagram = state.instagram,
                    paused = state.emergency.active,
                    onAction = onAction,
                )
                Spacer(Modifier.height(16.dp))
                DailyCard(state.daily, paused = state.emergency.active, onAction = onAction)
                Spacer(Modifier.height(16.dp))
                EmergencyCard(state, onAction)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun InstagramCard(
    instagram: InstagramLimitUiState,
    paused: Boolean,
    onAction: (DashboardAction) -> Unit,
) {
    DashboardCard(title = "Instagram") {
        when (instagram) {
            InstagramLimitUiState.Disabled -> Text(
                text = "Ограничение Instagram выключено",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )

            is InstagramLimitUiState.Enabled -> {
                if (instagram.accessLocked && !paused) {
                    Text("Instagram заблокирован", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Выполните входное задание для следующих " +
                            "${wholeMinutes(instagram.intervalSeconds)} минут",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onAction(DashboardAction.OpenInstagramChallenge) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Открыть задание") }
                    return@DashboardCard
                }
                val progress = progress(instagram.cycleUsedSeconds, instagram.intervalSeconds)
                if (paused) {
                    PausedLabel()
                    Spacer(Modifier.height(12.dp))
                }
                ContextualProgress(
                    progress = progress,
                    description = "Интервал Instagram: использовано " +
                        "${formatCountdown(progress.usedSeconds)} из " +
                        formatCountdown(progress.limitSeconds),
                    paused = paused,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "До паузы: ${formatCountdown(progress.remainingSeconds)}",
                    style = MaterialTheme.typography.titleLarge,
                )
                instagram.unlockedUntilLabel?.let { label ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Вход разрешён до $label",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Instagram сегодня: ${wholeMinutes(instagram.todaySeconds)} мин")
                Text(
                    "Учитывается всё время в приложении, включая ленту и сообщения",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    dateLabel: String,
    onAction: (DashboardAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Сегодня",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = dateLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        TextButton(
            onClick = { onAction(DashboardAction.ShowHelp) },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Справка")
        }
    }
}

@Composable
private fun ProtectionStatusChip(status: DashboardProtectionStatus) {
    val label = when (status) {
        DashboardProtectionStatus.WORKING -> "Защита работает"
        DashboardProtectionStatus.EMERGENCY_BYPASS -> "Ограничения отключены"
        DashboardProtectionStatus.ACCESSIBILITY_ERROR -> "Защита не работает"
    }
    val containerColor = when (status) {
        DashboardProtectionStatus.WORKING -> MaterialTheme.colorScheme.primaryContainer
        DashboardProtectionStatus.EMERGENCY_BYPASS -> MaterialTheme.colorScheme.tertiaryContainer
        DashboardProtectionStatus.ACCESSIBILITY_ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (status) {
        DashboardProtectionStatus.WORKING -> MaterialTheme.colorScheme.onPrimaryContainer
        DashboardProtectionStatus.EMERGENCY_BYPASS -> MaterialTheme.colorScheme.onTertiaryContainer
        DashboardProtectionStatus.ACCESSIBILITY_ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun PriorityStateBanner(
    state: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
) {
    when {
        state.emergency.active -> {
            Spacer(Modifier.height(16.dp))
            PriorityBanner(
                kind = BannerKind.EMERGENCY,
                iconLabel = "Пауза",
                iconText = "Ⅱ",
                title = "Ограничения отключены",
                body = buildString {
                    state.emergency.activeSinceLabel?.let { append("С $it. ") }
                    append("Учёт времени продолжается")
                },
                actionLabel = "Включить блокировку",
                onAction = {
                    onAction(DashboardAction.SetEmergencyEnabled(enabled = false))
                },
            )
        }

        !state.accessibilityEnabled -> {
            Spacer(Modifier.height(16.dp))
            PriorityBanner(
                kind = BannerKind.ERROR,
                iconLabel = "Ошибка доступа",
                iconText = "!",
                title = "Защита не работает",
                body = "NoScroll не может распознавать Shorts или показывать паузу",
                actionLabel = "Включить Accessibility",
                onAction = { onAction(DashboardAction.OpenAccessibilitySettings) },
            )
        }

        !state.monitoringHealthy -> {
            Spacer(Modifier.height(16.dp))
            PriorityBanner(
                kind = BannerKind.ERROR,
                iconLabel = "Ошибка мониторинга",
                iconText = "!",
                title = "Служба остановилась",
                body = "Откройте системные настройки, выключите и снова включите NoScroll",
                actionLabel = "Перезапустить службу",
                onAction = { onAction(DashboardAction.OpenAccessibilitySettings) },
            )
        }

        state.hasUsageAccessProblem -> {
            Spacer(Modifier.height(16.dp))
            PriorityBanner(
                kind = BannerKind.WARNING,
                iconLabel = "Требуется внимание",
                iconText = "i",
                title = "Дневной лимит приостановлен",
                body = "Паузы в Shorts продолжают работать",
                actionLabel = "Включить учёт",
                onAction = { onAction(DashboardAction.OpenUsageAccessSettings) },
            )
        }
    }
}

@Composable
private fun ShortsCard(
    shorts: ShortsLimitUiState,
    paused: Boolean,
    onAction: (DashboardAction) -> Unit,
) {
    DashboardCard(title = "Shorts") {
        when (shorts) {
            ShortsLimitUiState.Disabled -> {
                Text(
                    text = "Паузы Shorts выключены",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            is ShortsLimitUiState.Enabled -> {
                if (shorts.accessLocked && !paused) {
                    Text(
                        text = "Shorts заблокированы",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Откройте задание в NoScroll и осознанно разблокируйте " +
                            "следующие ${wholeMinutes(shorts.intervalSeconds)} минут",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onAction(DashboardAction.OpenChallenge) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Открыть задание")
                    }
                    return@DashboardCard
                }
                val progress = progress(
                    usedSeconds = shorts.cycleUsedSeconds,
                    limitSeconds = shorts.intervalSeconds,
                )
                if (paused) {
                    PausedLabel()
                    Spacer(Modifier.height(12.dp))
                }
                ContextualProgress(
                    progress = progress,
                    description = "Интервал Shorts: использовано " +
                        "${formatCountdown(progress.usedSeconds)} из " +
                        formatCountdown(progress.limitSeconds),
                    paused = paused,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "До паузы: ${formatCountdown(progress.remainingSeconds)}",
                    style = MaterialTheme.typography.titleLarge,
                )
                shorts.unlockedUntilLabel?.let { label ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Вход разрешён до $label",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (shorts.seenToday) {
                        "Shorts сегодня: ${wholeMinutes(shorts.todaySeconds)} мин"
                    } else {
                        "Сегодня Shorts ещё не запускались"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Время сохраняется, если выйти и вернуться",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DailyCard(
    daily: DailyLimitUiState,
    paused: Boolean,
    onAction: (DashboardAction) -> Unit,
) {
    DashboardCard(title = "YouTube за день") {
        when (daily) {
            DailyLimitUiState.Disabled -> {
                Text(
                    text = "Дневной лимит выключен",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            DailyLimitUiState.Unavailable -> {
                Text(
                    text = "Недоступно без доступа к статистике",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { onAction(DashboardAction.OpenUsageAccessSettings) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Разрешить")
                }
            }

            is DailyLimitUiState.Enabled -> {
                val progress = progress(
                    usedSeconds = daily.usedSeconds,
                    limitSeconds = daily.limitSeconds,
                )
                if (paused) {
                    PausedLabel()
                    Spacer(Modifier.height(12.dp))
                }
                ContextualProgress(
                    progress = progress,
                    description = "Дневной лимит YouTube: использовано " +
                        "${wholeMinutes(progress.usedSeconds)} из " +
                        "${wholeMinutes(progress.limitSeconds)} минут",
                    paused = paused,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Использовано ${wholeMinutes(progress.usedSeconds)} из " +
                        "${wholeMinutes(progress.limitSeconds)} мин",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Осталось ${wholeMinutes(progress.remainingSeconds)} мин",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun EmergencyCard(
    state: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Emergency Stop",
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                Switch(
                    checked = state.emergency.active,
                    onCheckedChange = {
                        onAction(DashboardAction.SetEmergencyEnabled(enabled = it))
                    },
                    enabled = state.emergencyAvailable,
                    modifier = Modifier.semantics {
                        stateDescription = if (state.emergency.active) {
                            "Включён"
                        } else {
                            "Выключен"
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Отключает задания и дневной блок до ручного включения",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!state.emergencyAvailable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Ограничения уже выключены",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun ContextualProgress(
    progress: DashboardProgress,
    description: String,
    paused: Boolean,
) {
    LinearProgressIndicator(
        progress = { progress.fraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .semantics {
                contentDescription = description
                stateDescription = if (paused) {
                    "Блокировка приостановлена"
                } else {
                    description
                }
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = progress.fraction,
                    range = 0f..1f,
                )
            },
        color = if (paused) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        },
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
private fun PausedLabel() {
    Text(
        text = "Блокировка приостановлена",
        color = MaterialTheme.colorScheme.tertiary,
        style = MaterialTheme.typography.labelLarge,
    )
}

private enum class BannerKind {
    EMERGENCY,
    WARNING,
    ERROR,
}

@Composable
private fun PriorityBanner(
    kind: BannerKind,
    iconLabel: String,
    iconText: String,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val containerColor = when (kind) {
        BannerKind.EMERGENCY -> MaterialTheme.colorScheme.tertiaryContainer
        BannerKind.WARNING -> MaterialTheme.colorScheme.secondaryContainer
        BannerKind.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (kind) {
        BannerKind.EMERGENCY -> MaterialTheme.colorScheme.onTertiaryContainer
        BannerKind.WARNING -> MaterialTheme.colorScheme.onSecondaryContainer
        BannerKind.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = iconLabel },
                    shape = CircleShape,
                    color = contentColor,
                    contentColor = containerColor,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(iconText, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    color = contentColor,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = body,
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}
