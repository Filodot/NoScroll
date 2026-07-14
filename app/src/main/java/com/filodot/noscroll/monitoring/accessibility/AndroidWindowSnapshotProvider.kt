package com.filodot.noscroll.monitoring.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.filodot.noscroll.core.contracts.WindowSnapshotProvider
import com.filodot.noscroll.core.model.AccessibilityWindowEvent
import com.filodot.noscroll.core.model.WindowSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidWindowSnapshotProvider(
    private val rootProvider: () -> AccessibilityNodeInfo?,
    private val mapper: AndroidWindowSnapshotMapper = AndroidWindowSnapshotMapper(),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : WindowSnapshotProvider {
    override suspend fun capture(event: AccessibilityWindowEvent): WindowSnapshot? {
        if (event.packageName != AccessibilityAdapterController.YOUTUBE_PACKAGE_NAME) return null
        return withContext(mainDispatcher) {
            val root = try {
                rootProvider()
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalStateException) {
                null
            }
            root?.let { mapper.mapAndRelease(event, it) }
        }
    }
}
