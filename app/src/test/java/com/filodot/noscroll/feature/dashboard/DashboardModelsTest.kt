package com.filodot.noscroll.feature.dashboard

import com.filodot.noscroll.core.model.DailyUsage
import java.time.Instant
import java.time.LocalDate
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
    fun `stopped monitoring reports protection error even when access remains enabled`() {
        val state = DashboardUiState(
            dateLabel = "14 июля",
            accessibilityEnabled = true,
            monitoringState = DashboardMonitoringState.DISCONNECTED,
        )

        assertEquals(DashboardProtectionStatus.ACCESSIBILITY_ERROR, state.protectionStatus)
    }

    @Test
    fun `recovering monitoring is visible without claiming that protection works`() {
        val state = DashboardUiState(
            dateLabel = "14 июля",
            monitoringState = DashboardMonitoringState.RECOVERING,
        )

        assertEquals(
            DashboardProtectionStatus.MONITORING_RECOVERING,
            state.protectionStatus,
        )
        assertFalse(state.hasUsageAccessProblem)
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
    fun `emergency is disabled only when every configured limit is off`() {
        val allOff = DashboardUiState(
            dateLabel = "14 июля",
            shorts = ShortsLimitUiState.Disabled,
            instagram = AppLimitUiState.Disabled,
            daily = DailyLimitUiState.Disabled,
        )
        val dailyUnavailable = allOff.copy(daily = DailyLimitUiState.Unavailable)

        assertFalse(allOff.emergencyAvailable)
        assertTrue(dailyUnavailable.emergencyAvailable)
        assertTrue(allOff.copy(focusMode = FocusModeUiState(active = true)).emergencyAvailable)
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

    @Test
    fun `statistics do not count Shorts twice and detect a lower weekly average`() {
        val today = LocalDate.of(2026, 8, 9)
        val current = usage(today, youtubeMinutes = 30, shortsMinutes = 20, instagramMinutes = 10)
        val history = listOf(
            usage(today.minusDays(1), youtubeMinutes = 20, instagramMinutes = 10),
            usage(today.minusDays(2), youtubeMinutes = 25, instagramMinutes = 5),
            usage(today.minusDays(8), youtubeMinutes = 50, instagramMinutes = 10),
            usage(today.minusDays(9), youtubeMinutes = 45, instagramMinutes = 15),
        )

        val statistics = buildUsageStatistics(current, history)

        assertEquals(40 * 60L, statistics.todayTotalSeconds)
        assertEquals(20 * 60L, statistics.todayShortsSeconds)
        assertEquals(UsageTrendDirection.IMPROVING, statistics.trend)
        assertEquals(-50, statistics.changePercent)
        assertTrue(statistics.days.first().observed)
        assertFalse(statistics.days.last().observed)
    }

    @Test
    fun `statistics wait for enough observed days before claiming progress`() {
        val today = LocalDate.of(2026, 8, 9)

        val statistics = buildUsageStatistics(
            current = usage(today, youtubeMinutes = 10),
            history = listOf(usage(today.minusDays(1), youtubeMinutes = 20)),
        )

        assertEquals(UsageTrendDirection.NOT_ENOUGH_DATA, statistics.trend)
        assertEquals(null, statistics.changePercent)
    }

    private fun usage(
        date: LocalDate,
        youtubeMinutes: Long,
        shortsMinutes: Long = 0,
        instagramMinutes: Long = 0,
    ) = DailyUsage(
        localDate = date,
        youtubeSeconds = youtubeMinutes * 60,
        shortsSeconds = shortsMinutes * 60,
        instagramSeconds = instagramMinutes * 60,
        updatedAt = Instant.parse("2026-08-09T00:00:00Z"),
    )
}
