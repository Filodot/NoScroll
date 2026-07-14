package com.filodot.noscroll.core.usage.daily

import com.filodot.noscroll.core.model.NormalizedUsageEvent
import com.filodot.noscroll.core.model.UsageEventType
import java.time.Duration
import java.time.Instant

data class ForegroundReconstruction(
    val totalSeconds: Long,
    val hasOpenIntervalAtEnd: Boolean,
)

/** Reconstructs YouTube foreground time from normalized, potentially unordered usage events. */
class YouTubeForegroundReconstructor(
    private val youtubePackageName: String = YOUTUBE_PACKAGE_NAME,
) {
    fun reconstruct(
        events: List<NormalizedUsageEvent>,
        rangeStart: Instant,
        rangeEnd: Instant,
    ): ForegroundReconstruction {
        if (!rangeEnd.isAfter(rangeStart)) {
            return ForegroundReconstruction(
                totalSeconds = 0,
                hasOpenIntervalAtEnd = false,
            )
        }

        val orderedEvents = events
            .withIndex()
            .filter { (_, event) -> !event.timestamp.isAfter(rangeEnd) }
            .sortedWith(compareBy({ it.value.timestamp }, { it.index }))

        var state = ForegroundState.UNKNOWN
        for ((_, event) in orderedEvents) {
            if (!event.timestamp.isBefore(rangeStart)) break
            state = state.after(event, youtubePackageName)
        }

        var intervalStart = if (state == ForegroundState.YOUTUBE) rangeStart else null
        var canInferFromRangeStart = state == ForegroundState.UNKNOWN
        var totalMillis = 0L

        for ((_, event) in orderedEvents) {
            if (event.timestamp.isBefore(rangeStart)) continue

            when {
                event.packageName == youtubePackageName &&
                    event.type == UsageEventType.ACTIVITY_RESUMED -> {
                    if (state != ForegroundState.YOUTUBE) {
                        intervalStart = event.timestamp
                    }
                    state = ForegroundState.YOUTUBE
                    canInferFromRangeStart = false
                }

                event.packageName == youtubePackageName &&
                    event.type == UsageEventType.ACTIVITY_PAUSED -> {
                    if (state == ForegroundState.YOUTUBE && intervalStart != null) {
                        totalMillis = saturatingAdd(
                            totalMillis,
                            positiveMillisBetween(intervalStart, event.timestamp),
                        )
                    } else if (canInferFromRangeStart) {
                        // The query can begin in the middle of an interval, for example at midnight.
                        totalMillis = saturatingAdd(
                            totalMillis,
                            positiveMillisBetween(rangeStart, event.timestamp),
                        )
                    }
                    intervalStart = null
                    state = ForegroundState.NOT_YOUTUBE
                    canInferFromRangeStart = false
                }

                event.packageName != youtubePackageName &&
                    event.type == UsageEventType.ACTIVITY_RESUMED -> {
                    if (state == ForegroundState.YOUTUBE && intervalStart != null) {
                        totalMillis = saturatingAdd(
                            totalMillis,
                            positiveMillisBetween(intervalStart, event.timestamp),
                        )
                    }
                    intervalStart = null
                    state = ForegroundState.NOT_YOUTUBE
                    canInferFromRangeStart = false
                }

                else -> canInferFromRangeStart = false
            }
        }

        val isOpenAtEnd = state == ForegroundState.YOUTUBE && intervalStart != null
        if (isOpenAtEnd) {
            totalMillis = saturatingAdd(
                totalMillis,
                positiveMillisBetween(requireNotNull(intervalStart), rangeEnd),
            )
        }

        return ForegroundReconstruction(
            totalSeconds = totalMillis / MILLIS_PER_SECOND,
            hasOpenIntervalAtEnd = isOpenAtEnd,
        )
    }
}

private enum class ForegroundState {
    UNKNOWN,
    YOUTUBE,
    NOT_YOUTUBE,
    ;

    fun after(event: NormalizedUsageEvent, youtubePackageName: String): ForegroundState =
        when {
            event.packageName == youtubePackageName &&
                event.type == UsageEventType.ACTIVITY_RESUMED -> YOUTUBE

            event.packageName == youtubePackageName &&
                event.type == UsageEventType.ACTIVITY_PAUSED -> NOT_YOUTUBE

            event.packageName != youtubePackageName &&
                event.type == UsageEventType.ACTIVITY_RESUMED -> NOT_YOUTUBE

            else -> this
        }
}

private fun positiveMillisBetween(start: Instant, end: Instant): Long {
    if (!end.isAfter(start)) return 0
    return try {
        Duration.between(start, end).toMillis().coerceAtLeast(0)
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }
}

private fun saturatingAdd(left: Long, right: Long): Long =
    if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right

const val YOUTUBE_PACKAGE_NAME = "com.google.android.youtube"

private const val MILLIS_PER_SECOND = 1_000L
