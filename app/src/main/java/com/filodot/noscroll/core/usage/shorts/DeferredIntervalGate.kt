package com.filodot.noscroll.core.usage.shorts

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
) {
    private var armedAtElapsedMillis: Long? = null

    init {
        require(nextScrollGuardMillis >= 0)
    }

    @Synchronized
    fun update(
        intervalDue: Boolean,
        shortsConfirmed: Boolean,
        viewScrolled: Boolean,
        elapsedMillis: Long,
    ): DeferredIntervalAction {
        val safeElapsed = elapsedMillis.coerceAtLeast(0L)
        if (!intervalDue) {
            armedAtElapsedMillis = null
            return DeferredIntervalAction.NONE
        }

        val armedAt = armedAtElapsedMillis
        if (armedAt == null || safeElapsed < armedAt) {
            armedAtElapsedMillis = safeElapsed
            return DeferredIntervalAction.ARMED
        }

        val guardPassed = safeElapsed - armedAt >= nextScrollGuardMillis
        return if (shortsConfirmed && viewScrolled && guardPassed) {
            DeferredIntervalAction.ENFORCE
        } else {
            DeferredIntervalAction.ARMED
        }
    }

    @Synchronized
    fun reset() {
        armedAtElapsedMillis = null
    }

    companion object {
        const val DEFAULT_NEXT_SCROLL_GUARD_MILLIS = 500L
    }
}
