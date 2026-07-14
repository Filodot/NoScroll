package com.filodot.noscroll.core.usage.daily

import com.filodot.noscroll.core.model.NormalizedUsageEvent
import com.filodot.noscroll.core.model.UsageEventType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeForegroundReconstructorTest {
    private val reconstructor = YouTubeForegroundReconstructor()
    private val start = Instant.parse("2026-07-14T00:00:00Z")
    private val end = start.plusSeconds(600)

    @Test
    fun `reconstructs multiple unordered YouTube intervals`() {
        val result = reconstructor.reconstruct(
            events = listOf(
                event(300, UsageEventType.ACTIVITY_PAUSED),
                event(200, UsageEventType.ACTIVITY_RESUMED),
                event(60, UsageEventType.ACTIVITY_RESUMED),
                event(120, UsageEventType.ACTIVITY_PAUSED),
            ),
            rangeStart = start,
            rangeEnd = end,
        )

        assertEquals(160, result.totalSeconds)
        assertFalse(result.hasOpenIntervalAtEnd)
    }

    @Test
    fun `another foreground package closes an unpaused YouTube interval`() {
        val result = reconstructor.reconstruct(
            events = listOf(
                event(10, UsageEventType.ACTIVITY_RESUMED),
                event(
                    seconds = 70,
                    type = UsageEventType.ACTIVITY_RESUMED,
                    packageName = "com.example.other",
                ),
            ),
            rangeStart = start,
            rangeEnd = end,
        )

        assertEquals(60, result.totalSeconds)
        assertFalse(result.hasOpenIntervalAtEnd)
    }

    @Test
    fun `first pause infers an interval crossing local midnight`() {
        val result = reconstructor.reconstruct(
            events = listOf(event(45, UsageEventType.ACTIVITY_PAUSED)),
            rangeStart = start,
            rangeEnd = end,
        )

        assertEquals(45, result.totalSeconds)
        assertFalse(result.hasOpenIntervalAtEnd)
    }

    @Test
    fun `known other foreground state prevents midnight inference`() {
        val result = reconstructor.reconstruct(
            events = listOf(
                event(
                    seconds = -10,
                    type = UsageEventType.ACTIVITY_RESUMED,
                    packageName = "com.example.other",
                ),
                event(45, UsageEventType.ACTIVITY_PAUSED),
            ),
            rangeStart = start,
            rangeEnd = end,
        )

        assertEquals(0, result.totalSeconds)
    }

    @Test
    fun `unclosed interval is reconstructed up to range end`() {
        val result = reconstructor.reconstruct(
            events = listOf(event(500, UsageEventType.ACTIVITY_RESUMED)),
            rangeStart = start,
            rangeEnd = end,
        )

        assertEquals(100, result.totalSeconds)
        assertTrue(result.hasOpenIntervalAtEnd)
    }

    @Test
    fun `events outside range and duplicate transitions never create negative time`() {
        val result = reconstructor.reconstruct(
            events = listOf(
                event(30, UsageEventType.ACTIVITY_PAUSED),
                event(20, UsageEventType.ACTIVITY_PAUSED),
                event(700, UsageEventType.ACTIVITY_RESUMED),
                event(100, UsageEventType.ACTIVITY_RESUMED),
                event(90, UsageEventType.ACTIVITY_PAUSED),
                event(100, UsageEventType.ACTIVITY_RESUMED),
                event(110, UsageEventType.ACTIVITY_PAUSED),
            ),
            rangeStart = start,
            rangeEnd = end,
        )

        assertEquals(30, result.totalSeconds)
        assertFalse(result.hasOpenIntervalAtEnd)
    }

    @Test
    fun `invalid or empty range returns zero`() {
        val result = reconstructor.reconstruct(
            events = listOf(event(0, UsageEventType.ACTIVITY_RESUMED)),
            rangeStart = end,
            rangeEnd = start,
        )

        assertEquals(0, result.totalSeconds)
        assertFalse(result.hasOpenIntervalAtEnd)
    }

    private fun event(
        seconds: Long,
        type: UsageEventType,
        packageName: String = YOUTUBE_PACKAGE_NAME,
    ) = NormalizedUsageEvent(
        packageName = packageName,
        type = type,
        timestamp = start.plusSeconds(seconds),
    )
}
