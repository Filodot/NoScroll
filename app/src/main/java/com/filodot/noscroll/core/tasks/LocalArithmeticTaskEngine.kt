package com.filodot.noscroll.core.tasks

import com.filodot.noscroll.core.contracts.WallClock
import com.filodot.noscroll.core.model.ArithmeticOperation
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskTrigger
import com.filodot.noscroll.core.model.TaskTarget
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class ArithmeticExample(
    val operation: ArithmeticOperation,
    val leftOperand: Int,
    val rightOperand: Int,
)

data class ArithmeticTaskState(
    val pendingTask: PendingTask? = null,
    val recentExamples: List<ArithmeticExample> = emptyList(),
)

sealed interface AnswerCheckResult {
    data object NoPendingTask : AnswerCheckResult

    data class InvalidInput(val task: PendingTask) : AnswerCheckResult

    data class Incorrect(
        val task: PendingTask,
        val replacementAvailable: Boolean,
    ) : AnswerCheckResult

    data class Correct(val task: PendingTask) : AnswerCheckResult

    data class AlreadySolved(val task: PendingTask) : AnswerCheckResult
}

sealed interface TaskReplacementResult {
    data object NoPendingTask : TaskReplacementResult

    data class NotAvailable(
        val task: PendingTask,
        val failuresRemaining: Int,
    ) : TaskReplacementResult

    data class AlreadySolved(val task: PendingTask) : TaskReplacementResult

    data class Replaced(val task: PendingTask) : TaskReplacementResult
}

fun interface TaskIdGenerator {
    fun create(createdAt: Instant): String
}

object UuidTaskIdGenerator : TaskIdGenerator {
    override fun create(createdAt: Instant): String = UUID.randomUUID().toString()
}

/** Local state machine for generating, restoring, checking, and replacing arithmetic tasks. */
class LocalArithmeticTaskEngine(
    private val wallClock: WallClock,
    private val random: Random = Random.Default,
    private val idGenerator: TaskIdGenerator = UuidTaskIdGenerator,
    initialState: ArithmeticTaskState = ArithmeticTaskState(),
) {
    private var state = initialState.normalized()

    /** Returns the restored pending task or creates exactly one new task. */
    @Synchronized
    fun requireTask(
        difficulty: TaskDifficulty = TaskDifficulty.MEDIUM,
        trigger: TaskTrigger = TaskTrigger.INTERVAL,
        target: TaskTarget = TaskTarget.YOUTUBE_SHORTS,
    ): PendingTask {
        state.pendingTask?.let { return it }
        return generateAndStoreTask(difficulty, trigger, target)
    }

    @Synchronized
    fun submitAnswer(input: String): AnswerCheckResult {
        val current = state.pendingTask ?: return AnswerCheckResult.NoPendingTask
        if (current.solved) return AnswerCheckResult.AlreadySolved(current)

        val parsedAnswer = input.trim().toIntOrNull()
            ?: return AnswerCheckResult.InvalidInput(current)

        if (parsedAnswer == current.expectedAnswer) {
            val solved = current.copy(solved = true)
            state = state.copy(pendingTask = solved)
            return AnswerCheckResult.Correct(solved)
        }

        val updated = current.copy(wrongAttempts = current.wrongAttempts.saturatingIncrement())
        state = state.copy(pendingTask = updated)
        return AnswerCheckResult.Incorrect(
            task = updated,
            replacementAvailable = updated.wrongAttempts >= FAILURES_BEFORE_REPLACEMENT,
        )
    }

    @Synchronized
    fun replaceAfterFailures(): TaskReplacementResult {
        val current = state.pendingTask ?: return TaskReplacementResult.NoPendingTask
        if (current.solved) return TaskReplacementResult.AlreadySolved(current)
        if (current.wrongAttempts < FAILURES_BEFORE_REPLACEMENT) {
            return TaskReplacementResult.NotAvailable(
                task = current,
                failuresRemaining = FAILURES_BEFORE_REPLACEMENT - current.wrongAttempts,
            )
        }

        return TaskReplacementResult.Replaced(
            generateAndStoreTask(current.difficulty, current.trigger, current.target),
        )
    }

    /** Clears only the matching solved task; an unsolved gate cannot be skipped through this API. */
    @Synchronized
    fun clearSolved(taskId: String): Boolean {
        val current = state.pendingTask ?: return false
        if (current.id != taskId || !current.solved) return false
        state = state.copy(pendingTask = null)
        return true
    }

    @Synchronized
    fun state(): ArithmeticTaskState = state

    private fun generateAndStoreTask(
        difficulty: TaskDifficulty,
        trigger: TaskTrigger,
        target: TaskTarget,
    ): PendingTask {
        val recent = state.recentExamples.toSet()
        var selected: ArithmeticExample? = null
        for (attempt in 0 until MAX_RANDOM_ATTEMPTS) {
            val candidate = randomExample(difficulty)
            if (candidate !in recent) {
                selected = candidate
                break
            }
        }

        val example = selected ?: firstAvailableExample(recent, difficulty)
        val createdAt = wallClock.now()
        val task = PendingTask(
            id = idGenerator.create(createdAt),
            operation = example.operation,
            leftOperand = example.leftOperand,
            rightOperand = example.rightOperand,
            expectedAnswer = example.expectedAnswer(),
            createdAt = createdAt,
            difficulty = difficulty,
            trigger = trigger,
            target = target,
        )
        state = state.copy(
            pendingTask = task,
            recentExamples = state.recentExamples.appendRecent(example),
        )
        return task
    }

    private fun randomExample(difficulty: TaskDifficulty): ArithmeticExample {
        val profile = ArithmeticDifficultyProfile.forDifficulty(difficulty)
        return when (ArithmeticOperation.entries[random.nextInt(ArithmeticOperation.entries.size)]) {
            ArithmeticOperation.ADD -> ArithmeticExample(
                operation = ArithmeticOperation.ADD,
                leftOperand = random.nextInt(profile.addRange.first, profile.addRange.last + 1),
                rightOperand = random.nextInt(profile.addRange.first, profile.addRange.last + 1),
            )

            ArithmeticOperation.SUBTRACT -> {
                val first = random.nextInt(
                    profile.subtractRange.first,
                    profile.subtractRange.last + 1,
                )
                val second = random.nextInt(
                    profile.subtractRange.first,
                    profile.subtractRange.last + 1,
                )
                ArithmeticExample(
                    operation = ArithmeticOperation.SUBTRACT,
                    leftOperand = max(first, second),
                    rightOperand = min(first, second),
                )
            }

            ArithmeticOperation.MULTIPLY -> ArithmeticExample(
                operation = ArithmeticOperation.MULTIPLY,
                leftOperand = random.nextInt(
                    profile.multiplyRange.first,
                    profile.multiplyRange.last + 1,
                ),
                rightOperand = random.nextInt(
                    profile.multiplyRange.first,
                    profile.multiplyRange.last + 1,
                ),
            )
        }
    }

    private fun firstAvailableExample(
        recent: Set<ArithmeticExample>,
        difficulty: TaskDifficulty,
    ): ArithmeticExample {
        val profile = ArithmeticDifficultyProfile.forDifficulty(difficulty)
        ArithmeticOperation.entries.forEach { operation ->
            when (operation) {
                ArithmeticOperation.ADD -> {
                    for (left in profile.addRange) {
                        for (right in profile.addRange) {
                            ArithmeticExample(operation, left, right)
                                .takeIf { it !in recent }
                                ?.let { return it }
                        }
                    }
                }

                ArithmeticOperation.SUBTRACT -> {
                    for (left in profile.subtractRange) {
                        for (right in profile.subtractRange.first..left) {
                            ArithmeticExample(operation, left, right)
                                .takeIf { it !in recent }
                                ?.let { return it }
                        }
                    }
                }

                ArithmeticOperation.MULTIPLY -> {
                    for (left in profile.multiplyRange) {
                        for (right in profile.multiplyRange) {
                            ArithmeticExample(operation, left, right)
                                .takeIf { it !in recent }
                                ?.let { return it }
                        }
                    }
                }
            }
        }
        error("Arithmetic task space is unexpectedly exhausted")
    }
}

private data class ArithmeticDifficultyProfile(
    val addRange: IntRange,
    val subtractRange: IntRange,
    val multiplyRange: IntRange,
) {
    companion object {
        fun forDifficulty(difficulty: TaskDifficulty): ArithmeticDifficultyProfile =
            when (difficulty) {
                TaskDifficulty.EASY -> ArithmeticDifficultyProfile(
                    addRange = 1..20,
                    subtractRange = 1..20,
                    multiplyRange = 2..5,
                )

                TaskDifficulty.MEDIUM -> ArithmeticDifficultyProfile(
                    addRange = 10..99,
                    subtractRange = 10..99,
                    multiplyRange = 2..9,
                )

                TaskDifficulty.HARD -> ArithmeticDifficultyProfile(
                    addRange = 100..999,
                    subtractRange = 100..999,
                    multiplyRange = 10..29,
                )
            }
    }
}

private fun ArithmeticTaskState.normalized(): ArithmeticTaskState {
    val normalizedTask = pendingTask?.copy(wrongAttempts = pendingTask.wrongAttempts.coerceAtLeast(0))
    val recent = recentExamples
        .takeLast(RECENT_EXAMPLE_LIMIT)
        .let { examples ->
            normalizedTask?.let { examples.appendRecent(it.toExample()) } ?: examples
        }
    return copy(
        pendingTask = normalizedTask,
        recentExamples = recent,
    )
}

private fun List<ArithmeticExample>.appendRecent(example: ArithmeticExample): List<ArithmeticExample> =
    (filterNot { it == example } + example).takeLast(RECENT_EXAMPLE_LIMIT)

private fun PendingTask.toExample(): ArithmeticExample =
    ArithmeticExample(operation, leftOperand, rightOperand)

private fun ArithmeticExample.expectedAnswer(): Int =
    when (operation) {
        ArithmeticOperation.ADD -> leftOperand + rightOperand
        ArithmeticOperation.SUBTRACT -> leftOperand - rightOperand
        ArithmeticOperation.MULTIPLY -> leftOperand * rightOperand
    }

private fun Int.saturatingIncrement(): Int = if (this == Int.MAX_VALUE) this else this + 1

private const val FAILURES_BEFORE_REPLACEMENT = 3
private const val RECENT_EXAMPLE_LIMIT = 5
private const val MAX_RANDOM_ATTEMPTS = 32
