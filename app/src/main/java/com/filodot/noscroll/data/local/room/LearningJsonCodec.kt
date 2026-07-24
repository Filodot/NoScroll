package com.filodot.noscroll.data.local.room

import com.filodot.noscroll.core.learning.model.ActivityContent
import com.filodot.noscroll.core.learning.model.ActivityKind
import com.filodot.noscroll.core.learning.model.ChoiceOption
import com.filodot.noscroll.core.learning.model.CodeCompletionContent
import com.filodot.noscroll.core.learning.model.CodeFixContent
import com.filodot.noscroll.core.learning.model.CodeLanguage
import com.filodot.noscroll.core.learning.model.CodeOutputContent
import com.filodot.noscroll.core.learning.model.CodeTestCase
import com.filodot.noscroll.core.learning.model.EvidenceSelectionContent
import com.filodot.noscroll.core.learning.model.FillBlankContent
import com.filodot.noscroll.core.learning.model.FlashcardContent
import com.filodot.noscroll.core.learning.model.MatchingContent
import com.filodot.noscroll.core.learning.model.MatchingItem
import com.filodot.noscroll.core.learning.model.MatchingPair
import com.filodot.noscroll.core.learning.model.MiniCodeContent
import com.filodot.noscroll.core.learning.model.MultipleChoiceContent
import com.filodot.noscroll.core.learning.model.NumericAnswerContent
import com.filodot.noscroll.core.learning.model.OrderingContent
import com.filodot.noscroll.core.learning.model.OrderingItem
import com.filodot.noscroll.core.learning.model.ScenarioContent
import com.filodot.noscroll.core.learning.model.ShortAnswerContent
import com.filodot.noscroll.core.learning.model.SingleChoiceContent
import com.filodot.noscroll.core.learning.model.SourceCitation
import com.filodot.noscroll.core.learning.model.TeachBackContent
import com.filodot.noscroll.core.learning.model.TrueFalseContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Versioned canonical JSON used by Room and later reused by provider adapters.
 *
 * It is deliberately explicit: corrupt or future payloads fail closed instead of silently turning
 * into another exercise.
 */
object LearningJsonCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun encodeStrings(values: Collection<String>): String =
        stringArray(values).toString()

    fun decodeStringList(value: String): List<String> =
        parseArray(value).map { it.jsonPrimitive.content }

    fun decodeStringSet(value: String): Set<String> = decodeStringList(value).toSet()

    fun encodeCitations(citations: List<SourceCitation>): String = buildJsonArray {
        citations.forEach { citation ->
            add(
                buildJsonObject {
                    put("sourceId", citation.sourceId)
                    put("chunkId", citation.chunkId)
                    putNullable("pageNumber", citation.pageNumber)
                    putNullable("sectionTitle", citation.sectionTitle)
                },
            )
        }
    }.toString()

    fun decodeCitations(value: String): List<SourceCitation> = parseArray(value).map { element ->
        val objectValue = element.jsonObject
        SourceCitation(
            sourceId = objectValue.requiredString("sourceId"),
            chunkId = objectValue.requiredString("chunkId"),
            pageNumber = objectValue["pageNumber"]?.jsonPrimitive?.contentOrNull?.toInt(),
            sectionTitle = objectValue["sectionTitle"]?.jsonPrimitive?.contentOrNull,
        )
    }

    fun encodeContent(content: ActivityContent): String = buildJsonObject {
        put("schemaVersion", CONTENT_SCHEMA_VERSION)
        put("kind", content.kind.name)
        when (content) {
            is SingleChoiceContent -> {
                put("options", options(content.options))
                put("correctOptionId", content.correctOptionId)
            }

            is MultipleChoiceContent -> {
                put("options", options(content.options))
                put("correctOptionIds", stringArray(content.correctOptionIds))
            }

            is TrueFalseContent -> {
                put("statement", content.statement)
                put("expected", content.expected)
                putNullable("correction", content.correction)
            }

            is OrderingContent -> {
                put("items", orderingItems(content.items))
                put("correctOrderIds", stringArray(content.correctOrderIds))
            }

            is MatchingContent -> {
                put("left", matchingItems(content.left))
                put("right", matchingItems(content.right))
                put(
                    "correctPairs",
                    buildJsonArray {
                        content.correctPairs.forEach { pair ->
                            add(
                                buildJsonObject {
                                    put("leftId", pair.leftId)
                                    put("rightId", pair.rightId)
                                },
                            )
                        }
                    },
                )
            }

            is FillBlankContent -> {
                put("textWithBlank", content.textWithBlank)
                put("acceptedAnswers", stringArray(content.acceptedAnswers))
                put("caseSensitive", content.caseSensitive)
            }

            is ShortAnswerContent -> {
                put("acceptedAnswers", stringArray(content.acceptedAnswers))
                putNullable("rubric", content.rubric)
                put("caseSensitive", content.caseSensitive)
            }

            is NumericAnswerContent -> {
                put("expected", content.expected)
                put("tolerance", content.tolerance)
            }

            is FlashcardContent -> put("answer", content.answer)

            is EvidenceSelectionContent -> {
                put("options", options(content.options))
                put("correctOptionIds", stringArray(content.correctOptionIds))
            }

            is ScenarioContent -> {
                put("options", options(content.options))
                put("correctOptionId", content.correctOptionId)
                put(
                    "consequenceByOptionId",
                    buildJsonObject {
                        content.consequenceByOptionId.toSortedMap().forEach { (key, value) ->
                            put(key, value)
                        }
                    },
                )
            }

            is CodeOutputContent -> {
                put("language", content.language.name)
                put("code", content.code)
                put("acceptedOutputs", stringArray(content.acceptedOutputs))
            }

            is CodeCompletionContent -> {
                put("language", content.language.name)
                put("codeWithBlank", content.codeWithBlank)
                put("acceptedSnippets", stringArray(content.acceptedSnippets))
                put("tests", tests(content.tests))
            }

            is CodeFixContent -> {
                put("language", content.language.name)
                put("brokenCode", content.brokenCode)
                put("tests", tests(content.tests))
            }

            is MiniCodeContent -> {
                put("language", content.language.name)
                put("starterCode", content.starterCode)
                put("tests", tests(content.tests))
            }

            is TeachBackContent -> {
                put("rubric", content.rubric)
                put("keyPoints", stringArray(content.keyPoints))
            }
        }
    }.toString()

    fun decodeContent(
        expectedKind: ActivityKind,
        value: String,
    ): ActivityContent {
        val root = parseObject(value)
        require(root["schemaVersion"]?.jsonPrimitive?.int == CONTENT_SCHEMA_VERSION) {
            "Unsupported learning content schema"
        }
        val storedKind = enumValueOf<ActivityKind>(root.requiredString("kind"))
        require(storedKind == expectedKind) {
            "Learning activity kind does not match its payload"
        }
        return when (storedKind) {
            ActivityKind.SINGLE_CHOICE -> SingleChoiceContent(
                options = root.options(),
                correctOptionId = root.requiredString("correctOptionId"),
            )

            ActivityKind.MULTIPLE_CHOICE -> MultipleChoiceContent(
                options = root.options(),
                correctOptionIds = root.stringSet("correctOptionIds"),
            )

            ActivityKind.TRUE_FALSE -> TrueFalseContent(
                statement = root.requiredString("statement"),
                expected = root.requiredBoolean("expected"),
                correction = root.nullableString("correction"),
            )

            ActivityKind.ORDERING -> OrderingContent(
                items = root.requiredArray("items").map { element ->
                    OrderingItem(
                        id = element.jsonObject.requiredString("id"),
                        text = element.jsonObject.requiredString("text"),
                    )
                },
                correctOrderIds = root.stringList("correctOrderIds"),
            )

            ActivityKind.MATCHING -> MatchingContent(
                left = root.matchingItems("left"),
                right = root.matchingItems("right"),
                correctPairs = root.requiredArray("correctPairs").map { element ->
                    MatchingPair(
                        leftId = element.jsonObject.requiredString("leftId"),
                        rightId = element.jsonObject.requiredString("rightId"),
                    )
                },
            )

            ActivityKind.FILL_BLANK -> FillBlankContent(
                textWithBlank = root.requiredString("textWithBlank"),
                acceptedAnswers = root.stringSet("acceptedAnswers"),
                caseSensitive = root.requiredBoolean("caseSensitive"),
            )

            ActivityKind.SHORT_ANSWER -> ShortAnswerContent(
                acceptedAnswers = root.stringSet("acceptedAnswers"),
                rubric = root.nullableString("rubric"),
                caseSensitive = root.requiredBoolean("caseSensitive"),
            )

            ActivityKind.NUMERIC_ANSWER -> NumericAnswerContent(
                expected = root.requiredDouble("expected"),
                tolerance = root.requiredDouble("tolerance"),
            )

            ActivityKind.FLASHCARD -> FlashcardContent(
                answer = root.requiredString("answer"),
            )

            ActivityKind.EVIDENCE_SELECTION -> EvidenceSelectionContent(
                options = root.options(),
                correctOptionIds = root.stringSet("correctOptionIds"),
            )

            ActivityKind.SCENARIO -> ScenarioContent(
                options = root.options(),
                correctOptionId = root.requiredString("correctOptionId"),
                consequenceByOptionId = root.requiredObject("consequenceByOptionId")
                    .mapValues { it.value.jsonPrimitive.content },
            )

            ActivityKind.CODE_OUTPUT -> CodeOutputContent(
                language = root.codeLanguage(),
                code = root.requiredString("code"),
                acceptedOutputs = root.stringSet("acceptedOutputs"),
            )

            ActivityKind.CODE_COMPLETION -> CodeCompletionContent(
                language = root.codeLanguage(),
                codeWithBlank = root.requiredString("codeWithBlank"),
                acceptedSnippets = root.stringSet("acceptedSnippets"),
                tests = root.codeTests(),
            )

            ActivityKind.CODE_FIX -> CodeFixContent(
                language = root.codeLanguage(),
                brokenCode = root.requiredString("brokenCode"),
                tests = root.codeTests(),
            )

            ActivityKind.MINI_CODE -> MiniCodeContent(
                language = root.codeLanguage(),
                starterCode = root.requiredString("starterCode"),
                tests = root.codeTests(),
            )

            ActivityKind.TEACH_BACK -> TeachBackContent(
                rubric = root.requiredString("rubric"),
                keyPoints = root.stringList("keyPoints"),
            )
        }
    }

    private fun parseArray(value: String): JsonArray = json.parseToJsonElement(value).jsonArray

    private fun parseObject(value: String): JsonObject = json.parseToJsonElement(value).jsonObject

    private fun stringArray(values: Collection<String>): JsonArray = buildJsonArray {
        values.forEach { add(JsonPrimitive(it)) }
    }

    private fun options(values: List<ChoiceOption>): JsonArray = buildJsonArray {
        values.forEach { option ->
            add(
                buildJsonObject {
                    put("id", option.id)
                    put("text", option.text)
                },
            )
        }
    }

    private fun orderingItems(values: List<OrderingItem>): JsonArray = buildJsonArray {
        values.forEach { item ->
            add(
                buildJsonObject {
                    put("id", item.id)
                    put("text", item.text)
                },
            )
        }
    }

    private fun matchingItems(values: List<MatchingItem>): JsonArray = buildJsonArray {
        values.forEach { item ->
            add(
                buildJsonObject {
                    put("id", item.id)
                    put("text", item.text)
                },
            )
        }
    }

    private fun tests(values: List<CodeTestCase>): JsonArray = buildJsonArray {
        values.forEach { test ->
            add(
                buildJsonObject {
                    put("id", test.id)
                    put("input", test.input)
                    put("expectedOutput", test.expectedOutput)
                    put("hidden", test.hidden)
                },
            )
        }
    }

    private fun JsonObject.requiredArray(key: String): JsonArray =
        requireNotNull(this[key]) { "Missing $key" }.jsonArray

    private fun JsonObject.requiredObject(key: String): JsonObject =
        requireNotNull(this[key]) { "Missing $key" }.jsonObject

    private fun JsonObject.requiredString(key: String): String =
        requireNotNull(this[key]) { "Missing $key" }.jsonPrimitive.content

    private fun JsonObject.nullableString(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.requiredBoolean(key: String): Boolean =
        requireNotNull(this[key]) { "Missing $key" }.jsonPrimitive.boolean

    private fun JsonObject.requiredDouble(key: String): Double =
        requireNotNull(this[key]) { "Missing $key" }.jsonPrimitive.double

    private fun JsonObject.stringList(key: String): List<String> =
        requiredArray(key).map { it.jsonPrimitive.content }

    private fun JsonObject.stringSet(key: String): Set<String> = stringList(key).toSet()

    private fun JsonObject.options(): List<ChoiceOption> = requiredArray("options").map { element ->
        ChoiceOption(
            id = element.jsonObject.requiredString("id"),
            text = element.jsonObject.requiredString("text"),
        )
    }

    private fun JsonObject.matchingItems(key: String): List<MatchingItem> =
        requiredArray(key).map { element ->
            MatchingItem(
                id = element.jsonObject.requiredString("id"),
                text = element.jsonObject.requiredString("text"),
            )
        }

    private fun JsonObject.codeLanguage(): CodeLanguage =
        enumValueOf(requiredString("language"))

    private fun JsonObject.codeTests(): List<CodeTestCase> =
        requiredArray("tests").map { element ->
            val objectValue = element.jsonObject
            CodeTestCase(
                id = objectValue.requiredString("id"),
                input = objectValue.requiredString("input"),
                expectedOutput = objectValue.requiredString("expectedOutput"),
                hidden = objectValue.requiredBoolean("hidden"),
            )
        }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(
        key: String,
        value: String?,
    ) {
        put(key, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(
        key: String,
        value: Int?,
    ) {
        put(key, value?.let(::JsonPrimitive) ?: JsonNull)
    }
}

private const val CONTENT_SCHEMA_VERSION = 1
