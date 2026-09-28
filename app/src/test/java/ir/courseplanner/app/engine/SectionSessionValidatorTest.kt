package ir.courseplanner.app.engine

import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Intra-section session validation: the parity semantics of
 * [WeekType.overlapsWith] must be honoured by [SectionSessionValidator] exactly
 * as the schedule engine uses them, because both the dialogs and the repository
 * rely on this one implementation.
 *
 * Plain JUnit: the validator and the entities are pure Kotlin.
 */
class SectionSessionValidatorTest {

    private fun session(
        day: Int = 0,
        start: String = "08:00",
        end: String = "10:00",
        weekType: WeekType = WeekType.EVERY_WEEK
    ) = ClassSession(
        sectionId = 1,
        dayOfWeek = day,
        startTime = start,
        endTime = end,
        weekType = weekType
    )

    private fun validate(vararg sessions: ClassSession) =
        SectionSessionValidator.validate(sessions.toList())

    // ---------- normal overlap / non-overlap ----------

    @Test
    fun `same day overlapping times are rejected`() {
        // Saturday 08:00–10:00 vs Saturday 09:00–11:00
        val result = validate(session(start = "08:00", end = "10:00"), session(start = "09:00", end = "11:00"))
        assertTrue(result is SectionSessionValidator.Result.Overlap)
        result as SectionSessionValidator.Result.Overlap
        assertEquals(0, result.firstIndex)
        assertEquals(1, result.secondIndex)
        assertEquals("09:00–10:00", result.overlapRange)
    }

    @Test
    fun `touching sessions and different days are fine`() {
        // 08–10 then 10–12 (border contact is not an overlap).
        assertTrue(validate(session(start = "08:00", end = "10:00"), session(start = "10:00", end = "12:00")) is SectionSessionValidator.Result.Valid)
        // Same time, different day.
        assertTrue(validate(session(day = 0), session(day = 1)) is SectionSessionValidator.Result.Valid)
    }

    // ---------- parity table ----------

    @Test
    fun `every week clashes with every parity`() {
        assertTrue(
            validate(session(weekType = WeekType.EVERY_WEEK), session(start = "09:00", end = "11:00", weekType = WeekType.EVERY_WEEK))
                is SectionSessionValidator.Result.Overlap
        )
        assertTrue(
            validate(session(weekType = WeekType.EVERY_WEEK), session(start = "09:00", end = "11:00", weekType = WeekType.ODD_WEEKS))
                is SectionSessionValidator.Result.Overlap
        )
        assertTrue(
            validate(session(weekType = WeekType.EVERY_WEEK), session(start = "09:00", end = "11:00", weekType = WeekType.EVEN_WEEKS))
                is SectionSessionValidator.Result.Overlap
        )
    }

    @Test
    fun `same parity clashes but odd versus even does not`() {
        assertTrue(
            validate(session(weekType = WeekType.ODD_WEEKS), session(start = "09:00", end = "11:00", weekType = WeekType.ODD_WEEKS))
                is SectionSessionValidator.Result.Overlap
        )
        assertTrue(
            validate(session(weekType = WeekType.EVEN_WEEKS), session(start = "09:00", end = "11:00", weekType = WeekType.EVEN_WEEKS))
                is SectionSessionValidator.Result.Overlap
        )
        // Odd-week and even-week sessions never meet in the same week.
        assertTrue(
            validate(session(weekType = WeekType.ODD_WEEKS), session(start = "09:00", end = "11:00", weekType = WeekType.EVEN_WEEKS))
                is SectionSessionValidator.Result.Valid
        )
        assertTrue(
            validate(session(weekType = WeekType.EVEN_WEEKS), session(start = "09:00", end = "11:00", weekType = WeekType.ODD_WEEKS))
                is SectionSessionValidator.Result.Valid
        )
    }

    // ---------- invalid sessions ----------

    @Test
    fun `unparseable or reversed times and bad days are invalid`() {
        assertTrue(validate(session(start = "10:00", end = "08:00")) is SectionSessionValidator.Result.InvalidTime)
        assertTrue(validate(session(start = "abc", end = "10:00")) is SectionSessionValidator.Result.InvalidTime)
        assertTrue(validate(session(start = "", end = "10:00")) is SectionSessionValidator.Result.InvalidTime)
        assertTrue(validate(session(day = 7)) is SectionSessionValidator.Result.InvalidTime)
        assertTrue(validate(session(day = -1)) is SectionSessionValidator.Result.InvalidTime)
        assertEquals(1, (validate(session(), session(start = "10:00", end = "09:00")) as SectionSessionValidator.Result.InvalidTime).index)
    }

    @Test
    fun `first clashing pair is reported for multi-session groups`() {
        val result = validate(
            session(day = 0, start = "08:00", end = "10:00"),
            session(day = 1, start = "08:00", end = "10:00"),
            session(day = 1, start = "09:00", end = "11:00")
        )
        assertTrue(result is SectionSessionValidator.Result.Overlap)
        result as SectionSessionValidator.Result.Overlap
        assertEquals(1, result.firstIndex)
        assertEquals(2, result.secondIndex)
    }

    @Test
    fun `empty session list is valid and overlap helper mirrors the rule`() {
        assertTrue(validate() is SectionSessionValidator.Result.Valid)
        assertFalse(
            SectionSessionValidator.overlaps(
                session(weekType = WeekType.ODD_WEEKS),
                session(start = "09:00", end = "11:00", weekType = WeekType.EVEN_WEEKS)
            )
        )
        assertTrue(
            SectionSessionValidator.overlaps(
                session(weekType = WeekType.ODD_WEEKS),
                session(start = "09:00", end = "11:00", weekType = WeekType.ODD_WEEKS)
            )
        )
    }
}
