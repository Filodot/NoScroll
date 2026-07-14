package com.filodot.noscroll.monitoring.detector

import com.filodot.noscroll.core.model.ShortsDetectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeShortsDetectorTest {
    @Test
    fun knownPositiveSurfaceMatrixConfirms() {
        val strongSurfaces = mapOf(
            "Shorts tab" to "reel_watch_fragment_root",
            "Short opened from Home" to "reel_player_page_container",
            "Short opened by deep link" to "shorts_watch_fragment_root",
            "return from channel to active Short" to "reel_watch_fragment_root",
        )

        strongSurfaces.entries.forEachIndexed { index, (surface, viewId) ->
            val detector = YouTubeShortsDetector()
            val timestamp = 10L + index * 10
            detector.evaluate(SyntheticWindowFixtures.withStrongViewId(timestamp, viewId))
            val result = detector.evaluate(
                SyntheticWindowFixtures.withStrongViewId(timestamp + 1, viewId),
            )
            assertEquals(surface, ShortsDetectionState.SHORTS_CONFIRMED, result.state)
        }

        val transitionDetector = YouTubeShortsDetector()
        transitionDetector.evaluate(SyntheticWindowFixtures.structuralShorts(90))
        val transitioned = transitionDetector.evaluate(SyntheticWindowFixtures.structuralShorts(91))
        assertEquals(
            "next or previous Short",
            ShortsDetectionState.SHORTS_CONFIRMED,
            transitioned.state,
        )
    }

    @Test
    fun strongSignalRequiresTwoDistinctConsistentObservations() {
        val detector = YouTubeShortsDetector()

        val first = detector.evaluate(SyntheticWindowFixtures.strongShorts(100))
        val duplicate = detector.evaluate(SyntheticWindowFixtures.strongShorts(100))
        val second = detector.evaluate(SyntheticWindowFixtures.strongShorts(101))

        assertEquals(ShortsDetectionState.UNKNOWN, first.state)
        assertTrue(ShortsSignalCode.POSITIVE_DEBOUNCE_PENDING in first.matchedSignalCodes)
        assertEquals(ShortsDetectionState.UNKNOWN, duplicate.state)
        assertEquals(ShortsDetectionState.SHORTS_CONFIRMED, second.state)
        assertFalse(ShortsSignalCode.POSITIVE_DEBOUNCE_PENDING in second.matchedSignalCodes)
    }

    @Test
    fun mediumPagerAndActionStackConfirmTogether() {
        val detector = YouTubeShortsDetector()

        val first = detector.evaluate(SyntheticWindowFixtures.structuralShorts(1_000))
        val second = detector.evaluate(SyntheticWindowFixtures.structuralShorts(1_010))

        assertEquals(ShortsDetectionState.UNKNOWN, first.state)
        assertEquals(ShortsDetectionState.SHORTS_CONFIRMED, second.state)
        assertTrue(ShortsSignalCode.VERTICAL_PAGER in second.matchedSignalCodes)
        assertTrue(ShortsSignalCode.ACTION_STACK in second.matchedSignalCodes)
    }

    @Test
    fun stableDurationCanConfirmBeforeConfiguredObservationCount() {
        val rules = ShortsDetectionRules.DEFAULT.copy(positiveObservationCount = 3)
        val detector = YouTubeShortsDetector(rules)

        val first = detector.evaluate(SyntheticWindowFixtures.strongIdOnly(1_000))
        val stable = detector.evaluate(SyntheticWindowFixtures.strongIdOnly(1_300))

        assertEquals(ShortsDetectionState.UNKNOWN, first.state)
        assertEquals(ShortsDetectionState.SHORTS_CONFIRMED, stable.state)
    }

    @Test
    fun singleWeakTextHintNeverConfirmsEvenWhenRepeated() {
        val detector = YouTubeShortsDetector()

        repeat(4) { index ->
            val result = detector.evaluate(
                SyntheticWindowFixtures.weakShortsLabel(2_000L + index * 500),
            )
            assertEquals(ShortsDetectionState.UNKNOWN, result.state)
            assertTrue(ShortsSignalCode.SHORTS_LABEL in result.matchedSignalCodes)
        }
    }

    @Test
    fun singleMediumPagerDoesNotConfirm() {
        val detector = YouTubeShortsDetector()

        val result = detector.evaluate(SyntheticWindowFixtures.pagerOnly(3_000))

        assertEquals(ShortsDetectionState.UNKNOWN, result.state)
        assertTrue(ShortsSignalCode.VERTICAL_PAGER in result.matchedSignalCodes)
    }

    @Test
    fun emptyTreeIsUnknownAndFailOpen() {
        val result = YouTubeShortsDetector().evaluate(SyntheticWindowFixtures.empty(4_000))

        assertEquals(ShortsDetectionState.UNKNOWN, result.state)
        assertEquals(0f, result.confidence)
        assertTrue(result.matchedSignalCodes.isEmpty())
    }

    @Test
    fun otherPackageIsImmediatelyNotShortsEvenWithShortsText() {
        val result = YouTubeShortsDetector().evaluate(
            SyntheticWindowFixtures.otherPackageWithShortsLabel(5_000),
        )

        assertEquals(ShortsDetectionState.NOT_SHORTS, result.state)
        assertEquals(setOf(ShortsSignalCode.PACKAGE_MISMATCH), result.matchedSignalCodes)
    }

    @Test
    fun foreignResourceNamespaceCannotTriggerStrongSignal() {
        val detector = YouTubeShortsDetector()

        detector.evaluate(SyntheticWindowFixtures.youtubeWithForeignStrongResource(5_100))
        val result = detector.evaluate(
            SyntheticWindowFixtures.youtubeWithForeignStrongResource(5_101),
        )

        assertEquals(ShortsDetectionState.UNKNOWN, result.state)
        assertFalse(ShortsSignalCode.STRONG_SHORTS_VIEW_ID in result.matchedSignalCodes)
    }

    @Test
    fun knownNegativeSurfaceMatrixIsNotShorts() {
        val negativeSurfaces = mapOf(
            "Home feed" to "home_results",
            "Search results" to "search_results",
            "Subscriptions" to "subscriptions_results",
            "ordinary portrait video and comments" to "watch_fragment_root",
            "ordinary fullscreen video" to "watch_player",
            "Settings" to "settings_list",
            "Account" to "account_header",
            "Library or You" to "library_results",
            "Create UI" to "create_fragment_root",
            "Upload UI" to "upload_fragment_root",
            "vertical live video" to "live_player",
            "live chat" to "live_chat_fragment_root",
        )

        negativeSurfaces.entries.forEachIndexed { index, (surface, viewId) ->
            val result = YouTubeShortsDetector().evaluate(
                SyntheticWindowFixtures.knownNonShorts(6_000L + index, viewId),
            )
            assertEquals(surface, ShortsDetectionState.NOT_SHORTS, result.state)
            assertTrue(
                surface,
                ShortsSignalCode.STRONG_NON_SHORTS_VIEW_ID in result.matchedSignalCodes,
            )
        }
    }

    @Test
    fun informativeTreeWithoutKnownSignalsIsUnknownFailOpen() {
        val result = YouTubeShortsDetector().evaluate(
            SyntheticWindowFixtures.informativeUnknownTree(7_000),
        )

        assertEquals(ShortsDetectionState.UNKNOWN, result.state)
        assertTrue(ShortsSignalCode.UNRECOGNIZED_LAYOUT in result.matchedSignalCodes)
    }

    @Test
    fun detectorCanEnterShortsAfterKnownNegativeSurface() {
        val detector = YouTubeShortsDetector()
        val negative = detector.evaluate(
            SyntheticWindowFixtures.knownNonShorts(7_100, "home_results"),
        )

        val pending = detector.evaluate(SyntheticWindowFixtures.strongShorts(7_110))
        val confirmed = detector.evaluate(SyntheticWindowFixtures.strongShorts(7_111))

        assertEquals(ShortsDetectionState.NOT_SHORTS, negative.state)
        assertEquals(ShortsDetectionState.UNKNOWN, pending.state)
        assertEquals(ShortsDetectionState.SHORTS_CONFIRMED, confirmed.state)
    }

    @Test
    fun firstNegativeObservationDoesNotEndConfirmedSession() {
        val detector = confirmedDetector()

        val firstNegative = detector.evaluate(
            SyntheticWindowFixtures.knownNonShorts(8_010, "watch_player"),
        )
        val secondNegative = detector.evaluate(
            SyntheticWindowFixtures.knownNonShorts(8_020, "watch_player"),
        )

        assertEquals(ShortsDetectionState.SHORTS_CONFIRMED, firstNegative.state)
        assertTrue(ShortsSignalCode.NEGATIVE_EXIT_PENDING in firstNegative.matchedSignalCodes)
        assertEquals(ShortsDetectionState.NOT_SHORTS, secondNegative.state)
    }

    @Test
    fun duplicateNegativeTimestampDoesNotCountAsSecondExitObservation() {
        val detector = confirmedDetector()

        val firstNegative = detector.evaluate(
            SyntheticWindowFixtures.knownNonShorts(8_010, "watch_player"),
        )
        val duplicate = detector.evaluate(
            SyntheticWindowFixtures.knownNonShorts(8_010, "watch_player"),
        )
        val distinct = detector.evaluate(
            SyntheticWindowFixtures.knownNonShorts(8_011, "watch_player"),
        )

        assertEquals(ShortsDetectionState.SHORTS_CONFIRMED, firstNegative.state)
        assertEquals(ShortsDetectionState.SHORTS_CONFIRMED, duplicate.state)
        assertEquals(ShortsDetectionState.NOT_SHORTS, distinct.state)
    }

    @Test
    fun packageChangeEndsConfirmedSessionImmediately() {
        val detector = confirmedDetector()

        val result = detector.evaluate(SyntheticWindowFixtures.otherPackageWithShortsLabel(8_010))

        assertEquals(ShortsDetectionState.NOT_SHORTS, result.state)
        assertTrue(ShortsSignalCode.PACKAGE_MISMATCH in result.matchedSignalCodes)
    }

    @Test
    fun unknownLayoutDropsConfirmationImmediatelyForFailOpen() {
        val detector = confirmedDetector()

        val result = detector.evaluate(SyntheticWindowFixtures.empty(8_010))

        assertEquals(ShortsDetectionState.UNKNOWN, result.state)
    }

    @Test
    fun commentsSheetKeepsConfirmedShortsWhenUnderlyingStrongSignalRemains() {
        val detector = confirmedDetector()

        val result = detector.evaluate(SyntheticWindowFixtures.commentsSheetOverShorts(8_010))

        assertEquals(ShortsDetectionState.SHORTS_CONFIRMED, result.state)
        assertTrue(ShortsSignalCode.STRONG_SHORTS_VIEW_ID in result.matchedSignalCodes)
    }

    @Test
    fun conflictingPositiveAndNegativeIdsAreUnknown() {
        val result = YouTubeShortsDetector().evaluate(
            SyntheticWindowFixtures.conflictingLayout(9_000),
        )

        assertEquals(ShortsDetectionState.UNKNOWN, result.state)
        assertTrue(ShortsSignalCode.CONFLICTING_LAYOUT in result.matchedSignalCodes)
    }

    @Test
    fun inconsistentPositiveSignaturesRestartDebounce() {
        val detector = YouTubeShortsDetector()

        detector.evaluate(SyntheticWindowFixtures.strongIdOnly(10_000))
        val changed = detector.evaluate(SyntheticWindowFixtures.structuralShorts(10_010))
        val stable = detector.evaluate(SyntheticWindowFixtures.structuralShorts(10_020))

        assertEquals(ShortsDetectionState.UNKNOWN, changed.state)
        assertEquals(ShortsDetectionState.SHORTS_CONFIRMED, stable.state)
    }

    @Test
    fun monotonicTimestampRollbackResetsToUnknown() {
        val detector = YouTubeShortsDetector()
        detector.evaluate(SyntheticWindowFixtures.strongShorts(11_000))

        val result = detector.evaluate(SyntheticWindowFixtures.strongShorts(10_999))

        assertEquals(ShortsDetectionState.UNKNOWN, result.state)
        assertEquals(setOf(ShortsSignalCode.TIMESTAMP_ROLLBACK), result.matchedSignalCodes)
    }

    @Test
    fun negativeMonotonicTimestampIsRejectedFailOpen() {
        val result = YouTubeShortsDetector().evaluate(
            SyntheticWindowFixtures.strongShorts(-1),
        )

        assertEquals(ShortsDetectionState.UNKNOWN, result.state)
        assertEquals(setOf(ShortsSignalCode.INVALID_TIMESTAMP), result.matchedSignalCodes)
    }

    @Test
    fun configuredViewIdsAreMatchedCaseInsensitively() {
        val rules = ShortsDetectionRules.DEFAULT.copy(
            strongShortsViewIds = setOf("CUSTOM_SHORTS_ROOT"),
        )
        val detector = YouTubeShortsDetector(rules)

        detector.evaluate(SyntheticWindowFixtures.withStrongViewId(11_100, "custom_shorts_root"))
        val result = detector.evaluate(
            SyntheticWindowFixtures.withStrongViewId(11_101, "CUSTOM_SHORTS_ROOT"),
        )

        assertEquals(ShortsDetectionState.SHORTS_CONFIRMED, result.state)
    }

    @Test
    fun resultContainsOnlyBoundedConfidenceStableCodesAndCurrentRulesVersion() {
        val result = YouTubeShortsDetector().evaluate(
            SyntheticWindowFixtures.structuralShorts(12_000),
        )

        assertTrue(result.confidence in 0f..1f)
        assertEquals(ShortsDetectionRules.CURRENT_VERSION, result.rulesVersion)
        assertTrue(result.matchedSignalCodes.all { code -> code == code.uppercase() })
        assertFalse(result.matchedSignalCodes.any { ShortsDetectionRules.YOUTUBE_PACKAGE in it })
    }

    @Test
    fun invalidRulesAreRejected() {
        val invalidConfigurations = listOf(
            { ShortsDetectionRules.DEFAULT.copy(minimumIndependentSignals = 1) },
            { ShortsDetectionRules.DEFAULT.copy(positiveDebounceMillis = 0) },
            {
                ShortsDetectionRules.DEFAULT.copy(
                    strongShortsViewIds = setOf("same_id"),
                    strongNonShortsViewIds = setOf("SAME_ID"),
                )
            },
        )

        invalidConfigurations.forEach { createRules ->
            assertTrue(runCatching(createRules).exceptionOrNull() is IllegalArgumentException)
        }
    }

    private fun confirmedDetector(): YouTubeShortsDetector = YouTubeShortsDetector().also { detector ->
        detector.evaluate(SyntheticWindowFixtures.strongShorts(8_000))
        val confirmed = detector.evaluate(SyntheticWindowFixtures.strongShorts(8_001))
        assertEquals(ShortsDetectionState.SHORTS_CONFIRMED, confirmed.state)
    }
}
