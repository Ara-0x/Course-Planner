package ir.courseplanner.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.SectionWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface SectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSection(section: CourseSection): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<CourseSection>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ClassSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<ClassSession>): List<Long>

    @Transaction
    @Query("SELECT * FROM course_sections WHERE isEnrolled = 1")
    fun getEnrolledSections(): Flow<List<SectionWithDetails>>

    @Transaction
    @Query("SELECT * FROM course_sections")
    fun getAllSectionsWithDetails(): Flow<List<SectionWithDetails>>

    @Transaction
    @Query("SELECT * FROM course_sections WHERE id = :sectionId LIMIT 1")
    suspend fun getSectionWithDetailsById(sectionId: Long): SectionWithDetails?

    @Query("UPDATE course_sections SET isEnrolled = :isEnrolled WHERE id = :sectionId")
    suspend fun setSectionEnrolled(sectionId: Long, isEnrolled: Boolean)

    @Query("UPDATE course_sections SET isEnrolled = 0 WHERE courseId = :courseId")
    suspend fun unenrollAllForCourse(courseId: Long)

    @Query("UPDATE course_sections SET isEnrolled = 0")
    suspend fun clearAllEnrollments()

    @Query("DELETE FROM course_sections WHERE id = :sectionId")
    suspend fun deleteSectionById(sectionId: Long)

    @Query("DELETE FROM course_sections")
    suspend fun deleteAllSections()

    @Query("DELETE FROM class_sessions")
    suspend fun deleteAllSessions()
}
