package com.filodot.noscroll.core.learning.quality

import com.filodot.noscroll.core.learning.content.StaticLearningCatalog
import com.filodot.noscroll.core.learning.model.ChoiceOption
import com.filodot.noscroll.core.learning.model.GroundingMode
import com.filodot.noscroll.core.learning.model.SingleChoiceContent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonQualityValidatorTest {
    private val validator = LessonQualityValidator()

    @Test
    fun `static offline lesson satisfies the production contract`() {
        val result = validator.validate(StaticLearningCatalog.firstLesson)

        assertTrue(result.issues.toString(), result.isValid)
    }

    @Test
    fun `source grounded lesson requires a citation for every activity`() {
        val lesson = StaticLearningCatalog.firstLesson.copy(
            groundingMode = GroundingMode.SOURCE_REQUIRED,
        )

        val result = validator.validate(lesson)

        assertFalse(result.isValid)
        assertTrue(
            result.issues.any { it.code == LessonValidationCode.MISSING_CITATION },
        )
    }

    @Test
    fun `single choice rejects an answer missing from options`() {
        val activity = StaticLearningCatalog.firstLesson.activities.first().copy(
            content = SingleChoiceContent(
                options = listOf(
                    ChoiceOption("a", "Первый"),
                    ChoiceOption("b", "Второй"),
                ),
                correctOptionId = "unknown",
            ),
        )
        val lesson = StaticLearningCatalog.firstLesson.copy(activities = listOf(activity))

        val result = validator.validate(lesson)

        assertTrue(result.issues.any { it.code == LessonValidationCode.INVALID_ANSWER })
    }

    @Test
    fun `rejected semantic quality cannot be marked validated`() {
        val activity = StaticLearningCatalog.firstLesson.activities.first()
        val rejected = activity.copy(
            quality = activity.quality.copy(correctness = 50),
        )

        val result = validator.validate(
            StaticLearningCatalog.firstLesson.copy(activities = listOf(rejected)),
        )

        assertTrue(result.issues.any { it.code == LessonValidationCode.QUALITY_REJECTED })
    }

    @Test
    fun `duplicate visible options are rejected even with different ids`() {
        val activity = StaticLearningCatalog.firstLesson.activities.first().copy(
            content = SingleChoiceContent(
                options = listOf(
                    ChoiceOption("a", "Одинаково"),
                    ChoiceOption("b", " одинаково "),
                ),
                correctOptionId = "a",
            ),
        )

        val result = validator.validate(
            StaticLearningCatalog.firstLesson.copy(activities = listOf(activity)),
        )

        assertTrue(result.issues.any { it.code == LessonValidationCode.INVALID_OPTIONS })
    }
}
