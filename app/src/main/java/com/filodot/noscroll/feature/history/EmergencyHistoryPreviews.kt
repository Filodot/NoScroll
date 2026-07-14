package com.filodot.noscroll.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.filodot.noscroll.ui.theme.NoScrollTheme

@Preview(name = "History empty", showBackground = true, locale = "ru")
@Composable
private fun EmptyHistoryPreview() {
    HistoryPreviewFrame(EmergencyHistoryUiState())
}

@Preview(name = "History with active event", showBackground = true, locale = "ru")
@Composable
private fun ActiveHistoryPreview() {
    HistoryPreviewFrame(EmergencyHistoryUiState(items = historyItems()))
}

@Preview(name = "History error dark", showBackground = true, locale = "ru", uiMode = 0x20)
@Composable
private fun ErrorHistoryPreview() {
    HistoryPreviewFrame(
        state = EmergencyHistoryUiState(loadError = "Локальная база временно недоступна"),
        darkTheme = true,
    )
}

@Preview(name = "Delete confirmation", showBackground = true, locale = "ru")
@Composable
private fun DeleteHistoryPreview() {
    HistoryPreviewFrame(
        EmergencyHistoryUiState(
            items = historyItems(),
            deleteConfirmationVisible = true,
        ),
    )
}

@Composable
private fun HistoryPreviewFrame(
    state: EmergencyHistoryUiState,
    darkTheme: Boolean = false,
) {
    NoScrollTheme(darkTheme = darkTheme) {
        EmergencyHistoryScreen(state = state, onAction = {})
    }
}

private fun historyItems() = listOf(
    EmergencyHistoryItemUi(
        id = "active",
        reason = "Смотрю учебную трансляцию",
        activatedAtLabel = "Сегодня, 14:32",
        durationMinutes = 18,
        youtubeMinutesDuring = 12,
        sourceLabel = "Сегодня",
    ),
    EmergencyHistoryItemUi(
        id = "completed",
        reason = "Рабочая инструкция",
        activatedAtLabel = "Вчера, 10:15",
        deactivatedAtLabel = "10:42",
        durationMinutes = 27,
        youtubeMinutesDuring = 19,
        sourceLabel = "Задание",
    ),
)
