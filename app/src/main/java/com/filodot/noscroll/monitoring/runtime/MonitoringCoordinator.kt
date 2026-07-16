package com.filodot.noscroll.monitoring.runtime

import android.os.SystemClock
import com.filodot.noscroll.core.contracts.WallClock
import com.filodot.noscroll.core.model.ArithmeticOperation
import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.DeviceState
import com.filodot.noscroll.core.model.EmergencyActivationSource
import com.filodot.noscroll.core.model.EmergencyEvent
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.model.PermissionState
import com.filodot.noscroll.core.model.PolicyDecision
import com.filodot.noscroll.core.model.PolicyInput
import com.filodot.noscroll.core.model.ShortsDetectionState
import com.filodot.noscroll.core.policy.PolicyEngine
import com.filodot.noscroll.core.tasks.ArithmeticTaskState
import com.filodot.noscroll.core.tasks.LocalArithmeticTaskEngine
import com.filodot.noscroll.core.usage.daily.YouTubeForegroundReconstructor
import com.filodot.noscroll.data.local.datastore.DataStoreSettingsRepository
import com.filodot.noscroll.data.local.repository.RoomEmergencyRepository
import com.filodot.noscroll.data.local.repository.RoomTaskGrantTransaction
import com.filodot.noscroll.data.local.repository.RoomTaskRepository
import com.filodot.noscroll.data.local.repository.RoomUsageRepository
import com.filodot.noscroll.feature.overlay.EnforcementUiState
import com.filodot.noscroll.monitoring.accessibility.AccessibilityAdapterController
import com.filodot.noscroll.monitoring.accessibility.NoScrollAccessibilityService
import com.filodot.noscroll.monitoring.detector.YouTubeShortsDetector
import com.filodot.noscroll.monitoring.usagestats.AndroidUsageStatsSource
import com.filodot.noscroll.platform.AndroidSystemAccess
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MonitoringDiagnostics(
    val detectorState: ShortsDetectionState = ShortsDetectionState.UNKNOWN,
    val lastRecognitionAt: Instant? = null,
    val unknownCount: Int = 0,
    val rulesVersion: Int = 1,
    val overlayAvailable: Boolean = true,
)

class MonitoringCoordinator(
    private val scope: CoroutineScope,
    private val settingsRepository: DataStoreSettingsRepository,
    private val usageRepository: RoomUsageRepository,
    private val taskRepository: RoomTaskRepository,
    private val emergencyRepository: RoomEmergencyRepository,
    private val taskGrantTransaction: RoomTaskGrantTransaction,
    private val usageStatsSource: AndroidUsageStatsSource,
    private val systemAccess: AndroidSystemAccess,
) {
    private val zoneId = ZoneId.systemDefault()
    private val wallClock = WallClock(Instant::now)
    private val detector = YouTubeShortsDetector()
    private val policyEngine = PolicyEngine()
    private val reconstructor = YouTubeForegroundReconstructor()
    private val mutex = Mutex()
    private val ready = combine(
        listOf(
            settingsRepository.initialized,
            usageRepository.dailyInitialized,
            usageRepository.gateInitialized,
            taskRepository.initialized,
            emergencyRepository.initialized,
        ),
    ) { values -> values.all { it } }
        .stateIn(scope, SharingStarted.Eagerly, false)
    private val mutableEnforcement = MutableStateFlow<EnforcementUiState?>(null)
    private val mutableDiagnostics = MutableStateFlow(MonitoringDiagnostics())
    private var sessionJob: Job? = null
    private var latestDeviceState = DeviceState(false, false, null)
    private var latestDetectionState = ShortsDetectionState.UNKNOWN
    private var lastTickElapsedMillis: Long? = null
    private var youtubeRemainderMillis = 0L
    private var shortsRemainderMillis = 0L
    private var emergencyOverrideActive = false
    private var ignoreTaskGateUntilElapsedMillis = 0L

    val enforcement: StateFlow<EnforcementUiState?> = mutableEnforcement.asStateFlow()
    val diagnostics: StateFlow<MonitoringDiagnostics> = mutableDiagnostics.asStateFlow()
    val isReady: StateFlow<Boolean> = ready

    fun reportOverlayAvailability(available: Boolean) {
        mutableDiagnostics.value = mutableDiagnostics.value.copy(overlayAvailable = available)
    }

    fun attach(service: NoScrollAccessibilityService) {
        sessionJob?.cancel()
        lastTickElapsedMillis = null
        sessionJob = scope.launch {
            ready.first { it }
            launch {
                service.state.collect { state ->
                    latestDeviceState = state
                    if (state.foregroundPackage != AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME) {
                        latestDetectionState = ShortsDetectionState.NOT_SHORTS
                    }
                    evaluatePolicy()
                }
            }
            launch {
                service.events.collect { event ->
                    val snapshot = service.capture(event) ?: return@collect
                    val result = detector.evaluate(snapshot)
                    latestDetectionState = result.state
                    mutableDiagnostics.value = mutableDiagnostics.value.copy(
                        detectorState = result.state,
                        lastRecognitionAt = Instant.now(),
                        unknownCount = mutableDiagnostics.value.unknownCount +
                            if (result.state == ShortsDetectionState.UNKNOWN) 1 else 0,
                        rulesVersion = result.rulesVersion,
                    )
                    evaluatePolicy()
                }
            }
            launch {
                while (isActive) {
                    recordHeartbeat()
                    delay(HEARTBEAT_MILLIS)
                }
            }
            launch {
                while (isActive) {
                    reconcileDailyUsage()
                    delay(RECONCILIATION_MILLIS)
                }
            }
        }
    }

    fun detach() {
        sessionJob?.cancel()
        sessionJob = null
        mutableEnforcement.value = null
        latestDeviceState = DeviceState(false, false, null)
        latestDetectionState = ShortsDetectionState.UNKNOWN
        lastTickElapsedMillis = null
    }

    suspend fun verifyAnswer(taskId: String, answer: String): Boolean = mutex.withLock {
        val task = taskRepository.pendingTask.value
        if (task == null || task.id != taskId || task.solved) return@withLock false
        val parsed = answer.trim().toIntOrNull()
        if (parsed != task.expectedAnswer) {
            val updated = task.copy(wrongAttempts = task.wrongAttempts.saturatingIncrement())
            taskRepository.save(updated)
            mutableEnforcement.value = updated.toUi(
                settingsRepository.settings.value.shortsIntervalMinutes,
            )
            return@withLock false
        }

        val now = wallClock.now()
        val granted = taskGrantTransaction.grant(
            taskId = task.id,
            localDate = now.atZone(zoneId).toLocalDate(),
            updatedAt = now,
        )
        if (granted) {
            ignoreTaskGateUntilElapsedMillis = SystemClock.elapsedRealtime() +
                TASK_GRANT_PROPAGATION_MILLIS
            mutableEnforcement.value = null
        }
        granted
    }

    suspend fun replaceTask(): EnforcementUiState.TaskGate? = mutex.withLock {
        val current = taskRepository.pendingTask.value ?: return@withLock null
        if (current.wrongAttempts < WRONG_ATTEMPTS_FOR_REPLACEMENT) return@withLock null
        taskRepository.clear(current.id)
        val replacement = newTask()
        taskRepository.save(replacement)
        usageRepository.saveGateCycle(
            usageRepository.gateCycle.value.copy(
                pendingTaskId = replacement.id,
                updatedAt = wallClock.now(),
            ),
        )
        replacement.toUi(settingsRepository.settings.value.shortsIntervalMinutes)
            .also { mutableEnforcement.value = it }
    }

    suspend fun activateEmergency(reason: String, source: EmergencyActivationSource) {
        mutex.withLock {
            emergencyOverrideActive = true
            val event = EmergencyEvent(
                id = UUID.randomUUID().toString(),
                reason = reason.trim(),
                activatedAt = wallClock.now(),
                activationSource = source,
            )
            emergencyRepository.activate(event)
            settingsRepository.save(settingsRepository.settings.value.copy(emergencyActive = true))
            mutableEnforcement.value = null
        }
    }

    suspend fun deactivateEmergency() {
        mutex.withLock {
            emergencyOverrideActive = false
            emergencyRepository.state.value.activeEvent?.let { active ->
                emergencyRepository.deactivate(active.copy(deactivatedAt = wallClock.now()))
            }
            settingsRepository.save(settingsRepository.settings.value.copy(emergencyActive = false))
        }
    }

    suspend fun dismissEnforcement(recordExit: Boolean) {
        mutex.withLock {
            if (recordExit && mutableEnforcement.value is EnforcementUiState.TaskGate) {
                val usage = usageRepository.dailyUsage.value
                usageRepository.saveDailyUsage(
                    usage.copy(
                        taskExits = usage.taskExits.saturatingIncrement(),
                        updatedAt = wallClock.now(),
                    ),
                )
            }
            mutableEnforcement.value = null
        }
    }

    private suspend fun recordHeartbeat() = mutex.withLock {
        val elapsed = SystemClock.elapsedRealtime()
        val previous = lastTickElapsedMillis
        lastTickElapsedMillis = elapsed
        val deltaMillis = if (previous != null && elapsed > previous) elapsed - previous else 0L
        val now = wallClock.now()
        val today = now.atZone(zoneId).toLocalDate()
        var usage = usageRepository.dailyUsage.value.forDate(today, now)
        var cycle = usageRepository.gateCycle.value.forDate(today, now)
        val youtubeActive = mutableEnforcement.value == null &&
            latestDeviceState.screenInteractive &&
            latestDeviceState.deviceUnlocked &&
            latestDeviceState.foregroundPackage == AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME
        val shortsActive = youtubeActive && latestDetectionState == ShortsDetectionState.SHORTS_CONFIRMED

        if (youtubeActive && deltaMillis > 0) {
            val total = youtubeRemainderMillis.saturatingAdd(deltaMillis)
            val seconds = total / MILLIS_PER_SECOND
            youtubeRemainderMillis = total % MILLIS_PER_SECOND
            if (seconds > 0) {
                val emergencyActive = emergencyOverrideActive ||
                    settingsRepository.settings.value.emergencyActive ||
                    emergencyRepository.state.value.isActive
                usage = usage.copy(
                    youtubeSeconds = usage.youtubeSeconds.saturatingAdd(seconds),
                    emergencyYoutubeSeconds = usage.emergencyYoutubeSeconds.saturatingAdd(
                        if (emergencyActive) seconds else 0,
                    ),
                    lastUpdatedElapsedMillis = elapsed,
                    updatedAt = now,
                )
                if (emergencyActive) {
                    emergencyRepository.state.value.activeEvent?.let { active ->
                        emergencyRepository.activate(
                            active.copy(
                                youtubeSecondsDuring = active.youtubeSecondsDuring
                                    .saturatingAdd(seconds),
                            ),
                        )
                    }
                }
            }
        } else {
            youtubeRemainderMillis = 0
        }

        if (shortsActive && deltaMillis > 0) {
            val total = shortsRemainderMillis.saturatingAdd(deltaMillis)
            val seconds = total / MILLIS_PER_SECOND
            shortsRemainderMillis = total % MILLIS_PER_SECOND
            if (seconds > 0) {
                usage = usage.copy(
                    shortsSeconds = usage.shortsSeconds.saturatingAdd(seconds),
                    updatedAt = now,
                )
                cycle = cycle.copy(
                    usedSeconds = cycle.usedSeconds.saturatingAdd(seconds),
                    updatedAt = now,
                )
            }
        } else {
            shortsRemainderMillis = 0
        }

        if (usage != usageRepository.dailyUsage.value) usageRepository.saveDailyUsage(usage)
        if (cycle != usageRepository.gateCycle.value) usageRepository.saveGateCycle(cycle)
        evaluatePolicyLocked(usage, cycle)
    }

    private suspend fun evaluatePolicy() = mutex.withLock {
        evaluatePolicyLocked(
            usage = usageRepository.dailyUsage.value,
            cycle = usageRepository.gateCycle.value,
        )
    }

    private suspend fun evaluatePolicyLocked(usage: DailyUsage, cycle: GateCycle) {
        if (mutableEnforcement.value != null) return
        val access = systemAccess.state.value
        val decision = policyEngine.decide(
            PolicyInput(
                settings = settingsRepository.settings.value.copy(
                    emergencyActive = emergencyOverrideActive ||
                        settingsRepository.settings.value.emergencyActive,
                ),
                permissions = PermissionState(
                    accessibilityGranted = access.accessibilityGranted,
                    usageAccessGranted = access.usageAccessGranted,
                ),
                dailyUsage = usage,
                gateCycle = cycle,
                pendingTask = taskRepository.pendingTask.value,
                emergencyState = emergencyRepository.state.value,
                detectorState = latestDetectionState,
                youtubeForeground = latestDeviceState.foregroundPackage ==
                    AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME,
            ),
        )
        when (decision) {
            is PolicyDecision.DailyLimitReached -> showEnforcement(
                EnforcementUiState.DailyLimit(
                    usedMinutes = (decision.usedSeconds / SECONDS_PER_MINUTE).toInt(),
                    limitMinutes = (decision.limitSeconds / SECONDS_PER_MINUTE).toInt(),
                ),
            )

            is PolicyDecision.TaskGateRequired -> if (
                SystemClock.elapsedRealtime() >= ignoreTaskGateUntilElapsedMillis
            ) {
                showEnforcement(ensureTask(cycle, usage))
            }
            PolicyDecision.Allow,
            PolicyDecision.EmergencyBypass,
            is PolicyDecision.RequirementsMissing,
            -> Unit
        }
    }

    private suspend fun ensureTask(
        cycle: GateCycle,
        usage: DailyUsage,
    ): EnforcementUiState.TaskGate {
        val existing = taskRepository.pendingTask.value
        val task = existing ?: newTask().also { created ->
            taskRepository.save(created)
            usageRepository.saveGateCycle(
                cycle.copy(pendingTaskId = created.id, updatedAt = wallClock.now()),
            )
            usageRepository.saveDailyUsage(
                usage.copy(
                    gatesShown = usage.gatesShown.saturatingIncrement(),
                    updatedAt = wallClock.now(),
                ),
            )
        }
        if (cycle.pendingTaskId != task.id) {
            usageRepository.saveGateCycle(
                cycle.copy(pendingTaskId = task.id, updatedAt = wallClock.now()),
            )
        }
        return task.toUi(settingsRepository.settings.value.shortsIntervalMinutes)
    }

    private fun newTask(): PendingTask = LocalArithmeticTaskEngine(
        wallClock = wallClock,
        initialState = ArithmeticTaskState(),
    ).requireTask()

    private fun showEnforcement(state: EnforcementUiState) {
        mutableEnforcement.value = state
    }

    private suspend fun reconcileDailyUsage() {
        if (!systemAccess.refresh().usageAccessGranted) return
        val now = wallClock.now()
        val dayStart = now.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant()
        val events = runCatching { usageStatsSource.eventsBetween(dayStart, now) }.getOrNull() ?: return
        val reconstructed = reconstructor.reconstruct(events, dayStart, now).totalSeconds
        mutex.withLock {
            val usage = usageRepository.dailyUsage.value
            if (usage.localDate == now.atZone(zoneId).toLocalDate() &&
                reconstructed > usage.youtubeSeconds
            ) {
                usageRepository.saveDailyUsage(
                    usage.copy(youtubeSeconds = reconstructed, updatedAt = now),
                )
                evaluatePolicyLocked(
                    usage = usage.copy(youtubeSeconds = reconstructed, updatedAt = now),
                    cycle = usageRepository.gateCycle.value,
                )
            }
        }
    }

    private fun DailyUsage.forDate(date: LocalDate, now: Instant): DailyUsage =
        if (localDate == date) this else DailyUsage(localDate = date, updatedAt = now)

    private fun GateCycle.forDate(date: LocalDate, now: Instant): GateCycle =
        if (localDate == date) this else GateCycle(localDate = date, updatedAt = now)

    private fun PendingTask.toUi(grantMinutes: Int): EnforcementUiState.TaskGate {
        val symbol = when (operation) {
            ArithmeticOperation.ADD -> "+"
            ArithmeticOperation.SUBTRACT -> "−"
            ArithmeticOperation.MULTIPLY -> "×"
        }
        val spokenOperation = when (operation) {
            ArithmeticOperation.ADD -> "плюс"
            ArithmeticOperation.SUBTRACT -> "минус"
            ArithmeticOperation.MULTIPLY -> "умножить на"
        }
        return EnforcementUiState.TaskGate(
            taskId = id,
            visualExpression = "$leftOperand $symbol $rightOperand",
            spokenExpression = "$leftOperand $spokenOperation $rightOperand",
            grantMinutes = grantMinutes,
            wrongAttempts = wrongAttempts,
        )
    }

    private fun Long.saturatingAdd(other: Long): Long =
        if (other > Long.MAX_VALUE - this) Long.MAX_VALUE else this + other

    private fun Int.saturatingIncrement(): Int = if (this == Int.MAX_VALUE) this else this + 1

    companion object {
        private const val HEARTBEAT_MILLIS = 1_000L
        private const val RECONCILIATION_MILLIS = 60_000L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val SECONDS_PER_MINUTE = 60L
        private const val WRONG_ATTEMPTS_FOR_REPLACEMENT = 3
        private const val TASK_GRANT_PROPAGATION_MILLIS = 2_000L
    }
}
