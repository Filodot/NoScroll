package com.filodot.noscroll.core.contracts

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun interface MonotonicClock {
    fun elapsedRealtimeMillis(): Long
}

fun interface WallClock {
    fun now(): Instant
}

fun WallClock.localDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    now().atZone(zoneId).toLocalDate()
