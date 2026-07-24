package com.filodot.noscroll.data.learning.code

import android.database.sqlite.SQLiteDatabase
import com.filodot.noscroll.core.learning.code.CodeEvaluation
import com.filodot.noscroll.core.learning.code.CodeEvaluationStatus
import com.filodot.noscroll.core.learning.code.CodeExerciseEvaluator
import com.filodot.noscroll.core.learning.code.SafePythonSubsetEvaluator
import com.filodot.noscroll.core.learning.model.ActivityContent
import com.filodot.noscroll.core.learning.model.CodeFixContent
import com.filodot.noscroll.core.learning.model.CodeLanguage
import com.filodot.noscroll.core.learning.model.CodeTestCase
import com.filodot.noscroll.core.learning.model.MiniCodeContent
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidCodeExerciseEvaluator(
    private val python: SafePythonSubsetEvaluator = SafePythonSubsetEvaluator(),
) : CodeExerciseEvaluator {
    override suspend fun evaluate(
        content: ActivityContent,
        submission: String,
    ): CodeEvaluation = withContext(Dispatchers.Default) {
        val language: CodeLanguage
        val tests: List<CodeTestCase>
        when (content) {
            is CodeFixContent -> {
                language = content.language
                tests = content.tests
            }

            is MiniCodeContent -> {
                language = content.language
                tests = content.tests
            }

            else -> return@withContext CodeEvaluation(CodeEvaluationStatus.UNSUPPORTED)
        }
        when (language) {
            CodeLanguage.PYTHON -> python.evaluate(submission, tests)
            CodeLanguage.SQL -> SqliteSelectSandbox.evaluate(submission, tests)
        }
    }
}

internal object SqliteSelectSandbox {
    fun evaluate(query: String, tests: List<CodeTestCase>): CodeEvaluation {
        val normalizedQuery = query.trim().removeSuffix(";").trim()
        val validationError = validateQuery(normalizedQuery)
        if (validationError != null) {
            return CodeEvaluation(CodeEvaluationStatus.INVALID_SUBMISSION, validationError)
        }
        if (tests.isEmpty()) {
            return CodeEvaluation(CodeEvaluationStatus.UNSUPPORTED, "У задания нет SQL-тестов")
        }
        for (test in tests.take(MAX_TESTS)) {
            val actual = runCatching { executeTest(normalizedQuery, test.input) }.getOrElse {
                return CodeEvaluation(
                    CodeEvaluationStatus.INVALID_SUBMISSION,
                    "SQL не выполнился: ${(it.message ?: "ошибка").take(160)}",
                )
            }
            if (normalizeOutput(actual) != normalizeOutput(test.expectedOutput)) {
                return CodeEvaluation(
                    CodeEvaluationStatus.INCORRECT,
                    if (test.hidden) "Один из скрытых SQL-тестов не пройден" else {
                        "SQL-тест ${test.id} не пройден"
                    },
                )
            }
        }
        return CodeEvaluation(CodeEvaluationStatus.CORRECT)
    }

    private fun executeTest(query: String, setup: String): String {
        val database = SQLiteDatabase.create(null)
        return try {
            setupStatements(setup).forEach(database::execSQL)
            database.rawQuery(query, emptyArray()).use { cursor ->
                require(cursor.columnCount in 1..MAX_COLUMNS) { "Слишком много колонок" }
                val rows = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    require(rows.size < MAX_ROWS) { "Слишком много строк" }
                    rows += (0 until cursor.columnCount).joinToString("|") { index ->
                        if (cursor.isNull(index)) "NULL" else cursor.getString(index)
                    }
                }
                rows.joinToString("\n").also {
                    require(it.length <= MAX_OUTPUT_LENGTH) { "Результат слишком большой" }
                }
            }
        } finally {
            database.close()
        }
    }

    private fun setupStatements(setup: String): List<String> {
        if (setup.isBlank()) return emptyList()
        val statements = setup.split(';')
            .map(String::trim)
            .filter(String::isNotEmpty)
        require(statements.size <= MAX_SETUP_STATEMENTS) { "Слишком много setup-команд" }
        statements.forEach { statement ->
            val normalized = statement.uppercase(Locale.ROOT)
            require(
                normalized.startsWith("CREATE TABLE ") ||
                    normalized.startsWith("INSERT INTO "),
            ) {
                "Setup допускает только CREATE TABLE и INSERT INTO"
            }
            require(SQL_SETUP_FORBIDDEN.none { Regex("\\b$it\\b").containsMatchIn(normalized) }) {
                "Опасная setup-команда"
            }
            require(statement.length <= MAX_SETUP_STATEMENT_LENGTH) {
                "Setup-команда слишком длинная"
            }
        }
        return statements
    }

    private fun validateQuery(query: String): String? {
        if (query.length !in 1..MAX_QUERY_LENGTH) return "Запрос пустой или слишком длинный"
        if (';' in query) return "Разрешён ровно один SQL-запрос"
        val normalized = query.uppercase(Locale.ROOT)
        if (!normalized.startsWith("SELECT ") && !normalized.startsWith("WITH ")) {
            return "Разрешены только SELECT и WITH … SELECT"
        }
        if (SQL_FORBIDDEN.any { Regex("\\b$it\\b").containsMatchIn(normalized) }) {
            return "Изменение базы и системные SQL-команды запрещены"
        }
        if ("LOAD_EXTENSION" in normalized) return "Расширения SQLite запрещены"
        return null
    }
}

private fun normalizeOutput(value: String): String =
    value.replace("\r\n", "\n")
        .lineSequence()
        .map { it.trimEnd() }
        .joinToString("\n")
        .trim()

private val SQL_FORBIDDEN = setOf(
    "ATTACH",
    "DETACH",
    "PRAGMA",
    "INSERT",
    "UPDATE",
    "DELETE",
    "REPLACE",
    "DROP",
    "ALTER",
    "CREATE",
    "TRIGGER",
    "VACUUM",
    "REINDEX",
    "RECURSIVE",
    "RANDOMBLOB",
    "ZEROBLOB",
)
private val SQL_SETUP_FORBIDDEN = SQL_FORBIDDEN - setOf("CREATE", "INSERT")
private const val MAX_TESTS = 20
private const val MAX_SETUP_STATEMENTS = 30
private const val MAX_SETUP_STATEMENT_LENGTH = 4_000
private const val MAX_QUERY_LENGTH = 8_000
private const val MAX_ROWS = 100
private const val MAX_COLUMNS = 20
private const val MAX_OUTPUT_LENGTH = 10_000
