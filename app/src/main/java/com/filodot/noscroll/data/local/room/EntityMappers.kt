package com.filodot.noscroll.data.local.room

import com.filodot.noscroll.core.model.ArithmeticOperation
import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.CustomTaskPreset
import com.filodot.noscroll.core.model.EmergencyActivationSource
import com.filodot.noscroll.core.model.EmergencyEvent
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskCompletionMode
import com.filodot.noscroll.core.model.TaskTarget
import com.filodot.noscroll.core.model.TaskType
import com.filodot.noscroll.core.model.TaskTrigger
import java.time.Instant
import java.time.LocalDate

fun DailyUsage.toEntity(): DailyUsageEntity = DailyUsageEntity(
    localDate = localDate.toString(),
    youtubeSeconds = youtubeSeconds,
    shortsSeconds = shortsSeconds,
    instagramSeconds = instagramSeconds,
    emergencyYoutubeSeconds = emergencyYoutubeSeconds,
    gatesShown = gatesShown,
    tasksSolved = tasksSolved,
    taskExits = taskExits,
    lastUpdatedElapsedMillis = lastUpdatedElapsedMillis,
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

fun DailyUsageEntity.toModel(): DailyUsage = DailyUsage(
    localDate = LocalDate.parse(localDate),
    youtubeSeconds = youtubeSeconds,
    shortsSeconds = shortsSeconds,
    instagramSeconds = instagramSeconds,
    emergencyYoutubeSeconds = emergencyYoutubeSeconds,
    gatesShown = gatesShown,
    tasksSolved = tasksSolved,
    taskExits = taskExits,
    lastUpdatedElapsedMillis = lastUpdatedElapsedMillis,
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

fun GateCycle.toEntity(): GateCycleEntity = GateCycleEntity(
    id = id,
    localDate = localDate.toString(),
    usedSeconds = usedSeconds,
    pendingTaskId = pendingTaskId,
    intervalBlockStreak = intervalBlockStreak,
    lastIntervalBlockAtEpochMillis = lastIntervalBlockAt?.toEpochMilli(),
    entryCooldownUntilEpochMillis = entryCooldownUntil?.toEpochMilli(),
    instagramUsedSeconds = instagramUsedSeconds,
    instagramEntryCooldownUntilEpochMillis = instagramEntryCooldownUntil?.toEpochMilli(),
    difficultyLoadSeconds = difficultyLoadSeconds,
    difficultyLoadUpdatedAtEpochMillis = difficultyLoadUpdatedAt?.toEpochMilli(),
    difficultyRecoverySeconds = difficultyRecoverySeconds,
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

fun GateCycleEntity.toModel(): GateCycle = GateCycle(
    id = id,
    localDate = LocalDate.parse(localDate),
    usedSeconds = usedSeconds,
    pendingTaskId = pendingTaskId,
    intervalBlockStreak = intervalBlockStreak,
    lastIntervalBlockAt = lastIntervalBlockAtEpochMillis?.let(Instant::ofEpochMilli),
    entryCooldownUntil = entryCooldownUntilEpochMillis?.let(Instant::ofEpochMilli),
    instagramUsedSeconds = instagramUsedSeconds,
    instagramEntryCooldownUntil =
        instagramEntryCooldownUntilEpochMillis?.let(Instant::ofEpochMilli),
    difficultyLoadSeconds = difficultyLoadSeconds,
    difficultyLoadUpdatedAt = difficultyLoadUpdatedAtEpochMillis?.let(Instant::ofEpochMilli),
    difficultyRecoverySeconds = difficultyRecoverySeconds,
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

fun PendingTask.toEntity(): PendingTaskEntity = PendingTaskEntity(
    id = id,
    operation = operation.name,
    leftOperand = leftOperand,
    rightOperand = rightOperand,
    expectedAnswer = expectedAnswer,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    wrongAttempts = wrongAttempts,
    solved = solved,
    difficulty = difficulty.name,
    trigger = trigger.name,
    target = target.name,
    taskType = type.name,
    completionMode = completionMode.name,
    prompt = prompt,
    customPresetId = customPresetId,
)

fun PendingTaskEntity.toModel(): PendingTask = PendingTask(
    id = id,
    operation = enumValueOf<ArithmeticOperation>(operation),
    leftOperand = leftOperand,
    rightOperand = rightOperand,
    expectedAnswer = expectedAnswer,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    wrongAttempts = wrongAttempts,
    solved = solved,
    difficulty = enumValueOf<TaskDifficulty>(difficulty),
    trigger = enumValueOf<TaskTrigger>(trigger),
    target = enumValueOf<TaskTarget>(target),
    type = enumValueOf<TaskType>(taskType),
    completionMode = enumValueOf<TaskCompletionMode>(completionMode),
    prompt = prompt,
    customPresetId = customPresetId,
)

fun CustomTaskPreset.toEntity(): CustomTaskPresetEntity = CustomTaskPresetEntity(
    id = id,
    title = title,
    instruction = instruction,
    enabled = enabled,
    createdAtEpochMillis = createdAt.toEpochMilli(),
)

fun CustomTaskPresetEntity.toModel(): CustomTaskPreset = CustomTaskPreset(
    id = id,
    title = title,
    instruction = instruction,
    enabled = enabled,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

fun EmergencyEvent.toEntity(): EmergencyEventEntity = EmergencyEventEntity(
    id = id,
    reason = reason,
    activatedAtEpochMillis = activatedAt.toEpochMilli(),
    deactivatedAtEpochMillis = deactivatedAt?.toEpochMilli(),
    activationSource = activationSource.name,
    youtubeSecondsDuring = youtubeSecondsDuring,
)

fun EmergencyEventEntity.toModel(): EmergencyEvent = EmergencyEvent(
    id = id,
    reason = reason,
    activatedAt = Instant.ofEpochMilli(activatedAtEpochMillis),
    deactivatedAt = deactivatedAtEpochMillis?.let(Instant::ofEpochMilli),
    activationSource = enumValueOf<EmergencyActivationSource>(activationSource),
    youtubeSecondsDuring = youtubeSecondsDuring,
)
