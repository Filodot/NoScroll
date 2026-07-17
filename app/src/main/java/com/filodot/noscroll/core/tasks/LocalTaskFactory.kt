package com.filodot.noscroll.core.tasks

import com.filodot.noscroll.core.contracts.WallClock
import com.filodot.noscroll.core.model.ArithmeticOperation
import com.filodot.noscroll.core.model.CustomTaskPreset
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.model.TaskCompletionMode
import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskTarget
import com.filodot.noscroll.core.model.TaskTrigger
import com.filodot.noscroll.core.model.TaskType
import java.time.Instant
import java.util.UUID

/** Creates typed tasks while keeping trigger, target, and difficulty independent from content. */
class LocalTaskFactory(
    private val wallClock: WallClock,
    private val idGenerator: (Instant) -> String = { UUID.randomUUID().toString() },
) {
    fun create(
        difficulty: TaskDifficulty,
        trigger: TaskTrigger,
        target: TaskTarget,
        enabledTypes: Set<TaskType>,
        customPresets: List<CustomTaskPreset>,
        sequence: Int,
    ): PendingTask {
        val enabledCustom = customPresets.filter(CustomTaskPreset::enabled)
        val available = TaskType.entries.filter { type ->
            type in enabledTypes && (type != TaskType.CUSTOM || enabledCustom.isNotEmpty())
        }.ifEmpty { listOf(TaskType.ARITHMETIC) }
        return when (val type = available[Math.floorMod(sequence, available.size)]) {
            TaskType.ARITHMETIC -> LocalArithmeticTaskEngine(wallClock = wallClock)
                .requireTask(difficulty, trigger, target)

            TaskType.PUSH_UPS -> manualTask(
                difficulty = difficulty,
                trigger = trigger,
                target = target,
                type = type,
                prompt = "Сделайте ${pushUpsFor(difficulty)} отжиманий в комфортном темпе",
            )

            TaskType.CUSTOM -> {
                val preset = enabledCustom[Math.floorMod(sequence, enabledCustom.size)]
                manualTask(
                    difficulty = difficulty,
                    trigger = trigger,
                    target = target,
                    type = type,
                    prompt = "${preset.title}\n${preset.instruction}",
                    presetId = preset.id,
                )
            }
        }
    }

    private fun manualTask(
        difficulty: TaskDifficulty,
        trigger: TaskTrigger,
        target: TaskTarget,
        type: TaskType,
        prompt: String,
        presetId: String? = null,
    ): PendingTask {
        val now = wallClock.now()
        return PendingTask(
            id = idGenerator(now),
            operation = ArithmeticOperation.ADD,
            leftOperand = 0,
            rightOperand = 0,
            expectedAnswer = 0,
            createdAt = now,
            difficulty = difficulty,
            trigger = trigger,
            target = target,
            type = type,
            completionMode = TaskCompletionMode.MANUAL_CONFIRMATION,
            prompt = prompt,
            customPresetId = presetId,
        )
    }
}

private fun pushUpsFor(difficulty: TaskDifficulty): Int = when (difficulty) {
    TaskDifficulty.EASY -> 5
    TaskDifficulty.MEDIUM -> 10
    TaskDifficulty.HARD -> 20
}
