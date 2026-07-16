package com.filodot.noscroll.core.usage.shorts

import com.filodot.noscroll.core.model.ShortsDetectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsEntryGateTest {
    private val gate = ShortsEntryGate(grantValidityMillis = 300, sessionExitMillis = 10)

    @Test
    fun `entry is unpaid until task is solved`() {
        assertFalse(gate.isPaid(0))

        gate.onTaskSolved(10)

        assertTrue(gate.isPaid(10))
    }

    @Test
    fun `confirmed Shorts consumes grant into paid session`() {
        gate.onTaskSolved(10)
        gate.onDetection(ShortsDetectionState.SHORTS_CONFIRMED, 20)

        assertTrue(gate.isPaid(1_000))
    }

    @Test
    fun `stable non Shorts interval ends session at timeout`() {
        gate.onTaskSolved(0)
        gate.onDetection(ShortsDetectionState.SHORTS_CONFIRMED, 1)
        gate.onDetection(ShortsDetectionState.NOT_SHORTS, 2)

        assertTrue(gate.isPaid(11))
        assertFalse(gate.isPaid(12))
    }

    @Test
    fun `unknown samples do not end paid session`() {
        gate.onTaskSolved(0)
        gate.onDetection(ShortsDetectionState.SHORTS_CONFIRMED, 1)
        gate.onDetection(ShortsDetectionState.UNKNOWN, 2)

        assertTrue(gate.isPaid(10_000))
    }

    @Test
    fun `unused entry grant expires`() {
        gate.onTaskSolved(0)

        assertTrue(gate.isPaid(299))
        assertFalse(gate.isPaid(300))
    }

    @Test
    fun `leaving YouTube ends active session but preserves unused grant`() {
        gate.onTaskSolved(0)
        gate.onYouTubeForegroundLost()
        assertTrue(gate.isPaid(1))

        gate.onDetection(ShortsDetectionState.SHORTS_CONFIRMED, 2)
        gate.onYouTubeForegroundLost()
        assertFalse(gate.isPaid(3))
    }
}
