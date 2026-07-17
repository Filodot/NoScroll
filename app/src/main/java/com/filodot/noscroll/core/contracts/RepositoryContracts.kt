package com.filodot.noscroll.core.contracts

import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.EmergencyEvent
import com.filodot.noscroll.core.model.EmergencyState
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.model.CustomTaskPreset
import com.filodot.noscroll.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: StateFlow<UserSettings>

    suspend fun save(settings: UserSettings)
}

interface UsageRepository {
    val dailyUsage: StateFlow<DailyUsage>
    val gateCycle: StateFlow<GateCycle>

    suspend fun saveDailyUsage(usage: DailyUsage)

    suspend fun saveGateCycle(cycle: GateCycle)
}

interface TaskRepository {
    val pendingTask: StateFlow<PendingTask?>

    suspend fun save(task: PendingTask)

    suspend fun clear(taskId: String)
}

interface TaskPresetRepository {
    val presets: StateFlow<List<CustomTaskPreset>>

    suspend fun save(preset: CustomTaskPreset)

    suspend fun delete(presetId: String)
}

interface EmergencyRepository {
    val state: StateFlow<EmergencyState>
    val history: Flow<List<EmergencyEvent>>

    suspend fun activate(event: EmergencyEvent)

    suspend fun deactivate(event: EmergencyEvent)

    suspend fun deleteHistory()
}
