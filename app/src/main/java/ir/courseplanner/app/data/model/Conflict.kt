package ir.courseplanner.app.data.model

enum class ConflictType {
    CLASS_TIME_OVERLAP,
    EXAM_OVERLAP
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
