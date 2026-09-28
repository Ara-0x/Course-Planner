package ir.courseplanner.app.engine

import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Data-integrity/semantics tests for the scheduler result model:
 * - `isComplete`: a result that skipped a selected course is NOT a complete
 *   schedule (and must therefore never be applied as one),
 * - `rawScore`/`breakdown`: the explainable rows ALWAYS add up to the displayed
 *   score, including when the score is clamped to 0..100,
 * - parity-aware scoring metrics: a biweekly-only day/session is charged half,
 *   never as if it met every week,
 * - the documented weights are the ones the code uses (the old comment claimed
 *   "5 points per extra day" while the code used 6).
 *
 * Plain JUnit: the engine is pure Kotlin.
 */
class ScheduleScoringIntegrityTest {

    private var nextSectionId = 1L

    private fun section(
        day: Int,
        start: String,
        end: String,
        weekType: WeekType = WeekType.EVERY_WEEK,
        courseId: Long = nextSectionId
    ): SectionWithDetails {
        val sectionId = nextSectionId++
        return SectionWithDetails(
            section = CourseSection(id = sectionId, courseId = courseId, sectionCode = "01"),
            course = Course(id = courseId, code = "C$courseId", name = "درس $courseId", credits = 3),
            sessions = listOf(
                ClassSession(
                    sectionId = sectionId,
                    dayOfWeek = day,
                    startTime = start,
                    endTime = end,
                    weekType = weekType
                )
            )
        )
    }

    // ---------------------------------------------------------------- isComplete

    @Test
    fun `result that skipped a selected course is incomplete`() {
        val math = section(0, "10:00", "12:00")
        val result = ScheduleEngine.generateTopSchedules(
            courses = listOf(
                GeneratorCourse("ریاضی", listOf(math)),
                GeneratorCourse("ساختمان داده", emptyList())
            )
        )
        assertEquals(listOf("ساختمان داده"), result.skippedCourses)
        assertFalse("A skipped course makes the whole result incomplete", result.isComplete)
        assertFalse(result.ranked.isEmpty())
    }

    @Test
    fun `result covering every selected course is complete`() {
        val a = section(0, "10:00", "12:00")
        val b = section(1, "10:00", "12:00")
        val result = ScheduleEngine.generateTopSchedules(
            courses = listOf(GeneratorCourse("A", listOf(a)), GeneratorCourse("B", listOf(b)))
        )
        assertTrue(result.skippedCourses.isEmpty())
        assertTrue(result.isComplete)
    }

    // ------------------------------------------------------- score vs breakdown

    @Test
    fun `breakdown always sums to the displayed score`() {
        // A comfortable schedule: one weekly 10:00–12:00 class.
        val good = ScheduleEngine.evaluateSchedule(listOf(section(0, "10:00", "12:00")))
        assertEquals(100, good.rawScore)
        assertEquals(100, good.score)
        assertEquals(100, good.breakdown.sumOf { it.delta })
        // No clamp row for an in-range score.
        assertTrue(good.breakdown.none { it.labelFa.contains("محدودسازی") })

        // A realistic penalty case (gap + early class).
        val b1 = section(0, "08:00", "10:00")
        val b2 = section(0, "13:00", "15:00")
        val penalized = ScheduleEngine.evaluateSchedule(listOf(b1, b2), OptimizationPreference.MIN_GAPS)
        assertEquals(penalized.score, penalized.breakdown.sumOf { it.delta })
        assertTrue(penalized.score in 0..100)
    }

    @Test
    fun `clamped score is explained by an explicit clamp row`() {
        // Five days, each with two 4-hour gaps: the deductions far exceed 100.
        val brutal = (0..4).flatMap { day ->
            listOf(
                section(day, "08:00", "10:00"),
                section(day, "14:00", "16:00"),
                section(day, "20:00", "22:00")
            )
        }
        val evaluated = ScheduleEngine.evaluateSchedule(brutal, OptimizationPreference.MIN_GAPS)

        assertTrue("raw score must be able to go below zero", evaluated.rawScore < 0)
        assertEquals("final score is clamped into 0..100", 0, evaluated.score)
        // The rows still add up to what the user sees.
        assertEquals(evaluated.score, evaluated.breakdown.sumOf { it.delta })
        val clampRow = evaluated.breakdown.first { it.labelFa.contains("محدودسازی") }
        assertEquals(-evaluated.rawScore, clampRow.delta)
    }

    @Test
    fun `score can never exceed 100`() {
        val perfect = ScheduleEngine.evaluateSchedule(listOf(section(0, "10:00", "12:00")))
        assertTrue(perfect.score <= 100)
        assertEquals(perfect.rawScore, perfect.score)
        assertTrue(perfect.breakdown.sumOf { it.delta } <= 100)
    }

    // ---------------------------------------------------------- parity metrics

    @Test
    fun `odd-only day counts half in scoring but full in display metrics`() {
        val oddOnly = listOf(section(0, "08:00", "10:00", WeekType.ODD_WEEKS))
        assertEquals(0.5, ScheduleEngine.weeklyActiveDays(oddOnly), 0.0001)
        assertEquals(0.5, ScheduleEngine.weeklyEarlyMorningCount(oddOnly), 0.0001)
        // Display metrics keep the union view: the day must be kept free anyway.
        assertEquals(1, ScheduleEngine.computeMetrics(oddOnly).activeDaysCount)

        val everyWeek = listOf(section(0, "08:00", "10:00"))
        assertEquals(1.0, ScheduleEngine.weeklyActiveDays(everyWeek), 0.0001)
        assertEquals(1.0, ScheduleEngine.weeklyEarlyMorningCount(everyWeek), 0.0001)
    }

    @Test
    fun `odd plus even sessions on one day count as a full weekly day`() {
        val both = listOf(
            section(0, "08:00", "10:00", WeekType.ODD_WEEKS),
            section(0, "14:00", "16:00", WeekType.EVEN_WEEKS)
        )
        assertEquals(1.0, ScheduleEngine.weeklyActiveDays(both), 0.0001)
        // Only the odd-week session is early: still half of the weekly average.
        assertEquals(0.5, ScheduleEngine.weeklyEarlyMorningCount(both), 0.0001)
    }

    @Test
    fun `late sessions never count as early morning`() {
        val late = listOf(section(0, "10:00", "12:00"))
        assertEquals(0.0, ScheduleEngine.weeklyEarlyMorningCount(late), 0.0001)
    }

    // ------------------------------------------------------------------ weights

    @Test
    fun `four weekly days cost 6 points while three and a half cost 3`() {
        val fourWeeklyDays = listOf(
            section(0, "10:00", "12:00"),
            section(1, "10:00", "12:00"),
            section(2, "10:00", "12:00"),
            section(3, "10:00", "12:00")
        )
        val weekly = ScheduleEngine.evaluateSchedule(fourWeeklyDays)
        assertEquals(4.0, weekly.weeklyActiveDays, 0.0001)
        // 100 - 1 extra day * 6 points = 94 (the shipped code used 6 while the
        // stale comment claimed 5: the constant now documents the truth).
        assertEquals(94, weekly.score)
        assertEquals(weekly.score, weekly.breakdown.sumOf { it.delta })

        val halfDayBiweekly = listOf(
            section(0, "10:00", "12:00"),
            section(1, "10:00", "12:00"),
            section(2, "10:00", "12:00"),
            section(3, "10:00", "12:00", WeekType.ODD_WEEKS)
        )
        val biweekly = ScheduleEngine.evaluateSchedule(halfDayBiweekly)
        assertEquals(3.5, biweekly.weeklyActiveDays, 0.0001)
        // 0.5 extra effective day * 6 = 3 points.
        assertEquals(97, biweekly.score)
        assertTrue(biweekly.score > weekly.score)
    }

    // ------------------------------------------------------ untouched behaviour

    @Test
    fun `conflict detection still honours parity`() {
        val a = section(0, "08:00", "10:00")
        val b = section(0, "09:00", "11:00")
        assertNotNull(ScheduleEngine.checkConflict(a, b))

        val odd = section(0, "08:00", "10:00", WeekType.ODD_WEEKS)
        val even = section(0, "09:00", "11:00", WeekType.EVEN_WEEKS)
        assertNull(ScheduleEngine.checkConflict(odd, even))
    }

    @Test
    fun `leaf cap still truncates instead of losing the best-so-far`() {
        val a1 = section(1, "10:00", "12:00", courseId = 1)
        val a2 = section(2, "10:00", "12:00", courseId = 1)
        val b1 = section(3, "10:00", "12:00", courseId = 2)
        val b2 = section(4, "10:00", "12:00", courseId = 2)
        val result = ScheduleEngine.generateTopSchedules(
            courses = listOf(GeneratorCourse("A", listOf(a1, a2)), GeneratorCourse("B", listOf(b1, b2))),
            maxLeaves = 1
        )
        assertTrue(result.truncated)
        assertFalse(result.ranked.isEmpty())
        assertTrue(result.isComplete)
    }

    @Test
    fun `optimization preferences still re-rank the same candidates`() {
        // Compact-but-gappy: ONE day with a 3-hour idle window and an 08:00 class.
        val compact = listOf(
            section(0, "08:00", "10:00", courseId = 1),
            section(0, "13:00", "15:00", courseId = 2)
        )
        // Spread: FOUR relaxed days, no gaps, no early class.
        val spread = listOf(
            section(0, "10:00", "12:00", courseId = 1),
            section(1, "10:00", "12:00", courseId = 2),
            section(2, "10:00", "12:00", courseId = 3),
            section(3, "10:00", "12:00", courseId = 4)
        )
        val minGaps = ScheduleEngine.rankSchedules(listOf(compact, spread), OptimizationPreference.MIN_GAPS)
        val minDays = ScheduleEngine.rankSchedules(listOf(compact, spread), OptimizationPreference.MIN_DAYS)

        // Gap-averse users get the zero-gap plan...
        assertEquals(0, minGaps[0].totalGapMinutes)
        assertEquals(4, minGaps[0].schedule.size)
        // ...while day-averse users accept the gap to stay on one day.
        assertEquals(1, minDays[0].activeDaysCount)
        assertEquals(2, minDays[0].schedule.size)
    }
}
