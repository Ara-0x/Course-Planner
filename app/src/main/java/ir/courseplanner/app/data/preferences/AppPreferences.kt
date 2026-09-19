package ir.courseplanner.app.data.preferences

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class AppColorTheme(
    val id: String,
    val titleFa: String,
    val primaryColor: Color,
    val primaryContainerColor: Color,
    val secondaryColor: Color,
    val darkPrimaryColor: Color,
    val darkPrimaryContainerColor: Color
) {
    INDIGO(
        id = "indigo",
        titleFa = "نیلی دانشگاهی",
        primaryColor = Color(0xFF2563EB),
        primaryContainerColor = Color(0xFFEFF6FF),
        secondaryColor = Color(0xFF0D9488),
        darkPrimaryColor = Color(0xFF60A5FA),
        darkPrimaryContainerColor = Color(0xFF1E3A8A)
    ),
    EMERALD(
        id = "emerald",
        titleFa = "زمردی جنگلی",
        primaryColor = Color(0xFF059669),
        primaryContainerColor = Color(0xFFECFDF5),
        secondaryColor = Color(0xFF0284C7),
        darkPrimaryColor = Color(0xFF34D399),
        darkPrimaryContainerColor = Color(0xFF064E3B)
    ),
    VIOLET(
        id = "violet",
        titleFa = "ارغوانی رویال",
        primaryColor = Color(0xFF7C3AED),
        primaryContainerColor = Color(0xFFF5F3FF),
        secondaryColor = Color(0xFFDB2777),
        darkPrimaryColor = Color(0xFFA78BFA),
        darkPrimaryContainerColor = Color(0xFF4C1D95)
    ),
    AMBER(
        id = "amber",
        titleFa = "غروب کهربایی",
        primaryColor = Color(0xFFD97706),
        primaryContainerColor = Color(0xFFFFFBEB),
        secondaryColor = Color(0xFFEA580C),
        darkPrimaryColor = Color(0xFFFBBF24),
        darkPrimaryContainerColor = Color(0xFF78350F)
    ),
    OCEAN(
        id = "ocean",
        titleFa = "اقیانوسی فیروزه‌ای",
        primaryColor = Color(0xFF0891B2),
        primaryContainerColor = Color(0xFFECFEFF),
        secondaryColor = Color(0xFF2563EB),
        darkPrimaryColor = Color(0xFF22D3EE),
        darkPrimaryContainerColor = Color(0xFF164E63)
    ),
    ROSE(
        id = "rose",
        titleFa = "یاقوت سرخ",
        primaryColor = Color(0xFFE11D48),
        primaryContainerColor = Color(0xFFFFF1F2),
        secondaryColor = Color(0xFF9333EA),
        darkPrimaryColor = Color(0xFFFB7185),
        darkPrimaryContainerColor = Color(0xFF881337)
    ),
    SLATE(
        id = "slate",
        titleFa = "نوک‌مدادی مینیمال",
        primaryColor = Color(0xFF475569),
        primaryContainerColor = Color(0xFFF1F5F9),
        secondaryColor = Color(0xFF0F766E),
        darkPrimaryColor = Color(0xFF94A3B8),
        darkPrimaryContainerColor = Color(0xFF1E293B)
    );

    companion object {
        fun fromId(id: String?): AppColorTheme {
            return values().find { it.id == id } ?: INDIGO
        }
    }
}

enum class ThemeMode(val id: String, val titleFa: String) {
    SYSTEM("system", "پیروی از سیستم"),
    LIGHT("light", "همیشه روشن"),
    DARK("dark", "همیشه تاریک");

    companion object {
        fun fromId(id: String?): ThemeMode {
            return values().find { it.id == id } ?: SYSTEM
        }
    }
}

enum class TimetableDensity(val id: String, val titleFa: String, val slotHeightDp: Int) {
    COMPACT("compact", "فشرده", 46),
    STANDARD("standard", "استاندارد", 58),
    SPACIOUS("spacious", "گسترده", 70);

    companion object {
        fun fromId(id: String?): TimetableDensity {
            return values().find { it.id == id } ?: STANDARD
        }
    }
}

data class UserPreferences(
    val theme: AppColorTheme = AppColorTheme.INDIGO,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val studentName: String = "",
    val major: String = "",
    val semesterName: String = "نیم‌سال اول ۱۴۰۳-۱۴۰۴",
    val creditTarget: Int = 20,
    val showThursday: Boolean = true,
    val timetableDensity: TimetableDensity = TimetableDensity.STANDARD
)

private const val DATASTORE_NAME = "planner_user_prefs"

private val Context.plannerDataStore by preferencesDataStore(
    name = DATASTORE_NAME,
    produceMigrations = { context ->
        // One-time upgrade path: existing installs keep their SharedPreferences values.
        listOf<DataMigration<Preferences>>(SharedPreferencesMigration(context, DATASTORE_NAME))
    }
)

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dataStore = context.plannerDataStore

    val preferences: StateFlow<UserPreferences> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { it.toUserPreferences() }
        .stateIn(scope, SharingStarted.Eagerly, UserPreferences())

    private fun update(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch { dataStore.edit(block) }
    }

    fun setColorTheme(theme: AppColorTheme) {
        update { it[KEY_THEME] = theme.id }
    }

    fun setThemeMode(mode: ThemeMode) {
        update { it[KEY_THEME_MODE] = mode.id }
    }

    fun setStudentProfile(name: String, major: String, semester: String) {
        update {
            it[KEY_STUDENT_NAME] = name.trim()
            it[KEY_MAJOR] = major.trim()
            it[KEY_SEMESTER_NAME] = semester.trim().ifBlank { DEFAULT_SEMESTER }
        }
    }

    fun setCreditTarget(target: Int) {
        update { it[KEY_CREDIT_TARGET] = target.coerceIn(10, 30) }
    }

    fun setShowThursday(show: Boolean) {
        update { it[KEY_SHOW_THURSDAY] = show }
    }

    fun setTimetableDensity(density: TimetableDensity) {
        update { it[KEY_DENSITY] = density.id }
    }

    suspend fun isReleaseCleanDone(): Boolean =
        dataStore.data.map { it[KEY_RELEASE_CLEAN] ?: false }.first()

    suspend fun markReleaseCleanDone() {
        dataStore.edit { it[KEY_RELEASE_CLEAN] = true }
    }

    private fun Preferences.toUserPreferences(): UserPreferences {
        return UserPreferences(
            theme = AppColorTheme.fromId(this[KEY_THEME]),
            themeMode = ThemeMode.fromId(this[KEY_THEME_MODE]),
            studentName = this[KEY_STUDENT_NAME] ?: "",
            major = this[KEY_MAJOR] ?: "",
            semesterName = this[KEY_SEMESTER_NAME] ?: DEFAULT_SEMESTER,
            creditTarget = this[KEY_CREDIT_TARGET] ?: 20,
            showThursday = this[KEY_SHOW_THURSDAY] ?: true,
            timetableDensity = TimetableDensity.fromId(this[KEY_DENSITY])
        )
    }

    companion object {
        private const val DEFAULT_SEMESTER = "نیم‌سال اول ۱۴۰۳-۱۴۰۴"
        private val KEY_THEME = stringPreferencesKey("app_theme")
        private val KEY_THEME_MODE = stringPreferencesKey("app_theme_mode")
        private val KEY_STUDENT_NAME = stringPreferencesKey("student_name")
        private val KEY_MAJOR = stringPreferencesKey("student_major")
        private val KEY_SEMESTER_NAME = stringPreferencesKey("semester_name")
        private val KEY_CREDIT_TARGET = intPreferencesKey("credit_target")
        private val KEY_SHOW_THURSDAY = booleanPreferencesKey("show_thursday")
        private val KEY_DENSITY = stringPreferencesKey("timetable_density")
        private val KEY_RELEASE_CLEAN = booleanPreferencesKey("release_clean_courses_v1")
    }
}
