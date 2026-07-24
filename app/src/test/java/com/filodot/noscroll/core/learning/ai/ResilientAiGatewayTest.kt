package com.filodot.noscroll.core.learning.ai

import com.filodot.noscroll.core.testing.InMemoryAiCredentialRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResilientAiGatewayTest {
    private val request = AiGenerationRequest(
        systemPrompt = "system",
        userPrompt = "user",
        schemaName = "test",
        jsonSchema = buildJsonObject { put("type", "object") },
    )

    @Test
    fun `falls back to next configured provider`() = runBlocking {
        val credentials = credentials()
        val gemini = StubProvider(AiProviderId.GEMINI) {
            throw AiProviderException(
                AiProviderId.GEMINI,
                AiFailureKind.RATE_LIMIT,
                retryable = true,
                message = "limit",
            )
        }
        val groq = StubProvider(AiProviderId.GROQ) {
            AiGenerationResponse(AiProviderId.GROQ, "groq-model", "{}", Instant.EPOCH)
        }
        val gateway = ResilientAiGateway(credentials, listOf(gemini, groq))

        val response = gateway.generate(request)

        assertEquals(AiProviderId.GROQ, response.providerId)
        assertEquals(1, gemini.calls)
        assertEquals(1, groq.calls)
    }

    @Test
    fun `opens circuit after repeated transient failures and retries after cooldown`() = runBlocking {
        var currentTime = Instant.EPOCH
        val credentials = credentials()
        val gemini = StubProvider(AiProviderId.GEMINI) {
            throw AiProviderException(
                AiProviderId.GEMINI,
                AiFailureKind.UNAVAILABLE,
                retryable = true,
                message = "down",
            )
        }
        val groq = StubProvider(AiProviderId.GROQ) {
            AiGenerationResponse(AiProviderId.GROQ, "groq-model", "{}", currentTime)
        }
        val gateway = ResilientAiGateway(
            credentials = credentials,
            providers = listOf(gemini, groq),
            now = { currentTime },
            failureThreshold = 2,
            circuitCooldown = Duration.ofMinutes(10),
        )

        gateway.generate(request)
        gateway.generate(request)
        gateway.generate(request)
        assertEquals(2, gemini.calls)

        currentTime = currentTime.plus(Duration.ofMinutes(11))
        gateway.generate(request)
        assertEquals(3, gemini.calls)
    }

    @Test
    fun `reports absence of configured keys without leaking secrets`() = runBlocking {
        val gateway = ResilientAiGateway(
            credentials = InMemoryAiCredentialRepository(),
            providers = emptyList(),
        )

        val error = runCatching { gateway.generate(request) }.exceptionOrNull()

        assertTrue(error is AllAiProvidersFailedException)
        assertTrue((error as AllAiProvidersFailedException).failures.isEmpty())
    }

    private fun credentials() = InMemoryAiCredentialRepository(
        initialKeys = mapOf(
            AiProviderId.GEMINI to "gemini-secret",
            AiProviderId.GROQ to "groq-secret",
        ),
    )

    private class StubProvider(
        override val id: AiProviderId,
        private val result: suspend () -> AiGenerationResponse,
    ) : AiTextProvider {
        var calls: Int = 0

        override suspend fun generate(
            request: AiGenerationRequest,
            apiKey: String,
            modelId: String,
        ): AiGenerationResponse {
            calls += 1
            return result()
        }
    }
}
