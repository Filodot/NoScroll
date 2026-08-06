package com.filodot.noscroll.feature.limits

import com.filodot.noscroll.core.model.LimitPreset
import com.filodot.noscroll.core.model.UserSettings
import com.filodot.noscroll.core.settings.DAILY_LIMIT_RANGE
import com.filodot.noscroll.core.settings.DAILY_LIMIT_STEP_MINUTES
import com.filodot.noscroll.core.settings.LimitPresets
import com.filodot.noscroll.core.settings.SHORTS_INTERVAL_RANGE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LimitsValues(
    val preset: LimitPreset = LimitPreset.BALANCED,
    val shortsEnabled: Boolean = true,
    val shortsMinutes: Int = UserSettings.DEFAULT_SHORTS_INTERVAL_MINUTES,
    val dailyEnabled: Boolean = true,
    val dailyMinutes: Int = UserSettings.DEFAULT_DAILY_LIMIT_MINUTES,
    val instagramEnabled: Boolean = true,
    val instagramMinutes: Int = UserSettings.DEFAULT_INSTAGRAM_INTERVAL_MINUTES,
    val youtubeEnabled: Boolean = false,
    val youtubeMinutes: Int = UserSettings.DEFAULT_YOUTUBE_INTERVAL_MINUTES,
    val pinterestEnabled: Boolean = false,
    val pinterestMinutes: Int = UserSettings.DEFAULT_PINTEREST_INTERVAL_MINUTES,
    val chromeEnabled: Boolean = false,
    val chromeMinutes: Int = UserSettings.DEFAULT_CHROME_INTERVAL_MINUTES,
)

data class LimitsUiState(
    val saved: LimitsValues = LimitsValues(),
    val draft: LimitsValues = saved,
    val announcement: String? = null,
) {
    val hasUnsavedChanges: Boolean
        get() = draft != saved

    val showDailyBeforeShortsWarning: Boolean
        get() = draft.shortsEnabled &&
            draft.dailyEnabled &&
            draft.dailyMinutes < draft.shortsMinutes

    val summary: String
        get() = when {
            draft.shortsEnabled && draft.dailyEnabled ->
                "Через каждые ${draft.shortsMinutes} минут Shorts появится пример. " +
                    "После ${draft.dailyMinutes} минут всего YouTube приложение будет закрыто " +
                    "до полуночи или Emergency Stop." + appIntervalsSummary()

            draft.shortsEnabled ->
                "Через каждые ${draft.shortsMinutes} минут Shorts появится пример. " +
                    "Дневной лимит YouTube выключен." + appIntervalsSummary()

            draft.dailyEnabled ->
                "Паузы в Shorts выключены. После ${draft.dailyMinutes} минут всего YouTube " +
                    "приложение будет закрыто до полуночи или Emergency Stop." + appIntervalsSummary()

            else -> "Паузы в Shorts и дневной лимит выключены." + appIntervalsSummary()
        }

    private fun appIntervalsSummary(): String {
        val enabled = buildList {
            if (draft.youtubeEnabled) add("YouTube — ${draft.youtubeMinutes}")
            if (draft.instagramEnabled) add("Instagram — ${draft.instagramMinutes}")
            if (draft.pinterestEnabled) add("Pinterest — ${draft.pinterestMinutes}")
            if (draft.chromeEnabled) add("Chrome — ${draft.chromeMinutes}")
        }
        return if (enabled.isEmpty()) {
            " Интервальные ограничения приложений выключены."
        } else {
            " Интервалы доступа: ${enabled.joinToString(", ")} мин."
        }
    }
}

sealed interface LimitsAction {
    data class SelectPreset(val preset: LimitPreset) : LimitsAction
    data class SetShortsEnabled(val enabled: Boolean) : LimitsAction
    data class SetShortsMinutes(val minutes: Int) : LimitsAction
    data class SetDailyEnabled(val enabled: Boolean) : LimitsAction
    data class SetDailyMinutes(val minutes: Int) : LimitsAction
    data class SetInstagramEnabled(val enabled: Boolean) : LimitsAction
    data class SetInstagramMinutes(val minutes: Int) : LimitsAction
    data class SetYoutubeEnabled(val enabled: Boolean) : LimitsAction
    data class SetYoutubeMinutes(val minutes: Int) : LimitsAction
    data class SetPinterestEnabled(val enabled: Boolean) : LimitsAction
    data class SetPinterestMinutes(val minutes: Int) : LimitsAction
    data class SetChromeEnabled(val enabled: Boolean) : LimitsAction
    data class SetChromeMinutes(val minutes: Int) : LimitsAction
    data object DecrementShorts : LimitsAction
    data object IncrementShorts : LimitsAction
    data object DecrementDaily : LimitsAction
    data object IncrementDaily : LimitsAction
    data object DecrementInstagram : LimitsAction
    data object IncrementInstagram : LimitsAction
    data object DecrementYoutube : LimitsAction
    data object IncrementYoutube : LimitsAction
    data object DecrementPinterest : LimitsAction
    data object IncrementPinterest : LimitsAction
    data object DecrementChrome : LimitsAction
    data object IncrementChrome : LimitsAction
    data object Save : LimitsAction
    data object Cancel : LimitsAction
}

sealed interface LimitsEffect {
    data class Saved(val values: LimitsValues) : LimitsEffect
    data object Cancelled : LimitsEffect
}

class LimitsStateHolder(
    initialValues: LimitsValues = LimitsValues(),
    private val emitEffect: (LimitsEffect) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(
        LimitsUiState(
            saved = normalize(initialValues),
            draft = normalize(initialValues),
        ),
    )
    val state: StateFlow<LimitsUiState> = mutableState.asStateFlow()

    fun dispatch(action: LimitsAction) {
        when (action) {
            is LimitsAction.SelectPreset -> selectPreset(action.preset)
            is LimitsAction.SetShortsEnabled -> updateDraft(
                announcement = if (action.enabled) {
                    "Пауза в Shorts включена"
                } else {
                    "Пауза в Shorts выключена"
                },
            ) { it.copy(shortsEnabled = action.enabled) }

            is LimitsAction.SetShortsMinutes -> setShortsMinutes(action.minutes)
            is LimitsAction.SetDailyEnabled -> updateDraft(
                announcement = if (action.enabled) {
                    "Дневной лимит включён"
                } else {
                    "Дневной лимит выключен"
                },
            ) { it.copy(dailyEnabled = action.enabled) }

            is LimitsAction.SetDailyMinutes -> setDailyMinutes(action.minutes)
            is LimitsAction.SetInstagramEnabled -> updateDraft(
                announcement = if (action.enabled) {
                    "Ограничение Instagram включено"
                } else {
                    "Ограничение Instagram выключено"
                },
            ) { it.copy(instagramEnabled = action.enabled) }

            is LimitsAction.SetInstagramMinutes -> setInstagramMinutes(action.minutes)
            is LimitsAction.SetYoutubeEnabled -> setAppEnabled(
                "YouTube",
                action.enabled,
            ) { it.copy(youtubeEnabled = action.enabled) }

            is LimitsAction.SetYoutubeMinutes -> setYoutubeMinutes(action.minutes)
            is LimitsAction.SetPinterestEnabled -> setAppEnabled(
                "Pinterest",
                action.enabled,
            ) { it.copy(pinterestEnabled = action.enabled) }

            is LimitsAction.SetPinterestMinutes -> setPinterestMinutes(action.minutes)
            is LimitsAction.SetChromeEnabled -> setAppEnabled(
                "Chrome",
                action.enabled,
            ) { it.copy(chromeEnabled = action.enabled) }

            is LimitsAction.SetChromeMinutes -> setChromeMinutes(action.minutes)
            LimitsAction.DecrementShorts ->
                setShortsMinutes(mutableState.value.draft.shortsMinutes - 1)

            LimitsAction.IncrementShorts ->
                setShortsMinutes(mutableState.value.draft.shortsMinutes + 1)

            LimitsAction.DecrementDaily ->
                setDailyMinutes(
                    mutableState.value.draft.dailyMinutes - DAILY_LIMIT_STEP_MINUTES,
                )

            LimitsAction.IncrementDaily ->
                setDailyMinutes(
                    mutableState.value.draft.dailyMinutes + DAILY_LIMIT_STEP_MINUTES,
                )

            LimitsAction.DecrementInstagram ->
                setInstagramMinutes(mutableState.value.draft.instagramMinutes - 1)

            LimitsAction.IncrementInstagram ->
                setInstagramMinutes(mutableState.value.draft.instagramMinutes + 1)

            LimitsAction.DecrementYoutube ->
                setYoutubeMinutes(mutableState.value.draft.youtubeMinutes - 1)

            LimitsAction.IncrementYoutube ->
                setYoutubeMinutes(mutableState.value.draft.youtubeMinutes + 1)

            LimitsAction.DecrementPinterest ->
                setPinterestMinutes(mutableState.value.draft.pinterestMinutes - 1)

            LimitsAction.IncrementPinterest ->
                setPinterestMinutes(mutableState.value.draft.pinterestMinutes + 1)

            LimitsAction.DecrementChrome ->
                setChromeMinutes(mutableState.value.draft.chromeMinutes - 1)

            LimitsAction.IncrementChrome ->
                setChromeMinutes(mutableState.value.draft.chromeMinutes + 1)

            LimitsAction.Save -> save()
            LimitsAction.Cancel -> cancel()
        }
    }

    private fun selectPreset(preset: LimitPreset) {
        val definition = LimitPresets.definition(preset)
        val current = mutableState.value.draft
        val next = if (preset == LimitPreset.CUSTOM) {
            current.copy(preset = LimitPreset.CUSTOM)
        } else {
            current.copy(
                preset = preset,
                shortsMinutes = requireNotNull(definition.shortsIntervalMinutes),
                dailyMinutes = requireNotNull(definition.dailyLimitMinutes),
            )
        }
        mutableState.value = mutableState.value.copy(
            draft = next,
            announcement = "Выбран режим ${presetLabel(preset)}: " +
                "${next.shortsMinutes} минут Shorts, ${next.dailyMinutes} минут YouTube",
        )
    }

    private fun setShortsMinutes(minutes: Int) {
        val normalized = minutes.coerceIn(SHORTS_INTERVAL_RANGE)
        updateDraft(announcement = "Пауза в Shorts: $normalized минут") {
            it.copy(
                preset = LimitPreset.CUSTOM,
                shortsMinutes = normalized,
            )
        }
    }

    private fun setDailyMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(DAILY_LIMIT_RANGE)
        val normalized = (
            (clamped + DAILY_LIMIT_STEP_MINUTES / 2) /
                DAILY_LIMIT_STEP_MINUTES * DAILY_LIMIT_STEP_MINUTES
            ).coerceIn(DAILY_LIMIT_RANGE)
        updateDraft(announcement = "Дневной лимит: $normalized минут") {
            it.copy(
                preset = LimitPreset.CUSTOM,
                dailyMinutes = normalized,
            )
        }
    }

    private fun setInstagramMinutes(minutes: Int) {
        val normalized = minutes.coerceIn(SHORTS_INTERVAL_RANGE)
        updateDraft(announcement = "Интервал Instagram: $normalized минут") {
            it.copy(
                preset = LimitPreset.CUSTOM,
                instagramMinutes = normalized,
            )
        }
    }

    private fun setYoutubeMinutes(minutes: Int) = setAppMinutes("YouTube", minutes) {
        it.copy(youtubeMinutes = minutes.coerceIn(SHORTS_INTERVAL_RANGE))
    }

    private fun setPinterestMinutes(minutes: Int) = setAppMinutes("Pinterest", minutes) {
        it.copy(pinterestMinutes = minutes.coerceIn(SHORTS_INTERVAL_RANGE))
    }

    private fun setChromeMinutes(minutes: Int) = setAppMinutes("Chrome", minutes) {
        it.copy(chromeMinutes = minutes.coerceIn(SHORTS_INTERVAL_RANGE))
    }

    private fun setAppEnabled(
        label: String,
        enabled: Boolean,
        transform: (LimitsValues) -> LimitsValues,
    ) = updateDraft(
        announcement = "Ограничение $label ${if (enabled) "включено" else "выключено"}",
        transform = transform,
    )

    private fun setAppMinutes(
        label: String,
        minutes: Int,
        transform: (LimitsValues) -> LimitsValues,
    ) {
        val normalized = minutes.coerceIn(SHORTS_INTERVAL_RANGE)
        updateDraft(announcement = "Интервал $label: $normalized минут") {
            transform(it).copy(preset = LimitPreset.CUSTOM)
        }
    }

    private fun save() {
        val current = mutableState.value
        if (!current.hasUnsavedChanges) return
        mutableState.value = current.copy(
            saved = current.draft,
            announcement = "Настройки сохранены",
        )
        emitEffect(LimitsEffect.Saved(current.draft))
    }

    private fun cancel() {
        val current = mutableState.value
        if (!current.hasUnsavedChanges) return
        mutableState.value = current.copy(
            draft = current.saved,
            announcement = "Изменения отменены",
        )
        emitEffect(LimitsEffect.Cancelled)
    }

    private fun updateDraft(
        announcement: String,
        transform: (LimitsValues) -> LimitsValues,
    ) {
        val current = mutableState.value
        mutableState.value = current.copy(
            draft = normalize(transform(current.draft)),
            announcement = announcement,
        )
    }

    private companion object {
        fun normalize(values: LimitsValues): LimitsValues = values.copy(
            shortsMinutes = values.shortsMinutes.coerceIn(SHORTS_INTERVAL_RANGE),
            dailyMinutes = (
                (values.dailyMinutes.coerceIn(DAILY_LIMIT_RANGE) +
                    DAILY_LIMIT_STEP_MINUTES / 2) /
                    DAILY_LIMIT_STEP_MINUTES * DAILY_LIMIT_STEP_MINUTES
                ).coerceIn(DAILY_LIMIT_RANGE),
            instagramMinutes = values.instagramMinutes.coerceIn(SHORTS_INTERVAL_RANGE),
            youtubeMinutes = values.youtubeMinutes.coerceIn(SHORTS_INTERVAL_RANGE),
            pinterestMinutes = values.pinterestMinutes.coerceIn(SHORTS_INTERVAL_RANGE),
            chromeMinutes = values.chromeMinutes.coerceIn(SHORTS_INTERVAL_RANGE),
        )

        fun presetLabel(preset: LimitPreset): String = when (preset) {
            LimitPreset.GENTLE -> "Мягкий"
            LimitPreset.BALANCED -> "Сбалансированный"
            LimitPreset.STRICT -> "Строгий"
            LimitPreset.CUSTOM -> "Свой"
        }
    }
}
