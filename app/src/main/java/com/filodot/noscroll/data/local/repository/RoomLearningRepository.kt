package com.filodot.noscroll.data.local.repository

import android.util.Log
import com.filodot.noscroll.core.contracts.LearningRepository
import com.filodot.noscroll.core.learning.model.ConceptMastery
import com.filodot.noscroll.core.learning.model.LearningAttempt
import com.filodot.noscroll.core.learning.model.LearningCourse
import com.filodot.noscroll.core.learning.model.LearningCourseContent
import com.filodot.noscroll.core.learning.model.LessonPackage
import com.filodot.noscroll.core.learning.model.LessonPackageStatus
import com.filodot.noscroll.core.learning.quality.LessonQualityValidator
import com.filodot.noscroll.data.local.room.LearningDao
import com.filodot.noscroll.data.local.room.toActivityEntities
import com.filodot.noscroll.data.local.room.toEntity
import com.filodot.noscroll.data.local.room.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomLearningRepository(
    private val dao: LearningDao,
    private val validator: LessonQualityValidator = LessonQualityValidator(),
) : LearningRepository {
    override val courses: Flow<List<LearningCourse>> = dao.observeCourses()
        .map { entities ->
            entities.mapNotNull { entity ->
                runCatching(entity::toModel).getOrElse { error ->
                    Log.w(LOG_TAG, "Ignored invalid learning_course ${entity.id}", error)
                    null
                }
            }
        }

    override suspend fun saveCourseContent(content: LearningCourseContent) {
        require(content.sources.all { it.courseId == content.course.id })
        require(content.sourceChunks.all { it.courseId == content.course.id })
        require(content.sourceChunks.all { chunk -> content.sources.any { it.id == chunk.sourceId } })
        require(content.curriculumNodes.all { it.courseId == content.course.id })
        require(content.concepts.all { it.courseId == content.course.id })
        dao.saveCourseContent(
            course = content.course.toEntity(),
            sources = content.sources.map { it.toEntity() },
            sourceChunks = content.sourceChunks.map { it.toEntity() },
            nodes = content.curriculumNodes.map { it.toEntity() },
            concepts = content.concepts.map { it.toEntity() },
        )
    }

    override suspend fun getCourseContent(courseId: String): LearningCourseContent? {
        val course = dao.getCourse(courseId)?.let { entity ->
            runCatching(entity::toModel).getOrNull()
        } ?: return null
        return LearningCourseContent(
            course = course,
            sources = dao.getSources(courseId).mapNotNull { entity ->
                runCatching(entity::toModel).getOrNull()
            },
            sourceChunks = dao.getSourceChunks(courseId).mapNotNull { entity ->
                runCatching(entity::toModel).getOrNull()
            },
            curriculumNodes = dao.getCurriculumNodes(courseId).mapNotNull { entity ->
                runCatching(entity::toModel).getOrNull()
            },
            concepts = dao.getConcepts(courseId).mapNotNull { entity ->
                runCatching(entity::toModel).getOrNull()
            },
        )
    }

    override suspend fun saveLesson(lesson: LessonPackage) {
        if (lesson.status == LessonPackageStatus.VALIDATED) {
            val result = validator.validate(lesson)
            require(result.isValid) { "Validated lesson failed checks: ${result.issues}" }
        }
        dao.saveLesson(lesson.toEntity(), lesson.toActivityEntities())
    }

    override suspend fun getLesson(lessonId: String): LessonPackage? =
        dao.getLesson(lessonId)?.let { entity ->
            runCatching { entity.toModel(dao.getActivities(entity.id)) }
                .getOrElse { error ->
                    Log.w(LOG_TAG, "Ignored invalid lesson_package ${entity.id}", error)
                    null
                }
        }

    override suspend fun peekNextLesson(courseId: String): LessonPackage? =
        dao.getNextValidatedLesson(courseId)?.let { entity ->
            runCatching { entity.toModel(dao.getActivities(entity.id)) }
                .getOrElse { error ->
                    Log.w(LOG_TAG, "Ignored invalid lesson_package ${entity.id}", error)
                    null
                }
        }

    override suspend fun takeNextLesson(courseId: String): LessonPackage? =
        dao.takeNextValidatedLesson(courseId)?.let { entity ->
            runCatching { entity.toModel(dao.getActivities(entity.id)) }
                .getOrElse { error ->
                    Log.w(LOG_TAG, "Ignored invalid lesson_package ${entity.id}", error)
                    null
                }
        }

    override suspend fun getValidatedLessons(courseId: String): List<LessonPackage> =
        dao.getValidatedLessons(courseId).mapNotNull { entity ->
            runCatching { entity.toModel(dao.getActivities(entity.id)) }
                .getOrElse { error ->
                    Log.w(LOG_TAG, "Ignored invalid lesson_package ${entity.id}", error)
                    null
                }
        }

    override fun observeValidatedLessonCount(courseId: String): Flow<Int> =
        dao.observeValidatedLessonCount(courseId)

    override suspend fun saveAttempt(attempt: LearningAttempt) {
        dao.upsertAttempt(attempt.toEntity())
    }

    override suspend fun getAttempts(courseId: String): List<LearningAttempt> =
        dao.getAttempts(courseId).mapNotNull { entity ->
            runCatching(entity::toModel).getOrNull()
        }

    override suspend fun saveMastery(mastery: ConceptMastery) {
        dao.upsertMastery(mastery.toEntity())
    }

    override suspend fun getMastery(courseId: String): List<ConceptMastery> =
        dao.getMastery(courseId).mapNotNull { entity ->
            runCatching(entity::toModel).getOrNull()
        }

    override suspend fun deleteCourse(courseId: String) {
        dao.deleteCourse(courseId)
    }

    private companion object {
        const val LOG_TAG = "NoScrollLearning"
    }
}
