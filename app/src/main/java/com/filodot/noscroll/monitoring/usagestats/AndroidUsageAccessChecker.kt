package com.filodot.noscroll.monitoring.usagestats

import android.app.AppOpsManager
import android.content.Context
import android.os.Process

class AndroidUsageAccessChecker internal constructor(
    private val modeReader: () -> Int,
) : UsageAccessChecker {
    constructor(context: Context) : this(
        modeReader = {
            val appOpsManager = context.getSystemService(AppOpsManager::class.java)
                ?: throw UsageStatsServiceUnavailableException()
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        },
    )

    override fun checkAccess(): UsageAccessState = try {
        if (modeReader() == AppOpsManager.MODE_ALLOWED) {
            UsageAccessState.GRANTED
        } else {
            UsageAccessState.DENIED
        }
    } catch (_: SecurityException) {
        UsageAccessState.DENIED
    } catch (_: RuntimeException) {
        UsageAccessState.SERVICE_UNAVAILABLE
    }
}

internal class UsageStatsServiceUnavailableException : IllegalStateException()
