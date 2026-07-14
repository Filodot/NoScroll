package com.filodot.noscroll.core.usage.shorts

import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.testing.FakeMonotonicClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsCycleCounterTest {
    private val date = LocalDate.of(2026, 7, 14)
    private val instant = Instant.parse("2026-07-14T12:00:00Z")

    @Test
    fun `three minutes then exit then two minutes reaches five minute gate`() {
        val fixture = fixture()

        fixture.heartbeat(active = true)
        fixture.advanceAndHeartbeat(Duration.ofMinutes(3), active = true)
        fixture.heartbeat(active = false)
        fixture.advanceAndHeartbeat(Duration.ofMinutes(20), active = false)
        fixture.heartbeat(active = true)
        val progress = fixture.advanceAndHeartbeat(Duration.ofMinutes(2), active = true)

        assertEquals(300, progress.cycle.usedSeconds)
        assertEquals(0L, progress.remainingSeconds)
        assertTrue(progress.isGateDue)
    }

    @Test
    fun `screen off heartbeat pauses accrual until active again`() {
        val fixture = fixture()

        fixture.heartbeat(active = true)
        fixture.advanceAndHeartbeat(Duration.ofSeconds(30), active = false)
        fixture.advanceAndHeartbeat(Duration.ofMinutes(5), active = false)
        fixture.heartbeat(active = true)
        val progress = fixture.advanceAndHeartbeat(Duration.ofSeconds(30), active = true)

        assertEquals(60, progress.cycle.usedSeconds)
        assertFalse(progress.isGateDue)
    }

    @Test
    fun `duplicate and out of order ticks never add time twice`() {
        val fixture = fixture(initialElapsedMillis = 10_000)

        fixture.heartbeat(active = true)
        fixture.clock.advanceBy(Duration.ofSeconds(1))
        val first = fixture.heartbeat(active = true)
        val duplicate = fixture.heartbeat(active = true)
        fixture.clock.setElapsedRealtimeMillis(5_000)
        val rollback = fixture.heartbeat(active = true)

        assertEquals(1, first.cycle.usedSeconds)
        assertEquals(1, duplicate.cycle.usedSeconds)
        assertEquals(1, rollback.cycle.usedSeconds)
    }

    @Test
    fun `sub-second active time is retained across pauses`() {
        val fixture = fixture()

        fixture.heartbeat(active = true)
        fixture.advanceAndHeartbeat(Duration.ofMillis(400), active = false)
        fixture.advanceAndHeartbeat(Duration.ofMinutes(1), active = false)
        fixture.heartbeat(active = true)
        val progress = fixture.advanceAndHeartbeat(Duration.ofMillis(600), active = true)

        assertEquals(1, progress.cycle.usedSeconds)
    }

    @Test
    fun `restored snapshot resumes from persisted seconds without dead process delta`() {
        val clock = FakeMonotonicClock(initialElapsedMillis = 900_000)
        val restored = ShortsCycleCounter(
            initialCycle = cycle(usedSeconds = 180),
            monotonicClock = clock,
        )

        restored.onHeartbeat(heartbeat(active = true), intervalMinutes = 5)
        clock.advanceBy(Duration.ofMinutes(2))
        val progress = restored.onHeartbeat(heartbeat(active = true), intervalMinutes = 5)

        assertEquals(300, progress.cycle.usedSeconds)
        assertTrue(progress.isGateDue)
    }

    @Test
    fun `shorter and longer interval immediately recalculate current progress`() {
        val fixture = fixture(usedSeconds = 180)

        val shorter = fixture.counter.progress(intervalMinutes = 2)
        val longer = fixture.counter.progress(intervalMinutes = 10)

        assertTrue(shorter.isGateDue)
        assertEquals(0L, shorter.remainingSeconds)
        assertFalse(longer.isGateDue)
        assertEquals(420L, longer.remainingSeconds)
        assertEquals(180, longer.cycle.usedSeconds)
    }

    @Test
    fun `invalid interval fails open without changing accumulated time`() {
        val fixture = fixture(usedSeconds = 180)

        val progress = fixture.counter.progress(intervalMinutes = 0)

        assertNull(progress.limitSeconds)
        assertNull(progress.remainingSeconds)
        assertFalse(progress.isGateDue)
        assertEquals(180, progress.cycle.usedSeconds)
    }

    @Test
    fun `successful task resets seconds pending task and monotonic baseline`() {
        val fixture = fixture(usedSeconds = 300, pendingTaskId = "task-1")
        fixture.heartbeat(active = true)
        fixture.clock.advanceBy(Duration.ofMinutes(1))

        val reset = fixture.counter.resetAfterTask(
            localDate = date,
            observedAt = instant.plusSeconds(60),
            intervalMinutes = 5,
        )
        fixture.clock.advanceBy(Duration.ofSeconds(30))
        val progress = fixture.heartbeat(active = true)

        assertEquals(0, reset.cycle.usedSeconds)
        assertNull(reset.cycle.pendingTaskId)
        assertEquals(30, progress.cycle.usedSeconds)
    }

    @Test
    fun `first heartbeat on a new local date resets cycle and starts a fresh baseline`() {
        val fixture = fixture(usedSeconds = 299, pendingTaskId = "task-1")
        fixture.heartbeat(active = true)
        fixture.clock.advanceBy(Duration.ofSeconds(30))

        val midnight = fixture.counter.onHeartbeat(
            heartbeat = heartbeat(active = true, localDate = date.plusDays(1)),
            intervalMinutes = 5,
        )
        fixture.clock.advanceBy(Duration.ofSeconds(1))
        val nextTick = fixture.counter.onHeartbeat(
            heartbeat = heartbeat(active = true, localDate = date.plusDays(1)),
            intervalMinutes = 5,
        )

        assertEquals(date.plusDays(1), midnight.cycle.localDate)
        assertEquals(0, midnight.cycle.usedSeconds)
        assertNull(midnight.cycle.pendingTaskId)
        assertEquals(1, nextTick.cycle.usedSeconds)
    }

    @Test
    fun `negative persisted seconds are repaired on restore`() {
        val fixture = fixture(usedSeconds = -50)

        assertEquals(0, fixture.counter.progress(intervalMinutes = 5).cycle.usedSeconds)
    }

    private fun fixture(
        usedSeconds: Long = 0,
        pendingTaskId: String? = null,
        initialElapsedMillis: Long = 0,
    ): Fixture {
        val clock = FakeMonotonicClock(initialElapsedMillis)
        return Fixture(
            clock = clock,
            counter = ShortsCycleCounter(
                initialCycle = cycle(usedSeconds, pendingTaskId),
                monotonicClock = clock,
            ),
        )
    }

    private fun cycle(
        usedSeconds: Long,
        pendingTaskId: String? = null,
    ) = GateCycle(
        localDate = date,
        usedSeconds = usedSeconds,
        pendingTaskId = pendingTaskId,
        updatedAt = instant,
    )

    private fun heartbeat(
        active: Boolean,
        localDate: LocalDate = date,
    ) = ShortsHeartbeat(
        active = active,
        localDate = localDate,
        observedAt = instant,
    )

    private inner class Fixture(
        val clock: FakeMonotonicClock,
        val counter: ShortsCycleCounter,
    ) {
        fun heartbeat(active: Boolean): ShortsCycleProgress =
            counter.onHeartbeat(this@ShortsCycleCounterTest.heartbeat(active), INTERVAL_MINUTES)

        fun advanceAndHeartbeat(
            duration: Duration,
            active: Boolean,
        ): ShortsCycleProgress {
            clock.advanceBy(duration)
            return heartbeat(active)
        }
    }

    private companion object {
        const val INTERVAL_MINUTES = 5
    }
}
