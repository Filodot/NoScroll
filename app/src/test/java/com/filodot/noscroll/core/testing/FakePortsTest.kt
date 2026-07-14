package com.filodot.noscroll.core.testing

import com.filodot.noscroll.core.model.AccessibilityWindowEvent
import com.filodot.noscroll.core.model.ArithmeticOperation
import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.EmergencyActivationSource
import com.filodot.noscroll.core.model.EmergencyEvent
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.model.OverlayState
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.model.UserSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePortsTest {
    private val instant = Instant.parse("2026-07-14T00:00:00Z")
    private val date = LocalDate.of(2026, 7, 14)

    @Test
    fun `settings repository exposes defaults and saves replacement`() = runTest {
        val repository = InMemorySettingsRepository()

        assertEquals(5, repository.settings.value.shortsIntervalMinutes)
        assertEquals(45, repository.settings.value.dailyLimitMinutes)

        repository.save(UserSettings(shortsIntervalMinutes = 10, dailyLimitMinutes = 90))

        assertEquals(10, repository.settings.value.shortsIntervalMinutes)
        assertEquals(90, repository.settings.value.dailyLimitMinutes)
    }

    @Test
    fun `usage and task repositories round trip contract models`() = runTest {
        val daily = DailyUsage(localDate = date, updatedAt = instant)
        val cycle = GateCycle(localDate = date, updatedAt = instant)
        val usageRepository = InMemoryUsageRepository(daily, cycle)
        val taskRepository = InMemoryTaskRepository()
        val task = PendingTask(
            id = "task-1",
            operation = ArithmeticOperation.ADD,
            leftOperand = 2,
            rightOperand = 3,
            expectedAnswer = 5,
            createdAt = instant,
        )

        usageRepository.saveDailyUsage(daily.copy(youtubeSeconds = 30))
        taskRepository.save(task)

        assertEquals(30, usageRepository.dailyUsage.value.youtubeSeconds)
        assertEquals(task, taskRepository.pendingTask.value)

        taskRepository.clear(task.id)
        assertNull(taskRepository.pendingTask.value)
    }

    @Test
    fun `emergency repository preserves active event when history is deleted`() = runTest {
        val repository = InMemoryEmergencyRepository()
        val active = EmergencyEvent(
            id = "emergency-1",
            reason = "Нужно ответить на срочный звонок",
            activatedAt = instant,
            activationSource = EmergencyActivationSource.DASHBOARD,
        )

        repository.activate(active)
        repository.deleteHistory()

        assertTrue(repository.state.value.isActive)
        assertEquals(listOf(active), repository.history.first())

        repository.deactivate(active.copy(deactivatedAt = instant.plusSeconds(60)))
        assertFalse(repository.state.value.isActive)
    }

    @Test
    fun `overlay host records state transitions`() = runTest {
        val host = RecordingOverlayHost()

        host.show(OverlayState.TaskGate("task-1"))
        host.hide()

        assertEquals(
            listOf(OverlayState.TaskGate("task-1"), OverlayState.Hidden),
            host.recordedStates,
        )
        assertEquals(OverlayState.Hidden, host.state.value)
    }

    @Test
    fun `fake clocks and accessibility source are deterministic`() = runTest {
        val monotonicClock = FakeMonotonicClock(1_000)
        val wallClock = FakeWallClock(instant)
        val source = FakeAccessibilityEventSource()
        val event = AccessibilityWindowEvent(
            packageName = "com.google.android.youtube",
            eventType = 32,
            elapsedRealtimeMillis = 6_000,
        )
        val received = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            source.events.first()
        }

        monotonicClock.advanceBy(Duration.ofSeconds(5))
        wallClock.advanceBy(Duration.ofMinutes(1))
        source.emit(event)

        assertEquals(6_000, monotonicClock.elapsedRealtimeMillis())
        assertEquals(instant.plusSeconds(60), wallClock.now())
        assertEquals(event, received.await())
    }
}
