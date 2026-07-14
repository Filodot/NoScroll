package com.filodot.noscroll.monitoring.usagestats

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.filodot.noscroll.core.contracts.UsageStatsSource
import com.filodot.noscroll.core.model.NormalizedUsageEvent
import com.filodot.noscroll.core.model.UsageEventType
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidUsageStatsSource internal constructor(
    private val accessChecker: UsageAccessChecker,
    private val eventsReader: PlatformUsageEventsReader,
    private val sdkInt: Int,
    private val backgroundDispatcher: CoroutineDispatcher,
) : UsageStatsSource {
    constructor(
        context: Context,
        backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        accessChecker = AndroidUsageAccessChecker(context),
        eventsReader = context.getSystemService(UsageStatsManager::class.java)
            ?.let(::AndroidUsageEventsReader)
            ?: PlatformUsageEventsReader { _, _ -> throw UsageStatsServiceUnavailableException() },
        sdkInt = Build.VERSION.SDK_INT,
        backgroundDispatcher = backgroundDispatcher,
    )

    override suspend fun eventsBetween(
        start: Instant,
        end: Instant,
    ): List<NormalizedUsageEvent> = when (val result = queryEventsBetween(start, end)) {
        is UsageStatsQueryResult.Success -> result.events
        is UsageStatsQueryResult.Failure -> throw UsageStatsSourceException(result.reason)
    }

    suspend fun eventsSinceLocalMidnight(
        now: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<NormalizedUsageEvent> = when (val result = querySinceLocalMidnight(now, zoneId)) {
        is UsageStatsQueryResult.Success -> result.events
        is UsageStatsQueryResult.Failure -> throw UsageStatsSourceException(result.reason)
    }

    suspend fun querySinceLocalMidnight(
        now: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): UsageStatsQueryResult {
        val start = now.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant()
        return queryEventsBetween(start, now)
    }

    suspend fun queryEventsBetween(
        start: Instant,
        end: Instant,
    ): UsageStatsQueryResult = withContext(backgroundDispatcher) {
        if (end.isBefore(start)) {
            return@withContext UsageStatsQueryResult.Failure(
                UsageStatsFailureReason.INVALID_RANGE,
            )
        }

        when (accessChecker.checkAccess()) {
            UsageAccessState.DENIED -> return@withContext UsageStatsQueryResult.Failure(
                UsageStatsFailureReason.PERMISSION_DENIED,
            )

            UsageAccessState.SERVICE_UNAVAILABLE -> return@withContext UsageStatsQueryResult.Failure(
                UsageStatsFailureReason.SERVICE_UNAVAILABLE,
            )

            UsageAccessState.GRANTED -> Unit
        }

        val range = epochMillisRange(start, end)
            ?: return@withContext UsageStatsQueryResult.Failure(
                UsageStatsFailureReason.INVALID_RANGE,
            )
        if (range.first == range.second) {
            return@withContext UsageStatsQueryResult.Success(emptyList())
        }

        val rawEvents = try {
            eventsReader.readEvents(range.first, range.second)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            return@withContext UsageStatsQueryResult.Failure(
                UsageStatsFailureReason.PERMISSION_DENIED,
            )
        } catch (_: UsageStatsServiceUnavailableException) {
            return@withContext UsageStatsQueryResult.Failure(
                UsageStatsFailureReason.SERVICE_UNAVAILABLE,
            )
        } catch (_: RuntimeException) {
            return@withContext UsageStatsQueryResult.Failure(
                UsageStatsFailureReason.QUERY_FAILED,
            )
        }

        UsageStatsQueryResult.Success(
            events = rawEvents.mapNotNull { raw ->
                normalize(raw, range.first, range.second, sdkInt)
            },
        )
    }

    private fun normalize(
        raw: RawUsageEvent,
        startMillis: Long,
        endMillis: Long,
        sdkInt: Int,
    ): NormalizedUsageEvent? {
        if (raw.timestampMillis !in startMillis..endMillis) return null
        val type = UsageEventNormalizer.normalize(raw.eventType, sdkInt) ?: return null
        val rawPackageName = raw.packageName?.takeIf(String::isNotBlank) ?: return null
        val packageName = when {
            rawPackageName == YOUTUBE_PACKAGE_NAME -> YOUTUBE_PACKAGE_NAME
            type == UsageEventType.ACTIVITY_RESUMED -> OTHER_FOREGROUND_PACKAGE
            else -> return null
        }
        return NormalizedUsageEvent(
            packageName = packageName,
            type = type,
            timestamp = Instant.ofEpochMilli(raw.timestampMillis),
        )
    }

    private fun epochMillisRange(start: Instant, end: Instant): Pair<Long, Long>? = try {
        start.toEpochMilli() to end.toEpochMilli()
    } catch (_: ArithmeticException) {
        null
    }

    companion object {
        const val YOUTUBE_PACKAGE_NAME = "com.google.android.youtube"
        const val OTHER_FOREGROUND_PACKAGE = "other_foreground"
    }
}

internal object UsageEventNormalizer {
    // MOVE_TO_FOREGROUND/BACKGROUND on API 26–28 and ACTIVITY_RESUMED/PAUSED on API 29+
    // intentionally share these stable platform values.
    private const val FOREGROUND_OR_RESUMED = 1
    private const val BACKGROUND_OR_PAUSED = 2

    fun normalize(eventType: Int, sdkInt: Int): UsageEventType? {
        if (sdkInt < Build.VERSION_CODES.O) return null
        return when (eventType) {
            FOREGROUND_OR_RESUMED -> UsageEventType.ACTIVITY_RESUMED
            BACKGROUND_OR_PAUSED -> UsageEventType.ACTIVITY_PAUSED
            else -> null
        }
    }
}
