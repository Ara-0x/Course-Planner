package ir.courseplanner.app.data.importer

import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.WeekType
import kotlin.math.roundToInt

/**
 * Parses the "Presented Courses" HTML page saved from university portals
 * (Pooya, Golestan, Sama, …) into [ImportItem]s.
 *
 * Expected table columns (Pooya layout):
 *   ردیف | شماره درس | گروه | نام درس | واحد | ثبت‌نام‌شده | ظرفیت |
 *   دانشکده | نام استاد | کد درس اصلی | نام درس اصلی | ثبت‌نام‌شده کل | رزرو | جزئیات
 *
 * Class-session details live inside the info-icon `title` tooltip, e.g.
 * `… جلسه اول روز: دوشنبه ساعت 8(هر هفته به مدت 120 دقیقه در کارگاه 2) شروع زوج …`
 *
 * Design notes:
 * - Pure Kotlin, zero new dependencies (regex + string ops only).
 * - Imported courses are catalog-only: `isSelectedForGeneration = false`, so a
 *   200+ row portal file never floods the "my courses" list or the generator.
 * - Users add courses by code from the Courses screen quick-add card.
 */
object PooyaHtmlParser {

    private val tableRegex =
        Regex("<table[^>]*>(.*?)</table>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val rowRegex =
        Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val cellRegex =
        Regex("<td[^>]*>(.*?)</td>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tagRegex = Regex("<[^>]*>")
    private val titleAttrRegex = Regex("title\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val titleAttrSingleRegex = Regex("title\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE)

    // جلسه اول روز: دوشنبه ساعت 14(هر هفته به مدت 120 دقیقه در کارگاه 1) شروع زوج
    private val sessionRegex = Regex(
        "جلسه\\s*(?:اول|دوم|سوم|چهارم|پنجم)?\\s*روز:\\s*(.*?)\\s*ساعت\\s*(\\d+)(?::(\\d+))?\\s*\\(([^)]+)\\)\\s*(?:شروع\\s*(زوج|فرد))?"
    )
    private val durationRegex = Regex("به مدت\\s*(\\d+)\\s*دقیقه")
    private val minutesAfterRegex = Regex("دقیقه\\s*در\\s+(.+)")
    private val examRegex = Regex(
        "امتحان روز:\\s*([^\\s]*)\\s*ساعت\\s*(\\d+)(?::(\\d+))?[^در]*در کلاس\\s*([^\\s]*)\\s*(?:به تاریخ\\s*([^\\s<]+))?"
    )
    private val degreeRegex =
        Regex("مقطع:\\s*(.+?)(?=\\s*(?:گروه آموزشی|جلسه|امتحان|قابل انتخاب|$))")
    private val departmentRegex =
        Regex("گروه آموزشی:\\s*(.+?)(?=\\s*(?:جلسه|امتحان|قابل انتخاب|$))")
    private val notesRegex =
        Regex("(?:تذکر|تدکر)\\s*:?\\s*(.+?)(?=\\s*(?:مقطع|گروه آموزشی|جلسه|امتحان|قابل انتخاب|$))")

    private val persianDigits = "۰۱۲۳۴۵۶۷۸۹"
    private val arabicDigits = "٠١٢٣٤٥٦٧٨٩"

    fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val p = persianDigits.indexOf(ch)
            if (p >= 0) {
                sb.append(('0' + p))
                continue
            }
            val a = arabicDigits.indexOf(ch)
            if (a >= 0) {
                sb.append(('0' + a))
                continue
            }
            sb.append(ch)
        }
        return sb.toString()
    }

    fun decodeEntities(raw: String): String {
        return raw
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
    }

    fun stripTags(html: String): String {
        return tagRegex.replace(html, " ")
            .replace("[\\u200B-\\u200D\\uFEFF]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    fun cleanText(htmlFragment: String): String = stripTags(decodeEntities(htmlFragment))

    /**
     * Single entry point. Returns [ImportResult] so the ViewModel import path
     * is identical to JSON/CSV imports.
     */
    fun parsePortalHtml(html: String): ImportResult {
        if (html.isBlank()) {
            return ImportResult.Failure("فایل HTML خالی است.")
        }
        return try {
            val items = parseRows(html)
            if (items.isEmpty()) {
                ImportResult.Failure(
                    "هیچ درسی در فایل HTML یافت نشد. مطمئن شوید فایل ذخیره‌شده " +
                        "صفحه «لیست دروس ارائه‌شده» پرتال است."
                )
            } else {
                val groupCount = items.sumOf { it.sections.size }
                ImportResult.Success(
                    items = items,
                    message = "تعداد ${items.size} درس در $groupCount گروه از فایل پرتال وارد کاتالوگ شد."
                )
            }
        } catch (e: Exception) {
            ImportResult.Failure("خطا در پردازش فایل پرتال: ${e.localizedMessage}")
        }
    }

    internal fun parseRows(html: String): List<ImportItem> {
        val tableHtml = findCoursesTable(html) ?: return emptyList()

        val coursesMap = linkedMapOf<String, MutableCourseAcc>()
        for (rowMatch in rowRegex.findAll(tableHtml)) {
            val rowHtml = rowMatch.groupValues[1]
            if (rowHtml.contains("<th", ignoreCase = true)) continue

            val cells = cellRegex.findAll(rowHtml).map { it.groupValues[1] }.toList()
            if (cells.size < 9) continue

            val code = normalizeDigits(cleanText(cells[1]))
            val groupCode = normalizeDigits(cleanText(cells[2])).ifBlank { "1" }
            val name = cleanText(cells[3])
            if (code.isBlank() || name.isBlank()) continue

            val credits = normalizeDigits(cleanText(cells[4])).toDoubleOrNull()
                ?.roundToInt()?.coerceAtLeast(1) ?: 1
            val enrolled = normalizeDigits(cleanText(cells[5])).toIntOrNull() ?: 0
            val capacity = normalizeDigits(cleanText(cells[6])).toIntOrNull() ?: 0
            val faculty = cleanText(cells[7]).ifBlank { "دانشکده اصلی" }
            val instructor = cleanText(cells[8]).replace("\\s+".toRegex(), " ").trim()

            val tooltip = normalizeDigits(cleanText(extractTooltip(rowHtml)))
            val sessions = parseSessions(tooltip)
            val exam = parseExam(tooltip)
            val degree = degreeRegex.find(tooltip)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val department = departmentRegex.find(tooltip)?.groupValues?.getOrNull(1)?.trim()
                .orEmpty().ifBlank { "كامپيوتر" }
            val notes = notesRegex.find(tooltip)?.groupValues?.getOrNull(1)?.trim()

            val section = ImportSectionItem(
                section = CourseSection(
                    courseId = 0,
                    sectionCode = groupCode,
                    instructor = instructor,
                    capacity = capacity,
                    examDate = exam?.date.orEmpty(),
                    examStartTime = exam?.time.orEmpty(),
                    examEndTime = "",
                    isEnrolled = false
                ),
                sessions = sessions
            )

            val acc = coursesMap.getOrPut(code) {
                MutableCourseAcc(
                    course = Course(
                        code = code,
                        name = name,
                        department = department,
                        credits = credits,
                        // Catalog-only: never floods "my courses" or the generator.
                        isSelectedForGeneration = false,
                        degree = degree
                    ),
                    faculty = faculty,
                    degree = degree,
                    enrolled = enrolled,
                    notes = notes,
                    sections = mutableListOf()
                )
            }
            if (acc.sections.none { it.section.sectionCode == groupCode }) {
                acc.sections.add(section)
            }
        }

        return coursesMap.values.map { acc ->
            ImportItem(course = acc.course, sections = acc.sections)
        }
    }

    private data class MutableCourseAcc(
        val course: Course,
        val faculty: String,
        val degree: String,
        val enrolled: Int,
        val notes: String?,
        val sections: MutableList<ImportSectionItem>
    )

    internal data class ParsedExam(val date: String, val time: String)

    private fun findCoursesTable(html: String): String? {
        val tables = tableRegex.findAll(html).map { it.groupValues[1] }.toList()
        // Prefer the table that actually holds the course list headers.
        for (t in tables) {
            val text = stripTags(t)
            if (text.contains("شماره درس") && text.contains("نام درس")) return t
        }
        // Fallback: Pooya renders the list with border="1".
        val borderTable = Regex(
            "<table[^>]*border\\s*=\\s*[\"']?1[\"']?[^>]*>(.*?)</table>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)
        if (borderTable != null) return borderTable.groupValues[1]
        return null
    }

    private fun extractTooltip(rowHtml: String): String {
        val candidates = titleAttrRegex.findAll(rowHtml).map { it.groupValues[1] } +
            titleAttrSingleRegex.findAll(rowHtml).map { it.groupValues[1] }
        for (raw in candidates) {
            if (raw.contains("جلسه") || raw.contains("body=") ||
                raw.contains("header=") || raw.contains("امتحان")
            ) {
                return raw
            }
        }
        return ""
    }

    internal fun parseSessions(cleanedTooltip: String): List<ClassSession> {
        val sessions = mutableListOf<ClassSession>()
        if (cleanedTooltip.isBlank()) return sessions

        for (m in sessionRegex.findAll(cleanedTooltip)) {
            val day = normalizeDayName(m.groupValues[1].trim())
            if (day.isEmpty()) continue
            val dayOfWeek = dayNameToNumber(day)
            if (dayOfWeek == -1) continue

            val hour = m.groupValues[2].toIntOrNull() ?: continue
            val minute = m.groupValues[3].toIntOrNull() ?: 0
            val details = m.groupValues[4]
            val parity = m.groupValues[5].ifBlank { "هردو" }

            val duration = durationRegex.find(details)?.groupValues?.getOrNull(1)
                ?.toIntOrNull() ?: 90
            // Placeholder rows (project/internship) carry duration 0 and no real class time.
            if (duration == 0) continue

            var location = minutesAfterRegex.find(details)?.groupValues?.getOrNull(1)
                ?.trim().orEmpty()
            if (location == "0") location = ""

            val biweekly = details.contains("هفته در میان")
            // Mirror of the web parser: explicit parity counts only for biweekly
            // sessions; plain "شروع زوج/فرد" on a weekly row stays EVERY_WEEK so the
            // conflict engine treats it conservatively.
            val weekType = if (biweekly) {
                when (parity) {
                    "زوج" -> WeekType.EVEN_WEEKS
                    "فرد" -> WeekType.ODD_WEEKS
                    else -> WeekType.EVERY_WEEK
                }
            } else {
                WeekType.EVERY_WEEK
            }

            val startTotal = hour * 60 + minute
            val endTotal = startTotal + duration
            sessions.add(
                ClassSession(
                    sectionId = 0,
                    dayOfWeek = dayOfWeek,
                    startTime = "%02d:%02d".format(startTotal / 60, startTotal % 60),
                    endTime = "%02d:%02d".format(endTotal / 60, endTotal % 60),
                    location = location,
                    weekType = weekType
                )
            )
        }
        return sessions
    }

    internal fun parseExam(cleanedTooltip: String): ParsedExam? {
        if (cleanedTooltip.isBlank()) return null
        val m = examRegex.find(cleanedTooltip) ?: return null
        val dayDesc = m.groupValues[1].trim()
        val hour = m.groupValues[2].toIntOrNull() ?: 0
        val minute = m.groupValues[3].toIntOrNull() ?: 0
        val rawLoc = m.groupValues[4].trim()
        val loc = if (rawLoc == "0") "" else rawLoc
        val date = m.groupValues[5].trim()
        // Dummy portal rows use hour 6, class 0 and no date/day.
        if (date.isEmpty() && loc.isEmpty() && (hour == 6 || hour == 0) && dayDesc.isEmpty()) {
            return null
        }
        return ParsedExam(
            date = date,
            time = "%02d:%02d".format(hour, minute)
        )
    }

    internal fun normalizeDayName(day: String): String {
        val clean = day.replace("[\u200B-\u200D\uFEFF]".toRegex(), "")
            .replace("\\s+".toRegex(), " ").trim()
        return when {
            clean.contains("جمعه") -> "جمعه"
            clean.contains("پنج") -> "پنج‌شنبه"
            clean.contains("چهار") -> "چهارشنبه"
            clean.contains("سه") -> "سه‌شنبه"
            clean.contains("دو") -> "دوشنبه"
            clean.contains("یک") -> "یکشنبه"
            clean.contains("شنبه") -> "شنبه"
            else -> ""
        }
    }

    internal fun dayNameToNumber(day: String): Int = when (day) {
        "شنبه" -> 0
        "یکشنبه" -> 1
        "دوشنبه" -> 2
        "سه‌شنبه" -> 3
        "چهارشنبه" -> 4
        "پنج‌شنبه" -> 5
        "جمعه" -> 6
        else -> -1
    }
}
