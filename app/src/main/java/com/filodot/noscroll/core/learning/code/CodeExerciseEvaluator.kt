package com.filodot.noscroll.core.learning.code

import com.filodot.noscroll.core.learning.model.ActivityContent

enum class CodeEvaluationStatus {
    CORRECT,
    INCORRECT,
    INVALID_SUBMISSION,
    UNSUPPORTED,
}

data class CodeEvaluation(
    val status: CodeEvaluationStatus,
    val message: String? = null,
)

fun interface CodeExerciseEvaluator {
    suspend fun evaluate(content: ActivityContent, submission: String): CodeEvaluation
}
