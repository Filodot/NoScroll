package com.filodot.noscroll.core.learning.generation

import com.filodot.noscroll.core.learning.ai.AiGateway
import com.filodot.noscroll.core.learning.ai.AiGenerationRequest
import com.filodot.noscroll.core.learning.ai.AiGenerationResponse
import com.filodot.noscroll.core.learning.ai.AiProviderId
import com.filodot.noscroll.core.learning.model.ActivityKind
import com.filodot.noscroll.core.learning.model.CourseOrigin
import com.filodot.noscroll.core.learning.model.CourseStatus
import com.filodot.noscroll.core.learning.model.CurriculumNode
import com.filodot.noscroll.core.learning.model.CurriculumNodeType
import com.filodot.noscroll.core.learning.model.GroundingMode
import com.filodot.noscroll.core.learning.model.LearningConcept
import com.filodot.noscroll.core.learning.model.LearningCourse
import com.filodot.noscroll.core.learning.model.LearningCourseContent
import com.filodot.noscroll.core.learning.model.LearningSource
import com.filodot.noscroll.core.learning.model.LearningSourceChunk
import com.filodot.noscroll.core.learning.model.LearningSourceType
import com.filodot.noscroll.core.learning.model.LessonPackageStatus
import com.filodot.noscroll.core.learning.model.SourceCitation
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiLessonGeneratorTest {
    @Test
    fun `retries invalid concept and returns validated diverse offline lesson`() = runBlocking {
        val gateway = QueueGateway(
            listOf(
                validLessonJson().replace("\"SELECT\"", "\"UNKNOWN\""),
                validLessonJson(),
            ),
        )
        var id = 0
        val generator = AiLessonGenerator(
            gateway = gateway,
            now = { Instant.EPOCH },
            idGenerator = { "generated-${id++}" },
        )

        val lesson = generator.generate(topicCourse(), emptyList())

        assertEquals(2, gateway.requests.size)
        assertEquals(LessonPackageStatus.VALIDATED, lesson.status)
        assertEquals(3, lesson.activities.size)
        assertEquals(3, lesson.activities.map { it.content.kind }.toSet().size)
        assertTrue(lesson.activities.all { it.quality.accepted })
        assertTrue(gateway.requests[1].userPrompt.contains("Предыдущий урок отклонён"))
    }

    @Test
    fun `rejects invented material citations`() = runBlocking {
        val invalid = validLessonJson(sourceChunkId = "invented")
        val gateway = QueueGateway(listOf(invalid, invalid))
        val generator = AiLessonGenerator(gateway, maximumAttempts = 2)

        val error = runCatching { generator.generate(materialCourse(), emptyList()) }
            .exceptionOrNull()

        assertTrue(error is LessonGenerationException)
        assertEquals(2, (error as LessonGenerationException).attempts)
    }

    @Test
    fun `accepts SQL mini code only with public and hidden sandbox tests`() = runBlocking {
        val gateway = QueueGateway(listOf(validSqlCodeLessonJson()))
        val generator = AiLessonGenerator(gateway)

        val lesson = generator.generate(topicCourse(), emptyList())

        assertEquals(LessonPackageStatus.VALIDATED, lesson.status)
        assertTrue(lesson.activities.any { it.content.kind == ActivityKind.MINI_CODE })
        assertTrue(gateway.requests.single().userPrompt.contains("CREATE TABLE"))
        assertTrue(gateway.requests.single().userPrompt.contains("скрытый"))
    }

    @Test
    fun `parses every declared activity content format`() {
        val samples = mapOf(
            ActivityKind.SINGLE_CHOICE to
                """{"options":[{"id":"a","text":"A"},{"id":"b","text":"B"}],"correctOptionId":"a"}""",
            ActivityKind.MULTIPLE_CHOICE to
                """{"options":[{"id":"a","text":"A"},{"id":"b","text":"B"},{"id":"c","text":"C"}],"correctOptionIds":["a","b"]}""",
            ActivityKind.TRUE_FALSE to
                """{"statement":"S","expected":true,"correction":""}""",
            ActivityKind.ORDERING to
                """{"items":[{"id":"a","text":"A"},{"id":"b","text":"B"},{"id":"c","text":"C"}],"correctOrderIds":["a","b","c"]}""",
            ActivityKind.MATCHING to
                """{"left":[{"id":"a","text":"A"},{"id":"b","text":"B"}],"right":[{"id":"x","text":"X"},{"id":"y","text":"Y"}],"pairs":[{"leftId":"a","rightId":"x"},{"leftId":"b","rightId":"y"}]}""",
            ActivityKind.FILL_BLANK to
                """{"textWithBlank":"A {{blank}}","acceptedAnswers":["B"],"caseSensitive":false}""",
            ActivityKind.SHORT_ANSWER to
                """{"acceptedAnswers":["A"],"rubric":"","caseSensitive":false}""",
            ActivityKind.NUMERIC_ANSWER to """{"expected":2.0,"tolerance":0.1}""",
            ActivityKind.FLASHCARD to """{"answer":"A"}""",
            ActivityKind.EVIDENCE_SELECTION to
                """{"options":[{"id":"a","text":"A"},{"id":"b","text":"B"}],"correctOptionIds":["a"]}""",
            ActivityKind.SCENARIO to
                """{"options":[{"id":"a","text":"A"},{"id":"b","text":"B"}],"correctOptionId":"a","consequences":[{"optionId":"a","text":"ok"},{"optionId":"b","text":"no"}]}""",
            ActivityKind.CODE_OUTPUT to
                """{"language":"PYTHON","code":"print(1)","acceptedOutputs":["1"]}""",
            ActivityKind.CODE_COMPLETION to
                """{"language":"SQL","codeWithBlank":"SELECT {{code}}","acceptedSnippets":["1"],"tests":[]}""",
            ActivityKind.CODE_FIX to
                """{"language":"PYTHON","brokenCode":"print(","tests":[{"id":"t","input":"","expectedOutput":"1","hidden":false}]}""",
            ActivityKind.MINI_CODE to
                """{"language":"SQL","starterCode":"SELECT","tests":[{"id":"t","input":"","expectedOutput":"1","hidden":true}]}""",
            ActivityKind.TEACH_BACK to
                """{"rubric":"Explain","keyPoints":["A"]}""",
        )

        samples.forEach { (kind, raw) ->
            val parsed = parseActivityContent(kind, Json.parseToJsonElement(raw).jsonObject)
            assertEquals(kind, parsed.kind)
        }
    }

    private class QueueGateway(responses: List<String>) : AiGateway {
        private val queue = ArrayDeque(responses)
        val requests = mutableListOf<AiGenerationRequest>()

        override suspend fun generate(request: AiGenerationRequest): AiGenerationResponse {
            requests += request
            return AiGenerationResponse(
                AiProviderId.GEMINI,
                "test-model",
                queue.removeFirst(),
                Instant.EPOCH,
            )
        }
    }

    private fun topicCourse() = courseContent(
        groundingMode = GroundingMode.AI_KNOWLEDGE,
        sourceChunks = emptyList(),
        citations = emptyList(),
    )

    private fun materialCourse(): LearningCourseContent {
        val chunk = LearningSourceChunk(
            id = "chunk-1",
            sourceId = "source-1",
            courseId = "course-1",
            position = 0,
            text = "SELECT выбирает данные из таблицы.",
            characterStart = 0,
            characterEnd = 34,
            estimatedTokens = 9,
        )
        return courseContent(
            groundingMode = GroundingMode.SOURCE_REQUIRED,
            sourceChunks = listOf(chunk),
            citations = listOf(SourceCitation("source-1", "chunk-1")),
        )
    }

    private fun courseContent(
        groundingMode: GroundingMode,
        sourceChunks: List<LearningSourceChunk>,
        citations: List<SourceCitation>,
    ): LearningCourseContent {
        val course = LearningCourse(
            id = "course-1",
            title = "Основы SQL",
            description = "Последовательный курс по языку запросов SQL.",
            origin = if (sourceChunks.isEmpty()) CourseOrigin.TOPIC else CourseOrigin.MATERIAL,
            groundingMode = groundingMode,
            status = CourseStatus.READY,
            planVersion = 2,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val concept = LearningConcept(
            id = "concept-1",
            courseId = course.id,
            title = "SELECT",
            summary = "Оператор выбирает строки и столбцы из таблицы.",
            position = 0,
            citations = citations,
        )
        return LearningCourseContent(
            course = course,
            sources = listOf(
                LearningSource(
                    id = "source-1",
                    courseId = course.id,
                    title = "SQL",
                    type = if (sourceChunks.isEmpty()) {
                        LearningSourceType.TOPIC
                    } else {
                        LearningSourceType.PLAIN_TEXT
                    },
                    contentHash = null,
                    importedAt = Instant.EPOCH,
                ),
            ),
            sourceChunks = sourceChunks,
            curriculumNodes = listOf(
                CurriculumNode(
                    id = "node-1",
                    courseId = course.id,
                    parentId = null,
                    type = CurriculumNodeType.TOPIC,
                    title = "Выборка",
                    description = "Научиться выбирать данные.",
                    position = 0,
                    estimatedMinutes = 15,
                    conceptIds = listOf(concept.id),
                ),
            ),
            concepts = listOf(concept),
        )
    }

    private fun validLessonJson(sourceChunkId: String = "") = """
        {
          "title":"Практика SELECT",
          "introduction":"Короткий урок проверит понимание выборки данных.",
          "activities":[
            {
              "kind":"SINGLE_CHOICE",
              "prompt":"Какой оператор выбирает данные из таблицы?",
              "explanation":"SELECT используется для получения выбранных данных.",
              "difficulty":"EASY",
              "estimatedSeconds":40,
              "conceptTitles":["SELECT"],
              "sourceChunkIds":${ids(sourceChunkId)},
              "content":{"options":[{"id":"a","text":"SELECT"},{"id":"b","text":"DELETE"}],"correctOptionId":"a"}
            },
            {
              "kind":"TRUE_FALSE",
              "prompt":"Оцените утверждение о назначении SELECT.",
              "explanation":"SELECT читает данные и сам по себе их не удаляет.",
              "difficulty":"MEDIUM",
              "estimatedSeconds":40,
              "conceptTitles":["SELECT"],
              "sourceChunkIds":${ids(sourceChunkId)},
              "content":{"statement":"SELECT удаляет выбранные строки","expected":false,"correction":"SELECT выбирает строки"}
            },
            {
              "kind":"FILL_BLANK",
              "prompt":"Заполните пропуск в простом запросе.",
              "explanation":"Запрос начинается с ключевого слова SELECT.",
              "difficulty":"EASY",
              "estimatedSeconds":40,
              "conceptTitles":["SELECT"],
              "sourceChunkIds":${ids(sourceChunkId)},
              "content":{"textWithBlank":"{{blank}} name FROM users","acceptedAnswers":["SELECT"],"caseSensitive":false}
            }
          ]
        }
    """.trimIndent()

    private fun validSqlCodeLessonJson() = """
        {
          "title":"Практика SQL",
          "introduction":"Короткий урок по безопасной выборке данных.",
          "activities":[
            {
              "kind":"SINGLE_CHOICE",
              "prompt":"Какой оператор читает строки?",
              "explanation":"SELECT читает выбранные строки.",
              "difficulty":"EASY",
              "estimatedSeconds":40,
              "conceptTitles":["SELECT"],
              "sourceChunkIds":[],
              "content":{"options":[{"id":"a","text":"SELECT"},{"id":"b","text":"DROP"}],"correctOptionId":"a"}
            },
            {
              "kind":"MINI_CODE",
              "prompt":"Напишите запрос, который вернёт активных пользователей по id.",
              "explanation":"WHERE фильтрует строки, ORDER BY задаёт порядок.",
              "difficulty":"MEDIUM",
              "estimatedSeconds":120,
              "conceptTitles":["SELECT"],
              "sourceChunkIds":[],
              "content":{
                "language":"SQL",
                "starterCode":"SELECT name FROM users",
                "tests":[
                  {
                    "id":"public",
                    "input":"CREATE TABLE users(id INTEGER, name TEXT, active INTEGER); INSERT INTO users VALUES(1, 'Ada', 1);",
                    "expectedOutput":"Ada",
                    "hidden":false
                  },
                  {
                    "id":"hidden",
                    "input":"CREATE TABLE users(id INTEGER, name TEXT, active INTEGER); INSERT INTO users VALUES(2, 'Linus', 0); INSERT INTO users VALUES(1, 'Grace', 1);",
                    "expectedOutput":"Grace",
                    "hidden":true
                  }
                ]
              }
            },
            {
              "kind":"TRUE_FALSE",
              "prompt":"Оцените безопасность запроса.",
              "explanation":"SELECT не изменяет строки.",
              "difficulty":"EASY",
              "estimatedSeconds":40,
              "conceptTitles":["SELECT"],
              "sourceChunkIds":[],
              "content":{"statement":"SELECT изменяет таблицу","expected":false,"correction":"SELECT читает данные"}
            }
          ]
        }
    """.trimIndent()

    private fun ids(value: String) = if (value.isBlank()) "[]" else """["$value"]"""
}
