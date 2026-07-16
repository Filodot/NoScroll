package com.filodot.noscroll.monitoring.accessibility

import android.os.Handler
import com.filodot.noscroll.core.model.AccessibilityWindowEvent
import kotlin.math.max

internal fun interface ScheduledScan {
    fun cancel()
}

internal fun interface AccessibilityScanScheduler {
    fun schedule(delayMillis: Long, task: () -> Unit): ScheduledScan
}

internal class HandlerAccessibilityScanScheduler(
    private val handler: Handler,
) : AccessibilityScanScheduler {
    override fun schedule(delayMillis: Long, task: () -> Unit): ScheduledScan {
        val runnable = Runnable(task)
        handler.postDelayed(runnable, delayMillis.coerceAtLeast(0L))
        return ScheduledScan { handler.removeCallbacks(runnable) }
    }
}

/**
 * Coalesces bursts and enforces a hard scan-rate ceiling. A scroll marker is preserved until
 * emission because YouTube normally follows it with lower-value content-change events.
 * This class never retains an Android accessibility node or any UI text.
 */
internal class AccessibilityEventCoalescer(
    private val scheduler: AccessibilityScanScheduler,
    private val elapsedRealtimeMillis: () -> Long,
    private val onEmit: (AccessibilityWindowEvent) -> Unit,
    private val coalescingWindowMillis: Long = DEFAULT_COALESCING_WINDOW_MILLIS,
    private val minimumScanIntervalMillis: Long = DEFAULT_MINIMUM_SCAN_INTERVAL_MILLIS,
) {
    private val lock = Any()
    private var pendingEvent: AccessibilityWindowEvent? = null
    private var scheduledScan: ScheduledScan? = null
    private var lastEmissionAtMillis: Long? = null
    private var generation = 0L

    init {
        require(coalescingWindowMillis in MIN_COALESCING_WINDOW_MILLIS..MAX_COALESCING_WINDOW_MILLIS)
        require(minimumScanIntervalMillis >= MINIMUM_ALLOWED_SCAN_INTERVAL_MILLIS)
    }

    fun offer(event: AccessibilityWindowEvent) {
        synchronized(lock) {
            pendingEvent = mergePendingEvent(pendingEvent, event)
            if (scheduledScan != null) return

            val now = elapsedRealtimeMillis().coerceAtLeast(0L)
            val coalescedAt = saturatedAdd(now, coalescingWindowMillis)
            val throttledAt = lastEmissionAtMillis
                ?.let { saturatedAdd(it, minimumScanIntervalMillis) }
                ?: now
            val runAt = max(coalescedAt, throttledAt)
            val token = ++generation
            scheduledScan = scheduler.schedule((runAt - now).coerceAtLeast(0L)) {
                emitPending(token)
            }
        }
    }

    private fun mergePendingEvent(
        current: AccessibilityWindowEvent?,
        incoming: AccessibilityWindowEvent,
    ): AccessibilityWindowEvent = when {
        current == null -> incoming
        incoming.eventType == AccessibilityAdapterController.TYPE_VIEW_SCROLLED -> incoming
        current.eventType == AccessibilityAdapterController.TYPE_VIEW_SCROLLED -> current.copy(
            elapsedRealtimeMillis = max(
                current.elapsedRealtimeMillis,
                incoming.elapsedRealtimeMillis,
            ),
        )
        else -> incoming
    }

    fun cancelAndReset() {
        val task = synchronized(lock) {
            generation += 1
            pendingEvent = null
            lastEmissionAtMillis = null
            scheduledScan.also { scheduledScan = null }
        }
        task?.cancel()
    }

    private fun emitPending(token: Long) {
        val event = synchronized(lock) {
            if (token != generation) return
            scheduledScan = null
            pendingEvent.also {
                pendingEvent = null
                if (it != null) {
                    lastEmissionAtMillis = elapsedRealtimeMillis().coerceAtLeast(0L)
                }
            }
        }
        event?.let(onEmit)
    }

    private fun saturatedAdd(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    companion object {
        const val DEFAULT_COALESCING_WINDOW_MILLIS = 150L
        const val DEFAULT_MINIMUM_SCAN_INTERVAL_MILLIS = 500L
        private const val MIN_COALESCING_WINDOW_MILLIS = 100L
        private const val MAX_COALESCING_WINDOW_MILLIS = 250L
        private const val MINIMUM_ALLOWED_SCAN_INTERVAL_MILLIS = 500L
    }
}
