package com.filodot.noscroll.data.local.room

import com.filodot.noscroll.core.model.ArithmeticOperation
import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.EmergencyActivationSource
import com.filodot.noscroll.core.model.EmergencyEvent
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.model.PendingTask
import java.time.Instant
import java.time.LocalDate

fun DailyUsage.toEntity(): DailyUsageEntity = DailyUsageEntity(
    localDate = localDate.toString(),
    youtubeSeconds = youtubeSeconds,
    shortsSeconds = shortsSeconds,
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
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

fun GateCycleEntity.toModel(): GateCycle = GateCycle(
    id = id,
    localDate = LocalDate.parse(localDate),
    usedSeconds = usedSeconds,
    pendingTaskId = pendingTaskId,
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
