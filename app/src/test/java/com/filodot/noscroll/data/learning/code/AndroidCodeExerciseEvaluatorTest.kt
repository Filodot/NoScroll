package com.filodot.noscroll.data.learning.code

import com.filodot.noscroll.core.learning.code.CodeEvaluationStatus
import com.filodot.noscroll.core.learning.model.CodeLanguage
import com.filodot.noscroll.core.learning.model.CodeTestCase
import com.filodot.noscroll.core.learning.model.MiniCodeContent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidCodeExerciseEvaluatorTest {
    private val evaluator = AndroidCodeExerciseEvaluator()

    @Test
    fun `read only SQL passes isolated public and hidden datasets`() = runTest {
        val content = MiniCodeContent(
            language = CodeLanguage.SQL,
            starterCode = "SELECT name FROM users WHERE active = 1 ORDER BY id",
            tests = listOf(
                CodeTestCase(
                    id = "public",
                    input = "CREATE TABLE users(id INTEGER, name TEXT, active INTEGER);" +
                        "INSERT INTO users VALUES(1, 'Ada', 1);" +
                        "INSERT INTO users VALUES(2, 'Linus', 0);",
                    expectedOutput = "Ada",
                ),
                CodeTestCase(
                    id = "hidden",
                    input = "CREATE TABLE users(id INTEGER, name TEXT, active INTEGER);" +
                        "INSERT INTO users VALUES(2, 'Grace', 1);" +
                        "INSERT INTO users VALUES(1, 'Alan', 1);",
                    expectedOutput = "Alan\nGrace",
                    hidden = true,
                ),
            ),
        )

        val result = evaluator.evaluate(
            content,
            "SELECT name FROM users WHERE active = 1 ORDER BY id",
        )

        assertEquals(CodeEvaluationStatus.CORRECT, result.status)
    }

    @Test
    fun `SQL mutation pragma and multiple statements are rejected`() = runTest {
        val content = MiniCodeContent(
            CodeLanguage.SQL,
            "SELECT 1",
            listOf(CodeTestCase("public", "", "1")),
        )

        listOf(
            "DROP TABLE users",
            "PRAGMA user_version",
            "SELECT 1; SELECT 2",
            "UPDATE users SET name = 'x'",
        ).forEach { query ->
            assertEquals(
                CodeEvaluationStatus.INVALID_SUBMISSION,
                evaluator.evaluate(content, query).status,
            )
        }
    }

    @Test
    fun `python activity delegates to restricted interpreter`() = runTest {
        val content = MiniCodeContent(
            CodeLanguage.PYTHON,
            "def solve(x):\n    return x",
            listOf(
                CodeTestCase("public", """{"x":2}""", "4"),
                CodeTestCase("hidden", """{"x":5}""", "10", hidden = true),
            ),
        )

        assertEquals(
            CodeEvaluationStatus.CORRECT,
            evaluator.evaluate(content, "def solve(x):\n    return x * 2").status,
        )
    }
}
