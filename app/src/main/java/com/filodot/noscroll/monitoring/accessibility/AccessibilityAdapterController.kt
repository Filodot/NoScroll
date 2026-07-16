package com.filodot.noscroll.monitoring.accessibility

import com.filodot.noscroll.core.contracts.AccessibilityEventSource
import com.filodot.noscroll.core.contracts.DeviceStateSource
import com.filodot.noscroll.core.contracts.WindowSnapshotProvider
import com.filodot.noscroll.core.model.AccessibilityWindowEvent
import com.filodot.noscroll.core.model.DeviceState
import com.filodot.noscroll.core.model.WindowSnapshot
import java.util.concurrent.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal data class ScreenStateSample(
    val screenInteractive: Boolean,
    val deviceUnlocked: Boolean,
)

internal class AccessibilityAdapterController(
    scheduler: AccessibilityScanScheduler,
    elapsedRealtimeMillis: () -> Long,
    private val screenStateProvider: () -> ScreenStateSample,
    private val snapshotCapture: suspend (AccessibilityWindowEvent) -> WindowSnapshot?,
) : AccessibilityEventSource, WindowSnapshotProvider, DeviceStateSource {
    private val mutableEvents = MutableSharedFlow<AccessibilityWindowEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val mutableState = MutableStateFlow(DISCONNECTED_STATE)
    private val coalescer = AccessibilityEventCoalescer(
        scheduler = scheduler,
        elapsedRealtimeMillis = elapsedRealtimeMillis,
        onEmit = { event -> mutableEvents.tryEmit(event) },
    )

    @Volatile
    private var connected = false

    override val events: Flow<AccessibilityWindowEvent> = mutableEvents
    override val state: StateFlow<DeviceState> = mutableState

    fun onServiceConnected() {
        coalescer.cancelAndReset()
        connected = true
        updateDeviceState(foregroundPackage = null)
    }

    fun onAccessibilityEvent(
        packageName: CharSequence?,
        eventType: Int,
        elapsedRealtimeMillis: Long,
    ): Boolean {
        if (!connected) return false
        if (eventType !in SUPPORTED_EVENT_TYPES) return false
        if (packageName?.contentEquals(YOUTUBE_PACKAGE_NAME) != true) {
            coalescer.cancelAndReset()
            updateDeviceState(foregroundPackage = null)
            return false
        }

        val sample = updateDeviceState(foregroundPackage = YOUTUBE_PACKAGE_NAME)
        if (!sample.screenInteractive || !sample.deviceUnlocked) {
            coalescer.cancelAndReset()
            return false
        }

        coalescer.offer(
            AccessibilityWindowEvent(
                packageName = YOUTUBE_PACKAGE_NAME,
                eventType = eventType,
                elapsedRealtimeMillis = elapsedRealtimeMillis.coerceAtLeast(0L),
            ),
        )
        return true
    }

    fun onScreenStateChanged() {
        val currentForeground = mutableState.value.foregroundPackage
        val sample = updateDeviceState(foregroundPackage = currentForeground)
        if (!sample.screenInteractive || !sample.deviceUnlocked) {
            coalescer.cancelAndReset()
            mutableState.value = mutableState.value.copy(foregroundPackage = null)
        }
    }

    fun onServiceInterrupted() {
        connected = false
        coalescer.cancelAndReset()
        updateDeviceState(foregroundPackage = null)
    }

    override suspend fun capture(event: AccessibilityWindowEvent): WindowSnapshot? {
        if (!connected || event.packageName != YOUTUBE_PACKAGE_NAME) return null
        val snapshot = try {
            snapshotCapture(event)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        if (!connected || snapshot?.packageName != YOUTUBE_PACKAGE_NAME) {
            mutableState.value = mutableState.value.copy(foregroundPackage = null)
            return null
        }
        mutableState.value = mutableState.value.copy(foregroundPackage = YOUTUBE_PACKAGE_NAME)
        return snapshot
    }

    private fun updateDeviceState(foregroundPackage: String?): ScreenStateSample {
        val sample = runCatching(screenStateProvider)
            .getOrDefault(ScreenStateSample(screenInteractive = false, deviceUnlocked = false))
        mutableState.value = DeviceState(
            screenInteractive = sample.screenInteractive,
            deviceUnlocked = sample.deviceUnlocked,
            foregroundPackage = foregroundPackage.takeIf {
                sample.screenInteractive && sample.deviceUnlocked
            },
        )
        return sample
    }

    companion object {
        const val YOUTUBE_PACKAGE_NAME = "com.google.android.youtube"

        // Values are stable Android AccessibilityEvent constants and kept Android-free for unit tests.
        const val TYPE_WINDOW_STATE_CHANGED = 32
        const val TYPE_WINDOW_CONTENT_CHANGED = 2_048
        const val TYPE_VIEW_SCROLLED = 4_096
        const val TYPE_WINDOWS_CHANGED = 4_194_304

        val SUPPORTED_EVENT_TYPES = setOf(
            TYPE_WINDOW_STATE_CHANGED,
            TYPE_WINDOWS_CHANGED,
            TYPE_WINDOW_CONTENT_CHANGED,
            TYPE_VIEW_SCROLLED,
        )

        private val DISCONNECTED_STATE = DeviceState(
            screenInteractive = false,
            deviceUnlocked = false,
            foregroundPackage = null,
        )
    }
}
