package com.filodot.noscroll.monitoring.usagestats

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager

internal class AndroidUsageEventsReader(
    private val usageStatsManager: UsageStatsManager,
) : PlatformUsageEventsReader {
    @SuppressLint("MissingPermission")
    override fun readEvents(startMillis: Long, endMillis: Long): List<RawUsageEvent> {
        // AndroidUsageStatsSource checks AppOps immediately before this call and maps a revoke race.
        // The final manifest permission is intentionally owned by the IP-04 integration package.
        val usageEvents = usageStatsManager.queryEvents(startMillis, endMillis) ?: return emptyList()
        val reusableEvent = UsageEvents.Event()
        return buildList {
            while (usageEvents.hasNextEvent()) {
                if (!usageEvents.getNextEvent(reusableEvent)) break
                add(
                    RawUsageEvent(
                        packageName = reusableEvent.packageName,
                        eventType = reusableEvent.eventType,
                        timestampMillis = reusableEvent.timeStamp,
                    ),
                )
            }
        }
    }
}
