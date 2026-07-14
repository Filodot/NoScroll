package com.filodot.noscroll.monitoring.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.filodot.noscroll.core.model.AccessibilityWindowEvent
import com.filodot.noscroll.core.model.KnownTextHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class AndroidWindowSnapshotMapperTest {
    @Test
    fun `mapper copies structural signals and only allowlisted text hints`() {
        val rawSecret = "private video title 7f64"
        val root = node(
            packageName = AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME,
            className = "androidx.viewpager2.widget.ViewPager2",
            viewId = "com.google.android.youtube:id/reel_watch_fragment_root",
            text = rawSecret,
            contentDescription = " Share ",
        ).apply {
            isScrollable = true
            addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            setBoundsInScreen(Rect(0, 0, 1_080, 1_920))
        }

        val snapshot = AndroidWindowSnapshotMapper().mapAndRelease(event(), root)

        requireNotNull(snapshot)
        assertEquals(1_080, snapshot.windowWidthPx)
        assertEquals(1_920, snapshot.windowHeightPx)
        assertEquals("androidx.viewpager2.widget.ViewPager2", snapshot.rootClassName)
        assertEquals(1, snapshot.nodes.size)
        val signal = snapshot.nodes.single()
        assertEquals("com.google.android.youtube:id/reel_watch_fragment_root", signal.viewIdResourceName)
        assertEquals("pager", signal.roleName)
        assertTrue(signal.isScrollable)
        assertTrue(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD in signal.actionIds)
        assertEquals(setOf(KnownTextHint.SHARE_ACTION), signal.knownTextHints)
        assertFalse(snapshot.toString().contains(rawSecret))
    }

    @Test
    fun `arbitrary text containing allowlisted word is not promoted to a hint`() {
        val hints = AllowlistedTextHintClassifier.classify(
            text = "my private Shorts diary",
            contentDescription = "share this secret later",
        )

        assertTrue(hints.isEmpty())
        assertEquals(
            setOf(KnownTextHint.SHORTS_LABEL, KnownTextHint.COMMENTS_ACTION),
            AllowlistedTextHintClassifier.classify("Shorts", "Комментарии"),
        )
    }

    @Test
    fun `foreign root package fails open and releases the node`() {
        var releasedNodes = 0
        val root = node(
            packageName = "com.example.overlay",
            className = "android.widget.FrameLayout",
            text = "Shorts",
        )

        val mapper = AndroidWindowSnapshotMapper(
            nodeReleaser = { releasedNodes += 1 },
        )

        assertNull(mapper.mapAndRelease(event(), root))
        assertEquals(1, releasedNodes)
    }

    @Test
    fun `unsafe metadata is discarded instead of copied`() {
        val root = node(
            packageName = AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME,
            className = "bad\u0000class",
            viewId = "x".repeat(300),
        )

        val snapshot = AndroidWindowSnapshotMapper().mapAndRelease(event(), root)

        requireNotNull(snapshot)
        assertNull(snapshot.rootClassName)
        assertNull(snapshot.nodes.single().className)
        assertNull(snapshot.nodes.single().viewIdResourceName)
    }

    @Test
    fun `mapper bounds prevent unbounded tree retention`() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidWindowSnapshotMapper(maximumNodes = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AndroidWindowSnapshotMapper(maximumDepth = 65)
        }
    }

    @Test
    fun `snapshot provider returns null when root is unavailable or access was revoked`() = runTest {
        val unavailable = AndroidWindowSnapshotProvider(
            rootProvider = { null },
            mainDispatcher = Dispatchers.Unconfined,
        )
        val revoked = AndroidWindowSnapshotProvider(
            rootProvider = { throw SecurityException("revoked") },
            mainDispatcher = Dispatchers.Unconfined,
        )

        assertNull(unavailable.capture(event()))
        assertNull(revoked.capture(event()))
        assertNull(
            unavailable.capture(
                event().copy(packageName = "com.example.foreign"),
            ),
        )
    }

    @Suppress("DEPRECATION")
    private fun node(
        packageName: String,
        className: String? = null,
        viewId: String? = null,
        text: String? = null,
        contentDescription: String? = null,
    ): AccessibilityNodeInfo = AccessibilityNodeInfo.obtain().apply {
        this.packageName = packageName
        this.className = className
        this.viewIdResourceName = viewId
        this.text = text
        this.contentDescription = contentDescription
    }

    private fun event() = AccessibilityWindowEvent(
        packageName = AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME,
        eventType = AccessibilityAdapterController.TYPE_WINDOW_CONTENT_CHANGED,
        elapsedRealtimeMillis = 123,
    )
}
