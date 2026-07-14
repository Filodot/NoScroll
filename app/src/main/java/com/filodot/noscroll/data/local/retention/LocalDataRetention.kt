package com.filodot.noscroll.data.local.retention

import com.filodot.noscroll.data.local.room.RetentionDao
import com.filodot.noscroll.data.local.room.RetentionResult
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class LocalDataRetention(
    private val dao: RetentionDao,
) {
    suspend fun deleteExpired(
        now: Instant,
        zoneId: ZoneId,
    ): RetentionResult {
        val cutoffInstant = now.minus(RETENTION_DAYS, ChronoUnit.DAYS)
        val cutoffLocalDate = now.atZone(zoneId).toLocalDate().minusDays(RETENTION_DAYS)
        return dao.deleteOlderThan(
            cutoffLocalDate = cutoffLocalDate.toString(),
            cutoffEpochMillis = cutoffInstant.toEpochMilli(),
        )
    }

    companion object {
        const val RETENTION_DAYS = 90L
    }
}
