package com.filodot.noscroll.core.usage.shorts

import com.filodot.noscroll.core.model.ShortsDetectionState

/**
 * Tracks the reusable admission granted by an entry task separately from interval usage.
 * UNKNOWN detector samples never end a paid session; only stable non-Shorts time does.
 */
class ShortsEntryGate(
    private val grantValidityMillis: Long = DEFAULT_GRANT_VALIDITY_MILLIS,
    private val sessionExitMillis: Long = DEFAULT_SESSION_EXIT_MILLIS,
) {
    private var sessionPaid = false
    private var grantExpiresAtElapsedMillis: Long? = null
    private var inactiveSinceElapsedMillis: Long? = null

    init {
        require(grantValidityMillis > 0)
        require(sessionExitMillis > 0)
    }

    @Synchronized
    fun onTaskSolved(
        elapsedMillis: Long,
        validityMillis: Long = grantValidityMillis,
    ) {
        require(validityMillis > 0)
        sessionPaid = false
        grantExpiresAtElapsedMillis = elapsedMillis.saturatingAdd(validityMillis)
        inactiveSinceElapsedMillis = null
    }

    @Synchronized
    fun onDetection(state: ShortsDetectionState, elapsedMillis: Long) {
        refresh(elapsedMillis)
        when (state) {
            ShortsDetectionState.SHORTS_CONFIRMED -> {
                if (grantExpiresAtElapsedMillis != null) {
                    grantExpiresAtElapsedMillis = null
                    sessionPaid = true
                }
                inactiveSinceElapsedMillis = null
            }

            ShortsDetectionState.NOT_SHORTS -> if (sessionPaid) {
                if (inactiveSinceElapsedMillis == null) {
                    inactiveSinceElapsedMillis = elapsedMillis
                }
            }

            ShortsDetectionState.UNKNOWN -> Unit
        }
    }

    @Synchronized
    fun onYouTubeForegroundLost() {
        sessionPaid = false
        inactiveSinceElapsedMillis = null
    }

    @Synchronized
    fun isPaid(elapsedMillis: Long): Boolean {
        refresh(elapsedMillis)
        return sessionPaid || grantExpiresAtElapsedMillis != null
    }

    @Synchronized
    fun reset() {
        sessionPaid = false
        grantExpiresAtElapsedMillis = null
        inactiveSinceElapsedMillis = null
    }

    private fun refresh(elapsedMillis: Long) {
        val grantExpiry = grantExpiresAtElapsedMillis
        if (grantExpiry != null && elapsedMillis >= grantExpiry) {
            grantExpiresAtElapsedMillis = null
        }
        val inactiveSince = inactiveSinceElapsedMillis
        if (
            sessionPaid &&
            inactiveSince != null &&
            elapsedMillis >= inactiveSince &&
            elapsedMillis - inactiveSince >= sessionExitMillis
        ) {
            sessionPaid = false
            inactiveSinceElapsedMillis = null
        }
    }

    private fun Long.saturatingAdd(other: Long): Long =
        if (other > Long.MAX_VALUE - this) Long.MAX_VALUE else this + other

    companion object {
        const val DEFAULT_GRANT_VALIDITY_MILLIS = 5 * 60_000L
        const val DEFAULT_SESSION_EXIT_MILLIS = 10_000L
    }
}
