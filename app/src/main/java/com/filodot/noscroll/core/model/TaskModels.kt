package com.filodot.noscroll.core.model

import java.time.Instant

enum class ArithmeticOperation {
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
}

/** Difficulty is task-type agnostic so future learning tasks can use the same gate policy. */
enum class TaskDifficulty {
    EASY,
    MEDIUM,
    HARD,
}

/** Describes why a task was requested, independently from the task's concrete content type. */
enum class TaskTrigger {
    ENTRY,
    INTERVAL,
}

/** Application whose use is unlocked by completing the task. */
enum class TaskTarget {
    YOUTUBE_SHORTS,
    YOUTUBE,
    INSTAGRAM,
    PINTEREST,
    CHROME,
}

/** Content family. Adding a new family does not change the difficulty policy. */
enum class TaskType {
    ARITHMETIC,
    PUSH_UPS,
    CUSTOM,
    LEARNING,
}

enum class TaskCompletionMode {
    CHECKED_ANSWER,
    SINGLE_CHOICE,
    MANUAL_CONFIRMATION,
}

data class TaskChoice(
    val id: String,
    val text: String,
)

data class CustomTaskPreset(
    val id: String,
    val title: String,
    val instruction: String,
    val enabled: Boolean = true,
    val createdAt: Instant,
)

data class PendingTask(
    val id: String,
    val operation: ArithmeticOperation,
    val leftOperand: Int,
    val rightOperand: Int,
    val expectedAnswer: Int,
    val createdAt: Instant,
    val wrongAttempts: Int = 0,
    val solved: Boolean = false,
    val difficulty: TaskDifficulty = TaskDifficulty.MEDIUM,
    val trigger: TaskTrigger = TaskTrigger.INTERVAL,
    val target: TaskTarget = TaskTarget.YOUTUBE_SHORTS,
    val type: TaskType = TaskType.ARITHMETIC,
    val completionMode: TaskCompletionMode = TaskCompletionMode.CHECKED_ANSWER,
    val prompt: String = "",
    val customPresetId: String? = null,
    val choices: List<TaskChoice> = emptyList(),
    val expectedChoiceId: String? = null,
    val learningCourseId: String? = null,
    val learningLessonId: String? = null,
    val learningActivityId: String? = null,
    val learningMaterial: String? = null,
    val learningExplanation: String? = null,
)
