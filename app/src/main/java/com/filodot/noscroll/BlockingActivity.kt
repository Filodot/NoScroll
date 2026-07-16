package com.filodot.noscroll

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filodot.noscroll.feature.overlay.BlockingOverlayAction
import com.filodot.noscroll.feature.overlay.BlockingOverlayEffect
import com.filodot.noscroll.feature.overlay.BlockingOverlayScreen
import com.filodot.noscroll.feature.overlay.BlockingOverlayStateHolder
import com.filodot.noscroll.ui.theme.NoScrollTheme
import kotlinx.coroutines.launch

class BlockingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runtime = (application as NoScrollApplication).runtime
        val initial = runtime.monitoring.enforcement.value
        if (initial == null) {
            finish()
            return
        }
        setContent {
            NoScrollTheme {
                BlockingRoute(
                    initialState = initial,
                    runtime = runtime,
                    finishActivity = ::finish,
                    exitToHome = ::exitToHome,
                )
            }
        }
    }

    private fun exitToHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(
                Intent(context, BlockingActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            )
        }
    }
}

@Composable
private fun BlockingRoute(
    initialState: com.filodot.noscroll.feature.overlay.EnforcementUiState,
    runtime: com.filodot.noscroll.runtime.NoScrollRuntime,
    finishActivity: () -> Unit,
    exitToHome: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    lateinit var holder: BlockingOverlayStateHolder
    holder = remember(initialState, runtime) {
        BlockingOverlayStateHolder(initialEnforcement = initialState) { effect ->
            when (effect) {
                is BlockingOverlayEffect.VerifyAnswer -> scope.launch {
                    val correct = runtime.monitoring.verifyAnswer(effect.taskId, effect.answer)
                    holder.dispatch(BlockingOverlayAction.AnswerChecked(correct))
                }

                BlockingOverlayEffect.RequestAnotherTask -> scope.launch {
                    runtime.monitoring.replaceTask()?.let { replacement ->
                        holder.dispatch(BlockingOverlayAction.ShowTask(replacement))
                    }
                }

                BlockingOverlayEffect.ExitYouTube -> scope.launch {
                    runtime.monitoring.dismissEnforcement(recordExit = true)
                    exitToHome()
                }

                is BlockingOverlayEffect.ConfirmEmergency -> scope.launch {
                    runtime.monitoring.activateEmergency(
                        reason = effect.normalizedReason,
                        source = effect.source,
                    )
                    holder.dispatch(
                        BlockingOverlayAction.EmergencyActivationFinished(succeeded = true),
                    )
                }

                BlockingOverlayEffect.TaskSolved,
                BlockingOverlayEffect.EmergencyActivated,
                -> finishActivity()
            }
        }
    }
    val state by holder.state.collectAsStateWithLifecycle()
    val runtimeEnforcement by runtime.monitoring.enforcement.collectAsStateWithLifecycle()
    LaunchedEffect(runtimeEnforcement) {
        when (val enforcement = runtimeEnforcement) {
            null -> if (state.emergencyForm == null) finishActivity()
            is com.filodot.noscroll.feature.overlay.EnforcementUiState.DailyLimit ->
                holder.dispatch(BlockingOverlayAction.DailyLimitReached(enforcement))

            is com.filodot.noscroll.feature.overlay.EnforcementUiState.TaskGate ->
                if (enforcement.taskId !=
                    (state.enforcement as? com.filodot.noscroll.feature.overlay.EnforcementUiState.TaskGate)
                        ?.taskId
                ) {
                    holder.dispatch(BlockingOverlayAction.ShowTask(enforcement))
                }
        }
    }
    BlockingOverlayScreen(state = state, onAction = holder::dispatch)
}
