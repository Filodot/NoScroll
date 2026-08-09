package com.filodot.noscroll.feature.dashboard

import com.filodot.noscroll.core.model.DailyUsage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class UsageTrendDirection {
    IMPROVING,
    INCREASING,
    STABLE,
    NOT_ENOUGH_DATA,
}

data class UsageDayUi(
    val localDate: LocalDate,
    val label: String,
    val observed: Boolean,
    val totalSeconds: Long,
    val youtubeSeconds: Long,
    val shortsSeconds: Long,
    val instagramSeconds: Long,
    val pinterestSeconds: Long,
    val chromeSeconds: Long,
)

data class UsageStatisticsUiState(
    val todayTotalSeconds: Long = 0,
    val todayYoutubeSeconds: Long = 0,
    val todayShortsSeconds: Long = 0,
    val todayInstagramSeconds: Long = 0,
    val todayPinterestSeconds: Long = 0,
    val todayChromeSeconds: Long = 0,
    val recentAverageSeconds: Long? = null,
    val previousAverageSeconds: Long? = null,
    /** Signed: negative means less screen time than in the previous period. */
    val changePercent: Int? = null,
    val trend: UsageTrendDirection = UsageTrendDirection.NOT_ENOUGH_DATA,
    val days: List<UsageDayUi> = emptyList(),
)

internal fun buildUsageStatistics(
    current: DailyUsage,
    history: List<DailyUsage>,
): UsageStatisticsUiState {
    val today = current.localDate
    val byDate = history
        .groupBy(DailyUsage::localDate)
        .mapValues { (_, rows) -> rows.maxBy(DailyUsage::updatedAt) }
        .toMutableMap()
        .apply { this[today] = current }
    val recentRows = rowsInRange(byDate, today.minusDays(7), today.minusDays(1))
    val previousRows = rowsInRange(byDate, today.minusDays(14), today.minusDays(8))
    val recentAverage = recentRows.takeIf { it.size >= MIN_DAYS_FOR_TREND }
        ?.map(DailyUsage::trackedSeconds)
        ?.averageAsLong()
    val previousAverage = previousRows.takeIf { it.size >= MIN_DAYS_FOR_TREND }
        ?.map(DailyUsage::trackedSeconds)
        ?.averageAsLong()
    val changePercent = if (recentAverage != null && previousAverage != null && previousAverage > 0) {
        (((recentAverage - previousAverage).toDouble() / previousAverage) * 100.0).roundToInt()
    } else {
        null
    }
    val trend = when {
        recentAverage == null || previousAverage == null -> UsageTrendDirection.NOT_ENOUGH_DATA
        previousAverage == 0L && recentAverage == 0L -> UsageTrendDirection.STABLE
        previousAverage == 0L -> UsageTrendDirection.INCREASING
        changePercent == null -> UsageTrendDirection.NOT_ENOUGH_DATA
        changePercent <= -MEANINGFUL_CHANGE_PERCENT -> UsageTrendDirection.IMPROVING
        changePercent >= MEANINGFUL_CHANGE_PERCENT -> UsageTrendDirection.INCREASING
        else -> UsageTrendDirection.STABLE
    }

    val days = (0L until DISPLAY_DAYS).map { offset ->
        val date = today.minusDays(offset)
        val usage = byDate[date]
        UsageDayUi(
            localDate = date,
            label = when (offset) {
                0L -> "Сегодня"
                1L -> "Вчера"
                else -> date.format(DAY_FORMATTER)
            },
            observed = usage != null,
            totalSeconds = usage?.trackedSeconds() ?: 0,
            youtubeSeconds = usage?.youtubeSeconds.safeSeconds(),
            shortsSeconds = usage?.shortsSeconds.safeSeconds(),
            instagramSeconds = usage?.instagramSeconds.safeSeconds(),
            pinterestSeconds = usage?.pinterestSeconds.safeSeconds(),
            chromeSeconds = usage?.chromeSeconds.safeSeconds(),
        )
    }

    return UsageStatisticsUiState(
        todayTotalSeconds = current.trackedSeconds(),
        todayYoutubeSeconds = current.youtubeSeconds.safeSeconds(),
        todayShortsSeconds = current.shortsSeconds.safeSeconds(),
        todayInstagramSeconds = current.instagramSeconds.safeSeconds(),
        todayPinterestSeconds = current.pinterestSeconds.safeSeconds(),
        todayChromeSeconds = current.chromeSeconds.safeSeconds(),
        recentAverageSeconds = recentAverage,
        previousAverageSeconds = previousAverage,
        changePercent = changePercent,
        trend = trend,
        days = days,
    )
}

private fun rowsInRange(
    byDate: Map<LocalDate, DailyUsage>,
    start: LocalDate,
    endInclusive: LocalDate,
): List<DailyUsage> = byDate.values.filter { usage ->
    !usage.localDate.isBefore(start) && !usage.localDate.isAfter(endInclusive)
}

private fun DailyUsage.trackedSeconds(): Long = listOf(
    youtubeSeconds,
    instagramSeconds,
    pinterestSeconds,
    chromeSeconds,
).fold(0L) { total, value ->
    val safe = value.coerceAtLeast(0)
    if (safe > Long.MAX_VALUE - total) Long.MAX_VALUE else total + safe
}

private fun List<Long>.averageAsLong(): Long =
    if (isEmpty()) 0 else (sumOf { it.toDouble() } / size).toLong().coerceAtLeast(0)

private fun Long?.safeSeconds(): Long = this?.coerceAtLeast(0) ?: 0

internal fun absoluteChangePercent(changePercent: Int?): Int? = changePercent?.let(::abs)

private val DAY_FORMATTER = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))
private const val DISPLAY_DAYS = 7L
private const val MIN_DAYS_FOR_TREND = 2
private const val MEANINGFUL_CHANGE_PERCENT = 3
