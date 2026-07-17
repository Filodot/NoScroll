package com.filodot.noscroll.core.tasks

import com.filodot.noscroll.core.contracts.WallClock
import com.filodot.noscroll.core.model.CustomTaskPreset
import com.filodot.noscroll.core.model.TaskCompletionMode
import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskTarget
import com.filodot.noscroll.core.model.TaskTrigger
import com.filodot.noscroll.core.model.TaskType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTaskFactoryTest {
    private val now = Instant.parse("2026-07-17T08:00:00Z")
    private val factory = LocalTaskFactory(WallClock { now }) { "task-${it.epochSecond}" }

    @Test
    fun `enabled task types rotate predictably`() {
        val types = setOf(TaskType.ARITHMETIC, TaskType.PUSH_UPS)

        val first = factory.create(
            TaskDifficulty.EASY,
            TaskTrigger.ENTRY,
            TaskTarget.YOUTUBE_SHORTS,
            types,
            emptyList(),
            sequence = 0,
        )
        val second = factory.create(
            TaskDifficulty.MEDIUM,
            TaskTrigger.INTERVAL,
            TaskTarget.INSTAGRAM,
            types,
            emptyList(),
            sequence = 1,
        )

        assertEquals(TaskType.ARITHMETIC, first.type)
        assertEquals(TaskType.PUSH_UPS, second.type)
        assertEquals(TaskTarget.INSTAGRAM, second.target)
        assertEquals(TaskCompletionMode.MANUAL_CONFIRMATION, second.completionMode)
        assertTrue(second.prompt.contains("10 отжиманий"))
    }

    @Test
    fun `custom preset content is copied into durable pending task`() {
        val preset = CustomTaskPreset(
            id = "water",
            title = "Пауза для воды",
            instruction = "Выпейте стакан воды",
            createdAt = now,
        )

        val task = factory.create(
            TaskDifficulty.HARD,
            TaskTrigger.INTERVAL,
            TaskTarget.INSTAGRAM,
            setOf(TaskType.CUSTOM),
            listOf(preset),
            sequence = 42,
        )

        assertEquals(TaskType.CUSTOM, task.type)
        assertEquals("water", task.customPresetId)
        assertTrue(task.prompt.contains("Выпейте стакан воды"))
    }

    @Test
    fun `unavailable custom type safely falls back to arithmetic`() {
        val task = factory.create(
            TaskDifficulty.EASY,
            TaskTrigger.ENTRY,
            TaskTarget.YOUTUBE_SHORTS,
            setOf(TaskType.CUSTOM),
            emptyList(),
            sequence = 0,
        )

        assertEquals(TaskType.ARITHMETIC, task.type)
        assertEquals(TaskCompletionMode.CHECKED_ANSWER, task.completionMode)
    }
}
