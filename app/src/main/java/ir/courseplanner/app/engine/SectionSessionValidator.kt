package ir.courseplanner.app.engine

import ir.courseplanner.app.data.model.ClassSession

/**
 * Validates the class sessions that belong to ONE section (group).
 *
 * A section must never persist a self-contradicting session list:
 * - every session needs a day in 0..6 and a parseable start/end with start < end;
 * - two sessions of the same section may not overlap on the same day, because
 *   the student cannot sit in two classes of the same group at the same time.
 *
 * Week parity follows [ir.courseplanner.app.data.model.WeekType.overlapsWith]:
 * EVERY+EVERY, EVERY+ODD, EVERY+EVEN, ODD+ODD and EVEN+EVEN overlap;
 * ODD+EVEN never does (they meet in different weeks).
 *
 * This is the single source of truth for intra-section validation:
 * - the manual add/edit dialogs call it before submitting (inline error), and
 * - [ir.courseplanner.app.data.repository.CourseRepository] re-checks it before
 *   persisting, so a non-UI caller can never write a broken section.
 *
 * Imported catalog data is deliberately NOT filtered through this rule: the
 * HTML/CSV/JSON parsers own and validate their own format, and a re-import must
 * keep working exactly as before.
 */
object SectionSessionValidator {

    sealed interface Result {
        data object Valid : Result

        /** Session [index] (0-based) has a missing/invalid day or start >= end. */
        data class InvalidTime(val index: Int) : Result

        /** Sessions [firstIndex] and [secondIndex] clash on [dayOfWeek] in [overlapRange]. */
        data class Overlap(
            val firstIndex: Int,
            val secondIndex: Int,
            val dayOfWeek: Int,
            val overlapRange: String
        ) : Result
    }

    /** True when the two sessions clash (same day + overlapping weeks + overlapping clock). */
    fun overlaps(a: ClassSession, b: ClassSession): Boolean {
        if (a.dayOfWeek != b.dayOfWeek) return false
        if (!a.weekType.overlapsWith(b.weekType)) return false
        return ScheduleEngine.timesOverlap(a.startMinutes, a.endMinutes, b.startMinutes, b.endMinutes)
    }

    /** Validates one section's session list against itself. */
    fun validate(sessions: List<ClassSession>): Result {
        sessions.forEachIndexed { index, session ->
            val start = ClassSession.parseTimeMinutesOrNull(session.startTime)
            val end = ClassSession.parseTimeMinutesOrNull(session.endTime)
            if (session.dayOfWeek !in 0..6 || start == null || end == null || start >= end) {
                return Result.InvalidTime(index)
            }
        }
        for (i in sessions.indices) {
            for (j in i + 1 until sessions.size) {
                val a = sessions[i]
                val b = sessions[j]
                if (overlaps(a, b)) {
                    return Result.Overlap(i, j, a.dayOfWeek, describeOverlap(a, b))
                }
            }
        }
        return Result.Valid
    }

    /** The clashing window, e.g. "09:00–11:00" (used in user-facing messages). */
    fun describeOverlap(a: ClassSession, b: ClassSession): String {
        val from = maxOf(a.startMinutes, b.startMinutes)
        val to = minOf(a.endMinutes, b.endMinutes)
        return "${clockOf(from)}–${clockOf(to)}"
    }

    private fun clockOf(minutes: Int): String =
        "%02d:%02d".format(minutes / 60, minutes % 60)
}
