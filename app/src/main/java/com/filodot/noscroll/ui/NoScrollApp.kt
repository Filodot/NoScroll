package com.filodot.noscroll.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.filodot.noscroll.core.model.EmergencyEvent
import com.filodot.noscroll.core.model.EmergencyActivationSource
import com.filodot.noscroll.feature.dashboard.DashboardAction
import com.filodot.noscroll.feature.dashboard.DashboardScreen
import com.filodot.noscroll.feature.dashboard.EmergencyUiState
import com.filodot.noscroll.feature.history.EmergencyHistoryAction
import com.filodot.noscroll.feature.history.EmergencyHistoryEffect
import com.filodot.noscroll.feature.history.EmergencyHistoryItemUi
import com.filodot.noscroll.feature.history.EmergencyHistoryRoute
import com.filodot.noscroll.feature.history.EmergencyHistoryStateHolder
import com.filodot.noscroll.feature.limits.LimitsEffect
import com.filodot.noscroll.feature.limits.LimitsRoute
import com.filodot.noscroll.feature.limits.LimitsStateHolder
import com.filodot.noscroll.feature.limits.LimitsValues
import com.filodot.noscroll.feature.onboarding.OnboardingEffect
import com.filodot.noscroll.feature.onboarding.OnboardingRoute
import com.filodot.noscroll.feature.onboarding.OnboardingStateHolder
import com.filodot.noscroll.feature.overlay.BlockingOverlayAction
import com.filodot.noscroll.feature.overlay.BlockingOverlayEffect
import com.filodot.noscroll.feature.overlay.BlockingOverlayScreen
import com.filodot.noscroll.feature.overlay.BlockingOverlayStateHolder
import com.filodot.noscroll.feature.overlay.EnforcementUiState
import com.filodot.noscroll.feature.settings.SettingsAction
import com.filodot.noscroll.feature.settings.SettingsScreen
import java.time.Instant
import java.util.UUID
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
    var dashboardState by remember(appGraph) { mutableStateOf(appGraph.dashboardState) }

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

    val limitsHolder = remember(appGraph, scope) {
        val settings = appGraph.settingsRepository.settings.value
        LimitsStateHolder(
            initialValues = LimitsValues(
                preset = settings.preset,
                shortsEnabled = settings.shortsGateEnabled,
                shortsMinutes = settings.shortsIntervalMinutes,
                dailyEnabled = settings.dailyLimitEnabled,
                dailyMinutes = settings.dailyLimitMinutes,
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
                        val activatedAt = Instant.now()
                        appGraph.emergencyRepository.activate(
                            EmergencyEvent(
                                id = UUID.randomUUID().toString(),
                                reason = effect.normalizedReason,
                                activatedAt = activatedAt,
                                activationSource = effect.source,
                            ),
                        )
                        dashboardState = dashboardState.copy(
                            emergency = EmergencyUiState(
                                active = true,
                                activeSinceLabel = "сейчас",
                            ),
                        )
                        historyHolder.dispatch(
                            EmergencyHistoryAction.ReplaceItems(
                                listOf(
                                    EmergencyHistoryItemUi(
                                        id = appGraph.emergencyRepository.state.value.activeEvent
                                            ?.id ?: "active",
                                        reason = effect.normalizedReason,
                                        activatedAtLabel = "Сейчас",
                                        durationMinutes = 0,
                                        youtubeMinutesDuring = 0,
                                        sourceLabel = "Сегодня",
                                    ),
                                ),
                            ),
                        )
                        holder.dispatch(
                            BlockingOverlayAction.EmergencyActivationFinished(succeeded = true),
                        )
                        navController.popBackStack()
                    }

                    BlockingOverlayEffect.ExitYouTube -> scope.launch {
                        snackbarHostState.showSnackbar("В интеграционном режиме YouTube не открыт")
                    }

                    BlockingOverlayEffect.RequestAnotherTask,
                    BlockingOverlayEffect.TaskSolved,
                    BlockingOverlayEffect.EmergencyActivated,
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
                OnboardingEffect.OpenAccessibilitySettings -> holder.dispatch(
                    com.filodot.noscroll.feature.onboarding.OnboardingAction
                        .AccessibilitySettingsReturned(enabled = true),
                )

                OnboardingEffect.OpenUsageAccessSettings -> holder.dispatch(
                    com.filodot.noscroll.feature.onboarding.OnboardingAction
                        .UsageAccessSettingsReturned(enabled = true),
                )

                is OnboardingEffect.Completed -> scope.launch {
                    val current = appGraph.settingsRepository.settings.value
                    appGraph.settingsRepository.save(
                        current.copy(
                            onboardingCompleted = true,
                            preset = effect.selection.preset,
                            shortsIntervalMinutes = effect.selection.shortsIntervalMinutes,
                            dailyLimitMinutes = effect.selection.dailyLimitMinutes,
                            dailyLimitEnabled = effect.selection.dailyLimitEnabled,
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

                OnboardingEffect.RefreshPermissionStates -> Unit
            }
        }
        holder
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

                                DashboardAction.OpenAccessibilitySettings,
                                DashboardAction.OpenUsageAccessSettings,
                                -> scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Fake-доступ уже включён для IP-01",
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
                                            appGraph.emergencyRepository.state.value.activeEvent
                                                ?.let { active ->
                                                    appGraph.emergencyRepository.deactivate(
                                                        active.copy(deactivatedAt = Instant.now()),
                                                    )
                                                }
                                            dashboardState = dashboardState.copy(
                                                emergency = EmergencyUiState(),
                                            )
                                            historyHolder.dispatch(
                                                EmergencyHistoryAction.ReplaceItems(
                                                    historyHolder.state.value.items.map { item ->
                                                        if (item.isActive) {
                                                            item.copy(
                                                                deactivatedAtLabel = "сейчас",
                                                            )
                                                        } else {
                                                            item
                                                        }
                                                    },
                                                ),
                                            )
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
            composable(AppRoute.Settings.path) {
                MainDestinationScaffold(navController, AppRoute.Settings) {
                    SettingsScreen(
                        state = appGraph.settingsState,
                        onAction = { action ->
                            when (action) {
                                SettingsAction.OpenEmergencyHistory ->
                                    navController.navigate(AppRoute.History.path)

                                SettingsAction.OpenAccessibilitySettings,
                                SettingsAction.OpenUsageAccessSettings,
                                SettingsAction.RefreshSystemAccess,
                                SettingsAction.OpenPrivacyDocument,
                                SettingsAction.OpenLicenses,
                                -> scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Действие подключено к fake dependency graph",
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
    data object Settings : AppRoute("settings", "Настройки", "Н")
    data object History : AppRoute("history", "История", "И")
    data object Emergency : AppRoute("emergency", "Emergency", "E")

    companion object {
        val topLevel = listOf(Dashboard, Limits, Settings)
    }
}
