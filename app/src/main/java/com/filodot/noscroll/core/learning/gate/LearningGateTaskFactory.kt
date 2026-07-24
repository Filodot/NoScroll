package com.filodot.noscroll.core.learning.gate

import com.filodot.noscroll.core.contracts.LearningRepository
import com.filodot.noscroll.core.contracts.WallClock
import com.filodot.noscroll.core.learning.model.ActivityContent
import com.filodot.noscroll.core.learning.model.AttemptResult
import com.filodot.noscroll.core.learning.model.ConceptMastery
import com.filodot.noscroll.core.learning.model.CourseStatus
import com.filodot.noscroll.core.learning.model.EvidenceSelectionContent
import com.filodot.noscroll.core.learning.model.LearningActivity
import com.filodot.noscroll.core.learning.model.LearningAttempt
import com.filodot.noscroll.core.learning.model.MultipleChoiceContent
import com.filodot.noscroll.core.learning.model.ScenarioContent
import com.filodot.noscroll.core.learning.model.SelfConfidence
import com.filodot.noscroll.core.learning.model.SingleChoiceContent
import com.filodot.noscroll.core.learning.model.TrueFalseContent
import com.filodot.noscroll.core.learning.progress.MasteryPolicy
import com.filodot.noscroll.core.model.ArithmeticOperation
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.model.TaskChoice
import com.filodot.noscroll.core.model.TaskCompletionMode
import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskTarget
import com.filodot.noscroll.core.model.TaskTrigger
import com.filodot.noscroll.core.model.TaskType
import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * Converts only objectively checkable, already validated offline activities into a gate task.
 * It never calls an AI provider, so enforcement cannot be held hostage by network availability.
 */
class LearningGateTaskFactory(
    private val repository: LearningRepository,
    private val wallClock: WallClock,
    private val masteryPolicy: MasteryPolicy = MasteryPolicy(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val idGenerator: (Instant) -> String = { UUID.randomUUID().toString() },
) {
    suspend fun create(
        difficulty: TaskDifficulty,
        trigger: TaskTrigger,
        target: TaskTarget,
        selectedCourseIds: Set<String>,
        sequence: Int,
    ): PendingTask? {
        if (selectedCourseIds.isEmpty()) return null
        val courses = repository.courses.first()
            .filter {
                it.id in selectedCourseIds &&
                    (it.status == CourseStatus.READY || it.status == CourseStatus.ACTIVE)
            }
            .sortedBy { it.id }
        if (courses.isEmpty()) return null
        val rotated = courses.rotate(Math.floorMod(sequence, courses.size))
        for (course in rotated) {
            val attempts = repository.getAttempts(course.id)
            val terminalActivityIds = attempts
                .filter {
                    it.result == AttemptResult.CORRECT ||
                        it.result == AttemptResult.REPLACED_AS_SUSPICIOUS
                }
                .mapTo(mutableSetOf()) { it.activityId }
            repeat(MAX_SKIPPED_LESSONS_PER_COURSE) {
                val lesson = repository.peekNextLesson(course.id) ?: return@repeat
                val eligible = lesson.activities
                    .filterNot { it.id in terminalActivityIds }
                    .mapNotNull { activity -> activity.toGatePayload()?.let { activity to it } }
                    .sortedWith(
                        compareBy<Pair<LearningActivity, GatePayload>> {
                            difficultyDistance(it.first.difficulty, difficulty)
                        }.thenBy { it.first.id },
                    )
                if (eligible.isNotEmpty()) {
                    val (activity, payload) = eligible[
                        Math.floorMod(sequence, eligible.size)
                    ]
                    val createdAt = wallClock.now()
                    return PendingTask(
                        id = idGenerator(createdAt),
                        operation = ArithmeticOperation.ADD,
                        leftOperand = 0,
                        rightOperand = 0,
                        expectedAnswer = 0,
                        createdAt = createdAt,
                        difficulty = difficulty,
                        trigger = trigger,
                        target = target,
                        type = TaskType.LEARNING,
                        completionMode = TaskCompletionMode.SINGLE_CHOICE,
                        prompt = payload.prompt,
                        choices = payload.choices,
                        expectedChoiceId = payload.expectedChoiceId,
                        learningCourseId = course.id,
                        learningLessonId = lesson.id,
                        learningActivityId = activity.id,
                        learningExplanation = activity.explanation,
                    )
                }
                repository.takeNextLesson(course.id)
            }
        }
        return null
    }

    suspend fun recordResult(task: PendingTask, result: AttemptResult) {
        if (task.type != TaskType.LEARNING) return
        val courseId = task.learningCourseId ?: return
        val lessonId = task.learningLessonId ?: return
        val activityId = task.learningActivityId ?: return
        val lesson = repository.getLesson(lessonId) ?: return
        val activity = lesson.activities.firstOrNull { it.id == activityId } ?: return
        val occurredAt = wallClock.now()
        val attempt = LearningAttempt(
            id = "${idGenerator(occurredAt)}-learning-attempt",
            courseId = courseId,
            lessonId = lessonId,
            activityId = activityId,
            conceptIds = activity.conceptIds,
            activityKind = activity.content.kind,
            result = result,
            hintsUsed = 0,
            durationSeconds = Duration.between(task.createdAt, occurredAt).seconds
                .coerceIn(0, Int.MAX_VALUE.toLong())
                .toInt(),
            confidence = SelfConfidence.MEDIUM,
            occurredAt = occurredAt,
            localDate = occurredAt.atZone(zoneId).toLocalDate(),
        )
        repository.saveAttempt(attempt)
        if (result != AttemptResult.REPLACED_AS_SUSPICIOUS) {
            val currentByConceptId = repository.getMastery(courseId)
                .associateBy(ConceptMastery::conceptId)
            activity.conceptIds.forEach { conceptId ->
                repository.saveMastery(
                    masteryPolicy.update(
                        current = currentByConceptId[conceptId] ?: ConceptMastery(conceptId),
                        attempt = attempt,
                    ),
                )
            }
        }
        if (result == AttemptResult.CORRECT ||
            result == AttemptResult.REPLACED_AS_SUSPICIOUS
        ) {
            consumeCompletedLesson(courseId, lessonId, lesson.activities)
        }
    }

    private suspend fun consumeCompletedLesson(
        courseId: String,
        lessonId: String,
        activities: List<LearningActivity>,
    ) {
        val eligibleIds = activities
            .filter { it.toGatePayload() != null }
            .mapTo(mutableSetOf(), LearningActivity::id)
        if (eligibleIds.isEmpty()) return
        val terminalIds = repository.getAttempts(courseId)
            .filter {
                it.result == AttemptResult.CORRECT ||
                    it.result == AttemptResult.REPLACED_AS_SUSPICIOUS
            }
            .mapTo(mutableSetOf()) { it.activityId }
        if (terminalIds.containsAll(eligibleIds) &&
            repository.peekNextLesson(courseId)?.id == lessonId
        ) {
            repository.takeNextLesson(courseId)
        }
    }
}

private data class GatePayload(
    val prompt: String,
    val choices: List<TaskChoice>,
    val expectedChoiceId: String,
)

private fun LearningActivity.toGatePayload(): GatePayload? {
    val payload = content.toGatePayload(prompt) ?: return null
    if (payload.choices.size !in MIN_CHOICES..MAX_CHOICES) return null
    if (payload.choices.map { it.id }.toSet().size != payload.choices.size) return null
    if (payload.expectedChoiceId !in payload.choices.mapTo(mutableSetOf()) { it.id }) return null
    return payload
}

private fun ActivityContent.toGatePayload(activityPrompt: String): GatePayload? = when (this) {
    is SingleChoiceContent -> GatePayload(
        prompt = activityPrompt,
        choices = options.map { TaskChoice(it.id, it.text) },
        expectedChoiceId = correctOptionId,
    )

    is ScenarioContent -> GatePayload(
        prompt = activityPrompt,
        choices = options.map { TaskChoice(it.id, it.text) },
        expectedChoiceId = correctOptionId,
    )

    is TrueFalseContent -> GatePayload(
        prompt = listOf(activityPrompt, statement)
            .distinctBy(String::trim)
            .joinToString("\n"),
        choices = listOf(
            TaskChoice("true", "Верно"),
            TaskChoice("false", "Неверно"),
        ),
        expectedChoiceId = expected.toString(),
    )

    is MultipleChoiceContent -> correctOptionIds.singleOrNull()?.let { expectedId ->
        GatePayload(
            prompt = activityPrompt,
            choices = options.map { TaskChoice(it.id, it.text) },
            expectedChoiceId = expectedId,
        )
    }

    is EvidenceSelectionContent -> correctOptionIds.singleOrNull()?.let { expectedId ->
        GatePayload(
            prompt = activityPrompt,
            choices = options.map { TaskChoice(it.id, it.text) },
            expectedChoiceId = expectedId,
        )
    }

    else -> null
}

private fun difficultyDistance(left: TaskDifficulty, right: TaskDifficulty): Int =
    kotlin.math.abs(left.ordinal - right.ordinal)

private fun <T> List<T>.rotate(offset: Int): List<T> =
    if (isEmpty() || offset == 0) this else drop(offset) + take(offset)

private const val MIN_CHOICES = 2
private const val MAX_CHOICES = 8
private const val MAX_SKIPPED_LESSONS_PER_COURSE = 5
