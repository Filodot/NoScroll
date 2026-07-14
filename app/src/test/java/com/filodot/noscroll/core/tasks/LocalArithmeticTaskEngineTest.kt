package com.filodot.noscroll.core.tasks

import com.filodot.noscroll.core.model.ArithmeticOperation
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.testing.FakeWallClock
import java.time.Instant
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalArithmeticTaskEngineTest {
    private val instant = Instant.parse("2026-07-14T12:00:00Z")

    @Test
    fun `same seed clock and ids generate the same task`() {
        val first = engine(seed = 42).requireTask()
        val second = engine(seed = 42).requireTask()

        assertEquals(first, second)
    }

    @Test
    fun `seeded generation respects all operation ranges and integer answers`() {
        val operations = buildSet {
            repeat(200) { seed ->
                val task = engine(seed = seed).requireTask()
                add(task.operation)
                when (task.operation) {
                    ArithmeticOperation.ADD -> {
                        assertTrue(task.leftOperand in 10..99)
                        assertTrue(task.rightOperand in 10..99)
                        assertEquals(task.leftOperand + task.rightOperand, task.expectedAnswer)
                    }

                    ArithmeticOperation.SUBTRACT -> {
                        assertTrue(task.leftOperand in 10..99)
                        assertTrue(task.rightOperand in 10..99)
                        assertTrue(task.leftOperand >= task.rightOperand)
                        assertEquals(task.leftOperand - task.rightOperand, task.expectedAnswer)
                        assertTrue(task.expectedAnswer >= 0)
                    }

                    ArithmeticOperation.MULTIPLY -> {
                        assertTrue(task.leftOperand in 2..9)
                        assertTrue(task.rightOperand in 2..9)
                        assertEquals(task.leftOperand * task.rightOperand, task.expectedAnswer)
                    }
                }
            }
        }

        assertEquals(ArithmeticOperation.entries.toSet(), operations)
    }

    @Test
    fun `leading zero answer is normalized and accepted`() {
        val engine = engine(initialTask = task(expectedAnswer = 56))

        val result = engine.submitAnswer(" 0056 ")

        assertTrue(result is AnswerCheckResult.Correct)
        assertTrue(engine.state().pendingTask?.solved == true)
    }

    @Test
    fun `invalid integer input does not consume an attempt`() {
        val engine = engine(initialTask = task(expectedAnswer = 56))

        listOf("", "not a number", "999999999999999999999999").forEach { input ->
            assertTrue(engine.submitAnswer(input) is AnswerCheckResult.InvalidInput)
        }

        assertEquals(0, engine.state().pendingTask?.wrongAttempts)
    }

    @Test
    fun `replacement becomes available only after three wrong integer answers`() {
        val engine = engine(initialTask = task(expectedAnswer = 56))

        val first = engine.submitAnswer("0") as AnswerCheckResult.Incorrect
        val earlyReplacement = engine.replaceAfterFailures() as TaskReplacementResult.NotAvailable
        val second = engine.submitAnswer("0") as AnswerCheckResult.Incorrect
        val third = engine.submitAnswer("0") as AnswerCheckResult.Incorrect

        assertFalse(first.replacementAvailable)
        assertEquals(2, earlyReplacement.failuresRemaining)
        assertFalse(second.replacementAvailable)
        assertTrue(third.replacementAvailable)
        assertEquals(3, third.task.wrongAttempts)
    }

    @Test
    fun `replacement creates a fresh unsolved pending task and keeps gate closed`() {
        val original = task(expectedAnswer = 56, id = "restored-task")
        val engine = engine(initialTask = original)
        repeat(3) { engine.submitAnswer("0") }

        val result = engine.replaceAfterFailures() as TaskReplacementResult.Replaced

        assertNotEquals(original.id, result.task.id)
        assertEquals(0, result.task.wrongAttempts)
        assertFalse(result.task.solved)
        assertEquals(result.task, engine.state().pendingTask)
    }

    @Test
    fun `generator never repeats any of the previous five examples`() {
        val engine = LocalArithmeticTaskEngine(
            wallClock = FakeWallClock(instant),
            random = ZeroRandom(),
            idGenerator = sequenceIds(),
        )
        val generated = mutableListOf<ArithmeticExample>()

        repeat(12) {
            val current = engine.requireTask()
            val example = current.toExample()
            assertFalse(example in generated.takeLast(5))
            generated += example
            repeat(3) { engine.submitAnswer((current.expectedAnswer + 1).toString()) }
            engine.replaceAfterFailures()
        }

        assertEquals(5, engine.state().recentExamples.size)
    }

    @Test
    fun `restored pending task is returned unchanged and remains answerable`() {
        val restored = task(expectedAnswer = 56, wrongAttempts = 2)
        val engine = engine(initialTask = restored)

        val required = engine.requireTask()
        val result = engine.submitAnswer("0") as AnswerCheckResult.Incorrect

        assertEquals(restored, required)
        assertEquals(3, result.task.wrongAttempts)
        assertEquals(listOf(restored.toExample()), engine.state().recentExamples)
    }

    @Test
    fun `only matching solved task can be cleared`() {
        val engine = engine(initialTask = task(expectedAnswer = 56))

        assertFalse(engine.clearSolved("task-1"))
        engine.submitAnswer("56")
        assertFalse(engine.clearSolved("other"))
        assertTrue(engine.clearSolved("task-1"))
        assertEquals(null, engine.state().pendingTask)
    }

    @Test
    fun `solved task cannot be answered or replaced again`() {
        val solved = task(expectedAnswer = 56, solved = true)
        val engine = engine(initialTask = solved)

        assertTrue(engine.submitAnswer("56") is AnswerCheckResult.AlreadySolved)
        assertTrue(engine.replaceAfterFailures() is TaskReplacementResult.AlreadySolved)
        assertEquals(solved, engine.requireTask())
    }

    @Test
    fun `missing task reports explicit no pending results`() {
        val engine = engine()

        assertTrue(engine.submitAnswer("1") is AnswerCheckResult.NoPendingTask)
        assertTrue(engine.replaceAfterFailures() is TaskReplacementResult.NoPendingTask)
    }

    @Test
    fun `negative restored attempt count is repaired`() {
        val engine = engine(initialTask = task(expectedAnswer = 56, wrongAttempts = -10))

        assertEquals(0, engine.requireTask().wrongAttempts)
    }

    private fun engine(
        seed: Int = 1,
        initialTask: PendingTask? = null,
    ) = LocalArithmeticTaskEngine(
        wallClock = FakeWallClock(instant),
        random = Random(seed),
        idGenerator = sequenceIds(),
        initialState = ArithmeticTaskState(pendingTask = initialTask),
    )

    private fun task(
        expectedAnswer: Int,
        wrongAttempts: Int = 0,
        solved: Boolean = false,
        id: String = "task-1",
    ) = PendingTask(
        id = id,
        operation = ArithmeticOperation.ADD,
        leftOperand = 20,
        rightOperand = 36,
        expectedAnswer = expectedAnswer,
        createdAt = instant,
        wrongAttempts = wrongAttempts,
        solved = solved,
    )

    private fun sequenceIds(): TaskIdGenerator {
        var next = 1
        return TaskIdGenerator { "task-${next++}" }
    }

    private fun PendingTask.toExample() =
        ArithmeticExample(operation, leftOperand, rightOperand)

    private class ZeroRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }
}
