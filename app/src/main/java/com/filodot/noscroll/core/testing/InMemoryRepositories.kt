package com.filodot.noscroll.core.testing

import com.filodot.noscroll.core.contracts.EmergencyRepository
import com.filodot.noscroll.core.contracts.SettingsRepository
import com.filodot.noscroll.core.contracts.TaskRepository
import com.filodot.noscroll.core.contracts.UsageRepository
import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.EmergencyEvent
import com.filodot.noscroll.core.model.EmergencyState
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InMemorySettingsRepository(
    initialSettings: UserSettings = UserSettings(),
) : SettingsRepository {
    private val mutableSettings = MutableStateFlow(initialSettings)

    override val settings: StateFlow<UserSettings> = mutableSettings

    override suspend fun save(settings: UserSettings) {
        mutableSettings.value = settings
    }
}

class InMemoryUsageRepository(
    initialDailyUsage: DailyUsage,
    initialGateCycle: GateCycle,
) : UsageRepository {
    private val mutableDailyUsage = MutableStateFlow(initialDailyUsage)
    private val mutableGateCycle = MutableStateFlow(initialGateCycle)

    override val dailyUsage: StateFlow<DailyUsage> = mutableDailyUsage
    override val gateCycle: StateFlow<GateCycle> = mutableGateCycle

    override suspend fun saveDailyUsage(usage: DailyUsage) {
        mutableDailyUsage.value = usage
    }

    override suspend fun saveGateCycle(cycle: GateCycle) {
        mutableGateCycle.value = cycle
    }
}

class InMemoryTaskRepository(
    initialTask: PendingTask? = null,
) : TaskRepository {
    private val mutablePendingTask = MutableStateFlow(initialTask)

    override val pendingTask: StateFlow<PendingTask?> = mutablePendingTask

    override suspend fun save(task: PendingTask) {
        mutablePendingTask.value = task
    }

    override suspend fun clear(taskId: String) {
        if (mutablePendingTask.value?.id == taskId) {
            mutablePendingTask.value = null
        }
    }
}

class InMemoryEmergencyRepository(
    initialHistory: List<EmergencyEvent> = emptyList(),
) : EmergencyRepository {
    private val mutableHistory = MutableStateFlow(initialHistory)
    private val mutableState = MutableStateFlow(
        EmergencyState(initialHistory.firstOrNull { it.deactivatedAt == null }),
    )

    override val state: StateFlow<EmergencyState> = mutableState
    override val history: Flow<List<EmergencyEvent>> = mutableHistory

    override suspend fun activate(event: EmergencyEvent) {
        mutableHistory.value = mutableHistory.value.filterNot { it.id == event.id } + event
        mutableState.value = EmergencyState(event)
    }

    override suspend fun deactivate(event: EmergencyEvent) {
        mutableHistory.value = mutableHistory.value.map { stored ->
            if (stored.id == event.id) event else stored
        }
        mutableState.value = EmergencyState()
    }

    override suspend fun deleteHistory() {
        val activeEvent = mutableState.value.activeEvent
        mutableHistory.value = listOfNotNull(activeEvent)
    }
}
