package com.filodot.noscroll.monitoring.usagestats

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.os.Process
import com.filodot.noscroll.core.model.UsageEventType
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidUsageStatsWiringTest {
    @Test
    @Suppress("DEPRECATION")
    fun `public Android wiring follows app-op and reads platform cursor`() {
        val context = RuntimeEnvironment.getApplication()
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
        shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED,
        )
        shadowOf(usageStatsManager).addEvent(
            AndroidUsageStatsSource.YOUTUBE_PACKAGE_NAME,
            START.toEpochMilli() + 1_000,
            1,
        )
        val source = AndroidUsageStatsSource(context, Dispatchers.Unconfined)

        val events = runBlocking { source.eventsBetween(START, END) }

        assertEquals(1, events.size)
        assertEquals(AndroidUsageStatsSource.YOUTUBE_PACKAGE_NAME, events.single().packageName)
        assertEquals(UsageEventType.ACTIVITY_RESUMED, events.single().type)

        shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED,
        )
        assertEquals(
            UsageStatsQueryResult.Failure(UsageStatsFailureReason.PERMISSION_DENIED),
            runBlocking { source.queryEventsBetween(START, END) },
        )
    }

    companion object {
        private val START = Instant.parse("2026-07-14T00:00:00Z")
        private val END = START.plusSeconds(60)
    }
}
