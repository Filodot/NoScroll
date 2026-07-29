package com.filodot.noscroll.monitoring.runtime

import com.filodot.noscroll.core.model.ArithmeticOperation
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.model.TaskTarget
import com.filodot.noscroll.core.model.TaskTrigger
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingGateTargetPolicyTest {
    private val now = Instant.parse("2026-07-29T10:00:00Z")
    private val pending = PendingTask(
        id = "pending-youtube",
        operation = ArithmeticOperation.ADD,
        leftOperand = 2,
        rightOperand = 3,
        expectedAnswer = 5,
        createdAt = now,
        target = TaskTarget.YOUTUBE_SHORTS,
        trigger = TaskTrigger.INTERVAL,
        wrongAttempts = 2,
    )
    private val cycle = GateCycle(
        localDate = LocalDate.of(2026, 7, 29),
        pendingTaskId = pending.id,
        usedSeconds = 300,
        instagramUsedSeconds = 600,
        updatedAt = now,
    )

    @Test
    fun `task of another target cannot make current policy believe it has a pending gate`() {
        val instagramCycle = cycle.forPolicyTarget(pending, TaskTarget.INSTAGRAM)

        assertNull(instagramCycle.pendingTaskId)
        assertEquals(300L, instagramCycle.usedSeconds)
        assertEquals(600L, instagramCycle.instagramUsedSeconds)
    }

    @Test
    fun `retarget preserves challenge and attempts but grants only requested application`() {
        val retargeted = pending.retargetFor(TaskTarget.INSTAGRAM, TaskTrigger.ENTRY)

        assertEquals(TaskTarget.INSTAGRAM, retargeted.target)
        assertEquals(TaskTrigger.ENTRY, retargeted.trigger)
        assertEquals(pending.id, retargeted.id)
        assertEquals(pending.expectedAnswer, retargeted.expectedAnswer)
        assertEquals(pending.wrongAttempts, retargeted.wrongAttempts)
    }

    @Test
    fun `matching target keeps cycle and task unchanged`() {
        assertEquals(cycle, cycle.forPolicyTarget(pending, TaskTarget.YOUTUBE_SHORTS))
        assertEquals(
            pending,
            pending.retargetFor(TaskTarget.YOUTUBE_SHORTS, TaskTrigger.ENTRY),
        )
    }
}
