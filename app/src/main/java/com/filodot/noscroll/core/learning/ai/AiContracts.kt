package com.filodot.noscroll.core.learning.ai

import java.time.Instant
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

enum class AiProviderId {
    GEMINI,
    GROQ,
    OPENROUTER,
}

data class AiProviderSettings(
    val id: AiProviderId,
    val enabled: Boolean,
    val priority: Int,
    val modelId: String,
    val hasApiKey: Boolean,
)

interface AiCredentialRepository {
    val settings: StateFlow<List<AiProviderSettings>>

    suspend fun saveApiKey(providerId: AiProviderId, apiKey: String)

    suspend fun clearApiKey(providerId: AiProviderId)

    suspend fun setEnabled(providerId: AiProviderId, enabled: Boolean)

    suspend fun setModel(providerId: AiProviderId, modelId: String)

    suspend fun getApiKey(providerId: AiProviderId): String?
}

data class AiGenerationRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val schemaName: String,
    val jsonSchema: JsonObject,
    val maxOutputTokens: Int = 4_096,
)

data class AiGenerationResponse(
    val providerId: AiProviderId,
    val modelId: String,
    val json: String,
    val generatedAt: Instant,
)

interface AiTextProvider {
    val id: AiProviderId

    suspend fun generate(
        request: AiGenerationRequest,
        apiKey: String,
        modelId: String,
    ): AiGenerationResponse
}

enum class AiFailureKind {
    AUTHENTICATION,
    RATE_LIMIT,
    TIMEOUT,
    UNAVAILABLE,
    INVALID_REQUEST,
    INVALID_RESPONSE,
    NETWORK,
}

class AiProviderException(
    val providerId: AiProviderId,
    val kind: AiFailureKind,
    val retryable: Boolean,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

data class AiProviderFailure(
    val providerId: AiProviderId,
    val kind: AiFailureKind,
    val message: String,
)

class AllAiProvidersFailedException(
    val failures: List<AiProviderFailure>,
) : Exception(
    failures.joinToString(
        prefix = "Все настроенные AI-провайдеры недоступны: ",
        separator = "; ",
    ) { "${it.providerId}: ${it.message}" },
)
