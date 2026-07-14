package com.filodot.noscroll.monitoring.usagestats

import android.app.AppOpsManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidUsageAccessCheckerTest {
    @Test
    fun `allowed app-op is granted`() {
        val checker = AndroidUsageAccessChecker { AppOpsManager.MODE_ALLOWED }

        assertEquals(UsageAccessState.GRANTED, checker.checkAccess())
    }

    @Test
    fun `all non-allowed app-op modes are denied`() {
        val deniedModes = listOf(
            AppOpsManager.MODE_IGNORED,
            AppOpsManager.MODE_ERRORED,
            AppOpsManager.MODE_DEFAULT,
            AppOpsManager.MODE_FOREGROUND,
        )

        deniedModes.forEach { mode ->
            assertEquals(
                UsageAccessState.DENIED,
                AndroidUsageAccessChecker { mode }.checkAccess(),
            )
        }
    }

    @Test
    fun `security and service failures use stable access states`() {
        assertEquals(
            UsageAccessState.DENIED,
            AndroidUsageAccessChecker { throw SecurityException("private platform detail") }
                .checkAccess(),
        )
        assertEquals(
            UsageAccessState.SERVICE_UNAVAILABLE,
            AndroidUsageAccessChecker { throw IllegalStateException("private platform detail") }
                .checkAccess(),
        )
    }
}
