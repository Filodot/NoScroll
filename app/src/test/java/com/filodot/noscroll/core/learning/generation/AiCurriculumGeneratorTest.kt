package com.filodot.noscroll.core.learning.generation

import com.filodot.noscroll.core.learning.ai.AiGateway
import com.filodot.noscroll.core.learning.ai.AiGenerationRequest
import com.filodot.noscroll.core.learning.ai.AiGenerationResponse
import com.filodot.noscroll.core.learning.ai.AiProviderId
import com.filodot.noscroll.core.learning.model.CourseOrigin
import com.filodot.noscroll.core.learning.model.CourseStatus
import com.filodot.noscroll.core.learning.model.GroundingMode
import com.filodot.noscroll.core.learning.model.LearningCourse
import com.filodot.noscroll.core.learning.model.LearningCourseContent
import com.filodot.noscroll.core.learning.model.LearningSource
import com.filodot.noscroll.core.learning.model.LearningSourceChunk
import com.filodot.noscroll.core.learning.model.LearningSourceType
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCurriculumGeneratorTest {
    @Test
    fun `retries malformed response and maps valid topic curriculum`() = runBlocking {
        val gateway = QueueGateway(listOf("{}", topicPlanJson()))
        var id = 0
        val generator = AiCurriculumGenerator(
            gateway = gateway,
            idGenerator = { "id-${id++}" },
        )

        val result = generator.generate(topicCourse())

        assertEquals(2, result.attempts)
        assertEquals(2, result.nodes.size)
        assertEquals(3, result.concepts.size)
        assertTrue(result.concepts.all { it.citations.isEmpty() })
        assertTrue(result.concepts[1].prerequisiteIds.contains(result.concepts[0].id))
        assertTrue(gateway.requests[1].userPrompt.contains("Предыдущий ответ отклонён"))
    }

    @Test
    fun `material curriculum keeps only real source citations`() = runBlocking {
        val content = materialCourse()
        val gateway = QueueGateway(listOf(materialPlanJson("chunk-1")))
        val generator = AiCurriculumGenerator(gateway)

        val result = generator.generate(content)

        assertEquals("chunk-1", result.concepts.first().citations.single().chunkId)
        assertEquals("source-1", result.concepts.first().citations.single().sourceId)
        assertTrue(gateway.requests.single().userPrompt.contains("<source chunkId=\"chunk-1\""))
    }

    @Test
    fun `rejects hallucinated citations after quality retries`() = runBlocking {
        val gateway = QueueGateway(
            listOf(
                materialPlanJson("invented"),
                materialPlanJson("invented"),
            ),
        )
        val generator = AiCurriculumGenerator(gateway, maximumAttempts = 2)

        val error = runCatching { generator.generate(materialCourse()) }.exceptionOrNull()

        assertTrue(error is CurriculumGenerationException)
        assertEquals(2, (error as CurriculumGenerationException).attempts)
        assertTrue(error.issues.any { it.contains("неизвестный фрагмент") })
    }

    @Test
    fun `representative selection includes beginning middle and end within budget`() {
        val chunks = (0 until 100).map { index ->
            chunk(id = "chunk-$index", position = index, text = "x".repeat(3_000))
        }

        val selected = selectRepresentativeChunks(chunks)

        assertEquals(12, selected.size)
        assertEquals("chunk-0", selected.first().id)
        assertEquals("chunk-99", selected.last().id)
        assertTrue(selected.all { it.text.length == 2_400 })
    }

    private class QueueGateway(
        responses: List<String>,
    ) : AiGateway {
        private val queue = ArrayDeque(responses)
        val requests = mutableListOf<AiGenerationRequest>()

        override suspend fun generate(request: AiGenerationRequest): AiGenerationResponse {
            requests += request
            return AiGenerationResponse(
                providerId = AiProviderId.GEMINI,
                modelId = "test-model",
                json = queue.removeFirst(),
                generatedAt = Instant.EPOCH,
            )
        }
    }

    private fun topicCourse(): LearningCourseContent {
        val course = course(CourseOrigin.TOPIC, GroundingMode.AI_KNOWLEDGE)
        return LearningCourseContent(
            course = course,
            sources = listOf(
                LearningSource(
                    id = "topic-source",
                    courseId = course.id,
                    title = "Основы SQL для аналитики",
                    type = LearningSourceType.TOPIC,
                    contentHash = null,
                    importedAt = Instant.EPOCH,
                ),
            ),
            curriculumNodes = emptyList(),
            concepts = emptyList(),
        )
    }

    private fun materialCourse(): LearningCourseContent {
        val course = course(CourseOrigin.MATERIAL, GroundingMode.SOURCE_REQUIRED)
        val source = LearningSource(
            id = "source-1",
            courseId = course.id,
            title = "Конспект",
            type = LearningSourceType.PLAIN_TEXT,
            contentHash = "hash",
            importedAt = Instant.EPOCH,
        )
        return LearningCourseContent(
            course = course,
            sources = listOf(source),
            sourceChunks = listOf(chunk("chunk-1", 0, "SQL выбирает данные из таблиц.")),
            curriculumNodes = emptyList(),
            concepts = emptyList(),
        )
    }

    private fun course(origin: CourseOrigin, grounding: GroundingMode) = LearningCourse(
        id = "course-1",
        title = "SQL",
        description = "Последовательный практический курс по основам SQL.",
        origin = origin,
        groundingMode = grounding,
        status = CourseStatus.DRAFT,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun chunk(id: String, position: Int, text: String) = LearningSourceChunk(
        id = id,
        sourceId = "source-1",
        courseId = "course-1",
        position = position,
        text = text,
        characterStart = position * text.length,
        characterEnd = (position + 1) * text.length,
        estimatedTokens = text.length / 4,
    )

    private fun topicPlanJson() = """
        {
          "title":"Основы SQL",
          "description":"Последовательная программа для освоения запросов и фильтрации данных.",
          "nodes":[
            {
              "title":"Выборка данных",
              "description":"Научиться получать нужные столбцы и строки из одной таблицы.",
              "estimatedMinutes":20,
              "concepts":[
                {
                  "title":"SELECT",
                  "summary":"Оператор выбирает значения указанных столбцов из источника данных.",
                  "prerequisiteTitles":[],
                  "sourceChunkIds":[]
                },
                {
                  "title":"FROM",
                  "summary":"Предложение задаёт таблицу, из которой выполняется выборка.",
                  "prerequisiteTitles":["SELECT"],
                  "sourceChunkIds":[]
                }
              ]
            },
            {
              "title":"Фильтрация строк",
              "description":"Научиться задавать условия и получать только подходящие строки.",
              "estimatedMinutes":20,
              "concepts":[
                {
                  "title":"WHERE",
                  "summary":"Предложение оставляет строки, для которых условие истинно.",
                  "prerequisiteTitles":["SELECT","FROM"],
                  "sourceChunkIds":[]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun materialPlanJson(chunkId: String) = """
        {
          "title":"Курс по конспекту",
          "description":"Последовательная программа, полностью основанная на загруженном конспекте.",
          "nodes":[
            {
              "title":"Получение данных",
              "description":"Разобраться, как запрос получает значения из исходной таблицы.",
              "estimatedMinutes":15,
              "concepts":[
                {
                  "title":"Выборка",
                  "summary":"Запрос позволяет выбирать данные, находящиеся в таблицах.",
                  "prerequisiteTitles":[],
                  "sourceChunkIds":["$chunkId"]
                },
                {
                  "title":"Таблица",
                  "summary":"Таблица является структурированным источником строк и столбцов.",
                  "prerequisiteTitles":["Выборка"],
                  "sourceChunkIds":["$chunkId"]
                }
              ]
            },
            {
              "title":"Практика запросов",
              "description":"Закрепить выбор данных несколькими последовательными запросами.",
              "estimatedMinutes":15,
              "concepts":[
                {
                  "title":"Результат запроса",
                  "summary":"Результат содержит выбранные запросом строки и столбцы.",
                  "prerequisiteTitles":["Выборка","Таблица"],
                  "sourceChunkIds":["$chunkId"]
                }
              ]
            }
          ]
        }
    """.trimIndent()
}
