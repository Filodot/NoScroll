package com.filodot.noscroll.feature.limits

import com.filodot.noscroll.core.model.LimitPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitsStateHolderTest {
    @Test
    fun `balanced initial state has no unsaved changes and approved summary`() {
        val state = LimitsStateHolder().state.value

        assertFalse(state.hasUnsavedChanges)
        assertEquals(LimitPreset.BALANCED, state.draft.preset)
        assertTrue(state.summary.contains("каждые 5 минут Shorts"))
        assertTrue(state.summary.contains("После 45 минут всего YouTube"))
    }

    @Test
    fun `preset cards apply gentle balanced and strict values`() {
        val holder = LimitsStateHolder()

        holder.dispatch(LimitsAction.SelectPreset(LimitPreset.GENTLE))
        assertEquals(10, holder.state.value.draft.shortsMinutes)
        assertEquals(90, holder.state.value.draft.dailyMinutes)

        holder.dispatch(LimitsAction.SelectPreset(LimitPreset.STRICT))
        assertEquals(2, holder.state.value.draft.shortsMinutes)
        assertEquals(20, holder.state.value.draft.dailyMinutes)
        assertEquals(LimitPreset.STRICT, holder.state.value.draft.preset)
    }

    @Test
    fun `editing either numeric value transitions to custom`() {
        val holder = LimitsStateHolder()

        holder.dispatch(LimitsAction.SetShortsMinutes(6))
        assertEquals(LimitPreset.CUSTOM, holder.state.value.draft.preset)

        holder.dispatch(LimitsAction.SelectPreset(LimitPreset.BALANCED))
        holder.dispatch(LimitsAction.SetDailyMinutes(50))
        assertEquals(LimitPreset.CUSTOM, holder.state.value.draft.preset)
    }

    @Test
    fun `master switches retain numeric values and selected preset`() {
        val holder = LimitsStateHolder()

        holder.dispatch(LimitsAction.SetShortsEnabled(false))
        holder.dispatch(LimitsAction.SetDailyEnabled(false))

        assertEquals(5, holder.state.value.draft.shortsMinutes)
        assertEquals(45, holder.state.value.draft.dailyMinutes)
        assertEquals(LimitPreset.BALANCED, holder.state.value.draft.preset)
        assertTrue(holder.state.value.summary.startsWith("Паузы в Shorts и дневной лимит выключены."))
        assertTrue(holder.state.value.summary.contains("Instagram доступен интервалами по 10 минут"))
    }

    @Test
    fun `shorts input is clamped to one through thirty`() {
        val holder = LimitsStateHolder()

        holder.dispatch(LimitsAction.SetShortsMinutes(-100))
        assertEquals(1, holder.state.value.draft.shortsMinutes)

        holder.dispatch(LimitsAction.SetShortsMinutes(100))
        assertEquals(30, holder.state.value.draft.shortsMinutes)
    }

    @Test
    fun `daily input is clamped and snapped to five minute steps`() {
        val holder = LimitsStateHolder()

        holder.dispatch(LimitsAction.SetDailyMinutes(8))
        assertEquals(10, holder.state.value.draft.dailyMinutes)

        holder.dispatch(LimitsAction.SetDailyMinutes(42))
        assertEquals(40, holder.state.value.draft.dailyMinutes)

        holder.dispatch(LimitsAction.SetDailyMinutes(43))
        assertEquals(45, holder.state.value.draft.dailyMinutes)

        holder.dispatch(LimitsAction.SetDailyMinutes(999))
        assertEquals(240, holder.state.value.draft.dailyMinutes)
    }

    @Test
    fun `instagram interval is independent and clamped`() {
        val holder = LimitsStateHolder()

        holder.dispatch(LimitsAction.SetInstagramMinutes(100))
        assertEquals(30, holder.state.value.draft.instagramMinutes)
        holder.dispatch(LimitsAction.DecrementInstagram)
        assertEquals(29, holder.state.value.draft.instagramMinutes)
        assertEquals(LimitPreset.CUSTOM, holder.state.value.draft.preset)
    }

    @Test
    fun `steppers use exact one and five minute increments`() {
        val holder = LimitsStateHolder()

        holder.dispatch(LimitsAction.IncrementShorts)
        holder.dispatch(LimitsAction.DecrementDaily)

        assertEquals(6, holder.state.value.draft.shortsMinutes)
        assertEquals(40, holder.state.value.draft.dailyMinutes)
        assertEquals(LimitPreset.CUSTOM, holder.state.value.draft.preset)
    }

    @Test
    fun `daily below Shorts shows informational warning without blocking save`() {
        val effects = mutableListOf<LimitsEffect>()
        val holder = LimitsStateHolder(emitEffect = effects::add)

        holder.dispatch(LimitsAction.SetShortsMinutes(20))
        holder.dispatch(LimitsAction.SetDailyMinutes(10))

        assertTrue(holder.state.value.showDailyBeforeShortsWarning)
        holder.dispatch(LimitsAction.Save)
        assertTrue(effects.single() is LimitsEffect.Saved)
    }

    @Test
    fun `warning disappears when either related limit is disabled`() {
        val holder = LimitsStateHolder(
            initialValues = LimitsValues(
                preset = LimitPreset.CUSTOM,
                shortsMinutes = 20,
                dailyMinutes = 10,
            ),
        )
        assertTrue(holder.state.value.showDailyBeforeShortsWarning)

        holder.dispatch(LimitsAction.SetDailyEnabled(false))

        assertFalse(holder.state.value.showDailyBeforeShortsWarning)
    }

    @Test
    fun `save advances baseline emits values and clears dirty state`() {
        val effects = mutableListOf<LimitsEffect>()
        val holder = LimitsStateHolder(emitEffect = effects::add)
        holder.dispatch(LimitsAction.SetShortsMinutes(7))

        holder.dispatch(LimitsAction.Save)

        assertFalse(holder.state.value.hasUnsavedChanges)
        assertEquals(holder.state.value.saved, holder.state.value.draft)
        assertEquals(LimitsEffect.Saved(holder.state.value.draft), effects.single())
        assertEquals("Настройки сохранены", holder.state.value.announcement)
    }

    @Test
    fun `cancel restores every saved value and emits cancelled`() {
        val effects = mutableListOf<LimitsEffect>()
        val holder = LimitsStateHolder(emitEffect = effects::add)
        val original = holder.state.value.saved
        holder.dispatch(LimitsAction.SelectPreset(LimitPreset.GENTLE))
        holder.dispatch(LimitsAction.SetDailyEnabled(false))

        holder.dispatch(LimitsAction.Cancel)

        assertEquals(original, holder.state.value.draft)
        assertFalse(holder.state.value.hasUnsavedChanges)
        assertEquals(listOf(LimitsEffect.Cancelled), effects)
    }

    @Test
    fun `save and cancel are ignored without unsaved changes`() {
        val effects = mutableListOf<LimitsEffect>()
        val holder = LimitsStateHolder(emitEffect = effects::add)

        holder.dispatch(LimitsAction.Save)
        holder.dispatch(LimitsAction.Cancel)

        assertTrue(effects.isEmpty())
        assertEquals(null, holder.state.value.announcement)
    }

    @Test
    fun `TalkBack announcement describes the latest direct change`() {
        val holder = LimitsStateHolder()

        holder.dispatch(LimitsAction.SetDailyEnabled(false))
        assertEquals("Дневной лимит выключен", holder.state.value.announcement)

        holder.dispatch(LimitsAction.SetShortsMinutes(9))
        assertEquals("Пауза в Shorts: 9 минут", holder.state.value.announcement)
    }
}
