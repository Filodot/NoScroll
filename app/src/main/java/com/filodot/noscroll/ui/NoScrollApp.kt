package com.filodot.noscroll.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.filodot.noscroll.core.model.EmergencyActivationSource
import com.filodot.noscroll.core.model.EmergencyEvent
import com.filodot.noscroll.core.model.EmergencyState
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.model.ShortsDetectionState
import com.filodot.noscroll.core.model.CustomTaskPreset
import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskTarget
import com.filodot.noscroll.core.model.TaskType
import com.filodot.noscroll.core.model.UserSettings
import com.filodot.noscroll.feature.dashboard.DashboardAction
import com.filodot.noscroll.feature.dashboard.DashboardScreen
import com.filodot.noscroll.feature.dashboard.DashboardUiState
import com.filodot.noscroll.feature.dashboard.DailyLimitUiState
import com.filodot.noscroll.feature.dashboard.EmergencyUiState
import com.filodot.noscroll.feature.dashboard.InstagramLimitUiState
import com.filodot.noscroll.feature.dashboard.ShortsLimitUiState
import com.filodot.noscroll.feature.history.EmergencyHistoryAction
import com.filodot.noscroll.feature.history.EmergencyHistoryEffect
import com.filodot.noscroll.feature.history.EmergencyHistoryItemUi
import com.filodot.noscroll.feature.history.EmergencyHistoryRoute
import com.filodot.noscroll.feature.history.EmergencyHistoryStateHolder
import com.filodot.noscroll.feature.limits.LimitsEffect
import com.filodot.noscroll.feature.limits.LimitsRoute
import com.filodot.noscroll.feature.limits.LimitsStateHolder
import com.filodot.noscroll.feature.limits.LimitsValues
import com.filodot.noscroll.feature.onboarding.OnboardingAction
import com.filodot.noscroll.feature.onboarding.OnboardingEffect
import com.filodot.noscroll.feature.onboarding.OnboardingRoute
import com.filodot.noscroll.feature.onboarding.OnboardingStateHolder
import com.filodot.noscroll.feature.onboarding.OnboardingStep
import com.filodot.noscroll.feature.overlay.BlockingOverlayAction
import com.filodot.noscroll.feature.overlay.BlockingOverlayEffect
import com.filodot.noscroll.feature.overlay.BlockingOverlayScreen
import com.filodot.noscroll.feature.overlay.BlockingOverlayStateHolder
import com.filodot.noscroll.feature.overlay.EnforcementUiState
import com.filodot.noscroll.feature.overlay.TaskAnswerStatus
import com.filodot.noscroll.feature.settings.DetectorUiStatus
import com.filodot.noscroll.feature.settings.DiagnosticResultCode
import com.filodot.noscroll.feature.settings.RedactedDiagnosticsUiState
import com.filodot.noscroll.feature.settings.SettingsAction
import com.filodot.noscroll.feature.settings.SettingsScreen
import com.filodot.noscroll.feature.settings.SettingsUiState
import com.filodot.noscroll.feature.settings.SystemAccessUiStatus
import com.filodot.noscroll.feature.tasks.TaskSettingsAction
import com.filodot.noscroll.feature.tasks.TaskSettingsScreen
import com.filodot.noscroll.feature.tasks.TaskSettingsUiState
import com.filodot.noscroll.monitoring.runtime.MonitoringDiagnostics
import com.filodot.noscroll.monitoring.runtime.MonitoringCoordinator
import com.filodot.noscroll.platform.SystemAccessSnapshot
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun NoScrollApp(
    modifier: Modifier = Modifier,
    graph: NoScrollAppGraph? = null,
) {
    val fallbackGraph = remember { NoScrollAppGraph.fake() }
    val appGraph = graph ?: fallbackGraph
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repositoriesReady by appGraph.settingsReady.collectAsStateWithLifecycle()
    if (!repositoriesReady) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Загружаем сохранённые данные…")
        }
        return
    }

    val settings by appGraph.settingsRepository.settings.collectAsStateWithLifecycle()
    val dailyUsage by appGraph.usageRepository.dailyUsage.collectAsStateWithLifecycle()
    val gateCycle by appGraph.usageRepository.gateCycle.collectAsStateWithLifecycle()
    val pendingTask by appGraph.taskRepository.pendingTask.collectAsStateWithLifecycle()
    val taskPresets by appGraph.taskPresetRepository.presets.collectAsStateWithLifecycle()
    val emergencyState by appGraph.emergencyRepository.state.collectAsStateWithLifecycle()
    val activeEnforcement = appGraph.monitoring?.let { monitoring ->
        val state by monitoring.enforcement.collectAsStateWithLifecycle()
        state
    }
    val accessState = appGraph.systemAccess?.let { access ->
        val state by access.state.collectAsStateWithLifecycle()
        state
    }
    val diagnostics = appGraph.monitoring?.let { monitoring ->
        val state by monitoring.diagnostics.collectAsStateWithLifecycle()
        state
    }
    val dashboardState = if (accessState == null) {
        appGraph.dashboardState
    } else {
        buildDashboardState(
            settings = settings,
            usage = dailyUsage,
            cycle = gateCycle,
            pendingTask = pendingTask,
            emergencyState = emergencyState,
            access = accessState,
        )
    }
    val settingsState = if (accessState == null) {
        appGraph.settingsState
    } else {
        buildSettingsState(accessState, diagnostics ?: MonitoringDiagnostics())
    }

    val historyHolder = remember(appGraph, scope) {
        lateinit var holder: EmergencyHistoryStateHolder
        holder = EmergencyHistoryStateHolder(
            initialState = appGraph.historyState,
            emitEffect = { effect ->
                when (effect) {
                    EmergencyHistoryEffect.DeleteHistory -> scope.launch {
                        appGraph.emergencyRepository.deleteHistory()
                        holder.dispatch(
                            EmergencyHistoryAction.DeletionFinished(succeeded = true),
                        )
                    }

                    EmergencyHistoryEffect.RetryLoad -> scope.launch {
                        holder.dispatch(
                            EmergencyHistoryAction.ReplaceItems(holder.state.value.items),
                        )
                        snackbarHostState.showSnackbar("История обновлена")
                    }
                }
            },
        )
        holder
    }
    LaunchedEffect(appGraph, historyHolder) {
        appGraph.emergencyRepository.history.collectLatest { events ->
            historyHolder.dispatch(
                EmergencyHistoryAction.ReplaceItems(events.map { it.toHistoryItem() }),
            )
        }
    }

    val limitsHolder = remember(appGraph, scope) {
        val settings = appGraph.settingsRepository.settings.value
        LimitsStateHolder(
            initialValues = LimitsValues(
                preset = settings.preset,
                shortsEnabled = settings.shortsGateEnabled,
                shortsMinutes = settings.shortsIntervalMinutes,
                dailyEnabled = settings.dailyLimitEnabled,
                dailyMinutes = settings.dailyLimitMinutes,
                instagramEnabled = settings.instagramGateEnabled,
                instagramMinutes = settings.instagramIntervalMinutes,
            ),
            emitEffect = { effect ->
                if (effect is LimitsEffect.Saved) scope.launch {
                    val current = appGraph.settingsRepository.settings.value
                    appGraph.settingsRepository.save(
                        current.copy(
                            preset = effect.values.preset,
                            shortsGateEnabled = effect.values.shortsEnabled,
                            shortsIntervalMinutes = effect.values.shortsMinutes,
                            dailyLimitEnabled = effect.values.dailyEnabled,
                            dailyLimitMinutes = effect.values.dailyMinutes,
                            instagramGateEnabled = effect.values.instagramEnabled,
                            instagramIntervalMinutes = effect.values.instagramMinutes,
                        ),
                    )
                    snackbarHostState.showSnackbar("Ограничения сохранены")
                }
            },
        )
    }

    val emergencyHolder = remember(appGraph, navController, scope) {
        lateinit var holder: BlockingOverlayStateHolder
        holder = BlockingOverlayStateHolder(
            initialEnforcement = EnforcementUiState.TaskGate(
                taskId = "fake-dashboard-emergency",
                visualExpression = "17 + 26",
                spokenExpression = "семнадцать плюс двадцать шесть",
                grantMinutes = 5,
            ),
            emergencySourceOverride = EmergencyActivationSource.DASHBOARD,
            emitEffect = { effect ->
                when (effect) {
                    is BlockingOverlayEffect.ConfirmEmergency -> scope.launch {
                        val monitoring = appGraph.monitoring
                        if (monitoring != null) {
                            monitoring.activateEmergency(effect.normalizedReason, effect.source)
                        } else {
                            appGraph.emergencyRepository.activate(
                                EmergencyEvent(
                                    id = UUID.randomUUID().toString(),
                                    reason = effect.normalizedReason,
                                    activatedAt = Instant.now(),
                                    activationSource = effect.source,
                                ),
                            )
                            appGraph.settingsRepository.save(
                                appGraph.settingsRepository.settings.value.copy(
                                    emergencyActive = true,
                                ),
                            )
                        }
                        holder.dispatch(
                            BlockingOverlayAction.EmergencyActivationFinished(succeeded = true),
                        )
                        navController.popBackStack()
                    }

                    BlockingOverlayEffect.ExitYouTube -> scope.launch {
                        snackbarHostState.showSnackbar("Вернитесь на экран «Сегодня»")
                    }

                    BlockingOverlayEffect.RequestAnotherTask,
                    BlockingOverlayEffect.TaskSolved,
                    BlockingOverlayEffect.EmergencyActivated,
                    BlockingOverlayEffect.OpenYouTube,
                    is BlockingOverlayEffect.VerifyAnswer,
                    -> Unit
                }
            },
        )
        holder
    }

    val onboardingHolder = remember(appGraph, navController, scope) {
        lateinit var holder: OnboardingStateHolder
        holder = OnboardingStateHolder { effect ->
            when (effect) {
                OnboardingEffect.OpenAccessibilitySettings -> {
                    val access = appGraph.systemAccess
                    if (access == null) {
                        holder.dispatch(OnboardingAction.AccessibilitySettingsReturned(true))
                    } else {
                        context.openSystemSettings(
                            intent = access.accessibilitySettingsIntent(),
                            onUnavailable = {
                                holder.dispatch(
                                    OnboardingAction.AccessibilitySettingsReturned(false),
                                )
                            },
                        )
                    }
                }

                OnboardingEffect.OpenUsageAccessSettings -> {
                    val access = appGraph.systemAccess
                    if (access == null) {
                        holder.dispatch(OnboardingAction.UsageAccessSettingsReturned(true))
                    } else {
                        context.openSystemSettings(
                            intent = access.usageAccessSettingsIntent(),
                            onUnavailable = {
                                holder.dispatch(OnboardingAction.UsageAccessSettingsReturned(false))
                            },
                        )
                    }
                }

                is OnboardingEffect.Completed -> scope.launch {
                    val current = appGraph.settingsRepository.settings.value
                    appGraph.settingsRepository.save(
                        current.copy(
                            onboardingCompleted = true,
                            preset = effect.selection.preset,
                            shortsIntervalMinutes = effect.selection.shortsIntervalMinutes,
                            dailyLimitMinutes = effect.selection.dailyLimitMinutes,
                            dailyLimitEnabled = effect.selection.dailyLimitEnabled,
                            accessibilityDisclosureAcceptedAt = Instant.now(),
                            usageDisclosureSeenAt = Instant.now(),
                        ),
                    )
                    navController.navigate(AppRoute.Dashboard.path) {
                        popUpTo(AppRoute.Onboarding.path) { inclusive = true }
                    }
                }

                OnboardingEffect.OpenPrivacyPolicy -> scope.launch {
                    snackbarHostState.showSnackbar("Политика приватности доступна в Настройках")
                }

                OnboardingEffect.ShowHowItWorks -> scope.launch {
                    snackbarHostState.showSnackbar("NoScrol создаёт паузу, не запрещая выход")
                }

                OnboardingEffect.RefreshPermissionStates ->
                    refreshOnboardingPermissions(holder, appGraph)
            }
        }
        holder
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(
        lifecycleOwner,
        appGraph,
        onboardingHolder,
        settings.onboardingCompleted,
    ) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshOnboardingPermissions(onboardingHolder, appGraph)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val initialRoute = remember(appGraph) {
        if (appGraph.settingsRepository.settings.value.onboardingCompleted) {
            AppRoute.Dashboard.path
        } else {
            AppRoute.Onboarding.path
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = initialRoute) {
            composable(AppRoute.Onboarding.path) {
                OnboardingRoute(onboardingHolder)
            }
            composable(AppRoute.Dashboard.path) {
                MainDestinationScaffold(navController, AppRoute.Dashboard) {
                    DashboardScreen(
                        state = dashboardState,
                        onAction = { action ->
                            when (action) {
                                DashboardAction.ShowHelp -> scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Shorts и дневной YouTube считаются отдельно",
                                    )
                                }

                                DashboardAction.OpenAccessibilitySettings ->
                                    appGraph.systemAccess?.let { access ->
                                        context.openSystemSettings(access.accessibilitySettingsIntent())
                                    }

                                DashboardAction.OpenUsageAccessSettings ->
                                    appGraph.systemAccess?.let { access ->
                                        context.openSystemSettings(access.usageAccessSettingsIntent())
                                    }

                                DashboardAction.OpenChallenge -> scope.launch {
                                    appGraph.monitoring?.prepareChallengeForApp()
                                }

                                DashboardAction.OpenInstagramChallenge -> scope.launch {
                                    appGraph.monitoring?.prepareChallengeForApp(
                                        TaskTarget.INSTAGRAM,
                                    )
                                }

                                is DashboardAction.SetEmergencyEnabled -> {
                                    if (action.enabled) {
                                        emergencyHolder.dispatch(
                                            BlockingOverlayAction.OpenEmergencyForm,
                                        )
                                        navController.navigate(AppRoute.Emergency.path)
                                    } else {
                                        scope.launch {
                                            val monitoring = appGraph.monitoring
                                            if (monitoring != null) {
                                                monitoring.deactivateEmergency()
                                            } else {
                                                appGraph.emergencyRepository.state.value.activeEvent
                                                    ?.let { active ->
                                                        appGraph.emergencyRepository.deactivate(
                                                            active.copy(deactivatedAt = Instant.now()),
                                                        )
                                                    }
                                                appGraph.settingsRepository.save(
                                                    appGraph.settingsRepository.settings.value.copy(
                                                        emergencyActive = false,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
            composable(AppRoute.Limits.path) {
                MainDestinationScaffold(navController, AppRoute.Limits) {
                    LimitsRoute(limitsHolder)
                }
            }
            composable(AppRoute.Tasks.path) {
                MainDestinationScaffold(navController, AppRoute.Tasks) {
                    TaskSettingsScreen(
                        state = buildTaskSettingsState(settings, gateCycle, taskPresets),
                        onAction = { action ->
                            when (action) {
                                is TaskSettingsAction.SetMediumThreshold -> scope.launch {
                                    val current = appGraph.settingsRepository.settings.value
                                    val medium = action.minutes.coerceIn(1, 120)
                                    appGraph.settingsRepository.save(
                                        current.copy(
                                            difficultyMediumThresholdMinutes = medium,
                                            difficultyHardThresholdMinutes =
                                                current.difficultyHardThresholdMinutes
                                                    .coerceAtLeast(medium + 1),
                                        ),
                                    )
                                }

                                is TaskSettingsAction.SetHardThreshold -> scope.launch {
                                    val current = appGraph.settingsRepository.settings.value
                                    appGraph.settingsRepository.save(
                                        current.copy(
                                            difficultyHardThresholdMinutes = action.minutes.coerceIn(
                                                current.difficultyMediumThresholdMinutes + 1,
                                                240,
                                            ),
                                        ),
                                    )
                                }

                                is TaskSettingsAction.SetDecayBreakMinutes -> scope.launch {
                                    val current = appGraph.settingsRepository.settings.value
                                    appGraph.settingsRepository.save(
                                        current.copy(
                                            difficultyDecayBreakMinutes =
                                                action.minutes.coerceIn(1, 30),
                                        ),
                                    )
                                }

                                is TaskSettingsAction.SetTaskTypeEnabled -> scope.launch {
                                    val current = appGraph.settingsRepository.settings.value
                                    val updated = if (action.enabled) {
                                        current.enabledTaskTypes + action.type
                                    } else {
                                        current.enabledTaskTypes - action.type
                                    }.ifEmpty { setOf(TaskType.ARITHMETIC) }
                                    appGraph.settingsRepository.save(
                                        current.copy(enabledTaskTypes = updated),
                                    )
                                }

                                is TaskSettingsAction.CreatePreset -> scope.launch {
                                    val preset = CustomTaskPreset(
                                        id = UUID.randomUUID().toString(),
                                        title = action.title,
                                        instruction = action.instruction,
                                        createdAt = Instant.now(),
                                    )
                                    appGraph.taskPresetRepository.save(preset)
                                    val current = appGraph.settingsRepository.settings.value
                                    appGraph.settingsRepository.save(
                                        current.copy(
                                            enabledTaskTypes = current.enabledTaskTypes +
                                                TaskType.CUSTOM,
                                        ),
                                    )
                                    snackbarHostState.showSnackbar("Пресет сохранён и включён")
                                }

                                is TaskSettingsAction.SetPresetEnabled -> scope.launch {
                                    appGraph.taskPresetRepository.save(
                                        action.preset.copy(enabled = action.enabled),
                                    )
                                }

                                is TaskSettingsAction.DeletePreset -> scope.launch {
                                    appGraph.taskPresetRepository.delete(action.presetId)
                                    val remaining = appGraph.taskPresetRepository.presets.value
                                        .filterNot { it.id == action.presetId }
                                    if (remaining.none(CustomTaskPreset::enabled)) {
                                        val current = appGraph.settingsRepository.settings.value
                                        appGraph.settingsRepository.save(
                                            current.copy(
                                                enabledTaskTypes =
                                                    (current.enabledTaskTypes - TaskType.CUSTOM)
                                                        .ifEmpty { setOf(TaskType.ARITHMETIC) },
                                            ),
                                        )
                                    }
                                }

                                is TaskSettingsAction.PrepareAccess -> scope.launch {
                                    appGraph.monitoring?.prepareChallengeForApp(action.target)
                                }
                            }
                        },
                    )
                }
            }
            composable(AppRoute.Settings.path) {
                MainDestinationScaffold(navController, AppRoute.Settings) {
                    SettingsScreen(
                        state = settingsState,
                        onAction = { action ->
                            when (action) {
                                SettingsAction.OpenEmergencyHistory ->
                                    navController.navigate(AppRoute.History.path)

                                SettingsAction.OpenAccessibilitySettings ->
                                    appGraph.systemAccess?.let { access ->
                                        context.openSystemSettings(access.accessibilitySettingsIntent())
                                    }

                                SettingsAction.OpenUsageAccessSettings ->
                                    appGraph.systemAccess?.let { access ->
                                        context.openSystemSettings(access.usageAccessSettingsIntent())
                                    }

                                SettingsAction.RefreshSystemAccess -> {
                                    appGraph.systemAccess?.refresh()
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Статусы обновлены")
                                    }
                                }

                                SettingsAction.OpenPrivacyDocument,
                                SettingsAction.OpenLicenses,
                                -> scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Документ будет добавлен перед публикацией",
                                    )
                                }
                            }
                        },
                    )
                }
            }
            composable(AppRoute.History.path) {
                EmergencyHistoryRoute(historyHolder)
            }
            composable(AppRoute.Emergency.path) {
                val state by emergencyHolder.state.collectAsStateWithLifecycle()
                BlockingOverlayScreen(
                    state = state,
                    onAction = { action ->
                        if (
                            action == BlockingOverlayAction.CancelEmergency ||
                            action == BlockingOverlayAction.SystemBack
                        ) {
                            emergencyHolder.dispatch(BlockingOverlayAction.CancelEmergency)
                            navController.popBackStack()
                        } else {
                            emergencyHolder.dispatch(action)
                        }
                    },
                )
            }
        }
        SnackbarHost(hostState = snackbarHostState)
        activeEnforcement?.let { enforcement ->
            InAppChallengeHost(
                enforcement = enforcement,
                monitoring = appGraph.monitoring,
                snackbarHostState = snackbarHostState,
            )
        }
    }
}

@Composable
private fun InAppChallengeHost(
    enforcement: EnforcementUiState,
    monitoring: MonitoringCoordinator,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    lateinit var holder: BlockingOverlayStateHolder
    holder = remember(enforcement, monitoring, scope) {
        BlockingOverlayStateHolder(
            initialEnforcement = enforcement,
            emitEffect = { effect ->
                when (effect) {
                    is BlockingOverlayEffect.VerifyAnswer -> scope.launch {
                        val correct = monitoring.verifyAnswer(effect.taskId, effect.answer)
                        holder.dispatch(BlockingOverlayAction.AnswerChecked(correct))
                    }

                    BlockingOverlayEffect.RequestAnotherTask -> scope.launch {
                        monitoring.replaceTask()?.let { replacement ->
                            holder.dispatch(BlockingOverlayAction.ShowTask(replacement))
                        }
                    }

                    BlockingOverlayEffect.ExitYouTube -> scope.launch {
                        val current = holder.state.value.enforcement
                        val solved = (current as? EnforcementUiState.TaskGate)
                            ?.answerStatus == TaskAnswerStatus.CORRECT
                        if (solved) {
                            monitoring.completeChallengePresentation()
                        } else {
                            monitoring.dismissEnforcement(
                                recordExit = current is EnforcementUiState.TaskGate,
                            )
                            snackbarHostState.showSnackbar(
                                if (current is EnforcementUiState.TaskGate) {
                                    "Shorts останутся заблокированы до решения задания"
                                } else {
                                    "Дневной лимит продолжает действовать"
                                },
                            )
                        }
                    }

                    BlockingOverlayEffect.OpenYouTube -> scope.launch {
                        val target = (holder.state.value.enforcement as? EnforcementUiState.TaskGate)
                            ?.target ?: TaskTarget.YOUTUBE_SHORTS
                        monitoring.completeChallengePresentation()
                        context.openTarget(target) {
                            snackbarHostState.showSnackbar(
                                if (target == TaskTarget.INSTAGRAM) {
                                    "Не удалось открыть Instagram"
                                } else {
                                    "Не удалось открыть YouTube"
                                },
                            )
                        }
                    }

                    is BlockingOverlayEffect.ConfirmEmergency -> scope.launch {
                        monitoring.activateEmergency(effect.normalizedReason, effect.source)
                        holder.dispatch(
                            BlockingOverlayAction.EmergencyActivationFinished(succeeded = true),
                        )
                    }

                    BlockingOverlayEffect.TaskSolved,
                    BlockingOverlayEffect.EmergencyActivated,
                    -> Unit
                }
            },
        )
    }
    val state by holder.state.collectAsStateWithLifecycle()
    BlockingOverlayScreen(state = state, onAction = holder::dispatch)
}

private fun buildDashboardState(
    settings: UserSettings,
    usage: DailyUsage,
    cycle: GateCycle,
    pendingTask: PendingTask?,
    emergencyState: EmergencyState,
    access: SystemAccessSnapshot,
): DashboardUiState {
    val activeEmergency = emergencyState.activeEvent
    val now = Instant.now()
    return DashboardUiState(
        dateLabel = usage.localDate.format(
            DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")),
        ),
        accessibilityEnabled = access.accessibilityGranted,
        shorts = if (settings.shortsGateEnabled) {
            ShortsLimitUiState.Enabled(
                cycleUsedSeconds = cycle.usedSeconds,
                intervalSeconds = settings.shortsIntervalMinutes.toLong() * 60,
                todaySeconds = usage.shortsSeconds,
                seenToday = usage.shortsSeconds > 0,
                accessLocked = !(
                    settings.emergencyActive || emergencyState.isActive
                    ) && (
                    pendingTask?.target == TaskTarget.YOUTUBE_SHORTS ||
                        cycle.entryCooldownUntil?.isAfter(now) != true
                    ),
                unlockedUntilLabel = cycle.entryCooldownUntil
                    ?.takeIf { it.isAfter(now) }
                    ?.atZone(ZoneId.systemDefault())
                    ?.format(DateTimeFormatter.ofPattern("HH:mm")),
            )
        } else {
            ShortsLimitUiState.Disabled
        },
        instagram = if (settings.instagramGateEnabled) {
            InstagramLimitUiState.Enabled(
                cycleUsedSeconds = cycle.instagramUsedSeconds,
                intervalSeconds = settings.instagramIntervalMinutes.toLong() * 60,
                todaySeconds = usage.instagramSeconds,
                accessLocked = !(
                    settings.emergencyActive || emergencyState.isActive
                    ) && (
                    pendingTask?.target == TaskTarget.INSTAGRAM ||
                        cycle.instagramEntryCooldownUntil?.isAfter(now) != true
                    ),
                unlockedUntilLabel = cycle.instagramEntryCooldownUntil
                    ?.takeIf { it.isAfter(now) }
                    ?.atZone(ZoneId.systemDefault())
                    ?.format(DateTimeFormatter.ofPattern("HH:mm")),
            )
        } else {
            InstagramLimitUiState.Disabled
        },
        daily = when {
            !settings.dailyLimitEnabled -> DailyLimitUiState.Disabled
            !access.usageAccessGranted -> DailyLimitUiState.Unavailable
            else -> DailyLimitUiState.Enabled(
                usedSeconds = usage.youtubeSeconds,
                limitSeconds = settings.dailyLimitMinutes.toLong() * 60,
            )
        },
        emergency = EmergencyUiState(
            active = settings.emergencyActive || emergencyState.isActive,
            activeSinceLabel = activeEmergency?.activatedAt?.atZone(ZoneId.systemDefault())
                ?.format(DateTimeFormatter.ofPattern("HH:mm")),
        ),
    )
}

private fun buildSettingsState(
    access: SystemAccessSnapshot,
    diagnostics: MonitoringDiagnostics,
): SettingsUiState = SettingsUiState(
    accessibilityStatus = if (access.accessibilityGranted) {
        SystemAccessUiStatus.ENABLED
    } else {
        SystemAccessUiStatus.NOT_ENABLED
    },
    usageAccessStatus = if (access.usageAccessGranted) {
        SystemAccessUiStatus.ENABLED
    } else {
        SystemAccessUiStatus.NOT_ENABLED
    },
    youtubeVersionLabel = access.youtubeVersionName,
    diagnostics = RedactedDiagnosticsUiState(
        detectorStatus = when {
            !access.accessibilityGranted -> DetectorUiStatus.INACTIVE
            diagnostics.detectorState == ShortsDetectionState.UNKNOWN ->
                DetectorUiStatus.UNKNOWN_LAYOUT

            else -> DetectorUiStatus.READY
        },
        lastRecognitionLabel = diagnostics.lastRecognitionAt?.atZone(ZoneId.systemDefault())
            ?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
        lastResultCode = when (diagnostics.detectorState) {
            ShortsDetectionState.SHORTS_CONFIRMED -> DiagnosticResultCode.SHORTS_CONFIRMED
            ShortsDetectionState.NOT_SHORTS -> DiagnosticResultCode.NON_SHORTS_CONFIRMED
            ShortsDetectionState.UNKNOWN -> DiagnosticResultCode.UNKNOWN
        },
        unknownCount = diagnostics.unknownCount,
        rulesVersion = diagnostics.rulesVersion,
    ),
    appVersionLabel = access.appVersionName,
)

private fun buildTaskSettingsState(
    settings: UserSettings,
    cycle: GateCycle,
    presets: List<CustomTaskPreset>,
): TaskSettingsUiState {
    val loadMinutes = (cycle.difficultyLoadSeconds / 60).toInt()
    val difficulty = when {
        loadMinutes >= settings.difficultyHardThresholdMinutes -> TaskDifficulty.HARD
        loadMinutes >= settings.difficultyMediumThresholdMinutes -> TaskDifficulty.MEDIUM
        else -> TaskDifficulty.EASY
    }
    return TaskSettingsUiState(
        loadMinutes = loadMinutes,
        currentDifficulty = difficulty,
        mediumThresholdMinutes = settings.difficultyMediumThresholdMinutes,
        hardThresholdMinutes = settings.difficultyHardThresholdMinutes,
        decayBreakMinutes = settings.difficultyDecayBreakMinutes,
        enabledTypes = settings.enabledTaskTypes,
        presets = presets,
        instagramEnabled = settings.instagramGateEnabled,
    )
}

private fun refreshOnboardingPermissions(
    holder: OnboardingStateHolder,
    graph: NoScrollAppGraph,
) {
    val access = graph.systemAccess?.refresh() ?: return
    val state = holder.state.value
    when {
        state.waitingForAccessibilityReturn -> holder.dispatch(
            OnboardingAction.AccessibilitySettingsReturned(access.accessibilityGranted),
        )

        state.waitingForUsageReturn -> holder.dispatch(
            OnboardingAction.UsageAccessSettingsReturned(access.usageAccessGranted),
        )

        state.step == OnboardingStep.READINESS -> holder.dispatch(
            OnboardingAction.RefreshReadiness(
                accessibilityEnabled = access.accessibilityGranted,
                usageAccessEnabled = access.usageAccessGranted,
                youtubeInstalled = access.youtubeInstalled,
            ),
        )
    }
}

private fun EmergencyEvent.toHistoryItem(): EmergencyHistoryItemUi {
    val end = deactivatedAt ?: Instant.now()
    val formatter = DateTimeFormatter.ofPattern(
        "d MMM, HH:mm",
        Locale.forLanguageTag("ru"),
    )
    return EmergencyHistoryItemUi(
        id = id,
        reason = reason,
        activatedAtLabel = activatedAt.atZone(ZoneId.systemDefault()).format(formatter),
        deactivatedAtLabel = deactivatedAt?.atZone(ZoneId.systemDefault())?.format(formatter),
        durationMinutes = Duration.between(activatedAt, end).toMinutes().coerceAtLeast(0),
        youtubeMinutesDuring = youtubeSecondsDuring.coerceAtLeast(0) / 60,
        sourceLabel = when (activationSource) {
            EmergencyActivationSource.DASHBOARD -> "Экран «Сегодня»"
            EmergencyActivationSource.TASK_GATE -> "Задание Shorts"
            EmergencyActivationSource.DAILY_LIMIT -> "Дневной лимит"
        },
    )
}

private fun Context.openSystemSettings(
    intent: android.content.Intent,
    onUnavailable: () -> Unit = {},
) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onUnavailable()
    }
}

private suspend fun Context.openTarget(
    target: TaskTarget,
    onUnavailable: suspend () -> Unit,
) {
    val packageName = when (target) {
        TaskTarget.YOUTUBE_SHORTS -> YOUTUBE_PACKAGE_NAME
        TaskTarget.INSTAGRAM -> INSTAGRAM_PACKAGE_NAME
    }
    val intent = packageManager.getLaunchIntentForPackage(packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent == null) {
        onUnavailable()
        return
    }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onUnavailable()
    }
}

@Composable
private fun MainDestinationScaffold(
    navController: NavHostController,
    selectedRoute: AppRoute,
    content: @Composable () -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                AppRoute.topLevel.forEach { destination ->
                    NavigationBarItem(
                        selected = selectedRoute == destination,
                        onClick = { navController.navigateTopLevel(destination) },
                        icon = {
                            Text(
                                text = destination.marker,
                                modifier = Modifier.semantics {
                                    contentDescription = destination.label
                                },
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            content()
        }
    }
}

private fun NavHostController.navigateTopLevel(destination: AppRoute) {
    navigate(destination.path) {
        popUpTo(AppRoute.Dashboard.path) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private sealed class AppRoute(
    val path: String,
    val label: String,
    val marker: String,
) {
    data object Onboarding : AppRoute("onboarding", "Onboarding", "O")
    data object Dashboard : AppRoute("dashboard", "Сегодня", "С")
    data object Limits : AppRoute("limits", "Ограничения", "О")
    data object Tasks : AppRoute("tasks", "Задания", "З")
    data object Settings : AppRoute("settings", "Настройки", "Н")
    data object History : AppRoute("history", "История", "И")
    data object Emergency : AppRoute("emergency", "Emergency", "E")

    companion object {
        val topLevel = listOf(Dashboard, Limits, Tasks, Settings)
    }
}

private const val YOUTUBE_PACKAGE_NAME = "com.google.android.youtube"
private const val INSTAGRAM_PACKAGE_NAME = "com.instagram.android"
