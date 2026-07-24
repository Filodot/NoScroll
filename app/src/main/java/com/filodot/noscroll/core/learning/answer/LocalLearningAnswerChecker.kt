package com.filodot.noscroll.core.learning.answer

import com.filodot.noscroll.core.learning.model.ActivityContent
import com.filodot.noscroll.core.learning.model.CodeCompletionContent
import com.filodot.noscroll.core.learning.model.CodeFixContent
import com.filodot.noscroll.core.learning.model.CodeOutputContent
import com.filodot.noscroll.core.learning.model.EvidenceSelectionContent
import com.filodot.noscroll.core.learning.model.FillBlankContent
import com.filodot.noscroll.core.learning.model.FlashcardContent
import com.filodot.noscroll.core.learning.model.MatchingContent
import com.filodot.noscroll.core.learning.model.MiniCodeContent
import com.filodot.noscroll.core.learning.model.MultipleChoiceContent
import com.filodot.noscroll.core.learning.model.NumericAnswerContent
import com.filodot.noscroll.core.learning.model.OrderingContent
import com.filodot.noscroll.core.learning.model.ScenarioContent
import com.filodot.noscroll.core.learning.model.ShortAnswerContent
import com.filodot.noscroll.core.learning.model.SingleChoiceContent
import com.filodot.noscroll.core.learning.model.TeachBackContent
import com.filodot.noscroll.core.learning.model.TrueFalseContent
import kotlin.math.abs

sealed interface LearningAnswer {
    data class Choices(val optionIds: Set<String>) : LearningAnswer
    data class Ordered(val itemIds: List<String>) : LearningAnswer
    data class Matches(val rightIdByLeftId: Map<String, String>) : LearningAnswer
    data class Text(val value: String) : LearningAnswer
    data class BooleanValue(val value: Boolean) : LearningAnswer
    data object ConfirmedRecall : LearningAnswer
}

enum class AnswerEvaluation {
    CORRECT,
    INCORRECT,
    REQUIRES_EXTERNAL_EVALUATION,
    INCOMPATIBLE_ANSWER,
}

class LocalLearningAnswerChecker {
    fun evaluate(
        content: ActivityContent,
        answer: LearningAnswer,
    ): AnswerEvaluation = when (content) {
        is SingleChoiceContent -> choices(answer) {
            it == setOf(content.correctOptionId)
        }

        is MultipleChoiceContent -> choices(answer) {
            it == content.correctOptionIds
        }

        is TrueFalseContent -> when (answer) {
            is LearningAnswer.BooleanValue ->
                result(answer.value == content.expected)

            else -> AnswerEvaluation.INCOMPATIBLE_ANSWER
        }

        is OrderingContent -> when (answer) {
            is LearningAnswer.Ordered -> result(answer.itemIds == content.correctOrderIds)
            else -> AnswerEvaluation.INCOMPATIBLE_ANSWER
        }

        is MatchingContent -> when (answer) {
            is LearningAnswer.Matches -> {
                val expected = content.correctPairs.associate { it.leftId to it.rightId }
                result(answer.rightIdByLeftId == expected)
            }

            else -> AnswerEvaluation.INCOMPATIBLE_ANSWER
        }

        is FillBlankContent -> text(answer) { value ->
            content.acceptedAnswers.any {
                normalize(value, content.caseSensitive) == normalize(it, content.caseSensitive)
            }
        }

        is ShortAnswerContent -> {
            if (content.acceptedAnswers.isEmpty()) {
                AnswerEvaluation.REQUIRES_EXTERNAL_EVALUATION
            } else {
                text(answer) { value ->
                    content.acceptedAnswers.any {
                        normalize(value, content.caseSensitive) ==
                            normalize(it, content.caseSensitive)
                    }
                }
            }
        }

        is NumericAnswerContent -> text(answer) { value ->
            val parsed = value.trim().replace(',', '.').toDoubleOrNull() ?: return@text false
            abs(parsed - content.expected) <= content.tolerance
        }

        is FlashcardContent -> result(answer == LearningAnswer.ConfirmedRecall)

        is EvidenceSelectionContent -> choices(answer) {
            it == content.correctOptionIds
        }

        is ScenarioContent -> choices(answer) {
            it == setOf(content.correctOptionId)
        }

        is CodeOutputContent -> text(answer) { value ->
            content.acceptedOutputs.any { normalizeCodeOutput(value) == normalizeCodeOutput(it) }
        }

        is CodeCompletionContent -> text(answer) { value ->
            content.acceptedSnippets.any { normalizeCode(value) == normalizeCode(it) }
        }

        is CodeFixContent,
        is MiniCodeContent,
        is TeachBackContent,
        -> AnswerEvaluation.REQUIRES_EXTERNAL_EVALUATION
    }

    private inline fun choices(
        answer: LearningAnswer,
        predicate: (Set<String>) -> Boolean,
    ): AnswerEvaluation = when (answer) {
        is LearningAnswer.Choices -> result(predicate(answer.optionIds))
        else -> AnswerEvaluation.INCOMPATIBLE_ANSWER
    }

    private inline fun text(
        answer: LearningAnswer,
        predicate: (String) -> Boolean,
    ): AnswerEvaluation = when (answer) {
        is LearningAnswer.Text -> result(predicate(answer.value))
        else -> AnswerEvaluation.INCOMPATIBLE_ANSWER
    }

    private fun result(correct: Boolean): AnswerEvaluation =
        if (correct) AnswerEvaluation.CORRECT else AnswerEvaluation.INCORRECT
}

private fun normalize(value: String, caseSensitive: Boolean): String {
    val normalized = value.trim().replace(Regex("\\s+"), " ")
    return if (caseSensitive) normalized else normalized.lowercase()
}

private fun normalizeCodeOutput(value: String): String =
    value.replace("\r\n", "\n").trimEnd()

private fun normalizeCode(value: String): String =
    value.replace("\r\n", "\n").trim()
