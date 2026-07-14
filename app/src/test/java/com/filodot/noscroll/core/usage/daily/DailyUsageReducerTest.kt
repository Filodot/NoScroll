package com.filodot.noscroll.core.usage.daily

import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.NormalizedUsageEvent
import com.filodot.noscroll.core.model.UsageEventType
import com.filodot.noscroll.core.testing.FakeMonotonicClock
import com.filodot.noscroll.core.testing.FakeWallClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyUsageReducerTest {
    private val instant = Instant.parse("2026-07-14T12:00:00Z")
    private val date = LocalDate.of(2026, 7, 14)

    @Test
    fun `live time pauses and resumes without counting inactive gap`() {
        val fixture = fixture()

        fixture.heartbeat(active = true)
        fixture.advanceAndHeartbeat(Duration.ofSeconds(30), active = false)
        fixture.advanceAndHeartbeat(Duration.ofMinutes(5), active = false)
        fixture.heartbeat(active = true)
        val progress = fixture.advanceAndHeartbeat(Duration.ofSeconds(30), active = true)

        assertEquals(60, progress.usage.youtubeSeconds)
        assertTrue(progress.isYoutubeActive)
    }

    @Test
    fun `reconciliation raises live value when UsageEvents contain more time`() {
        val fixture = fixture()
        fixture.heartbeat(active = true)
        fixture.advanceAndHeartbeat(Duration.ofSeconds(30), active = false)

        val progress = fixture.reducer.reconcile(
            events = listOf(
                event(fixture.wallClock.now().minusSeconds(120), UsageEventType.ACTIVITY_RESUMED),
                event(fixture.wallClock.now(), UsageEventType.ACTIVITY_PAUSED),
            ),
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(120L, progress.reconstructedSeconds)
        assertEquals(120, progress.usage.youtubeSeconds)
    }

    @Test
    fun `reconciliation never lowers a larger live or persisted value`() {
        val fixture = fixture(initialSeconds = 180)

        val progress = fixture.reducer.reconcile(
            events = listOf(
                event(fixture.wallClock.now().minusSeconds(60), UsageEventType.ACTIVITY_RESUMED),
                event(fixture.wallClock.now(), UsageEventType.ACTIVITY_PAUSED),
            ),
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(60L, progress.reconstructedSeconds)
        assertEquals(180, progress.usage.youtubeSeconds)
    }

    @Test
    fun `process restore ignores persisted elapsed realtime before adding new live delta`() {
        val fixture = fixture(
            initialSeconds = 120,
            persistedElapsedMillis = 1_000,
            currentElapsedMillis = 900_000,
        )

        val restored = fixture.heartbeat(active = true)
        val progress = fixture.advanceAndHeartbeat(Duration.ofSeconds(30), active = true)

        assertEquals(120, restored.usage.youtubeSeconds)
        assertEquals(150, progress.usage.youtubeSeconds)
    }

    @Test
    fun `first tick after midnight resets the complete daily record`() {
        val beforeMidnight = Instant.parse("2026-07-14T23:59:50Z")
        val fixture = fixture(
            initialInstant = beforeMidnight,
            initialSeconds = 300,
            initialUsage = DailyUsage(
                localDate = date,
                youtubeSeconds = 300,
                shortsSeconds = 200,
                gatesShown = 2,
                tasksSolved = 1,
                updatedAt = beforeMidnight,
            ),
        )
        fixture.heartbeat(active = true)

        val midnight = fixture.advanceAndHeartbeat(Duration.ofSeconds(20), active = true)
        val nextTick = fixture.advanceAndHeartbeat(Duration.ofSeconds(1), active = true)

        assertEquals(date.plusDays(1), midnight.usage.localDate)
        assertEquals(0, midnight.usage.youtubeSeconds)
        assertEquals(0, midnight.usage.shortsSeconds)
        assertEquals(0, midnight.usage.gatesShown)
        assertEquals(1, nextTick.usage.youtubeSeconds)
    }

    @Test
    fun `timezone date change starts a new local record without negative usage`() {
        val nearUtcMidnight = Instant.parse("2026-07-14T23:30:00Z")
        val fixture = fixture(
            initialInstant = nearUtcMidnight,
            initialSeconds = 300,
            initialUsage = DailyUsage(
                localDate = LocalDate.of(2026, 7, 14),
                youtubeSeconds = 300,
                updatedAt = nearUtcMidnight,
            ),
        )

        val progress = fixture.reducer.onHeartbeat(
            active = false,
            zoneId = ZoneId.of("Asia/Tokyo"),
        )

        assertEquals(LocalDate.of(2026, 7, 15), progress.usage.localDate)
        assertEquals(0, progress.usage.youtubeSeconds)
    }

    @Test
    fun `duplicate and rolled back monotonic heartbeats do not double count`() {
        val fixture = fixture(currentElapsedMillis = 10_000)
        fixture.heartbeat(active = true)
        fixture.clock.advanceBy(Duration.ofSeconds(1))

        val first = fixture.heartbeat(active = true)
        val duplicate = fixture.heartbeat(active = true)
        fixture.clock.setElapsedRealtimeMillis(5_000)
        val rollback = fixture.heartbeat(active = true)

        assertEquals(1, first.usage.youtubeSeconds)
        assertEquals(1, duplicate.usage.youtubeSeconds)
        assertEquals(1, rollback.usage.youtubeSeconds)
    }

    @Test
    fun `negative persisted usage is repaired and remains non-negative`() {
        val fixture = fixture(initialSeconds = -100)

        val progress = fixture.reducer.progress()

        assertEquals(0, progress.usage.youtubeSeconds)
        assertFalse(progress.isYoutubeActive)
    }

    @Test
    fun `sub-second live deltas accumulate across inactive periods`() {
        val fixture = fixture()
        fixture.heartbeat(active = true)
        fixture.advanceAndHeartbeat(Duration.ofMillis(400), active = false)
        fixture.advanceAndHeartbeat(Duration.ofMinutes(1), active = false)
        fixture.heartbeat(active = true)

        val progress = fixture.advanceAndHeartbeat(Duration.ofMillis(600), active = true)

        assertEquals(1, progress.usage.youtubeSeconds)
    }

    private fun fixture(
        initialInstant: Instant = instant,
        initialSeconds: Long = 0,
        persistedElapsedMillis: Long? = null,
        currentElapsedMillis: Long = 0,
        initialUsage: DailyUsage = DailyUsage(
            localDate = initialInstant.atZone(ZoneOffset.UTC).toLocalDate(),
            youtubeSeconds = initialSeconds,
            lastUpdatedElapsedMillis = persistedElapsedMillis,
            updatedAt = initialInstant,
        ),
    ): Fixture {
        val wallClock = FakeWallClock(initialInstant)
        val clock = FakeMonotonicClock(currentElapsedMillis)
        return Fixture(
            wallClock = wallClock,
            clock = clock,
            reducer = DailyUsageReducer(
                initialUsage = initialUsage,
                wallClock = wallClock,
                monotonicClock = clock,
            ),
        )
    }

    private fun event(
        timestamp: Instant,
        type: UsageEventType,
    ) = NormalizedUsageEvent(
        packageName = YOUTUBE_PACKAGE_NAME,
        type = type,
        timestamp = timestamp,
    )

    private class Fixture(
        val wallClock: FakeWallClock,
        val clock: FakeMonotonicClock,
        val reducer: DailyUsageReducer,
    ) {
        fun heartbeat(active: Boolean): DailyUsageProgress =
            reducer.onHeartbeat(active = active, zoneId = ZoneOffset.UTC)

        fun advanceAndHeartbeat(
            duration: Duration,
            active: Boolean,
        ): DailyUsageProgress {
            wallClock.advanceBy(duration)
            clock.advanceBy(duration)
            return heartbeat(active)
        }
    }
}
