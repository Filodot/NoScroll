package com.filodot.noscroll.core.usage.daily

import com.filodot.noscroll.core.contracts.MonotonicClock
import com.filodot.noscroll.core.contracts.WallClock
import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.NormalizedUsageEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

data class DailyUsageProgress(
    val usage: DailyUsage,
    val isYoutubeActive: Boolean,
    val reconstructedSeconds: Long? = null,
)

/**
 * Combines a monotonic live YouTube counter with UsageEvents reconciliation.
 *
 * The first live heartbeat after construction establishes a new elapsed-realtime baseline, so a
 * persisted value is safe to restore after process death or reboot. Reconciliation can raise the
 * value but never lower it within one local date.
 */
class DailyUsageReducer(
    initialUsage: DailyUsage,
    private val wallClock: WallClock,
    private val monotonicClock: MonotonicClock,
    private val reconstructor: YouTubeForegroundReconstructor = YouTubeForegroundReconstructor(),
) {
    private var usage = initialUsage.copy(
        youtubeSeconds = initialUsage.youtubeSeconds.coerceAtLeast(0),
    )
    private var youtubeActive = false
    private var lastHeartbeatElapsedMillis: Long? = null
    private var remainderMillis = 0L

    @Synchronized
    fun onHeartbeat(
        active: Boolean,
        zoneId: ZoneId,
    ): DailyUsageProgress {
        val now = wallClock.now()
        val elapsedMillis = monotonicClock.elapsedRealtimeMillis()
        val localDate = now.atZone(zoneId).toLocalDate()

        if (usage.localDate != localDate) {
            resetForDate(localDate, now, elapsedMillis)
        } else {
            accrueLiveUntil(elapsedMillis, now)
        }

        youtubeActive = active
        lastHeartbeatElapsedMillis = elapsedMillis
        usage = usage.copy(
            lastUpdatedElapsedMillis = elapsedMillis,
            updatedAt = now,
        )
        return progress()
    }

    @Synchronized
    fun reconcile(
        events: List<NormalizedUsageEvent>,
        zoneId: ZoneId,
    ): DailyUsageProgress {
        val now = wallClock.now()
        val elapsedMillis = monotonicClock.elapsedRealtimeMillis()
        val localDate = now.atZone(zoneId).toLocalDate()

        if (usage.localDate != localDate) {
            resetForDate(localDate, now, elapsedMillis)
        } else {
            accrueLiveUntil(elapsedMillis, now)
        }

        val dayStart = localDate.atStartOfDay(zoneId).toInstant()
        val reconstructed = reconstructor.reconstruct(
            events = events,
            rangeStart = dayStart,
            rangeEnd = now,
        )
        usage = usage.copy(
            youtubeSeconds = max(usage.youtubeSeconds, reconstructed.totalSeconds),
            lastUpdatedElapsedMillis = elapsedMillis,
            updatedAt = now,
        )
        lastHeartbeatElapsedMillis = elapsedMillis

        return progress(reconstructed.totalSeconds)
    }

    @Synchronized
    fun progress(): DailyUsageProgress = progress(reconstructedSeconds = null)

    private fun progress(reconstructedSeconds: Long?): DailyUsageProgress =
        DailyUsageProgress(
            usage = usage,
            isYoutubeActive = youtubeActive,
            reconstructedSeconds = reconstructedSeconds,
        )

    private fun resetForDate(
        localDate: LocalDate,
        now: Instant,
        elapsedMillis: Long,
    ) {
        usage = DailyUsage(
            localDate = localDate,
            lastUpdatedElapsedMillis = elapsedMillis,
            updatedAt = now,
        )
        youtubeActive = false
        lastHeartbeatElapsedMillis = elapsedMillis
        remainderMillis = 0
    }

    private fun accrueLiveUntil(elapsedMillis: Long, now: Instant) {
        val previousElapsedMillis = lastHeartbeatElapsedMillis
        if (
            youtubeActive &&
            previousElapsedMillis != null &&
            elapsedMillis > previousElapsedMillis
        ) {
            val totalMillis = saturatingAdd(
                remainderMillis,
                elapsedMillis - previousElapsedMillis,
            )
            val wholeSeconds = totalMillis / MILLIS_PER_SECOND
            remainderMillis = totalMillis % MILLIS_PER_SECOND
            if (wholeSeconds > 0) {
                usage = usage.copy(
                    youtubeSeconds = saturatingAdd(usage.youtubeSeconds, wholeSeconds),
                    updatedAt = now,
                )
            }
        }
        lastHeartbeatElapsedMillis = elapsedMillis
    }
}

private fun saturatingAdd(left: Long, right: Long): Long =
    if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right

private const val MILLIS_PER_SECOND = 1_000L
