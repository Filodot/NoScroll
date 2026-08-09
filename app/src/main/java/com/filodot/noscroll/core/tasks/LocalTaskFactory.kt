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
            type in enabledTypes &&
                type != TaskType.LEARNING &&
                (type != TaskType.CUSTOM || enabledCustom.isNotEmpty())
        }.ifEmpty { listOf(TaskType.ARITHMETIC) }
        return when (val type = available[Math.floorMod(sequence, available.size)]) {
            TaskType.ARITHMETIC -> LocalArithmeticTaskEngine(wallClock = wallClock)
                .requireTask(difficulty, trigger, target)

            TaskType.ENGLISH_VOCABULARY -> {
                val now = wallClock.now()
                EnglishVocabularyTaskFactory.create(
                    id = idGenerator(now),
                    createdAt = now,
                    difficulty = difficulty,
                    trigger = trigger,
                    target = target,
                    sequence = sequence,
                )
            }

            TaskType.PUSH_UPS -> manualTask(
                difficulty = difficulty,
                trigger = trigger,
                target = target,
                type = type,
                prompt = sportPrompt(difficulty, sequence),
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

            TaskType.LEARNING -> error("Learning tasks are created from the offline lesson queue")
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

private data class SportExercise(
    val name: String,
    val easy: String,
    val medium: String,
    val hard: String,
)

private val SPORT_EXERCISES = listOf(
    SportExercise("приседаний", "6", "12", "20"),
    SportExercise("отжиманий от стены или опоры", "5", "10", "15"),
    SportExercise("обратных выпадов на каждую ногу", "4", "8", "12"),
    SportExercise("ягодичных мостиков", "8", "15", "25"),
    SportExercise("подъёмов на носки", "10", "20", "30"),
    SportExercise("секунд планки в удобном варианте", "15", "30", "45"),
)

private fun sportPrompt(difficulty: TaskDifficulty, sequence: Int): String {
    val exercise = SPORT_EXERCISES[Math.floorMod(sequence, SPORT_EXERCISES.size)]
    val amount = when (difficulty) {
        TaskDifficulty.EASY -> exercise.easy
        TaskDifficulty.MEDIUM -> exercise.medium
        TaskDifficulty.HARD -> exercise.hard
    }
    return "Сделайте $amount ${exercise.name} в спокойном темпе. " +
        "Если есть противопоказания или боль — выберите другое задание."
}
