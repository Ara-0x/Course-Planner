package ir.courseplanner.app.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class SectionWithDetails(
    @Embedded
    val section: CourseSection,

    @Relation(
        parentColumn = "courseId",
        entityColumn = "id"
    )
    val course: Course,

    @Relation(
        parentColumn = "id",
        entityColumn = "sectionId"
    )
    val sessions: List<ClassSession>
) {
    val courseCode: String
        get() = course.code

    val courseName: String
        get() = course.name

    val sectionCode: String
        get() = section.sectionCode

    val instructor: String
        get() = section.instructor

    val credits: Int
        get() = course.credits

    val examDate: String
        get() = section.examDate

    val examTimeRange: String
        get() = if (section.examStartTime.isNotBlank() && section.examEndTime.isNotBlank()) {
            "${section.examStartTime} - ${section.examEndTime}"
        } else ""
}

data class SectionWithSessions(
    @Embedded
    val section: CourseSection,

    @Relation(
        parentColumn = "id",
        entityColumn = "sectionId"
    )
    val sessions: List<ClassSession>
)

data class CourseWithSections(
    @Embedded
    val course: Course,

    @Relation(
        entity = CourseSection::class,
        parentColumn = "id",
        entityColumn = "courseId"
    )
    val sections: List<SectionWithSessions>
)
