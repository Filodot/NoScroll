package com.filodot.noscroll.monitoring.detector

import com.filodot.noscroll.core.model.KnownTextHint
import com.filodot.noscroll.core.model.WindowNodeSignal
import com.filodot.noscroll.core.model.WindowSnapshot

object SyntheticWindowFixtures {
    fun strongShorts(timestamp: Long): WindowSnapshot = snapshot(
        timestamp = timestamp,
        nodes = listOf(
            node(viewId = "reel_watch_fragment_root"),
            pager(),
            action(KnownTextHint.COMMENTS_ACTION),
            action(KnownTextHint.SHARE_ACTION),
        ),
    )

    fun strongIdOnly(timestamp: Long): WindowSnapshot = snapshot(
        timestamp = timestamp,
        nodes = listOf(node(viewId = "reel_watch_fragment_root")),
    )

    fun withStrongViewId(timestamp: Long, viewId: String): WindowSnapshot = snapshot(
        timestamp = timestamp,
        nodes = listOf(node(viewId = viewId)),
    )

    fun structuralShorts(timestamp: Long): WindowSnapshot = snapshot(
        timestamp = timestamp,
        eventType = TYPE_VIEW_SCROLLED,
        nodes = listOf(
            pager(),
            action(KnownTextHint.COMMENTS_ACTION),
            action(KnownTextHint.SHARE_ACTION),
        ),
    )

    fun commentsSheetOverShorts(timestamp: Long): WindowSnapshot = snapshot(
        timestamp = timestamp,
        nodes = listOf(
            node(viewId = "reel_watch_fragment_root"),
            node(className = "android.widget.FrameLayout", roleName = "bottom_sheet"),
            action(KnownTextHint.COMMENTS_ACTION),
        ),
    )

    fun knownNonShorts(timestamp: Long, viewId: String): WindowSnapshot = snapshot(
        timestamp = timestamp,
        nodes = listOf(
            node(viewId = viewId),
            node(className = "android.widget.FrameLayout"),
            node(className = "android.widget.TextView"),
        ),
    )

    fun informativeUnknownTree(timestamp: Long): WindowSnapshot = snapshot(
        timestamp = timestamp,
        nodes = listOf(
            node(className = "android.widget.FrameLayout"),
            node(className = "android.widget.TextView"),
            node(className = "android.widget.ImageView"),
        ),
    )

    fun weakShortsLabel(timestamp: Long): WindowSnapshot = snapshot(
        timestamp = timestamp,
        nodes = listOf(action(KnownTextHint.SHORTS_LABEL)),
    )

    fun pagerOnly(timestamp: Long): WindowSnapshot = snapshot(
        timestamp = timestamp,
        nodes = listOf(pager()),
    )

    fun regularVideoWithBottomNavigation(timestamp: Long): WindowSnapshot = snapshot(
        timestamp = timestamp,
        eventType = TYPE_VIEW_SCROLLED,
        nodes = listOf(
            scrollingList(),
            action(KnownTextHint.COMMENTS_ACTION),
            action(KnownTextHint.SHARE_ACTION),
            action(KnownTextHint.SHORTS_LABEL),
        ),
    )

    fun conflictingLayout(timestamp: Long): WindowSnapshot = snapshot(
        timestamp = timestamp,
        nodes = listOf(
            node(viewId = "reel_watch_fragment_root"),
            node(viewId = "watch_player"),
        ),
    )

    fun empty(timestamp: Long): WindowSnapshot = snapshot(timestamp = timestamp)

    fun otherPackageWithShortsLabel(timestamp: Long): WindowSnapshot = snapshot(
        timestamp = timestamp,
        packageName = "com.example.notes",
        nodes = listOf(action(KnownTextHint.SHORTS_LABEL)),
    )

    fun youtubeWithForeignStrongResource(timestamp: Long): WindowSnapshot = snapshot(
        timestamp = timestamp,
        nodes = listOf(
            WindowNodeSignal(
                viewIdResourceName = "com.example.overlay:id/reel_watch_fragment_root",
            ),
        ),
    )

    private fun snapshot(
        timestamp: Long,
        packageName: String = ShortsDetectionRules.YOUTUBE_PACKAGE,
        eventType: Int = 0,
        nodes: List<WindowNodeSignal> = emptyList(),
    ): WindowSnapshot = WindowSnapshot(
        packageName = packageName,
        eventType = eventType,
        capturedAtElapsedMillis = timestamp,
        rootClassName = "android.widget.FrameLayout",
        windowWidthPx = 1_080,
        windowHeightPx = 2_400,
        nodes = nodes,
    )

    private fun node(
        viewId: String? = null,
        className: String? = null,
        roleName: String? = null,
    ): WindowNodeSignal = WindowNodeSignal(
        viewIdResourceName = viewId?.let {
            "${ShortsDetectionRules.YOUTUBE_PACKAGE}:id/$it"
        },
        className = className,
        roleName = roleName,
    )

    private fun pager(): WindowNodeSignal = WindowNodeSignal(
        className = "androidx.viewpager2.widget.ViewPager2",
        roleName = "vertical_pager",
        isScrollable = true,
        actionIds = setOf(ACTION_SCROLL_FORWARD, ACTION_SCROLL_BACKWARD),
    )

    private fun scrollingList(): WindowNodeSignal = WindowNodeSignal(
        className = "androidx.recyclerview.widget.RecyclerView",
        roleName = "list",
        isScrollable = true,
        actionIds = setOf(ACTION_SCROLL_FORWARD, ACTION_SCROLL_BACKWARD),
    )

    private fun action(hint: KnownTextHint): WindowNodeSignal = WindowNodeSignal(
        className = "android.widget.ImageButton",
        knownTextHints = setOf(hint),
    )

    private const val TYPE_VIEW_SCROLLED = 4_096
    private const val ACTION_SCROLL_FORWARD = 4_096
    private const val ACTION_SCROLL_BACKWARD = 8_192
}
