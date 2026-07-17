package com.filodot.noscroll.data.local.repository

import com.filodot.noscroll.core.contracts.EmergencyRepository
import com.filodot.noscroll.core.contracts.TaskRepository
import com.filodot.noscroll.core.contracts.TaskPresetRepository
import com.filodot.noscroll.core.contracts.UsageRepository
import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.CustomTaskPreset
import com.filodot.noscroll.core.model.EmergencyEvent
import com.filodot.noscroll.core.model.EmergencyState
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.data.local.room.DailyUsageDao
import com.filodot.noscroll.data.local.room.CustomTaskPresetDao
import com.filodot.noscroll.data.local.room.EmergencyEventDao
import com.filodot.noscroll.data.local.room.GateCycleDao
import com.filodot.noscroll.data.local.room.PendingTaskDao
import com.filodot.noscroll.data.local.room.TaskGrantDao
import com.filodot.noscroll.data.local.room.toEntity
import com.filodot.noscroll.data.local.room.toModel
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class RoomUsageRepository(
    private val dailyUsageDao: DailyUsageDao,
    private val gateCycleDao: GateCycleDao,
    scope: CoroutineScope,
    initialDailyUsage: DailyUsage,
    initialGateCycle: GateCycle,
) : UsageRepository {
    private val mutableDailyInitialized = MutableStateFlow(false)
    private val mutableGateInitialized = MutableStateFlow(false)
    val dailyInitialized: StateFlow<Boolean> = mutableDailyInitialized.asStateFlow()
    val gateInitialized: StateFlow<Boolean> = mutableGateInitialized.asStateFlow()

    override val dailyUsage: StateFlow<DailyUsage> = dailyUsageDao.observeLatest()
        .map { entity -> entity?.toModel() ?: initialDailyUsage }
        .onEach { mutableDailyInitialized.value = true }
        .stateIn(scope, SharingStarted.Eagerly, initialDailyUsage)

    override val gateCycle: StateFlow<GateCycle> = gateCycleDao
        .observe(GateCycle.CURRENT_GATE_CYCLE_ID)
        .map { entity -> entity?.toModel() ?: initialGateCycle }
        .onEach { mutableGateInitialized.value = true }
        .stateIn(scope, SharingStarted.Eagerly, initialGateCycle)

    override suspend fun saveDailyUsage(usage: DailyUsage) {
        dailyUsageDao.upsert(usage.toEntity())
    }

    override suspend fun saveGateCycle(cycle: GateCycle) {
        gateCycleDao.upsert(cycle.toEntity())
    }
}

class RoomTaskRepository(
    private val dao: PendingTaskDao,
    scope: CoroutineScope,
) : TaskRepository {
    private val mutableInitialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = mutableInitialized.asStateFlow()

    override val pendingTask: StateFlow<PendingTask?> = dao.observePending()
        .map { entity -> entity?.toModel() }
        .onEach { mutableInitialized.value = true }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun save(task: PendingTask) {
        dao.upsert(task.toEntity())
    }

    override suspend fun clear(taskId: String) {
        dao.delete(taskId)
    }
}

class RoomEmergencyRepository(
    private val dao: EmergencyEventDao,
    scope: CoroutineScope,
) : EmergencyRepository {
    private val mutableInitialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = mutableInitialized.asStateFlow()

    override val state: StateFlow<EmergencyState> = dao.observeActive()
        .map { entity -> EmergencyState(entity?.toModel()) }
        .onEach { mutableInitialized.value = true }
        .stateIn(scope, SharingStarted.Eagerly, EmergencyState())

    override val history: Flow<List<EmergencyEvent>> = dao.observeHistory()
        .map { entities -> entities.map { it.toModel() } }

    override suspend fun activate(event: EmergencyEvent) {
        dao.upsert(event.toEntity())
    }

    override suspend fun deactivate(event: EmergencyEvent) {
        dao.upsert(event.toEntity())
    }

    override suspend fun deleteHistory() {
        dao.deleteClosedHistory()
    }
}

class RoomTaskGrantTransaction(
    private val dao: TaskGrantDao,
) {
    suspend fun grant(
        taskId: String,
        localDate: LocalDate,
        updatedAt: Instant,
        entryCooldownUntil: Instant,
        cycleId: String = GateCycle.CURRENT_GATE_CYCLE_ID,
    ): Boolean = dao.grant(
        taskId = taskId,
        localDate = localDate.toString(),
        cycleId = cycleId,
        updatedAtEpochMillis = updatedAt.toEpochMilli(),
        entryCooldownUntilEpochMillis = entryCooldownUntil.toEpochMilli(),
    )
}

class RoomTaskPresetRepository(
    private val dao: CustomTaskPresetDao,
    scope: CoroutineScope,
) : TaskPresetRepository {
    private val mutableInitialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = mutableInitialized.asStateFlow()

    override val presets: StateFlow<List<CustomTaskPreset>> = dao.observeAll()
        .map { entities -> entities.map { it.toModel() } }
        .onEach { mutableInitialized.value = true }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun save(preset: CustomTaskPreset) {
        dao.upsert(preset.toEntity())
    }

    override suspend fun delete(presetId: String) {
        dao.delete(presetId)
    }
}
