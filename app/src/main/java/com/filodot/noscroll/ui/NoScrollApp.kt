package com.filodot.noscroll.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
import com.filodot.noscroll.core.focus.FocusAppCatalog
import com.filodot.noscroll.core.model.EmergencyEvent
import com.filodot.noscroll.core.model.EmergencyState
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.model.ShortsDetectionState
import com.filodot.noscroll.core.model.CustomTaskPreset
import com.filodot.noscroll.core.model.TaskTarget
import com.filodot.noscroll.core.model.TaskType
import com.filodot.noscroll.core.model.UserSettings
import com.filodot.noscroll.core.tasks.TaskDifficultyConfig
import com.filodot.noscroll.core.tasks.TaskDifficultyPolicy
import com.filodot.noscroll.core.tasks.TaskDifficultyState
import com.filodot.noscroll.core.learning.model.CourseStatus
import com.filodot.noscroll.core.learning.model.LearningCourse
import com.filodot.noscroll.feature.dashboard.DashboardAction
import com.filodot.noscroll.feature.dashboard.DashboardMonitoringState
import com.filodot.noscroll.feature.dashboard.DashboardScreen
import com.filodot.noscroll.feature.dashboard.DashboardUiState
import com.filodot.noscroll.feature.dashboard.DailyLimitUiState
import com.filodot.noscroll.feature.dashboard.AppLimitUiState
import com.filodot.noscroll.feature.dashboard.EmergencyUiState
import com.filodot.noscroll.feature.dashboard.FocusAppUi
import com.filodot.noscroll.feature.dashboard.FocusModeUiState
import com.filodot.noscroll.feature.dashboard.ShortsLimitUiState
import com.filodot.noscroll.feature.dashboard.buildUsageStatistics
import com.filodot.noscroll.feature.history.EmergencyHistoryAction
import com.filodot.noscroll.feature.history.EmergencyHistoryEffect
import com.filodot.noscroll.feature.history.EmergencyHistoryItemUi
import com.filodot.noscroll.feature.history.EmergencyHistoryRoute
import com.filodot.noscroll.feature.history.EmergencyHistoryStateHolder
import com.filodot.noscroll.feature.learning.LearningRoute
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
import com.filodot.noscroll.feature.tasks.LearningCourseChoiceUi
import com.filodot.noscroll.monitoring.runtime.MonitoringDiagnostics
import com.filodot.noscroll.monitoring.runtime.MonitoringCoordinator
import com.filodot.noscroll.monitoring.runtime.MonitoringHealthStatus
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
    val usageHistory by appGraph.usageRepository.usageHistory.collectAsStateWithLifecycle()
    val gateCycle by appGraph.usageRepository.gateCycle.collectAsStateWithLifecycle()
    val pendingTask by appGraph.taskRepository.pendingTask.collectAsStateWithLifecycle()
    val taskPresets by appGraph.taskPresetRepository.presets.collectAsStateWithLifecycle()
    val learningCourses by appGraph.learningRepository.courses.collectAsStateWithLifecycle(
        initialValue = emptyList(),
    )
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
            usageHistory = usageHistory,
            cycle = gateCycle,
            pendingTask = pendingTask,
            emergencyState = emergencyState,
            access = accessState,
            diagnostics = diagnostics ?: MonitoringDiagnostics(),
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
                youtubeEnabled = settings.youtubeGateEnabled,
                youtubeMinutes = settings.youtubeIntervalMinutes,
                pinterestEnabled = settings.pinterestGateEnabled,
                pinterestMinutes = settings.pinterestIntervalMinutes,
                chromeEnabled = settings.chromeGateEnabled,
                chromeMinutes = settings.chromeIntervalMinutes,
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
                            youtubeGateEnabled = effect.values.youtubeEnabled,
                            youtubeIntervalMinutes = effect.values.youtubeMinutes,
                            pinterestGateEnabled = effect.values.pinterestEnabled,
                            pinterestIntervalMinutes = effect.values.pinterestMinutes,
                            chromeGateEnabled = effect.values.chromeEnabled,
                            chromeIntervalMinutes = effect.values.chromeMinutes,
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

                OnboardingEffect.OpenAppDetailsSettings ->
                    appGraph.systemAccess?.let { access ->
                        context.openSystemSettings(access.appDetailsSettingsIntent())
                    }

                OnboardingEffect.ShowHowItWorks -> scope.launch {
                    snackbarHostState.showSnackbar("NoScroll создаёт паузу, не запрещая выход")
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
                appGraph.monitoring?.requestHealthCheck()
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

                                DashboardAction.OpenDiagnostics ->
                                    navController.navigate(AppRoute.Settings.path)

                                DashboardAction.OpenChallenge -> scope.launch {
                                    appGraph.monitoring?.prepareChallengeForApp()
                                }

                                is DashboardAction.OpenAppChallenge -> scope.launch {
                                    appGraph.monitoring?.prepareChallengeForApp(action.target)
                                }

                                is DashboardAction.StartFocusMode -> scope.launch {
                                    val monitoring = appGraph.monitoring
                                    val started = if (monitoring != null) {
                                        monitoring.startFocusMode(
                                            action.durationMinutes,
                                            action.packageNames,
                                        )
                                    } else {
                                        val now = Instant.now()
                                        val packages = FocusAppCatalog.sanitize(action.packageNames)
                                        if (packages.isEmpty()) {
                                            false
                                        } else {
                                            appGraph.settingsRepository.save(
                                                appGraph.settingsRepository.settings.value.copy(
                                                    focusDurationMinutes = action.durationMinutes,
                                                    focusBlockedPackages = packages,
                                                    focusStartedAt = now,
                                                    focusEndsAt = now.plusSeconds(
                                                        action.durationMinutes.toLong() * 60,
                                                    ),
                                                ),
                                            )
                                            true
                                        }
                                    }
                                    snackbarHostState.showSnackbar(
                                        if (started) {
                                            "Режим «Не отвлекаться» включён"
                                        } else {
                                            "Не удалось включить фокус: проверьте Accessibility"
                                        },
                                    )
                                }

                                DashboardAction.OpenFocusEmergency -> {
                                    emergencyHolder.dispatch(
                                        BlockingOverlayAction.OpenEmergencyFormFor(
                                            EmergencyActivationSource.FOCUS_MODE,
                                        ),
                                    )
                                    navController.navigate(AppRoute.Emergency.path)
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
                        state = buildTaskSettingsState(
                            settings,
                            gateCycle,
                            taskPresets,
                            learningCourses,
                        ),
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
                                        current.copy(
                                            enabledTaskTypes = updated,
                                            selectedLearningCourseIds = if (
                                                action.type == TaskType.LEARNING &&
                                                action.enabled &&
                                                current.selectedLearningCourseIds.isEmpty()
                                            ) {
                                                learningCourses.mapTo(mutableSetOf()) { it.id }
                                            } else {
                                                current.selectedLearningCourseIds
                                            },
                                        ),
                                    )
                                }

                                is TaskSettingsAction.SetLearningCourseEnabled -> scope.launch {
                                    val current = appGraph.settingsRepository.settings.value
                                    val selected = if (action.enabled) {
                                        current.selectedLearningCourseIds + action.courseId
                                    } else {
                                        current.selectedLearningCourseIds - action.courseId
                                    }
                                    appGraph.settingsRepository.save(
                                        current.copy(selectedLearningCourseIds = selected),
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
            composable(AppRoute.Learning.path) {
                MainDestinationScaffold(navController, AppRoute.Learning) {
                    LearningRoute(
                        repository = appGraph.learningRepository,
                        aiCredentials = appGraph.aiCredentialRepository,
                        aiGateway = appGraph.aiGateway,
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

                                SettingsAction.OpenAppDetailsSettings ->
                                    appGraph.systemAccess?.let { access ->
                                        context.openSystemSettings(access.appDetailsSettingsIntent())
                                    }

                                SettingsAction.RefreshSystemAccess -> {
                                    appGraph.systemAccess?.refresh()
                                    appGraph.monitoring?.requestHealthCheck()
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Запущена повторная проверка")
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
                                    "Доступ к ${current.target.appLabel()} останется закрыт до решения задания"
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
                                "Не удалось открыть ${target.appLabel()}",
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
    usageHistory: List<DailyUsage>,
    cycle: GateCycle,
    pendingTask: PendingTask?,
    emergencyState: EmergencyState,
    access: SystemAccessSnapshot,
    diagnostics: MonitoringDiagnostics,
): DashboardUiState {
    val activeEmergency = emergencyState.activeEvent
    val now = Instant.now()
    return DashboardUiState(
        dateLabel = usage.localDate.format(
            DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")),
        ),
        accessibilityEnabled = access.accessibilityGranted,
        monitoringState = when {
            !diagnostics.serviceConnected -> DashboardMonitoringState.DISCONNECTED
            diagnostics.healthStatus == MonitoringHealthStatus.STARTING ->
                DashboardMonitoringState.STARTING
            diagnostics.healthStatus == MonitoringHealthStatus.RECOVERING ->
                DashboardMonitoringState.RECOVERING
            diagnostics.healthStatus == MonitoringHealthStatus.RUNNING ->
                DashboardMonitoringState.RUNNING
            else -> DashboardMonitoringState.DISCONNECTED
        },
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
        youtube = buildAppLimitState(
            enabled = settings.youtubeGateEnabled,
            intervalMinutes = settings.youtubeIntervalMinutes,
            cycleUsedSeconds = cycle.youtubeUsedSeconds,
            todaySeconds = usage.youtubeSeconds,
            cooldownUntil = cycle.youtubeEntryCooldownUntil,
            target = TaskTarget.YOUTUBE,
            pendingTask = pendingTask,
            bypassActive = settings.emergencyActive || emergencyState.isActive,
            now = now,
        ),
        instagram = buildAppLimitState(
            enabled = settings.instagramGateEnabled,
            intervalMinutes = settings.instagramIntervalMinutes,
            cycleUsedSeconds = cycle.instagramUsedSeconds,
            todaySeconds = usage.instagramSeconds,
            cooldownUntil = cycle.instagramEntryCooldownUntil,
            target = TaskTarget.INSTAGRAM,
            pendingTask = pendingTask,
            bypassActive = settings.emergencyActive || emergencyState.isActive,
            now = now,
        ),
        pinterest = buildAppLimitState(
            enabled = settings.pinterestGateEnabled,
            intervalMinutes = settings.pinterestIntervalMinutes,
            cycleUsedSeconds = cycle.pinterestUsedSeconds,
            todaySeconds = usage.pinterestSeconds,
            cooldownUntil = cycle.pinterestEntryCooldownUntil,
            target = TaskTarget.PINTEREST,
            pendingTask = pendingTask,
            bypassActive = settings.emergencyActive || emergencyState.isActive,
            now = now,
        ),
        chrome = buildAppLimitState(
            enabled = settings.chromeGateEnabled,
            intervalMinutes = settings.chromeIntervalMinutes,
            cycleUsedSeconds = cycle.chromeUsedSeconds,
            todaySeconds = usage.chromeSeconds,
            cooldownUntil = cycle.chromeEntryCooldownUntil,
            target = TaskTarget.CHROME,
            pendingTask = pendingTask,
            bypassActive = settings.emergencyActive || emergencyState.isActive,
            now = now,
        ),
        statistics = buildUsageStatistics(usage, usageHistory),
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
        focusMode = settings.focusEndsAt.let { endsAt ->
            val active = endsAt?.isAfter(now) == true &&
                settings.focusBlockedPackages.isNotEmpty()
            val packages = FocusAppCatalog.sanitize(settings.focusBlockedPackages)
            FocusModeUiState(
                active = active,
                endsAtLabel = endsAt
                    ?.takeIf { active }
                    ?.atZone(ZoneId.systemDefault())
                    ?.format(DateTimeFormatter.ofPattern("HH:mm")),
                remainingMinutes = endsAt
                    ?.takeIf { active }
                    ?.let { end ->
                        (Duration.between(now, end).seconds.coerceAtLeast(0) + 59) / 60
                    }
                    ?: 0,
                durationMinutes = settings.focusDurationMinutes,
                selectedPackages = packages,
                blockedAppLabels = FocusAppCatalog.apps
                    .filter { it.packageName in packages }
                    .map { it.label },
                availableApps = FocusAppCatalog.apps.map {
                    FocusAppUi(packageName = it.packageName, label = it.label)
                },
            )
        },
    )
}

private fun buildAppLimitState(
    enabled: Boolean,
    intervalMinutes: Int,
    cycleUsedSeconds: Long,
    todaySeconds: Long,
    cooldownUntil: Instant?,
    target: TaskTarget,
    pendingTask: PendingTask?,
    bypassActive: Boolean,
    now: Instant,
): AppLimitUiState = if (enabled) {
    AppLimitUiState.Enabled(
        cycleUsedSeconds = cycleUsedSeconds,
        intervalSeconds = intervalMinutes.coerceAtLeast(1).toLong() * 60,
        todaySeconds = todaySeconds,
        accessLocked = !bypassActive && (
            pendingTask?.target == target || cooldownUntil?.isAfter(now) != true
            ),
        unlockedUntilLabel = cooldownUntil
            ?.takeIf { it.isAfter(now) }
            ?.atZone(ZoneId.systemDefault())
            ?.format(DateTimeFormatter.ofPattern("HH:mm")),
    )
} else {
    AppLimitUiState.Disabled
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
    instagramVersionLabel = access.instagramVersionName,
    monitoringHealthLabel = when (diagnostics.healthStatus) {
        MonitoringHealthStatus.DISCONNECTED -> "Не подключён"
        MonitoringHealthStatus.STARTING -> "Запускается"
        MonitoringHealthStatus.RUNNING -> "Работает"
        MonitoringHealthStatus.RECOVERING -> "Восстанавливается"
    },
    monitoringHealthy = diagnostics.serviceConnected &&
        diagnostics.healthStatus == MonitoringHealthStatus.RUNNING,
    lastHeartbeatLabel = diagnostics.lastHeartbeatAt?.atZone(ZoneId.systemDefault())
        ?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)),
    lastInstagramEventLabel = diagnostics.lastInstagramEventAt?.atZone(ZoneId.systemDefault())
        ?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)),
    recoveryCount = diagnostics.recoveryCount,
    lastFailureCode = diagnostics.lastFailureCode,
    diagnostics = RedactedDiagnosticsUiState(
        detectorStatus = when {
            !access.accessibilityGranted -> DetectorUiStatus.INACTIVE
            diagnostics.healthStatus == MonitoringHealthStatus.RECOVERING ->
                DetectorUiStatus.ERROR
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
    courses: List<LearningCourse>,
): TaskSettingsUiState {
    val difficultyPolicy = TaskDifficultyPolicy()
    val difficultyConfig = TaskDifficultyConfig(
        mediumThresholdMinutes = settings.difficultyMediumThresholdMinutes,
        hardThresholdMinutes = settings.difficultyHardThresholdMinutes,
        decayBreakMinutesPerLoadMinute = settings.difficultyDecayBreakMinutes,
    )
    val effectiveLoad = difficultyPolicy.update(
        state = TaskDifficultyState(
            loadSeconds = cycle.difficultyLoadSeconds,
            updatedAt = cycle.difficultyLoadUpdatedAt,
            recoverySeconds = cycle.difficultyRecoverySeconds,
        ),
        now = Instant.now(),
        distractingAppActive = false,
        config = difficultyConfig,
    )
    val loadMinutes = (effectiveLoad.loadSeconds / 60).toInt()
    val difficulty = difficultyPolicy.difficulty(effectiveLoad, difficultyConfig)
    return TaskSettingsUiState(
        loadMinutes = loadMinutes,
        currentDifficulty = difficulty,
        mediumThresholdMinutes = settings.difficultyMediumThresholdMinutes,
        hardThresholdMinutes = settings.difficultyHardThresholdMinutes,
        decayBreakMinutes = settings.difficultyDecayBreakMinutes,
        enabledTypes = settings.enabledTaskTypes,
        presets = presets,
        enabledTargets = buildSet {
            if (settings.shortsGateEnabled) add(TaskTarget.YOUTUBE_SHORTS)
            if (settings.youtubeGateEnabled) add(TaskTarget.YOUTUBE)
            if (settings.instagramGateEnabled) add(TaskTarget.INSTAGRAM)
            if (settings.pinterestGateEnabled) add(TaskTarget.PINTEREST)
            if (settings.chromeGateEnabled) add(TaskTarget.CHROME)
        },
        learningCourses = courses
            .filter { it.status == CourseStatus.READY || it.status == CourseStatus.ACTIVE }
            .map { LearningCourseChoiceUi(it.id, it.title) },
        selectedLearningCourseIds = settings.selectedLearningCourseIds,
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
                instagramInstalled = access.instagramInstalled,
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
            EmergencyActivationSource.TASK_GATE -> "Задание ограничения"
            EmergencyActivationSource.DAILY_LIMIT -> "Дневной лимит"
            EmergencyActivationSource.FOCUS_MODE -> "Режим «Не отвлекаться»"
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
        TaskTarget.YOUTUBE -> YOUTUBE_PACKAGE_NAME
        TaskTarget.INSTAGRAM -> INSTAGRAM_PACKAGE_NAME
        TaskTarget.PINTEREST -> FocusAppCatalog.PINTEREST
        TaskTarget.CHROME -> FocusAppCatalog.CHROME
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

private fun TaskTarget.appLabel(): String = when (this) {
    TaskTarget.YOUTUBE_SHORTS -> "YouTube Shorts"
    TaskTarget.YOUTUBE -> "YouTube"
    TaskTarget.INSTAGRAM -> "Instagram"
    TaskTarget.PINTEREST -> "Pinterest"
    TaskTarget.CHROME -> "Chrome"
}

@Composable
private fun MainDestinationScaffold(
    navController: NavHostController,
    selectedRoute: AppRoute,
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                AppRoute.topLevel.forEach { destination ->
                    NavigationBarItem(
                        selected = selectedRoute == destination,
                        onClick = { navController.navigateTopLevel(destination) },
                        icon = {
                            AppNavigationIcon(
                                destination = destination,
                                selected = selectedRoute == destination,
                                modifier = Modifier.semantics {
                                    contentDescription = destination.label
                                },
                            )
                        },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
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

@Composable
private fun AppNavigationIcon(
    destination: AppRoute,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(modifier = modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        when (destination) {
            AppRoute.Dashboard -> {
                drawLine(
                    color,
                    Offset(size.width * .14f, size.height * .48f),
                    Offset(size.width * .5f, size.height * .17f),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * .5f, size.height * .17f),
                    Offset(size.width * .86f, size.height * .48f),
                    stroke,
                    StrokeCap.Round,
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .25f, size.height * .43f),
                    size = Size(size.width * .5f, size.height * .43f),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(stroke),
                )
            }

            AppRoute.Limits -> {
                drawCircle(color, radius = size.minDimension * .39f, style = Stroke(stroke))
                drawLine(color, center, Offset(center.x, size.height * .28f), stroke, StrokeCap.Round)
                drawLine(color, center, Offset(size.width * .68f, size.height * .58f), stroke, StrokeCap.Round)
            }

            AppRoute.Tasks -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .22f, size.height * .16f),
                    size = Size(size.width * .56f, size.height * .7f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(stroke),
                )
                drawLine(color, Offset(size.width * .36f, size.height * .4f), Offset(size.width * .65f, size.height * .4f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * .36f, size.height * .6f), Offset(size.width * .65f, size.height * .6f), stroke, StrokeCap.Round)
            }

            AppRoute.Learning -> {
                drawLine(color, Offset(center.x, size.height * .22f), Offset(center.x, size.height * .82f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * .12f, size.height * .27f), Offset(size.width * .42f, size.height * .35f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * .12f, size.height * .27f), Offset(size.width * .12f, size.height * .72f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * .12f, size.height * .72f), Offset(size.width * .42f, size.height * .8f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * .88f, size.height * .27f), Offset(size.width * .58f, size.height * .35f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * .88f, size.height * .27f), Offset(size.width * .88f, size.height * .72f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * .88f, size.height * .72f), Offset(size.width * .58f, size.height * .8f), stroke, StrokeCap.Round)
            }

            AppRoute.Settings -> {
                drawCircle(color, radius = size.minDimension * .18f, style = Stroke(stroke))
                drawCircle(color, radius = size.minDimension * .36f, style = Stroke(stroke))
                repeat(8) { index ->
                    val angle = Math.toRadians(index * 45.0)
                    val inner = size.minDimension * .39f
                    val outer = size.minDimension * .47f
                    drawLine(
                        color,
                        Offset(
                            center.x + kotlin.math.cos(angle).toFloat() * inner,
                            center.y + kotlin.math.sin(angle).toFloat() * inner,
                        ),
                        Offset(
                            center.x + kotlin.math.cos(angle).toFloat() * outer,
                            center.y + kotlin.math.sin(angle).toFloat() * outer,
                        ),
                        stroke,
                        StrokeCap.Round,
                    )
                }
            }

            else -> drawCircle(color, radius = size.minDimension * .3f, style = Stroke(stroke))
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

internal sealed class AppRoute(
    val path: String,
    val label: String,
    val marker: String,
) {
    data object Onboarding : AppRoute("onboarding", "Onboarding", "O")
    data object Dashboard : AppRoute("dashboard", "Сегодня", "С")
    data object Limits : AppRoute("limits", "Ограничения", "О")
    data object Tasks : AppRoute("tasks", "Задания", "З")
    data object Learning : AppRoute("learning", "Учёба", "У")
    data object Settings : AppRoute("settings", "Настройки", "Н")
    data object History : AppRoute("history", "История", "И")
    data object Emergency : AppRoute("emergency", "Emergency", "E")

    companion object {
        val topLevel: List<AppRoute>
            get() = listOf(Dashboard, Limits, Tasks, Learning, Settings)
    }
}

private const val YOUTUBE_PACKAGE_NAME = "com.google.android.youtube"
private const val INSTAGRAM_PACKAGE_NAME = "com.instagram.android"
