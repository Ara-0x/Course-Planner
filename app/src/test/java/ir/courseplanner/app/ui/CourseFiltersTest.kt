package ir.courseplanner.app.ui

import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.CourseWithSections
import ir.courseplanner.app.data.model.SectionWithSessions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JUnit: filter helpers are pure functions over data classes. */
class CourseFiltersTest {

    private fun course(
        code: String,
        credits: Int,
        sessionsBySection: List<List<Pair<Int, Pair<String, String>>>>,
        selected: Boolean = false
    ): CourseWithSections {
        val sections = sessionsBySection.mapIndexed { idx, sessions ->
            SectionWithSessions(
                section = CourseSection(
                    id = idx.toLong() + 1,
                    courseId = 1,
                    sectionCode = "0${idx + 1}",
                    instructor = "استاد $idx"
                ),
                sessions = sessions.map { (day, times) ->
                    ClassSession(
                        sectionId = idx.toLong() + 1,
                        dayOfWeek = day,
                        startTime = times.first,
                        endTime = times.second
                    )
                }
            )
        }
        return CourseWithSections(
            course = Course(
                id = 1,
                code = code,
                name = "درس $code",
                credits = credits,
                isSelectedForGeneration = selected
            ),
            sections = sections
        )
    }

    @Test
    fun `units filter matches exact and 4plus buckets`() {
        assertTrue(CourseUnitsFilter.ALL.matches(3))
        assertTrue(CourseUnitsFilter.U2.matches(2))
        assertFalse(CourseUnitsFilter.U2.matches(3))
        assertTrue(CourseUnitsFilter.U4_PLUS.matches(4))
        assertTrue(CourseUnitsFilter.U4_PLUS.matches(6))
        assertFalse(CourseUnitsFilter.U4_PLUS.matches(3))
    }

    @Test
    fun `day filter finds courses meeting on that day`() {
        val c = course("10559", 2, listOf(listOf(2 to ("14:00" to "16:00"))))
        assertTrue(courseHasSessionOnDay(c, 2))
        assertFalse(courseHasSessionOnDay(c, 0))
    }

    @Test
    fun `sessionless project courses are detected`() {
        val project = course("10318", 2, listOf(emptyList()))
        assertFalse(courseHasAnySession(project))
        val normal = course("10309", 2, listOf(listOf(2 to ("08:00" to "10:00"))))
        assertTrue(courseHasAnySession(normal))
    }

    @Test
    fun `isMyCourse covers selected and enrolled courses`() {
        val selected = course("A", 3, listOf(emptyList()), selected = true)
        assertTrue(isMyCourse(selected))
        val catalog = course("B", 3, listOf(listOf(0 to ("08:00" to "10:00"))))
        assertFalse(isMyCourse(catalog))
    }

    @Test
    fun `degree filter separates kardani karshenasi and arshad`() {
        assertTrue(CourseDegreeFilter.ALL.matches("کاردانی پیوسته"))
        assertTrue(CourseDegreeFilter.KARDANI.matches("کاردانی پیوسته"))
        assertFalse(CourseDegreeFilter.KARSHENASI.matches("کاردانی پیوسته"))
        assertFalse(CourseDegreeFilter.ARSHAD.matches("کاردانی پیوسته"))

        assertTrue(CourseDegreeFilter.KARSHENASI.matches("کارشناسی"))
        assertTrue(CourseDegreeFilter.KARSHENASI.matches("کارشناسی ناپیوسته"))
        assertFalse(CourseDegreeFilter.KARDANI.matches("کارشناسی"))

        // ارشد stays separated: it must not leak into the کارشناسی bucket.
        assertTrue(CourseDegreeFilter.ARSHAD.matches("کارشناسی ارشد"))
        assertFalse(CourseDegreeFilter.KARSHENASI.matches("کارشناسی ارشد"))
        assertFalse(CourseDegreeFilter.KARDANI.matches("کارشناسی ارشد"))
    }

    @Test
    fun `unknown degree is never hidden by degree filter`() {
        for (filter in CourseDegreeFilter.values()) {
            assertTrue(filter.matches(null))
            assertTrue(filter.matches(""))
            assertTrue(filter.matches("   "))
        }
    }
}
