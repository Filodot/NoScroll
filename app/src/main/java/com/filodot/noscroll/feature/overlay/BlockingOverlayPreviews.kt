package com.filodot.noscroll.feature.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.filodot.noscroll.ui.theme.NoScrollTheme

@Preview(name = "Task gate", showBackground = true, locale = "ru")
@Composable
private fun TaskGatePreview() {
    OverlayPreviewFrame(taskState())
}

@Preview(name = "Task after three errors", showBackground = true, locale = "ru")
@Composable
private fun IncorrectTaskPreview() {
    OverlayPreviewFrame(
        taskState().copy(
            enforcement = task().copy(
                wrongAttempts = 3,
                answerStatus = TaskAnswerStatus.INCORRECT,
            ),
        ),
    )
}

@Preview(name = "Task solved", showBackground = true, locale = "ru")
@Composable
private fun SolvedTaskPreview() {
    OverlayPreviewFrame(
        taskState().copy(
            enforcement = task().copy(answerStatus = TaskAnswerStatus.CORRECT),
        ),
    )
}

@Preview(name = "Daily limit dark", showBackground = true, locale = "ru", uiMode = 0x20)
@Composable
private fun DailyLimitPreview() {
    OverlayPreviewFrame(
        BlockingOverlayUiState(
            enforcement = EnforcementUiState.DailyLimit(
                usedMinutes = 45,
                limitMinutes = 45,
            ),
        ),
        darkTheme = true,
    )
}

@Preview(
    name = "Emergency form 200%",
    showBackground = true,
    locale = "ru",
    fontScale = 2f,
)
@Composable
private fun EmergencyFormLargeTextPreview() {
    OverlayPreviewFrame(
        taskState().copy(
            emergencyForm = EmergencyFormUiState(
                reason = "Учебная трансляция",
            ),
        ),
    )
}

@Composable
private fun OverlayPreviewFrame(
    state: BlockingOverlayUiState,
    darkTheme: Boolean = false,
) {
    NoScrollTheme(darkTheme = darkTheme) {
        BlockingOverlayScreen(state = state, onAction = {})
    }
}

private fun taskState() = BlockingOverlayUiState(enforcement = task())

private fun task() = EnforcementUiState.TaskGate(
    taskId = "preview-task",
    visualExpression = "17 + 26",
    spokenExpression = "семнадцать плюс двадцать шесть",
    grantMinutes = 5,
)
