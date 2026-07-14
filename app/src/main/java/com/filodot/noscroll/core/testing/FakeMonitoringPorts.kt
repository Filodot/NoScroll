package com.filodot.noscroll.core.testing

import com.filodot.noscroll.core.contracts.AccessibilityEventSource
import com.filodot.noscroll.core.contracts.DeviceStateSource
import com.filodot.noscroll.core.contracts.ShortsDetector
import com.filodot.noscroll.core.contracts.UsageStatsSource
import com.filodot.noscroll.core.contracts.WindowSnapshotProvider
import com.filodot.noscroll.core.model.AccessibilityWindowEvent
import com.filodot.noscroll.core.model.DetectionResult
import com.filodot.noscroll.core.model.DeviceState
import com.filodot.noscroll.core.model.NormalizedUsageEvent
import com.filodot.noscroll.core.model.ShortsDetectionState
import com.filodot.noscroll.core.model.WindowSnapshot
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAccessibilityEventSource : AccessibilityEventSource {
    private val mutableEvents = MutableSharedFlow<AccessibilityWindowEvent>(extraBufferCapacity = 16)

    override val events: Flow<AccessibilityWindowEvent> = mutableEvents

    suspend fun emit(event: AccessibilityWindowEvent) {
        mutableEvents.emit(event)
    }
}

class FakeWindowSnapshotProvider(
    var nextSnapshot: WindowSnapshot? = null,
) : WindowSnapshotProvider {
    val capturedEvents = mutableListOf<AccessibilityWindowEvent>()

    override suspend fun capture(event: AccessibilityWindowEvent): WindowSnapshot? {
        capturedEvents += event
        return nextSnapshot
    }
}

class FakeShortsDetector(
    var nextResult: DetectionResult = DetectionResult(
        state = ShortsDetectionState.UNKNOWN,
        confidence = 0f,
        rulesVersion = 1,
    ),
) : ShortsDetector {
    val evaluatedSnapshots = mutableListOf<WindowSnapshot>()

    override fun evaluate(snapshot: WindowSnapshot): DetectionResult {
        evaluatedSnapshots += snapshot
        return nextResult
    }
}

class FakeDeviceStateSource(
    initialState: DeviceState = DeviceState(
        screenInteractive = true,
        deviceUnlocked = true,
        foregroundPackage = null,
    ),
) : DeviceStateSource {
    private val mutableState = MutableStateFlow(initialState)

    override val state: StateFlow<DeviceState> = mutableState

    fun update(state: DeviceState) {
        mutableState.value = state
    }
}

class FakeUsageStatsSource(
    var events: List<NormalizedUsageEvent> = emptyList(),
) : UsageStatsSource {
    val requests = mutableListOf<Pair<Instant, Instant>>()

    override suspend fun eventsBetween(start: Instant, end: Instant): List<NormalizedUsageEvent> {
        requests += start to end
        return events
    }
}
