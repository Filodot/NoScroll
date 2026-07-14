package com.filodot.noscroll.feature.history

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EmergencyHistoryItemUi(
    val id: String,
    val reason: String,
    val activatedAtLabel: String,
    val deactivatedAtLabel: String? = null,
    val durationMinutes: Long,
    val youtubeMinutesDuring: Long,
    val sourceLabel: String,
) {
    val isActive: Boolean
        get() = deactivatedAtLabel == null
}

data class EmergencyHistoryUiState(
    val items: List<EmergencyHistoryItemUi> = emptyList(),
    val loading: Boolean = false,
    val loadError: String? = null,
    val deleteConfirmationVisible: Boolean = false,
    val deleting: Boolean = false,
    val deleteError: String? = null,
)

sealed interface EmergencyHistoryAction {
    data object RetryLoad : EmergencyHistoryAction
    data object RequestDeleteHistory : EmergencyHistoryAction
    data object CancelDeleteHistory : EmergencyHistoryAction
    data object ConfirmDeleteHistory : EmergencyHistoryAction
    data class DeletionFinished(
        val succeeded: Boolean,
        val errorMessage: String? = null,
    ) : EmergencyHistoryAction
}

sealed interface EmergencyHistoryEffect {
    data object RetryLoad : EmergencyHistoryEffect
    data object DeleteHistory : EmergencyHistoryEffect
}

class EmergencyHistoryStateHolder(
    initialState: EmergencyHistoryUiState = EmergencyHistoryUiState(),
    private val emitEffect: (EmergencyHistoryEffect) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<EmergencyHistoryUiState> = mutableState.asStateFlow()

    fun dispatch(action: EmergencyHistoryAction) {
        when (action) {
            EmergencyHistoryAction.RetryLoad -> {
                mutableState.value = mutableState.value.copy(loading = true, loadError = null)
                emitEffect(EmergencyHistoryEffect.RetryLoad)
            }

            EmergencyHistoryAction.RequestDeleteHistory -> {
                mutableState.value = mutableState.value.copy(
                    deleteConfirmationVisible = true,
                    deleteError = null,
                )
            }

            EmergencyHistoryAction.CancelDeleteHistory -> {
                if (!mutableState.value.deleting) {
                    mutableState.value = mutableState.value.copy(
                        deleteConfirmationVisible = false,
                        deleteError = null,
                    )
                }
            }

            EmergencyHistoryAction.ConfirmDeleteHistory -> {
                if (mutableState.value.deleteConfirmationVisible && !mutableState.value.deleting) {
                    mutableState.value = mutableState.value.copy(deleting = true, deleteError = null)
                    emitEffect(EmergencyHistoryEffect.DeleteHistory)
                }
            }

            is EmergencyHistoryAction.DeletionFinished -> finishDeletion(action)
        }
    }

    private fun finishDeletion(action: EmergencyHistoryAction.DeletionFinished) {
        val current = mutableState.value
        if (!current.deleting) return
        mutableState.value = if (action.succeeded) {
            current.copy(
                items = current.items.filter(EmergencyHistoryItemUi::isActive),
                deleteConfirmationVisible = false,
                deleting = false,
                deleteError = null,
            )
        } else {
            current.copy(
                deleting = false,
                deleteError = action.errorMessage ?: "Не получилось удалить локальную историю",
            )
        }
    }
}
