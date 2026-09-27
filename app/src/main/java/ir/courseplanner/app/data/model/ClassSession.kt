package ir.courseplanner.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class WeekType(val titleFa: String, val titleEn: String) {
    EVERY_WEEK("هر هفته", "Every Week"),
    EVEN_WEEKS("هفته‌های زوج", "Even Weeks"),
    ODD_WEEKS("هفته‌های فرد", "Odd Weeks");

    fun overlapsWith(other: WeekType): Boolean {
        if (this == EVERY_WEEK || other == EVERY_WEEK) return true
        return this == other
    }
}

@Entity(
    tableName = "class_sessions",
    foreignKeys = [
        ForeignKey(
            entity = CourseSection::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sectionId"])]
)
data class ClassSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sectionId: Long,
    val dayOfWeek: Int, // 0 = شنبه (Sat), 1 = یکشنبه (Sun), 2 = دوشنبه (Mon), 3 = سه‌شنبه (Tue), 4 = چهارشنبه (Wed), 5 = پنج‌شنبه (Thu)
    val startTime: String, // HH:mm
    val endTime: String,   // HH:mm
    val location: String = "",
    val weekType: WeekType = WeekType.EVERY_WEEK
) {
    /**
     * Converts time string "HH:mm" to minutes from midnight.
     */
    val startMinutes: Int
        get() = timeToMinutes(startTime)

    val endMinutes: Int
        get() = timeToMinutes(endTime)

    companion object {
        private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
        private const val ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"

        /** Normalizes Persian/Arabic-Indic digits to ASCII so "۸:۳۰" parses like "8:30". */
        fun normalizeDigits(input: String): String {
            var out = input
            PERSIAN_DIGITS.forEachIndexed { i, c -> out = out.replace(c, '0' + i) }
            ARABIC_DIGITS.forEachIndexed { i, c -> out = out.replace(c, '0' + i) }
            return out
        }

        /**
         * Strictly parses "H:mm"/"HH:mm" (00–23 : 00–59) to minutes from midnight.
         * Returns null for anything else ("abc", "25:90", "08:00 - 10:00", "") —
         * callers must handle null explicitly instead of silently using 00:00.
         */
        fun parseTimeMinutesOrNull(raw: String): Int? {
            val parts = normalizeDigits(raw).trim().split(":")
            if (parts.size != 2) return null
            // Reject empty components and stray whitespace inside ("8 :00").
            if (parts[0].isBlank() || parts[1].isBlank()) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return h * 60 + m
        }

        /**
         * Legacy lenient conversion. Invalid input yields 0 — only use it for
         * already-validated data (conflict math, timetable layout). For any new
         * parsing/validation use [parseTimeMinutesOrNull].
         */
        fun timeToMinutes(time: String): Int = parseTimeMinutesOrNull(time) ?: 0

        fun getDayName(dayIndex: Int, isFarsi: Boolean = true): String {
            return if (isFarsi) {
                when (dayIndex) {
                    0 -> "شنبه"
                    1 -> "یکشنبه"
                    2 -> "دوشنبه"
                    3 -> "سه‌شنبه"
                    4 -> "چهارشنبه"
                    5 -> "پنج‌شنبه"
                    6 -> "جمعه"
                    else -> "نامشخص"
                }
            } else {
                when (dayIndex) {
                    0 -> "Saturday"
                    1 -> "Sunday"
                    2 -> "Monday"
                    3 -> "Tuesday"
                    4 -> "Wednesday"
                    5 -> "Thursday"
                    6 -> "Friday"
                    else -> "Unknown"
                }
            }
        }
    }
}
