package com.filodot.noscroll.monitoring.accessibility

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.pm.PackageManager
import com.filodot.noscroll.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityServiceConfigurationTest {
    @Test
    fun `manifest registers a permission-protected service with metadata`() {
        val context = RuntimeEnvironment.getApplication()
        val component = ComponentName(context, NoScrollAccessibilityService::class.java)
        @Suppress("DEPRECATION")
        val serviceInfo = context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)

        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, serviceInfo.permission)
        assertTrue(serviceInfo.exported)
        assertNotNull(serviceInfo.metaData)
        assertEquals(
            R.xml.noscroll_accessibility_service,
            serviceInfo.metaData.getInt(AccessibilityService.SERVICE_META_DATA),
        )
    }

    @Test
    fun `metadata requests only required capabilities`() {
        val parser = RuntimeEnvironment.getApplication().resources
            .getXml(R.xml.noscroll_accessibility_service)
        while (parser.eventType != XmlPullParser.START_TAG) parser.next()

        val eventTypes = parser.getAttributeIntValue(ANDROID_NAMESPACE, "accessibilityEventTypes", 0)
        assertEquals(
            AccessibilityAdapterController.SUPPORTED_EVENT_TYPES.fold(0) { combined, type ->
                combined or type
            },
            eventTypes,
        )
        assertEquals(null, parser.getAttributeValue(ANDROID_NAMESPACE, "packageNames"))
        assertEquals(
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS,
            parser.getAttributeIntValue(ANDROID_NAMESPACE, "accessibilityFlags", 0),
        )
        assertTrue(parser.getAttributeBooleanValue(ANDROID_NAMESPACE, "canRetrieveWindowContent", false))
        assertFalse(parser.getAttributeBooleanValue(ANDROID_NAMESPACE, "isAccessibilityTool", true))
        assertEquals(150, parser.getAttributeIntValue(ANDROID_NAMESPACE, "notificationTimeout", 0))
    }

    @Test
    fun `service registration is included in production manifest`() {
        val mainManifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val debugManifest = projectFile("app/src/debug/AndroidManifest.xml").readText()

        assertTrue(mainManifest.contains("NoScrollAccessibilityService"))
        assertTrue(mainManifest.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"))
        assertFalse(mainManifest.contains("BlockingActivity"))
        assertFalse(debugManifest.contains("NoScrollAccessibilityService"))
    }

    @Test
    fun `repeated interrupt reconnect and destroy do not crash`() {
        val first = Robolectric.buildService(NoScrollAccessibilityService::class.java).create().get()

        first.onServiceConnected()
        first.onInterrupt()
        first.onInterrupt()
        first.onServiceConnected()
        first.onDestroy()

        val reconnected = Robolectric.buildService(NoScrollAccessibilityService::class.java).create().get()
        reconnected.onServiceConnected()
        reconnected.onInterrupt()
        reconnected.onDestroy()
    }

    @Test
    fun `adapter sources contain no logging calls`() {
        val sourceDirectory = projectFile(
            "app/src/main/java/com/filodot/noscroll/monitoring/accessibility",
        )
        val source = sourceDirectory.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString(separator = "\n") { it.readText() }

        assertFalse(source.contains("android.util.Log"))
        assertFalse(source.contains("Log."))
        assertFalse(source.contains("println("))
    }

    @Test
    fun `production accessibility path contains no overlay window`() {
        val sourceDirectory = projectFile(
            "app/src/main/java/com/filodot/noscroll/monitoring/accessibility",
        )
        val source = sourceDirectory.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString(separator = "\n") { it.readText() }

        assertFalse(source.contains("TYPE_ACCESSIBILITY_OVERLAY"))
        assertFalse(source.contains("WindowManager.LayoutParams"))
        assertFalse(File(sourceDirectory, "AccessibilityBlockingOverlay.kt").exists())
        assertTrue(source.contains("ejectShortsAndOpenChallenge"))
    }

    private fun projectFile(relativePath: String): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val candidates = listOf(
            File(workingDirectory, relativePath),
            File(workingDirectory, relativePath.removePrefix("app/")),
            workingDirectory.parentFile?.let { File(it, relativePath) },
        ).filterNotNull()
        return requireNotNull(candidates.firstOrNull(File::exists)) {
            "Project file not found: $relativePath"
        }
    }

    companion object {
        private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
