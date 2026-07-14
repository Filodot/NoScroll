package com.filodot.noscroll.monitoring.detector

import com.filodot.noscroll.core.contracts.ShortsDetector
import com.filodot.noscroll.core.model.DetectionResult
import com.filodot.noscroll.core.model.KnownTextHint
import com.filodot.noscroll.core.model.ShortsDetectionState
import com.filodot.noscroll.core.model.WindowNodeSignal
import com.filodot.noscroll.core.model.WindowSnapshot
import java.util.Locale

class YouTubeShortsDetector(
    private val rules: ShortsDetectionRules = ShortsDetectionRules.DEFAULT,
) : ShortsDetector {
    private val strongShortsViewIds = rules.strongShortsViewIds
        .mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
    private val strongNonShortsViewIds = rules.strongNonShortsViewIds
        .mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
    private var currentState = ShortsDetectionState.UNKNOWN
    private var lastTimestamp: Long? = null
    private var positiveSince: Long? = null
    private var positiveObservations = 0
    private var positiveSignature: Set<String> = emptySet()
    private var negativeObservations = 0

    @Synchronized
    override fun evaluate(snapshot: WindowSnapshot): DetectionResult {
        if (snapshot.capturedAtElapsedMillis < 0) {
            reset(ShortsDetectionState.UNKNOWN, 0)
            return result(
                state = ShortsDetectionState.UNKNOWN,
                confidence = 0f,
                codes = setOf(ShortsSignalCode.INVALID_TIMESTAMP),
            )
        }
        if (snapshot.packageName != rules.youtubePackageName) {
            reset(ShortsDetectionState.NOT_SHORTS, snapshot.capturedAtElapsedMillis)
            return result(
                state = ShortsDetectionState.NOT_SHORTS,
                confidence = 1f,
                codes = setOf(ShortsSignalCode.PACKAGE_MISMATCH),
            )
        }

        val previousTimestamp = lastTimestamp
        if (previousTimestamp != null && snapshot.capturedAtElapsedMillis < previousTimestamp) {
            reset(ShortsDetectionState.UNKNOWN, snapshot.capturedAtElapsedMillis)
            return result(
                state = ShortsDetectionState.UNKNOWN,
                confidence = 0f,
                codes = setOf(ShortsSignalCode.TIMESTAMP_ROLLBACK),
            )
        }

        val duplicateTimestamp = previousTimestamp == snapshot.capturedAtElapsedMillis
        lastTimestamp = snapshot.capturedAtElapsedMillis
        return when (val observation = score(snapshot)) {
            is Observation.Positive -> handlePositive(
                observation = observation,
                capturedAtElapsedMillis = snapshot.capturedAtElapsedMillis,
                duplicateTimestamp = duplicateTimestamp,
            )

            is Observation.Negative -> handleNegative(observation, duplicateTimestamp)
            is Observation.Unknown -> {
                clearStreaks()
                currentState = ShortsDetectionState.UNKNOWN
                result(
                    state = ShortsDetectionState.UNKNOWN,
                    confidence = observation.confidence,
                    codes = observation.codes,
                )
            }
        }
    }

    private fun handlePositive(
        observation: Observation.Positive,
        capturedAtElapsedMillis: Long,
        duplicateTimestamp: Boolean,
    ): DetectionResult {
        negativeObservations = 0
        if (currentState == ShortsDetectionState.SHORTS_CONFIRMED) {
            return result(
                state = currentState,
                confidence = observation.confidence,
                codes = observation.codes,
            )
        }

        val signature = observation.codes intersect STABLE_POSITIVE_CODES
        val isConsistent = positiveSignature.isNotEmpty() &&
            signature.isNotEmpty() &&
            positiveSignature.intersect(signature).isNotEmpty()
        if (positiveSince == null || !isConsistent) {
            positiveSince = capturedAtElapsedMillis
            positiveObservations = 1
            positiveSignature = signature
        } else if (!duplicateTimestamp) {
            positiveObservations += 1
        }

        val stableDuration = capturedAtElapsedMillis - requireNotNull(positiveSince)
        val confirmed = positiveObservations >= rules.positiveObservationCount ||
            stableDuration >= rules.positiveDebounceMillis
        currentState = if (confirmed) {
            ShortsDetectionState.SHORTS_CONFIRMED
        } else {
            ShortsDetectionState.UNKNOWN
        }
        return result(
            state = currentState,
            confidence = observation.confidence,
            codes = if (confirmed) {
                observation.codes
            } else {
                observation.codes + ShortsSignalCode.POSITIVE_DEBOUNCE_PENDING
            },
        )
    }

    private fun handleNegative(
        observation: Observation.Negative,
        duplicateTimestamp: Boolean,
    ): DetectionResult {
        positiveSince = null
        positiveObservations = 0
        positiveSignature = emptySet()
        if (currentState != ShortsDetectionState.SHORTS_CONFIRMED) {
            currentState = ShortsDetectionState.NOT_SHORTS
            negativeObservations = 0
            return result(currentState, observation.confidence, observation.codes)
        }

        if (!duplicateTimestamp) negativeObservations += 1
        if (negativeObservations >= rules.negativeObservationCount) {
            currentState = ShortsDetectionState.NOT_SHORTS
            negativeObservations = 0
            return result(currentState, observation.confidence, observation.codes)
        }
        return result(
            state = ShortsDetectionState.SHORTS_CONFIRMED,
            confidence = observation.confidence.coerceAtMost(0.5f),
            codes = observation.codes + ShortsSignalCode.NEGATIVE_EXIT_PENDING,
        )
    }

    private fun score(snapshot: WindowSnapshot): Observation {
        val viewIds = snapshot.nodes.mapNotNull { node ->
            node.viewIdResourceName?.let(::resourceEntryName)
        }.toSet()
        val strongPositive = viewIds.any(strongShortsViewIds::contains)
        val strongNegative = viewIds.any(strongNonShortsViewIds::contains)

        val codes = linkedSetOf<String>()
        if (strongPositive) codes += ShortsSignalCode.STRONG_SHORTS_VIEW_ID
        if (strongNegative) codes += ShortsSignalCode.STRONG_NON_SHORTS_VIEW_ID
        if (strongPositive && strongNegative) {
            return Observation.Unknown(
                confidence = 0f,
                codes = codes + ShortsSignalCode.CONFLICTING_LAYOUT,
            )
        }

        val verticalPager = snapshot.nodes.any(::isVerticalPager)
        val actionStack = snapshot.nodes
            .flatMap { it.knownTextHints }
            .toSet()
            .intersect(ACTION_HINTS)
            .size >= 2
        val shortsLabel = snapshot.nodes.any {
            KnownTextHint.SHORTS_LABEL in it.knownTextHints
        }
        val portraitWindow = snapshot.windowWidthPx != null &&
            snapshot.windowHeightPx != null &&
            snapshot.windowHeightPx > snapshot.windowWidthPx
        val verticalScrollEvent = snapshot.eventType == TYPE_VIEW_SCROLLED
        val hasPrimaryShortsEvidence = strongPositive || verticalPager || actionStack || shortsLabel

        var score = 0f
        var independentSignals = 0
        if (verticalPager) {
            codes += ShortsSignalCode.VERTICAL_PAGER
            score += 0.4f
            independentSignals += 1
        }
        if (actionStack) {
            codes += ShortsSignalCode.ACTION_STACK
            score += 0.35f
            independentSignals += 1
        }
        if (shortsLabel) {
            codes += ShortsSignalCode.SHORTS_LABEL
            score += 0.15f
            independentSignals += 1
        }
        if (portraitWindow && hasPrimaryShortsEvidence) {
            codes += ShortsSignalCode.PORTRAIT_WINDOW
            score += 0.1f
            independentSignals += 1
        }
        if (verticalScrollEvent && hasPrimaryShortsEvidence) {
            codes += ShortsSignalCode.VERTICAL_SCROLL_EVENT
            score += 0.1f
            independentSignals += 1
        }

        if (strongPositive) {
            return Observation.Positive(confidence = 0.95f, codes = codes)
        }
        if (
            independentSignals >= rules.minimumIndependentSignals &&
            score >= rules.positiveThreshold
        ) {
            return Observation.Positive(confidence = score.coerceAtMost(0.9f), codes = codes)
        }
        if (strongNegative) {
            return Observation.Negative(confidence = 0.95f, codes = codes)
        }
        if (codes.isNotEmpty()) {
            return Observation.Unknown(confidence = score.coerceIn(0f, 0.6f), codes = codes)
        }
        if (snapshot.nodes.size >= INFORMATIVE_TREE_NODE_COUNT) {
            return Observation.Unknown(
                confidence = 0f,
                codes = setOf(ShortsSignalCode.UNRECOGNIZED_LAYOUT),
            )
        }
        return Observation.Unknown(confidence = 0f, codes = emptySet())
    }

    private fun resourceEntryName(viewIdResourceName: String): String? {
        val packageSeparator = ":id/"
        if (packageSeparator in viewIdResourceName) {
            val resourcePackage = viewIdResourceName.substringBefore(packageSeparator)
            if (resourcePackage != rules.youtubePackageName) return null
            return viewIdResourceName.substringAfter(packageSeparator).lowercase(Locale.ROOT)
        }
        return viewIdResourceName.substringAfterLast('/').lowercase(Locale.ROOT)
    }

    private fun reset(state: ShortsDetectionState, timestamp: Long) {
        currentState = state
        lastTimestamp = timestamp
        clearStreaks()
    }

    private fun clearStreaks() {
        positiveSince = null
        positiveObservations = 0
        positiveSignature = emptySet()
        negativeObservations = 0
    }

    private fun result(
        state: ShortsDetectionState,
        confidence: Float,
        codes: Set<String>,
    ): DetectionResult = DetectionResult(
        state = state,
        confidence = confidence.coerceIn(0f, 1f),
        matchedSignalCodes = codes,
        rulesVersion = rules.rulesVersion,
    )

    private sealed interface Observation {
        val confidence: Float
        val codes: Set<String>

        data class Positive(
            override val confidence: Float,
            override val codes: Set<String>,
        ) : Observation

        data class Negative(
            override val confidence: Float,
            override val codes: Set<String>,
        ) : Observation

        data class Unknown(
            override val confidence: Float,
            override val codes: Set<String>,
        ) : Observation
    }

    companion object {
        private const val TYPE_VIEW_SCROLLED = 4_096
        private const val ACTION_SCROLL_FORWARD = 4_096
        private const val ACTION_SCROLL_BACKWARD = 8_192
        private const val INFORMATIVE_TREE_NODE_COUNT = 3

        private val ACTION_HINTS = setOf(
            KnownTextHint.COMMENTS_ACTION,
            KnownTextHint.SHARE_ACTION,
            KnownTextHint.SUBSCRIBE_ACTION,
        )
        private val STABLE_POSITIVE_CODES = setOf(
            ShortsSignalCode.STRONG_SHORTS_VIEW_ID,
            ShortsSignalCode.VERTICAL_PAGER,
            ShortsSignalCode.ACTION_STACK,
        )

        private fun isVerticalPager(node: WindowNodeSignal): Boolean {
            val type = listOfNotNull(node.className, node.roleName)
                .joinToString(separator = " ")
                .lowercase(Locale.ROOT)
            val pagerType = "viewpager" in type || "recyclerview" in type
            val canPage = ACTION_SCROLL_FORWARD in node.actionIds ||
                ACTION_SCROLL_BACKWARD in node.actionIds
            return node.isScrollable && pagerType && canPage
        }
    }
}
