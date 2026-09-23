package ir.courseplanner.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseWithSections
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    @Query("SELECT * FROM courses ORDER BY code ASC")
    fun getAllCourses(): Flow<List<Course>>

    @Query("""
        SELECT * FROM courses 
        WHERE code LIKE '%' || :query || '%' 
           OR name LIKE '%' || :query || '%' 
           OR department LIKE '%' || :query || '%'
        ORDER BY code ASC
    """)
    fun searchCourses(query: String): Flow<List<Course>>

    @Transaction
    @Query("SELECT * FROM courses ORDER BY code ASC")
    fun getCoursesWithSections(): Flow<List<CourseWithSections>>

    @Transaction
    @Query("SELECT * FROM courses WHERE isSelectedForGeneration = 1 ORDER BY code ASC")
    fun getCoursesSelectedForGeneration(): Flow<List<CourseWithSections>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: Course): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<Course>): List<Long>

    @Update
    suspend fun updateCourse(course: Course)

    @Delete
    suspend fun deleteCourse(course: Course)

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourseById(courseId: Long)

    // Portal re-imports replace same-code courses (cascades to sections/sessions),
    // so importing the same file twice never creates duplicates.
    @Query("DELETE FROM courses WHERE code = :code")
    suspend fun deleteCourseByCode(code: String)

    @Query("UPDATE courses SET isSelectedForGeneration = :isSelected WHERE id = :courseId")
    suspend fun setCourseSelectedForGeneration(courseId: Long, isSelected: Boolean)

    @Query("UPDATE courses SET isSelectedForGeneration = 0")
    suspend fun clearAllSelectedForGeneration()

    @Query("DELETE FROM courses")
    suspend fun deleteAllCourses()

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun getCourseCount(): Int
}
