package com.filodot.noscroll.runtime

import android.Manifest
import android.content.pm.PackageManager
import com.filodot.noscroll.NoScrollApplication
import com.filodot.noscroll.data.local.datastore.DataStoreSettingsRepository
import com.filodot.noscroll.data.local.repository.RoomEmergencyRepository
import com.filodot.noscroll.data.local.repository.RoomTaskRepository
import com.filodot.noscroll.data.local.repository.RoomUsageRepository
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoScrollRuntimeWiringTest {
    @Test
    fun `production application graph uses persistent repositories`() {
        val application = RuntimeEnvironment.getApplication() as NoScrollApplication
        val graph = application.runtime.appGraph

        assertTrue(graph.settingsRepository is DataStoreSettingsRepository)
        assertTrue(graph.usageRepository is RoomUsageRepository)
        assertTrue(graph.taskRepository is RoomTaskRepository)
        assertTrue(graph.emergencyRepository is RoomEmergencyRepository)
        assertNotNull(graph.systemAccess)
        assertNotNull(graph.monitoring)
    }

    @Test
    fun `production manifest requests special usage access`() {
        val application = RuntimeEnvironment.getApplication() as NoScrollApplication
        @Suppress("DEPRECATION")
        val packageInfo = application.packageManager.getPackageInfo(
            application.packageName,
            PackageManager.GET_PERMISSIONS,
        )

        assertTrue(
            packageInfo.requestedPermissions.orEmpty()
                .contains(Manifest.permission.PACKAGE_USAGE_STATS),
        )
    }
}
