package com.filodot.noscroll.core.tasks

import com.filodot.noscroll.core.contracts.WallClock
import com.filodot.noscroll.core.model.ArithmeticOperation
import com.filodot.noscroll.core.model.PendingTask
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
    fun requireTask(): PendingTask {
        state.pendingTask?.let { return it }
        return generateAndStoreTask()
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

        return TaskReplacementResult.Replaced(generateAndStoreTask())
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

    private fun generateAndStoreTask(): PendingTask {
        val recent = state.recentExamples.toSet()
        var selected: ArithmeticExample? = null
        for (attempt in 0 until MAX_RANDOM_ATTEMPTS) {
            val candidate = randomExample()
            if (candidate !in recent) {
                selected = candidate
                break
            }
        }

        val example = selected ?: firstAvailableExample(recent)
        val createdAt = wallClock.now()
        val task = PendingTask(
            id = idGenerator.create(createdAt),
            operation = example.operation,
            leftOperand = example.leftOperand,
            rightOperand = example.rightOperand,
            expectedAnswer = example.expectedAnswer(),
            createdAt = createdAt,
        )
        state = state.copy(
            pendingTask = task,
            recentExamples = state.recentExamples.appendRecent(example),
        )
        return task
    }

    private fun randomExample(): ArithmeticExample =
        when (ArithmeticOperation.entries[random.nextInt(ArithmeticOperation.entries.size)]) {
            ArithmeticOperation.ADD -> ArithmeticExample(
                operation = ArithmeticOperation.ADD,
                leftOperand = random.nextInt(ADD_MIN, ADD_MAX_EXCLUSIVE),
                rightOperand = random.nextInt(ADD_MIN, ADD_MAX_EXCLUSIVE),
            )

            ArithmeticOperation.SUBTRACT -> {
                val first = random.nextInt(SUBTRACT_MIN, SUBTRACT_MAX_EXCLUSIVE)
                val second = random.nextInt(SUBTRACT_MIN, SUBTRACT_MAX_EXCLUSIVE)
                ArithmeticExample(
                    operation = ArithmeticOperation.SUBTRACT,
                    leftOperand = max(first, second),
                    rightOperand = min(first, second),
                )
            }

            ArithmeticOperation.MULTIPLY -> ArithmeticExample(
                operation = ArithmeticOperation.MULTIPLY,
                leftOperand = random.nextInt(MULTIPLY_MIN, MULTIPLY_MAX_EXCLUSIVE),
                rightOperand = random.nextInt(MULTIPLY_MIN, MULTIPLY_MAX_EXCLUSIVE),
            )
        }

    private fun firstAvailableExample(recent: Set<ArithmeticExample>): ArithmeticExample {
        ArithmeticOperation.entries.forEach { operation ->
            when (operation) {
                ArithmeticOperation.ADD -> {
                    for (left in ADD_MIN until ADD_MAX_EXCLUSIVE) {
                        for (right in ADD_MIN until ADD_MAX_EXCLUSIVE) {
                            ArithmeticExample(operation, left, right)
                                .takeIf { it !in recent }
                                ?.let { return it }
                        }
                    }
                }

                ArithmeticOperation.SUBTRACT -> {
                    for (left in SUBTRACT_MIN until SUBTRACT_MAX_EXCLUSIVE) {
                        for (right in SUBTRACT_MIN..left) {
                            ArithmeticExample(operation, left, right)
                                .takeIf { it !in recent }
                                ?.let { return it }
                        }
                    }
                }

                ArithmeticOperation.MULTIPLY -> {
                    for (left in MULTIPLY_MIN until MULTIPLY_MAX_EXCLUSIVE) {
                        for (right in MULTIPLY_MIN until MULTIPLY_MAX_EXCLUSIVE) {
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
private const val ADD_MIN = 10
private const val ADD_MAX_EXCLUSIVE = 100
private const val SUBTRACT_MIN = 10
private const val SUBTRACT_MAX_EXCLUSIVE = 100
private const val MULTIPLY_MIN = 2
private const val MULTIPLY_MAX_EXCLUSIVE = 10
