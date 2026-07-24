package com.filodot.noscroll.data.local.repository

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
        .map { entities -> entities.map { it.toModel() } }

    override suspend fun saveCourseContent(content: LearningCourseContent) {
        require(content.sources.all { it.courseId == content.course.id })
        require(content.curriculumNodes.all { it.courseId == content.course.id })
        require(content.concepts.all { it.courseId == content.course.id })
        dao.saveCourseContent(
            course = content.course.toEntity(),
            sources = content.sources.map { it.toEntity() },
            nodes = content.curriculumNodes.map { it.toEntity() },
            concepts = content.concepts.map { it.toEntity() },
        )
    }

    override suspend fun getCourseContent(courseId: String): LearningCourseContent? {
        val course = dao.getCourse(courseId)?.toModel() ?: return null
        return LearningCourseContent(
            course = course,
            sources = dao.getSources(courseId).map { it.toModel() },
            curriculumNodes = dao.getCurriculumNodes(courseId).map { it.toModel() },
            concepts = dao.getConcepts(courseId).map { it.toModel() },
        )
    }

    override suspend fun saveLesson(lesson: LessonPackage) {
        if (lesson.status == LessonPackageStatus.VALIDATED) {
            val result = validator.validate(lesson)
            require(result.isValid) { "Validated lesson failed checks: ${result.issues}" }
        }
        dao.saveLesson(lesson.toEntity(), lesson.toActivityEntities())
    }

    override suspend fun peekNextLesson(courseId: String): LessonPackage? =
        dao.getNextValidatedLesson(courseId)?.let { entity ->
            entity.toModel(dao.getActivities(entity.id))
        }

    override suspend fun takeNextLesson(courseId: String): LessonPackage? =
        dao.takeNextValidatedLesson(courseId)?.let { entity ->
            entity.toModel(dao.getActivities(entity.id))
        }

    override fun observeValidatedLessonCount(courseId: String): Flow<Int> =
        dao.observeValidatedLessonCount(courseId)

    override suspend fun saveAttempt(attempt: LearningAttempt) {
        dao.upsertAttempt(attempt.toEntity())
    }

    override suspend fun getAttempts(courseId: String): List<LearningAttempt> =
        dao.getAttempts(courseId).map { it.toModel() }

    override suspend fun saveMastery(mastery: ConceptMastery) {
        dao.upsertMastery(mastery.toEntity())
    }

    override suspend fun getMastery(courseId: String): List<ConceptMastery> =
        dao.getMastery(courseId).map { it.toModel() }

    override suspend fun deleteCourse(courseId: String) {
        dao.deleteCourse(courseId)
    }
}
