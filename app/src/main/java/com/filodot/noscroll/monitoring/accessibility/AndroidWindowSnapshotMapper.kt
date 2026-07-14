package com.filodot.noscroll.monitoring.accessibility

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.filodot.noscroll.core.model.AccessibilityWindowEvent
import com.filodot.noscroll.core.model.KnownTextHint
import com.filodot.noscroll.core.model.WindowNodeSignal
import com.filodot.noscroll.core.model.WindowSnapshot
import java.util.ArrayDeque
import java.util.Locale

internal object AllowlistedTextHintClassifier {
    private val tokens = listOf(
        Token(KnownTextHint.SHORTS_LABEL, "shorts"),
        Token(KnownTextHint.SHORTS_LABEL, "короткие видео"),
        Token(KnownTextHint.COMMENTS_ACTION, "comments"),
        Token(KnownTextHint.COMMENTS_ACTION, "comments button"),
        Token(KnownTextHint.COMMENTS_ACTION, "комментарии"),
        Token(KnownTextHint.SHARE_ACTION, "share"),
        Token(KnownTextHint.SHARE_ACTION, "share button"),
        Token(KnownTextHint.SHARE_ACTION, "поделиться"),
        Token(KnownTextHint.SUBSCRIBE_ACTION, "subscribe"),
        Token(KnownTextHint.SUBSCRIBE_ACTION, "subscribe button"),
        Token(KnownTextHint.SUBSCRIBE_ACTION, "подписаться"),
    )

    fun classify(text: CharSequence?, contentDescription: CharSequence?): Set<KnownTextHint> =
        buildSet {
            tokens.forEach { token ->
                if (matches(text, token.value) || matches(contentDescription, token.value)) {
                    add(token.hint)
                }
            }
        }

    /** Compares in place so arbitrary UI text is never copied into a String or retained. */
    private fun matches(value: CharSequence?, allowlistedToken: String): Boolean {
        if (value == null || value.length > MAX_INSPECTED_TEXT_LENGTH) return false
        var start = 0
        var end = value.length
        while (start < end && value[start].isWhitespace()) start += 1
        while (end > start && value[end - 1].isWhitespace()) end -= 1
        if (end - start != allowlistedToken.length) return false
        return allowlistedToken.indices.all { index ->
            value[start + index].lowercaseChar() == allowlistedToken[index]
        }
    }

    private data class Token(
        val hint: KnownTextHint,
        val value: String,
    )

    private const val MAX_INSPECTED_TEXT_LENGTH = 64
}

internal class AndroidWindowSnapshotMapper(
    private val maximumNodes: Int = DEFAULT_MAXIMUM_NODES,
    private val maximumDepth: Int = DEFAULT_MAXIMUM_DEPTH,
    private val nodeReleaser: (AccessibilityNodeInfo) -> Unit = ::releaseFrameworkNode,
) {
    init {
        require(maximumNodes in 1..MAXIMUM_ALLOWED_NODES)
        require(maximumDepth in 0..MAXIMUM_ALLOWED_DEPTH)
    }

    /**
     * Takes ownership of [root]. Every acquired node is released before this method returns.
     */
    fun mapAndRelease(
        event: AccessibilityWindowEvent,
        root: AccessibilityNodeInfo,
    ): WindowSnapshot? {
        val queue = ArrayDeque<NodeAtDepth>()
        queue.add(NodeAtDepth(root, depth = 0))
        val signals = ArrayList<WindowNodeSignal>(maximumNodes)
        var rootClassName: String? = null
        var rootBounds: Rect? = null
        var rootPackageName: String? = null

        try {
            while (queue.isNotEmpty() && signals.size < maximumNodes) {
                val current = queue.removeFirst()
                val node = current.node
                try {
                    if (current.depth == 0) {
                        rootPackageName = safeMetadata(node.packageName)
                        rootClassName = safeMetadata(node.className)
                        rootBounds = Rect().also(node::getBoundsInScreen)
                    }

                    signals += mapNode(node)
                    if (current.depth < maximumDepth) {
                        val remainingCapacity = maximumNodes - signals.size - queue.size
                        val childrenToAcquire = minOf(node.childCount, remainingCapacity.coerceAtLeast(0))
                        repeat(childrenToAcquire) { index ->
                            acquireChild(node, index)?.let { child ->
                                queue.addLast(NodeAtDepth(child, current.depth + 1))
                            }
                        }
                    }
                } catch (_: SecurityException) {
                    // A revoked service can invalidate a node between reads; fail open with partial data.
                } catch (_: IllegalStateException) {
                    // Stale/recycled framework nodes are treated as unavailable, never as Shorts proof.
                } finally {
                    nodeReleaser(node)
                }
            }
        } finally {
            while (queue.isNotEmpty()) {
                nodeReleaser(queue.removeFirst().node)
            }
        }

        if (rootPackageName != AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME) return null
        val bounds = rootBounds
        return WindowSnapshot(
            packageName = AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME,
            eventType = event.eventType,
            capturedAtElapsedMillis = event.elapsedRealtimeMillis.coerceAtLeast(0L),
            rootClassName = rootClassName,
            windowWidthPx = bounds?.width()?.takeIf { it > 0 },
            windowHeightPx = bounds?.height()?.takeIf { it > 0 },
            nodes = signals,
        )
    }

    private fun mapNode(node: AccessibilityNodeInfo): WindowNodeSignal {
        val className = safeMetadata(node.className)
        return WindowNodeSignal(
            viewIdResourceName = safeMetadata(node.viewIdResourceName),
            className = className,
            roleName = canonicalRole(className),
            knownTextHints = AllowlistedTextHintClassifier.classify(
                text = node.text,
                contentDescription = node.contentDescription,
            ),
            isScrollable = node.isScrollable,
            actionIds = node.actionList
                .asSequence()
                .map { action -> action.id }
                .take(MAXIMUM_ACTION_IDS)
                .toSet(),
        )
    }

    private fun acquireChild(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? =
        try {
            node.getChild(index)
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalStateException) {
            null
        }

    private fun safeMetadata(value: CharSequence?): String? {
        if (value == null || value.isEmpty() || value.length > MAXIMUM_METADATA_LENGTH) return null
        if (value.any(Char::isISOControl)) return null
        return value.toString()
    }

    private fun canonicalRole(className: String?): String? {
        val normalized = className?.lowercase(Locale.ROOT) ?: return null
        return when {
            "viewpager" in normalized -> "pager"
            "recyclerview" in normalized -> "list"
            "button" in normalized -> "button"
            "image" in normalized -> "image"
            else -> null
        }
    }

    private data class NodeAtDepth(
        val node: AccessibilityNodeInfo,
        val depth: Int,
    )

    companion object {
        const val DEFAULT_MAXIMUM_NODES = 256
        const val DEFAULT_MAXIMUM_DEPTH = 24
        private const val MAXIMUM_ALLOWED_NODES = 1_024
        private const val MAXIMUM_ALLOWED_DEPTH = 64
        private const val MAXIMUM_METADATA_LENGTH = 256
        private const val MAXIMUM_ACTION_IDS = 32
    }
}

@Suppress("DEPRECATION")
private fun releaseFrameworkNode(node: AccessibilityNodeInfo) {
    // recycle() is required through API 32; newer Android releases manage node pooling themselves.
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) node.recycle()
}
