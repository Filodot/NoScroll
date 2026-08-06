package com.filodot.noscroll.core.focus

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusModeTest {
    private val now = Instant.parse("2026-08-06T10:00:00Z")

    @Test
    fun `active session blocks only selected supported packages before deadline`() {
        val session = FocusSession(
            startedAt = now,
            endsAt = now.plusSeconds(30 * 60),
            blockedPackages = setOf(FocusAppCatalog.YOUTUBE, FocusAppCatalog.TELEGRAM),
        )

        assertTrue(session.blocks(FocusAppCatalog.YOUTUBE, now.plusSeconds(1)))
        assertTrue(session.blocks(FocusAppCatalog.TELEGRAM, now.plusSeconds(1)))
        assertFalse(session.blocks(FocusAppCatalog.INSTAGRAM, now.plusSeconds(1)))
        assertFalse(session.blocks(FocusAppCatalog.YOUTUBE, now.plusSeconds(30 * 60)))
    }

    @Test
    fun `catalog removes unsupported packages without broad package access`() {
        assertEquals(
            setOf(FocusAppCatalog.INSTAGRAM),
            FocusAppCatalog.sanitize(setOf(FocusAppCatalog.INSTAGRAM, "unknown.package")),
        )
    }

    @Test
    fun `focus catalog exposes Pinterest and Chrome`() {
        assertTrue(FocusAppCatalog.PINTEREST in FocusAppCatalog.supportedPackages)
        assertTrue(FocusAppCatalog.CHROME in FocusAppCatalog.supportedPackages)
        assertEquals(
            setOf("Pinterest", "Chrome"),
            FocusAppCatalog.apps
                .filter {
                    it.packageName in setOf(FocusAppCatalog.PINTEREST, FocusAppCatalog.CHROME)
                }
                .mapTo(linkedSetOf()) { it.label },
        )
    }
}
