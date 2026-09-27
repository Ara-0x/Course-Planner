package ir.courseplanner.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anchors verified by hand: 1 Farvardin 1403 = Wednesday 2024-03-20
 * (epoch day 19802), and 1403 is a leap year, so 30 Esfand 1403 exists.
 * Plain JUnit (no Robolectric): pure Kotlin date math.
 */
class JalaliDateTest {

    @Test
    fun `known anchor converts both ways`() {
        assertEquals(19802L, JalaliDate.toEpochDay(1403, 1, 1))
        assertEquals(JalaliYmd(1403, 1, 1), JalaliDate.fromEpochDay(19802L))
    }

    @Test
    fun `leap year has 30 esfand`() {
        assertTrue(JalaliDate.isLeapYear(1403))
        assertTrue(JalaliDate.isValid(1403, 12, 30))
        assertEquals(20167L, JalaliDate.toEpochDay(1403, 12, 30))
        assertEquals(JalaliYmd(1403, 12, 30), JalaliDate.fromEpochDay(20167L))
        // 1402 is not a leap year.
        assertFalse(JalaliDate.isLeapYear(1402))
        assertFalse(JalaliDate.isValid(1402, 12, 30))
        assertNull(JalaliDate.toEpochDay(1402, 12, 30))
    }

    @Test
    fun `invalid dates rejected`() {
        assertFalse(JalaliDate.isValid(1404, 13, 1))
        assertFalse(JalaliDate.isValid(1404, 0, 10))
        assertFalse(JalaliDate.isValid(1404, 7, 0))
        assertFalse(JalaliDate.isValid(1404, 7, 31))
        assertFalse(JalaliDate.isValid(1404, 6, 32))
        assertNull(JalaliDate.toEpochDay(1404, 13, 1))
    }

    @Test
    fun `parse accepts separators and persian digits`() {
        assertEquals(JalaliYmd(1404, 7, 5), JalaliDate.parse("1404/07/05"))
        assertEquals(JalaliYmd(1404, 7, 5), JalaliDate.parse("1404-7-5"))
        assertEquals(JalaliYmd(1404, 7, 5), JalaliDate.parse("۱۴۰۴/۰۷/۰۵"))
        assertEquals(JalaliYmd(1404, 7, 5), JalaliDate.parse("  1404 / 7 / 5  "))
        assertNull(JalaliDate.parse(""))
        assertNull(JalaliDate.parse("1404/13/01"))
        assertNull(JalaliDate.parse("not a date"))
        assertNull(JalaliDate.parse("1404/07"))
    }

    @Test
    fun `format zero pads`() {
        assertEquals("1404/07/05", JalaliDate.format(1404, 7, 5))
        assertEquals("1404/07/05", JalaliYmd(1404, 7, 5).toString())
    }

    @Test
    fun `round trip across year boundary`() {
        val dates = listOf(
            JalaliYmd(1403, 12, 29), JalaliYmd(1403, 12, 30),
            JalaliYmd(1404, 1, 1), JalaliYmd(1404, 6, 31),
            JalaliYmd(1404, 7, 1), JalaliYmd(1402, 5, 15)
        )
        for (d in dates) {
            val epoch = JalaliDate.toEpochDay(d.year, d.month, d.day)
            assertTrue("valid: $d", epoch != null)
            assertEquals(d, JalaliDate.fromEpochDay(epoch!!))
        }
    }

    @Test
    fun `app weekday index starts saturday`() {
        // 1970-01-03 was a Saturday.
        assertEquals(0, JalaliDate.appDayIndexOfEpochDay(2L))
        // 2024-03-20 (1403/01/01) was a Wednesday.
        assertEquals(4, JalaliDate.appDayIndexOfEpochDay(19802L))
    }
}
