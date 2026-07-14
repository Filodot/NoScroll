package com.filodot.noscroll.core.contracts

import com.filodot.noscroll.core.model.AccessibilityWindowEvent
import com.filodot.noscroll.core.model.DetectionResult
import com.filodot.noscroll.core.model.DeviceState
import com.filodot.noscroll.core.model.NormalizedUsageEvent
import com.filodot.noscroll.core.model.WindowSnapshot
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AccessibilityEventSource {
    val events: Flow<AccessibilityWindowEvent>
}

fun interface WindowSnapshotProvider {
    suspend fun capture(event: AccessibilityWindowEvent): WindowSnapshot?
}

fun interface ShortsDetector {
    fun evaluate(snapshot: WindowSnapshot): DetectionResult
}

interface DeviceStateSource {
    val state: StateFlow<DeviceState>
}

fun interface UsageStatsSource {
    suspend fun eventsBetween(start: Instant, end: Instant): List<NormalizedUsageEvent>
}
