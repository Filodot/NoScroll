package com.filodot.noscroll.data.learning.ai

import com.filodot.noscroll.core.learning.ai.AiFailureKind
import com.filodot.noscroll.core.learning.ai.AiGenerationRequest
import com.filodot.noscroll.core.learning.ai.AiGenerationResponse
import com.filodot.noscroll.core.learning.ai.AiProviderException
import com.filodot.noscroll.core.learning.ai.AiProviderId
import com.filodot.noscroll.core.learning.ai.AiTextProvider
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class GeminiAiProvider(
    private val transport: AiHttpTransport,
    private val now: () -> Instant = Instant::now,
) : AiTextProvider {
    override val id = AiProviderId.GEMINI

    override suspend fun generate(
        request: AiGenerationRequest,
        apiKey: String,
        modelId: String,
    ): AiGenerationResponse = providerCall(id) {
        val body = buildJsonObject {
            put("systemInstruction", parts(request.systemPrompt))
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("parts", textParts(request.userPrompt))
                        },
                    )
                },
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("responseMimeType", "application/json")
                    put("responseJsonSchema", request.jsonSchema)
                    put("maxOutputTokens", request.maxOutputTokens)
                },
            )
        }
        val response = transport.execute(
            AiHttpRequest(
                url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "${encodePathSegment(modelId)}:generateContent",
                headers = mapOf("x-goog-api-key" to apiKey),
                body = body.toString(),
            ),
        )
        checkResponse(id, response)
        val root = JSON.parseToJsonElement(response.body).jsonObject
        val text = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            ?.takeIf(String::isNotBlank)
            ?: throw invalidResponse(id, "Gemini вернул ответ без JSON")
        AiGenerationResponse(id, modelId, stripCodeFence(text), now())
    }

    private fun parts(text: String): JsonObject = buildJsonObject {
        put("parts", textParts(text))
    }
}

class GroqAiProvider(
    transport: AiHttpTransport,
    now: () -> Instant = Instant::now,
) : OpenAiCompatibleProvider(
    id = AiProviderId.GROQ,
    endpoint = "https://api.groq.com/openai/v1/chat/completions",
    transport = transport,
    now = now,
)

class OpenRouterAiProvider(
    transport: AiHttpTransport,
    now: () -> Instant = Instant::now,
) : OpenAiCompatibleProvider(
    id = AiProviderId.OPENROUTER,
    endpoint = "https://openrouter.ai/api/v1/chat/completions",
    transport = transport,
    now = now,
)

abstract class OpenAiCompatibleProvider(
    final override val id: AiProviderId,
    private val endpoint: String,
    private val transport: AiHttpTransport,
    private val now: () -> Instant,
) : AiTextProvider {
    override suspend fun generate(
        request: AiGenerationRequest,
        apiKey: String,
        modelId: String,
    ): AiGenerationResponse = providerCall(id) {
        val body = buildJsonObject {
            put("model", modelId)
            put(
                "messages",
                buildJsonArray {
                    add(message("system", request.systemPrompt))
                    add(message("user", request.userPrompt))
                },
            )
            put("max_completion_tokens", request.maxOutputTokens)
            put(
                "response_format",
                buildJsonObject {
                    put("type", "json_schema")
                    put(
                        "json_schema",
                        buildJsonObject {
                            put("name", request.schemaName)
                            put("strict", true)
                            put("schema", request.jsonSchema)
                        },
                    )
                },
            )
            if (id == AiProviderId.OPENROUTER) {
                put(
                    "provider",
                    buildJsonObject {
                        put("require_parameters", true)
                    },
                )
            }
        }
        val response = transport.execute(
            AiHttpRequest(
                url = endpoint,
                headers = mapOf("Authorization" to "Bearer $apiKey"),
                body = body.toString(),
            ),
        )
        checkResponse(id, response)
        val root = JSON.parseToJsonElement(response.body).jsonObject
        val text = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: throw invalidResponse(id, "Провайдер вернул ответ без JSON")
        AiGenerationResponse(id, modelId, stripCodeFence(text), now())
    }
}

private suspend fun <T> providerCall(
    providerId: AiProviderId,
    block: suspend () -> T,
): T = try {
    block()
} catch (error: AiProviderException) {
    throw error
} catch (error: AiNetworkTimeoutException) {
    throw AiProviderException(
        providerId,
        AiFailureKind.TIMEOUT,
        retryable = true,
        message = "Время ожидания ответа истекло",
        cause = error,
    )
} catch (error: IOException) {
    throw AiProviderException(
        providerId,
        AiFailureKind.NETWORK,
        retryable = true,
        message = "Ошибка сети",
        cause = error,
    )
} catch (error: Exception) {
    if (error is CancellationException) throw error
    throw invalidResponse(providerId, "Не удалось разобрать ответ", error)
}

private fun checkResponse(providerId: AiProviderId, response: AiHttpResponse) {
    if (response.statusCode in 200..299) return
    val kind = when (response.statusCode) {
        401, 403 -> AiFailureKind.AUTHENTICATION
        408 -> AiFailureKind.TIMEOUT
        429 -> AiFailureKind.RATE_LIMIT
        in 400..499 -> AiFailureKind.INVALID_REQUEST
        else -> AiFailureKind.UNAVAILABLE
    }
    val safeMessage = runCatching {
        JSON.parseToJsonElement(response.body).jsonObject["error"]?.let { error ->
            when (error) {
                is JsonObject -> error["message"]?.jsonPrimitive?.contentOrNull
                else -> error.jsonPrimitive.contentOrNull
            }
        }
    }.getOrNull()?.take(240)
    throw AiProviderException(
        providerId = providerId,
        kind = kind,
        retryable = kind in setOf(
            AiFailureKind.RATE_LIMIT,
            AiFailureKind.TIMEOUT,
            AiFailureKind.UNAVAILABLE,
            AiFailureKind.NETWORK,
        ),
        message = safeMessage ?: "HTTP ${response.statusCode}",
    )
}

private fun invalidResponse(
    providerId: AiProviderId,
    message: String,
    cause: Throwable? = null,
) = AiProviderException(
    providerId,
    AiFailureKind.INVALID_RESPONSE,
    retryable = true,
    message = message,
    cause = cause,
)

private fun message(role: String, text: String): JsonObject = buildJsonObject {
    put("role", role)
    put("content", text)
}

private fun textParts(text: String): JsonArray = buildJsonArray {
    add(buildJsonObject { put("text", text) })
}

private fun stripCodeFence(value: String): String {
    val trimmed = value.trim()
    if (!trimmed.startsWith("```")) return trimmed
    return trimmed
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
}

private fun encodePathSegment(value: String): String {
    require(value.matches(Regex("[A-Za-z0-9._-]+"))) { "Недопустимый идентификатор модели" }
    return value
}

private val JSON = Json {
    ignoreUnknownKeys = true
    isLenient = false
}
