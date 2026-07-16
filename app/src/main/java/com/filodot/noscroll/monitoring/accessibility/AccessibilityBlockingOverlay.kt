package com.filodot.noscroll.monitoring.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.core.widget.doAfterTextChanged
import com.filodot.noscroll.core.emergency.MAX_REASON_LENGTH
import com.filodot.noscroll.core.emergency.MIN_REASON_LENGTH
import com.filodot.noscroll.core.model.EmergencyActivationSource
import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskTrigger
import com.filodot.noscroll.feature.overlay.EnforcementUiState
import com.filodot.noscroll.monitoring.runtime.MonitoringCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal object AccessibilityOverlayWindowPolicy {
    fun createLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.OPAQUE,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "NoScroll blocking overlay"
    }
}

@SuppressLint("SetTextI18n")
internal class AccessibilityBlockingOverlay(
    private val service: AccessibilityService,
    private val monitoring: MonitoringCoordinator,
    private val scope: CoroutineScope,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var collectionJob: Job? = null
    private var rootView: View? = null
    private var currentEnforcement: EnforcementUiState? = null
    private var emergencyFormVisible = false
    private var emergencyReason = ""
    private var playbackEjectedForGate = false
    private var ignoreExternalEventsUntilElapsedMillis = 0L
    private val inputMethodPackage: String? by lazy {
        Settings.Secure.getString(
            service.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        )?.substringBefore('/')
    }

    val isShowing: Boolean
        get() = rootView != null

    fun shouldIgnoreAccessibilityEvent(event: AccessibilityEvent): Boolean {
        if (!isShowing) return false
        if (SystemClock.elapsedRealtime() < ignoreExternalEventsUntilElapsedMillis) return true
        val eventPackage = event.packageName?.toString()
        return eventPackage == service.packageName ||
            eventPackage == inputMethodPackage ||
            eventPackage == AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME ||
            eventPackage == SYSTEM_UI_PACKAGE &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
    }

    fun start() {
        collectionJob?.cancel()
        collectionJob = scope.launch {
            monitoring.enforcement.collectLatest { enforcement ->
                currentEnforcement = enforcement
                if (enforcement == null) {
                    playbackEjectedForGate = false
                    emergencyFormVisible = false
                    emergencyReason = ""
                    hide()
                } else {
                    render(enforcement)
                }
            }
        }
    }

    fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        currentEnforcement = null
        emergencyFormVisible = false
        emergencyReason = ""
        hide()
    }

    private fun render(enforcement: EnforcementUiState) {
        if (enforcement is EnforcementUiState.TaskGate && !playbackEjectedForGate) {
            playbackEjectedForGate = true
            ignoreExternalEventsUntilElapsedMillis = SystemClock.elapsedRealtime() +
                PLAYBACK_EJECTION_EVENT_SUPPRESSION_MILLIS
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            monitoring.onShortsPlaybackEjected()
        }
        val nextView = if (emergencyFormVisible) {
            buildEmergencyForm(enforcement)
        } else {
            buildEnforcementView(enforcement)
        }
        replaceView(nextView)
    }

    private fun replaceView(nextView: View) {
        hide()
        val manager = windowManager ?: return
        try {
            manager.addView(nextView, AccessibilityOverlayWindowPolicy.createLayoutParams())
            rootView = nextView
            monitoring.reportOverlayAvailability(true)
            nextView.isFocusableInTouchMode = true
            nextView.requestFocus()
        } catch (_: RuntimeException) {
            rootView = null
            monitoring.reportOverlayAvailability(false)
        }
    }

    private fun hide() {
        val current = rootView ?: return
        rootView = null
        try {
            windowManager?.removeViewImmediate(current)
        } catch (_: RuntimeException) {
            // The system may already have removed the window after service revocation.
        }
    }

    private fun buildEnforcementView(enforcement: EnforcementUiState): View =
        scrollContainer(
            contentContainer().apply {
            addView(mark())
            addView(spacer(28))
            when (enforcement) {
                is EnforcementUiState.TaskGate -> addTaskContent(enforcement)
                is EnforcementUiState.DailyLimit -> addDailyContent(enforcement)
            }
            addView(spacer(28))
            addView(actionButton("Выйти из YouTube", primary = true) { exitYouTube() })
            addView(spacer(8))
            addView(actionButton("Emergency Stop", primary = false) {
                emergencyFormVisible = true
                render(enforcement)
            })
            },
        )

    private fun LinearLayout.addTaskContent(task: EnforcementUiState.TaskGate) {
        addView(
            title(
                if (task.trigger == TaskTrigger.ENTRY) {
                    "Вход в Shorts"
                } else {
                    "Пора сделать паузу"
                },
            ),
        )
        addView(body(task.difficulty.label()))
        addView(
            body(
                if (task.trigger == TaskTrigger.ENTRY) {
                    "Решите пример — это плата за вход в новую сессию Shorts"
                } else {
                    "Решите пример, чтобы открыть Shorts ещё на ${task.grantMinutes} минут"
                },
            ),
        )
        addView(spacer(28))
        addView(title(task.visualExpression, sizeSp = 40f).apply {
            contentDescription = task.spokenExpression
            gravity = Gravity.CENTER
        })
        addView(spacer(20))
        val answerInput = EditText(service).apply {
            hint = "Ответ"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(TEXT_COLOR)
            setHintTextColor(MUTED_TEXT_COLOR)
            textSize = 20f
            isSingleLine = true
            backgroundTintList = ColorStateList.valueOf(PRIMARY_COLOR)
            layoutParams = matchWidth(heightDp = 56)
        }
        addView(answerInput)
        if (task.wrongAttempts > 0) {
            addView(body("Неверный ответ. Попробуйте ещё раз", ERROR_COLOR))
        }
        addView(spacer(12))
        val verifyButton = actionButton("Проверить", primary = true) {
            val answer = answerInput.text?.toString().orEmpty()
            if (answer.isBlank()) return@actionButton
            answerInput.isEnabled = false
            scope.launch {
                val correct = monitoring.verifyAnswer(task.taskId, answer)
                if (!correct && currentEnforcement != null) answerInput.isEnabled = true
            }
        }.apply { isEnabled = false }
        answerInput.doAfterTextChanged { verifyButton.isEnabled = !it.isNullOrBlank() }
        addView(verifyButton)
        if (task.wrongAttempts >= WRONG_ATTEMPTS_FOR_REPLACEMENT) {
            addView(spacer(8))
            addView(actionButton("Другой пример", primary = false) {
                scope.launch { monitoring.replaceTask() }
            })
        }
    }

    private fun LinearLayout.addDailyContent(daily: EnforcementUiState.DailyLimit) {
        addView(title("Дневной лимит YouTube исчерпан"))
        addView(spacer(20))
        addView(
            card(
                "Сегодня: ${daily.usedMinutes} из ${daily.limitMinutes} минут\n\n" +
                    "Лимит обновится ${daily.resetLabel}",
            ),
        )
        addView(spacer(20))
        addView(body("Задание не продлевает дневной лимит."))
    }

    private fun buildEmergencyForm(enforcement: EnforcementUiState): View =
        scrollContainer(
            contentContainer().apply {
            addView(title("Отключить все ограничения?"))
            addView(
                body(
                    "Задания и дневной лимит не будут появляться, пока вы вручную не " +
                        "включите блокировку снова. Учёт времени продолжится.",
                ),
            )
            addView(spacer(20))
            val reasonInput = EditText(service).apply {
                hint = "Почему вам нужно отключить блокировку?"
                setText(emergencyReason)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 3
                maxLines = 6
                filters = arrayOf(InputFilter.LengthFilter(MAX_REASON_LENGTH))
                setTextColor(TEXT_COLOR)
                setHintTextColor(MUTED_TEXT_COLOR)
                textSize = 18f
                backgroundTintList = ColorStateList.valueOf(PRIMARY_COLOR)
                layoutParams = matchWidth(heightDp = ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            addView(reasonInput)
            addView(spacer(12))
            val confirm = actionButton("Отключить блокировку", primary = true) {
                val normalized = reasonInput.text?.toString()?.trim().orEmpty()
                if (normalized.length !in MIN_REASON_LENGTH..MAX_REASON_LENGTH) {
                    return@actionButton
                }
                emergencyReason = normalized
                reasonInput.isEnabled = false
                scope.launch {
                    monitoring.activateEmergency(normalized, enforcement.emergencySource())
                }
            }
            reasonInput.doAfterTextChanged {
                emergencyReason = it?.toString().orEmpty()
                confirm.isEnabled = emergencyReason.trim().length in
                    MIN_REASON_LENGTH..MAX_REASON_LENGTH
            }
            confirm.isEnabled = emergencyReason.trim().length in
                MIN_REASON_LENGTH..MAX_REASON_LENGTH
            addView(confirm)
            addView(spacer(8))
            addView(actionButton("Отмена", primary = false) {
                emergencyFormVisible = false
                emergencyReason = ""
                render(enforcement)
            })
            },
        )

    private fun contentContainer(): LinearLayout = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24))
            setBackgroundColor(BACKGROUND_COLOR)
            isClickable = true
            isFocusable = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    exitYouTube()
                    true
                } else {
                    false
                }
            }
        }

    private fun scrollContainer(content: LinearLayout): ScrollView =
        ScrollView(service).apply {
            setBackgroundColor(BACKGROUND_COLOR)
            isFillViewport = true
            isClickable = true
            isFocusable = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    exitYouTube()
                    true
                } else {
                    false
                }
            }
            addView(content, matchWidth(ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    private fun mark(): TextView = TextView(service).apply {
        text = "N"
        textSize = 28f
        gravity = Gravity.CENTER
        setTextColor(TEXT_COLOR)
        typeface = Typeface.DEFAULT_BOLD
        background = roundedBackground(PRIMARY_DARK_COLOR, 20)
        layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
    }

    private fun title(text: String, sizeSp: Float = 32f): TextView = TextView(service).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(TEXT_COLOR)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.START
        layoutParams = matchWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun body(text: String, color: Int = TEXT_COLOR): TextView = TextView(service).apply {
        this.text = text
        textSize = 18f
        setTextColor(color)
        setLineSpacing(0f, 1.15f)
        layoutParams = matchWidth(ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        }
    }

    private fun card(text: String): TextView = body(text).apply {
        textSize = 22f
        setPadding(dp(20))
        background = roundedBackground(PRIMARY_DARK_COLOR, 22)
    }

    private fun actionButton(
        text: String,
        primary: Boolean,
        onClick: () -> Unit,
    ): Button = Button(service).apply {
        this.text = text
        textSize = 17f
        isAllCaps = false
        minHeight = dp(52)
        setOnClickListener { onClick() }
        if (primary) {
            backgroundTintList = ColorStateList.valueOf(PRIMARY_COLOR)
            setTextColor(Color.BLACK)
        } else {
            backgroundTintList = ColorStateList.valueOf(SURFACE_COLOR)
            setTextColor(TEXT_COLOR)
        }
        layoutParams = matchWidth(heightDp = 56)
    }

    private fun spacer(heightDp: Int): View = View(service).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    private fun roundedBackground(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun matchWidth(heightDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (heightDp >= 0) dp(heightDp) else heightDp,
        )

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()

    private fun exitYouTube() {
        scope.launch {
            monitoring.dismissEnforcement(recordExit = true)
            val handled = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            if (!handled) {
                service.startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private fun EnforcementUiState.emergencySource(): EmergencyActivationSource = when (this) {
        is EnforcementUiState.TaskGate -> EmergencyActivationSource.TASK_GATE
        is EnforcementUiState.DailyLimit -> EmergencyActivationSource.DAILY_LIMIT
    }

    private fun TaskDifficulty.label(): String = when (this) {
        TaskDifficulty.EASY -> "Лёгкая задача"
        TaskDifficulty.MEDIUM -> "Средняя задача"
        TaskDifficulty.HARD -> "Сложная задача"
    }

    companion object {
        private const val WRONG_ATTEMPTS_FOR_REPLACEMENT = 3
        private const val PLAYBACK_EJECTION_EVENT_SUPPRESSION_MILLIS = 1_500L
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private val BACKGROUND_COLOR = Color.rgb(17, 14, 9)
        private val SURFACE_COLOR = Color.rgb(50, 46, 55)
        private val PRIMARY_COLOR = Color.rgb(255, 198, 48)
        private val PRIMARY_DARK_COLOR = Color.rgb(105, 78, 0)
        private val TEXT_COLOR = Color.rgb(245, 240, 235)
        private val MUTED_TEXT_COLOR = Color.rgb(185, 179, 188)
        private val ERROR_COLOR = Color.rgb(255, 180, 171)
    }
}
