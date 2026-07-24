package com.filodot.noscroll.core.learning.code

import com.filodot.noscroll.core.learning.model.CodeTestCase
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A deliberately small expression interpreter, not a Python runtime.
 *
 * Accepted submissions contain one `def` and one `return` expression. There are no imports,
 * loops, attribute access, filesystem, network, reflection, or Android APIs.
 */
class SafePythonSubsetEvaluator {
    fun evaluate(submission: String, tests: List<CodeTestCase>): CodeEvaluation {
        if (submission.length !in 1..MAX_CODE_LENGTH) {
            return invalid("Код пустой или слишком длинный")
        }
        val parsed = runCatching { parseSubmission(submission) }.getOrElse {
            return invalid(it.message ?: "Поддерживается одна функция с выражением return")
        }
        if (tests.isEmpty()) return invalid("У задания нет тестов")
        for (test in tests.take(MAX_TESTS)) {
            val variables = runCatching { parseInput(test.input, parsed.parameters) }.getOrElse {
                return CodeEvaluation(CodeEvaluationStatus.UNSUPPORTED, "Некорректный тест задания")
            }
            val actual = runCatching {
                ExpressionParser(parsed.expression, variables).parse().render()
            }.getOrElse {
                return invalid(it.message ?: "Выражение не поддерживается безопасной песочницей")
            }
            if (normalizeOutput(actual) != normalizeOutput(test.expectedOutput)) {
                return CodeEvaluation(
                    CodeEvaluationStatus.INCORRECT,
                    if (test.hidden) "Один из скрытых тестов не пройден" else {
                        "Тест ${test.id} не пройден"
                    },
                )
            }
        }
        return CodeEvaluation(CodeEvaluationStatus.CORRECT)
    }

    private fun parseSubmission(code: String): ParsedSubmission {
        require(code.none { it.code < 9 || it == '\u000B' || it == '\u000C' }) {
            "Недопустимые управляющие символы"
        }
        val lowered = code.lowercase(Locale.ROOT)
        require(FORBIDDEN_WORDS.none { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(lowered) }) {
            "Импорты, циклы и системные функции отключены"
        }
        require("__" !in code && !ATTRIBUTE_ACCESS_PATTERN.containsMatchIn(code)) {
            "Доступ к объектам и атрибутам отключён"
        }
        val meaningful = code.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        val definitionIndex = meaningful.indexOfFirst { it.startsWith("def ") }
        require(definitionIndex >= 0) { "Добавьте объявление функции def" }
        val definition = meaningful[definitionIndex]
        val match = FUNCTION_PATTERN.matchEntire(definition)
            ?: error("Формат функции: def solve(x):")
        val parameters = match.groupValues[2]
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
        require(parameters.size <= MAX_PARAMETERS && parameters.toSet().size == parameters.size) {
            "Слишком много или повторяющиеся параметры"
        }
        require(parameters.all(IDENTIFIER_PATTERN::matches)) { "Некорректное имя параметра" }
        val returnLines = meaningful.filter { it.startsWith("return ") }
        require(returnLines.size == 1) { "Нужна ровно одна строка return" }
        require(
            meaningful.all {
                it == definition ||
                    it.startsWith("return ") ||
                    it.startsWith("\"\"\"") ||
                    it.startsWith("'''")
            },
        ) {
            "Разрешены только def и одно выражение return"
        }
        val expression = returnLines.single().removePrefix("return ").trim()
        require(expression.isNotEmpty()) { "После return нет выражения" }
        return ParsedSubmission(parameters, expression)
    }

    private fun parseInput(input: String, parameters: List<String>): Map<String, Value> {
        if (input.isBlank()) {
            require(parameters.isEmpty()) { "Для параметров отсутствуют входные данные" }
            return emptyMap()
        }
        val element = JSON.parseToJsonElement(input)
        return when (element) {
            is JsonObject -> {
                require(element.keys == parameters.toSet()) { "Набор параметров теста не совпадает" }
                element.mapValues { (_, value) -> value.jsonPrimitive.toValue() }
            }

            is JsonArray -> {
                require(element.size == parameters.size) { "Число аргументов теста не совпадает" }
                parameters.zip(element.jsonArray.map { it.jsonPrimitive.toValue() }).toMap()
            }

            else -> {
                require(parameters.size == 1) { "Одиночное значение допустимо для одного параметра" }
                mapOf(parameters.single() to element.jsonPrimitive.toValue())
            }
        }
    }
}

private data class ParsedSubmission(
    val parameters: List<String>,
    val expression: String,
)

private sealed interface Value {
    data class Number(val value: Double) : Value
    data class Text(val value: String) : Value
    data class Bool(val value: Boolean) : Value

    fun render(): String = when (this) {
        is Number -> if (value % 1.0 == 0.0) value.toLong().toString() else {
            value.toString()
        }

        is Text -> value
        is Bool -> if (value) "True" else "False"
    }
}

private class ExpressionParser(
    expression: String,
    private val variables: Map<String, Value>,
) {
    private val tokens = Lexer(expression).tokens()
    private var position = 0

    fun parse(): Value {
        val result = parseOr()
        require(peek().type == TokenType.END) { "Лишние символы в выражении" }
        return result
    }

    private fun parseOr(): Value {
        var left = parseAnd()
        while (matchKeyword("or")) {
            val right = parseAnd()
            left = Value.Bool(left.asBoolean() || right.asBoolean())
        }
        return left
    }

    private fun parseAnd(): Value {
        var left = parseComparison()
        while (matchKeyword("and")) {
            val right = parseComparison()
            left = Value.Bool(left.asBoolean() && right.asBoolean())
        }
        return left
    }

    private fun parseComparison(): Value {
        var left = parseAdditive()
        while (peek().text in COMPARISON_OPERATORS) {
            val operator = advance().text
            val right = parseAdditive()
            left = Value.Bool(compare(left, right, operator))
        }
        return left
    }

    private fun parseAdditive(): Value {
        var left = parseMultiplicative()
        while (peek().text == "+" || peek().text == "-") {
            val operator = advance().text
            val right = parseMultiplicative()
            left = when (operator) {
                "+" -> when {
                    left is Value.Number && right is Value.Number ->
                        Value.Number(left.value + right.value)

                    left is Value.Text && right is Value.Text -> Value.Text(left.value + right.value)
                    else -> error("Оператор + требует два числа или две строки")
                }

                else -> Value.Number(left.asNumber() - right.asNumber())
            }
        }
        return left
    }

    private fun parseMultiplicative(): Value {
        var left = parsePower()
        while (peek().text in MULTIPLICATIVE_OPERATORS) {
            val operator = advance().text
            val right = parsePower()
            left = when (operator) {
                "*" -> Value.Number(left.asNumber() * right.asNumber())
                "/" -> Value.Number(left.asNumber() / right.nonZeroNumber())
                "//" -> Value.Number(kotlin.math.floor(left.asNumber() / right.nonZeroNumber()))
                "%" -> Value.Number(left.asNumber() % right.nonZeroNumber())
                else -> error("Неизвестный оператор")
            }
        }
        return left
    }

    private fun parsePower(): Value {
        val left = parseUnary()
        return if (match("**")) {
            Value.Number(left.asNumber().pow(parsePower().asNumber()))
        } else {
            left
        }
    }

    private fun parseUnary(): Value = when {
        match("-") -> Value.Number(-parseUnary().asNumber())
        match("+") -> Value.Number(parseUnary().asNumber())
        matchKeyword("not") -> Value.Bool(!parseUnary().asBoolean())
        else -> parsePrimary()
    }

    private fun parsePrimary(): Value {
        val token = advance()
        return when (token.type) {
            TokenType.NUMBER -> Value.Number(token.text.toDouble())
            TokenType.STRING -> Value.Text(token.text)
            TokenType.IDENTIFIER -> when (token.text) {
                "True" -> Value.Bool(true)
                "False" -> Value.Bool(false)
                "len", "abs", "round" -> parseFunction(token.text)
                else -> variables[token.text] ?: error("Неизвестная переменная ${token.text}")
            }

            TokenType.SYMBOL -> {
                require(token.text == "(") { "Ожидалось значение" }
                val nested = parseOr()
                require(match(")")) { "Не закрыта скобка" }
                nested
            }

            TokenType.END -> error("Выражение неожиданно закончилось")
        }
    }

    private fun parseFunction(name: String): Value {
        require(match("(")) { "После $name нужна скобка" }
        val argument = parseOr()
        require(match(")")) { "Не закрыта скобка функции" }
        return when (name) {
            "len" -> Value.Number((argument as? Value.Text)?.value?.length?.toDouble()
                ?: error("len поддерживает строку"))

            "abs" -> Value.Number(kotlin.math.abs(argument.asNumber()))
            "round" -> Value.Number(kotlin.math.round(argument.asNumber()))
            else -> error("Функция не поддерживается")
        }
    }

    private fun compare(left: Value, right: Value, operator: String): Boolean = when (operator) {
        "==" -> left == right
        "!=" -> left != right
        "<" -> left.comparableTo(right) < 0
        "<=" -> left.comparableTo(right) <= 0
        ">" -> left.comparableTo(right) > 0
        ">=" -> left.comparableTo(right) >= 0
        else -> error("Неизвестное сравнение")
    }

    private fun match(text: String): Boolean =
        if (peek().text == text) {
            position++
            true
        } else {
            false
        }

    private fun matchKeyword(text: String): Boolean =
        if (peek().type == TokenType.IDENTIFIER && peek().text == text) {
            position++
            true
        } else {
            false
        }

    private fun advance(): Token = tokens[position++]
    private fun peek(): Token = tokens[position]
}

private class Lexer(private val source: String) {
    private var position = 0
    private val result = mutableListOf<Token>()

    fun tokens(): List<Token> {
        while (position < source.length) {
            when {
                source[position].isWhitespace() -> position++
                source[position].isDigit() ||
                    (source[position] == '.' &&
                        source.getOrNull(position + 1)?.isDigit() == true) -> number()

                source[position].isLetter() || source[position] == '_' -> identifier()
                source[position] == '"' || source[position] == '\'' -> string()
                else -> symbol()
            }
            require(result.size <= MAX_TOKENS) { "Выражение слишком сложное" }
        }
        result += Token(TokenType.END, "")
        return result
    }

    private fun number() {
        val start = position
        while (source.getOrNull(position)?.let { it.isDigit() || it == '.' } == true) position++
        val value = source.substring(start, position)
        require(value.count { it == '.' } <= 1 && value.toDoubleOrNull()?.isFinite() == true) {
            "Некорректное число"
        }
        result += Token(TokenType.NUMBER, value)
    }

    private fun identifier() {
        val start = position
        while (source.getOrNull(position)?.let { it.isLetterOrDigit() || it == '_' } == true) {
            position++
        }
        result += Token(TokenType.IDENTIFIER, source.substring(start, position))
    }

    private fun string() {
        val quote = source[position++]
        val value = StringBuilder()
        while (position < source.length && source[position] != quote) {
            val current = source[position++]
            if (current == '\\') {
                val escaped = source.getOrNull(position++) ?: error("Незакрытая строка")
                value.append(
                    when (escaped) {
                        'n' -> '\n'
                        't' -> '\t'
                        '\\' -> '\\'
                        quote -> quote
                        else -> error("Escape-последовательность не поддерживается")
                    },
                )
            } else {
                value.append(current)
            }
            require(value.length <= MAX_STRING_LENGTH) { "Строка слишком длинная" }
        }
        require(source.getOrNull(position) == quote) { "Незакрытая строка" }
        position++
        result += Token(TokenType.STRING, value.toString())
    }

    private fun symbol() {
        val pair = source.substring(position, (position + 2).coerceAtMost(source.length))
        val symbol = if (pair in TWO_CHARACTER_OPERATORS) pair else source[position].toString()
        require(symbol in ALLOWED_SYMBOLS) { "Символ $symbol не поддерживается" }
        position += symbol.length
        result += Token(TokenType.SYMBOL, symbol)
    }
}

private enum class TokenType {
    NUMBER,
    STRING,
    IDENTIFIER,
    SYMBOL,
    END,
}

private data class Token(val type: TokenType, val text: String)

private fun JsonPrimitive.toValue(): Value = when {
    booleanOrNull != null -> Value.Bool(requireNotNull(booleanOrNull))
    doubleOrNull != null -> Value.Number(requireNotNull(doubleOrNull))
    isString -> Value.Text(contentOrNull.orEmpty())
    else -> error("Поддерживаются числа, строки и boolean")
}

private fun Value.asNumber(): Double = (this as? Value.Number)?.value
    ?: error("Ожидалось число")

private fun Value.nonZeroNumber(): Double = asNumber().also {
    require(it != 0.0) { "Деление на ноль" }
}

private fun Value.asBoolean(): Boolean = (this as? Value.Bool)?.value
    ?: error("Ожидалось логическое значение")

private fun Value.comparableTo(other: Value): Int = when {
    this is Value.Number && other is Value.Number -> value.compareTo(other.value)
    this is Value.Text && other is Value.Text -> value.compareTo(other.value)
    else -> error("Сравнивать можно два числа или две строки")
}

private fun Double.pow(exponent: Double): Double = Math.pow(this, exponent).also {
    require(it.isFinite() && kotlin.math.abs(it) <= MAX_ABSOLUTE_RESULT) {
        "Результат слишком большой"
    }
}

private fun normalizeOutput(value: String): String =
    value.replace("\r\n", "\n").trim()

private fun invalid(message: String) =
    CodeEvaluation(CodeEvaluationStatus.INVALID_SUBMISSION, message)

private val JSON = Json { isLenient = false }
private val FUNCTION_PATTERN = Regex("""def\s+([A-Za-z_]\w*)\s*\(([^)]*)\)\s*:""")
private val IDENTIFIER_PATTERN = Regex("""[A-Za-z_]\w*""")
private val ATTRIBUTE_ACCESS_PATTERN = Regex("""[A-Za-z_]\w*\s*\.""")
private val FORBIDDEN_WORDS = setOf(
    "import",
    "from",
    "exec",
    "eval",
    "open",
    "compile",
    "globals",
    "locals",
    "getattr",
    "setattr",
    "delattr",
    "while",
    "for",
    "class",
    "lambda",
    "yield",
    "await",
    "async",
    "raise",
    "try",
    "with",
)
private val COMPARISON_OPERATORS = setOf("==", "!=", "<", "<=", ">", ">=")
private val MULTIPLICATIVE_OPERATORS = setOf("*", "/", "//", "%")
private val TWO_CHARACTER_OPERATORS = setOf("==", "!=", "<=", ">=", "//", "**")
private val ALLOWED_SYMBOLS = setOf(
    "+",
    "-",
    "*",
    "/",
    "//",
    "%",
    "**",
    "(",
    ")",
    "==",
    "!=",
    "<",
    "<=",
    ">",
    ">=",
)
private const val MAX_CODE_LENGTH = 4_000
private const val MAX_PARAMETERS = 8
private const val MAX_TESTS = 20
private const val MAX_TOKENS = 256
private const val MAX_STRING_LENGTH = 2_000
private const val MAX_ABSOLUTE_RESULT = 1e15
