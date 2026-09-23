package ir.courseplanner.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val name: String,
    val department: String = "",
    val credits: Int = 3,
    val isSelectedForGeneration: Boolean = false,
    // Degree level from the portal (e.g. "کاردانی پیوسته", "کارشناسی", "کارشناسی ارشد").
    // Nullable on purpose: the 3->4 migration adds the column WITHOUT a default value,
    // which is the only ADD COLUMN form Room validates identically on every version.
    // Null = unknown (pre-migration rows, manual/JSON entries without degree).
    val degree: String? = null
)
