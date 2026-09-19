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
    val isSelectedForGeneration: Boolean = false
)
