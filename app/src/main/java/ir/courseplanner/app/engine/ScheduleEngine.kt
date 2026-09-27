package ir.courseplanner.app.engine

import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Conflict
import ir.courseplanner.app.data.model.ConflictType
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.WeekType

enum class OptimizationPreference(val titleFa: String, val descriptionFa: String) {
    BALANCED("بهترین توازن کلی", "ترکیب بهینه حداقل روز و کمترین فاصله خالی"),
    MIN_GAPS("حداقل گپ و زمان مرده", "کلاس‌های پشت سر هم با کمترین زمان انتظار در دانشگاه"),
    MIN_DAYS("فشرده‌ترین روزها", "کمترین تعداد روزهای حضور در دانشگاه"),
    NO_EARLY_MORNING("شروع دیرتر (بدون ۸ صبح)", "اولویت با برنامه‌هایی که کلاس ۸ صبح ندارند")
}

data class ScoredSchedule(
    val schedule: List<SectionWithDetails>,
    val score: Int, // 0 to 100, always the real computed score (never faked)
    val totalGapMinutes: Int,
    val activeDaysCount: Int,
    val earlyMorningClassCount: Int,
    val totalWeeklyHours: Float,
    val tags: List<String>,
    /** Explainable parts: base 100 plus weighted deductions (negative deltas). */
    val breakdown: List<ScoreComponent> = emptyList()
)

/** One explainable line of the quality score. [delta] is negative for deductions. */
data class ScoreComponent(
    val labelFa: String,
    val delta: Int
)

/** One selected course as seen by the generator (name kept for user-facing reports). */
data class GeneratorCourse(
    val courseName: String,
    val sections: List<SectionWithDetails>
)

/** Full outcome of a generation run — never silently drops a course. */
data class ScheduleSearchResult(
    /** Top-K schedules, best first. */
    val ranked: List<ScoredSchedule>,
    /** Every valid combination found (before the top-K cut). */
    val totalValid: Int,
    /** True when the leaf cap stopped the search early (best-so-far is kept). */
    val truncated: Boolean,
    /** Selected courses that had zero usable sections and were reported, not dropped. */
    val skippedCourses: List<String>
)

object ScheduleEngine {

    /**
     * Checks if two time intervals overlap (strictly overlap, touching borders is not an overlap).
     */
    fun timesOverlap(startA: Int, endA: Int, startB: Int, endB: Int): Boolean {
        return maxOf(startA, startB) < minOf(endA, endB)
    }

    /**
     * Checks if two sections conflict in either class schedule or exam schedule.
     * Takes week recurrence (every week, even, odd) into account.
     */
    fun checkConflict(sectionA: SectionWithDetails, sectionB: SectionWithDetails): Conflict? {
        // 1. Check class session overlap
        for (sessA in sectionA.sessions) {
            for (sessB in sectionB.sessions) {
                if (sessA.dayOfWeek == sessB.dayOfWeek) {
                    // Check week parity overlap (e.g., EVEN vs ODD weeks do NOT conflict)
                    if (sessA.weekType.overlapsWith(sessB.weekType)) {
                        if (timesOverlap(sessA.startMinutes, sessA.endMinutes, sessB.startMinutes, sessB.endMinutes)) {
                            val dayNameFa = ClassSession.getDayName(sessA.dayOfWeek, isFarsi = true)
                            val dayNameEn = ClassSession.getDayName(sessA.dayOfWeek, isFarsi = false)
                            val clashTime = "${maxOf(sessA.startTime, sessB.startTime)} - ${minOf(sessA.endTime, sessB.endTime)}"
                            val weekDetailFa = if (sessA.weekType != WeekType.EVERY_WEEK || sessB.weekType != WeekType.EVERY_WEEK) {
                                " (${sessA.weekType.titleFa} / ${sessB.weekType.titleFa})"
                            } else ""

                            return Conflict(
                                type = ConflictType.CLASS_TIME_OVERLAP,
                                sectionA = sectionA,
                                sectionB = sectionB,
                                description = "Time clash between ${sectionA.courseName} (Sec ${sectionA.sectionCode}) and ${sectionB.courseName} (Sec ${sectionB.sectionCode}) on $dayNameEn ($clashTime)",
                                descriptionFa = "تداخل کلاسی در روز $dayNameFa$weekDetailFa ساعت $clashTime بین «${sectionA.courseName}» و «${sectionB.courseName}»",
                                dayOfWeek = sessA.dayOfWeek,
                                timeRange = clashTime
                            )
                        }
                    }
                }
            }
        }

        // 2. Check exam date & time overlap — DEFINITE conflicts only.
        // Same day with unknown/missing times is NOT a conflict here; see
        // checkExamWarning() which reports it as insufficient-data instead.
        val examDateA = sectionA.examDate.trim()
        val examDateB = sectionB.examDate.trim()
        if (examDateA.isNotBlank() && examDateB.isNotBlank() && examDateA == examDateB) {
            val startA = ClassSession.parseTimeMinutesOrNull(sectionA.section.examStartTime)
            val endA = ClassSession.parseTimeMinutesOrNull(sectionA.section.examEndTime)
            val startB = ClassSession.parseTimeMinutesOrNull(sectionB.section.examStartTime)
            val endB = ClassSession.parseTimeMinutesOrNull(sectionB.section.examEndTime)

            if (startA != null && endA != null && startB != null && endB != null &&
                startA < endA && startB < endB
            ) {
                if (timesOverlap(startA, endA, startB, endB)) {
                    val clashTime = "${maxOf(sectionA.section.examStartTime, sectionB.section.examStartTime)} - ${minOf(sectionA.section.examEndTime, sectionB.section.examEndTime)}"
                    return Conflict(
                        type = ConflictType.EXAM_OVERLAP,
                        sectionA = sectionA,
                        sectionB = sectionB,
                        description = "Final exam overlap on $examDateA ($clashTime) between ${sectionA.courseName} and ${sectionB.courseName}",
                        descriptionFa = "تداخل تاریخ امتحان در $examDateA ساعت $clashTime بین «${sectionA.courseName}» و «${sectionB.courseName}»",
                        timeRange = "$examDateA $clashTime"
                    )
                }
            }
        }

        return null
    }

    /**
     * Same exam day, but at least one side lacks usable start/end times.
     * Returns an [ConflictType.EXAM_SAME_DAY_WARNING] (insufficient data),
     * or null when there is nothing to warn about:
     * - different/blank exam days → null
     * - both sides fully timed (overlap or disjoint is owned by [checkConflict]) → null
     *
     * The generator and conflict banners must treat warnings as non-blocking.
     */
    fun checkExamWarning(sectionA: SectionWithDetails, sectionB: SectionWithDetails): Conflict? {
        val examDateA = sectionA.examDate.trim()
        val examDateB = sectionB.examDate.trim()
        if (examDateA.isBlank() || examDateA != examDateB) return null

        val aFull = ClassSession.parseTimeMinutesOrNull(sectionA.section.examStartTime) != null &&
            ClassSession.parseTimeMinutesOrNull(sectionA.section.examEndTime) != null
        val bFull = ClassSession.parseTimeMinutesOrNull(sectionB.section.examStartTime) != null &&
            ClassSession.parseTimeMinutesOrNull(sectionB.section.examEndTime) != null
        if (aFull && bFull) return null

        return Conflict(
            type = ConflictType.EXAM_SAME_DAY_WARNING,
            sectionA = sectionA,
            sectionB = sectionB,
            description = "Same exam day ($examDateA) for ${sectionA.courseName} and ${sectionB.courseName}, but exam times are incomplete",
            descriptionFa = "هر دو امتحان در تاریخ $examDateA است ولی ساعت دقیق حداقل یکی مشخص نیست؛ لطفاً برنامه امتحانات را بررسی کنید («${sectionA.courseName}» و «${sectionB.courseName}»)",
            timeRange = examDateA
        )
    }

    /** All same-day-with-unknown-time exam pairs (warnings, non-blocking). */
    fun findExamWarnings(sections: List<SectionWithDetails>): List<Conflict> {
        val warnings = mutableListOf<Conflict>()
        for (i in 0 until sections.size) {
            for (j in i + 1 until sections.size) {
                val warning = checkExamWarning(sections[i], sections[j])
                if (warning != null) warnings.add(warning)
            }
        }
        return warnings
    }

    /**
     * Finds all pairwise conflicts in a list of sections.
     */
    fun findAllConflicts(sections: List<SectionWithDetails>): List<Conflict> {
        val conflicts = mutableListOf<Conflict>()
        for (i in 0 until sections.size) {
            for (j in i + 1 until sections.size) {
                val conflict = checkConflict(sections[i], sections[j])
                if (conflict != null) {
                    conflicts.add(conflict)
                }
            }
        }
        return conflicts
    }

    /**
     * Checks if a candidate section conflicts with any already enrolled sections.
     */
    fun findConflictWithCurrent(
        candidate: SectionWithDetails,
        currentSections: List<SectionWithDetails>
    ): Conflict? {
        for (enrolled in currentSections) {
            if (enrolled.section.id == candidate.section.id) continue
            if (enrolled.course.id == candidate.course.id) continue
            val conflict = checkConflict(candidate, enrolled)
            if (conflict != null) return conflict
        }
        return null
    }

    /**
     * Searches conflict-free schedules with branch & bound, keeping the best
     * top-K instead of the first-K in DFS order.
     *
     * Guarantees:
     * - Courses with zero usable sections are REPORTED in [ScheduleSearchResult.skippedCourses],
     *   never silently dropped.
     * - The full space is explored (conflict-pruned) up to [maxLeaves] leaves, so the
     *   best schedule is not missed just because it sorts after an arbitrary cutoff.
     * - Constrained courses first (MRV) for earlier pruning; exam same-day warnings
     *   never block a combination (only definite conflicts do).
     */
    fun generateTopSchedules(
        courses: List<GeneratorCourse>,
        preference: OptimizationPreference = OptimizationPreference.BALANCED,
        topK: Int = 12,
        maxLeaves: Int = 200_000
    ): ScheduleSearchResult {
        val skipped = courses.filter { it.sections.isEmpty() }.map { it.courseName }
        val ordered = courses.filter { it.sections.isNotEmpty() }.sortedBy { it.sections.size }
        if (ordered.isEmpty()) {
            return ScheduleSearchResult(emptyList(), 0, false, skipped)
        }

        val top = mutableListOf<ScoredSchedule>()
        var totalValid = 0
        var leaves = 0
        var truncated = false
        val current = mutableListOf<SectionWithDetails>()

        fun insertCandidate(scored: ScoredSchedule) {
            if (top.size < topK) {
                top.add(scored)
                top.sortByDescending { it.score }
            } else if (topK > 0 && scored.score > top.last().score) {
                top[top.lastIndex] = scored
                top.sortByDescending { it.score }
            }
        }

        fun backtrack(courseIndex: Int) {
            if (truncated) return
            if (courseIndex == ordered.size) {
                leaves++
                if (leaves > maxLeaves) {
                    truncated = true
                    return
                }
                totalValid++
                insertCandidate(evaluateSchedule(current.toList(), preference))
                return
            }
            for (candidate in ordered[courseIndex].sections) {
                if (truncated) return
                var hasConflict = false
                for (placed in current) {
                    if (checkConflict(candidate, placed) != null) {
                        hasConflict = true
                        break
                    }
                }
                if (!hasConflict) {
                    current.add(candidate)
                    backtrack(courseIndex + 1)
                    current.removeAt(current.size - 1)
                }
            }
        }

        backtrack(0)
        return ScheduleSearchResult(
            ranked = markBest(top.toList()),
            totalValid = totalValid,
            truncated = truncated,
            skippedCourses = skipped
        )
    }

    /**
     * Legacy entry point kept for compatibility: returns the best-first raw
     * schedules (up to [maxCombinations]), same shape as before.
     */
    fun generateConflictFreeSchedules(
        courseGroups: List<List<SectionWithDetails>>,
        maxCombinations: Int = 100
    ): List<List<SectionWithDetails>> {
        return generateTopSchedules(
            courses = courseGroups.map { GeneratorCourse(courseName = "", sections = it) },
            topK = maxCombinations
        ).ranked.map { it.schedule }
    }

    /** Prepends the "top recommendation" tag without touching the real score. */
    private fun markBest(ranked: List<ScoredSchedule>): List<ScoredSchedule> {
        if (ranked.isEmpty()) return ranked
        val top = ranked[0]
        return listOf(top.copy(tags = listOf("پیشنهاد برتر هوشمند") + top.tags)) + ranked.drop(1)
    }

    /** Academic week parity, counted from the semester start (week 1 = first 7 days). */
    enum class WeekParity(val titleFa: String) {
        EVEN("زوج"),
        ODD("فرد")
    }

    /** Which academic week [todayEpochDay] falls in, or null when unknowable. */
    data class AcademicWeek(
        /** 1-based week number since the semester start. */
        val number: Int,
        val parity: WeekParity
    )

    fun academicWeek(
        todayEpochDay: Long,
        semesterStartEpochDay: Long?,
        firstWeekIsOdd: Boolean
    ): AcademicWeek? {
        if (semesterStartEpochDay == null || todayEpochDay < semesterStartEpochDay) return null
        val weekNumber = ((todayEpochDay - semesterStartEpochDay) / 7 + 1).toInt()
        val isEven = if (firstWeekIsOdd) weekNumber % 2 == 0 else weekNumber % 2 == 1
        return AcademicWeek(weekNumber, if (isEven) WeekParity.EVEN else WeekParity.ODD)
    }

    /** True when this session actually meets in a week of the given parity. */
    fun occursInWeek(session: ClassSession, parity: WeekParity?): Boolean {
        if (parity == null) return true
        return when (session.weekType) {
            WeekType.EVERY_WEEK -> true
            WeekType.EVEN_WEEKS -> parity == WeekParity.EVEN
            WeekType.ODD_WEEKS -> parity == WeekParity.ODD
        }
    }

    /**
     * Analyzes idle gap time between classes on each day.
     * Returns total gap minutes across the week.
     *
     * Parity-aware: a day mixing weekly and biweekly sessions is scored as the
     * average of its even-week and odd-week gaps. Example — weekly 8–10, even
     * 10–12, weekly 12–14: even weeks have no gap, odd weeks have a 120-minute
     * dead window, so the honest weekly figure is 60, not 0.
     */
    fun calculateTotalGaps(sections: List<SectionWithDetails>): Int {
        val sessionsByDay = sections.flatMap { it.sessions }.groupBy { it.dayOfWeek }
        var totalGapMinutes = 0

        for ((_, daySessions) in sessionsByDay) {
            if (daySessions.size <= 1) continue
            if (daySessions.none { it.weekType != WeekType.EVERY_WEEK }) {
                totalGapMinutes += gapOf(daySessions)
            } else {
                val evenSet = daySessions.filter {
                    it.weekType == WeekType.EVERY_WEEK || it.weekType == WeekType.EVEN_WEEKS
                }
                val oddSet = daySessions.filter {
                    it.weekType == WeekType.EVERY_WEEK || it.weekType == WeekType.ODD_WEEKS
                }
                totalGapMinutes += (gapOf(evenSet) + gapOf(oddSet)) / 2
            }
        }
        return totalGapMinutes
    }

    private fun gapOf(daySessions: List<ClassSession>): Int {
        if (daySessions.size <= 1) return 0
        val sorted = daySessions.sortedBy { it.startMinutes }
        var gap = 0
        for (i in 0 until sorted.size - 1) {
            val currentEnd = sorted[i].endMinutes
            val nextStart = sorted[i + 1].startMinutes
            if (nextStart > currentEnd) {
                gap += (nextStart - currentEnd)
            }
        }
        return gap
    }

    /**
     * True average class minutes per week: biweekly sessions count half, since
     * they meet every other week. Days without parity sessions are unchanged.
     */
    fun averageWeeklyMinutes(sections: List<SectionWithDetails>): Int {
        val all = sections.flatMap { it.sessions }
        if (all.none { it.weekType != WeekType.EVERY_WEEK }) {
            return all.sumOf { maxOf(0, it.endMinutes - it.startMinutes) }
        }
        fun minutesOf(parity: WeekParity): Int {
            return all.filter {
                it.weekType == WeekType.EVERY_WEEK ||
                    (parity == WeekParity.EVEN && it.weekType == WeekType.EVEN_WEEKS) ||
                    (parity == WeekParity.ODD && it.weekType == WeekType.ODD_WEEKS)
            }.sumOf { maxOf(0, it.endMinutes - it.startMinutes) }
        }
        return (minutesOf(WeekParity.EVEN) + minutesOf(WeekParity.ODD)) / 2
    }

    /**
     * Evaluates and scores a schedule based on user preferences.
     * Optimization goals:
     * - Minimize idle gap time between classes in the same day.
     * - Minimize active days count.
     * - Avoid 8 AM classes if preferred.
     */
    fun evaluateSchedule(
        schedule: List<SectionWithDetails>,
        preference: OptimizationPreference = OptimizationPreference.BALANCED
    ): ScoredSchedule {
        val allSessions = schedule.flatMap { it.sessions }
        val activeDays = allSessions.map { it.dayOfWeek }.toSet().size
        val totalGaps = calculateTotalGaps(schedule)
        val earlyMorningCount = allSessions.count { it.startMinutes <= 480 } // 8:00 AM or earlier

        val totalMinutes = averageWeeklyMinutes(schedule)

        // Base score starts at 100; every deduction below is recorded in the
        // breakdown so the UI can explain exactly why a schedule won.
        val breakdown = mutableListOf<ScoreComponent>()
        breakdown.add(ScoreComponent("امتیاز پایه", 100))

        // Deduct points for gaps (each 30 min of idle gap drops 3 points)
        val gapDeduction = (totalGaps / 30.0) * 3.0
        // Deduct points for high number of days (each day above 3 drops 5 points)
        val dayDeduction = maxOf(0, activeDays - 3) * 6.0
        // Deduct points for 8 AM classes
        val earlyDeduction = earlyMorningCount * 4.0

        val weightedGap: Double
        val weightedDay: Double
        val weightedEarly: Double
        when (preference) {
            OptimizationPreference.BALANCED -> {
                weightedGap = gapDeduction * 1.0
                weightedDay = dayDeduction * 1.0
                weightedEarly = earlyDeduction * 0.5
            }
            OptimizationPreference.MIN_GAPS -> {
                weightedGap = gapDeduction * 2.2
                weightedDay = dayDeduction * 0.5
                weightedEarly = earlyDeduction * 0.3
            }
            OptimizationPreference.MIN_DAYS -> {
                weightedDay = dayDeduction * 2.5
                weightedGap = gapDeduction * 0.7
                weightedEarly = earlyDeduction * 0.3
            }
            OptimizationPreference.NO_EARLY_MORNING -> {
                weightedEarly = earlyDeduction * 3.0
                weightedGap = gapDeduction * 0.8
                weightedDay = dayDeduction * 0.6
            }
        }

        val gapDelta = -weightedGap.toInt()
        val dayDelta = -weightedDay.toInt()
        val earlyDelta = -weightedEarly.toInt()
        if (gapDelta != 0) {
            breakdown.add(ScoreComponent("گپ بین کلاس‌ها ($totalGaps دقیقه)", gapDelta))
        }
        if (dayDelta != 0) {
            breakdown.add(ScoreComponent("تعداد روزهای دانشگاه ($activeDays روز)", dayDelta))
        }
        if (earlyDelta != 0) {
            breakdown.add(ScoreComponent("کلاس صبح زود ($earlyMorningCount جلسه)", earlyDelta))
        }

        val rawScore = 100.0 + gapDelta + dayDelta + earlyDelta
        // Honest clamp: a terrible schedule scores 0, a perfect one 100.
        val finalScore = rawScore.toInt().coerceIn(0, 100)

        // Generate descriptive tags
        val tags = mutableListOf<String>()
        if (totalGaps == 0) {
            tags.add("بدون گپ و زمان مرده (فشرده)")
        } else if (totalGaps <= 90) {
            tags.add("حداقل زمان انتظار (${totalGaps} دقیقه)")
        }

        if (activeDays <= 3) {
            tags.add("فقط $activeDays روز در هفته")
        }

        if (earlyMorningCount == 0) {
            tags.add("بدون کلاس صبح زود (۸ صبح)")
        }

        return ScoredSchedule(
            schedule = schedule,
            score = finalScore,
            totalGapMinutes = totalGaps,
            activeDaysCount = activeDays,
            earlyMorningClassCount = earlyMorningCount,
            totalWeeklyHours = totalMinutes / 60f,
            tags = tags,
            breakdown = breakdown.toList()
        )
    }

    /**
     * Ranks generated conflict-free schedules according to selected optimization goal.
     * The best schedule keeps its real score; only the "top recommendation" tag is added.
     */
    fun rankSchedules(
        schedules: List<List<SectionWithDetails>>,
        preference: OptimizationPreference = OptimizationPreference.BALANCED
    ): List<ScoredSchedule> {
        val scoredList = schedules.map { evaluateSchedule(it, preference) }
        return markBest(scoredList.sortedByDescending { it.score })
    }

    /**
     * Maps [java.util.Calendar.DAY_OF_WEEK] to the app's day index
     * (Saturday = 0 … Friday = 6, the Iranian academic week).
     */
    fun calendarDayOfWeekToAppDay(calendarDay: Int): Int = when (calendarDay) {
        java.util.Calendar.SATURDAY -> 0
        java.util.Calendar.SUNDAY -> 1
        java.util.Calendar.MONDAY -> 2
        java.util.Calendar.TUESDAY -> 3
        java.util.Calendar.WEDNESDAY -> 4
        java.util.Calendar.THURSDAY -> 5
        else -> 6 // Friday (and anything unexpected)
    }

    /** One class occurrence, possibly on a later day when scanning ahead. */
    data class UpcomingSession(
        val section: SectionWithDetails,
        val session: ClassSession,
        val dayOfWeek: Int,
        /** True when [nowMinutes] falls inside this session right now. */
        val ongoing: Boolean
    )

    /** Today's sessions sorted by start time (pure; UI supplies the weekday). */
    fun sessionsOnDay(
        sections: List<SectionWithDetails>,
        dayOfWeek: Int
    ): List<UpcomingSession> {
        return sections.flatMap { sec ->
            sec.sessions.filter { it.dayOfWeek == dayOfWeek }
                .map { UpcomingSession(sec, it, dayOfWeek, ongoing = false) }
        }.sortedBy { it.session.startMinutes }
    }

    /**
     * Next upcoming class at/after ([dayOfWeek], [nowMinutes]), scanning the
     * coming 7 days. A currently-running class counts as "next" (ongoing).
     * Null when nothing is scheduled at all.
     */
    fun nextUpcomingSession(
        sections: List<SectionWithDetails>,
        dayOfWeek: Int,
        nowMinutes: Int
    ): UpcomingSession? {
        for (offset in 0..6) {
            val day = (dayOfWeek + offset) % 7
            val todays = sections.flatMap { sec ->
                sec.sessions.filter { it.dayOfWeek == day }.map { sec to it }
            }.sortedBy { it.second.startMinutes }
            for ((sec, sess) in todays) {
                if (offset > 0 || sess.endMinutes > nowMinutes) {
                    val ongoing = offset == 0 && sess.startMinutes <= nowMinutes
                    return UpcomingSession(sec, sess, day, ongoing)
                }
            }
        }
        return null
    }

    /**
     * Computes basic metrics for a schedule.
     */
    fun computeMetrics(sections: List<SectionWithDetails>): ScheduleMetrics {
        val totalCredits = sections.sumOf { it.course.credits }
        val activeDays = sections.flatMap { it.sessions }.map { it.dayOfWeek }.toSet().size
        val totalGaps = calculateTotalGaps(sections)
        val totalMinutes = averageWeeklyMinutes(sections)
        return ScheduleMetrics(
            totalCourses = sections.size,
            totalCredits = totalCredits,
            activeDaysCount = activeDays,
            totalWeeklyHours = totalMinutes / 60f,
            totalGapMinutes = totalGaps
        )
    }
}

data class ScheduleMetrics(
    val totalCourses: Int,
    val totalCredits: Int,
    val activeDaysCount: Int,
    val totalWeeklyHours: Float,
    val totalGapMinutes: Int = 0
)
