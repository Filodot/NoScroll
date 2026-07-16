package com.filodot.noscroll.ui

import com.filodot.noscroll.core.contracts.EmergencyRepository
import com.filodot.noscroll.core.contracts.SettingsRepository
import com.filodot.noscroll.core.contracts.TaskRepository
import com.filodot.noscroll.core.contracts.UsageRepository
import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.model.UserSettings
import com.filodot.noscroll.core.testing.InMemoryEmergencyRepository
import com.filodot.noscroll.core.testing.InMemorySettingsRepository
import com.filodot.noscroll.core.testing.InMemoryTaskRepository
import com.filodot.noscroll.core.testing.InMemoryUsageRepository
import com.filodot.noscroll.feature.dashboard.DashboardUiState
import com.filodot.noscroll.feature.history.EmergencyHistoryUiState
import com.filodot.noscroll.feature.settings.DetectorUiStatus
import com.filodot.noscroll.feature.settings.DiagnosticResultCode
import com.filodot.noscroll.feature.settings.RedactedDiagnosticsUiState
import com.filodot.noscroll.feature.settings.SettingsUiState
import com.filodot.noscroll.feature.settings.SystemAccessUiStatus
import com.filodot.noscroll.monitoring.runtime.MonitoringCoordinator
import com.filodot.noscroll.platform.AndroidSystemAccess
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NoScrollAppGraph(
    val settingsRepository: SettingsRepository,
    val usageRepository: UsageRepository,
    val taskRepository: TaskRepository,
    val emergencyRepository: EmergencyRepository,
    val dashboardState: DashboardUiState,
    val settingsState: SettingsUiState,
    val historyState: EmergencyHistoryUiState,
    val settingsReady: StateFlow<Boolean> = MutableStateFlow(true),
    val systemAccess: AndroidSystemAccess? = null,
    val monitoring: MonitoringCoordinator? = null,
) {
    companion object {
        fun fake(onboardingCompleted: Boolean = false): NoScrollAppGraph {
            val now = Instant.parse("2026-07-14T10:00:00Z")
            val date = LocalDate.of(2026, 7, 14)
            return NoScrollAppGraph(
                settingsRepository = InMemorySettingsRepository(
                    UserSettings(onboardingCompleted = onboardingCompleted),
                ),
                usageRepository = InMemoryUsageRepository(
                    initialDailyUsage = DailyUsage(
                        localDate = date,
                        youtubeSeconds = 18 * 60,
                        shortsSeconds = 12 * 60,
                        updatedAt = now,
                    ),
                    initialGateCycle = GateCycle(
                        localDate = date,
                        usedSeconds = 78,
                        updatedAt = now,
                    ),
                ),
                taskRepository = InMemoryTaskRepository(),
                emergencyRepository = InMemoryEmergencyRepository(),
                dashboardState = DashboardUiState(dateLabel = "14 июля"),
                settingsState = SettingsUiState(
                    accessibilityStatus = SystemAccessUiStatus.ENABLED,
                    usageAccessStatus = SystemAccessUiStatus.ENABLED,
                    youtubeVersionLabel = "20.27.33",
                    diagnostics = RedactedDiagnosticsUiState(
                        detectorStatus = DetectorUiStatus.READY,
                        lastRecognitionLabel = "Сегодня, 14:32",
                        lastResultCode = DiagnosticResultCode.SHORTS_CONFIRMED,
                        unknownCount = 0,
                        rulesVersion = 1,
                    ),
                    appVersionLabel = "0.1.0",
                ),
                historyState = EmergencyHistoryUiState(),
            )
        }
    }
}
