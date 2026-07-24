package com.filodot.noscroll.core.learning.answer

import com.filodot.noscroll.core.learning.model.ChoiceOption
import com.filodot.noscroll.core.learning.model.CodeFixContent
import com.filodot.noscroll.core.learning.model.CodeLanguage
import com.filodot.noscroll.core.learning.model.CodeOutputContent
import com.filodot.noscroll.core.learning.model.FillBlankContent
import com.filodot.noscroll.core.learning.model.NumericAnswerContent
import com.filodot.noscroll.core.learning.model.OrderingContent
import com.filodot.noscroll.core.learning.model.OrderingItem
import com.filodot.noscroll.core.learning.model.SingleChoiceContent
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalLearningAnswerCheckerTest {
    private val checker = LocalLearningAnswerChecker()

    @Test
    fun `single choice requires exact correct option`() {
        val content = SingleChoiceContent(
            options = listOf(ChoiceOption("a", "A"), ChoiceOption("b", "B")),
            correctOptionId = "b",
        )

        assertEquals(
            AnswerEvaluation.CORRECT,
            checker.evaluate(content, LearningAnswer.Choices(setOf("b"))),
        )
        assertEquals(
            AnswerEvaluation.INCORRECT,
            checker.evaluate(content, LearningAnswer.Choices(setOf("a"))),
        )
    }

    @Test
    fun `ordering remains order sensitive`() {
        val content = OrderingContent(
            items = listOf(
                OrderingItem("a", "A"),
                OrderingItem("b", "B"),
                OrderingItem("c", "C"),
            ),
            correctOrderIds = listOf("b", "a", "c"),
        )

        assertEquals(
            AnswerEvaluation.CORRECT,
            checker.evaluate(content, LearningAnswer.Ordered(listOf("b", "a", "c"))),
        )
        assertEquals(
            AnswerEvaluation.INCORRECT,
            checker.evaluate(content, LearningAnswer.Ordered(listOf("a", "b", "c"))),
        )
    }

    @Test
    fun `text and numeric answers normalize safe user variations`() {
        assertEquals(
            AnswerEvaluation.CORRECT,
            checker.evaluate(
                FillBlankContent("x={{blank}}", setOf("Hello world")),
                LearningAnswer.Text("  HELLO   world "),
            ),
        )
        assertEquals(
            AnswerEvaluation.CORRECT,
            checker.evaluate(
                NumericAnswerContent(expected = 3.14, tolerance = 0.01),
                LearningAnswer.Text("3,141"),
            ),
        )
    }

    @Test
    fun `code output ignores final line break but not wrong output`() {
        val content = CodeOutputContent(
            language = CodeLanguage.PYTHON,
            code = "print(8)",
            acceptedOutputs = setOf("8"),
        )

        assertEquals(
            AnswerEvaluation.CORRECT,
            checker.evaluate(content, LearningAnswer.Text("8\n")),
        )
        assertEquals(
            AnswerEvaluation.INCORRECT,
            checker.evaluate(content, LearningAnswer.Text("9")),
        )
    }

    @Test
    fun `executable code waits for sandbox instead of guessing`() {
        val content = CodeFixContent(
            language = CodeLanguage.PYTHON,
            brokenCode = "print(",
            tests = emptyList(),
        )

        assertEquals(
            AnswerEvaluation.REQUIRES_EXTERNAL_EVALUATION,
            checker.evaluate(content, LearningAnswer.Text("print(1)")),
        )
    }
}
