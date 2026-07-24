package com.filodot.noscroll.core.learning.model

import com.filodot.noscroll.core.model.TaskDifficulty
import java.time.Instant
import java.time.LocalDate

enum class CourseOrigin {
    MATERIAL,
    TOPIC,
}

enum class CourseStatus {
    DRAFT,
    READY,
    ACTIVE,
    COMPLETED,
    ARCHIVED,
}

enum class LearningSourceType {
    PDF,
    DOCX,
    PLAIN_TEXT,
    MARKDOWN,
    TOPIC,
    KNOWLEDGE_BASE,
}

enum class GroundingMode {
    /** Every factual activity must point to an imported source fragment. */
    SOURCE_REQUIRED,

    /** The model may use its own knowledge and the UI must disclose that fact. */
    AI_KNOWLEDGE,
}

data class LearningSource(
    val id: String,
    val courseId: String,
    val title: String,
    val type: LearningSourceType,
    val contentHash: String?,
    val importedAt: Instant,
)

data class SourceCitation(
    val sourceId: String,
    val chunkId: String,
    val pageNumber: Int? = null,
    val sectionTitle: String? = null,
)

data class LearningCourse(
    val id: String,
    val title: String,
    val description: String,
    val languageTag: String = "ru",
    val origin: CourseOrigin,
    val groundingMode: GroundingMode,
    val status: CourseStatus = CourseStatus.DRAFT,
    val planVersion: Int = 1,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class CurriculumNodeType {
    SECTION,
    TOPIC,
}

/**
 * Flat, ordered curriculum node. parentId forms an editable tree without coupling the domain to UI.
 */
data class CurriculumNode(
    val id: String,
    val courseId: String,
    val parentId: String?,
    val type: CurriculumNodeType,
    val title: String,
    val description: String,
    val position: Int,
    val estimatedMinutes: Int,
    val optional: Boolean = false,
    val conceptIds: List<String> = emptyList(),
)

data class LearningConcept(
    val id: String,
    val courseId: String,
    val title: String,
    val summary: String,
    val position: Int,
    val prerequisiteIds: Set<String> = emptySet(),
    val citations: List<SourceCitation> = emptyList(),
)

enum class ActivityKind {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    ORDERING,
    MATCHING,
    FILL_BLANK,
    SHORT_ANSWER,
    NUMERIC_ANSWER,
    FLASHCARD,
    EVIDENCE_SELECTION,
    SCENARIO,
    CODE_OUTPUT,
    CODE_COMPLETION,
    CODE_FIX,
    MINI_CODE,
    TEACH_BACK,
}

enum class CodeLanguage {
    PYTHON,
    SQL,
}

data class ChoiceOption(
    val id: String,
    val text: String,
)

data class OrderingItem(
    val id: String,
    val text: String,
)

data class MatchingItem(
    val id: String,
    val text: String,
)

data class MatchingPair(
    val leftId: String,
    val rightId: String,
)

data class CodeTestCase(
    val id: String,
    val input: String,
    val expectedOutput: String,
    val hidden: Boolean = false,
)

sealed interface ActivityContent {
    val kind: ActivityKind
}

data class SingleChoiceContent(
    val options: List<ChoiceOption>,
    val correctOptionId: String,
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.SINGLE_CHOICE
}

data class MultipleChoiceContent(
    val options: List<ChoiceOption>,
    val correctOptionIds: Set<String>,
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.MULTIPLE_CHOICE
}

data class TrueFalseContent(
    val statement: String,
    val expected: Boolean,
    val correction: String?,
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.TRUE_FALSE
}

data class OrderingContent(
    val items: List<OrderingItem>,
    val correctOrderIds: List<String>,
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.ORDERING
}

data class MatchingContent(
    val left: List<MatchingItem>,
    val right: List<MatchingItem>,
    val correctPairs: List<MatchingPair>,
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.MATCHING
}

data class FillBlankContent(
    /** The marker {{blank}} identifies the editable part. */
    val textWithBlank: String,
    val acceptedAnswers: Set<String>,
    val caseSensitive: Boolean = false,
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.FILL_BLANK
}

data class ShortAnswerContent(
    val acceptedAnswers: Set<String> = emptySet(),
    val rubric: String?,
    val caseSensitive: Boolean = false,
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.SHORT_ANSWER
}

data class NumericAnswerContent(
    val expected: Double,
    val tolerance: Double = 0.0,
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.NUMERIC_ANSWER
}

data class FlashcardContent(
    val answer: String,
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.FLASHCARD
}

data class EvidenceSelectionContent(
    val options: List<ChoiceOption>,
    val correctOptionIds: Set<String>,
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.EVIDENCE_SELECTION
}

data class ScenarioContent(
    val options: List<ChoiceOption>,
    val correctOptionId: String,
    val consequenceByOptionId: Map<String, String>,
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.SCENARIO
}

data class CodeOutputContent(
    val language: CodeLanguage,
    val code: String,
    val acceptedOutputs: Set<String>,
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.CODE_OUTPUT
}

data class CodeCompletionContent(
    val language: CodeLanguage,
    /** The marker {{code}} identifies the editable part. */
    val codeWithBlank: String,
    val acceptedSnippets: Set<String>,
    val tests: List<CodeTestCase> = emptyList(),
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.CODE_COMPLETION
}

data class CodeFixContent(
    val language: CodeLanguage,
    val brokenCode: String,
    val tests: List<CodeTestCase>,
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.CODE_FIX
}

data class MiniCodeContent(
    val language: CodeLanguage,
    val starterCode: String,
    val tests: List<CodeTestCase>,
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.MINI_CODE
}

data class TeachBackContent(
    val rubric: String,
    val keyPoints: List<String>,
) : ActivityContent {
    override val kind: ActivityKind = ActivityKind.TEACH_BACK
}

data class GenerationMetadata(
    val providerId: String,
    val modelId: String,
    val promptVersion: String,
    val generatedAt: Instant,
)

data class QualityReport(
    val correctness: Int,
    val grounding: Int,
    val clarity: Int,
    val pedagogy: Int,
    val formatValidity: Int,
    val reviewedAt: Instant,
) {
    val overall: Int
        get() = (
            correctness * 35 +
                grounding * 25 +
                clarity * 15 +
                pedagogy * 15 +
                formatValidity * 10
            ) / 100

    val accepted: Boolean
        get() = correctness >= 90 &&
            grounding >= 90 &&
            clarity >= 80 &&
            pedagogy >= 80 &&
            formatValidity >= 90 &&
            overall >= 85
}

data class LearningActivity(
    val id: String,
    val conceptIds: Set<String>,
    val prompt: String,
    val explanation: String,
    val difficulty: TaskDifficulty,
    val estimatedSeconds: Int,
    val content: ActivityContent,
    val citations: List<SourceCitation>,
    val generation: GenerationMetadata?,
    val quality: QualityReport,
)

enum class LessonPackageStatus {
    GENERATED,
    VALIDATED,
    QUARANTINED,
    CONSUMED,
}

data class LessonPackage(
    val id: String,
    val courseId: String,
    val curriculumNodeId: String,
    val planVersion: Int,
    val title: String,
    val introduction: String,
    val groundingMode: GroundingMode,
    val activities: List<LearningActivity>,
    val status: LessonPackageStatus,
    val generatedAt: Instant,
)

enum class AttemptResult {
    CORRECT,
    INCORRECT,
    REPLACED_AS_SUSPICIOUS,
}

enum class SelfConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

data class LearningAttempt(
    val id: String,
    val courseId: String,
    val lessonId: String,
    val activityId: String,
    val conceptIds: Set<String>,
    val activityKind: ActivityKind,
    val result: AttemptResult,
    val hintsUsed: Int,
    val durationSeconds: Int,
    val confidence: SelfConfidence,
    val occurredAt: Instant,
    val localDate: LocalDate,
)

data class ConceptMastery(
    val conceptId: String,
    val score: Int = 0,
    val attemptCount: Int = 0,
    val correctCount: Int = 0,
    val consecutiveCorrect: Int = 0,
    val successfulFormats: Set<ActivityKind> = emptySet(),
    val successfulDates: Set<LocalDate> = emptySet(),
    val lastAttemptAt: Instant? = null,
    val nextReviewAt: Instant? = null,
) {
    val mastered: Boolean
        get() = score >= 80 &&
            correctCount >= 3 &&
            successfulFormats.size >= 2 &&
            successfulDates.size >= 2
}
