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
    val score: Int, // 0 to 100
    val totalGapMinutes: Int,
    val activeDaysCount: Int,
    val earlyMorningClassCount: Int,
    val totalWeeklyHours: Float,
    val tags: List<String>
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

        // 2. Check exam date & time overlap
        val examDateA = sectionA.examDate.trim()
        val examDateB = sectionB.examDate.trim()
        if (examDateA.isNotBlank() && examDateB.isNotBlank() && examDateA == examDateB) {
            val startA = ClassSession.timeToMinutes(sectionA.section.examStartTime)
            val endA = ClassSession.timeToMinutes(sectionA.section.examEndTime)
            val startB = ClassSession.timeToMinutes(sectionB.section.examStartTime)
            val endB = ClassSession.timeToMinutes(sectionB.section.examEndTime)

            if (startA > 0 && endA > 0 && startB > 0 && endB > 0) {
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
            } else {
                return Conflict(
                    type = ConflictType.EXAM_OVERLAP,
                    sectionA = sectionA,
                    sectionB = sectionB,
                    description = "Same exam day ($examDateA) for ${sectionA.courseName} and ${sectionB.courseName}",
                    descriptionFa = "همزمانی روز امتحان در تاریخ $examDateA برای «${sectionA.courseName}» و «${sectionB.courseName}»",
                    timeRange = examDateA
                )
            }
        }

        return null
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
     * Generates all conflict-free combinations of sections for the given target courses.
     */
    fun generateConflictFreeSchedules(
        courseGroups: List<List<SectionWithDetails>>,
        maxCombinations: Int = 100
    ): List<List<SectionWithDetails>> {
        val validCourses = courseGroups.filter { it.isNotEmpty() }
        if (validCourses.isEmpty()) return emptyList()

        val results = mutableListOf<List<SectionWithDetails>>()

        fun backtrack(courseIndex: Int, currentSchedule: MutableList<SectionWithDetails>) {
            if (results.size >= maxCombinations) return

            if (courseIndex == validCourses.size) {
                results.add(currentSchedule.toList())
                return
            }

            val currentGroup = validCourses[courseIndex]
            for (candidateSection in currentGroup) {
                var hasConflict = false
                for (placed in currentSchedule) {
                    if (checkConflict(candidateSection, placed) != null) {
                        hasConflict = true
                        break
                    }
                }

                if (!hasConflict) {
                    currentSchedule.add(candidateSection)
                    backtrack(courseIndex + 1, currentSchedule)
                    currentSchedule.removeAt(currentSchedule.size - 1)
                }
            }
        }

        backtrack(0, mutableListOf())
        return results
    }

    /**
     * Analyzes idle gap time between classes on each day.
     * Returns total gap minutes across the week.
     */
    fun calculateTotalGaps(sections: List<SectionWithDetails>): Int {
        val sessionsByDay = sections.flatMap { it.sessions }.groupBy { it.dayOfWeek }
        var totalGapMinutes = 0

        for ((_, daySessions) in sessionsByDay) {
            if (daySessions.size <= 1) continue
            val sorted = daySessions.sortedBy { it.startMinutes }
            for (i in 0 until sorted.size - 1) {
                val currentEnd = sorted[i].endMinutes
                val nextStart = sorted[i + 1].startMinutes
                if (nextStart > currentEnd) {
                    totalGapMinutes += (nextStart - currentEnd)
                }
            }
        }
        return totalGapMinutes
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

        var totalMinutes = 0
        for (sec in schedule) {
            for (sess in sec.sessions) {
                totalMinutes += maxOf(0, sess.endMinutes - sess.startMinutes)
            }
        }

        // Base score starts at 100
        var rawScore = 100.0

        // Deduct points for gaps (each 30 min of idle gap drops 3 points)
        val gapDeduction = (totalGaps / 30.0) * 3.0
        // Deduct points for high number of days (each day above 3 drops 5 points)
        val dayDeduction = maxOf(0, activeDays - 3) * 6.0
        // Deduct points for 8 AM classes
        val earlyDeduction = earlyMorningCount * 4.0

        when (preference) {
            OptimizationPreference.BALANCED -> {
                rawScore -= (gapDeduction * 1.0 + dayDeduction * 1.0 + earlyDeduction * 0.5)
            }
            OptimizationPreference.MIN_GAPS -> {
                rawScore -= (gapDeduction * 2.2 + dayDeduction * 0.5 + earlyDeduction * 0.3)
            }
            OptimizationPreference.MIN_DAYS -> {
                rawScore -= (dayDeduction * 2.5 + gapDeduction * 0.7 + earlyDeduction * 0.3)
            }
            OptimizationPreference.NO_EARLY_MORNING -> {
                rawScore -= (earlyDeduction * 3.0 + gapDeduction * 0.8 + dayDeduction * 0.6)
            }
        }

        val finalScore = rawScore.toInt().coerceIn(40, 99)

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
            tags = tags
        )
    }

    /**
     * Ranks generated conflict-free schedules according to selected optimization goal.
     */
    fun rankSchedules(
        schedules: List<List<SectionWithDetails>>,
        preference: OptimizationPreference = OptimizationPreference.BALANCED
    ): List<ScoredSchedule> {
        val scoredList = schedules.map { evaluateSchedule(it, preference) }
        val sorted = scoredList.sortedByDescending { it.score }

        // Tag the top recommendation
        if (sorted.isNotEmpty()) {
            val top = sorted[0]
            val updatedTags = mutableListOf("پیشنهاد برتر هوشمند") + top.tags
            return listOf(top.copy(score = 100, tags = updatedTags)) + sorted.drop(1)
        }
        return sorted
    }

    /**
     * Computes basic metrics for a schedule.
     */
    fun computeMetrics(sections: List<SectionWithDetails>): ScheduleMetrics {
        val totalCredits = sections.sumOf { it.course.credits }
        val activeDays = sections.flatMap { it.sessions }.map { it.dayOfWeek }.toSet().size
        val totalGaps = calculateTotalGaps(sections)
        var totalMinutes = 0
        for (sec in sections) {
            for (sess in sec.sessions) {
                totalMinutes += maxOf(0, sess.endMinutes - sess.startMinutes)
            }
        }
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
