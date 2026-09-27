package ir.courseplanner.app.data.model

enum class ConflictType {
    CLASS_TIME_OVERLAP,
    EXAM_OVERLAP,
    /**
     * Same exam day, but at least one side lacks usable start/end times.
     * This is a WARNING (insufficient data), never a definite conflict —
     * the generator must not block on it.
     */
    EXAM_SAME_DAY_WARNING
}

data class Conflict(
    val type: ConflictType,
    val sectionA: SectionWithDetails,
    val sectionB: SectionWithDetails,
    val description: String,
    val descriptionFa: String,
    val dayOfWeek: Int? = null,
    val timeRange: String = ""
)
