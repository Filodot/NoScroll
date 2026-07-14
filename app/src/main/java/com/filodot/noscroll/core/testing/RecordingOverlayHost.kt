package com.filodot.noscroll.core.testing

import com.filodot.noscroll.core.contracts.OverlayHost
import com.filodot.noscroll.core.model.OverlayState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RecordingOverlayHost : OverlayHost {
    private val mutableState = MutableStateFlow<OverlayState>(OverlayState.Hidden)

    override val state: StateFlow<OverlayState> = mutableState
    val recordedStates = mutableListOf<OverlayState>()

    override suspend fun show(state: OverlayState) {
        recordedStates += state
        mutableState.value = state
    }

    override suspend fun hide() {
        recordedStates += OverlayState.Hidden
        mutableState.value = OverlayState.Hidden
    }
}
