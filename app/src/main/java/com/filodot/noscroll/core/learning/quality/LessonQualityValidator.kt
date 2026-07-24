package com.filodot.noscroll.core.learning.quality

import com.filodot.noscroll.core.learning.model.ActivityContent
import com.filodot.noscroll.core.learning.model.CodeCompletionContent
import com.filodot.noscroll.core.learning.model.CodeFixContent
import com.filodot.noscroll.core.learning.model.CodeOutputContent
import com.filodot.noscroll.core.learning.model.EvidenceSelectionContent
import com.filodot.noscroll.core.learning.model.FillBlankContent
import com.filodot.noscroll.core.learning.model.FlashcardContent
import com.filodot.noscroll.core.learning.model.GroundingMode
import com.filodot.noscroll.core.learning.model.LearningActivity
import com.filodot.noscroll.core.learning.model.LessonPackage
import com.filodot.noscroll.core.learning.model.LessonPackageStatus
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

enum class LessonValidationCode {
    INVALID_LESSON_ID,
    INVALID_COURSE_ID,
    INVALID_PLAN_VERSION,
    INVALID_TITLE,
    INVALID_INTRODUCTION,
    INVALID_ACTIVITY_COUNT,
    INVALID_DURATION,
    INVALID_ACTIVITY_ID,
    MISSING_CONCEPT,
    INVALID_PROMPT,
    INVALID_EXPLANATION,
    MISSING_CITATION,
    INVALID_CITATION,
    QUALITY_REJECTED,
    INVALID_OPTIONS,
    INVALID_ANSWER,
    INVALID_ORDER,
    INVALID_MATCHING,
    INVALID_BLANK,
    INVALID_RUBRIC,
    INVALID_NUMBER,
    INVALID_CODE,
    INVALID_TESTS,
}

data class LessonValidationIssue(
    val code: LessonValidationCode,
    val activityId: String? = null,
)

data class LessonValidationResult(
    val issues: List<LessonValidationIssue>,
) {
    val isValid: Boolean get() = issues.isEmpty()
}

/**
 * Deterministic first line of defence. Model-based critique is allowed only after this succeeds.
 */
class LessonQualityValidator {
    fun validate(lesson: LessonPackage): LessonValidationResult {
        val issues = buildList {
            if (lesson.id.isBlank()) add(LessonValidationIssue(LessonValidationCode.INVALID_LESSON_ID))
            if (lesson.courseId.isBlank()) {
                add(LessonValidationIssue(LessonValidationCode.INVALID_COURSE_ID))
            }
            if (lesson.planVersion <= 0) {
                add(LessonValidationIssue(LessonValidationCode.INVALID_PLAN_VERSION))
            }
            if (lesson.title.isBlank()) add(LessonValidationIssue(LessonValidationCode.INVALID_TITLE))
            if (lesson.introduction.isBlank()) {
                add(LessonValidationIssue(LessonValidationCode.INVALID_INTRODUCTION))
            }
            if (lesson.activities.size !in MIN_ACTIVITIES..MAX_ACTIVITIES) {
                add(LessonValidationIssue(LessonValidationCode.INVALID_ACTIVITY_COUNT))
            }
            val totalSeconds = lesson.activities.sumOf { it.estimatedSeconds.coerceAtLeast(0) }
            if (totalSeconds !in MIN_LESSON_SECONDS..MAX_LESSON_SECONDS) {
                add(LessonValidationIssue(LessonValidationCode.INVALID_DURATION))
            }
            lesson.activities.forEach { activity ->
                addAll(validateActivity(activity, lesson.groundingMode))
            }
            if (lesson.status == LessonPackageStatus.VALIDATED &&
                lesson.activities.any { !it.quality.accepted }
            ) {
                add(LessonValidationIssue(LessonValidationCode.QUALITY_REJECTED))
            }
        }
        return LessonValidationResult(issues.distinct())
    }

    private fun validateActivity(
        activity: LearningActivity,
        groundingMode: GroundingMode,
    ): List<LessonValidationIssue> = buildList {
        fun issue(code: LessonValidationCode) {
            add(LessonValidationIssue(code, activity.id))
        }

        if (activity.id.isBlank()) issue(LessonValidationCode.INVALID_ACTIVITY_ID)
        if (activity.conceptIds.isEmpty() || activity.conceptIds.any(String::isBlank)) {
            issue(LessonValidationCode.MISSING_CONCEPT)
        }
        if (activity.prompt.isBlank()) issue(LessonValidationCode.INVALID_PROMPT)
        if (activity.explanation.isBlank()) issue(LessonValidationCode.INVALID_EXPLANATION)
        if (activity.estimatedSeconds !in MIN_ACTIVITY_SECONDS..MAX_ACTIVITY_SECONDS) {
            issue(LessonValidationCode.INVALID_DURATION)
        }
        if (!activity.quality.accepted) issue(LessonValidationCode.QUALITY_REJECTED)
        if (groundingMode == GroundingMode.SOURCE_REQUIRED && activity.citations.isEmpty()) {
            issue(LessonValidationCode.MISSING_CITATION)
        }
        if (activity.citations.any {
                it.sourceId.isBlank() ||
                    it.chunkId.isBlank() ||
                    (it.pageNumber != null && it.pageNumber <= 0)
            }
        ) {
            issue(LessonValidationCode.INVALID_CITATION)
        }
        validateContent(activity.content).forEach(::issue)
    }

    private fun validateContent(content: ActivityContent): List<LessonValidationCode> = buildList {
        when (content) {
            is SingleChoiceContent -> {
                if (!validOptions(content.options, 2, 6)) add(LessonValidationCode.INVALID_OPTIONS)
                if (content.correctOptionId !in content.options.map { it.id }) {
                    add(LessonValidationCode.INVALID_ANSWER)
                }
            }

            is MultipleChoiceContent -> {
                if (!validOptions(content.options, 3, 8)) add(LessonValidationCode.INVALID_OPTIONS)
                val ids = content.options.map { it.id }.toSet()
                if (content.correctOptionIds.size !in 2 until content.options.size ||
                    !ids.containsAll(content.correctOptionIds)
                ) {
                    add(LessonValidationCode.INVALID_ANSWER)
                }
            }

            is TrueFalseContent -> {
                if (content.statement.isBlank()) add(LessonValidationCode.INVALID_PROMPT)
                if (!content.expected && content.correction.isNullOrBlank()) {
                    add(LessonValidationCode.INVALID_ANSWER)
                }
            }

            is OrderingContent -> {
                val ids = content.items.map { it.id }
                if (content.items.size < 3 ||
                    ids.any(String::isBlank) ||
                    content.items.any { it.text.isBlank() } ||
                    ids.toSet().size != ids.size
                ) {
                    add(LessonValidationCode.INVALID_OPTIONS)
                }
                if (content.correctOrderIds.size != ids.size ||
                    content.correctOrderIds.toSet() != ids.toSet()
                ) {
                    add(LessonValidationCode.INVALID_ORDER)
                }
            }

            is MatchingContent -> {
                val leftIds = content.left.map { it.id }
                val rightIds = content.right.map { it.id }
                val pairedLeft = content.correctPairs.map { it.leftId }
                val pairedRight = content.correctPairs.map { it.rightId }
                if (content.left.size < 2 ||
                    content.right.size < 2 ||
                    leftIds.toSet().size != leftIds.size ||
                    rightIds.toSet().size != rightIds.size ||
                    content.left.any { it.id.isBlank() || it.text.isBlank() } ||
                    content.right.any { it.id.isBlank() || it.text.isBlank() }
                ) {
                    add(LessonValidationCode.INVALID_OPTIONS)
                }
                if (pairedLeft.toSet() != leftIds.toSet() ||
                    pairedRight.toSet() != rightIds.toSet() ||
                    pairedLeft.size != pairedLeft.toSet().size ||
                    pairedRight.size != pairedRight.toSet().size
                ) {
                    add(LessonValidationCode.INVALID_MATCHING)
                }
            }

            is FillBlankContent -> {
                if (content.textWithBlank.countOccurrences(BLANK_MARKER) != 1 ||
                    content.acceptedAnswers.none { it.isNotBlank() }
                ) {
                    add(LessonValidationCode.INVALID_BLANK)
                }
            }

            is ShortAnswerContent -> if (
                content.acceptedAnswers.none { it.isNotBlank() } && content.rubric.isNullOrBlank()
            ) {
                add(LessonValidationCode.INVALID_RUBRIC)
            }

            is NumericAnswerContent -> if (
                !content.expected.isFinite() ||
                !content.tolerance.isFinite() ||
                content.tolerance < 0
            ) {
                add(LessonValidationCode.INVALID_NUMBER)
            }

            is FlashcardContent -> if (content.answer.isBlank()) {
                add(LessonValidationCode.INVALID_ANSWER)
            }

            is EvidenceSelectionContent -> {
                if (!validOptions(content.options, 2, 8)) add(LessonValidationCode.INVALID_OPTIONS)
                val ids = content.options.map { it.id }.toSet()
                if (content.correctOptionIds.isEmpty() ||
                    !ids.containsAll(content.correctOptionIds)
                ) {
                    add(LessonValidationCode.INVALID_ANSWER)
                }
            }

            is ScenarioContent -> {
                if (!validOptions(content.options, 2, 6)) add(LessonValidationCode.INVALID_OPTIONS)
                val ids = content.options.map { it.id }.toSet()
                if (content.correctOptionId !in ids ||
                    content.consequenceByOptionId.keys != ids ||
                    content.consequenceByOptionId.values.any(String::isBlank)
                ) {
                    add(LessonValidationCode.INVALID_ANSWER)
                }
            }

            is CodeOutputContent -> if (
                content.code.isBlank() || content.acceptedOutputs.none { it.isNotBlank() }
            ) {
                add(LessonValidationCode.INVALID_CODE)
            }

            is CodeCompletionContent -> {
                if (content.codeWithBlank.countOccurrences(CODE_MARKER) != 1 ||
                    content.acceptedSnippets.none { it.isNotBlank() }
                ) {
                    add(LessonValidationCode.INVALID_CODE)
                }
                if (content.tests.anyInvalid()) add(LessonValidationCode.INVALID_TESTS)
            }

            is CodeFixContent -> {
                if (content.brokenCode.isBlank()) add(LessonValidationCode.INVALID_CODE)
                if (content.tests.isEmpty() || content.tests.anyInvalid()) {
                    add(LessonValidationCode.INVALID_TESTS)
                }
            }

            is MiniCodeContent -> {
                if (content.starterCode.isBlank()) add(LessonValidationCode.INVALID_CODE)
                if (content.tests.isEmpty() || content.tests.anyInvalid()) {
                    add(LessonValidationCode.INVALID_TESTS)
                }
            }

            is TeachBackContent -> if (
                content.rubric.isBlank() ||
                content.keyPoints.isEmpty() ||
                content.keyPoints.any(String::isBlank)
            ) {
                add(LessonValidationCode.INVALID_RUBRIC)
            }
        }
    }

    private fun validOptions(
        options: List<com.filodot.noscroll.core.learning.model.ChoiceOption>,
        minimum: Int,
        maximum: Int,
    ): Boolean {
        val ids = options.map { it.id }
        val normalizedText = options.map { it.text.trim().lowercase() }
        return options.size in minimum..maximum &&
            options.none { it.id.isBlank() || it.text.isBlank() } &&
            ids.toSet().size == ids.size &&
            normalizedText.toSet().size == normalizedText.size
    }
}

private fun String.countOccurrences(marker: String): Int =
    windowed(marker.length).count { it == marker }

private fun List<com.filodot.noscroll.core.learning.model.CodeTestCase>.anyInvalid(): Boolean =
    any { it.id.isBlank() || it.expectedOutput.isBlank() } ||
        map { it.id }.toSet().size != size

private const val BLANK_MARKER = "{{blank}}"
private const val CODE_MARKER = "{{code}}"
private const val MIN_ACTIVITIES = 1
private const val MAX_ACTIVITIES = 5
private const val MIN_ACTIVITY_SECONDS = 15
private const val MAX_ACTIVITY_SECONDS = 300
private const val MIN_LESSON_SECONDS = 30
private const val MAX_LESSON_SECONDS = 600
