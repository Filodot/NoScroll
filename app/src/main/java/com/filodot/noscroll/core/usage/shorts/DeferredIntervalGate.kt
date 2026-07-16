package com.filodot.noscroll.core.usage.shorts

import com.filodot.noscroll.core.model.ShortsDetectionState

enum class DeferredIntervalAction {
    NONE,
    ARMED,
    ENFORCE,
}

/**
 * Lets the currently visible Short finish after the interval expires and enforces the gate only
 * on a later, confirmed scroll. The small guard window prevents the scroll that happened while
 * the timer crossed its boundary from being mistaken for the user's next swipe.
 */
class DeferredIntervalGate(
    private val nextScrollGuardMillis: Long = DEFAULT_NEXT_SCROLL_GUARD_MILLIS,
    private val forcedExitMillis: Long = DEFAULT_FORCED_EXIT_MILLIS,
) {
    private var armedAtElapsedMillis: Long? = null
    private var shortsObservedSinceArming = false

    init {
        require(nextScrollGuardMillis >= 0)
        require(forcedExitMillis > 0)
    }

    @Synchronized
    fun update(
        intervalDue: Boolean,
        shortsState: ShortsDetectionState,
        viewScrolled: Boolean,
        elapsedMillis: Long,
    ): DeferredIntervalAction {
        val safeElapsed = elapsedMillis.coerceAtLeast(0L)
        if (!intervalDue) {
            reset()
            return DeferredIntervalAction.NONE
        }

        val armedAt = armedAtElapsedMillis
        if (armedAt == null || safeElapsed < armedAt) {
            armedAtElapsedMillis = safeElapsed
            shortsObservedSinceArming = shortsState == ShortsDetectionState.SHORTS_CONFIRMED
            return DeferredIntervalAction.ARMED
        }

        when (shortsState) {
            ShortsDetectionState.SHORTS_CONFIRMED -> shortsObservedSinceArming = true
            ShortsDetectionState.NOT_SHORTS -> shortsObservedSinceArming = false
            ShortsDetectionState.UNKNOWN -> Unit
        }

        val guardPassed = safeElapsed - armedAt >= nextScrollGuardMillis
        val stillInShorts = shortsState == ShortsDetectionState.SHORTS_CONFIRMED ||
            shortsState == ShortsDetectionState.UNKNOWN && shortsObservedSinceArming
        val forcedExitDue = safeElapsed - armedAt >= forcedExitMillis
        return if (stillInShorts && (viewScrolled && guardPassed || forcedExitDue)) {
            DeferredIntervalAction.ENFORCE
        } else {
            DeferredIntervalAction.ARMED
        }
    }

    @Synchronized
    fun reset() {
        armedAtElapsedMillis = null
        shortsObservedSinceArming = false
    }

    companion object {
        const val DEFAULT_NEXT_SCROLL_GUARD_MILLIS = 500L
        const val DEFAULT_FORCED_EXIT_MILLIS = 2 * 60_000L
    }
}
