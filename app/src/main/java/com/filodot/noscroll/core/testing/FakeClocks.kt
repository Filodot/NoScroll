package com.filodot.noscroll.core.testing

import com.filodot.noscroll.core.contracts.MonotonicClock
import com.filodot.noscroll.core.contracts.WallClock
import java.time.Duration
import java.time.Instant

class FakeMonotonicClock(
    initialElapsedMillis: Long = 0,
) : MonotonicClock {
    private var currentElapsedMillis = initialElapsedMillis

    override fun elapsedRealtimeMillis(): Long = currentElapsedMillis

    fun advanceBy(duration: Duration) {
        currentElapsedMillis += duration.toMillis()
    }
}

class FakeWallClock(
    initialInstant: Instant = Instant.EPOCH,
) : WallClock {
    private var currentInstant = initialInstant

    override fun now(): Instant = currentInstant

    fun advanceBy(duration: Duration) {
        currentInstant = currentInstant.plus(duration)
    }

    fun set(instant: Instant) {
        currentInstant = instant
    }
}
