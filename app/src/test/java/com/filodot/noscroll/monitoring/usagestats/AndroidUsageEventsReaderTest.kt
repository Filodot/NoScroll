package com.filodot.noscroll.monitoring.usagestats

import android.app.usage.UsageStatsManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidUsageEventsReaderTest {
    @Test
    @Suppress("DEPRECATION")
    fun `reader copies only safe fields from platform cursor`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val shadow = shadowOf(manager)
        shadow.addEvent("com.example.before", 999, 1)
        shadow.addEvent(AndroidUsageStatsSource.YOUTUBE_PACKAGE_NAME, 1_100, 1)
        shadow.addEvent("com.example.other", 1_200, 2)
        shadow.addEvent("com.example.after", 2_001, 1)

        val events = AndroidUsageEventsReader(manager).readEvents(1_000, 2_000)

        assertEquals(
            listOf(
                RawUsageEvent(AndroidUsageStatsSource.YOUTUBE_PACKAGE_NAME, 1, 1_100),
                RawUsageEvent("com.example.other", 2, 1_200),
            ),
            events,
        )
    }
}
