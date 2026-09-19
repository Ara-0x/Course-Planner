package ir.courseplanner.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DocumentCategory(
    val titleFa: String,
    val iconName: String
) {
    PAMPHLET("جزوه کلاسی", "book"),
    SUMMARY("خلاصه درس", "summarize"),
    EXAM_SAMPLE("نمونه سوال", "quiz"),
    ASSIGNMENT("تمرین و پروژه", "assignment"),
    SLIDES("اسلاید و رفرنس", "slides"),
    NOTE("یادداشت مهم", "note")
}

@Entity(
    tableName = "course_documents",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["course_id"]),
        Index(value = ["category"])
    ]
)
data class CourseDocument(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "course_id")
    val courseId: Long,
    val title: String,
    val category: DocumentCategory = DocumentCategory.PAMPHLET,
    val contentNotes: String = "",
    val fileUri: String? = null,
    val fileName: String? = null,
    val fileSizeBytes: Long = 0,
    val isBookmarked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class DocumentWithCourse(
    val document: CourseDocument,
    val course: Course
)
