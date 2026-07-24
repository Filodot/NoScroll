package com.filodot.noscroll.core.learning.content

import com.filodot.noscroll.core.learning.model.ActivityKind
import com.filodot.noscroll.core.learning.model.ChoiceOption
import com.filodot.noscroll.core.learning.model.CodeLanguage
import com.filodot.noscroll.core.learning.model.CodeOutputContent
import com.filodot.noscroll.core.learning.model.CourseOrigin
import com.filodot.noscroll.core.learning.model.CourseStatus
import com.filodot.noscroll.core.learning.model.CurriculumNode
import com.filodot.noscroll.core.learning.model.CurriculumNodeType
import com.filodot.noscroll.core.learning.model.GenerationMetadata
import com.filodot.noscroll.core.learning.model.GroundingMode
import com.filodot.noscroll.core.learning.model.LearningActivity
import com.filodot.noscroll.core.learning.model.LearningConcept
import com.filodot.noscroll.core.learning.model.LearningCourse
import com.filodot.noscroll.core.learning.model.LessonPackage
import com.filodot.noscroll.core.learning.model.LessonPackageStatus
import com.filodot.noscroll.core.learning.model.OrderingContent
import com.filodot.noscroll.core.learning.model.OrderingItem
import com.filodot.noscroll.core.learning.model.QualityReport
import com.filodot.noscroll.core.learning.model.SingleChoiceContent
import com.filodot.noscroll.core.model.TaskDifficulty
import java.time.Instant

/**
 * Offline fixtures prove the complete learning contract before external providers are connected.
 */
object StaticLearningCatalog {
    private val generatedAt = Instant.parse("2026-07-24T08:00:00Z")
    private val quality = QualityReport(
        correctness = 100,
        grounding = 100,
        clarity = 100,
        pedagogy = 100,
        formatValidity = 100,
        reviewedAt = generatedAt,
    )
    private val metadata = GenerationMetadata(
        providerId = "local",
        modelId = "static-catalog-v1",
        promptVersion = "static-v1",
        generatedAt = generatedAt,
    )

    val pythonCourse = LearningCourse(
        id = "course-python-basics",
        title = "Основы Python",
        description = "Короткий тестовый курс для проверки образовательного движка.",
        origin = CourseOrigin.TOPIC,
        groundingMode = GroundingMode.AI_KNOWLEDGE,
        status = CourseStatus.ACTIVE,
        createdAt = generatedAt,
        updatedAt = generatedAt,
    )

    val variablesConcept = LearningConcept(
        id = "python-variables",
        courseId = pythonCourse.id,
        title = "Переменные",
        summary = "Имена связываются со значениями с помощью оператора присваивания.",
        position = 0,
    )

    val expressionsConcept = LearningConcept(
        id = "python-expressions",
        courseId = pythonCourse.id,
        title = "Выражения",
        summary = "Операторы комбинируют значения и возвращают результат.",
        position = 1,
        prerequisiteIds = setOf(variablesConcept.id),
    )

    val firstTopic = CurriculumNode(
        id = "python-topic-variables",
        courseId = pythonCourse.id,
        parentId = null,
        type = CurriculumNodeType.TOPIC,
        title = "Переменные и выражения",
        description = "Первый микроурок курса.",
        position = 0,
        estimatedMinutes = 4,
        conceptIds = listOf(variablesConcept.id, expressionsConcept.id),
    )

    val firstLesson = LessonPackage(
        id = "python-lesson-variables-1",
        courseId = pythonCourse.id,
        curriculumNodeId = firstTopic.id,
        planVersion = pythonCourse.planVersion,
        title = "Переменные: первое знакомство",
        introduction = "Переменная хранит ссылку на значение, которому мы даём понятное имя.",
        groundingMode = GroundingMode.AI_KNOWLEDGE,
        activities = listOf(
            LearningActivity(
                id = "python-variable-choice",
                conceptIds = setOf(variablesConcept.id),
                prompt = "Какая строка присваивает переменной age значение 18?",
                explanation = "В Python присваивание записывается как имя, знак = и значение.",
                difficulty = TaskDifficulty.EASY,
                estimatedSeconds = 45,
                content = SingleChoiceContent(
                    options = listOf(
                        ChoiceOption("a", "age = 18"),
                        ChoiceOption("b", "18 = age"),
                        ChoiceOption("c", "age == 18"),
                    ),
                    correctOptionId = "a",
                ),
                citations = emptyList(),
                generation = metadata,
                quality = quality,
            ),
            LearningActivity(
                id = "python-variable-order",
                conceptIds = setOf(variablesConcept.id, expressionsConcept.id),
                prompt = "Расположите действия программы в порядке выполнения.",
                explanation = "Сначала создаются значения, затем вычисляется выражение и выводится результат.",
                difficulty = TaskDifficulty.MEDIUM,
                estimatedSeconds = 70,
                content = OrderingContent(
                    items = listOf(
                        OrderingItem("first", "x = 2"),
                        OrderingItem("second", "y = x + 3"),
                        OrderingItem("third", "print(y)"),
                    ),
                    correctOrderIds = listOf("first", "second", "third"),
                ),
                citations = emptyList(),
                generation = metadata,
                quality = quality,
            ),
            LearningActivity(
                id = "python-variable-output",
                conceptIds = setOf(variablesConcept.id, expressionsConcept.id),
                prompt = "Что выведет этот код?",
                explanation = "После присваивания x равно 4, поэтому выражение x * 2 даёт 8.",
                difficulty = TaskDifficulty.MEDIUM,
                estimatedSeconds = 55,
                content = CodeOutputContent(
                    language = CodeLanguage.PYTHON,
                    code = "x = 4\nprint(x * 2)",
                    acceptedOutputs = setOf("8"),
                ),
                citations = emptyList(),
                generation = metadata,
                quality = quality,
            ),
        ),
        status = LessonPackageStatus.VALIDATED,
        generatedAt = generatedAt,
    )

    val supportedActivityKinds: Set<ActivityKind> = ActivityKind.entries.toSet()
}
