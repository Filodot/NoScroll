package com.filodot.noscroll.data.local.repository

import android.util.Log
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

class RoomUsageRepository(
    private val dailyUsageDao: DailyUsageDao,
    private val gateCycleDao: GateCycleDao,
    scope: CoroutineScope,
    initialDailyUsage: DailyUsage,
    initialGateCycle: GateCycle,
) : UsageRepository {
    private val mutableDailyInitialized = MutableStateFlow(false)
    private val mutableGateInitialized = MutableStateFlow(false)
    private val mutableDailyUsage = MutableStateFlow(initialDailyUsage)
    private val mutableUsageHistory = MutableStateFlow<List<DailyUsage>>(emptyList())
    private val mutableGateCycle = MutableStateFlow(initialGateCycle)
    private var dailySnapshotObserved = false
    private var gateSnapshotObserved = false
    val dailyInitialized: StateFlow<Boolean> = mutableDailyInitialized.asStateFlow()
    val gateInitialized: StateFlow<Boolean> = mutableGateInitialized.asStateFlow()

    override val dailyUsage: StateFlow<DailyUsage> = mutableDailyUsage.asStateFlow()
    override val usageHistory: StateFlow<List<DailyUsage>> = mutableUsageHistory.asStateFlow()
    override val gateCycle: StateFlow<GateCycle> = mutableGateCycle.asStateFlow()

    init {
        scope.launch {
            dailyUsageDao.observeLatest()
                .retryWhen { error, attempt ->
                    Log.w(LOG_TAG, "Could not observe daily_usage; retrying", error)
                    mutableDailyInitialized.value = true
                    delay(retryDelayMillis(attempt))
                    true
                }
                .collect { entity ->
                    entity?.let { stored ->
                        decodeOrDelete(
                            kind = "daily_usage",
                            entityId = stored.localDate,
                            delete = { dailyUsageDao.delete(stored.localDate) },
                            decode = stored::toModel,
                        )
                    }?.let { persisted ->
                        val current = mutableDailyUsage.value
                        if (!dailySnapshotObserved || persisted.isAtLeastAsFreshAs(current)) {
                            mutableDailyUsage.value = persisted
                        }
                    }
                    dailySnapshotObserved = true
                    mutableDailyInitialized.value = true
                }
        }
        scope.launch {
            dailyUsageDao.observeRecent(USAGE_HISTORY_DAYS)
                .retryWhen { error, attempt ->
                    Log.w(LOG_TAG, "Could not observe usage history; retrying", error)
                    delay(retryDelayMillis(attempt))
                    true
                }
                .collect { entities ->
                    mutableUsageHistory.value = entities.mapNotNull { stored ->
                        decodeOrDelete(
                            kind = "daily_usage_history",
                            entityId = stored.localDate,
                            delete = { dailyUsageDao.delete(stored.localDate) },
                            decode = stored::toModel,
                        )
                    }.sortedByDescending(DailyUsage::localDate)
                }
        }
        scope.launch {
            gateCycleDao.observe(GateCycle.CURRENT_GATE_CYCLE_ID)
                .retryWhen { error, attempt ->
                    Log.w(LOG_TAG, "Could not observe gate_cycle; retrying", error)
                    mutableGateInitialized.value = true
                    delay(retryDelayMillis(attempt))
                    true
                }
                .collect { entity ->
                    entity?.let { stored ->
                        decodeOrDelete(
                            kind = "gate_cycle",
                            entityId = stored.id,
                            delete = { gateCycleDao.delete(stored.id) },
                            decode = stored::toModel,
                        )
                    }?.let { persisted ->
                        val current = mutableGateCycle.value
                        if (!gateSnapshotObserved || persisted.updatedAt >= current.updatedAt) {
                            mutableGateCycle.value = persisted
                        }
                    }
                    gateSnapshotObserved = true
                    mutableGateInitialized.value = true
                }
        }
    }

    override suspend fun saveDailyUsage(usage: DailyUsage) {
        dailyUsageDao.upsert(usage.toEntity())
        mutableDailyUsage.value = usage
        mutableUsageHistory.value = (mutableUsageHistory.value
            .filterNot { it.localDate == usage.localDate } + usage)
            .sortedByDescending(DailyUsage::localDate)
            .take(USAGE_HISTORY_DAYS)
    }

    override suspend fun saveGateCycle(cycle: GateCycle) {
        gateCycleDao.upsert(cycle.toEntity())
        mutableGateCycle.value = cycle
    }
}

class RoomTaskRepository(
    private val dao: PendingTaskDao,
    scope: CoroutineScope,
) : TaskRepository {
    private val mutableInitialized = MutableStateFlow(false)
    private val mutablePendingTask = MutableStateFlow<PendingTask?>(null)
    val initialized: StateFlow<Boolean> = mutableInitialized.asStateFlow()

    override val pendingTask: StateFlow<PendingTask?> = mutablePendingTask.asStateFlow()

    init {
        scope.launch {
            dao.observePending()
                .retryWhen { error, attempt ->
                    Log.w(LOG_TAG, "Could not observe pending_task; retrying", error)
                    mutableInitialized.value = true
                    delay(retryDelayMillis(attempt))
                    true
                }
                .collect { entity ->
                    mutablePendingTask.value = entity?.let { stored ->
                        decodeOrDelete(
                            kind = "pending_task",
                            entityId = stored.id,
                            delete = { dao.delete(stored.id) },
                            decode = stored::toModel,
                        )
                    }
                    mutableInitialized.value = true
                }
        }
    }

    override suspend fun save(task: PendingTask) {
        dao.upsert(task.toEntity())
        mutablePendingTask.value = task
    }

    override suspend fun clear(taskId: String) {
        dao.delete(taskId)
        if (mutablePendingTask.value?.id == taskId) mutablePendingTask.value = null
    }
}

class RoomEmergencyRepository(
    private val dao: EmergencyEventDao,
    scope: CoroutineScope,
) : EmergencyRepository {
    private val mutableInitialized = MutableStateFlow(false)
    private val mutableState = MutableStateFlow(EmergencyState())
    val initialized: StateFlow<Boolean> = mutableInitialized.asStateFlow()

    override val state: StateFlow<EmergencyState> = mutableState.asStateFlow()

    init {
        scope.launch {
            dao.observeActive()
                .retryWhen { error, attempt ->
                    Log.w(LOG_TAG, "Could not observe emergency_event; retrying", error)
                    mutableInitialized.value = true
                    delay(retryDelayMillis(attempt))
                    true
                }
                .collect { entity ->
                    mutableState.value = EmergencyState(
                        entity?.let { stored ->
                            decodeOrDelete(
                                kind = "emergency_event",
                                entityId = stored.id,
                                delete = { dao.delete(stored.id) },
                                decode = stored::toModel,
                            )
                        },
                    )
                    mutableInitialized.value = true
                }
        }
    }

    override val history: Flow<List<EmergencyEvent>> = dao.observeHistory()
        .map { entities ->
            entities.mapNotNull { entity ->
                runCatching(entity::toModel).getOrElse { error ->
                    Log.w(LOG_TAG, "Ignored invalid emergency_event ${entity.id}", error)
                    null
                }
            }
        }
        .retryWhen { _, attempt ->
            delay(retryDelayMillis(attempt))
            true
        }

    override suspend fun activate(event: EmergencyEvent) {
        dao.upsert(event.toEntity())
        mutableState.value = EmergencyState(event)
    }

    override suspend fun deactivate(event: EmergencyEvent) {
        dao.upsert(event.toEntity())
        if (mutableState.value.activeEvent?.id == event.id) {
            mutableState.value = EmergencyState()
        }
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

private const val USAGE_HISTORY_DAYS = 15

class RoomTaskPresetRepository(
    private val dao: CustomTaskPresetDao,
    scope: CoroutineScope,
) : TaskPresetRepository {
    private val mutableInitialized = MutableStateFlow(false)
    private val mutablePresets = MutableStateFlow<List<CustomTaskPreset>>(emptyList())
    val initialized: StateFlow<Boolean> = mutableInitialized.asStateFlow()

    override val presets: StateFlow<List<CustomTaskPreset>> = mutablePresets.asStateFlow()

    init {
        scope.launch {
            dao.observeAll()
                .retryWhen { error, attempt ->
                    Log.w(LOG_TAG, "Could not observe custom_task_preset; retrying", error)
                    mutableInitialized.value = true
                    delay(retryDelayMillis(attempt))
                    true
                }
                .collect { entities ->
                    mutablePresets.value = buildList {
                        entities.forEach { entity ->
                            decodeOrDelete(
                                kind = "custom_task_preset",
                                entityId = entity.id,
                                delete = { dao.delete(entity.id) },
                                decode = entity::toModel,
                            )?.let(::add)
                        }
                    }
                    mutableInitialized.value = true
                }
        }
    }

    override suspend fun save(preset: CustomTaskPreset) {
        dao.upsert(preset.toEntity())
        mutablePresets.value = (mutablePresets.value.filterNot { it.id == preset.id } + preset)
            .sortedWith(compareBy(CustomTaskPreset::createdAt, CustomTaskPreset::id))
    }

    override suspend fun delete(presetId: String) {
        dao.delete(presetId)
        mutablePresets.value = mutablePresets.value.filterNot { it.id == presetId }
    }
}

private fun DailyUsage.isAtLeastAsFreshAs(other: DailyUsage): Boolean =
    localDate > other.localDate || (localDate == other.localDate && updatedAt >= other.updatedAt)

private fun retryDelayMillis(attempt: Long): Long =
    (500L shl attempt.coerceAtMost(5).toInt()).coerceAtMost(15_000L)

private suspend fun <Model> decodeOrDelete(
    kind: String,
    entityId: String,
    delete: suspend () -> Unit,
    decode: () -> Model,
): Model? = try {
    decode()
} catch (error: Exception) {
    Log.w(LOG_TAG, "Removed invalid $kind $entityId", error)
    try {
        delete()
    } catch (deleteError: Exception) {
        Log.w(LOG_TAG, "Could not remove invalid $kind $entityId", deleteError)
    }
    null
}

private const val LOG_TAG = "NoScrollStorage"
