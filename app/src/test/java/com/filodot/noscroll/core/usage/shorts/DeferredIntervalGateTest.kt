package com.filodot.noscroll.core.usage.shorts

import org.junit.Assert.assertEquals
import org.junit.Test

class DeferredIntervalGateTest {
    private val gate = DeferredIntervalGate(nextScrollGuardMillis = 500)

    @Test
    fun `limit arms without interrupting current Short`() {
        assertEquals(
            DeferredIntervalAction.ARMED,
            gate.update(true, shortsConfirmed = true, viewScrolled = false, elapsedMillis = 1_000),
        )
    }

    @Test
    fun `first later confirmed scroll enforces gate`() {
        gate.update(true, shortsConfirmed = true, viewScrolled = false, elapsedMillis = 1_000)

        assertEquals(
            DeferredIntervalAction.ENFORCE,
            gate.update(true, shortsConfirmed = true, viewScrolled = true, elapsedMillis = 1_500),
        )
    }

    @Test
    fun `scroll inside guard window does not interrupt current transition`() {
        gate.update(true, shortsConfirmed = true, viewScrolled = false, elapsedMillis = 1_000)

        assertEquals(
            DeferredIntervalAction.ARMED,
            gate.update(true, shortsConfirmed = true, viewScrolled = true, elapsedMillis = 1_499),
        )
    }

    @Test
    fun `non Shorts scrolling never enforces`() {
        gate.update(true, shortsConfirmed = true, viewScrolled = false, elapsedMillis = 1_000)

        assertEquals(
            DeferredIntervalAction.ARMED,
            gate.update(true, shortsConfirmed = false, viewScrolled = true, elapsedMillis = 2_000),
        )
    }

    @Test
    fun `new interval resets armed state`() {
        gate.update(true, shortsConfirmed = true, viewScrolled = false, elapsedMillis = 1_000)
        gate.update(false, shortsConfirmed = false, viewScrolled = false, elapsedMillis = 2_000)

        assertEquals(
            DeferredIntervalAction.ARMED,
            gate.update(true, shortsConfirmed = true, viewScrolled = true, elapsedMillis = 3_000),
        )
    }

    @Test
    fun `elapsed realtime rollback safely rearms`() {
        gate.update(true, shortsConfirmed = true, viewScrolled = false, elapsedMillis = 1_000)

        assertEquals(
            DeferredIntervalAction.ARMED,
            gate.update(true, shortsConfirmed = true, viewScrolled = true, elapsedMillis = 900),
        )
    }
}
