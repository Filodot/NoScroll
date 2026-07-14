package com.filodot.noscroll.monitoring.usagestats

import com.filodot.noscroll.core.model.NormalizedUsageEvent

enum class UsageAccessState {
    GRANTED,
    DENIED,
    SERVICE_UNAVAILABLE,
}

enum class UsageStatsFailureReason {
    PERMISSION_DENIED,
    SERVICE_UNAVAILABLE,
    QUERY_FAILED,
    INVALID_RANGE,
}

sealed interface UsageStatsQueryResult {
    data class Success(
        val events: List<NormalizedUsageEvent>,
    ) : UsageStatsQueryResult

    data class Failure(
        val reason: UsageStatsFailureReason,
    ) : UsageStatsQueryResult
}

class UsageStatsSourceException(
    val reason: UsageStatsFailureReason,
) : IllegalStateException("UsageStats query failed: ${reason.name}")

fun interface UsageAccessChecker {
    fun checkAccess(): UsageAccessState
}

internal data class RawUsageEvent(
    val packageName: String?,
    val eventType: Int,
    val timestampMillis: Long,
)

internal fun interface PlatformUsageEventsReader {
    fun readEvents(startMillis: Long, endMillis: Long): List<RawUsageEvent>
}
