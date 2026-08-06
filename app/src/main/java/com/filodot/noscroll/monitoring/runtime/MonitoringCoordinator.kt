package com.filodot.noscroll.monitoring.runtime

import android.os.SystemClock
import android.util.Log
import com.filodot.noscroll.core.contracts.WallClock
import com.filodot.noscroll.core.contracts.LearningRepository
import com.filodot.noscroll.core.focus.FocusAppCatalog
import com.filodot.noscroll.core.focus.FocusSession
import com.filodot.noscroll.core.learning.gate.LearningGateTaskFactory
import com.filodot.noscroll.core.learning.model.AttemptResult
import com.filodot.noscroll.core.model.ArithmeticOperation
import com.filodot.noscroll.core.model.AccessibilityWindowEvent
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
import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskCompletionMode
import com.filodot.noscroll.core.model.TaskTarget
import com.filodot.noscroll.core.model.TaskType
import com.filodot.noscroll.core.model.TaskTrigger
import com.filodot.noscroll.core.model.UserSettings
import com.filodot.noscroll.core.policy.PolicyEngine
import com.filodot.noscroll.core.policy.AppGateDecision
import com.filodot.noscroll.core.policy.AppGateInput
import com.filodot.noscroll.core.policy.AppGatePolicy
import com.filodot.noscroll.core.tasks.LocalTaskFactory
import com.filodot.noscroll.core.tasks.TaskDifficultyConfig
import com.filodot.noscroll.core.tasks.TaskDifficultyPolicy
import com.filodot.noscroll.core.tasks.TaskDifficultyState
import com.filodot.noscroll.core.usage.daily.YouTubeForegroundReconstructor
import com.filodot.noscroll.core.usage.shorts.DeferredIntervalAction
import com.filodot.noscroll.core.usage.shorts.DeferredIntervalGate
import com.filodot.noscroll.core.usage.shorts.ShortsEntryGate
import com.filodot.noscroll.data.local.datastore.DataStoreSettingsRepository
import com.filodot.noscroll.data.local.repository.RoomEmergencyRepository
import com.filodot.noscroll.data.local.repository.RoomTaskGrantTransaction
import com.filodot.noscroll.data.local.repository.RoomTaskRepository
import com.filodot.noscroll.data.local.repository.RoomTaskPresetRepository
import com.filodot.noscroll.data.local.repository.RoomUsageRepository
import com.filodot.noscroll.feature.overlay.EnforcementUiState
import com.filodot.noscroll.feature.overlay.TaskAnswerStatus
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

enum class MonitoringHealthStatus {
    DISCONNECTED,
    STARTING,
    RUNNING,
    RECOVERING,
}

data class MonitoringDiagnostics(
    val detectorState: ShortsDetectionState = ShortsDetectionState.UNKNOWN,
    val lastRecognitionAt: Instant? = null,
    val lastTargetEventAt: Instant? = null,
    val lastInstagramEventAt: Instant? = null,
    val lastHeartbeatAt: Instant? = null,
    val unknownCount: Int = 0,
    val rulesVersion: Int = 1,
    val healthStatus: MonitoringHealthStatus = MonitoringHealthStatus.DISCONNECTED,
    val serviceConnected: Boolean = false,
    val recoveryCount: Int = 0,
    val lastFailureCode: String? = null,
)

class MonitoringCoordinator(
    private val scope: CoroutineScope,
    private val settingsRepository: DataStoreSettingsRepository,
    private val usageRepository: RoomUsageRepository,
    private val taskRepository: RoomTaskRepository,
    private val taskPresetRepository: RoomTaskPresetRepository,
    private val learningRepository: LearningRepository,
    private val emergencyRepository: RoomEmergencyRepository,
    private val taskGrantTransaction: RoomTaskGrantTransaction,
    private val usageStatsSource: AndroidUsageStatsSource,
    private val systemAccess: AndroidSystemAccess,
) {
    private val zoneId = ZoneId.systemDefault()
    private val wallClock = WallClock(Instant::now)
    private val detector = YouTubeShortsDetector()
    private val policyEngine = PolicyEngine()
    private val appGatePolicy = AppGatePolicy()
    private val taskDifficultyPolicy = TaskDifficultyPolicy()
    private val taskFactory = LocalTaskFactory(wallClock)
    private val learningTaskFactory = LearningGateTaskFactory(learningRepository, wallClock)
    private val reconstructor = YouTubeForegroundReconstructor()
    private val entryGate = ShortsEntryGate()
    private val deferredIntervalGate = DeferredIntervalGate()
    private val mutex = Mutex()
    private val ready = combine(
        listOf(
            settingsRepository.initialized,
            usageRepository.dailyInitialized,
            usageRepository.gateInitialized,
            taskRepository.initialized,
            taskPresetRepository.initialized,
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
    private var instagramRemainderMillis = 0L
    private var pinterestRemainderMillis = 0L
    private var chromeRemainderMillis = 0L
    private val volatileGrantedUntil = mutableMapOf<TaskTarget, Instant>()
    private var emergencyOverrideActive = false
    private var activeService: NoScrollAccessibilityService? = null
    private var shortsEjectionBlockedUntilElapsedMillis = 0L
    private var focusEjectionBlockedUntilElapsedMillis = 0L
    @Volatile
    private var lastHealthyHeartbeatElapsedMillis: Long? = null
    private val heartbeatWake = Channel<Unit>(Channel.CONFLATED)
    private val failureLock = Any()
    private var activeFailureCodes: Set<String> = emptySet()

    val enforcement: StateFlow<EnforcementUiState?> = mutableEnforcement.asStateFlow()
    val diagnostics: StateFlow<MonitoringDiagnostics> = mutableDiagnostics.asStateFlow()
    val isReady: StateFlow<Boolean> = ready

    fun attach(service: NoScrollAccessibilityService) {
        sessionJob?.cancel()
        activeService = service
        lastTickElapsedMillis = null
        lastHealthyHeartbeatElapsedMillis = SystemClock.elapsedRealtime()
        while (heartbeatWake.tryReceive().isSuccess) Unit
        synchronized(failureLock) { activeFailureCodes = emptySet() }
        mutableDiagnostics.update {
            it.copy(
                healthStatus = MonitoringHealthStatus.STARTING,
                serviceConnected = true,
                lastFailureCode = null,
            )
        }
        sessionJob = scope.launch {
            ready.first { it }
            supervisorScope {
                launch {
                    runRecoveringStream(FAILURE_DEVICE_STATE_STREAM) {
                        service.state.collect { state ->
                            handleDeviceState(state)
                            heartbeatWake.trySend(Unit)
                        }
                    }
                }
                launch {
                    runRecoveringStream(FAILURE_EVENT_STREAM) {
                        service.events.collect { event ->
                            val eventAt = wallClock.now()
                            mutableDiagnostics.update { diagnostics ->
                                diagnostics.copy(
                                    lastTargetEventAt = eventAt,
                                    lastInstagramEventAt = if (
                                        event.packageName ==
                                        AccessibilityAdapterController.INSTAGRAM_PACKAGE_NAME
                                    ) {
                                        eventAt
                                    } else {
                                        diagnostics.lastInstagramEventAt
                                    },
                                )
                            }
                            if (event.packageName != AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME) {
                                latestDetectionState = ShortsDetectionState.NOT_SHORTS
                                evaluatePolicy(triggeringEvent = event)
                                return@collect
                            }
                            val snapshot = service.capture(event) ?: return@collect
                            val result = detector.evaluate(snapshot)
                            latestDetectionState = result.state
                            entryGate.onDetection(result.state, SystemClock.elapsedRealtime())
                            mutableDiagnostics.update { diagnostics ->
                                diagnostics.copy(
                                    detectorState = result.state,
                                    lastRecognitionAt = eventAt,
                                    unknownCount = diagnostics.unknownCount +
                                        if (result.state == ShortsDetectionState.UNKNOWN) 1 else 0,
                                    rulesVersion = result.rulesVersion,
                                )
                            }
                            evaluatePolicy(triggeringEvent = event)
                        }
                    }
                }
                launch {
                    while (isActive) {
                        runGuarded(FAILURE_HEARTBEAT) {
                            recordHeartbeat()
                            markHeartbeatHealthy()
                        }
                        awaitNextHeartbeat()
                    }
                }
                launch {
                    while (isActive) {
                        runGuarded(FAILURE_RECONCILIATION) { reconcileDailyUsage() }
                        delay(RECONCILIATION_MILLIS)
                    }
                }
                launch {
                    while (isActive) {
                        delay(HEALTH_WATCHDOG_INTERVAL_MILLIS)
                        val elapsed = SystemClock.elapsedRealtime()
                        val lastHealthy = lastHealthyHeartbeatElapsedMillis
                        if (lastHealthy == null ||
                            elapsed >= lastHealthy &&
                            elapsed - lastHealthy > HEARTBEAT_STALE_AFTER_MILLIS
                        ) {
                            recordRuntimeFailure(FAILURE_HEARTBEAT_STALE)
                        } else {
                            recordRuntimeRecovery(FAILURE_HEARTBEAT_STALE)
                        }
                    }
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
        lastHealthyHeartbeatElapsedMillis = null
        entryGate.reset()
        deferredIntervalGate.reset()
        activeService = null
        synchronized(failureLock) { activeFailureCodes = emptySet() }
        mutableDiagnostics.update {
            it.copy(
                healthStatus = MonitoringHealthStatus.DISCONNECTED,
                serviceConnected = false,
            )
        }
    }

    fun onTransientServiceInterrupt() {
        latestDeviceState = DeviceState(false, false, null)
        latestDetectionState = ShortsDetectionState.UNKNOWN
        lastTickElapsedMillis = null
        entryGate.onYouTubeForegroundLost()
        deferredIntervalGate.reset()
        heartbeatWake.trySend(Unit)
        recordRuntimeFailure(FAILURE_TRANSIENT_INTERRUPT)
    }

    /** Wakes a healthy session and recreates it if Android left the bound service without a job. */
    fun requestHealthCheck() {
        systemAccess.refresh()
        val service = activeService
        if (service != null && sessionJob?.isActive != true) {
            attach(service)
        } else {
            heartbeatWake.trySend(Unit)
        }
    }

    suspend fun verifyAnswer(taskId: String, answer: String): Boolean = mutex.withLock {
        val task = taskRepository.pendingTask.value
        if (task == null || task.id != taskId || task.solved) return@withLock false
        val correct = when (task.completionMode) {
            TaskCompletionMode.CHECKED_ANSWER -> answer.trim().toIntOrNull() == task.expectedAnswer
            TaskCompletionMode.SINGLE_CHOICE -> answer == task.expectedChoiceId
            TaskCompletionMode.MANUAL_CONFIRMATION -> true
        }
        if (!correct) {
            recordLearningResult(task, AttemptResult.INCORRECT)
            val updated = task.copy(wrongAttempts = task.wrongAttempts.saturatingIncrement())
            taskRepository.save(updated)
            mutableEnforcement.value = updated.toUi(grantMinutesFor(updated.target))
            return@withLock false
        }

        val now = wallClock.now()
        val intervalMinutes = grantMinutesFor(task.target).coerceAtLeast(1)
        val entryCooldownSeconds = intervalMinutes.toLong() * SECONDS_PER_MINUTE
        val entryCooldownUntil = now.plusSeconds(entryCooldownSeconds)
        val usageBeforeGrant = usageRepository.dailyUsage.value
        val cycleBeforeGrant = usageRepository.gateCycle.value
        val granted = taskGrantTransaction.grant(
            taskId = task.id,
            localDate = now.atZone(zoneId).toLocalDate(),
            updatedAt = now,
            entryCooldownUntil = entryCooldownUntil,
        )
        if (granted) {
            recordLearningResult(task, AttemptResult.CORRECT)
            val solvedUsage = usageRepository.dailyUsage.value.let { current ->
                current.copy(
                    tasksSolved = maxOf(
                        current.tasksSolved,
                        usageBeforeGrant.tasksSolved.saturatingIncrement(),
                    ),
                    updatedAt = now,
                )
            }
            usageRepository.saveDailyUsage(solvedUsage)
            val currentCycle = usageRepository.gateCycle.value
            val today = now.atZone(zoneId).toLocalDate()
            val synchronizedCycle = (
                if (currentCycle.localDate == today) currentCycle else cycleBeforeGrant.forDate(today, now)
                ).grantTarget(task.target, entryCooldownUntil, now)
            usageRepository.saveGateCycle(synchronizedCycle)
            taskRepository.clear(task.id)
            if (task.target == TaskTarget.YOUTUBE_SHORTS) {
                entryGate.onTaskSolved(
                    elapsedMillis = SystemClock.elapsedRealtime(),
                    validityMillis = entryCooldownSeconds * MILLIS_PER_SECOND,
                )
            } else {
                volatileGrantedUntil[task.target] = entryCooldownUntil
            }
            deferredIntervalGate.reset()
            latestDetectionState = ShortsDetectionState.NOT_SHORTS
        }
        granted
    }

    suspend fun replaceTask(): EnforcementUiState.TaskGate? = mutex.withLock {
        val current = taskRepository.pendingTask.value ?: return@withLock null
        if (
            current.type != TaskType.PUSH_UPS &&
            current.wrongAttempts < WRONG_ATTEMPTS_FOR_REPLACEMENT
        ) {
            return@withLock null
        }
        recordLearningResult(current, AttemptResult.REPLACED_AS_SUSPICIOUS)
        taskRepository.clear(current.id)
        val replacement = newTask(
            current.difficulty,
            current.trigger,
            current.target,
            sequenceOffset = 1,
        )
        taskRepository.save(replacement)
        usageRepository.saveGateCycle(
            usageRepository.gateCycle.value.copy(
                pendingTaskId = replacement.id,
                updatedAt = wallClock.now(),
            ),
        )
        replacement.toUi(grantMinutesFor(replacement.target))
            .also { mutableEnforcement.value = it }
    }

    /** Creates or restores the challenge shown inside NoScroll, never in a system overlay. */
    suspend fun prepareChallengeForApp(target: TaskTarget = TaskTarget.YOUTUBE_SHORTS) {
        ready.first { it }
        mutex.withLock {
            val settings = settingsRepository.settings.value
            val targetEnabled = settings.gateEnabled(target)
            if (
                !settings.onboardingCompleted ||
                !targetEnabled ||
                !systemAccess.state.value.accessibilityGranted ||
                settings.emergencyActive ||
                emergencyRepository.state.value.isActive
            ) {
                return@withLock
            }

            val now = wallClock.now()
            val today = now.atZone(zoneId).toLocalDate()
            val usage = usageRepository.dailyUsage.value.forDate(today, now)
            val cycle = usageRepository.gateCycle.value.forDate(today, now)
            val dailyLimitSeconds = settings.dailyLimitMinutes
                .takeIf { settings.dailyLimitEnabled && it > 0 }
                ?.toLong()
                ?.times(SECONDS_PER_MINUTE)
            if (
                target in YOUTUBE_TARGETS &&
                systemAccess.state.value.usageAccessGranted &&
                dailyLimitSeconds != null &&
                usage.youtubeSeconds >= dailyLimitSeconds
            ) {
                mutableEnforcement.value = EnforcementUiState.DailyLimit(
                    usedMinutes = (usage.youtubeSeconds / SECONDS_PER_MINUTE).toInt(),
                    limitMinutes = settings.dailyLimitMinutes,
                )
                return@withLock
            }
            val existing = taskRepository.pendingTask.value
            if (existing?.target == target) {
                mutableEnforcement.value = existing.toUi(grantMinutesFor(existing.target))
                return@withLock
            }

            val intervalMinutes = grantMinutesFor(target)
            val intervalSeconds = intervalMinutes
                .takeIf { it > 0 }
                ?.toLong()
                ?.times(SECONDS_PER_MINUTE)
            val usedSeconds = cycle.usedSecondsFor(target)
            val cooldownUntil = listOfNotNull(
                cycle.cooldownUntilFor(target),
                volatileGrantedUntil[target],
            ).maxOrNull()
            val trigger = when {
                intervalSeconds != null && usedSeconds >= intervalSeconds ->
                    TaskTrigger.INTERVAL

                cooldownUntil?.let(now::isBefore) != true -> TaskTrigger.ENTRY
                else -> null
            } ?: return@withLock

            mutableEnforcement.value = ensureTask(cycle, usage, trigger, target)
        }
    }

    /** Starts a persisted focus session. Early bypass is intentionally delegated to Emergency Stop. */
    suspend fun startFocusMode(durationMinutes: Int, packageNames: Set<String>): Boolean {
        ready.first { it }
        return mutex.withLock {
            val packages = FocusAppCatalog.sanitize(packageNames)
            val safeDuration = durationMinutes.coerceIn(MIN_FOCUS_MINUTES, MAX_FOCUS_MINUTES)
            val current = settingsRepository.settings.value
            if (
                packages.isEmpty() ||
                !systemAccess.state.value.accessibilityGranted ||
                current.emergencyActive ||
                emergencyRepository.state.value.isActive
            ) {
                return@withLock false
            }
            val now = wallClock.now()
            settingsRepository.save(
                current.copy(
                    focusDurationMinutes = safeDuration,
                    focusBlockedPackages = packages,
                    focusStartedAt = now,
                    focusEndsAt = now.plusSeconds(safeDuration.toLong() * SECONDS_PER_MINUTE),
                ),
            )
            heartbeatWake.trySend(Unit)
            true
        }
    }

    suspend fun completeChallengePresentation() = mutex.withLock {
        mutableEnforcement.value = null
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
        val settings = settingsRepository.settings.value
        var usage = usageRepository.dailyUsage.value.forDate(today, now)
        var cycle = usageRepository.gateCycle.value.forDate(today, now)
        val youtubeActive = latestDeviceState.screenInteractive &&
            latestDeviceState.deviceUnlocked &&
            latestDeviceState.foregroundPackage == AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME
        val shortsActive = youtubeActive && latestDetectionState == ShortsDetectionState.SHORTS_CONFIRMED
        val instagramActive = latestDeviceState.screenInteractive &&
            latestDeviceState.deviceUnlocked &&
            latestDeviceState.foregroundPackage == AccessibilityAdapterController.INSTAGRAM_PACKAGE_NAME
        val pinterestActive = latestDeviceState.screenInteractive &&
            latestDeviceState.deviceUnlocked &&
            latestDeviceState.foregroundPackage == AccessibilityAdapterController.PINTEREST_PACKAGE_NAME
        val chromeActive = latestDeviceState.screenInteractive &&
            latestDeviceState.deviceUnlocked &&
            latestDeviceState.foregroundPackage == AccessibilityAdapterController.CHROME_PACKAGE_NAME
        var observedShortsSeconds = 0L

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
                if (settings.youtubeGateEnabled) {
                    cycle = cycle.copy(
                        youtubeUsedSeconds = cycle.youtubeUsedSeconds.saturatingAdd(seconds),
                        updatedAt = now,
                    )
                }
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
                observedShortsSeconds = seconds
                usage = usage.copy(
                    shortsSeconds = usage.shortsSeconds.saturatingAdd(seconds),
                    updatedAt = now,
                )
                if (settings.shortsGateEnabled) {
                    cycle = cycle.copy(
                        usedSeconds = cycle.usedSeconds.saturatingAdd(seconds),
                        updatedAt = now,
                    )
                }
            }
        } else {
            shortsRemainderMillis = 0
        }

        if (instagramActive && deltaMillis > 0) {
            val total = instagramRemainderMillis.saturatingAdd(deltaMillis)
            val seconds = total / MILLIS_PER_SECOND
            instagramRemainderMillis = total % MILLIS_PER_SECOND
            if (seconds > 0) {
                usage = usage.copy(
                    instagramSeconds = usage.instagramSeconds.saturatingAdd(seconds),
                    updatedAt = now,
                )
                if (settings.instagramGateEnabled) {
                    cycle = cycle.copy(
                        instagramUsedSeconds = cycle.instagramUsedSeconds.saturatingAdd(seconds),
                        updatedAt = now,
                    )
                }
            }
        } else {
            instagramRemainderMillis = 0
        }

        if (pinterestActive && deltaMillis > 0) {
            val total = pinterestRemainderMillis.saturatingAdd(deltaMillis)
            val seconds = total / MILLIS_PER_SECOND
            pinterestRemainderMillis = total % MILLIS_PER_SECOND
            if (seconds > 0) {
                usage = usage.copy(
                    pinterestSeconds = usage.pinterestSeconds.saturatingAdd(seconds),
                    updatedAt = now,
                )
                if (settings.pinterestGateEnabled) {
                    cycle = cycle.copy(
                        pinterestUsedSeconds = cycle.pinterestUsedSeconds.saturatingAdd(seconds),
                        updatedAt = now,
                    )
                }
            }
        } else {
            pinterestRemainderMillis = 0
        }

        if (chromeActive && deltaMillis > 0) {
            val total = chromeRemainderMillis.saturatingAdd(deltaMillis)
            val seconds = total / MILLIS_PER_SECOND
            chromeRemainderMillis = total % MILLIS_PER_SECOND
            if (seconds > 0) {
                usage = usage.copy(
                    chromeSeconds = usage.chromeSeconds.saturatingAdd(seconds),
                    updatedAt = now,
                )
                if (settings.chromeGateEnabled) {
                    cycle = cycle.copy(
                        chromeUsedSeconds = cycle.chromeUsedSeconds.saturatingAdd(seconds),
                        updatedAt = now,
                    )
                }
            }
        } else {
            chromeRemainderMillis = 0
        }

        if (observedShortsSeconds > 0) {
            val difficultyState = taskDifficultyPolicy.update(
                state = cycle.toDifficultyState(),
                now = now,
                shortsActive = true,
                config = difficultyConfig(),
                observedActiveSeconds = observedShortsSeconds,
            )
            cycle = cycle.withDifficultyState(difficultyState, now)
        }

        if (usage != usageRepository.dailyUsage.value) usageRepository.saveDailyUsage(usage)
        if (cycle != usageRepository.gateCycle.value) usageRepository.saveGateCycle(cycle)
        evaluatePolicyLocked(usage, cycle, triggeringEvent = null)
    }

    private suspend fun handleDeviceState(state: DeviceState) = mutex.withLock {
        if (state.foregroundPackage != latestDeviceState.foregroundPackage) {
            lastTickElapsedMillis = SystemClock.elapsedRealtime()
            youtubeRemainderMillis = 0
            shortsRemainderMillis = 0
            instagramRemainderMillis = 0
            pinterestRemainderMillis = 0
            chromeRemainderMillis = 0
        }
        latestDeviceState = state
        if (
            state.foregroundPackage != AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME
        ) {
            latestDetectionState = ShortsDetectionState.NOT_SHORTS
            detector.resetToNotShorts(SystemClock.elapsedRealtime())
            entryGate.onYouTubeForegroundLost()
        }
        evaluatePolicyLocked(
            usage = usageRepository.dailyUsage.value,
            cycle = usageRepository.gateCycle.value,
            triggeringEvent = null,
        )
    }

    private suspend fun awaitNextHeartbeat() {
        val settings = settingsRepository.settings.value
        val targetForeground = latestDeviceState.foregroundPackage in
            AccessibilityAdapterController.TARGET_PACKAGE_NAMES ||
            FocusSession(
                startedAt = settings.focusStartedAt,
                endsAt = settings.focusEndsAt,
                blockedPackages = settings.focusBlockedPackages,
            ).blocks(latestDeviceState.foregroundPackage, wallClock.now())
        val waitMillis = if (targetForeground) {
            ACTIVE_HEARTBEAT_MILLIS
        } else {
            IDLE_HEARTBEAT_MILLIS
        }
        withTimeoutOrNull(waitMillis) { heartbeatWake.receive() }
    }

    private suspend fun evaluatePolicy(
        triggeringEvent: AccessibilityWindowEvent?,
    ) = mutex.withLock {
        evaluatePolicyLocked(
            usage = usageRepository.dailyUsage.value,
            cycle = usageRepository.gateCycle.value,
            triggeringEvent = triggeringEvent,
        )
    }

    private suspend fun evaluatePolicyLocked(
        usage: DailyUsage,
        cycle: GateCycle,
        triggeringEvent: AccessibilityWindowEvent?,
    ) {
        val now = wallClock.now()
        if (evaluateFocusModeLocked(now)) return
        val youtubeForeground = latestDeviceState.foregroundPackage ==
            AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME
        val access = systemAccess.state.value
        val effectiveSettings = settingsRepository.settings.value.copy(
            emergencyActive = emergencyOverrideActive ||
                settingsRepository.settings.value.emergencyActive,
        )
        val foregroundTarget = latestDeviceState.foregroundPackage.toWholeAppTarget()
        if (foregroundTarget != null) {
            val dailyLimitReached = foregroundTarget == TaskTarget.YOUTUBE &&
                effectiveSettings.dailyLimitEnabled &&
                access.usageAccessGranted &&
                usage.youtubeSeconds >= effectiveSettings.dailyLimitMinutes
                .coerceAtLeast(1).toLong() * SECONDS_PER_MINUTE
            if (!dailyLimitReached && evaluateAppPolicyLocked(
                    target = foregroundTarget,
                    usage = usage,
                    cycle = cycle,
                    settings = effectiveSettings,
                    accessibilityGranted = access.accessibilityGranted,
                )
            ) {
                return
            }
            if (foregroundTarget != TaskTarget.YOUTUBE) return
        }
        val intervalDue = effectiveSettings.shortsGateEnabled &&
            effectiveSettings.shortsIntervalMinutes > 0 &&
            cycle.usedSeconds >= effectiveSettings.shortsIntervalMinutes.toLong() *
            SECONDS_PER_MINUTE
        val pendingTask = taskRepository.pendingTask.value
        val youtubeCycle = cycle.forPolicyTarget(
            pendingTask = pendingTask,
            target = TaskTarget.YOUTUBE_SHORTS,
        )
        val decision = policyEngine.decide(
            PolicyInput(
                settings = effectiveSettings,
                permissions = PermissionState(
                    accessibilityGranted = access.accessibilityGranted,
                    usageAccessGranted = access.usageAccessGranted,
                ),
                dailyUsage = usage,
                gateCycle = youtubeCycle,
                pendingTask = pendingTask
                    ?.takeIf { it.target == TaskTarget.YOUTUBE_SHORTS },
                emergencyState = emergencyRepository.state.value,
                detectorState = latestDetectionState,
                youtubeForeground = youtubeForeground,
                entryGatePaid = entryGate.isPaid(SystemClock.elapsedRealtime()) ||
                    cycle.entryCooldownUntil?.let(now::isBefore) == true,
            ),
        )
        when (decision) {
            is PolicyDecision.DailyLimitReached -> {
                deferredIntervalGate.reset()
                showEnforcement(
                    EnforcementUiState.DailyLimit(
                        usedMinutes = (decision.usedSeconds / SECONDS_PER_MINUTE).toInt(),
                        limitMinutes = (decision.limitSeconds / SECONDS_PER_MINUTE).toInt(),
                    ),
                )
                requestShortsEjection()
            }

            is PolicyDecision.TaskGateRequired -> when {
                decision.pendingTaskId != null -> {
                    deferredIntervalGate.reset()
                    showEnforcement(
                        ensureTask(cycle, usage, decision.trigger, TaskTarget.YOUTUBE_SHORTS),
                    )
                    requestShortsEjection()
                }

                decision.trigger == TaskTrigger.ENTRY -> {
                    deferredIntervalGate.reset()
                    showEnforcement(
                        ensureTask(cycle, usage, TaskTrigger.ENTRY, TaskTarget.YOUTUBE_SHORTS),
                    )
                    requestShortsEjection()
                }

                else -> {
                    handleDeferredInterval(cycle, usage, triggeringEvent)
                }
            }

            PolicyDecision.Allow -> if (intervalDue) {
                handleDeferredInterval(cycle, usage, triggeringEvent)
            } else {
                deferredIntervalGate.reset()
            }
            PolicyDecision.EmergencyBypass,
            is PolicyDecision.RequirementsMissing,
            -> deferredIntervalGate.reset()
        }
    }

    private suspend fun evaluateFocusModeLocked(now: Instant): Boolean {
        val settings = settingsRepository.settings.value
        val session = FocusSession(
            startedAt = settings.focusStartedAt,
            endsAt = settings.focusEndsAt,
            blockedPackages = FocusAppCatalog.sanitize(settings.focusBlockedPackages),
        )
        if (!session.isActiveAt(now)) {
            if (settings.focusStartedAt != null || settings.focusEndsAt != null) {
                settingsRepository.save(settings.copy(focusStartedAt = null, focusEndsAt = null))
            }
            return false
        }
        if (
            settings.emergencyActive ||
            emergencyOverrideActive ||
            emergencyRepository.state.value.isActive ||
            !systemAccess.state.value.accessibilityGranted
        ) {
            return false
        }
        val packageName = latestDeviceState.foregroundPackage
        if (!session.blocks(packageName, now)) return false
        requestFocusEjection(packageName.orEmpty())
        return true
    }

    private fun requestFocusEjection(packageName: String) {
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed < focusEjectionBlockedUntilElapsedMillis) return
        focusEjectionBlockedUntilElapsedMillis = elapsed + FOCUS_EJECTION_THROTTLE_MILLIS
        val label = FocusAppCatalog.apps
            .firstOrNull { it.packageName == packageName }
            ?.label
            ?: "Приложение"
        activeService?.ejectBlockedApp(label)
    }

    /** Returns true only when this app gate actively enforces a pending or newly due challenge. */
    private suspend fun evaluateAppPolicyLocked(
        target: TaskTarget,
        usage: DailyUsage,
        cycle: GateCycle,
        settings: com.filodot.noscroll.core.model.UserSettings,
        accessibilityGranted: Boolean,
    ): Boolean {
        val now = wallClock.now()
        val existing = taskRepository.pendingTask.value
        val cooldownUntil = listOfNotNull(
            cycle.cooldownUntilFor(target),
            volatileGrantedUntil[target],
        ).maxOrNull()
        val decision = appGatePolicy.decide(
            input = AppGateInput(
                enabled = settings.gateEnabled(target),
                accessibilityGranted = accessibilityGranted,
                bypassActive = settings.emergencyActive ||
                    emergencyRepository.state.value.isActive,
                intervalMinutes = settings.intervalMinutesFor(target),
                usedSeconds = cycle.usedSecondsFor(target),
                cooldownUntil = cooldownUntil,
                pendingTrigger = existing?.takeIf { it.target == target }?.trigger,
            ),
            now = now,
        )
        val trigger = (decision as? AppGateDecision.RequireTask)?.trigger ?: return false
        showEnforcement(ensureTask(cycle, usage, trigger, target))
        requestTargetEjection()
        return true
    }

    private suspend fun handleDeferredInterval(
        cycle: GateCycle,
        usage: DailyUsage,
        triggeringEvent: AccessibilityWindowEvent?,
    ) {
        val eventElapsed = triggeringEvent?.elapsedRealtimeMillis
            ?: SystemClock.elapsedRealtime()
        val action = deferredIntervalGate.update(
            intervalDue = true,
            shortsState = latestDetectionState,
            viewScrolled = triggeringEvent?.eventType ==
                AccessibilityAdapterController.TYPE_VIEW_SCROLLED,
            elapsedMillis = eventElapsed,
        )
        if (action == DeferredIntervalAction.ENFORCE) {
            showEnforcement(
                ensureTask(cycle, usage, TaskTrigger.INTERVAL, TaskTarget.YOUTUBE_SHORTS),
            )
            deferredIntervalGate.reset()
            requestTargetEjection()
        }
    }

    private fun requestShortsEjection() {
        requestTargetEjection()
    }

    private fun requestTargetEjection() {
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed < shortsEjectionBlockedUntilElapsedMillis) return
        shortsEjectionBlockedUntilElapsedMillis = elapsed + SHORTS_EJECTION_THROTTLE_MILLIS
        latestDetectionState = ShortsDetectionState.NOT_SHORTS
        detector.resetToNotShorts(elapsed)
        activeService?.ejectTargetAndOpenChallenge()
    }

    private suspend fun ensureTask(
        cycle: GateCycle,
        usage: DailyUsage,
        requestedTrigger: TaskTrigger,
        target: TaskTarget,
    ): EnforcementUiState.TaskGate {
        val existing = taskRepository.pendingTask.value
        val activeTaskGate = mutableEnforcement.value as? EnforcementUiState.TaskGate
        if (existing == null && activeTaskGate?.target == target) return activeTaskGate
        val now = wallClock.now()
        val effectiveDifficultyState = taskDifficultyPolicy.update(
            state = cycle.toDifficultyState(),
            now = now,
            shortsActive = false,
            config = difficultyConfig(),
        )
        val cycleForTask = cycle.withDifficultyState(effectiveDifficultyState, now)
        val difficulty = taskDifficultyPolicy.difficulty(
            state = effectiveDifficultyState,
            config = difficultyConfig(),
        )
        val task = when {
            existing == null -> newTask(difficulty, requestedTrigger, target).also { created ->
                taskRepository.save(created)
                usageRepository.saveGateCycle(
                    cycleForTask.copy(
                        pendingTaskId = created.id,
                        updatedAt = now,
                    ),
                )
                usageRepository.saveDailyUsage(
                    usage.copy(
                        gatesShown = usage.gatesShown.saturatingIncrement(),
                        updatedAt = now,
                    ),
                )
            }

            existing.target != target -> existing.retargetFor(target, requestedTrigger)
                .also { retargeted ->
                    taskRepository.save(retargeted)
                }

            else -> existing
        }
        if (cycleForTask.pendingTaskId != task.id) {
            usageRepository.saveGateCycle(
                cycleForTask.copy(pendingTaskId = task.id, updatedAt = wallClock.now()),
            )
        }
        return task.toUi(grantMinutesFor(task.target))
    }

    private suspend fun newTask(
        difficulty: TaskDifficulty,
        trigger: TaskTrigger,
        target: TaskTarget,
        sequenceOffset: Int = 0,
    ): PendingTask {
        val settings = settingsRepository.settings.value
        val customPresets = taskPresetRepository.presets.value
        val available = TaskType.entries.filter { type ->
            type in settings.enabledTaskTypes &&
                (type != TaskType.CUSTOM || customPresets.any { it.enabled })
        }.ifEmpty { listOf(TaskType.ARITHMETIC) }
        val sequence = (usageRepository.dailyUsage.value.gatesShown.toLong() + sequenceOffset)
            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
            .toInt()
        val selected = available[Math.floorMod(sequence, available.size)]
        if (selected == TaskType.LEARNING) {
            learningTaskFactory.create(
                difficulty = difficulty,
                trigger = trigger,
                target = target,
                selectedCourseIds = settings.selectedLearningCourseIds,
                sequence = sequence,
            )?.let { return it }
        }
        val localTypes = if (selected == TaskType.LEARNING) {
            settings.enabledTaskTypes - TaskType.LEARNING
        } else {
            setOf(selected)
        }
        return taskFactory.create(
            difficulty = difficulty,
            trigger = trigger,
            target = target,
            enabledTypes = localTypes,
            customPresets = customPresets,
            sequence = sequence,
        )
    }

    private suspend fun recordLearningResult(task: PendingTask, result: AttemptResult) {
        if (task.type != TaskType.LEARNING) return
        try {
            learningTaskFactory.recordResult(task, result)
        } catch (error: Exception) {
            Log.w(LOG_TAG, "Learning progress write failed: ${error::class.java.simpleName}")
        }
    }

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
                    triggeringEvent = null,
                )
            }
        }
    }

    private fun DailyUsage.forDate(date: LocalDate, now: Instant): DailyUsage =
        if (localDate == date) this else DailyUsage(localDate = date, updatedAt = now)

    private fun GateCycle.forDate(date: LocalDate, now: Instant): GateCycle =
        if (localDate == date) {
            this
        } else {
            GateCycle(
                localDate = date,
                updatedAt = now,
                entryCooldownUntil = entryCooldownUntil?.takeIf(now::isBefore),
                instagramEntryCooldownUntil = instagramEntryCooldownUntil?.takeIf(now::isBefore),
                youtubeEntryCooldownUntil = youtubeEntryCooldownUntil?.takeIf(now::isBefore),
                pinterestEntryCooldownUntil = pinterestEntryCooldownUntil?.takeIf(now::isBefore),
                chromeEntryCooldownUntil = chromeEntryCooldownUntil?.takeIf(now::isBefore),
                difficultyLoadSeconds = difficultyLoadSeconds,
                difficultyLoadUpdatedAt = difficultyLoadUpdatedAt,
                difficultyRecoverySeconds = difficultyRecoverySeconds,
            )
        }

    private fun PendingTask.toUi(grantMinutes: Int): EnforcementUiState.TaskGate {
        val symbol = when (operation) {
            ArithmeticOperation.ADD -> "+"
            ArithmeticOperation.SUBTRACT -> "−"
            ArithmeticOperation.MULTIPLY -> "×"
            ArithmeticOperation.DIVIDE -> "÷"
        }
        val spokenOperation = when (operation) {
            ArithmeticOperation.ADD -> "плюс"
            ArithmeticOperation.SUBTRACT -> "минус"
            ArithmeticOperation.MULTIPLY -> "умножить на"
            ArithmeticOperation.DIVIDE -> "разделить на"
        }
        return EnforcementUiState.TaskGate(
            taskId = id,
            visualExpression = if (type == TaskType.ARITHMETIC) {
                "$leftOperand $symbol $rightOperand"
            } else {
                prompt
            },
            spokenExpression = if (type == TaskType.ARITHMETIC) {
                "$leftOperand $spokenOperation $rightOperand"
            } else {
                prompt
            },
            grantMinutes = grantMinutes,
            wrongAttempts = wrongAttempts,
            answerStatus = if (wrongAttempts > 0) {
                TaskAnswerStatus.INCORRECT
            } else {
                TaskAnswerStatus.READY
            },
            difficulty = difficulty,
            trigger = trigger,
            target = target,
            type = type,
            completionMode = completionMode,
            choices = choices,
            learningMaterial = learningMaterial,
            showingLearningMaterial = !learningMaterial.isNullOrBlank(),
            explanation = learningExplanation,
        )
    }

    private fun difficultyConfig(): TaskDifficultyConfig {
        val settings = settingsRepository.settings.value
        return TaskDifficultyConfig(
            mediumThresholdMinutes = settings.difficultyMediumThresholdMinutes,
            hardThresholdMinutes = settings.difficultyHardThresholdMinutes,
            decayBreakMinutesPerLoadMinute = settings.difficultyDecayBreakMinutes,
        )
    }

    private fun grantMinutesFor(target: TaskTarget): Int {
        val settings = settingsRepository.settings.value
        return settings.intervalMinutesFor(target)
    }

    private fun GateCycle.toDifficultyState(): TaskDifficultyState = TaskDifficultyState(
        loadSeconds = difficultyLoadSeconds,
        updatedAt = difficultyLoadUpdatedAt,
        recoverySeconds = difficultyRecoverySeconds,
    )

    private fun GateCycle.withDifficultyState(
        state: TaskDifficultyState,
        now: Instant,
    ): GateCycle = copy(
        difficultyLoadSeconds = state.loadSeconds,
        difficultyLoadUpdatedAt = state.updatedAt,
        difficultyRecoverySeconds = state.recoverySeconds,
        updatedAt = now,
    )

    private suspend fun runRecoveringStream(
        failureCode: String,
        block: suspend () -> Unit,
    ) {
        while (currentCoroutineContext().isActive) {
            try {
                recordRuntimeRecovery(failureCode)
                block()
                if (currentCoroutineContext().isActive) {
                    recordRuntimeFailure("${failureCode}_ENDED")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                recordRuntimeFailure(failureCode, error)
            }
            delay(STREAM_RETRY_MILLIS)
        }
    }

    private suspend fun runGuarded(
        failureCode: String,
        block: suspend () -> Unit,
    ) {
        try {
            block()
            recordRuntimeRecovery(failureCode)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            recordRuntimeFailure(failureCode, error)
        }
    }

    private fun markHeartbeatHealthy() {
        lastHealthyHeartbeatElapsedMillis = SystemClock.elapsedRealtime()
        recordRuntimeRecovery(FAILURE_TRANSIENT_INTERRUPT)
        recordRuntimeRecovery(FAILURE_HEARTBEAT_STALE)
        val health = synchronized(failureLock) {
            if (activeFailureCodes.isEmpty()) {
                MonitoringHealthStatus.RUNNING
            } else {
                MonitoringHealthStatus.RECOVERING
            }
        }
        mutableDiagnostics.update {
            it.copy(
                healthStatus = health,
                serviceConnected = true,
                lastHeartbeatAt = wallClock.now(),
            )
        }
    }

    private fun recordRuntimeFailure(code: String, error: Exception? = null) {
        val newlyActive = synchronized(failureLock) {
            val added = code !in activeFailureCodes
            activeFailureCodes = activeFailureCodes + code
            added
        }
        if (error != null) Log.w(LOG_TAG, code, error)
        mutableDiagnostics.update {
            it.copy(
                healthStatus = MonitoringHealthStatus.RECOVERING,
                recoveryCount = if (newlyActive) {
                    it.recoveryCount.saturatingIncrement()
                } else {
                    it.recoveryCount
                },
                lastFailureCode = code,
            )
        }
    }

    private fun recordRuntimeRecovery(code: String) {
        val health = synchronized(failureLock) {
            activeFailureCodes = activeFailureCodes - code
            if (activeFailureCodes.isEmpty()) {
                MonitoringHealthStatus.RUNNING
            } else {
                MonitoringHealthStatus.RECOVERING
            }
        }
        mutableDiagnostics.update { diagnostics ->
            if (!diagnostics.serviceConnected) {
                diagnostics
            } else {
                diagnostics.copy(healthStatus = health)
            }
        }
    }

    private fun UserSettings.gateEnabled(target: TaskTarget): Boolean = when (target) {
        TaskTarget.YOUTUBE_SHORTS -> shortsGateEnabled
        TaskTarget.YOUTUBE -> youtubeGateEnabled
        TaskTarget.INSTAGRAM -> instagramGateEnabled
        TaskTarget.PINTEREST -> pinterestGateEnabled
        TaskTarget.CHROME -> chromeGateEnabled
    }

    private fun UserSettings.intervalMinutesFor(target: TaskTarget): Int = when (target) {
        TaskTarget.YOUTUBE_SHORTS -> shortsIntervalMinutes
        TaskTarget.YOUTUBE -> youtubeIntervalMinutes
        TaskTarget.INSTAGRAM -> instagramIntervalMinutes
        TaskTarget.PINTEREST -> pinterestIntervalMinutes
        TaskTarget.CHROME -> chromeIntervalMinutes
    }

    private fun GateCycle.usedSecondsFor(target: TaskTarget): Long = when (target) {
        TaskTarget.YOUTUBE_SHORTS -> usedSeconds
        TaskTarget.YOUTUBE -> youtubeUsedSeconds
        TaskTarget.INSTAGRAM -> instagramUsedSeconds
        TaskTarget.PINTEREST -> pinterestUsedSeconds
        TaskTarget.CHROME -> chromeUsedSeconds
    }

    private fun GateCycle.cooldownUntilFor(target: TaskTarget): Instant? = when (target) {
        TaskTarget.YOUTUBE_SHORTS -> entryCooldownUntil
        TaskTarget.YOUTUBE -> youtubeEntryCooldownUntil
        TaskTarget.INSTAGRAM -> instagramEntryCooldownUntil
        TaskTarget.PINTEREST -> pinterestEntryCooldownUntil
        TaskTarget.CHROME -> chromeEntryCooldownUntil
    }

    private fun GateCycle.grantTarget(
        target: TaskTarget,
        cooldownUntil: Instant,
        now: Instant,
    ): GateCycle = when (target) {
        TaskTarget.YOUTUBE_SHORTS -> copy(
            usedSeconds = 0,
            entryCooldownUntil = cooldownUntil,
        )

        TaskTarget.YOUTUBE -> copy(
            youtubeUsedSeconds = 0,
            youtubeEntryCooldownUntil = cooldownUntil,
        )

        TaskTarget.INSTAGRAM -> copy(
            instagramUsedSeconds = 0,
            instagramEntryCooldownUntil = cooldownUntil,
        )

        TaskTarget.PINTEREST -> copy(
            pinterestUsedSeconds = 0,
            pinterestEntryCooldownUntil = cooldownUntil,
        )

        TaskTarget.CHROME -> copy(
            chromeUsedSeconds = 0,
            chromeEntryCooldownUntil = cooldownUntil,
        )
    }.copy(pendingTaskId = null, updatedAt = now)

    private fun String?.toWholeAppTarget(): TaskTarget? = when (this) {
        AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME -> TaskTarget.YOUTUBE
        AccessibilityAdapterController.INSTAGRAM_PACKAGE_NAME -> TaskTarget.INSTAGRAM
        AccessibilityAdapterController.PINTEREST_PACKAGE_NAME -> TaskTarget.PINTEREST
        AccessibilityAdapterController.CHROME_PACKAGE_NAME -> TaskTarget.CHROME
        else -> null
    }

    private fun Long.saturatingAdd(other: Long): Long =
        if (other > Long.MAX_VALUE - this) Long.MAX_VALUE else this + other

    private fun Int.saturatingIncrement(): Int = if (this == Int.MAX_VALUE) this else this + 1

    companion object {
        private const val LOG_TAG = "NoScrollMonitoring"
        private const val ACTIVE_HEARTBEAT_MILLIS = 1_000L
        private const val IDLE_HEARTBEAT_MILLIS = 15_000L
        private const val RECONCILIATION_MILLIS = 60_000L
        private const val HEALTH_WATCHDOG_INTERVAL_MILLIS = 15_000L
        private const val HEARTBEAT_STALE_AFTER_MILLIS = 45_000L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val SECONDS_PER_MINUTE = 60L
        private const val WRONG_ATTEMPTS_FOR_REPLACEMENT = 3
        private const val SHORTS_EJECTION_THROTTLE_MILLIS = 2_000L
        private const val FOCUS_EJECTION_THROTTLE_MILLIS = 1_500L
        private const val MIN_FOCUS_MINUTES = 5
        private const val MAX_FOCUS_MINUTES = 720
        private const val STREAM_RETRY_MILLIS = 1_000L
        private const val FAILURE_DEVICE_STATE_STREAM = "DEVICE_STATE_STREAM"
        private const val FAILURE_EVENT_STREAM = "EVENT_STREAM"
        private const val FAILURE_HEARTBEAT = "HEARTBEAT"
        private const val FAILURE_HEARTBEAT_STALE = "HEARTBEAT_STALE"
        private const val FAILURE_RECONCILIATION = "RECONCILIATION"
        private const val FAILURE_TRANSIENT_INTERRUPT = "TRANSIENT_INTERRUPT"
        private val YOUTUBE_TARGETS = setOf(TaskTarget.YOUTUBE_SHORTS, TaskTarget.YOUTUBE)
    }
}

internal fun GateCycle.forPolicyTarget(
    pendingTask: PendingTask?,
    target: TaskTarget,
): GateCycle = if (pendingTask != null && pendingTask.target != target) {
    copy(pendingTaskId = null)
} else {
    this
}

internal fun PendingTask.retargetFor(
    target: TaskTarget,
    trigger: TaskTrigger,
): PendingTask = if (this.target == target) this else copy(target = target, trigger = trigger)
