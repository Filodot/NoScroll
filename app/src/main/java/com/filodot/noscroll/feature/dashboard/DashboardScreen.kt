package com.filodot.noscroll.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.filodot.noscroll.core.model.TaskTarget

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProtectionStatusChip(state.protectionStatus)
                    TextButton(
                        onClick = { onAction(DashboardAction.ShowHelp) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Справка")
                    }
                }
                Spacer(Modifier.height(22.dp))
                DashboardHeader(state.dateLabel)
                PriorityStateBanner(state, onAction)
                Spacer(Modifier.height(20.dp))
                UsageStatisticsCard(state.statistics)
                Spacer(Modifier.height(16.dp))
                FocusModeCard(
                    focus = state.focusMode,
                    accessibilityEnabled = state.accessibilityEnabled,
                    emergencyActive = state.emergency.active,
                    onAction = onAction,
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    "Текущие интервалы",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(12.dp))
                ShortsCard(
                    shorts = state.shorts,
                    paused = state.emergency.active,
                    onAction = onAction,
                )
                Spacer(Modifier.height(16.dp))
                AppIntervalCard(
                    label = "Весь YouTube",
                    detail = "Учитывается всё время в YouTube, включая Shorts",
                    target = TaskTarget.YOUTUBE,
                    app = state.youtube,
                    paused = state.emergency.active,
                    onAction = onAction,
                )
                Spacer(Modifier.height(16.dp))
                AppIntervalCard(
                    label = "Instagram",
                    detail = "Учитывается всё время в приложении, включая ленту и сообщения",
                    target = TaskTarget.INSTAGRAM,
                    app = state.instagram,
                    paused = state.emergency.active,
                    onAction = onAction,
                )
                Spacer(Modifier.height(16.dp))
                AppIntervalCard(
                    label = "Pinterest",
                    detail = "Учитывается всё время в приложении Pinterest",
                    target = TaskTarget.PINTEREST,
                    app = state.pinterest,
                    paused = state.emergency.active,
                    onAction = onAction,
                )
                Spacer(Modifier.height(16.dp))
                AppIntervalCard(
                    label = "Chrome",
                    detail = "Учитывается всё время в Chrome, независимо от сайта",
                    target = TaskTarget.CHROME,
                    app = state.chrome,
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
private fun UsageStatisticsCard(statistics: UsageStatisticsUiState) {
    DashboardCard(title = "Статистика использования") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Сегодня", style = MaterialTheme.typography.labelLarge)
                Text(
                    formatUsageDuration(statistics.todayTotalSeconds),
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(6.dp))
                UsageTrendLabel(statistics)
            }
            CalmPlantIllustration()
        }
        Spacer(Modifier.height(20.dp))
        Text("Последние 7 дней", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        WeeklyUsageChart(statistics.days)
        Spacer(Modifier.height(20.dp))
        Text("По приложениям", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        UsageBreakdownRow("YouTube", statistics.todayYoutubeSeconds) {
            statistics.todayShortsSeconds.takeIf { it > 0 }?.let {
                "включая Shorts ${formatUsageDuration(it)}"
            }
        }
        UsageBreakdownRow("Instagram", statistics.todayInstagramSeconds)
        UsageBreakdownRow("Pinterest", statistics.todayPinterestSeconds)
        UsageBreakdownRow("Chrome", statistics.todayChromeSeconds)
        Spacer(Modifier.height(10.dp))
        Text(
            "Shorts входят во время YouTube и не прибавляются второй раз.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun UsageBreakdownRow(
    label: String,
    seconds: Long,
    detail: () -> String? = { null },
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppBadge(label)
        Text(label, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatUsageDuration(seconds))
            detail()?.let { value ->
                Text(
                    value,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun UsageTrendLabel(statistics: UsageStatisticsUiState) {
    val text = when (statistics.trend) {
        UsageTrendDirection.IMPROVING ->
            "↓ ${absoluteChangePercent(statistics.changePercent)}% за неделю"
        UsageTrendDirection.INCREASING -> statistics.changePercent?.let {
            "↑ ${absoluteChangePercent(it)}% за неделю"
        } ?: "Экранное время выросло"
        UsageTrendDirection.STABLE -> "Почти без изменений"
        UsageTrendDirection.NOT_ENOUGH_DATA -> "Собираем данные для сравнения"
    }
    val fullDescription = when (statistics.trend) {
        UsageTrendDirection.IMPROVING ->
            "Экранное время снижается на ${absoluteChangePercent(statistics.changePercent)}%"
        UsageTrendDirection.INCREASING -> statistics.changePercent?.let {
            "Экранное время выросло на ${absoluteChangePercent(it)}%"
        } ?: "Экранное время выросло"
        UsageTrendDirection.STABLE -> "Экранное время почти не изменилось"
        UsageTrendDirection.NOT_ENOUGH_DATA -> "Собираем базу для сравнения"
    }
    Text(
        text = text,
        color = when (statistics.trend) {
            UsageTrendDirection.IMPROVING -> MaterialTheme.colorScheme.tertiary
            UsageTrendDirection.INCREASING -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.semantics { contentDescription = fullDescription },
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun WeeklyUsageChart(days: List<UsageDayUi>) {
    val maxSeconds = days.filter(UsageDayUi::observed)
        .maxOfOrNull(UsageDayUi::totalSeconds)
        ?.coerceAtLeast(1)
        ?: 1
    val displayDays = days.asReversed()
    Row(
        modifier = Modifier.fillMaxWidth().height(94.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        displayDays.forEachIndexed { index, day ->
            val fraction = if (day.observed) {
                (day.totalSeconds.toFloat() / maxSeconds).coerceIn(.07f, 1f)
            } else {
                .04f
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics {
                        contentDescription = if (day.observed) {
                            "${day.label}: ${formatUsageDuration(day.totalSeconds)}"
                        } else {
                            "${day.label}: нет данных"
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .weight(1f),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .background(
                                if (index == displayDays.lastIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = .78f)
                                },
                                RoundedCornerShape(6.dp),
                            ),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(shortDayLabel(day), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun shortDayLabel(day: UsageDayUi): String = when (day.localDate.dayOfWeek.value) {
    1 -> "Пн"
    2 -> "Вт"
    3 -> "Ср"
    4 -> "Чт"
    5 -> "Пт"
    6 -> "Сб"
    else -> "Вс"
}

@Composable
private fun AppBadge(label: String) {
    val background = when (label) {
        "YouTube" -> MaterialTheme.colorScheme.errorContainer
        "Instagram" -> MaterialTheme.colorScheme.secondaryContainer
        "Pinterest" -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    Surface(
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = background,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label.take(1), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CalmPlantIllustration() {
    val sage = MaterialTheme.colorScheme.tertiary
    val paleSage = MaterialTheme.colorScheme.tertiaryContainer
    val amber = MaterialTheme.colorScheme.primaryContainer
    Canvas(
        modifier = Modifier
            .size(width = 108.dp, height = 112.dp)
            .semantics { contentDescription = "Рост полезных привычек" },
    ) {
        drawCircle(amber, radius = 20.dp.toPx(), center = Offset(size.width * .72f, size.height * .2f))
        drawOval(
            color = paleSage,
            topLeft = Offset(size.width * .08f, size.height * .69f),
            size = Size(size.width * .84f, size.height * .23f),
        )
        drawLine(
            color = sage,
            start = Offset(size.width * .52f, size.height * .8f),
            end = Offset(size.width * .52f, size.height * .28f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        listOf(
            Triple(.48f, .49f, -32f),
            Triple(.56f, .39f, 30f),
            Triple(.47f, .62f, -40f),
            Triple(.57f, .56f, 35f),
        ).forEach { (x, y, angle) ->
            rotate(angle, pivot = Offset(size.width * x, size.height * y)) {
                drawOval(
                    color = sage.copy(alpha = .84f),
                    topLeft = Offset(size.width * (x - .14f), size.height * (y - .06f)),
                    size = Size(size.width * .28f, size.height * .12f),
                )
            }
        }
    }
}

private fun formatUsageDuration(seconds: Long): String {
    val minutes = seconds.coerceAtLeast(0) / 60
    if (minutes == 0L) return "0 мин"
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours == 0L -> "$minutes мин"
        remainingMinutes == 0L -> "$hours ч"
        else -> "$hours ч $remainingMinutes мин"
    }
}

@Composable
private fun FocusModeCard(
    focus: FocusModeUiState,
    accessibilityEnabled: Boolean,
    emergencyActive: Boolean,
    onAction: (DashboardAction) -> Unit,
) {
    var showSetup by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var durationMinutes by remember(focus.durationMinutes) {
        mutableIntStateOf(focus.durationMinutes)
    }
    var selectedPackages by remember(focus.selectedPackages) {
        mutableStateOf(focus.selectedPackages)
    }

    DashboardCard(title = "Не отвлекаться") {
        if (focus.active) {
            Text(
                if (emergencyActive) "Фокус временно обойдён" else "Фокус включён",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "До ${focus.endsAtLabel ?: "завершения таймера"} · " +
                    "примерно ${focus.remainingMinutes.coerceAtLeast(1)} мин",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text("Заблокированы: ${focus.blockedAppLabels.joinToString()}")
            Spacer(Modifier.height(12.dp))
            Text(
                "Досрочный выход возможен только через Emergency Stop с указанием причины.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onAction(DashboardAction.OpenFocusEmergency) },
                enabled = !emergencyActive,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(if (emergencyActive) "Emergency Stop уже включён" else "Emergency Stop")
            }
        } else {
            Text(
                "На выбранное время полностью закрывает отвлекающие приложения.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showSetup = true },
                enabled = accessibilityEnabled && !emergencyActive,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Начать фокус")
            }
            if (!accessibilityEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Сначала включите Accessibility в системных настройках.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showSetup) {
        AlertDialog(
            onDismissRequest = { showSetup = false },
            title = { Text("Настроить фокус") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Время")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60).forEach { minutes ->
                            FilterChip(
                                selected = durationMinutes == minutes,
                                onClick = { durationMinutes = minutes },
                                label = { Text("$minutes мин") },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(120, 240).forEach { minutes ->
                            FilterChip(
                                selected = durationMinutes == minutes,
                                onClick = { durationMinutes = minutes },
                                label = { Text(if (minutes == 120) "2 часа" else "4 часа") },
                            )
                        }
                    }
                    Text("Приложения")
                    focus.availableApps.forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(app.label, modifier = Modifier.weight(1f))
                            Switch(
                                checked = app.packageName in selectedPackages,
                                onCheckedChange = { checked ->
                                    selectedPackages = if (checked) {
                                        selectedPackages + app.packageName
                                    } else {
                                        selectedPackages - app.packageName
                                    }
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSetup = false
                        showConfirmation = true
                    },
                    enabled = selectedPackages.isNotEmpty(),
                ) { Text("Продолжить") }
            },
            dismissButton = {
                TextButton(onClick = { showSetup = false }) { Text("Отмена") }
            },
        )
    }

    if (showConfirmation) {
        val selectedLabels = focus.availableApps
            .filter { it.packageName in selectedPackages }
            .joinToString { it.label }
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("Включить полный блок?") },
            text = {
                Text(
                    "$selectedLabels будут недоступны $durationMinutes минут. " +
                        "Отменить таймер обычной кнопкой нельзя: только Emergency Stop " +
                        "с обязательным объяснением причины.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmation = false
                        onAction(
                            DashboardAction.StartFocusMode(
                                durationMinutes = durationMinutes,
                                packageNames = selectedPackages,
                            ),
                        )
                    },
                ) { Text("Включить блок") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) { Text("Назад") }
            },
        )
    }
}

@Composable
private fun AppIntervalCard(
    label: String,
    detail: String,
    target: TaskTarget,
    app: AppLimitUiState,
    paused: Boolean,
    onAction: (DashboardAction) -> Unit,
) {
    DashboardCard(title = label) {
        when (app) {
            AppLimitUiState.Disabled -> Text(
                text = "Ограничение $label выключено",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )

            is AppLimitUiState.Enabled -> {
                if (app.accessLocked && !paused) {
                    Text("$label заблокирован", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Выполните входное задание для следующих " +
                            "${wholeMinutes(app.intervalSeconds)} минут",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onAction(DashboardAction.OpenAppChallenge(target)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Открыть задание") }
                    return@DashboardCard
                }
                val progress = progress(app.cycleUsedSeconds, app.intervalSeconds)
                if (paused) {
                    PausedLabel()
                    Spacer(Modifier.height(12.dp))
                }
                ContextualProgress(
                    progress = progress,
                    description = "Интервал $label: использовано " +
                        "${formatCountdown(progress.usedSeconds)} из " +
                        formatCountdown(progress.limitSeconds),
                    paused = paused,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "До паузы: ${formatCountdown(progress.remainingSeconds)}",
                    style = MaterialTheme.typography.titleLarge,
                )
                app.unlockedUntilLabel?.let { untilLabel ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Вход разрешён до $untilLabel",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Сегодня: ${wholeMinutes(app.todaySeconds)} мин")
                Text(
                    detail,
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
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
}

@Composable
private fun ProtectionStatusChip(status: DashboardProtectionStatus) {
    val label = when (status) {
        DashboardProtectionStatus.WORKING -> "Защита работает"
        DashboardProtectionStatus.EMERGENCY_BYPASS -> "Ограничения отключены"
        DashboardProtectionStatus.MONITORING_RECOVERING -> "Защита восстанавливается"
        DashboardProtectionStatus.ACCESSIBILITY_ERROR -> "Защита не работает"
    }
    val containerColor = when (status) {
        DashboardProtectionStatus.WORKING -> MaterialTheme.colorScheme.tertiaryContainer
        DashboardProtectionStatus.EMERGENCY_BYPASS -> MaterialTheme.colorScheme.tertiaryContainer
        DashboardProtectionStatus.MONITORING_RECOVERING ->
            MaterialTheme.colorScheme.secondaryContainer
        DashboardProtectionStatus.ACCESSIBILITY_ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (status) {
        DashboardProtectionStatus.WORKING -> MaterialTheme.colorScheme.onTertiaryContainer
        DashboardProtectionStatus.EMERGENCY_BYPASS -> MaterialTheme.colorScheme.onTertiaryContainer
        DashboardProtectionStatus.MONITORING_RECOVERING ->
            MaterialTheme.colorScheme.onSecondaryContainer
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

        state.monitoringState == DashboardMonitoringState.STARTING ||
            state.monitoringState == DashboardMonitoringState.RECOVERING -> {
            Spacer(Modifier.height(16.dp))
            PriorityBanner(
                kind = BannerKind.WARNING,
                iconLabel = "Восстановление",
                iconText = "↻",
                title = if (state.monitoringState == DashboardMonitoringState.STARTING) {
                    "Запускаем защиту"
                } else {
                    "Восстанавливаем наблюдение"
                },
                body = "Служба выполняет самопроверку. Если статус не изменится, откройте диагностику.",
                actionLabel = "Открыть диагностику",
                onAction = { onAction(DashboardAction.OpenDiagnostics) },
            )
        }

        state.monitoringState == DashboardMonitoringState.DISCONNECTED -> {
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
