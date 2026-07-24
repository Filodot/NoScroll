package com.filodot.noscroll.core.learning.progress

import com.filodot.noscroll.core.learning.model.AttemptResult
import com.filodot.noscroll.core.learning.model.ConceptMastery
import com.filodot.noscroll.core.learning.model.LearningAttempt
import com.filodot.noscroll.core.learning.model.SelfConfidence
import java.time.Duration

/**
 * Transparent deterministic policy for the first release.
 *
 * It intentionally avoids an opaque ML score: the user can understand why a concept progressed,
 * and a disputed AI task never changes mastery.
 */
class MasteryPolicy {
    fun update(
        current: ConceptMastery,
        attempt: LearningAttempt,
    ): ConceptMastery {
        require(attempt.conceptIds.contains(current.conceptId)) {
            "Attempt does not target concept ${current.conceptId}"
        }
        if (attempt.result == AttemptResult.REPLACED_AS_SUSPICIOUS) return current

        val correct = attempt.result == AttemptResult.CORRECT
        val nextConsecutive = if (correct) current.consecutiveCorrect + 1 else 0
        val scoreDelta = if (correct) correctDelta(attempt) else INCORRECT_DELTA
        val nextReviewAt = attempt.occurredAt.plus(
            if (correct) reviewDelay(nextConsecutive) else INCORRECT_RETRY_DELAY,
        )
        return current.copy(
            score = (current.score + scoreDelta).coerceIn(MIN_SCORE, MAX_SCORE),
            attemptCount = current.attemptCount + 1,
            correctCount = current.correctCount + if (correct) 1 else 0,
            consecutiveCorrect = nextConsecutive,
            successfulFormats = if (correct) {
                current.successfulFormats + attempt.activityKind
            } else {
                current.successfulFormats
            },
            successfulDates = if (correct) {
                current.successfulDates + attempt.localDate
            } else {
                current.successfulDates
            },
            lastAttemptAt = attempt.occurredAt,
            nextReviewAt = nextReviewAt,
        )
    }

    private fun correctDelta(attempt: LearningAttempt): Int {
        val hintPenalty = (attempt.hintsUsed.coerceAtLeast(0) * HINT_PENALTY)
            .coerceAtMost(MAX_HINT_PENALTY)
        val confidenceAdjustment = when (attempt.confidence) {
            SelfConfidence.LOW -> LOW_CONFIDENCE_BONUS
            SelfConfidence.MEDIUM -> 0
            SelfConfidence.HIGH -> HIGH_CONFIDENCE_PENALTY
        }
        return (BASE_CORRECT_DELTA - hintPenalty + confidenceAdjustment)
            .coerceAtLeast(MIN_CORRECT_DELTA)
    }

    private fun reviewDelay(consecutiveCorrect: Int): Duration = when (consecutiveCorrect) {
        1 -> Duration.ofMinutes(10)
        2 -> Duration.ofDays(1)
        3 -> Duration.ofDays(3)
        4 -> Duration.ofDays(7)
        5 -> Duration.ofDays(14)
        else -> Duration.ofDays(30)
    }
}

private const val MIN_SCORE = 0
private const val MAX_SCORE = 100
private const val BASE_CORRECT_DELTA = 14
private const val INCORRECT_DELTA = -8
private const val HINT_PENALTY = 3
private const val MAX_HINT_PENALTY = 6
private const val LOW_CONFIDENCE_BONUS = 2
private const val HIGH_CONFIDENCE_PENALTY = -1
private const val MIN_CORRECT_DELTA = 5
private val INCORRECT_RETRY_DELAY: Duration = Duration.ofMinutes(10)
