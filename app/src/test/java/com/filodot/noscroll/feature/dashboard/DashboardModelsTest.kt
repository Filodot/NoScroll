package com.filodot.noscroll.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardModelsTest {
    @Test
    fun `normal state reports working protection`() {
        val state = DashboardUiState(dateLabel = "14 июля")

        assertEquals(DashboardProtectionStatus.WORKING, state.protectionStatus)
        assertFalse(state.hasUsageAccessProblem)
        assertTrue(state.emergencyAvailable)
    }

    @Test
    fun `emergency is the highest priority dashboard status`() {
        val state = DashboardUiState(
            dateLabel = "14 июля",
            accessibilityEnabled = false,
            emergency = EmergencyUiState(active = true, activeSinceLabel = "14:32"),
        )

        assertEquals(DashboardProtectionStatus.EMERGENCY_BYPASS, state.protectionStatus)
        assertFalse(state.hasUsageAccessProblem)
    }

    @Test
    fun `missing accessibility reports protection error`() {
        val state = DashboardUiState(
            dateLabel = "14 июля",
            accessibilityEnabled = false,
        )

        assertEquals(DashboardProtectionStatus.ACCESSIBILITY_ERROR, state.protectionStatus)
    }

    @Test
    fun `unavailable daily access is reported only while accessibility works`() {
        val unavailable = DashboardUiState(
            dateLabel = "14 июля",
            daily = DailyLimitUiState.Unavailable,
        )
        val requiredAccessMissing = unavailable.copy(accessibilityEnabled = false)

        assertTrue(unavailable.hasUsageAccessProblem)
        assertFalse(requiredAccessMissing.hasUsageAccessProblem)
    }

    @Test
    fun `emergency is disabled only when both configured limits are off`() {
        val allOff = DashboardUiState(
            dateLabel = "14 июля",
            shorts = ShortsLimitUiState.Disabled,
            daily = DailyLimitUiState.Disabled,
        )
        val dailyUnavailable = allOff.copy(daily = DailyLimitUiState.Unavailable)

        assertFalse(allOff.emergencyAvailable)
        assertTrue(dailyUnavailable.emergencyAvailable)
    }

    @Test
    fun `progress clamps negative usage to zero`() {
        val progress = progress(usedSeconds = -30, limitSeconds = 300)

        assertEquals(0f, progress.fraction)
        assertEquals(0L, progress.usedSeconds)
        assertEquals(300L, progress.remainingSeconds)
    }

    @Test
    fun `progress clamps overrun to complete and remaining to zero`() {
        val progress = progress(usedSeconds = 400, limitSeconds = 300)

        assertEquals(1f, progress.fraction)
        assertEquals(400L, progress.usedSeconds)
        assertEquals(0L, progress.remainingSeconds)
    }

    @Test
    fun `progress protects UI against invalid zero limit`() {
        val progress = progress(usedSeconds = 0, limitSeconds = 0)

        assertEquals(0f, progress.fraction)
        assertEquals(1L, progress.limitSeconds)
        assertEquals(1L, progress.remainingSeconds)
    }

    @Test
    fun `countdown formats the documented minutes and seconds`() {
        assertEquals("03:42", formatCountdown(222))
        assertEquals("00:00", formatCountdown(0))
        assertEquals("00:00", formatCountdown(-1))
    }

    @Test
    fun `minute totals use completed minutes and never become negative`() {
        assertEquals(18L, wholeMinutes(18 * 60 + 59L))
        assertEquals(0L, wholeMinutes(59))
        assertEquals(0L, wholeMinutes(-60))
    }
}
