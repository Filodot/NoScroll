package com.filodot.noscroll.core.policy

import com.filodot.noscroll.core.model.ArithmeticOperation
import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.EmergencyActivationSource
import com.filodot.noscroll.core.model.EmergencyEvent
import com.filodot.noscroll.core.model.EmergencyState
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.model.PermissionState
import com.filodot.noscroll.core.model.PolicyDecision
import com.filodot.noscroll.core.model.PolicyInput
import com.filodot.noscroll.core.model.ShortsDetectionState
import com.filodot.noscroll.core.model.UserSettings
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyEngineTest {
    private val engine = PolicyEngine()
    private val instant = Instant.parse("2026-07-14T00:00:00Z")
    private val date = LocalDate.of(2026, 7, 14)

    @Test
    fun `priority matrix covers every emergency permission daily and task combination`() {
        listOf(false, true).forEach { emergencyActive ->
            listOf(false, true).forEach { accessibilityGranted ->
                listOf(false, true).forEach { usageAccessGranted ->
                    listOf(false, true).forEach { youtubeForeground ->
                        listOf(false, true).forEach { dailyReached ->
                            listOf(false, true).forEach { taskReached ->
                                val input = input(
                                    emergencyActive = emergencyActive,
                                    accessibilityGranted = accessibilityGranted,
                                    usageAccessGranted = usageAccessGranted,
                                    youtubeForeground = youtubeForeground,
                                    dailySeconds = if (dailyReached) DAILY_LIMIT_SECONDS else 0,
                                    cycleSeconds = if (taskReached) SHORTS_LIMIT_SECONDS else 0,
                                    detectorState = ShortsDetectionState.SHORTS_CONFIRMED,
                                )
                                val expected = when {
                                    emergencyActive -> PolicyDecision.EmergencyBypass
                                    !accessibilityGranted -> PolicyDecision.RequirementsMissing(
                                        accessibilityMissing = true,
                                        usageAccessMissing = !usageAccessGranted,
                                    )
                                    !youtubeForeground -> PolicyDecision.Allow
                                    dailyReached && usageAccessGranted ->
                                        PolicyDecision.DailyLimitReached(
                                            usedSeconds = DAILY_LIMIT_SECONDS,
                                            limitSeconds = DAILY_LIMIT_SECONDS,
                                        )
                                    taskReached ->
                                        PolicyDecision.TaskGateRequired(pendingTaskId = null)
                                    else -> PolicyDecision.Allow
                                }

                                assertEquals(
                                    "emergency=$emergencyActive, " +
                                        "accessibility=$accessibilityGranted, " +
                                        "usage=$usageAccessGranted, youtube=$youtubeForeground, " +
                                        "daily=$dailyReached, task=$taskReached",
                                    expected,
                                    engine.decide(input),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `emergency repository state has absolute priority`() {
        val decision = engine.decide(
            input(
                emergencyState = activeEmergencyState(),
                accessibilityGranted = false,
                usageAccessGranted = false,
                youtubeForeground = true,
                dailySeconds = DAILY_LIMIT_SECONDS,
                cycleSeconds = SHORTS_LIMIT_SECONDS,
                detectorState = ShortsDetectionState.SHORTS_CONFIRMED,
            ),
        )

        assertEquals(PolicyDecision.EmergencyBypass, decision)
    }

    @Test
    fun `settings emergency mirror also fails open`() {
        val decision = engine.decide(
            input(
                settings = UserSettings(emergencyActive = true),
                emergencyState = EmergencyState(),
                accessibilityGranted = false,
            ),
        )

        assertEquals(PolicyDecision.EmergencyBypass, decision)
    }

    @Test
    fun `missing accessibility reports required missing usage access`() {
        val decision = engine.decide(
            input(
                accessibilityGranted = false,
                usageAccessGranted = false,
            ),
        )

        assertEquals(
            PolicyDecision.RequirementsMissing(
                accessibilityMissing = true,
                usageAccessMissing = true,
            ),
            decision,
        )
    }

    @Test
    fun `missing usage access does not disable a due Shorts gate`() {
        val decision = engine.decide(
            input(
                usageAccessGranted = false,
                dailySeconds = DAILY_LIMIT_SECONDS,
                cycleSeconds = SHORTS_LIMIT_SECONDS,
                detectorState = ShortsDetectionState.SHORTS_CONFIRMED,
            ),
        )

        assertEquals(PolicyDecision.TaskGateRequired(pendingTaskId = null), decision)
    }

    @Test
    fun `missing usage access makes daily unavailable and otherwise allows`() {
        val decision = engine.decide(
            input(
                usageAccessGranted = false,
                dailySeconds = DAILY_LIMIT_SECONDS,
                detectorState = ShortsDetectionState.NOT_SHORTS,
            ),
        )

        assertEquals(PolicyDecision.Allow, decision)
    }

    @Test
    fun `disabled daily limit is never enforced`() {
        val decision = engine.decide(
            input(
                settings = UserSettings(dailyLimitEnabled = false),
                dailySeconds = DAILY_LIMIT_SECONDS * 2,
            ),
        )

        assertEquals(PolicyDecision.Allow, decision)
    }

    @Test
    fun `daily limit triggers exactly at its boundary and returns totals`() {
        val decision = engine.decide(input(dailySeconds = DAILY_LIMIT_SECONDS))

        assertEquals(
            PolicyDecision.DailyLimitReached(
                usedSeconds = DAILY_LIMIT_SECONDS,
                limitSeconds = DAILY_LIMIT_SECONDS,
            ),
            decision,
        )
    }

    @Test
    fun `pending task restores gate before the interval boundary`() {
        val task = pendingTask("task-1")
        val decision = engine.decide(
            input(
                cycleSeconds = 1,
                pendingTask = task,
                detectorState = ShortsDetectionState.SHORTS_CONFIRMED,
            ),
        )

        assertEquals(PolicyDecision.TaskGateRequired("task-1"), decision)
    }

    @Test
    fun `gate cycle task id restores gate while task snapshot is loading`() {
        val decision = engine.decide(
            input(
                gateCyclePendingTaskId = "task-from-cycle",
                detectorState = ShortsDetectionState.SHORTS_CONFIRMED,
            ),
        )

        assertEquals(PolicyDecision.TaskGateRequired("task-from-cycle"), decision)
    }

    @Test
    fun `unknown detector state fails open even when cycle and task are pending`() {
        val decision = engine.decide(
            input(
                cycleSeconds = SHORTS_LIMIT_SECONDS,
                pendingTask = pendingTask("task-1"),
                detectorState = ShortsDetectionState.UNKNOWN,
            ),
        )

        assertEquals(PolicyDecision.Allow, decision)
    }

    @Test
    fun `ordinary YouTube does not show a pending Shorts task`() {
        val decision = engine.decide(
            input(
                cycleSeconds = SHORTS_LIMIT_SECONDS,
                pendingTask = pendingTask("task-1"),
                detectorState = ShortsDetectionState.NOT_SHORTS,
            ),
        )

        assertEquals(PolicyDecision.Allow, decision)
    }

    @Test
    fun `disabled Shorts gate ignores cycle and pending task`() {
        val decision = engine.decide(
            input(
                settings = UserSettings(shortsGateEnabled = false),
                cycleSeconds = SHORTS_LIMIT_SECONDS,
                pendingTask = pendingTask("task-1"),
                detectorState = ShortsDetectionState.SHORTS_CONFIRMED,
            ),
        )

        assertEquals(PolicyDecision.Allow, decision)
    }

    @Test
    fun `non-positive persisted limits fail open`() {
        val decision = engine.decide(
            input(
                settings = UserSettings(
                    shortsIntervalMinutes = 0,
                    dailyLimitMinutes = 0,
                ),
                dailySeconds = 1,
                cycleSeconds = 1,
                detectorState = ShortsDetectionState.SHORTS_CONFIRMED,
            ),
        )

        assertEquals(PolicyDecision.Allow, decision)
    }

    @Test
    fun `lowering Shorts interval recalculates gate immediately`() {
        val original = input(
            settings = UserSettings(shortsIntervalMinutes = 10),
            cycleSeconds = SHORTS_LIMIT_SECONDS,
            detectorState = ShortsDetectionState.SHORTS_CONFIRMED,
        )

        assertEquals(PolicyDecision.Allow, engine.decide(original))
        assertEquals(
            PolicyDecision.TaskGateRequired(pendingTaskId = null),
            engine.decide(original.copy(settings = original.settings.copy(shortsIntervalMinutes = 5))),
        )
    }

    @Test
    fun `daily crossing replaces task gate without an allow decision`() {
        val taskDue = input(
            dailySeconds = DAILY_LIMIT_SECONDS - 1,
            cycleSeconds = SHORTS_LIMIT_SECONDS,
            pendingTask = pendingTask("task-1"),
            detectorState = ShortsDetectionState.SHORTS_CONFIRMED,
        )

        assertEquals(PolicyDecision.TaskGateRequired("task-1"), engine.decide(taskDue))
        assertEquals(
            PolicyDecision.DailyLimitReached(
                usedSeconds = DAILY_LIMIT_SECONDS,
                limitSeconds = DAILY_LIMIT_SECONDS,
            ),
            engine.decide(
                taskDue.copy(
                    dailyUsage = taskDue.dailyUsage.copy(youtubeSeconds = DAILY_LIMIT_SECONDS),
                ),
            ),
        )
    }

    @Test
    fun `emergency deactivation immediately reveals an expired daily limit`() {
        val emergency = input(
            emergencyState = activeEmergencyState(),
            dailySeconds = DAILY_LIMIT_SECONDS,
            cycleSeconds = SHORTS_LIMIT_SECONDS,
            detectorState = ShortsDetectionState.SHORTS_CONFIRMED,
        )

        assertEquals(PolicyDecision.EmergencyBypass, engine.decide(emergency))
        assertEquals(
            PolicyDecision.DailyLimitReached(
                usedSeconds = DAILY_LIMIT_SECONDS,
                limitSeconds = DAILY_LIMIT_SECONDS,
            ),
            engine.decide(emergency.copy(emergencyState = EmergencyState())),
        )
    }

    @Test
    fun `leaving and returning to Shorts preserves a pending gate`() {
        val taskDue = input(
            pendingTask = pendingTask("task-1"),
            detectorState = ShortsDetectionState.SHORTS_CONFIRMED,
        )

        assertEquals(PolicyDecision.TaskGateRequired("task-1"), engine.decide(taskDue))
        assertEquals(
            PolicyDecision.Allow,
            engine.decide(taskDue.copy(youtubeForeground = false)),
        )
        assertEquals(PolicyDecision.TaskGateRequired("task-1"), engine.decide(taskDue))
    }

    @Test
    fun `accessibility revoke and restore returns to the due task`() {
        val taskDue = input(
            pendingTask = pendingTask("task-1"),
            detectorState = ShortsDetectionState.SHORTS_CONFIRMED,
        )

        assertEquals(
            PolicyDecision.RequirementsMissing(
                accessibilityMissing = true,
                usageAccessMissing = false,
            ),
            engine.decide(
                taskDue.copy(
                    permissions = taskDue.permissions.copy(accessibilityGranted = false),
                ),
            ),
        )
        assertEquals(PolicyDecision.TaskGateRequired("task-1"), engine.decide(taskDue))
    }

    private fun input(
        settings: UserSettings = UserSettings(),
        emergencyActive: Boolean = false,
        emergencyState: EmergencyState = EmergencyState(),
        accessibilityGranted: Boolean = true,
        usageAccessGranted: Boolean = true,
        youtubeForeground: Boolean = true,
        dailySeconds: Long = 0,
        cycleSeconds: Long = 0,
        gateCyclePendingTaskId: String? = null,
        pendingTask: PendingTask? = null,
        detectorState: ShortsDetectionState = ShortsDetectionState.NOT_SHORTS,
    ): PolicyInput = PolicyInput(
        settings = settings.copy(emergencyActive = emergencyActive || settings.emergencyActive),
        permissions = PermissionState(
            accessibilityGranted = accessibilityGranted,
            usageAccessGranted = usageAccessGranted,
        ),
        dailyUsage = DailyUsage(
            localDate = date,
            youtubeSeconds = dailySeconds,
            updatedAt = instant,
        ),
        gateCycle = GateCycle(
            localDate = date,
            usedSeconds = cycleSeconds,
            pendingTaskId = gateCyclePendingTaskId,
            updatedAt = instant,
        ),
        pendingTask = pendingTask,
        emergencyState = emergencyState,
        detectorState = detectorState,
        youtubeForeground = youtubeForeground,
    )

    private fun pendingTask(id: String) = PendingTask(
        id = id,
        operation = ArithmeticOperation.ADD,
        leftOperand = 2,
        rightOperand = 3,
        expectedAnswer = 5,
        createdAt = instant,
    )

    private fun activeEmergencyState() = EmergencyState(
        activeEvent = EmergencyEvent(
            id = "emergency-1",
            reason = "Нужно срочно посмотреть учебное видео",
            activatedAt = instant,
            activationSource = EmergencyActivationSource.DASHBOARD,
        ),
    )

    private companion object {
        const val SHORTS_LIMIT_SECONDS = 5L * 60L
        const val DAILY_LIMIT_SECONDS = 45L * 60L
    }
}
