package com.filodot.noscroll.core.model

import java.time.Instant

enum class ArithmeticOperation {
    ADD,
    SUBTRACT,
    MULTIPLY,
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
)
