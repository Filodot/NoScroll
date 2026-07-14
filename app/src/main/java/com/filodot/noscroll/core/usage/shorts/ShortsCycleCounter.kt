package com.filodot.noscroll.core.usage.shorts

import com.filodot.noscroll.core.contracts.MonotonicClock
import com.filodot.noscroll.core.model.GateCycle
import java.time.Instant
import java.time.LocalDate
import kotlin.math.max

/**
 * A normalized heartbeat emitted only while the monitoring pipeline can confirm whether Shorts
 * should accrue time. Duration is measured by [MonotonicClock]; wall time is metadata only.
 */
data class ShortsHeartbeat(
    val active: Boolean,
    val localDate: LocalDate,
    val observedAt: Instant,
)

data class ShortsCycleProgress(
    val cycle: GateCycle,
    val isActive: Boolean,
    val limitSeconds: Long?,
    val remainingSeconds: Long?,
    val isGateDue: Boolean,
)

/**
 * Accumulates confirmed active Shorts time across pause/resume boundaries.
 *
 * The persisted [GateCycle] contains only completed seconds. A sub-second remainder is retained
 * in memory and may be lost on process death, intentionally limiting restoration error to less
 * than one second. The first heartbeat after construction establishes a new monotonic baseline,
 * so elapsed-realtime values are never compared across process or device restarts.
 */
class ShortsCycleCounter(
    initialCycle: GateCycle,
    private val monotonicClock: MonotonicClock,
) {
    private var cycle = initialCycle.copy(usedSeconds = initialCycle.usedSeconds.coerceAtLeast(0))
    private var active = false
    private var lastHeartbeatElapsedMillis: Long? = null
    private var remainderMillis = 0L

    @Synchronized
    fun onHeartbeat(
        heartbeat: ShortsHeartbeat,
        intervalMinutes: Int,
    ): ShortsCycleProgress {
        val elapsedMillis = monotonicClock.elapsedRealtimeMillis()

        if (cycle.localDate != heartbeat.localDate) {
            resetCycle(
                localDate = heartbeat.localDate,
                observedAt = heartbeat.observedAt,
                elapsedMillis = elapsedMillis,
                keepActive = false,
            )
        } else {
            val previousElapsedMillis = lastHeartbeatElapsedMillis
            if (active && previousElapsedMillis != null && elapsedMillis > previousElapsedMillis) {
                addActiveMillis(elapsedMillis - previousElapsedMillis, heartbeat.observedAt)
            }
        }

        active = heartbeat.active
        lastHeartbeatElapsedMillis = elapsedMillis
        return progress(intervalMinutes)
    }

    /** Resets the cycle atomically when a task grants a fresh Shorts interval. */
    @Synchronized
    fun resetAfterTask(
        localDate: LocalDate,
        observedAt: Instant,
        intervalMinutes: Int,
    ): ShortsCycleProgress {
        resetCycle(
            localDate = localDate,
            observedAt = observedAt,
            elapsedMillis = monotonicClock.elapsedRealtimeMillis(),
            keepActive = active,
        )
        return progress(intervalMinutes)
    }

    /** Re-evaluates the existing used time after a settings change without mutating it. */
    @Synchronized
    fun progress(intervalMinutes: Int): ShortsCycleProgress {
        val limitSeconds = intervalMinutes.toPositiveSeconds()
        val remainingSeconds = limitSeconds?.let { limit ->
            max(limit - cycle.usedSeconds, 0L)
        }
        return ShortsCycleProgress(
            cycle = cycle,
            isActive = active,
            limitSeconds = limitSeconds,
            remainingSeconds = remainingSeconds,
            isGateDue = limitSeconds != null && cycle.usedSeconds >= limitSeconds,
        )
    }

    private fun resetCycle(
        localDate: LocalDate,
        observedAt: Instant,
        elapsedMillis: Long,
        keepActive: Boolean,
    ) {
        cycle = cycle.copy(
            localDate = localDate,
            usedSeconds = 0,
            pendingTaskId = null,
            updatedAt = observedAt,
        )
        active = keepActive
        lastHeartbeatElapsedMillis = elapsedMillis
        remainderMillis = 0
    }

    private fun addActiveMillis(deltaMillis: Long, observedAt: Instant) {
        val totalMillis = saturatingAdd(remainderMillis, deltaMillis)
        val wholeSeconds = totalMillis / MILLIS_PER_SECOND
        remainderMillis = totalMillis % MILLIS_PER_SECOND

        if (wholeSeconds > 0) {
            cycle = cycle.copy(
                usedSeconds = saturatingAdd(cycle.usedSeconds, wholeSeconds),
                updatedAt = observedAt,
            )
        }
    }
}

private fun Int.toPositiveSeconds(): Long? =
    takeIf { it > 0 }?.toLong()?.times(SECONDS_PER_MINUTE)

private fun saturatingAdd(left: Long, right: Long): Long =
    if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
