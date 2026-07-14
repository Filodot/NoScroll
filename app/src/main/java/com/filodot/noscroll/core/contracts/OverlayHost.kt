package com.filodot.noscroll.core.contracts

import com.filodot.noscroll.core.model.OverlayState
import kotlinx.coroutines.flow.StateFlow

interface OverlayHost {
    val state: StateFlow<OverlayState>

    suspend fun show(state: OverlayState)

    suspend fun hide()
}
