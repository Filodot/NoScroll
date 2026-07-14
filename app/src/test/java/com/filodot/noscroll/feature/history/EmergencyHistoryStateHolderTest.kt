package com.filodot.noscroll.feature.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyHistoryStateHolderTest {
    @Test
    fun `history item is active only without deactivation label`() {
        assertTrue(activeItem().isActive)
        assertFalse(completedItem().isActive)
    }

    @Test
    fun `delete request can be cancelled without emitting persistence effect`() {
        val effects = mutableListOf<EmergencyHistoryEffect>()
        val holder = holder(effects)

        holder.dispatch(EmergencyHistoryAction.RequestDeleteHistory)
        assertTrue(holder.state.value.deleteConfirmationVisible)

        holder.dispatch(EmergencyHistoryAction.CancelDeleteHistory)
        assertFalse(holder.state.value.deleteConfirmationVisible)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `delete confirmation emits effect and waits for repository result`() {
        val effects = mutableListOf<EmergencyHistoryEffect>()
        val holder = holder(effects)
        holder.dispatch(EmergencyHistoryAction.RequestDeleteHistory)

        holder.dispatch(EmergencyHistoryAction.ConfirmDeleteHistory)

        assertTrue(holder.state.value.deleting)
        assertTrue(holder.state.value.deleteConfirmationVisible)
        assertEquals(listOf(EmergencyHistoryEffect.DeleteHistory), effects)
    }

    @Test
    fun `successful deletion removes completed history but preserves active emergency`() {
        val holder = holder()
        holder.dispatch(EmergencyHistoryAction.RequestDeleteHistory)
        holder.dispatch(EmergencyHistoryAction.ConfirmDeleteHistory)

        holder.dispatch(EmergencyHistoryAction.DeletionFinished(succeeded = true))

        assertEquals(listOf(activeItem()), holder.state.value.items)
        assertFalse(holder.state.value.deleteConfirmationVisible)
        assertFalse(holder.state.value.deleting)
    }

    @Test
    fun `failed deletion keeps data and confirmation with safe error`() {
        val holder = holder()
        holder.dispatch(EmergencyHistoryAction.RequestDeleteHistory)
        holder.dispatch(EmergencyHistoryAction.ConfirmDeleteHistory)

        holder.dispatch(
            EmergencyHistoryAction.DeletionFinished(
                succeeded = false,
                errorMessage = "Локальная база недоступна",
            ),
        )

        assertEquals(2, holder.state.value.items.size)
        assertTrue(holder.state.value.deleteConfirmationVisible)
        assertFalse(holder.state.value.deleting)
        assertEquals("Локальная база недоступна", holder.state.value.deleteError)
    }

    @Test
    fun `retry clears load error and emits reload effect`() {
        val effects = mutableListOf<EmergencyHistoryEffect>()
        val holder = EmergencyHistoryStateHolder(
            initialState = EmergencyHistoryUiState(loadError = "Ошибка"),
            emitEffect = effects::add,
        )

        holder.dispatch(EmergencyHistoryAction.RetryLoad)

        assertTrue(holder.state.value.loading)
        assertNull(holder.state.value.loadError)
        assertEquals(listOf(EmergencyHistoryEffect.RetryLoad), effects)
    }

    private fun holder(
        effects: MutableList<EmergencyHistoryEffect> = mutableListOf(),
    ) = EmergencyHistoryStateHolder(
        initialState = EmergencyHistoryUiState(
            items = listOf(activeItem(), completedItem()),
        ),
        emitEffect = effects::add,
    )

    private fun activeItem() = EmergencyHistoryItemUi(
        id = "active",
        reason = "Учебная трансляция",
        activatedAtLabel = "Сегодня, 14:32",
        durationMinutes = 18,
        youtubeMinutesDuring = 12,
        sourceLabel = "Сегодня",
    )

    private fun completedItem() = EmergencyHistoryItemUi(
        id = "completed",
        reason = "Рабочая инструкция",
        activatedAtLabel = "Вчера, 10:15",
        deactivatedAtLabel = "10:42",
        durationMinutes = 27,
        youtubeMinutesDuring = 19,
        sourceLabel = "Задание",
    )
}
