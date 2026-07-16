package com.filodot.noscroll.platform

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.net.toUri
import com.filodot.noscroll.monitoring.accessibility.NoScrollAccessibilityService
import com.filodot.noscroll.monitoring.usagestats.AndroidUsageAccessChecker
import com.filodot.noscroll.monitoring.usagestats.UsageAccessState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SystemAccessSnapshot(
    val accessibilityGranted: Boolean,
    val usageAccessGranted: Boolean,
    val youtubeInstalled: Boolean,
    val youtubeVersionName: String?,
    val appVersionName: String,
)

class AndroidSystemAccess(private val context: Context) {
    private val mutableState = MutableStateFlow(readSnapshot())
    val state: StateFlow<SystemAccessSnapshot> = mutableState.asStateFlow()

    fun refresh(): SystemAccessSnapshot = readSnapshot().also { mutableState.value = it }

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun usageAccessSettingsIntent(): Intent = Intent(
        Settings.ACTION_USAGE_ACCESS_SETTINGS,
        "package:${context.packageName}".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun readSnapshot(): SystemAccessSnapshot {
        val youtubePackage = YOUTUBE_PACKAGE_NAME
        val youtubeInfo = packageInfo(youtubePackage)
        return SystemAccessSnapshot(
            accessibilityGranted = isAccessibilityEnabled(),
            usageAccessGranted = AndroidUsageAccessChecker(context).checkAccess() ==
                UsageAccessState.GRANTED,
            youtubeInstalled = youtubeInfo != null,
            youtubeVersionName = youtubeInfo?.versionName,
            appVersionName = packageInfo(context.packageName)?.versionName ?: "—",
        )
    }

    private fun isAccessibilityEnabled(): Boolean = runCatching {
        val manager = context.getSystemService(AccessibilityManager::class.java)
            ?: return@runCatching false
        manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val service = info.resolveInfo?.serviceInfo
                service?.packageName == context.packageName &&
                    service.name == NoScrollAccessibilityService::class.java.name
            }
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun packageInfo(packageName: String) = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
    }.getOrNull()

    companion object {
        const val YOUTUBE_PACKAGE_NAME = "com.google.android.youtube"
    }
}
