package com.filodot.noscroll.core.emergency

import com.filodot.noscroll.core.contracts.EmergencyRepository
import com.filodot.noscroll.core.contracts.WallClock
import com.filodot.noscroll.core.model.EmergencyActivationSource
import com.filodot.noscroll.core.model.EmergencyEvent
import com.filodot.noscroll.core.model.EmergencyState
import com.filodot.noscroll.core.model.UserSettings
import java.time.Duration
import java.time.Instant
import java.util.UUID

sealed interface EmergencyReasonError {
    data class TooShort(val length: Int) : EmergencyReasonError

    data class TooLong(val length: Int) : EmergencyReasonError
}

data class EmergencyReasonValidation(
    val normalizedReason: String,
    val error: EmergencyReasonError? = null,
) {
    val isValid: Boolean get() = error == null
}

data class EmergencyMetrics(
    val durationSeconds: Long,
    val youtubeSecondsDuring: Long,
)

enum class EmergencyCommand {
    REEVALUATE_POLICY,
}

sealed interface EmergencyActivationResult {
    data class Activated(
        val event: EmergencyEvent,
        val command: EmergencyCommand = EmergencyCommand.REEVALUATE_POLICY,
    ) : EmergencyActivationResult

    data class InvalidReason(val validation: EmergencyReasonValidation) :
        EmergencyActivationResult

    data object LimitsAlreadyDisabled : EmergencyActivationResult

    data class AlreadyActive(val event: EmergencyEvent) : EmergencyActivationResult
}

sealed interface EmergencyUsageUpdateResult {
    data object NoActiveEmergency : EmergencyUsageUpdateResult

    data class IgnoredNonPositiveDelta(val event: EmergencyEvent) : EmergencyUsageUpdateResult

    data class Updated(val event: EmergencyEvent) : EmergencyUsageUpdateResult
}

sealed interface EmergencyDeactivationResult {
    data object NoActiveEmergency : EmergencyDeactivationResult

    data class Deactivated(
        val event: EmergencyEvent,
        val metrics: EmergencyMetrics,
        val command: EmergencyCommand = EmergencyCommand.REEVALUATE_POLICY,
    ) : EmergencyDeactivationResult
}

fun interface EmergencyIdGenerator {
    fun create(activatedAt: Instant): String
}

object UuidEmergencyIdGenerator : EmergencyIdGenerator {
    override fun create(activatedAt: Instant): String = UUID.randomUUID().toString()
}

/** Persistent Emergency Stop use cases. No state changes occur until [activate] succeeds. */
class EmergencyStopDomain(
    private val repository: EmergencyRepository,
    private val wallClock: WallClock,
    private val idGenerator: EmergencyIdGenerator = UuidEmergencyIdGenerator,
) {
    fun state(): EmergencyState = repository.state.value

    fun validateReason(reason: String): EmergencyReasonValidation {
        val normalized = reason.trim()
        val error = when {
            normalized.length < MIN_REASON_LENGTH ->
                EmergencyReasonError.TooShort(normalized.length)

            normalized.length > MAX_REASON_LENGTH ->
                EmergencyReasonError.TooLong(normalized.length)

            else -> null
        }
        return EmergencyReasonValidation(normalizedReason = normalized, error = error)
    }

    /** Explicitly models dismissing the form: validation text is never persisted on cancel. */
    fun cancelActivation(): EmergencyState = repository.state.value

    suspend fun activate(
        reason: String,
        source: EmergencyActivationSource,
        settings: UserSettings,
    ): EmergencyActivationResult {
        repository.state.value.activeEvent?.takeIf { it.deactivatedAt == null }?.let {
            return EmergencyActivationResult.AlreadyActive(it)
        }
        if (!settings.shortsGateEnabled && !settings.dailyLimitEnabled) {
            return EmergencyActivationResult.LimitsAlreadyDisabled
        }

        val validation = validateReason(reason)
        if (!validation.isValid) return EmergencyActivationResult.InvalidReason(validation)

        val activatedAt = wallClock.now()
        val event = EmergencyEvent(
            id = idGenerator.create(activatedAt),
            reason = validation.normalizedReason,
            activatedAt = activatedAt,
            activationSource = source,
        )
        repository.activate(event)
        return EmergencyActivationResult.Activated(event)
    }

    /**
     * Persists an aggregate delta on the active event. The frozen repository's activate operation
     * is an idempotent upsert for the same event ID, so the aggregate survives process death.
     */
    suspend fun addYoutubeSeconds(deltaSeconds: Long): EmergencyUsageUpdateResult {
        val active = repository.state.value.activeEvent
            ?.takeIf { it.deactivatedAt == null }
            ?: return EmergencyUsageUpdateResult.NoActiveEmergency
        if (deltaSeconds <= 0) {
            return EmergencyUsageUpdateResult.IgnoredNonPositiveDelta(active)
        }

        val updated = active.copy(
            youtubeSecondsDuring = saturatingAdd(
                active.youtubeSecondsDuring.coerceAtLeast(0),
                deltaSeconds,
            ),
        )
        repository.activate(updated)
        return EmergencyUsageUpdateResult.Updated(updated)
    }

    suspend fun deactivate(): EmergencyDeactivationResult {
        val active = repository.state.value.activeEvent
            ?.takeIf { it.deactivatedAt == null }
            ?: return EmergencyDeactivationResult.NoActiveEmergency
        val observedAt = wallClock.now()
        val deactivatedAt = if (observedAt.isBefore(active.activatedAt)) {
            active.activatedAt
        } else {
            observedAt
        }
        val completed = active.copy(deactivatedAt = deactivatedAt)
        repository.deactivate(completed)
        return EmergencyDeactivationResult.Deactivated(
            event = completed,
            metrics = metrics(completed, deactivatedAt),
        )
    }

    fun metrics(
        event: EmergencyEvent,
        observedAt: Instant = wallClock.now(),
    ): EmergencyMetrics {
        val end = event.deactivatedAt ?: observedAt
        return EmergencyMetrics(
            durationSeconds = positiveSecondsBetween(event.activatedAt, end),
            youtubeSecondsDuring = event.youtubeSecondsDuring.coerceAtLeast(0),
        )
    }

    suspend fun deleteHistory() {
        repository.deleteHistory()
    }
}

private fun positiveSecondsBetween(start: Instant, end: Instant): Long {
    if (!end.isAfter(start)) return 0
    return try {
        Duration.between(start, end).seconds.coerceAtLeast(0)
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }
}

private fun saturatingAdd(left: Long, right: Long): Long =
    if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right

const val MIN_REASON_LENGTH = 5
const val MAX_REASON_LENGTH = 300
