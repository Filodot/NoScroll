package com.filodot.noscroll.monitoring.detector

data class ShortsDetectionRules(
    val rulesVersion: Int,
    val youtubePackageName: String,
    val strongShortsViewIds: Set<String>,
    val strongNonShortsViewIds: Set<String>,
    val positiveThreshold: Float,
    val minimumIndependentSignals: Int,
    val positiveDebounceMillis: Long,
    val positiveObservationCount: Int,
    val negativeObservationCount: Int,
) {
    init {
        require(rulesVersion > 0)
        require(youtubePackageName.isNotBlank())
        require(positiveThreshold in 0f..1f)
        require(minimumIndependentSignals >= 2)
        require(positiveDebounceMillis > 0)
        require(positiveObservationCount >= 2)
        require(negativeObservationCount >= 2)
        require(strongShortsViewIds.none(String::isBlank))
        require(strongNonShortsViewIds.none(String::isBlank))
        val normalizedPositiveIds = strongShortsViewIds.map { it.lowercase() }.toSet()
        val normalizedNegativeIds = strongNonShortsViewIds.map { it.lowercase() }.toSet()
        require(normalizedPositiveIds.intersect(normalizedNegativeIds).isEmpty())
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"

        val DEFAULT = ShortsDetectionRules(
            rulesVersion = CURRENT_VERSION,
            youtubePackageName = YOUTUBE_PACKAGE,
            strongShortsViewIds = setOf(
                "reel_watch_fragment_root",
                "reel_player_page_container",
                "shorts_watch_fragment_root",
            ),
            strongNonShortsViewIds = setOf(
                "home_results",
                "search_results",
                "subscriptions_results",
                "watch_fragment_root",
                "watch_player",
                "settings_list",
                "account_header",
                "library_results",
                "create_fragment_root",
                "upload_fragment_root",
                "live_player",
                "live_chat_fragment_root",
            ),
            positiveThreshold = 0.65f,
            minimumIndependentSignals = 2,
            positiveDebounceMillis = 300,
            positiveObservationCount = 2,
            negativeObservationCount = 2,
        )
    }
}

object ShortsSignalCode {
    const val PACKAGE_MISMATCH = "PACKAGE_MISMATCH"
    const val STRONG_SHORTS_VIEW_ID = "STRONG_SHORTS_VIEW_ID"
    const val STRONG_NON_SHORTS_VIEW_ID = "STRONG_NON_SHORTS_VIEW_ID"
    const val VERTICAL_PAGER = "VERTICAL_PAGER"
    const val ACTION_STACK = "ACTION_STACK"
    const val SHORTS_LABEL = "SHORTS_LABEL"
    const val PORTRAIT_WINDOW = "PORTRAIT_WINDOW"
    const val VERTICAL_SCROLL_EVENT = "VERTICAL_SCROLL_EVENT"
    const val UNRECOGNIZED_LAYOUT = "UNRECOGNIZED_LAYOUT"
    const val CONFLICTING_LAYOUT = "CONFLICTING_LAYOUT"
    const val POSITIVE_DEBOUNCE_PENDING = "POSITIVE_DEBOUNCE_PENDING"
    const val NEGATIVE_EXIT_PENDING = "NEGATIVE_EXIT_PENDING"
    const val INVALID_TIMESTAMP = "INVALID_TIMESTAMP"
    const val TIMESTAMP_ROLLBACK = "TIMESTAMP_ROLLBACK"
}
