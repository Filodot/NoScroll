package com.filodot.noscroll.core.learning.ai

import java.time.Duration
import java.time.Instant

class ResilientAiGateway(
    private val credentials: AiCredentialRepository,
    providers: List<AiTextProvider>,
    private val now: () -> Instant = Instant::now,
    private val failureThreshold: Int = 2,
    private val circuitCooldown: Duration = Duration.ofMinutes(10),
) {
    private val providersById = providers.associateBy(AiTextProvider::id)
    private val failures = mutableMapOf<AiProviderId, FailureState>()

    suspend fun generate(request: AiGenerationRequest): AiGenerationResponse {
        val providerSettings = credentials.settings.value
            .filter { it.enabled && it.hasApiKey }
            .sortedBy(AiProviderSettings::priority)
        if (providerSettings.isEmpty()) {
            throw AllAiProvidersFailedException(emptyList())
        }
        val errors = mutableListOf<AiProviderFailure>()
        providerSettings.forEach { settings ->
            val provider = providersById[settings.id] ?: return@forEach
            if (isCircuitOpen(settings.id)) {
                errors += AiProviderFailure(
                    settings.id,
                    AiFailureKind.UNAVAILABLE,
                    "временно пропущен после повторных сбоев",
                )
                return@forEach
            }
            val apiKey = credentials.getApiKey(settings.id)
            if (apiKey.isNullOrBlank()) return@forEach
            try {
                return provider.generate(request, apiKey, settings.modelId).also {
                    failures.remove(settings.id)
                }
            } catch (error: AiProviderException) {
                errors += AiProviderFailure(settings.id, error.kind, error.message.orEmpty())
                recordFailure(error)
            }
        }
        throw AllAiProvidersFailedException(errors)
    }

    private fun recordFailure(error: AiProviderException) {
        if (!error.retryable) return
        val previous = failures[error.providerId]
        val count = (previous?.count ?: 0) + 1
        failures[error.providerId] = FailureState(
            count = count,
            openedAt = if (count >= failureThreshold) now() else previous?.openedAt,
        )
    }

    private fun isCircuitOpen(providerId: AiProviderId): Boolean {
        val state = failures[providerId] ?: return false
        val openedAt = state.openedAt ?: return false
        if (Duration.between(openedAt, now()) >= circuitCooldown) {
            failures.remove(providerId)
            return false
        }
        return true
    }

    private data class FailureState(
        val count: Int,
        val openedAt: Instant?,
    )
}
