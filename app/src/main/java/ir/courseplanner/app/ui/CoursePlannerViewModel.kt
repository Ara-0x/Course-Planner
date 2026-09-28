package ir.courseplanner.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.courseplanner.app.data.importer.CourseImporter
import ir.courseplanner.app.data.importer.ImportItem
import ir.courseplanner.app.data.importer.ImportResult
import ir.courseplanner.app.data.importer.PooyaHtmlParser
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Conflict
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseDocument
import ir.courseplanner.app.data.model.CourseSection
import ir.courseplanner.app.data.model.CourseWithSections
import ir.courseplanner.app.data.model.DocumentCategory
import ir.courseplanner.app.data.model.DocumentWithCourse
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.WeekType
import ir.courseplanner.app.data.preferences.AppColorTheme
import ir.courseplanner.app.data.preferences.PreferencesManager
import ir.courseplanner.app.data.preferences.ThemeMode
import ir.courseplanner.app.data.preferences.TimetableDensity
import ir.courseplanner.app.data.preferences.UserPreferences
import ir.courseplanner.app.data.repository.CourseRepository
import ir.courseplanner.app.engine.OptimizationPreference
import ir.courseplanner.app.engine.ScheduleEngine
import ir.courseplanner.app.engine.ScheduleMetrics
import ir.courseplanner.app.engine.ScoredSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

enum class AppDestination {
    HOME,
    COURSES,
    SCHEDULE,
    DOCUMENTS,
    SETTINGS
}

data class ManualSessionInput(
    val dayOfWeek: Int = 0,
    val startTime: String = "08:00",
    val endTime: String = "10:00",
    val location: String = "",
    val weekType: WeekType = WeekType.EVERY_WEEK
)

data class GenerationState(
    val isGenerated: Boolean = false,
    val combinations: List<ScoredSchedule> = emptyList(),
    val rawCombinations: List<List<SectionWithDetails>> = emptyList(),
    val currentIndex: Int = 0,
    val message: String? = null,
    /** Selected courses with zero usable sections (reported, never silently dropped). */
    val skippedCourses: List<String> = emptyList(),
    /** True when the search hit its safety cap (best-so-far is still shown). */
    val truncated: Boolean = false
)

enum class CourseStatusFilter(val titleFa: String) {
    // NOTE: there is intentionally no separate "generator target" tab:
    // every enrolled/manual/quick-added course already carries
    // isSelectedForGeneration, so such a tab would duplicate "دروس من".
    MY_COURSES("دروس من"),
    ALL("همه دروس"),
    ENROLLED("واحدهای من")
}

enum class CourseSortOrder(val titleFa: String) {
    NAME("نام درس"),
    CREDITS_DESC("بیشترین واحد"),
    CODE("کد درس")
}

@HiltViewModel
class CoursePlannerViewModel @Inject constructor(
    application: Application,
    private val repository: CourseRepository,
    private val preferencesManager: PreferencesManager
) : AndroidViewModel(application) {

    val userPreferences: StateFlow<UserPreferences> = preferencesManager.preferences

    init {
        // One-time cleanup of pre-loaded sample courses for release builds.
        viewModelScope.launch {
            try {
                if (!preferencesManager.isReleaseCleanDone()) {
                    repository.clearAllData()
                    preferencesManager.markReleaseCleanDone()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("CoursePlannerVM", "Release cleanup failed", e)
            }
        }
    }

    private val _currentDestination = MutableStateFlow(AppDestination.HOME)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDepartment = MutableStateFlow<String?>(null)
    val selectedDepartment: StateFlow<String?> = _selectedDepartment.asStateFlow()

    // Default is "my courses": the portal catalog stays hidden until the user
    // adds courses by code, instead of flooding the list with 200+ rows.
    private val _statusFilter = MutableStateFlow(CourseStatusFilter.MY_COURSES)
    val statusFilter: StateFlow<CourseStatusFilter> = _statusFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(CourseSortOrder.NAME)
    val sortOrder: StateFlow<CourseSortOrder> = _sortOrder.asStateFlow()

    // Extra catalog filters (all default to "no filtering").
    private val _unitsFilter = MutableStateFlow(CourseUnitsFilter.ALL)
    val unitsFilter: StateFlow<CourseUnitsFilter> = _unitsFilter.asStateFlow()

    private val _degreeFilter = MutableStateFlow(CourseDegreeFilter.ALL)
    val degreeFilter: StateFlow<CourseDegreeFilter> = _degreeFilter.asStateFlow()

    /** Day of week 0..5 (Sat..Thu), or null for "any day". */
    private val _dayFilter = MutableStateFlow<Int?>(null)
    val dayFilter: StateFlow<Int?> = _dayFilter.asStateFlow()

    private val _onlyWithSessions = MutableStateFlow(false)
    val onlyWithSessions: StateFlow<Boolean> = _onlyWithSessions.asStateFlow()

    private val _optimizationPreference = MutableStateFlow(OptimizationPreference.BALANCED)
    val optimizationPreference: StateFlow<OptimizationPreference> = _optimizationPreference.asStateFlow()

    val enrolledSections: StateFlow<List<SectionWithDetails>> = repository.enrolledSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSections: StateFlow<List<SectionWithDetails>> = repository.allSectionsWithDetails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val coursesWithSections: StateFlow<List<CourseWithSections>> = repository.coursesWithSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCourses: StateFlow<List<Course>> = repository.allCourses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDocuments: StateFlow<List<CourseDocument>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All documents paired with their Course entity
    val documentsWithCourse: StateFlow<List<DocumentWithCourse>> = combine(
        allDocuments,
        allCourses
    ) { docs, courses ->
        val courseMap = courses.associateBy { it.id }
        docs.mapNotNull { doc ->
            val course = courseMap[doc.courseId]
            if (course != null) {
                DocumentWithCourse(document = doc, course = course)
            } else {
                null
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Precomputed Courses-screen lookups: the Lazy list renders dozens of rows,
    // so per-row full-list scans (filter/count over ALL sections or documents)
    // are replaced by these O(1) maps derived once per data change.
    val sectionsByCourse: StateFlow<Map<Long, List<SectionWithDetails>>> = allSections
        .map { sections -> sections.groupBy { it.course.id } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val documentCountByCourse: StateFlow<Map<Long, Int>> = documentsWithCourse
        .map { docs -> docs.groupingBy { it.course.id }.eachCount() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val departmentNames: StateFlow<List<String>> = coursesWithSections
        .map { courses ->
            listOf("همه") + courses.map { it.course.department }.filter { it.isNotBlank() }.distinct()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("همه"))

    /** Portal-catalog courses not yet added to \"my courses\" (empty-state hint). */
    val catalogOnlyCourseCount: StateFlow<Int> = coursesWithSections
        .map { courses ->
            courses.count { cws ->
                !cws.course.isSelectedForGeneration && cws.sections.none { it.section.isEnrolled }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Document Filters
    private val _selectedDocCourseId = MutableStateFlow<Long?>(null)
    val selectedDocCourseId: StateFlow<Long?> = _selectedDocCourseId.asStateFlow()

    private val _selectedDocCategory = MutableStateFlow<DocumentCategory?>(null)
    val selectedDocCategory: StateFlow<DocumentCategory?> = _selectedDocCategory.asStateFlow()

    private val _docSearchQuery = MutableStateFlow("")
    val docSearchQuery: StateFlow<String> = _docSearchQuery.asStateFlow()

    private val _onlyBookmarkedDocs = MutableStateFlow(false)
    val onlyBookmarkedDocs: StateFlow<Boolean> = _onlyBookmarkedDocs.asStateFlow()

    val filteredDocuments: StateFlow<List<DocumentWithCourse>> = combine(
        documentsWithCourse,
        _selectedDocCourseId,
        _selectedDocCategory,
        _docSearchQuery,
        _onlyBookmarkedDocs
    ) { list, courseId, category, query, onlyBookmarked ->
        list.filter { dwc ->
            val matchesCourse = courseId == null || dwc.course.id == courseId
            val matchesCategory = category == null || dwc.document.category == category
            val matchesBookmarked = !onlyBookmarked || dwc.document.isBookmarked
            val matchesQuery = query.isBlank() ||
                dwc.document.title.contains(query, ignoreCase = true) ||
                dwc.document.contentNotes.contains(query, ignoreCase = true) ||
                (dwc.document.fileName?.contains(query, ignoreCase = true) == true) ||
                dwc.course.name.contains(query, ignoreCase = true) ||
                dwc.course.code.contains(query, ignoreCase = true)

            matchesCourse && matchesCategory && matchesBookmarked && matchesQuery
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered courses: text/department/status/sort first, then the extra
    // catalog filters (units, degree, day, has-sessions). Two typed stages
    // keep the combine overloads simple and null-safe.
    private val baseFilteredCourses: StateFlow<List<CourseWithSections>> = combine(
        coursesWithSections,
        _searchQuery,
        _selectedDepartment,
        _statusFilter,
        _sortOrder
    ) { courses, query, dept, status, sort ->
        val filtered = courses.filter { cws ->
            val matchQuery = query.isBlank() ||
                cws.course.name.contains(query, ignoreCase = true) ||
                cws.course.code.contains(query, ignoreCase = true) ||
                cws.sections.any { it.section.instructor.contains(query, ignoreCase = true) }

            val matchDept = dept == null || cws.course.department.equals(dept, ignoreCase = true)

            val matchStatus = when (status) {
                CourseStatusFilter.ALL -> true
                CourseStatusFilter.MY_COURSES ->
                    cws.course.isSelectedForGeneration ||
                        cws.sections.any { it.section.isEnrolled }
                CourseStatusFilter.ENROLLED -> cws.sections.any { it.section.isEnrolled }
            }

            matchQuery && matchDept && matchStatus
        }

        when (sort) {
            CourseSortOrder.NAME -> filtered.sortedBy { it.course.name }
            CourseSortOrder.CREDITS_DESC -> filtered.sortedByDescending { it.course.credits }
            CourseSortOrder.CODE -> filtered.sortedBy { it.course.code }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredCourses: StateFlow<List<CourseWithSections>> = combine(
        baseFilteredCourses,
        _unitsFilter,
        _degreeFilter,
        _dayFilter,
        _onlyWithSessions
    ) { courses, units, degree, day, withSessions ->
        courses.filter { cws ->
            units.matches(cws.course.credits) &&
                degree.matches(cws.course.degree) &&
                (day == null || courseHasSessionOnDay(cws, day)) &&
                (!withSessions || courseHasAnySession(cws))
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Enrolled sections conflicts (definite only — exam same-day unknowns are warnings).
    val enrolledConflicts: StateFlow<List<Conflict>> = enrolledSections
        .map { ScheduleEngine.findAllConflicts(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Same exam day with incomplete times — shown as warnings, never blocking. */
    val enrolledExamWarnings: StateFlow<List<Conflict>> = enrolledSections
        .map { ScheduleEngine.findExamWarnings(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Enrolled metrics
    val enrolledMetrics: StateFlow<ScheduleMetrics> = enrolledSections
        .map { ScheduleEngine.computeMetrics(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleMetrics(0, 0, 0, 0f, 0))

    // Schedule Generator State
    private val _generationState = MutableStateFlow(GenerationState())
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    // Status Messages
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _isErrorMessage = MutableStateFlow(false)
    val isErrorMessage: StateFlow<Boolean> = _isErrorMessage.asStateFlow()

    private fun showInfo(message: String) {
        _userMessage.value = message
        _isErrorMessage.value = false
    }

    private fun showError(message: String) {
        _userMessage.value = message
        _isErrorMessage.value = true
    }

    /**
     * Runs a repository write inside a structured, user-visible error boundary:
     * - thrown exceptions (disk full, IO, constraint races) become a visible
     *   error snackbar instead of a silently swallowed coroutine failure;
     * - CancellationException is never caught (structured concurrency stays intact).
     */
    private inline fun launchDbWrite(
        crossinline onErrorMessage: () -> String = { "خطا در ذخیره اطلاعات؛ لطفاً دوباره تلاش کنید." },
        crossinline block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("CoursePlannerVM", "DB write failed", e)
                showError(onErrorMessage())
            }
        }
    }

    fun navigateTo(destination: AppDestination) {
        _currentDestination.value = destination
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onDepartmentSelected(dept: String?) {
        _selectedDepartment.value = dept
    }

    fun setStatusFilter(filter: CourseStatusFilter) {
        _statusFilter.value = filter
    }

    fun setSortOrder(order: CourseSortOrder) {
        _sortOrder.value = order
    }

    fun setUnitsFilter(filter: CourseUnitsFilter) {
        _unitsFilter.value = filter
    }

    fun setDegreeFilter(filter: CourseDegreeFilter) {
        _degreeFilter.value = filter
    }

    fun setDayFilter(dayOfWeek: Int?) {
        _dayFilter.value = dayOfWeek?.takeIf { it in 0..5 }
    }

    fun setOnlyWithSessions(only: Boolean) {
        _onlyWithSessions.value = only
    }

    /** Number of non-default catalog filters (for the filter-panel badge). */
    fun activeFilterCount(): Int {
        var count = 0
        if (_selectedDepartment.value != null) count++
        if (_statusFilter.value != CourseStatusFilter.MY_COURSES) count++
        if (_unitsFilter.value != CourseUnitsFilter.ALL) count++
        if (_degreeFilter.value != CourseDegreeFilter.ALL) count++
        if (_dayFilter.value != null) count++
        if (_onlyWithSessions.value) count++
        return count
    }

    /** Resets every course-list filter (including search) to its default. */
    fun clearCourseFilters() {
        _searchQuery.value = ""
        _selectedDepartment.value = null
        _statusFilter.value = CourseStatusFilter.MY_COURSES
        _unitsFilter.value = CourseUnitsFilter.ALL
        _degreeFilter.value = CourseDegreeFilter.ALL
        _dayFilter.value = null
        _onlyWithSessions.value = false
    }

    fun setColorTheme(theme: AppColorTheme) {
        preferencesManager.setColorTheme(theme)
    }

    fun setThemeMode(mode: ThemeMode) {
        preferencesManager.setThemeMode(mode)
    }

    fun setStudentProfile(name: String, major: String, semester: String) {
        preferencesManager.setStudentProfile(name, major, semester)
    }

    fun setCreditTarget(target: Int) {
        preferencesManager.setCreditTarget(target)
    }

    fun setShowThursday(show: Boolean) {
        preferencesManager.setShowThursday(show)
    }

    fun setTimetableDensity(density: TimetableDensity) {
        preferencesManager.setTimetableDensity(density)
    }

    fun setSemesterStartEpochDay(epochDay: Long?) {
        preferencesManager.setSemesterStartEpochDay(epochDay)
    }

    fun setFirstWeekIsOdd(firstWeekIsOdd: Boolean) {
        preferencesManager.setFirstWeekIsOdd(firstWeekIsOdd)
    }

    fun setOptimizationPreference(preference: OptimizationPreference) {
        if (_optimizationPreference.value == preference) return
        _optimizationPreference.value = preference
        val state = _generationState.value
        if (!state.isGenerated) return
        // Only the previous Top-K (cut with the OLD weights) is retained, so a
        // mere re-rank of those few survivors could not recover combinations the
        // old cut already discarded. Re-run the search over the full candidate
        // space so the new preference judges every valid schedule.
        runScheduleGenerator()
    }

    fun toggleCourseSelectedForGeneration(courseId: Long, isSelected: Boolean) {
        viewModelScope.launch {
            repository.toggleCourseSelectedForGeneration(courseId, isSelected)
        }
    }

    fun toggleSectionEnrolled(section: SectionWithDetails, isEnrolled: Boolean) {
        viewModelScope.launch {
            repository.setSectionEnrolled(section, isEnrolled)
        }
    }

    fun checkConflictForCandidate(candidate: SectionWithDetails): Conflict? {
        return ScheduleEngine.findConflictWithCurrent(candidate, enrolledSections.value)
    }

    fun runScheduleGenerator() {
        viewModelScope.launch {
            val courses = coursesWithSections.value.filter { it.course.isSelectedForGeneration }
            if (courses.isEmpty()) {
                _generationState.value = GenerationState(
                    isGenerated = true,
                    combinations = emptyList(),
                    rawCombinations = emptyList(),
                    message = "هیچ درسی برای ساخت برنامه انتخاب نشده است. لطفاً در تب دروس، تیک «برنامه‌ساز» را فعال کنید."
                )
                return@launch
            }

            // Group sections with details for each selected course (names kept
            // so courses without any usable section can be reported by name).
            val sectionsGrouped = allSections.value.groupBy { it.course.id }
            val generatorCourses = courses.map { cws ->
                ir.courseplanner.app.engine.GeneratorCourse(
                    courseName = cws.course.name,
                    sections = sectionsGrouped[cws.course.id].orEmpty()
                )
            }

            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                ScheduleEngine.generateTopSchedules(generatorCourses, _optimizationPreference.value)
            }

            if (result.ranked.isEmpty()) {
                val detail = if (result.skippedCourses.isNotEmpty()) {
                    " برای این دروس هیچ گروه قابل‌استفاده‌ای وجود ندارد: ${result.skippedCourses.joinToString("، ")}."
                } else {
                    " همزمانی ساعات کلاس‌ها یا تداخل روز امتحانات مانع ساخت برنامه است."
                }
                _generationState.value = GenerationState(
                    isGenerated = true,
                    combinations = emptyList(),
                    rawCombinations = emptyList(),
                    message = "هیچ ترکیب بدون تداخلی برای دروس انتخاب شده یافت نشد." + detail,
                    skippedCourses = result.skippedCourses
                )
            } else {
                val skippedNote = if (result.skippedCourses.isNotEmpty()) {
                    " این دروس گروه قابل‌استفاده‌ای نداشتند و لحاظ نشدند: ${result.skippedCourses.joinToString("، ")}."
                } else ""
                val truncatedNote = if (result.truncated) {
                    " جست‌وجو به سقف امن رسید؛ بهترین‌های یافت‌شده نمایش داده می‌شود."
                } else ""
                _generationState.value = GenerationState(
                    isGenerated = true,
                    combinations = result.ranked,
                    rawCombinations = result.ranked.map { it.schedule },
                    currentIndex = 0,
                    message = "${result.totalValid} ترکیب بدون تداخل بررسی شد؛ ${result.ranked.size} پیشنهاد برتر نمایش داده می‌شود." +
                        skippedNote + truncatedNote,
                    skippedCourses = result.skippedCourses,
                    truncated = result.truncated
                )
            }
        }
    }

    fun nextCombination() {
        val state = _generationState.value
        if (state.combinations.isNotEmpty()) {
            val next = (state.currentIndex + 1) % state.combinations.size
            _generationState.value = state.copy(currentIndex = next)
        }
    }

    fun previousCombination() {
        val state = _generationState.value
        if (state.combinations.isNotEmpty()) {
            val prev = if (state.currentIndex - 1 < 0) state.combinations.size - 1 else state.currentIndex - 1
            _generationState.value = state.copy(currentIndex = prev)
        }
    }

    fun applyCurrentGeneratedSchedule() {
        val state = _generationState.value
        val scheduleToApply = state.combinations.getOrNull(state.currentIndex)?.schedule ?: return
        launchDbWrite(onErrorMessage = { "اعمال برنامه ناموفق بود؛ لطفاً دوباره تلاش کنید." }) {
            repository.applySchedule(scheduleToApply)
            showInfo("برنامه بهینه رتبه ${state.currentIndex + 1} با موفقیت به عنوان برنامه هفتگی اعمال شد.")
            _currentDestination.value = AppDestination.HOME
        }
    }

    fun addManualCourse(
        name: String,
        code: String,
        department: String,
        credits: Int,
        sectionCode: String,
        instructor: String,
        examDate: String,
        examStartTime: String,
        examEndTime: String,
        sessions: List<ManualSessionInput>
    ) {
        viewModelScope.launch {
            val course = Course(
                code = code.trim().ifBlank { "CRS-${System.currentTimeMillis() % 10000}" },
                name = name.trim(),
                department = department.trim(),
                credits = credits.coerceIn(1, 20),
                isSelectedForGeneration = true
            )
            val section = CourseSection(
                courseId = 0,
                sectionCode = sectionCode.trim().ifBlank { "01" },
                instructor = instructor.trim(),
                capacity = 30,
                examDate = examDate.trim(),
                examStartTime = examStartTime.trim(),
                examEndTime = examEndTime.trim(),
                isEnrolled = false
            )
            val sessionEntities = sessions.map { input ->
                ClassSession(
                    sectionId = 0,
                    dayOfWeek = input.dayOfWeek,
                    startTime = input.startTime.trim(),
                    endTime = input.endTime.trim(),
                    location = input.location.trim(),
                    weekType = input.weekType
                )
            }
            repository.addManualCourse(course, section, sessionEntities)
            showInfo("درس «$name» با موفقیت اضافه شد.")
        }
    }

    fun addSectionToCourse(
        courseId: Long,
        sectionCode: String,
        instructor: String,
        examDate: String,
        examStartTime: String,
        examEndTime: String,
        sessions: List<ManualSessionInput>
    ) {
        viewModelScope.launch {
            val section = CourseSection(
                courseId = courseId,
                sectionCode = sectionCode.trim().ifBlank { "01" },
                instructor = instructor.trim(),
                capacity = 30,
                examDate = examDate.trim(),
                examStartTime = examStartTime.trim(),
                examEndTime = examEndTime.trim(),
                isEnrolled = false
            )
            val sessionEntities = sessions.map { input ->
                ClassSession(
                    sectionId = 0,
                    dayOfWeek = input.dayOfWeek,
                    startTime = input.startTime.trim(),
                    endTime = input.endTime.trim(),
                    location = input.location.trim(),
                    weekType = input.weekType
                )
            }
            repository.addSectionToCourse(courseId, section, sessionEntities)
            showInfo("گروه $sectionCode با موفقیت به درس افزوده شد.")
        }
    }

    fun deleteCourse(courseId: Long, courseName: String) {
        launchDbWrite(onErrorMessage = { "حذف درس ناموفق بود؛ لطفاً دوباره تلاش کنید." }) {
            repository.deleteCourse(courseId)
            showInfo("درس «$courseName» حذف شد.")
        }
    }

    fun updateCourse(courseId: Long, name: String, code: String, department: String, credits: Int) {
        if (name.isBlank()) {
            showError("نام درس نمی‌تواند خالی باشد.")
            return
        }
        launchDbWrite(onErrorMessage = { "ذخیره تغییرات درس ناموفق بود." }) {
            when (
                repository.updateCourseDetails(
                    courseId = courseId,
                    name = name.trim(),
                    code = code.trim(),
                    department = department.trim(),
                    credits = credits.coerceIn(1, 20)
                )
            ) {
                CourseRepository.UpdateCourseResult.Success -> {
                    showInfo("تغییرات درس «${name.trim()}» ذخیره شد.")
                }
                CourseRepository.UpdateCourseResult.DuplicateCode -> {
                    showError("کد «${code.trim()}» قبلاً برای درس دیگری ثبت شده است.")
                }
                CourseRepository.UpdateCourseResult.NotFound -> {
                    showError("درس موردنظر یافت نشد؛ ممکن است حذف شده باشد.")
                }
            }
        }
    }

    fun deleteSection(sectionId: Long, sectionCode: String) {
        launchDbWrite(onErrorMessage = { "حذف گروه ناموفق بود؛ لطفاً دوباره تلاش کنید." }) {
            repository.deleteSection(sectionId)
            showInfo("گروه $sectionCode حذف شد.")
        }
    }

    /**
     * Edits a section/group in place (code, instructor, exam, sessions).
     * Enrollment is preserved by the repository, so editing an enrolled group
     * never drops it from the weekly program.
     */
    fun updateSection(
        sectionId: Long,
        sectionCode: String,
        instructor: String,
        examDate: String,
        examStartTime: String,
        examEndTime: String,
        sessions: List<ManualSessionInput>
    ) {
        if (sectionCode.isBlank()) {
            showError("کد گروه نمی‌تواند خالی باشد.")
            return
        }
        launchDbWrite(onErrorMessage = { "ذخیره تغییرات گروه ناموفق بود." }) {
            val sessionEntities = sessions.map { input ->
                ClassSession(
                    sectionId = sectionId,
                    dayOfWeek = input.dayOfWeek,
                    startTime = input.startTime.trim(),
                    endTime = input.endTime.trim(),
                    location = input.location.trim(),
                    weekType = input.weekType
                )
            }
            when (
                repository.updateSectionDetails(
                    sectionId = sectionId,
                    sectionCode = sectionCode,
                    instructor = instructor,
                    examDate = examDate,
                    examStartTime = examStartTime,
                    examEndTime = examEndTime,
                    sessions = sessionEntities
                )
            ) {
                CourseRepository.UpdateSectionResult.Success -> {
                    showInfo("تغییرات گروه «${sectionCode.trim()}» ذخیره شد.")
                }
                CourseRepository.UpdateSectionResult.DuplicateCode -> {
                    showError("کد گروه «${sectionCode.trim()}» قبلاً برای همین درس ثبت شده است.")
                }
                CourseRepository.UpdateSectionResult.NotFound -> {
                    showError("گروه موردنظر یافت نشد؛ ممکن است حذف شده باشد.")
                }
            }
        }
    }

    fun importData(content: String, isJson: Boolean, clearExisting: Boolean) {
        val result = if (isJson) {
            CourseImporter.parseJson(content)
        } else {
            CourseImporter.parseCsv(content)
        }

        when (result) {
            is ImportResult.Success -> {
                launchDbWrite(onErrorMessage = { "درون‌ریزی در پایگاه داده ناموفق بود." }) {
                    repository.importItems(result.items, clearExisting = clearExisting)
                    showInfo(result.message)
                }
            }
            is ImportResult.Failure -> {
                showError(result.errorMessage)
            }
        }
    }

    /**
     * Imports a saved university-portal HTML file (Pooya/Golestan/…) into the
     * hidden course catalog. Same-code courses are replaced, never duplicated.
     * Parsing runs off the main thread; the file can hold 200+ rows.
     */
    fun importPortalHtml(html: String, clearExisting: Boolean) {
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                PooyaHtmlParser.parsePortalHtml(html)
            }
            when (result) {
                is ImportResult.Success -> {
                    try {
                        repository.importPortalItems(result.items, clearExisting = clearExisting)
                        showInfo(
                            result.message +
                                " از تب «دروس» با وارد کردن کد درس، به دروس خود اضافه کنید."
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("CoursePlannerVM", "Portal import failed", e)
                        showError("درون‌ریزی کاتالوگ پرتال ناموفق بود؛ لطفاً دوباره تلاش کنید.")
                    }
                }
                is ImportResult.Failure -> {
                    showError(result.errorMessage)
                }
            }
        }
    }

    /**
     * Quick-add: moves a catalog course into "my courses" so it shows up in
     * the generator and the default list. Called from the code-search card.
     */
    fun addCatalogCourseToMine(courseId: Long) {
        launchDbWrite(onErrorMessage = { "افزودن درس به دروس من ناموفق بود." }) {
            val target = coursesWithSections.value.firstOrNull { it.course.id == courseId }
            repository.toggleCourseSelectedForGeneration(courseId, true)
            showInfo(
                if (target != null) {
                    "درس «${target.course.name}» به دروس من اضافه شد."
                } else {
                    "درس به دروس من اضافه شد."
                }
            )
        }
    }

    fun loadSampleData(clearExisting: Boolean = true) {
        launchDbWrite(onErrorMessage = { "بارگذاری نمونه اطلاعات ناموفق بود." }) {
            repository.importItems(CourseImporter.getSampleCatalog(), clearExisting = clearExisting)
            showInfo("نمونه اطلاعات دروس دانشگاهی با موفقیت بارگذاری شد.")
        }
    }

    fun clearAllData() {
        launchDbWrite(onErrorMessage = { "پاکسازی اطلاعات ناموفق بود." }) {
            repository.clearAllData()
            _generationState.value = GenerationState()
            showInfo("تمامی اطلاعات با موفقیت پاکسازی شدند.")
        }
    }

    fun clearScheduleOnly() {
        launchDbWrite(onErrorMessage = { "خالی کردن برنامه هفتگی ناموفق بود." }) {
            repository.clearEnrollments()
            showInfo("برنامه هفتگی خالی شد.")
        }
    }

    fun onDocSearchQueryChange(query: String) {
        _docSearchQuery.value = query
    }

    fun onDocCourseFilterSelected(courseId: Long?) {
        _selectedDocCourseId.value = courseId
    }

    fun onDocCategoryFilterSelected(category: DocumentCategory?) {
        _selectedDocCategory.value = category
    }

    fun toggleOnlyBookmarkedDocs() {
        _onlyBookmarkedDocs.value = !_onlyBookmarkedDocs.value
    }

    fun navigateToCourseDocuments(courseId: Long) {
        _selectedDocCourseId.value = courseId
        _selectedDocCategory.value = null
        _docSearchQuery.value = ""
        _currentDestination.value = AppDestination.DOCUMENTS
    }

    fun addDocument(
        courseId: Long,
        title: String,
        category: DocumentCategory,
        notes: String,
        fileUri: String?,
        fileName: String?,
        fileSizeBytes: Long,
        isBookmarked: Boolean
    ) {
        launchDbWrite(onErrorMessage = { "ذخیره جزوه / سند ناموفق بود." }) {
            val doc = CourseDocument(
                courseId = courseId,
                title = title.trim(),
                category = category,
                contentNotes = notes.trim(),
                fileUri = fileUri,
                fileName = fileName,
                fileSizeBytes = fileSizeBytes,
                isBookmarked = isBookmarked
            )
            repository.insertDocument(doc)
            showInfo("جزوه / سند با موفقیت ذخیره شد.")
        }
    }

    fun updateDocument(doc: CourseDocument) {
        launchDbWrite(onErrorMessage = { "به‌روزرسانی جزوه ناموفق بود." }) {
            repository.updateDocument(doc)
            showInfo("اطلاعات جزوه به‌روزرسانی شد.")
        }
    }

    fun deleteDocument(id: Long) {
        launchDbWrite(onErrorMessage = { "حذف جزوه / سند ناموفق بود." }) {
            repository.deleteDocument(id)
            showInfo("جزوه / سند حذف گردید.")
        }
    }

    fun toggleDocumentBookmark(id: Long, current: Boolean) {
        viewModelScope.launch {
            repository.toggleDocumentBookmark(id, !current)
        }
    }

    fun dismissUserMessage() {
        _userMessage.value = null
    }
}
