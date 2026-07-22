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
import android.widget.Toast
import com.filodot.noscroll.MainActivity
import com.filodot.noscroll.NoScrollApplication
import com.filodot.noscroll.core.contracts.AccessibilityEventSource
import com.filodot.noscroll.core.contracts.DeviceStateSource
import com.filodot.noscroll.core.contracts.WindowSnapshotProvider
import com.filodot.noscroll.core.model.AccessibilityWindowEvent
import com.filodot.noscroll.core.model.DeviceState
import com.filodot.noscroll.core.model.WindowSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Package-scoped Android adapter. It does not detect Shorts, count time, enforce policy, or draw UI.
 */
class NoScrollAccessibilityService :
    AccessibilityService(),
    AccessibilityEventSource,
    WindowSnapshotProvider,
    DeviceStateSource {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val snapshotProvider by lazy {
        AndroidWindowSnapshotProvider(rootProvider = { rootInActiveWindow })
    }
    private val controller by lazy {
        AccessibilityAdapterController(
            scheduler = HandlerAccessibilityScanScheduler(mainHandler),
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
    private var screenReceiverRegistered = false

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
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        controller.onAccessibilityEvent(
            packageName = event.packageName,
            eventType = event.eventType,
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        )
    }

    override fun onInterrupt() {
        controller.onTransientInterrupt()
        runtime()?.monitoring?.onTransientServiceInterrupt()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        controller.onServiceInterrupted()
        stopRuntimeConnection()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        controller.onServiceInterrupted()
        stopRuntimeConnection()
        unregisterScreenReceiverIfNeeded()
        super.onDestroy()
    }

    override suspend fun capture(event: AccessibilityWindowEvent): WindowSnapshot? =
        controller.capture(event)

    /** Leaves the monitored app first, then opens the in-app challenge screen. */
    fun ejectTargetAndOpenChallenge() {
        mainHandler.post {
            val leftTarget = performGlobalAction(GLOBAL_ACTION_BACK)
            if (!leftTarget) performGlobalAction(GLOBAL_ACTION_HOME)
            mainHandler.postDelayed(
                {
                    try {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                                ),
                        )
                    } catch (_: RuntimeException) {
                        Toast.makeText(
                            this,
                            "Откройте NoScroll, чтобы решить задание",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                OPEN_APP_AFTER_BACK_MILLIS,
            )
        }
    }

    @Deprecated("Use ejectTargetAndOpenChallenge")
    fun ejectShortsAndOpenChallenge() = ejectTargetAndOpenChallenge()

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
        runtime()?.monitoring?.detach()
    }

    companion object {
        private val SCREEN_STATE_ACTIONS = setOf(
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_SCREEN_OFF,
            Intent.ACTION_USER_PRESENT,
        )
        private const val OPEN_APP_AFTER_BACK_MILLIS = 250L
    }
}
