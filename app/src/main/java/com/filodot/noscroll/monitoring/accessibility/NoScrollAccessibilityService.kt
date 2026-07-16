package com.filodot.noscroll.monitoring.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.filodot.noscroll.NoScrollApplication
import com.filodot.noscroll.core.contracts.AccessibilityEventSource
import com.filodot.noscroll.core.contracts.DeviceStateSource
import com.filodot.noscroll.core.contracts.WindowSnapshotProvider
import com.filodot.noscroll.core.model.AccessibilityWindowEvent
import com.filodot.noscroll.core.model.DeviceState
import com.filodot.noscroll.core.model.WindowSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Package-scoped Android adapter. It does not detect Shorts, count time, enforce policy, or draw UI.
 */
class NoScrollAccessibilityService :
    AccessibilityService(),
    AccessibilityEventSource,
    WindowSnapshotProvider,
    DeviceStateSource {
    private val snapshotProvider by lazy {
        AndroidWindowSnapshotProvider(rootProvider = { rootInActiveWindow })
    }
    private val controller by lazy {
        AccessibilityAdapterController(
            scheduler = HandlerAccessibilityScanScheduler(Handler(Looper.getMainLooper())),
            elapsedRealtimeMillis = SystemClock::elapsedRealtime,
            screenStateProvider = ::readScreenState,
            snapshotCapture = snapshotProvider::capture,
        )
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action in SCREEN_STATE_ACTIONS) controller.onScreenStateChanged()
        }
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var screenReceiverRegistered = false
    private var blockingOverlay: AccessibilityBlockingOverlay? = null

    override val events: Flow<AccessibilityWindowEvent>
        get() = controller.events

    override val state: StateFlow<DeviceState>
        get() = controller.state

    public override fun onServiceConnected() {
        super.onServiceConnected()
        registerScreenReceiverIfNeeded()
        controller.onServiceConnected()
        runtime()?.let { runtime ->
            runtime.systemAccess.refresh()
            runtime.monitoring.attach(this)
            blockingOverlay?.stop()
            blockingOverlay = AccessibilityBlockingOverlay(
                service = this,
                monitoring = runtime.monitoring,
                scope = serviceScope,
            ).also(AccessibilityBlockingOverlay::start)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (blockingOverlay?.shouldIgnoreAccessibilityEvent(event) == true) return
        controller.onAccessibilityEvent(
            packageName = event.packageName,
            eventType = event.eventType,
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        )
    }

    override fun onInterrupt() {
        controller.onServiceInterrupted()
        stopRuntimeConnection()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        controller.onServiceInterrupted()
        stopRuntimeConnection()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        controller.onServiceInterrupted()
        stopRuntimeConnection()
        serviceScope.cancel()
        unregisterScreenReceiverIfNeeded()
        super.onDestroy()
    }

    override suspend fun capture(event: AccessibilityWindowEvent): WindowSnapshot? =
        controller.capture(event)

    private fun readScreenState(): ScreenStateSample {
        val powerManager = getSystemService(PowerManager::class.java)
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        return ScreenStateSample(
            screenInteractive = powerManager?.isInteractive == true,
            deviceUnlocked = keyguardManager?.isDeviceLocked == false,
        )
    }

    private fun registerScreenReceiverIfNeeded() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(screenReceiver, filter)
            }
            screenReceiverRegistered = true
        } catch (_: RuntimeException) {
            screenReceiverRegistered = false
            controller.onScreenStateChanged()
        }
    }

    private fun unregisterScreenReceiverIfNeeded() {
        if (!screenReceiverRegistered) return
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: IllegalArgumentException) {
            // The platform may already have disconnected the receiver during service revocation.
        } finally {
            screenReceiverRegistered = false
        }
    }

    private fun runtime() = (application as? NoScrollApplication)?.runtime

    private fun stopRuntimeConnection() {
        blockingOverlay?.stop()
        blockingOverlay = null
        runtime()?.monitoring?.detach()
    }

    companion object {
        private val SCREEN_STATE_ACTIONS = setOf(
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_SCREEN_OFF,
            Intent.ACTION_USER_PRESENT,
        )
    }
}
