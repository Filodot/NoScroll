package com.filodot.noscroll.runtime

import android.app.Application
import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.data.local.datastore.DataStoreSettingsRepository
import com.filodot.noscroll.data.local.repository.RoomEmergencyRepository
import com.filodot.noscroll.data.local.repository.RoomTaskGrantTransaction
import com.filodot.noscroll.data.local.repository.RoomTaskRepository
import com.filodot.noscroll.data.local.repository.RoomTaskPresetRepository
import com.filodot.noscroll.data.local.repository.RoomUsageRepository
import com.filodot.noscroll.data.local.room.NoScrollDatabase
import com.filodot.noscroll.feature.dashboard.DashboardUiState
import com.filodot.noscroll.feature.history.EmergencyHistoryUiState
import com.filodot.noscroll.feature.settings.DetectorUiStatus
import com.filodot.noscroll.feature.settings.RedactedDiagnosticsUiState
import com.filodot.noscroll.feature.settings.SettingsUiState
import com.filodot.noscroll.feature.settings.SystemAccessUiStatus
import com.filodot.noscroll.monitoring.runtime.MonitoringCoordinator
import com.filodot.noscroll.monitoring.usagestats.AndroidUsageStatsSource
import com.filodot.noscroll.platform.AndroidSystemAccess
import com.filodot.noscroll.ui.NoScrollAppGraph
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class NoScrollRuntime private constructor(application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = NoScrollDatabase.build(application)
    private val today = LocalDate.now()
    private val now = Instant.now()

    val settingsRepository = DataStoreSettingsRepository(
        dataStore = DataStoreSettingsRepository.createDataStore(application),
        scope = applicationScope,
    )
    val usageRepository = RoomUsageRepository(
        dailyUsageDao = database.dailyUsageDao(),
        gateCycleDao = database.gateCycleDao(),
        scope = applicationScope,
        initialDailyUsage = DailyUsage(localDate = today, updatedAt = now),
        initialGateCycle = GateCycle(localDate = today, updatedAt = now),
    )
    val taskRepository = RoomTaskRepository(database.pendingTaskDao(), applicationScope)
    val taskPresetRepository = RoomTaskPresetRepository(
        database.customTaskPresetDao(),
        applicationScope,
    )
    val emergencyRepository = RoomEmergencyRepository(
        database.emergencyEventDao(),
        applicationScope,
    )
    val systemAccess = AndroidSystemAccess(application)
    private val repositoriesReady = combine(
        listOf(
            settingsRepository.initialized,
            usageRepository.dailyInitialized,
            usageRepository.gateInitialized,
            taskRepository.initialized,
            taskPresetRepository.initialized,
            emergencyRepository.initialized,
        ),
    ) { values -> values.all { it } }
        .stateIn(applicationScope, SharingStarted.Eagerly, false)
    val monitoring = MonitoringCoordinator(
        scope = applicationScope,
        settingsRepository = settingsRepository,
        usageRepository = usageRepository,
        taskRepository = taskRepository,
        taskPresetRepository = taskPresetRepository,
        emergencyRepository = emergencyRepository,
        taskGrantTransaction = RoomTaskGrantTransaction(database.taskGrantDao()),
        usageStatsSource = AndroidUsageStatsSource(application),
        systemAccess = systemAccess,
    )

    val appGraph = NoScrollAppGraph(
        settingsRepository = settingsRepository,
        usageRepository = usageRepository,
        taskRepository = taskRepository,
        taskPresetRepository = taskPresetRepository,
        emergencyRepository = emergencyRepository,
        dashboardState = DashboardUiState(dateLabel = "Сегодня"),
        settingsState = SettingsUiState(
            accessibilityStatus = SystemAccessUiStatus.NOT_ENABLED,
            usageAccessStatus = SystemAccessUiStatus.NOT_ENABLED,
            diagnostics = RedactedDiagnosticsUiState(DetectorUiStatus.INACTIVE),
            appVersionLabel = "—",
        ),
        historyState = EmergencyHistoryUiState(),
        settingsReady = repositoriesReady,
        systemAccess = systemAccess,
        monitoring = monitoring,
    )

    companion object {
        fun create(application: Application): NoScrollRuntime = NoScrollRuntime(application)
    }
}
