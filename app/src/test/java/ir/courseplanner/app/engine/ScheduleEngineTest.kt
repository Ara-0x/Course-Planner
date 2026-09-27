package ir.courseplanner.app.engine

import ir.courseplanner.app.data.importer.CourseImporter
import ir.courseplanner.app.data.importer.ImportResult
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.ConflictType
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleEngineTest {

    private fun createMockSection(
        courseId: Long,
        courseCode: String,
        courseName: String,
        credits: Int,
        sectionId: Long,
        sectionCode: String,
        sessions: List<Pair<Int, Pair<String, String>>>, // day to (start, end)
        examDate: String = "",
        examStart: String = "",
        examEnd: String = "",
        weekTypes: List<WeekType> = emptyList()
    ): SectionWithDetails {
        val course = Course(id = courseId, code = courseCode, name = courseName, credits = credits)
        val section = CourseSection(
            id = sectionId,
            courseId = courseId,
            sectionCode = sectionCode,
            examDate = examDate,
            examStartTime = examStart,
            examEndTime = examEnd
        )
        val sessionEntities = sessions.mapIndexed { idx, (day, times) ->
            ClassSession(
                id = (sectionId * 10) + idx,
                sectionId = sectionId,
                dayOfWeek = day,
                startTime = times.first,
                endTime = times.second,
                weekType = weekTypes.getOrElse(idx) { WeekType.EVERY_WEEK }
            )
        }
        return SectionWithDetails(section = section, course = course, sessions = sessionEntities)
    }

    @Test
    fun testTimesOverlap_overlappingIntervals() {
        // 08:00 (480) - 10:00 (600) vs 09:00 (540) - 11:00 (660) -> overlap!
        assertTrue(ScheduleEngine.timesOverlap(480, 600, 540, 660))
    }

    @Test
    fun testTimesOverlap_backToBackClassesDoNotConflict() {
        // 08:00 (480) - 10:00 (600) vs 10:00 (600) - 12:00 (720) -> touching borders is NOT an overlap!
        assertFalse(ScheduleEngine.timesOverlap(480, 600, 600, 720))
    }

    @Test
    fun testTimesOverlap_disjointIntervals() {
        // 08:00 (480) - 10:00 (600) vs 13:00 (780) - 15:00 (900) -> no overlap
        assertFalse(ScheduleEngine.timesOverlap(480, 600, 780, 900))
    }

    @Test
    fun testClassSessionConflict_sameDayOverlap() {
        val mathSec1 = createMockSection(
            courseId = 1, courseCode = "MATH101", courseName = "Math 1", credits = 3,
            sectionId = 101, sectionCode = "01",
            sessions = listOf(0 to ("08:00" to "10:00")) // Saturday 8-10
        )

        val physSec1 = createMockSection(
            courseId = 2, courseCode = "PHYS101", courseName = "Physics 1", credits = 3,
            sectionId = 201, sectionCode = "01",
            sessions = listOf(0 to ("09:00" to "11:00")) // Saturday 9-11 (clashes!)
        )

        val conflict = ScheduleEngine.checkConflict(mathSec1, physSec1)
        assertNotNull(conflict)
        assertEquals(ConflictType.CLASS_TIME_OVERLAP, conflict?.type)
        assertEquals(0, conflict?.dayOfWeek)
    }

    @Test
    fun testClassSessionConflict_differentDaysNoConflict() {
        val mathSec1 = createMockSection(
            courseId = 1, courseCode = "MATH101", courseName = "Math 1", credits = 3,
            sectionId = 101, sectionCode = "01",
            sessions = listOf(0 to ("08:00" to "10:00")) // Saturday
        )

        val physSec1 = createMockSection(
            courseId = 2, courseCode = "PHYS101", courseName = "Physics 1", credits = 3,
            sectionId = 201, sectionCode = "01",
            sessions = listOf(1 to ("08:00" to "10:00")) // Sunday (different day!)
        )

        val conflict = ScheduleEngine.checkConflict(mathSec1, physSec1)
        assertNull(conflict)
    }

    @Test
    fun testWeekTypeConflict_evenVsOddWeeksDoNotConflict() {
        // Lab 1 (Even weeks): Monday 14:00 - 16:00
        val lab1 = createMockSection(
            courseId = 1, courseCode = "PHYS101L", courseName = "Physics Lab", credits = 1,
            sectionId = 101, sectionCode = "01",
            sessions = listOf(2 to ("14:00" to "16:00")),
            weekTypes = listOf(WeekType.EVEN_WEEKS)
        )

        // Lab 2 (Odd weeks): Monday 14:00 - 16:00 (Same time and day, but alternating weeks!)
        val lab2 = createMockSection(
            courseId = 2, courseCode = "CHEM101L", courseName = "Chemistry Lab", credits = 1,
            sectionId = 201, sectionCode = "01",
            sessions = listOf(2 to ("14:00" to "16:00")),
            weekTypes = listOf(WeekType.ODD_WEEKS)
        )

        val conflict = ScheduleEngine.checkConflict(lab1, lab2)
        assertNull("Even and Odd week classes at the same time must NOT conflict!", conflict)
    }

    @Test
    fun testWeekTypeConflict_evenVsEvenWeeksDoConflict() {
        val lab1 = createMockSection(
            courseId = 1, courseCode = "PHYS101L", courseName = "Physics Lab", credits = 1,
            sectionId = 101, sectionCode = "01",
            sessions = listOf(2 to ("14:00" to "16:00")),
            weekTypes = listOf(WeekType.EVEN_WEEKS)
        )

        val lab2 = createMockSection(
            courseId = 2, courseCode = "CHEM101L", courseName = "Chemistry Lab", credits = 1,
            sectionId = 201, sectionCode = "01",
            sessions = listOf(2 to ("14:00" to "16:00")),
            weekTypes = listOf(WeekType.EVEN_WEEKS)
        )

        val conflict = ScheduleEngine.checkConflict(lab1, lab2)
        assertNotNull("Two even-week classes at the same time must conflict!", conflict)
    }

    @Test
    fun testWeekTypeConflict_everyWeekVsEvenWeeksConflict() {
        val regularClass = createMockSection(
            courseId = 1, courseCode = "MATH101", courseName = "Calculus", credits = 3,
            sectionId = 101, sectionCode = "01",
            sessions = listOf(2 to ("14:00" to "16:00")),
            weekTypes = listOf(WeekType.EVERY_WEEK)
        )

        val evenLab = createMockSection(
            courseId = 2, courseCode = "PHYS101L", courseName = "Physics Lab", credits = 1,
            sectionId = 201, sectionCode = "01",
            sessions = listOf(2 to ("14:00" to "16:00")),
            weekTypes = listOf(WeekType.EVEN_WEEKS)
        )

        val conflict = ScheduleEngine.checkConflict(regularClass, evenLab)
        assertNotNull("Every-week class must conflict with even-week class at the same time!", conflict)
    }

    @Test
    fun testExamConflict_sameDayAndHourOverlap() {
        val csSec1 = createMockSection(
            courseId = 1, courseCode = "CS101", courseName = "Intro to CS", credits = 3,
            sectionId = 101, sectionCode = "01",
            sessions = listOf(0 to ("08:00" to "10:00")),
            examDate = "1403/10/25", examStart = "09:00", examEnd = "12:00"
        )

        val logicSec1 = createMockSection(
            courseId = 2, courseCode = "CS202", courseName = "Logic Circuits", credits = 3,
            sectionId = 201, sectionCode = "01",
            sessions = listOf(1 to ("10:00" to "12:00")),
            examDate = "1403/10/25", examStart = "10:00", examEnd = "12:30"
        )

        val conflict = ScheduleEngine.checkConflict(csSec1, logicSec1)
        assertNotNull(conflict)
        assertEquals(ConflictType.EXAM_OVERLAP, conflict?.type)
    }

    @Test
    fun testScheduleGenerator_findsConflictFreeCombinations() {
        val math1 = createMockSection(1, "MATH101", "Math", 3, 101, "01", listOf(0 to ("08:00" to "10:00")))
        val math2 = createMockSection(1, "MATH101", "Math", 3, 102, "02", listOf(1 to ("10:00" to "12:00")))

        val phys1 = createMockSection(2, "PHYS101", "Physics", 3, 201, "01", listOf(0 to ("08:00" to "10:00")))
        val phys2 = createMockSection(2, "PHYS101", "Physics", 3, 202, "02", listOf(4 to ("08:00" to "10:00")))

        val courseGroups = listOf(
            listOf(math1, math2),
            listOf(phys1, phys2)
        )

        val schedules = ScheduleEngine.generateConflictFreeSchedules(courseGroups)

        assertEquals(3, schedules.size)

        for (schedule in schedules) {
            val conflicts = ScheduleEngine.findAllConflicts(schedule)
            assertTrue("Expected no conflicts in generated schedule", conflicts.isEmpty())
        }
    }

    @Test
    fun testScheduleEngine_optimizationScoringAndRanking() {
        // Schedule A: Back to back classes on Saturday 08:00-10:00 and 10:00-12:00 (Gap = 0 mins)
        val secA1 = createMockSection(1, "A1", "Course A", 3, 1, "01", listOf(0 to ("08:00" to "10:00")))
        val secA2 = createMockSection(2, "A2", "Course B", 3, 2, "01", listOf(0 to ("10:00" to "12:00")))
        val scheduleA = listOf(secA1, secA2)

        // Schedule B: Classes with a 3-hour dead gap (08:00-10:00 and 13:00-15:00, Gap = 180 mins)
        val secB1 = createMockSection(1, "B1", "Course A", 3, 3, "01", listOf(0 to ("08:00" to "10:00")))
        val secB2 = createMockSection(2, "B2", "Course B", 3, 4, "01", listOf(0 to ("13:00" to "15:00")))
        val scheduleB = listOf(secB1, secB2)

        val scoredA = ScheduleEngine.evaluateSchedule(scheduleA, OptimizationPreference.MIN_GAPS)
        val scoredB = ScheduleEngine.evaluateSchedule(scheduleB, OptimizationPreference.MIN_GAPS)

        assertEquals(0, scoredA.totalGapMinutes)
        assertEquals(180, scoredB.totalGapMinutes)
        assertTrue("Schedule with 0 gap must score higher than schedule with 180 mins gap", scoredA.score > scoredB.score)

        // Ranking should place schedule A first
        val ranked = ScheduleEngine.rankSchedules(listOf(scheduleB, scheduleA), OptimizationPreference.MIN_GAPS)
        assertEquals(scheduleA, ranked[0].schedule)
    }

    @Test
    fun testStrictTimeParsing_validForms() {
        assertEquals(480, ClassSession.parseTimeMinutesOrNull("08:00"))
        assertEquals(480, ClassSession.parseTimeMinutesOrNull("8:00"))
        assertEquals(510, ClassSession.parseTimeMinutesOrNull("08:30"))
        assertEquals(510, ClassSession.parseTimeMinutesOrNull("۸:۳۰"))
        assertEquals(0, ClassSession.parseTimeMinutesOrNull("00:00"))
        assertEquals(1439, ClassSession.parseTimeMinutesOrNull("23:59"))
    }

    @Test
    fun testStrictTimeParsing_invalidFormsAreNullNeverZero() {
        assertNull(ClassSession.parseTimeMinutesOrNull("abc"))
        assertNull(ClassSession.parseTimeMinutesOrNull("25:90"))
        assertNull(ClassSession.parseTimeMinutesOrNull("24:00"))
        assertNull(ClassSession.parseTimeMinutesOrNull("08:00 - 10:00"))
        assertNull(ClassSession.parseTimeMinutesOrNull(""))
        assertNull(ClassSession.parseTimeMinutesOrNull("8"))
        // Legacy lenient helper keeps old behavior for already-validated data.
        assertEquals(0, ClassSession.timeToMinutes("abc"))
    }

    @Test
    fun testAdjacentClasses_noConflict_oneMinuteOverlapConflicts() {
        val first = createMockSection(
            1, "A", "A", 3, 1, "01", listOf(0 to ("10:00" to "12:00"))
        )
        val adjacent = createMockSection(
            2, "B", "B", 3, 2, "01", listOf(0 to ("12:00" to "14:00"))
        )
        assertNull(ScheduleEngine.checkConflict(first, adjacent))

        val overlapByMinute = createMockSection(
            2, "B", "B", 3, 3, "01", listOf(0 to ("11:59" to "14:00"))
        )
        val conflict = ScheduleEngine.checkConflict(first, overlapByMinute)
        assertNotNull(conflict)
        assertEquals(ConflictType.CLASS_TIME_OVERLAP, conflict?.type)
    }

    @Test
    fun testExamWarning_sameDayUnknownTimeIsNotDefiniteConflict() {
        val full = createMockSection(
            1, "A", "A", 3, 1, "01", listOf(0 to ("08:00" to "10:00")),
            examDate = "1403/10/25", examStart = "09:00", examEnd = "12:00"
        )
        val unknownEnd = createMockSection(
            2, "B", "B", 3, 2, "01", listOf(1 to ("08:00" to "10:00")),
            examDate = "1403/10/25", examStart = "10:00", examEnd = ""
        )
        // Not a definite conflict...
        assertNull(ScheduleEngine.checkConflict(full, unknownEnd))
        // ...but a warning with insufficient-data semantics.
        val warning = ScheduleEngine.checkExamWarning(full, unknownEnd)
        assertNotNull(warning)
        assertEquals(ConflictType.EXAM_SAME_DAY_WARNING, warning?.type)
        assertEquals(1, ScheduleEngine.findExamWarnings(listOf(full, unknownEnd)).size)
    }

    @Test
    fun testExamWarning_bothFullyTimedDisjointIsClean() {
        val a = createMockSection(
            1, "A", "A", 3, 1, "01", listOf(0 to ("08:00" to "10:00")),
            examDate = "1403/10/25", examStart = "08:00", examEnd = "10:00"
        )
        val b = createMockSection(
            2, "B", "B", 3, 2, "01", listOf(1 to ("08:00" to "10:00")),
            examDate = "1403/10/25", examStart = "10:00", examEnd = "12:00"
        )
        assertNull(ScheduleEngine.checkConflict(a, b))
        assertNull(ScheduleEngine.checkExamWarning(a, b))
        assertTrue(ScheduleEngine.findExamWarnings(listOf(a, b)).isEmpty())
    }

    @Test
    fun testExamWarning_differentDaysNoWarning() {
        val a = createMockSection(
            1, "A", "A", 3, 1, "01", listOf(0 to ("08:00" to "10:00")),
            examDate = "1403/10/25", examStart = "", examEnd = ""
        )
        val b = createMockSection(
            2, "B", "B", 3, 2, "01", listOf(1 to ("08:00" to "10:00")),
            examDate = "1403/10/26", examStart = "", examEnd = ""
        )
        assertNull(ScheduleEngine.checkConflict(a, b))
        assertNull(ScheduleEngine.checkExamWarning(a, b))
    }

    @Test
    fun testTopSearch_reportsSkippedCourseInsteadOfDroppingIt() {
        val math = createMockSection(
            1, "MATH", "ریاضی", 3, 1, "01", listOf(0 to ("08:00" to "10:00"))
        )
        val result = ScheduleEngine.generateTopSchedules(
            courses = listOf(
                GeneratorCourse("ریاضی", listOf(math)),
                GeneratorCourse("ساختمان داده", emptyList())
            ),
            preference = OptimizationPreference.BALANCED
        )
        assertEquals(listOf("ساختمان داده"), result.skippedCourses)
        assertEquals(1, result.totalValid)
        assertEquals(1, result.ranked.size)
        assertFalse(result.truncated)
    }

    @Test
    fun testTopSearch_keepsTrueBestWithTopKOne() {
        val s1 = createMockSection(1, "M", "M", 3, 1, "01", listOf(0 to ("08:00" to "10:00")))
        val s2 = createMockSection(1, "M", "M", 3, 2, "02", listOf(0 to ("13:00" to "15:00")))
        val t1 = createMockSection(2, "P", "P", 3, 3, "01", listOf(0 to ("10:00" to "12:00")))
        val t2 = createMockSection(2, "P", "P", 3, 4, "02", listOf(0 to ("16:00" to "18:00")))
        val result = ScheduleEngine.generateTopSchedules(
            courses = listOf(GeneratorCourse("M", listOf(s1, s2)), GeneratorCourse("P", listOf(t1, t2))),
            preference = OptimizationPreference.MIN_GAPS,
            topK = 1
        )
        // All 4 combos valid, but only the true best (zero gap) is kept.
        assertEquals(4, result.totalValid)
        assertEquals(1, result.ranked.size)
        assertEquals(0, result.ranked[0].totalGapMinutes)
        val expected = ScheduleEngine.evaluateSchedule(listOf(s1, t1), OptimizationPreference.MIN_GAPS)
        assertEquals(expected.score, result.ranked[0].score)
    }

    @Test
    fun testTopSearch_impossibleScheduleReportsZeroWithoutSkips() {
        val a = createMockSection(1, "A", "A", 3, 1, "01", listOf(0 to ("08:00" to "10:00")))
        val b = createMockSection(2, "B", "B", 3, 2, "01", listOf(0 to ("09:00" to "11:00")))
        val result = ScheduleEngine.generateTopSchedules(
            courses = listOf(GeneratorCourse("A", listOf(a)), GeneratorCourse("B", listOf(b))),
            preference = OptimizationPreference.BALANCED
        )
        assertEquals(0, result.totalValid)
        assertTrue(result.ranked.isEmpty())
        assertTrue(result.skippedCourses.isEmpty())
    }

    @Test
    fun testRealScore_noFakeHundredAndExplainableBreakdown() {
        val secB1 = createMockSection(1, "B1", "Course A", 3, 3, "01", listOf(0 to ("08:00" to "10:00")))
        val secB2 = createMockSection(2, "B2", "Course B", 3, 4, "01", listOf(0 to ("13:00" to "15:00")))
        val scheduleB = listOf(secB1, secB2)

        val evaluated = ScheduleEngine.evaluateSchedule(scheduleB, OptimizationPreference.MIN_GAPS)
        // gap 180 -> -39, one 8:00 class -> -1 => real score 60, never faked to 100.
        assertEquals(60, evaluated.score)
        val deltaSum = evaluated.breakdown.sumOf { it.delta }
        assertEquals(60, (100 + deltaSum).coerceIn(0, 100))
        assertTrue(evaluated.breakdown.any { it.labelFa.contains("گپ") })

        val ranked = ScheduleEngine.rankSchedules(listOf(scheduleB), OptimizationPreference.MIN_GAPS)
        assertEquals(60, ranked[0].score)
        assertTrue(ranked[0].tags.any { it.contains("پیشنهاد برتر") })
    }

    @Test
    fun testCalendarMapping_iranianWeekStartsSaturday() {
        assertEquals(0, ScheduleEngine.calendarDayOfWeekToAppDay(java.util.Calendar.SATURDAY))
        assertEquals(1, ScheduleEngine.calendarDayOfWeekToAppDay(java.util.Calendar.SUNDAY))
        assertEquals(5, ScheduleEngine.calendarDayOfWeekToAppDay(java.util.Calendar.THURSDAY))
        assertEquals(6, ScheduleEngine.calendarDayOfWeekToAppDay(java.util.Calendar.FRIDAY))
    }

    @Test
    fun testNextSession_todayLaterAndOngoingAndWrap() {
        val morning = createMockSection(
            1, "A", "ریاضی", 3, 1, "01", listOf(0 to ("08:00" to "10:00"))
        )
        val noon = createMockSection(
            2, "B", "فیزیک", 3, 2, "01", listOf(0 to ("10:00" to "12:00"), 1 to ("08:00" to "10:00"))
        )
        val sections = listOf(morning, noon)

        // Saturday 07:00 -> morning class next, not ongoing.
        val first = ScheduleEngine.nextUpcomingSession(sections, 0, 420)
        assertNotNull(first)
        assertEquals("08:00", first?.session?.startTime)
        assertEquals(false, first?.ongoing)

        // Saturday 08:30 -> morning class ongoing.
        val ongoing = ScheduleEngine.nextUpcomingSession(sections, 0, 510)
        assertNotNull(ongoing)
        assertEquals("08:00", ongoing?.session?.startTime)
        assertEquals(true, ongoing?.ongoing)

        // Saturday 18:00 -> wraps to Sunday 08:00, not ongoing.
        val wrapped = ScheduleEngine.nextUpcomingSession(sections, 0, 1080)
        assertNotNull(wrapped)
        assertEquals(1, wrapped?.dayOfWeek)
        assertEquals(false, wrapped?.ongoing)

        // Empty schedule -> null.
        assertNull(ScheduleEngine.nextUpcomingSession(emptyList(), 0, 0))
    }

    @Test
    fun testSessionsOnDay_sorted() {
        val a = createMockSection(
            1, "A", "A", 3, 1, "01",
            listOf(0 to ("14:00" to "16:00"), 0 to ("08:00" to "10:00"))
        )
        val todays = ScheduleEngine.sessionsOnDay(listOf(a), 0)
        assertEquals(2, todays.size)
        assertEquals("08:00", todays[0].session.startTime)
        assertEquals("14:00", todays[1].session.startTime)
        assertTrue(ScheduleEngine.sessionsOnDay(listOf(a), 3).isEmpty())
    }

    @Test
    fun testCourseImporter_jsonValidation() {
        val invalidJson = "this is not json"
        val result = CourseImporter.parseJson(invalidJson)
        assertTrue(result is ImportResult.Failure)

        val emptyJson = "[]"
        val emptyResult = CourseImporter.parseJson(emptyJson)
        assertTrue(emptyResult is ImportResult.Failure)

        val validJson = """
            [
              {
                "code": "MATH101",
                "name": "Calculus 1",
                "department": "Math",
                "credits": 4,
                "sections": [
                  {
                    "sectionCode": "01",
                    "instructor": "Dr. Smith",
                    "examDate": "2025-01-15",
                    "examStartTime": "09:00",
                    "examEndTime": "12:00",
                    "sessions": [
                      { "dayOfWeek": 0, "startTime": "08:00", "endTime": "10:00", "location": "Room 101", "weekType": "EVERY_WEEK" }
                    ]
                  }
                ]
              }
            ]
        """.trimIndent()

        val validResult = CourseImporter.parseJson(validJson)
        assertTrue(validResult is ImportResult.Success)
        val success = validResult as ImportResult.Success
        assertEquals(1, success.items.size)
        assertEquals("MATH101", success.items[0].course.code)
    }

    @Test
    fun testCourseImporter_csvValidation() {
        val validCsv = """
            course_code,course_name,department,credits,section_code,instructor,capacity,exam_date,exam_start,exam_end,day_of_week,start_time,end_time,location,week_type
            CS101,Intro to Programming,CS,3,01,Dr. Reza,40,1403/10/20,08:30,11:30,شنبه,08:00,10:00,Lab 1,every
        """.trimIndent()

        val result = CourseImporter.parseCsv(validCsv)
        assertTrue(result is ImportResult.Success)
        val success = result as ImportResult.Success
        assertEquals(1, success.items.size)
        assertEquals("CS101", success.items[0].course.code)
    }
}
