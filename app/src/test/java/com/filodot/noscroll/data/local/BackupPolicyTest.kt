package com.filodot.noscroll.data.local

import android.content.pm.ApplicationInfo
import com.filodot.noscroll.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupPolicyTest {
    @Test
    fun backupIsDisabledAndBothRuleFilesExcludeTheAppPrivateRoot() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)

        assertEquals(1, countRootExclusions(context.resources.getXml(R.xml.backup_rules)))
        assertEquals(2, countRootExclusions(context.resources.getXml(R.xml.data_extraction_rules)))
    }

    private fun countRootExclusions(parser: XmlPullParser): Int {
        var exclusions = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (
                parser.eventType == XmlPullParser.START_TAG &&
                parser.name == "exclude" &&
                parser.getAttributeValue(null, "domain") == "root" &&
                parser.getAttributeValue(null, "path") == "."
            ) {
                exclusions += 1
            }
            parser.next()
        }
        return exclusions
    }
}
