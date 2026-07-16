package com.filodot.noscroll.monitoring.accessibility

import com.filodot.noscroll.core.model.AccessibilityWindowEvent
import com.filodot.noscroll.core.model.DeviceState
import com.filodot.noscroll.core.model.WindowSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccessibilityAdapterControllerTest {
    @Test
    fun `coalescer preserves scroll marker and never emits more than twice per second`() {
        val scheduler = ManualAccessibilityScanScheduler()
        val emissions = mutableListOf<TimedEvent>()
        val coalescer = AccessibilityEventCoalescer(
            scheduler = scheduler,
            elapsedRealtimeMillis = scheduler::now,
            onEmit = { event -> emissions += TimedEvent(scheduler.now(), event) },
        )

        coalescer.offer(event(type = AccessibilityAdapterController.TYPE_WINDOW_CONTENT_CHANGED, at = 0))
        scheduler.advanceBy(100)
        coalescer.offer(event(type = AccessibilityAdapterController.TYPE_VIEW_SCROLLED, at = 100))
        scheduler.advanceBy(20)
        coalescer.offer(
            event(type = AccessibilityAdapterController.TYPE_WINDOW_CONTENT_CHANGED, at = 120),
        )
        scheduler.advanceBy(29)
        assertTrue(emissions.isEmpty())

        scheduler.advanceBy(1)
        assertEquals(listOf(150L), emissions.map(TimedEvent::emittedAt))
        assertEquals(AccessibilityAdapterController.TYPE_VIEW_SCROLLED, emissions.single().event.eventType)
        assertEquals(120L, emissions.single().event.elapsedRealtimeMillis)

        scheduler.advanceBy(1)
        coalescer.offer(event(type = AccessibilityAdapterController.TYPE_WINDOW_CONTENT_CHANGED, at = 151))
        scheduler.advanceBy(249)
        coalescer.offer(event(type = AccessibilityAdapterController.TYPE_WINDOW_STATE_CHANGED, at = 400))
        scheduler.advanceBy(249)
        assertEquals(1, emissions.size)

        scheduler.advanceBy(1)
        assertEquals(listOf(150L, 650L), emissions.map(TimedEvent::emittedAt))
        assertEquals(AccessibilityAdapterController.TYPE_WINDOW_STATE_CHANGED, emissions.last().event.eventType)
    }

    @Test
    fun `coalescer cancellation invalidates stale callback and resets throttle`() {
        val scheduler = ManualAccessibilityScanScheduler()
        val emissions = mutableListOf<Long>()
        val coalescer = AccessibilityEventCoalescer(
            scheduler = scheduler,
            elapsedRealtimeMillis = scheduler::now,
            onEmit = { emissions += scheduler.now() },
        )

        coalescer.offer(event(at = 0))
        coalescer.cancelAndReset()
        scheduler.advanceBy(1_000)
        assertTrue(emissions.isEmpty())

        coalescer.offer(event(at = 1_000))
        scheduler.advanceBy(150)
        assertEquals(listOf(1_150L), emissions)
    }

    @Test
    fun `coalescer rejects settings that could exceed scan budget`() {
        val scheduler = ManualAccessibilityScanScheduler()

        assertThrows(IllegalArgumentException::class.java) {
            AccessibilityEventCoalescer(
                scheduler = scheduler,
                elapsedRealtimeMillis = scheduler::now,
                onEmit = {},
                coalescingWindowMillis = 99,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccessibilityEventCoalescer(
                scheduler = scheduler,
                elapsedRealtimeMillis = scheduler::now,
                onEmit = {},
                minimumScanIntervalMillis = 499,
            )
        }
    }

    @Test
    fun `controller exposes only supported YouTube events`() = runTest {
        val scheduler = ManualAccessibilityScanScheduler()
        val controller = controller(scheduler)
        val received = mutableListOf<AccessibilityWindowEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            controller.events.collect { event -> received += event }
        }
        controller.onServiceConnected()

        assertFalse(
            controller.onAccessibilityEvent(
                packageName = "com.example.overlay",
                eventType = AccessibilityAdapterController.TYPE_WINDOW_STATE_CHANGED,
                elapsedRealtimeMillis = 0,
            ),
        )
        assertFalse(
            controller.onAccessibilityEvent(
                packageName = AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME,
                eventType = 1,
                elapsedRealtimeMillis = 1,
            ),
        )
        assertTrue(
            controller.onAccessibilityEvent(
                packageName = AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME,
                eventType = AccessibilityAdapterController.TYPE_WINDOWS_CHANGED,
                elapsedRealtimeMillis = 2,
            ),
        )

        scheduler.advanceBy(150)
        runCurrent()
        assertEquals(1, received.size)
        assertEquals(AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME, received.single().packageName)
        assertEquals(AccessibilityAdapterController.TYPE_WINDOWS_CHANGED, received.single().eventType)
    }

    @Test
    fun `controller publishes foreground and screen signals without raw event data`() {
        val scheduler = ManualAccessibilityScanScheduler()
        var screen = ScreenStateSample(screenInteractive = false, deviceUnlocked = false)
        val controller = controller(scheduler, screenProvider = { screen })

        controller.onServiceConnected()
        assertEquals(DeviceState(false, false, null), controller.state.value)
        assertFalse(
            controller.onAccessibilityEvent(
                AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME,
                AccessibilityAdapterController.TYPE_WINDOW_STATE_CHANGED,
                0,
            ),
        )

        screen = ScreenStateSample(screenInteractive = true, deviceUnlocked = true)
        controller.onScreenStateChanged()
        assertEquals(DeviceState(true, true, null), controller.state.value)
        assertTrue(
            controller.onAccessibilityEvent(
                AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME,
                AccessibilityAdapterController.TYPE_WINDOW_STATE_CHANGED,
                1,
            ),
        )
        assertEquals(
            DeviceState(true, true, AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME),
            controller.state.value,
        )

        screen = ScreenStateSample(screenInteractive = false, deviceUnlocked = false)
        controller.onScreenStateChanged()
        assertEquals(DeviceState(false, false, null), controller.state.value)
    }

    @Test
    fun `interrupt drops pending scan and reconnect starts cleanly`() = runTest {
        val scheduler = ManualAccessibilityScanScheduler()
        val controller = controller(scheduler)
        val received = mutableListOf<AccessibilityWindowEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            controller.events.collect { event -> received += event }
        }
        controller.onServiceConnected()
        assertTrue(
            controller.onAccessibilityEvent(
                AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME,
                AccessibilityAdapterController.TYPE_WINDOW_CONTENT_CHANGED,
                0,
            ),
        )

        controller.onServiceInterrupted()
        scheduler.advanceBy(1_000)
        runCurrent()
        assertTrue(received.isEmpty())
        assertFalse(
            controller.onAccessibilityEvent(
                AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME,
                AccessibilityAdapterController.TYPE_WINDOW_CONTENT_CHANGED,
                1_000,
            ),
        )

        controller.onServiceConnected()
        assertTrue(
            controller.onAccessibilityEvent(
                AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME,
                AccessibilityAdapterController.TYPE_WINDOW_CONTENT_CHANGED,
                1_001,
            ),
        )
        scheduler.advanceBy(150)
        runCurrent()
        assertEquals(1, received.size)
    }

    @Test
    fun `snapshot capture fails open before connection after revoke and on provider failure`() = runTest {
        val scheduler = ManualAccessibilityScanScheduler()
        var captures = 0
        var failCapture = false
        val controller = AccessibilityAdapterController(
            scheduler = scheduler,
            elapsedRealtimeMillis = scheduler::now,
            screenStateProvider = { ScreenStateSample(true, true) },
            snapshotCapture = { requestedEvent ->
                captures += 1
                if (failCapture) throw SecurityException("revoked")
                WindowSnapshot(
                    packageName = requestedEvent.packageName,
                    eventType = requestedEvent.eventType,
                    capturedAtElapsedMillis = requestedEvent.elapsedRealtimeMillis,
                )
            },
        )
        val event = event()

        assertNull(controller.capture(event))
        assertEquals(0, captures)
        controller.onServiceConnected()
        assertEquals(event.elapsedRealtimeMillis, controller.capture(event)?.capturedAtElapsedMillis)
        assertEquals(1, captures)

        failCapture = true
        assertNull(controller.capture(event))
        assertNull(controller.state.value.foregroundPackage)
        controller.onServiceInterrupted()
        assertNull(controller.capture(event))
        assertEquals(2, captures)
    }

    @Test
    fun `foreign snapshot returned by provider is discarded`() = runTest {
        val scheduler = ManualAccessibilityScanScheduler()
        val controller = AccessibilityAdapterController(
            scheduler = scheduler,
            elapsedRealtimeMillis = scheduler::now,
            screenStateProvider = { ScreenStateSample(true, true) },
            snapshotCapture = {
                WindowSnapshot(
                    packageName = "com.example.foreign",
                    eventType = it.eventType,
                    capturedAtElapsedMillis = it.elapsedRealtimeMillis,
                )
            },
        )
        controller.onServiceConnected()

        assertNull(controller.capture(event()))
        assertNull(controller.state.value.foregroundPackage)
    }

    @Test
    fun `revoke during capture cannot restore stale foreground state`() = runTest {
        val scheduler = ManualAccessibilityScanScheduler()
        val captureStarted = CompletableDeferred<Unit>()
        val captureResult = CompletableDeferred<WindowSnapshot>()
        val controller = AccessibilityAdapterController(
            scheduler = scheduler,
            elapsedRealtimeMillis = scheduler::now,
            screenStateProvider = { ScreenStateSample(true, true) },
            snapshotCapture = {
                captureStarted.complete(Unit)
                captureResult.await()
            },
        )
        controller.onServiceConnected()
        val event = event()
        val result = async { controller.capture(event) }
        captureStarted.await()

        controller.onServiceInterrupted()
        captureResult.complete(
            WindowSnapshot(
                packageName = AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME,
                eventType = event.eventType,
                capturedAtElapsedMillis = event.elapsedRealtimeMillis,
            ),
        )

        assertNull(result.await())
        assertNull(controller.state.value.foregroundPackage)
    }

    private fun controller(
        scheduler: ManualAccessibilityScanScheduler,
        screenProvider: () -> ScreenStateSample = { ScreenStateSample(true, true) },
    ) = AccessibilityAdapterController(
        scheduler = scheduler,
        elapsedRealtimeMillis = scheduler::now,
        screenStateProvider = screenProvider,
        snapshotCapture = { null },
    )

    private fun event(
        type: Int = AccessibilityAdapterController.TYPE_WINDOW_CONTENT_CHANGED,
        at: Long = 0,
    ) = AccessibilityWindowEvent(
        packageName = AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME,
        eventType = type,
        elapsedRealtimeMillis = at,
    )

    private data class TimedEvent(
        val emittedAt: Long,
        val event: AccessibilityWindowEvent,
    )
}

private class ManualAccessibilityScanScheduler : AccessibilityScanScheduler {
    private var elapsedMillis = 0L
    private val tasks = mutableListOf<Task>()

    fun now(): Long = elapsedMillis

    override fun schedule(delayMillis: Long, task: () -> Unit): ScheduledScan {
        val scheduledAt = if (elapsedMillis > Long.MAX_VALUE - delayMillis) {
            Long.MAX_VALUE
        } else {
            elapsedMillis + delayMillis
        }
        val scheduled = Task(scheduledAt = scheduledAt, block = task)
        tasks += scheduled
        return ScheduledScan { scheduled.cancelled = true }
    }

    fun advanceBy(deltaMillis: Long) {
        require(deltaMillis >= 0)
        val target = elapsedMillis + deltaMillis
        while (true) {
            val next = tasks
                .filterNot(Task::cancelled)
                .filter { it.scheduledAt <= target }
                .minByOrNull(Task::scheduledAt)
                ?: break
            tasks.remove(next)
            elapsedMillis = next.scheduledAt
            if (!next.cancelled) next.block()
        }
        elapsedMillis = target
    }

    private data class Task(
        val scheduledAt: Long,
        val block: () -> Unit,
        var cancelled: Boolean = false,
    )
}
