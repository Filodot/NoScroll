package com.filodot.noscroll.monitoring.usagestats

import com.filodot.noscroll.core.model.UsageEventType
import com.filodot.noscroll.core.usage.daily.YouTubeForegroundReconstructor
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUsageStatsSourceTest {
    @Test
    fun `permission denied is distinct from empty result and skips platform query`() = runTest {
        val reader = RecordingUsageEventsReader()
        val denied = source(
            accessState = UsageAccessState.DENIED,
            reader = reader,
        )

        assertEquals(
            UsageStatsQueryResult.Failure(UsageStatsFailureReason.PERMISSION_DENIED),
            denied.queryEventsBetween(START, END),
        )
        assertEquals(0, reader.requests.size)

        val empty = source(reader = reader).queryEventsBetween(START, END)
        assertEquals(UsageStatsQueryResult.Success(emptyList()), empty)
        assertEquals(1, reader.requests.size)
    }

    @Test
    fun `legacy and current API event fixtures normalize identically`() = runTest {
        val raw = listOf(
            raw(AndroidUsageStatsSource.YOUTUBE_PACKAGE_NAME, type = 1, offsetSeconds = 1),
            raw(AndroidUsageStatsSource.YOUTUBE_PACKAGE_NAME, type = 2, offsetSeconds = 2),
            raw("com.example.private", type = 1, offsetSeconds = 3),
            raw("com.example.private", type = 2, offsetSeconds = 4),
            raw(AndroidUsageStatsSource.YOUTUBE_PACKAGE_NAME, type = 23, offsetSeconds = 5),
            raw(null, type = 1, offsetSeconds = 6),
        )

        val legacy = requireSuccess(source(reader = RecordingUsageEventsReader(raw), sdkInt = 28))
        val current = requireSuccess(source(reader = RecordingUsageEventsReader(raw), sdkInt = 35))

        assertEquals(legacy, current)
        assertEquals(3, current.size)
        assertEquals(
            listOf(
                UsageEventType.ACTIVITY_RESUMED,
                UsageEventType.ACTIVITY_PAUSED,
                UsageEventType.ACTIVITY_RESUMED,
            ),
            current.map { it.type },
        )
        assertEquals(
            AndroidUsageStatsSource.OTHER_FOREGROUND_PACKAGE,
            current.last().packageName,
        )
        assertFalse(current.toString().contains("com.example.private"))
    }

    @Test
    fun `unsupported API and out-of-range events are ignored`() = runTest {
        val raw = listOf(
            RawUsageEvent(AndroidUsageStatsSource.YOUTUBE_PACKAGE_NAME, 1, START.toEpochMilli() - 1),
            raw(AndroidUsageStatsSource.YOUTUBE_PACKAGE_NAME, 1, 1),
            RawUsageEvent(AndroidUsageStatsSource.YOUTUBE_PACKAGE_NAME, 2, END.toEpochMilli() + 1),
        )

        val unsupported = requireSuccess(
            source(reader = RecordingUsageEventsReader(raw), sdkInt = 25),
        )
        val supported = requireSuccess(
            source(reader = RecordingUsageEventsReader(raw), sdkInt = 26),
        )

        assertTrue(unsupported.isEmpty())
        assertEquals(1, supported.size)
        assertEquals(START.plusSeconds(1), supported.single().timestamp)
    }

    @Test
    fun `open YouTube interval remains available to frozen reconstructor`() = runTest {
        val source = source(
            reader = RecordingUsageEventsReader(
                listOf(raw(AndroidUsageStatsSource.YOUTUBE_PACKAGE_NAME, 1, 60)),
            ),
        )
        val events = source.eventsBetween(START, END)

        val reconstruction = YouTubeForegroundReconstructor().reconstruct(events, START, END)

        assertTrue(reconstruction.hasOpenIntervalAtEnd)
        assertEquals(Duration.between(START.plusSeconds(60), END).seconds, reconstruction.totalSeconds)
    }

    @Test
    fun `revoked access and platform failures map without leaking platform messages`() = runTest {
        val cases = listOf(
            SecurityException("private denied detail") to UsageStatsFailureReason.PERMISSION_DENIED,
            UsageStatsServiceUnavailableException() to UsageStatsFailureReason.SERVICE_UNAVAILABLE,
            IllegalArgumentException("private vendor detail") to UsageStatsFailureReason.QUERY_FAILED,
        )

        cases.forEach { (failure, reason) ->
            val result = source(
                reader = RecordingUsageEventsReader(failure = failure),
            ).queryEventsBetween(START, END)
            assertEquals(UsageStatsQueryResult.Failure(reason), result)
            assertFalse(result.toString().contains("private"))
        }
    }

    @Test
    fun `frozen source throws stable typed exception for a failed query`() {
        val source = source(
            reader = RecordingUsageEventsReader(
                failure = IllegalArgumentException("private vendor detail"),
            ),
        )

        val exception = assertThrows(UsageStatsSourceException::class.java) {
            runBlocking { source.eventsBetween(START, END) }
        }

        assertEquals(UsageStatsFailureReason.QUERY_FAILED, exception.reason)
        assertFalse(exception.message.orEmpty().contains("private"))
    }

    @Test
    fun `invalid range fails before access or platform calls`() = runTest {
        var accessChecks = 0
        val reader = RecordingUsageEventsReader()
        val source = AndroidUsageStatsSource(
            accessChecker = UsageAccessChecker {
                accessChecks += 1
                UsageAccessState.GRANTED
            },
            eventsReader = reader,
            sdkInt = 35,
            backgroundDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )

        assertEquals(
            UsageStatsQueryResult.Failure(UsageStatsFailureReason.INVALID_RANGE),
            source.queryEventsBetween(END, START),
        )
        assertEquals(0, accessChecks)
        assertEquals(0, reader.requests.size)
    }

    @Test
    fun `equal range still checks access but never queries platform`() = runTest {
        val reader = RecordingUsageEventsReader()

        assertEquals(
            UsageStatsQueryResult.Failure(UsageStatsFailureReason.PERMISSION_DENIED),
            source(accessState = UsageAccessState.DENIED, reader = reader)
                .queryEventsBetween(START, START),
        )
        assertEquals(
            UsageStatsQueryResult.Success(emptyList()),
            source(reader = reader).queryEventsBetween(START, START),
        )
        assertTrue(reader.requests.isEmpty())
    }

    @Test
    fun `local midnight uses requested zone including DST transition`() = runTest {
        val reader = RecordingUsageEventsReader()
        val source = source(reader = reader)
        val now = Instant.parse("2026-03-29T12:00:00Z")

        source.querySinceLocalMidnight(now, ZoneId.of("Europe/Berlin"))

        assertEquals(
            Instant.parse("2026-03-28T23:00:00Z").toEpochMilli() to now.toEpochMilli(),
            reader.requests.single(),
        )
    }

    @Test
    fun `platform query runs on injected background dispatcher`() {
        val callerThread = Thread.currentThread().name
        val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "usage-stats-io")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            val reader = RecordingUsageEventsReader()
            val source = AndroidUsageStatsSource(
                accessChecker = UsageAccessChecker { UsageAccessState.GRANTED },
                eventsReader = reader,
                sdkInt = 35,
                backgroundDispatcher = dispatcher,
            )

            runBlocking { source.eventsBetween(START, END) }

            assertTrue(reader.queryThreadName.orEmpty().startsWith("usage-stats-io"))
            assertFalse(callerThread == reader.queryThreadName)
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    private suspend fun requireSuccess(source: AndroidUsageStatsSource) =
        (source.queryEventsBetween(START, END) as UsageStatsQueryResult.Success).events

    private fun source(
        accessState: UsageAccessState = UsageAccessState.GRANTED,
        reader: RecordingUsageEventsReader = RecordingUsageEventsReader(),
        sdkInt: Int = 35,
    ) = AndroidUsageStatsSource(
        accessChecker = UsageAccessChecker { accessState },
        eventsReader = reader,
        sdkInt = sdkInt,
        backgroundDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
    )

    private fun raw(
        packageName: String?,
        type: Int,
        offsetSeconds: Long,
    ) = RawUsageEvent(
        packageName = packageName,
        eventType = type,
        timestampMillis = START.plusSeconds(offsetSeconds).toEpochMilli(),
    )

    companion object {
        private val START = Instant.parse("2026-07-14T00:00:00Z")
        private val END = START.plusSeconds(3_600)
    }
}

private class RecordingUsageEventsReader(
    private val events: List<RawUsageEvent> = emptyList(),
    private val failure: RuntimeException? = null,
) : PlatformUsageEventsReader {
    val requests = mutableListOf<Pair<Long, Long>>()
    var queryThreadName: String? = null

    override fun readEvents(startMillis: Long, endMillis: Long): List<RawUsageEvent> {
        requests += startMillis to endMillis
        queryThreadName = Thread.currentThread().name
        failure?.let { throw it }
        return events
    }
}
