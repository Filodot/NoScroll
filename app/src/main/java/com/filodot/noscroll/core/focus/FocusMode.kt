package com.filodot.noscroll.core.focus

import java.time.Instant

data class FocusAppDefinition(
    val packageName: String,
    val label: String,
)

/**
 * A deliberately small allow-list keeps focus mode predictable and avoids broad package access.
 * New applications can be added here without changing the persisted settings format.
 */
object FocusAppCatalog {
    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"
    const val TIKTOK = "com.zhiliaoapp.musically"
    const val TELEGRAM = "org.telegram.messenger"
    const val VK = "com.vkontakte.android"
    const val X = "com.twitter.android"
    const val PINTEREST = "com.pinterest"
    const val CHROME = "com.android.chrome"

    val apps = listOf(
        FocusAppDefinition(YOUTUBE, "YouTube"),
        FocusAppDefinition(INSTAGRAM, "Instagram"),
        FocusAppDefinition(TIKTOK, "TikTok"),
        FocusAppDefinition(TELEGRAM, "Telegram"),
        FocusAppDefinition(VK, "VK"),
        FocusAppDefinition(X, "X"),
        FocusAppDefinition(PINTEREST, "Pinterest"),
        FocusAppDefinition(CHROME, "Chrome"),
    )

    val supportedPackages: Set<String> = apps.mapTo(linkedSetOf()) { it.packageName }
    val defaultPackages: Set<String> = linkedSetOf(YOUTUBE, INSTAGRAM)

    fun sanitize(packageNames: Set<String>): Set<String> =
        packageNames.intersect(supportedPackages)
}

data class FocusSession(
    val startedAt: Instant?,
    val endsAt: Instant?,
    val blockedPackages: Set<String>,
) {
    fun isActiveAt(now: Instant): Boolean =
        endsAt?.isAfter(now) == true && blockedPackages.isNotEmpty()

    fun blocks(packageName: String?, now: Instant): Boolean =
        packageName != null && isActiveAt(now) && packageName in blockedPackages
}
