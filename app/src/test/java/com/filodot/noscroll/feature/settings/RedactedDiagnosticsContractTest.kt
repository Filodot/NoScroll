package com.filodot.noscroll.feature.settings

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactedDiagnosticsContractTest {
    @Test
    fun `diagnostics model exposes only approved aggregate fields`() {
        assertEquals(
            setOf(
                "detectorStatus",
                "lastRecognitionLabel",
                "lastResultCode",
                "unknownCount",
                "rulesVersion",
            ),
            RedactedDiagnosticsUiState::class.java.declaredFields
                .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun `diagnostics contract has no raw YouTube content field names`() {
        val forbiddenFragments = listOf(
            "text",
            "title",
            "contentdescription",
            "viewid",
            "nodetree",
            "videoname",
            "query",
        )
        val fields = RedactedDiagnosticsUiState::class.java.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name.lowercase() }

        assertTrue(fields.none { field -> forbiddenFragments.any(field::contains) })
    }
}
