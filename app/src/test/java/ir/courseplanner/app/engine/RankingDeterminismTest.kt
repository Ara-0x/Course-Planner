package ir.courseplanner.app.engine

import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.SectionWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generator's scores are preference-weighted, so the *ranking* must be
 * deterministic: the same candidate space must always yield the same order,
 * and a topK=1 cut must keep the true best — regardless of the order the DFS
 * happened to visit combinations in.
 */
class RankingDeterminismTest {

    private var nextId = 1L

    private fun section(
        courseId: Long,
        sessions: List<Pair<Int, Pair<String, String>>>
    ): SectionWithDetails {
        val id = nextId++
        return SectionWithDetails(
            course = Course(id = courseId, code = "C$courseId", name = "Course $courseId"),
            section = CourseSection(
                id = id,
                courseId = courseId,
                sectionCode = "0$id"
            ),
            sessions = sessions.map { (day, times) ->
                ClassSession(
                    sectionId = id,
                    dayOfWeek = day,
                    startTime = times.first,
                    endTime = times.second
                )
            }
        )
    }

    @Test
    fun `topK one keeps the true best regardless of input order`() {
        fun search(order: List<List<SectionWithDetails>>): ScheduleSearchResult {
            return ScheduleEngine.generateTopSchedules(
                courses = listOf(
                    GeneratorCourse("A", order[0]),
                    GeneratorCourse("B", order[1])
                ),
                preference = OptimizationPreference.MIN_GAPS,
                topK = 1
            )
        }
        val a1 = section(1, listOf(0 to ("08:00" to "10:00")))
        val a2 = section(1, listOf(0 to ("13:00" to "15:00")))
        val b1 = section(2, listOf(0 to ("10:00" to "12:00")))
        val b2 = section(2, listOf(0 to ("16:00" to "18:00")))

        val r1 = search(listOf(listOf(a1, a2), listOf(b1, b2)))
        val r2 = search(listOf(listOf(a2, a1), listOf(b2, b1)))

        assertEquals(4, r1.totalValid)
        assertEquals(4, r2.totalValid)
        assertEquals(1, r1.ranked.size)
        // Zero-gap winner: section ids are stable, so the same schedule wins.
        assertEquals(r1.ranked[0].schedule.map { it.section.id },
            r2.ranked[0].schedule.map { it.section.id })
        assertEquals(0, r1.ranked[0].totalGapMinutes)
    }

    @Test
    fun `equal scores break ties by fewer gaps then fewer days`() {
        // S1: 2 active days, no gap.  S2: 1 active day, big gap.
        // With MIN_GAPS weights the gap-free one ranks first either way round.
        val s1 = listOf(
            section(1, listOf(0 to ("08:00" to "10:00"))),
            section(2, listOf(1 to ("08:00" to "10:00")))
        )
        val s2 = listOf(
            section(1, listOf(0 to ("08:00" to "10:00"))),
            section(2, listOf(0 to ("14:00" to "16:00")))
        )
        val fwd = ScheduleEngine.rankSchedules(
            listOf(s1, s2), OptimizationPreference.MIN_GAPS
        )
        val rev = ScheduleEngine.rankSchedules(
            listOf(s2, s1), OptimizationPreference.MIN_GAPS
        )
        assertEquals(
            fwd.map { it.schedule.flattenIds() },
            rev.map { it.schedule.flattenIds() }
        )
        assertTrue(fwd[0].totalGapMinutes <= fwd[1].totalGapMinutes)
    }

    private fun List<SectionWithDetails>.flattenIds(): List<Long> =
        map { it.section.id }.sorted()
}
