package com.filodot.noscroll.core.learning.scheduling

import com.filodot.noscroll.core.learning.model.ConceptMastery
import com.filodot.noscroll.core.learning.model.LearningConcept
import java.time.Instant

enum class ScheduleReason {
    NEW_MATERIAL,
    DUE_REVIEW,
    WEAK_PREREQUISITE,
}

data class ScheduledConcept(
    val concept: LearningConcept,
    val reason: ScheduleReason,
)

data class LearningScheduleConfig(
    val newPercent: Int = 50,
    val reviewPercent: Int = 35,
    val weakPercent: Int = 15,
) {
    fun normalized(): LearningScheduleConfig {
        val newValue = newPercent.coerceIn(0, 100)
        val reviewValue = reviewPercent.coerceIn(0, 100 - newValue)
        return copy(
            newPercent = newValue,
            reviewPercent = reviewValue,
            weakPercent = 100 - newValue - reviewValue,
        )
    }
}

/**
 * Selects concepts, not concrete exercises. Exercise generation stays replaceable and cached.
 */
class LearningScheduler {
    fun select(
        concepts: List<LearningConcept>,
        masteryByConceptId: Map<String, ConceptMastery>,
        now: Instant,
        maxConcepts: Int,
        config: LearningScheduleConfig = LearningScheduleConfig(),
    ): List<ScheduledConcept> {
        if (maxConcepts <= 0) return emptyList()
        val ordered = concepts.sortedBy(LearningConcept::position)
        val normalized = config.normalized()
        val byId = ordered.associateBy(LearningConcept::id)
        val due = ordered.filter { concept ->
            val mastery = masteryByConceptId[concept.id] ?: return@filter false
            mastery.attemptCount > 0 &&
                mastery.nextReviewAt?.let { !it.isAfter(now) } == true
        }
        val weakPrerequisiteIds = ordered
            .filter { concept -> masteryByConceptId[concept.id]?.attemptCount.orZero() == 0 }
            .flatMap(LearningConcept::prerequisiteIds)
            .filter { prerequisiteId ->
                val state = masteryByConceptId[prerequisiteId]
                state != null && !state.mastered
            }
            .toSet()
        val weak = weakPrerequisiteIds.mapNotNull(byId::get)
            .sortedWith(
                compareBy<LearningConcept> {
                    masteryByConceptId[it.id]?.score ?: 0
                }.thenBy(LearningConcept::position),
            )
        val newMaterial = ordered.filter { concept ->
            masteryByConceptId[concept.id]?.attemptCount.orZero() == 0 &&
                concept.prerequisiteIds.all { masteryByConceptId[it]?.mastered == true }
        }

        val newQuota = quota(maxConcepts, normalized.newPercent, minimumWhenAvailable = true)
        val reviewQuota = quota(maxConcepts, normalized.reviewPercent, minimumWhenAvailable = true)
        val weakQuota = (maxConcepts - newQuota - reviewQuota).coerceAtLeast(0)
        val selected = linkedMapOf<String, ScheduledConcept>()

        due.take(reviewQuota).forEach {
            if (selected.size < maxConcepts) {
                selected[it.id] = ScheduledConcept(it, ScheduleReason.DUE_REVIEW)
            }
        }
        weak.take(weakQuota).forEach {
            if (selected.size < maxConcepts) {
                selected.putIfAbsent(it.id, ScheduledConcept(it, ScheduleReason.WEAK_PREREQUISITE))
            }
        }
        newMaterial.take(newQuota).forEach {
            if (selected.size < maxConcepts) {
                selected.putIfAbsent(it.id, ScheduledConcept(it, ScheduleReason.NEW_MATERIAL))
            }
        }

        if (selected.size < maxConcepts) {
            val fallback = buildList {
                addAll(due.map { ScheduledConcept(it, ScheduleReason.DUE_REVIEW) })
                addAll(weak.map { ScheduledConcept(it, ScheduleReason.WEAK_PREREQUISITE) })
                addAll(newMaterial.map { ScheduledConcept(it, ScheduleReason.NEW_MATERIAL) })
            }
            fallback.forEach {
                if (selected.size < maxConcepts) selected.putIfAbsent(it.concept.id, it)
            }
        }
        return selected.values.toList()
    }

    private fun quota(total: Int, percent: Int, minimumWhenAvailable: Boolean): Int {
        val calculated = total * percent / 100
        return if (minimumWhenAvailable && percent > 0 && total > 0) {
            calculated.coerceAtLeast(1)
        } else {
            calculated
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0
