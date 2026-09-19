package ir.courseplanner.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "course_sections",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["courseId"])]
)
data class CourseSection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseId: Long,
    val sectionCode: String,
    val instructor: String = "",
    val capacity: Int = 40,
    val examDate: String = "",
    val examStartTime: String = "",
    val examEndTime: String = "",
    val isEnrolled: Boolean = false
)
