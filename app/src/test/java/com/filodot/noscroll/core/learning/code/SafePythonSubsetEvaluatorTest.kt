package com.filodot.noscroll.core.learning.code

import com.filodot.noscroll.core.learning.model.CodeTestCase
import org.junit.Assert.assertEquals
import org.junit.Test

class SafePythonSubsetEvaluatorTest {
    private val evaluator = SafePythonSubsetEvaluator()

    @Test
    fun `safe return expression passes public and hidden tests`() {
        val result = evaluator.evaluate(
            submission = "def solve(x, bonus):\n    return x * 2 + bonus",
            tests = listOf(
                CodeTestCase("public", """{"x":2,"bonus":1}""", "5"),
                CodeTestCase("hidden", """[10,-2]""", "18", hidden = true),
            ),
        )

        assertEquals(CodeEvaluationStatus.CORRECT, result.status)
    }

    @Test
    fun `wrong output reports failed test without exposing hidden values`() {
        val result = evaluator.evaluate(
            submission = "def solve(x):\n    return x + 1",
            tests = listOf(CodeTestCase("hidden", """{"x":3}""", "8", hidden = true)),
        )

        assertEquals(CodeEvaluationStatus.INCORRECT, result.status)
        assertEquals("Один из скрытых тестов не пройден", result.message)
    }

    @Test
    fun `boolean strings and approved functions are supported`() {
        val result = evaluator.evaluate(
            submission = "def solve(name, minimum):\n    return len(name) >= minimum and name != ''",
            tests = listOf(
                CodeTestCase("public", """{"name":"Ada","minimum":3}""", "True"),
                CodeTestCase("hidden", """{"name":"","minimum":1}""", "False", hidden = true),
            ),
        )

        assertEquals(CodeEvaluationStatus.CORRECT, result.status)
    }

    @Test
    fun `imports loops and attribute access fail closed`() {
        listOf(
            "import os\ndef solve(x):\n return x",
            "def solve(x):\n while True:\n  return x",
            "def solve(x):\n return x.__class__",
        ).forEach { code ->
            assertEquals(
                CodeEvaluationStatus.INVALID_SUBMISSION,
                evaluator.evaluate(
                    code,
                    listOf(CodeTestCase("public", """{"x":1}""", "1")),
                ).status,
            )
        }
    }
}
