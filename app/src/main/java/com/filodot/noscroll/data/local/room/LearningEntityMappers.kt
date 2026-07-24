package com.filodot.noscroll.data.local.room

import com.filodot.noscroll.core.learning.model.ActivityKind
import com.filodot.noscroll.core.learning.model.AttemptResult
import com.filodot.noscroll.core.learning.model.ConceptMastery
import com.filodot.noscroll.core.learning.model.CourseOrigin
import com.filodot.noscroll.core.learning.model.CourseStatus
import com.filodot.noscroll.core.learning.model.CurriculumNode
import com.filodot.noscroll.core.learning.model.CurriculumNodeType
import com.filodot.noscroll.core.learning.model.GenerationMetadata
import com.filodot.noscroll.core.learning.model.GroundingMode
import com.filodot.noscroll.core.learning.model.LearningActivity
import com.filodot.noscroll.core.learning.model.LearningAttempt
import com.filodot.noscroll.core.learning.model.LearningConcept
import com.filodot.noscroll.core.learning.model.LearningCourse
import com.filodot.noscroll.core.learning.model.LearningSource
import com.filodot.noscroll.core.learning.model.LearningSourceType
import com.filodot.noscroll.core.learning.model.LessonPackage
import com.filodot.noscroll.core.learning.model.LessonPackageStatus
import com.filodot.noscroll.core.learning.model.QualityReport
import com.filodot.noscroll.core.learning.model.SelfConfidence
import com.filodot.noscroll.core.model.TaskDifficulty
import java.time.Instant
import java.time.LocalDate

fun LearningCourse.toEntity(): LearningCourseEntity = LearningCourseEntity(
    id = id,
    title = title,
    description = description,
    languageTag = languageTag,
    origin = origin.name,
    groundingMode = groundingMode.name,
    status = status.name,
    planVersion = planVersion,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

fun LearningCourseEntity.toModel(): LearningCourse = LearningCourse(
    id = id,
    title = title,
    description = description,
    languageTag = languageTag,
    origin = enumValueOf<CourseOrigin>(origin),
    groundingMode = enumValueOf<GroundingMode>(groundingMode),
    status = enumValueOf<CourseStatus>(status),
    planVersion = planVersion,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

fun LearningSource.toEntity(): LearningSourceEntity = LearningSourceEntity(
    id = id,
    courseId = courseId,
    title = title,
    sourceType = type.name,
    contentHash = contentHash,
    importedAtEpochMillis = importedAt.toEpochMilli(),
)

fun LearningSourceEntity.toModel(): LearningSource = LearningSource(
    id = id,
    courseId = courseId,
    title = title,
    type = enumValueOf<LearningSourceType>(sourceType),
    contentHash = contentHash,
    importedAt = Instant.ofEpochMilli(importedAtEpochMillis),
)

fun CurriculumNode.toEntity(): CurriculumNodeEntity = CurriculumNodeEntity(
    id = id,
    courseId = courseId,
    parentId = parentId,
    nodeType = type.name,
    title = title,
    description = description,
    position = position,
    estimatedMinutes = estimatedMinutes,
    optional = optional,
    conceptIdsJson = LearningJsonCodec.encodeStrings(conceptIds),
)

fun CurriculumNodeEntity.toModel(): CurriculumNode = CurriculumNode(
    id = id,
    courseId = courseId,
    parentId = parentId,
    type = enumValueOf<CurriculumNodeType>(nodeType),
    title = title,
    description = description,
    position = position,
    estimatedMinutes = estimatedMinutes,
    optional = optional,
    conceptIds = LearningJsonCodec.decodeStringList(conceptIdsJson),
)

fun LearningConcept.toEntity(): LearningConceptEntity = LearningConceptEntity(
    id = id,
    courseId = courseId,
    title = title,
    summary = summary,
    position = position,
    prerequisiteIdsJson = LearningJsonCodec.encodeStrings(prerequisiteIds),
    citationsJson = LearningJsonCodec.encodeCitations(citations),
)

fun LearningConceptEntity.toModel(): LearningConcept = LearningConcept(
    id = id,
    courseId = courseId,
    title = title,
    summary = summary,
    position = position,
    prerequisiteIds = LearningJsonCodec.decodeStringSet(prerequisiteIdsJson),
    citations = LearningJsonCodec.decodeCitations(citationsJson),
)

fun LessonPackage.toEntity(): LessonPackageEntity = LessonPackageEntity(
    id = id,
    courseId = courseId,
    curriculumNodeId = curriculumNodeId,
    planVersion = planVersion,
    title = title,
    introduction = introduction,
    groundingMode = groundingMode.name,
    status = status.name,
    generatedAtEpochMillis = generatedAt.toEpochMilli(),
)

fun LessonPackage.toActivityEntities(): List<LearningActivityEntity> =
    activities.mapIndexed { index, activity -> activity.toEntity(id, index) }

fun LessonPackageEntity.toModel(
    activities: List<LearningActivityEntity>,
): LessonPackage = LessonPackage(
    id = id,
    courseId = courseId,
    curriculumNodeId = curriculumNodeId,
    planVersion = planVersion,
    title = title,
    introduction = introduction,
    groundingMode = enumValueOf<GroundingMode>(groundingMode),
    activities = activities.sortedBy(LearningActivityEntity::position).map { it.toModel() },
    status = enumValueOf<LessonPackageStatus>(status),
    generatedAt = Instant.ofEpochMilli(generatedAtEpochMillis),
)

private fun LearningActivity.toEntity(
    lessonId: String,
    position: Int,
): LearningActivityEntity = LearningActivityEntity(
    id = id,
    lessonId = lessonId,
    position = position,
    conceptIdsJson = LearningJsonCodec.encodeStrings(conceptIds),
    prompt = prompt,
    explanation = explanation,
    difficulty = difficulty.name,
    estimatedSeconds = estimatedSeconds,
    activityKind = content.kind.name,
    contentJson = LearningJsonCodec.encodeContent(content),
    citationsJson = LearningJsonCodec.encodeCitations(citations),
    providerId = generation?.providerId,
    modelId = generation?.modelId,
    promptVersion = generation?.promptVersion,
    generatedAtEpochMillis = generation?.generatedAt?.toEpochMilli(),
    correctness = quality.correctness,
    grounding = quality.grounding,
    clarity = quality.clarity,
    pedagogy = quality.pedagogy,
    formatValidity = quality.formatValidity,
    reviewedAtEpochMillis = quality.reviewedAt.toEpochMilli(),
)

private fun LearningActivityEntity.toModel(): LearningActivity {
    val kind = enumValueOf<ActivityKind>(activityKind)
    return LearningActivity(
        id = id,
        conceptIds = LearningJsonCodec.decodeStringSet(conceptIdsJson),
        prompt = prompt,
        explanation = explanation,
        difficulty = enumValueOf<TaskDifficulty>(difficulty),
        estimatedSeconds = estimatedSeconds,
        content = LearningJsonCodec.decodeContent(kind, contentJson),
        citations = LearningJsonCodec.decodeCitations(citationsJson),
        generation = providerId?.let { provider ->
            GenerationMetadata(
                providerId = provider,
                modelId = requireNotNull(modelId),
                promptVersion = requireNotNull(promptVersion),
                generatedAt = Instant.ofEpochMilli(requireNotNull(generatedAtEpochMillis)),
            )
        },
        quality = QualityReport(
            correctness = correctness,
            grounding = grounding,
            clarity = clarity,
            pedagogy = pedagogy,
            formatValidity = formatValidity,
            reviewedAt = Instant.ofEpochMilli(reviewedAtEpochMillis),
        ),
    )
}

fun LearningAttempt.toEntity(): LearningAttemptEntity = LearningAttemptEntity(
    id = id,
    courseId = courseId,
    lessonId = lessonId,
    activityId = activityId,
    conceptIdsJson = LearningJsonCodec.encodeStrings(conceptIds),
    activityKind = activityKind.name,
    result = result.name,
    hintsUsed = hintsUsed,
    durationSeconds = durationSeconds,
    confidence = confidence.name,
    occurredAtEpochMillis = occurredAt.toEpochMilli(),
    localDate = localDate.toString(),
)

fun LearningAttemptEntity.toModel(): LearningAttempt = LearningAttempt(
    id = id,
    courseId = courseId,
    lessonId = lessonId,
    activityId = activityId,
    conceptIds = LearningJsonCodec.decodeStringSet(conceptIdsJson),
    activityKind = enumValueOf<ActivityKind>(activityKind),
    result = enumValueOf<AttemptResult>(result),
    hintsUsed = hintsUsed,
    durationSeconds = durationSeconds,
    confidence = enumValueOf<SelfConfidence>(confidence),
    occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
    localDate = LocalDate.parse(localDate),
)

fun ConceptMastery.toEntity(): ConceptMasteryEntity = ConceptMasteryEntity(
    conceptId = conceptId,
    score = score,
    attemptCount = attemptCount,
    correctCount = correctCount,
    consecutiveCorrect = consecutiveCorrect,
    successfulFormatsJson = LearningJsonCodec.encodeStrings(
        successfulFormats.map(ActivityKind::name),
    ),
    successfulDatesJson = LearningJsonCodec.encodeStrings(
        successfulDates.map(LocalDate::toString),
    ),
    lastAttemptAtEpochMillis = lastAttemptAt?.toEpochMilli(),
    nextReviewAtEpochMillis = nextReviewAt?.toEpochMilli(),
)

fun ConceptMasteryEntity.toModel(): ConceptMastery = ConceptMastery(
    conceptId = conceptId,
    score = score,
    attemptCount = attemptCount,
    correctCount = correctCount,
    consecutiveCorrect = consecutiveCorrect,
    successfulFormats = LearningJsonCodec.decodeStringSet(successfulFormatsJson)
        .mapTo(mutableSetOf()) { enumValueOf<ActivityKind>(it) },
    successfulDates = LearningJsonCodec.decodeStringSet(successfulDatesJson)
        .mapTo(mutableSetOf(), LocalDate::parse),
    lastAttemptAt = lastAttemptAtEpochMillis?.let(Instant::ofEpochMilli),
    nextReviewAt = nextReviewAtEpochMillis?.let(Instant::ofEpochMilli),
)
