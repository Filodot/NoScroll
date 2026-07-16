package com.filodot.noscroll.core.model

import java.time.Instant

enum class ArithmeticOperation {
    ADD,
    SUBTRACT,
    MULTIPLY,
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
)
