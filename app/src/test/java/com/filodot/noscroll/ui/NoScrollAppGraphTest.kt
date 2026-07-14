package com.filodot.noscroll.ui

import com.filodot.noscroll.core.testing.InMemoryEmergencyRepository
import com.filodot.noscroll.core.testing.InMemorySettingsRepository
import com.filodot.noscroll.core.testing.InMemoryTaskRepository
import com.filodot.noscroll.core.testing.InMemoryUsageRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoScrollAppGraphTest {
    @Test
    fun `fake graph contains no Android repository adapter`() {
        val graph = NoScrollAppGraph.fake()

        assertTrue(graph.settingsRepository is InMemorySettingsRepository)
        assertTrue(graph.usageRepository is InMemoryUsageRepository)
        assertTrue(graph.taskRepository is InMemoryTaskRepository)
        assertTrue(graph.emergencyRepository is InMemoryEmergencyRepository)
    }

    @Test
    fun `fake graph starts with coherent dashboard and usage values`() {
        val graph = NoScrollAppGraph.fake(onboardingCompleted = true)

        assertTrue(graph.settingsRepository.settings.value.onboardingCompleted)
        assertEquals(18 * 60L, graph.usageRepository.dailyUsage.value.youtubeSeconds)
        assertEquals(12 * 60L, graph.usageRepository.dailyUsage.value.shortsSeconds)
        assertEquals(78L, graph.usageRepository.gateCycle.value.usedSeconds)
        assertFalse(graph.dashboardState.emergency.active)
    }
}
