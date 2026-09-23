package ir.courseplanner.app.ui

import ir.courseplanner.app.data.model.CourseWithSections
import ir.courseplanner.app.data.model.SectionWithSessions

/**
 * Extra catalog filters for the Courses screen.
 *
 * Everything here is a pure function over plain data classes so it stays
 * unit-testable without Robolectric (see CourseFiltersTest).
 */
enum class CourseUnitsFilter(val titleFa: String) {
    ALL("همه واحدها"),
    U1("۱ واحد"),
    U2("۲ واحد"),
    U3("۳ واحد"),
    U4_PLUS("۴+ واحد");

    fun matches(credits: Int): Boolean = when (this) {
        ALL -> true
        U1 -> credits == 1
        U2 -> credits == 2
        U3 -> credits == 3
        U4_PLUS -> credits >= 4
    }
}

/** Days shown as filter chips: Sat(0) .. Thu(5). Friday has no classes. */
val FILTERABLE_DAYS: List<Int> = listOf(0, 1, 2, 3, 4, 5)

/**
 * Degree-level filter. Portal values look like "کاردانی پیوسته", "کارشناسی",
 * "کارشناسی ناپیوسته", "کارشناسی ارشد".
 *
 * Safety rule: courses with an unknown degree (pre-migration rows, manual or
 * JSON entries) are NEVER hidden by this filter — a filter must not make
 * existing user data disappear.
 */
enum class CourseDegreeFilter(val titleFa: String) {
    ALL("همه مقاطع"),
    KARDANI("کاردانی"),
    KARSHENASI("کارشناسی"),
    ARSHAD("کارشناسی ارشد");

    fun matches(degree: String?): Boolean {
        val d = degree.orEmpty().trim()
        if (d.isEmpty()) return true
        return when (this) {
            ALL -> true
            ARSHAD -> d.contains("ارشد")
            KARDANI -> d.contains("کاردانی")
            // Exclude ارشد explicitly so the two levels stay separated.
            KARSHENASI -> d.contains("کارشناسی") && !d.contains("ارشد")
        }
    }
}

/** True when any group of the course meets on [dayOfWeek]. */
fun courseHasSessionOnDay(course: CourseWithSections, dayOfWeek: Int): Boolean =
    course.sections.any { section -> section.sessions.any { it.dayOfWeek == dayOfWeek } }

/** True when at least one group has real class time (excludes projects/internships). */
fun courseHasAnySession(course: CourseWithSections): Boolean =
    course.sections.any { it.sessions.isNotEmpty() }

/** True when the course is part of "my courses" (generator target or enrolled). */
fun isMyCourse(course: CourseWithSections): Boolean =
    course.course.isSelectedForGeneration ||
        course.sections.any { it.section.isEnrolled }
