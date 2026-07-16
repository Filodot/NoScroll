package com.filodot.noscroll.core.usage.shorts

import com.filodot.noscroll.core.model.ShortsDetectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class DeferredIntervalGateTest {
    private val gate = DeferredIntervalGate(nextScrollGuardMillis = 500)

    @Test
    fun `limit arms without interrupting current Short`() {
        assertEquals(
            DeferredIntervalAction.ARMED,
            gate.update(
                true,
                shortsState = ShortsDetectionState.SHORTS_CONFIRMED,
                viewScrolled = false,
                elapsedMillis = 1_000,
            ),
        )
    }

    @Test
    fun `first later confirmed scroll enforces gate`() {
        gate.update(
            true,
            ShortsDetectionState.SHORTS_CONFIRMED,
            viewScrolled = false,
            elapsedMillis = 1_000,
        )

        assertEquals(
            DeferredIntervalAction.ENFORCE,
            gate.update(
                true,
                ShortsDetectionState.SHORTS_CONFIRMED,
                viewScrolled = true,
                elapsedMillis = 1_500,
            ),
        )
    }

    @Test
    fun `scroll inside guard window does not interrupt current transition`() {
        gate.update(true, ShortsDetectionState.SHORTS_CONFIRMED, false, 1_000)

        assertEquals(
            DeferredIntervalAction.ARMED,
            gate.update(true, ShortsDetectionState.SHORTS_CONFIRMED, true, 1_499),
        )
    }

    @Test
    fun `non Shorts scrolling never enforces`() {
        gate.update(true, ShortsDetectionState.SHORTS_CONFIRMED, false, 1_000)

        assertEquals(
            DeferredIntervalAction.ARMED,
            gate.update(true, ShortsDetectionState.NOT_SHORTS, true, 2_000),
        )
    }

    @Test
    fun `new interval resets armed state`() {
        gate.update(true, ShortsDetectionState.SHORTS_CONFIRMED, false, 1_000)
        gate.update(false, ShortsDetectionState.NOT_SHORTS, false, 2_000)

        assertEquals(
            DeferredIntervalAction.ARMED,
            gate.update(true, ShortsDetectionState.SHORTS_CONFIRMED, true, 3_000),
        )
    }

    @Test
    fun `elapsed realtime rollback safely rearms`() {
        gate.update(true, ShortsDetectionState.SHORTS_CONFIRMED, false, 1_000)

        assertEquals(
            DeferredIntervalAction.ARMED,
            gate.update(true, ShortsDetectionState.SHORTS_CONFIRMED, true, 900),
        )
    }

    @Test
    fun `scroll still enforces when swipe snapshot becomes temporarily unknown`() {
        gate.update(true, ShortsDetectionState.SHORTS_CONFIRMED, false, 1_000)

        assertEquals(
            DeferredIntervalAction.ENFORCE,
            gate.update(true, ShortsDetectionState.UNKNOWN, true, 1_500),
        )
    }

    @Test
    fun `two minute fail safe enforces without a reported scroll`() {
        gate.update(true, ShortsDetectionState.SHORTS_CONFIRMED, false, 1_000)

        assertEquals(
            DeferredIntervalAction.ARMED,
            gate.update(true, ShortsDetectionState.UNKNOWN, false, 120_999),
        )
        assertEquals(
            DeferredIntervalAction.ENFORCE,
            gate.update(true, ShortsDetectionState.UNKNOWN, false, 121_000),
        )
    }

    @Test
    fun `definite Shorts exit cancels fail safe until Shorts is confirmed again`() {
        gate.update(true, ShortsDetectionState.SHORTS_CONFIRMED, false, 1_000)
        gate.update(true, ShortsDetectionState.NOT_SHORTS, false, 2_000)

        assertEquals(
            DeferredIntervalAction.ARMED,
            gate.update(true, ShortsDetectionState.UNKNOWN, false, 121_000),
        )
    }
}
