package com.filodot.noscroll.monitoring.accessibility

import android.graphics.PixelFormat
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityOverlayWindowPolicyTest {
    @Test
    fun `blocking window is a full screen interactive accessibility overlay`() {
        val params = AccessibilityOverlayWindowPolicy.createLayoutParams()

        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, params.type)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.height)
        assertEquals(PixelFormat.OPAQUE, params.format)
        assertFalse(params.flags.hasFlag(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
        assertFalse(params.flags.hasFlag(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
        assertFalse(params.flags.hasFlag(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN))
    }
}

private fun Int.hasFlag(flag: Int): Boolean = this and flag == flag
