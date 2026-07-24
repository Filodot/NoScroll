package com.filodot.noscroll.data.learning.ai

import com.filodot.noscroll.core.learning.ai.AiGenerationRequest
import com.filodot.noscroll.core.learning.ai.AiProviderId
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProvidersTest {
    private val request = AiGenerationRequest(
        systemPrompt = "system",
        userPrompt = "user",
        schemaName = "course_plan",
        jsonSchema = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
        },
        maxOutputTokens = 123,
    )
    private val timestamp = Instant.parse("2026-07-24T12:00:00Z")

    @Test
    fun `Gemini sends schema and parses candidate JSON`() = runBlocking {
        val transport = RecordingTransport(
            AiHttpResponse(
                200,
                """{"candidates":[{"content":{"parts":[{"text":"{\"title\":\"SQL\"}"}]}}]}""",
                emptyMap(),
            ),
        )

        val result = GeminiAiProvider(transport) { timestamp }
            .generate(request, "gemini-secret", "gemini-3.6-flash")

        assertEquals(AiProviderId.GEMINI, result.providerId)
        assertEquals("""{"title":"SQL"}""", result.json)
        assertTrue(transport.request.body.contains("\"responseJsonSchema\""))
        assertTrue(transport.request.body.contains("\"maxOutputTokens\":123"))
        assertEquals("gemini-secret", transport.request.headers["x-goog-api-key"])
        assertFalse(transport.request.body.contains("gemini-secret"))
    }

    @Test
    fun `Groq uses strict structured output and bearer key`() = runBlocking {
        val transport = RecordingTransport(
            AiHttpResponse(
                200,
                """{"choices":[{"message":{"content":"{\"nodes\":[]}"}}]}""",
                emptyMap(),
            ),
        )

        val result = GroqAiProvider(transport) { timestamp }
            .generate(request, "groq-secret", "openai/gpt-oss-20b")

        assertEquals("""{"nodes":[]}""", result.json)
        assertEquals("Bearer groq-secret", transport.request.headers["Authorization"])
        assertTrue(transport.request.body.contains("\"strict\":true"))
        assertTrue(transport.request.body.contains("\"schema\""))
        assertFalse(transport.request.body.contains("groq-secret"))
    }

    @Test
    fun `OpenRouter requires a provider that supports requested schema`() = runBlocking {
        val transport = RecordingTransport(
            AiHttpResponse(
                200,
                """{"choices":[{"message":{"content":"```json\n{\"ok\":true}\n```"}}]}""",
                emptyMap(),
            ),
        )

        val result = OpenRouterAiProvider(transport) { timestamp }
            .generate(request, "router-secret", "openrouter/free")

        assertEquals("""{"ok":true}""", result.json)
        assertTrue(transport.request.body.contains("\"require_parameters\":true"))
    }

    private class RecordingTransport(
        private val response: AiHttpResponse,
    ) : AiHttpTransport {
        lateinit var request: AiHttpRequest

        override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
            this.request = request
            return response
        }
    }
}
