package com.filodot.noscroll.feature.overlay

import com.filodot.noscroll.core.emergency.MAX_REASON_LENGTH
import com.filodot.noscroll.core.emergency.MIN_REASON_LENGTH
import com.filodot.noscroll.core.model.EmergencyActivationSource
import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface EnforcementUiState {
    data class TaskGate(
        val taskId: String,
        val visualExpression: String,
        val spokenExpression: String,
        val grantMinutes: Int,
        val difficulty: TaskDifficulty = TaskDifficulty.MEDIUM,
        val trigger: TaskTrigger = TaskTrigger.INTERVAL,
        val answer: String = "",
        val wrongAttempts: Int = 0,
        val answerStatus: TaskAnswerStatus = TaskAnswerStatus.READY,
    ) : EnforcementUiState

    data class DailyLimit(
        val usedMinutes: Int,
        val limitMinutes: Int,
        val resetLabel: String = "завтра в 00:00",
    ) : EnforcementUiState
}

enum class TaskAnswerStatus {
    READY,
    CHECKING,
    INCORRECT,
    CORRECT,
}

data class EmergencyFormUiState(
    val reason: String = "",
    val submitting: Boolean = false,
    val submissionError: String? = null,
) {
    val normalizedReason: String
        get() = reason.trim()

    val canConfirm: Boolean
        get() = !submitting && normalizedReason.length in
            MIN_REASON_LENGTH..MAX_REASON_LENGTH
}

data class BlockingOverlayUiState(
    val enforcement: EnforcementUiState,
    val emergencyForm: EmergencyFormUiState? = null,
    val emergencySourceOverride: EmergencyActivationSource? = null,
) {
    val emergencySource: EmergencyActivationSource
        get() = emergencySourceOverride ?: when (enforcement) {
            is EnforcementUiState.TaskGate -> EmergencyActivationSource.TASK_GATE
            is EnforcementUiState.DailyLimit -> EmergencyActivationSource.DAILY_LIMIT
        }
}

sealed interface BlockingOverlayAction {
    data class UpdateAnswer(val value: String) : BlockingOverlayAction
    data object SubmitAnswer : BlockingOverlayAction
    data class AnswerChecked(val correct: Boolean) : BlockingOverlayAction
    data object RequestAnotherTask : BlockingOverlayAction
    data class ShowTask(val task: EnforcementUiState.TaskGate) : BlockingOverlayAction
    data class DailyLimitReached(val daily: EnforcementUiState.DailyLimit) :
        BlockingOverlayAction

    data object ExitYouTube : BlockingOverlayAction
    data object SystemBack : BlockingOverlayAction
    data object OpenEmergencyForm : BlockingOverlayAction
    data class UpdateEmergencyReason(val value: String) : BlockingOverlayAction
    data object ConfirmEmergency : BlockingOverlayAction
    data object CancelEmergency : BlockingOverlayAction
    data class EmergencyActivationFinished(
        val succeeded: Boolean,
        val errorMessage: String? = null,
    ) : BlockingOverlayAction
}

sealed interface BlockingOverlayEffect {
    data class VerifyAnswer(
        val taskId: String,
        val answer: String,
    ) : BlockingOverlayEffect

    data object RequestAnotherTask : BlockingOverlayEffect
    data object ExitYouTube : BlockingOverlayEffect
    data class ConfirmEmergency(
        val normalizedReason: String,
        val source: EmergencyActivationSource,
    ) : BlockingOverlayEffect

    data object TaskSolved : BlockingOverlayEffect
    data object EmergencyActivated : BlockingOverlayEffect
}

class BlockingOverlayStateHolder(
    initialEnforcement: EnforcementUiState,
    emergencySourceOverride: EmergencyActivationSource? = null,
    private val emitEffect: (BlockingOverlayEffect) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(
        BlockingOverlayUiState(
            enforcement = initialEnforcement,
            emergencySourceOverride = emergencySourceOverride,
        ),
    )
    val state: StateFlow<BlockingOverlayUiState> = mutableState.asStateFlow()

    fun dispatch(action: BlockingOverlayAction) {
        when (action) {
            is BlockingOverlayAction.UpdateAnswer -> updateAnswer(action.value)
            BlockingOverlayAction.SubmitAnswer -> submitAnswer()
            is BlockingOverlayAction.AnswerChecked -> handleAnswerResult(action.correct)
            BlockingOverlayAction.RequestAnotherTask -> requestAnotherTask()
            is BlockingOverlayAction.ShowTask -> showTask(action.task)
            is BlockingOverlayAction.DailyLimitReached -> showDaily(action.daily)
            BlockingOverlayAction.ExitYouTube -> emitEffect(BlockingOverlayEffect.ExitYouTube)
            BlockingOverlayAction.SystemBack -> handleBack()
            BlockingOverlayAction.OpenEmergencyForm -> openEmergencyForm()
            is BlockingOverlayAction.UpdateEmergencyReason ->
                updateEmergencyReason(action.value)

            BlockingOverlayAction.ConfirmEmergency -> confirmEmergency()
            BlockingOverlayAction.CancelEmergency -> cancelEmergency()
            is BlockingOverlayAction.EmergencyActivationFinished ->
                finishEmergencyActivation(action)
        }
    }

    private fun updateAnswer(value: String) {
        val current = mutableState.value
        if (current.emergencyForm != null) return
        val task = current.enforcement as? EnforcementUiState.TaskGate ?: return
        if (task.answerStatus == TaskAnswerStatus.CHECKING ||
            task.answerStatus == TaskAnswerStatus.CORRECT
        ) {
            return
        }
        val normalized = value.filter(Char::isDigit).take(MAX_ANSWER_LENGTH)
        mutableState.value = current.copy(
            enforcement = task.copy(
                answer = normalized,
                answerStatus = TaskAnswerStatus.READY,
            ),
        )
    }

    private fun submitAnswer() {
        val current = mutableState.value
        if (current.emergencyForm != null) return
        val task = current.enforcement as? EnforcementUiState.TaskGate ?: return
        if (task.answer.isBlank() || task.answerStatus == TaskAnswerStatus.CHECKING ||
            task.answerStatus == TaskAnswerStatus.CORRECT
        ) {
            return
        }
        mutableState.value = current.copy(
            enforcement = task.copy(answerStatus = TaskAnswerStatus.CHECKING),
        )
        emitEffect(
            BlockingOverlayEffect.VerifyAnswer(
                taskId = task.taskId,
                answer = task.answer,
            ),
        )
    }

    private fun handleAnswerResult(correct: Boolean) {
        val current = mutableState.value
        val task = current.enforcement as? EnforcementUiState.TaskGate ?: return
        if (task.answerStatus != TaskAnswerStatus.CHECKING) return
        val updated = if (correct) {
            task.copy(answerStatus = TaskAnswerStatus.CORRECT)
        } else {
            task.copy(
                answer = "",
                wrongAttempts = task.wrongAttempts + 1,
                answerStatus = TaskAnswerStatus.INCORRECT,
            )
        }
        mutableState.value = current.copy(enforcement = updated)
        if (correct) emitEffect(BlockingOverlayEffect.TaskSolved)
    }

    private fun requestAnotherTask() {
        val task = mutableState.value.enforcement as? EnforcementUiState.TaskGate ?: return
        if (task.wrongAttempts < WRONG_ATTEMPTS_FOR_REPLACEMENT ||
            task.answerStatus == TaskAnswerStatus.CHECKING ||
            task.answerStatus == TaskAnswerStatus.CORRECT
        ) {
            return
        }
        emitEffect(BlockingOverlayEffect.RequestAnotherTask)
    }

    private fun showTask(task: EnforcementUiState.TaskGate) {
        mutableState.value = mutableState.value.copy(
            enforcement = task.copy(
                answer = "",
                answerStatus = TaskAnswerStatus.READY,
            ),
        )
    }

    private fun showDaily(daily: EnforcementUiState.DailyLimit) {
        mutableState.value = mutableState.value.copy(enforcement = daily)
    }

    private fun handleBack() {
        if (mutableState.value.emergencyForm != null) {
            cancelEmergency()
        } else {
            emitEffect(BlockingOverlayEffect.ExitYouTube)
        }
    }

    private fun openEmergencyForm() {
        val current = mutableState.value
        if (current.emergencyForm != null) return
        mutableState.value = current.copy(emergencyForm = EmergencyFormUiState())
    }

    private fun updateEmergencyReason(value: String) {
        val current = mutableState.value
        val form = current.emergencyForm ?: return
        if (form.submitting) return
        mutableState.value = current.copy(
            emergencyForm = form.copy(
                reason = value.take(MAX_REASON_LENGTH),
                submissionError = null,
            ),
        )
    }

    private fun confirmEmergency() {
        val current = mutableState.value
        val form = current.emergencyForm ?: return
        if (!form.canConfirm) return
        mutableState.value = current.copy(
            emergencyForm = form.copy(submitting = true, submissionError = null),
        )
        emitEffect(
            BlockingOverlayEffect.ConfirmEmergency(
                normalizedReason = form.normalizedReason,
                source = current.emergencySource,
            ),
        )
    }

    private fun cancelEmergency() {
        val current = mutableState.value
        val form = current.emergencyForm ?: return
        if (form.submitting) return
        mutableState.value = current.copy(emergencyForm = null)
    }

    private fun finishEmergencyActivation(
        action: BlockingOverlayAction.EmergencyActivationFinished,
    ) {
        val current = mutableState.value
        val form = current.emergencyForm ?: return
        if (!form.submitting) return
        if (action.succeeded) {
            mutableState.value = current.copy(emergencyForm = null)
            emitEffect(BlockingOverlayEffect.EmergencyActivated)
        } else {
            mutableState.value = current.copy(
                emergencyForm = form.copy(
                    submitting = false,
                    submissionError = action.errorMessage ?: "Не получилось отключить ограничения",
                ),
            )
        }
    }

    private companion object {
        const val MAX_ANSWER_LENGTH = 9
        const val WRONG_ATTEMPTS_FOR_REPLACEMENT = 3
    }
}
