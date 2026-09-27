package ir.courseplanner.app.data.repository

import androidx.room.withTransaction
import ir.courseplanner.app.data.importer.ImportItem
import ir.courseplanner.app.data.local.AppDatabase
import ir.courseplanner.app.data.local.CourseDao
import ir.courseplanner.app.data.local.CourseDocumentDao
import ir.courseplanner.app.data.local.SectionDao
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseDocument
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.CourseWithSections
import ir.courseplanner.app.data.model.SectionWithDetails
import kotlinx.coroutines.flow.Flow

class CourseRepository(
    private val db: AppDatabase,
    private val courseDao: CourseDao,
    private val sectionDao: SectionDao,
    private val documentDao: CourseDocumentDao
) {
    val allCourses: Flow<List<Course>> = courseDao.getAllCourses()
    val coursesWithSections: Flow<List<CourseWithSections>> = courseDao.getCoursesWithSections()
    val selectedForGeneration: Flow<List<CourseWithSections>> = courseDao.getCoursesSelectedForGeneration()
    val enrolledSections: Flow<List<SectionWithDetails>> = sectionDao.getEnrolledSections()
    val allSectionsWithDetails: Flow<List<SectionWithDetails>> = sectionDao.getAllSectionsWithDetails()
    val allDocuments: Flow<List<CourseDocument>> = documentDao.getAllDocuments()

    fun getDocumentsByCourse(courseId: Long): Flow<List<CourseDocument>> {
        return documentDao.getDocumentsByCourse(courseId)
    }

    suspend fun insertDocument(document: CourseDocument): Long {
        return documentDao.insertDocument(document)
    }

    suspend fun updateDocument(document: CourseDocument) {
        documentDao.updateDocument(document)
    }

    suspend fun deleteDocument(id: Long) {
        documentDao.deleteDocumentById(id)
    }

    suspend fun toggleDocumentBookmark(id: Long, isBookmarked: Boolean) {
        documentDao.setBookmarked(id, isBookmarked)
    }

    fun searchCourses(query: String): Flow<List<Course>> {
        return courseDao.searchCourses(query)
    }

    suspend fun getCourseCount(): Int {
        return courseDao.getCourseCount()
    }

    /** Whole import runs in one transaction: all-or-nothing, never partial. */
    suspend fun importItems(items: List<ImportItem>, clearExisting: Boolean = false) {
        db.withTransaction {
            if (clearExisting) {
                clearAllData()
            }
            insertAll(items)
        }
    }

    /**
     * Portal catalog import with UPSERT sync per course code (one transaction):
     * - New course → plain insert.
     * - Known course → catalog fields updated, but user-owned state
     *   (`isSelectedForGeneration`, section `isEnrolled`) is preserved, so a
     *   re-import never wipes enrollments, picks, or documents (ids are kept).
     * - Imported section matched by sectionCode → fields updated, sessions replaced.
     * - Stale sections (gone from the portal) are deleted ONLY when not enrolled;
     *   an enrolled section the user chose is always kept.
     */
    suspend fun importPortalItems(items: List<ImportItem>, clearExisting: Boolean = false) {
        db.withTransaction {
            if (clearExisting) {
                clearAllData()
                insertAll(items)
                return@withTransaction
            }
            for (item in items) {
                syncCourse(item)
            }
        }
    }

    private suspend fun insertAll(items: List<ImportItem>) {
        for (item in items) {
            val courseId = courseDao.insertCourse(item.course)
            for (secItem in item.sections) {
                val secWithCourse = secItem.section.copy(courseId = courseId)
                val sectionId = sectionDao.insertSection(secWithCourse)
                val sessionsWithSec = secItem.sessions.map { it.copy(sectionId = sectionId) }
                sectionDao.insertSessions(sessionsWithSec)
            }
        }
    }

    private suspend fun syncCourse(item: ImportItem) {
        val existing = courseDao.getCourseByCode(item.course.code)
        if (existing == null) {
            insertAll(listOf(item))
            return
        }
        // Catalog fields refresh; the user's own flags survive the re-import.
        courseDao.updateCourse(
            item.course.copy(
                id = existing.id,
                isSelectedForGeneration = existing.isSelectedForGeneration
            )
        )
        val existingSections = sectionDao.getSectionsByCourseId(existing.id)
            .associateBy { it.sectionCode }
        val importedCodes = mutableSetOf<String>()
        for (secItem in item.sections) {
            importedCodes.add(secItem.section.sectionCode)
            val old = existingSections[secItem.section.sectionCode]
            if (old == null) {
                val sectionId = sectionDao.insertSection(secItem.section.copy(courseId = existing.id))
                sectionDao.insertSessions(secItem.sessions.map { it.copy(sectionId = sectionId) })
            } else {
                sectionDao.updateSection(
                    secItem.section.copy(
                        id = old.id,
                        courseId = existing.id,
                        isEnrolled = old.isEnrolled
                    )
                )
                sectionDao.deleteSessionsBySectionId(old.id)
                sectionDao.insertSessions(secItem.sessions.map { it.copy(sectionId = old.id) })
            }
        }
        // Drop stale catalog-only sections; never touch the user's enrolled pick.
        for ((code, old) in existingSections) {
            if (code !in importedCodes && !old.isEnrolled) {
                sectionDao.deleteSectionById(old.id)
            }
        }
    }

    /**
     * Inserts a manually created course along with its first section and class sessions.
     */
    suspend fun addManualCourse(
        course: Course,
        section: CourseSection,
        sessions: List<ClassSession>
    ): Long = db.withTransaction {
        val courseId = courseDao.insertCourse(course)
        val sectionId = sectionDao.insertSection(section.copy(courseId = courseId))
        val sessionsWithSec = sessions.map { it.copy(sectionId = sectionId) }
        sectionDao.insertSessions(sessionsWithSec)
        courseId
    }

    /**
     * Adds an additional section/group to an existing course.
     */
    suspend fun addSectionToCourse(
        courseId: Long,
        section: CourseSection,
        sessions: List<ClassSession>
    ): Long = db.withTransaction {
        val sectionId = sectionDao.insertSection(section.copy(courseId = courseId))
        val sessionsWithSec = sessions.map { it.copy(sectionId = sectionId) }
        sectionDao.insertSessions(sessionsWithSec)
        sectionId
    }

    suspend fun deleteCourse(courseId: Long) {
        courseDao.deleteCourseById(courseId)
    }

    sealed interface UpdateCourseResult {
        data object Success : UpdateCourseResult
        data object DuplicateCode : UpdateCourseResult
        data object NotFound : UpdateCourseResult
    }

    /**
     * Updates a course's own fields (name/code/department/credits) in place.
     * Sections, enrollments, generator flag and documents are untouched, so the
     * user never has to delete and re-add a course to fix a typo. Runs in a
     * transaction together with the duplicate-code check.
     */
    suspend fun updateCourseDetails(
        courseId: Long,
        name: String,
        code: String,
        department: String,
        credits: Int
    ): UpdateCourseResult = db.withTransaction {
        val current = courseDao.getCourseById(courseId) ?: return@withTransaction UpdateCourseResult.NotFound
        val clash = courseDao.getCourseByCode(code)
        if (clash != null && clash.id != courseId) {
            return@withTransaction UpdateCourseResult.DuplicateCode
        }
        courseDao.updateCourse(
            current.copy(name = name, code = code, department = department, credits = credits)
        )
        UpdateCourseResult.Success
    }

    suspend fun deleteSection(sectionId: Long) {
        sectionDao.deleteSectionById(sectionId)
    }

    sealed interface UpdateSectionResult {
        data object Success : UpdateSectionResult
        data object DuplicateCode : UpdateSectionResult
        data object NotFound : UpdateSectionResult
    }

    /**
     * Updates a section/group in place: code, instructor, exam fields plus the
     * full session list (replaced atomically). Enrollment flag is preserved so
     * editing an enrolled group never drops it from the weekly program.
     */
    suspend fun updateSectionDetails(
        sectionId: Long,
        sectionCode: String,
        instructor: String,
        examDate: String,
        examStartTime: String,
        examEndTime: String,
        sessions: List<ClassSession>
    ): UpdateSectionResult = db.withTransaction {
        val current = sectionDao.getSectionById(sectionId)
            ?: return@withTransaction UpdateSectionResult.NotFound
        val clash = sectionDao.getSectionByCourseAndCode(current.courseId, sectionCode.trim())
        if (clash != null && clash.id != sectionId) {
            return@withTransaction UpdateSectionResult.DuplicateCode
        }
        sectionDao.updateSection(
            current.copy(
                sectionCode = sectionCode.trim(),
                instructor = instructor.trim(),
                examDate = examDate.trim(),
                examStartTime = examStartTime.trim(),
                examEndTime = examEndTime.trim()
            )
        )
        sectionDao.deleteSessionsBySectionId(sectionId)
        sectionDao.insertSessions(sessions.map { it.copy(id = 0, sectionId = sectionId) })
        UpdateSectionResult.Success
    }

    suspend fun toggleCourseSelectedForGeneration(courseId: Long, isSelected: Boolean) {
        courseDao.setCourseSelectedForGeneration(courseId, isSelected)
    }

    suspend fun setSectionEnrolled(section: SectionWithDetails, isEnrolled: Boolean) {
        db.withTransaction {
            if (isEnrolled) {
                // Unenroll other sections of the same course so only 1 section is enrolled per course
                sectionDao.unenrollAllForCourse(section.course.id)
                sectionDao.setSectionEnrolled(section.section.id, true)
            } else {
                sectionDao.setSectionEnrolled(section.section.id, false)
            }
        }
    }

    suspend fun applySchedule(sections: List<SectionWithDetails>) {
        db.withTransaction {
            sectionDao.clearAllEnrollments()
            for (sec in sections) {
                sectionDao.setSectionEnrolled(sec.section.id, true)
            }
        }
    }

    suspend fun clearEnrollments() {
        sectionDao.clearAllEnrollments()
    }

    suspend fun clearAllData() {
        db.withTransaction {
            documentDao.deleteAllDocuments()
            sectionDao.deleteAllSessions()
            sectionDao.deleteAllSections()
            courseDao.deleteAllCourses()
        }
    }
}
