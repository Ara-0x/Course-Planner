package ir.courseplanner.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseDocument
import ir.courseplanner.app.data.model.CourseSection

@Database(
    entities = [
        Course::class,
        CourseSection::class,
        ClassSession::class,
        CourseDocument::class
    ],
    version = 4
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun sectionDao(): SectionDao
    abstract fun documentDao(): CourseDocumentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Discipline: NEVER use fallbackToDestructiveMigration() here.
         * The database version is the contract with student data on device:
         * every schema change MUST bump [version] and add a Migration to
         * [ALL_MIGRATIONS]. Schemas are exported to app/schemas for review.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Portal degree level per course. Nullable + no default on purpose:
                // this is the only ADD COLUMN shape Room's schema validation
                // accepts byte-identically (pre-migration rows read back as null).
                db.execSQL("ALTER TABLE courses ADD COLUMN degree TEXT")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_3_4)

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "course_planner_database"
                )
                .addMigrations(*ALL_MIGRATIONS)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
