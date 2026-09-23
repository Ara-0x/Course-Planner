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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val message: String? = null
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
            if (!preferencesManager.isReleaseCleanDone()) {
                repository.clearAllData()
                preferencesManager.markReleaseCleanDone()
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Enrolled sections conflicts
    val enrolledConflicts: StateFlow<List<Conflict>> = enrolledSections
        .combine(MutableStateFlow(Unit)) { enrolled, _ ->
            ScheduleEngine.findAllConflicts(enrolled)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Enrolled metrics
    val enrolledMetrics: StateFlow<ScheduleMetrics> = enrolledSections
        .combine(MutableStateFlow(Unit)) { enrolled, _ ->
            ScheduleEngine.computeMetrics(enrolled)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleMetrics(0, 0, 0, 0f, 0))

    // Schedule Generator State
    private val _generationState = MutableStateFlow(GenerationState())
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    // Status Messages
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _isErrorMessage = MutableStateFlow(false)
    val isErrorMessage: StateFlow<Boolean> = _isErrorMessage.asStateFlow()

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

    fun setOptimizationPreference(preference: OptimizationPreference) {
        _optimizationPreference.value = preference
        val state = _generationState.value
        if (state.isGenerated && state.rawCombinations.isNotEmpty()) {
            val ranked = ScheduleEngine.rankSchedules(state.rawCombinations, preference)
            _generationState.value = state.copy(
                combinations = ranked,
                currentIndex = 0
            )
        }
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

            // Group sections with details for each selected course
            val allSecs = allSections.value
            val courseGroups = courses.map { cws ->
                allSecs.filter { it.course.id == cws.course.id }
            }

            val validSchedules = ScheduleEngine.generateConflictFreeSchedules(courseGroups)

            if (validSchedules.isEmpty()) {
                _generationState.value = GenerationState(
                    isGenerated = true,
                    combinations = emptyList(),
                    rawCombinations = emptyList(),
                    message = "هیچ ترکیب بدون تداخلی برای دروس انتخاب شده یافت نشد. همزمانی ساعات کلاس‌ها یا تداخل روز امتحانات مانع ساخت برنامه است."
                )
            } else {
                val ranked = ScheduleEngine.rankSchedules(validSchedules, _optimizationPreference.value)
                _generationState.value = GenerationState(
                    isGenerated = true,
                    combinations = ranked,
                    rawCombinations = validSchedules,
                    currentIndex = 0,
                    message = "${validSchedules.size} برنامه بدون تداخل تولید و بر اساس اولویت بهینه‌سازی رتبه‌بندی شد."
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
        viewModelScope.launch {
            repository.applySchedule(scheduleToApply)
            _userMessage.value = "برنامه بهینه رتبه ${state.currentIndex + 1} با موفقیت به عنوان برنامه هفتگی اعمال شد."
            _isErrorMessage.value = false
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
            _userMessage.value = "درس «$name» با موفقیت اضافه شد."
            _isErrorMessage.value = false
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
            _userMessage.value = "گروه $sectionCode با موفقیت به درس افزوده شد."
            _isErrorMessage.value = false
        }
    }

    fun deleteCourse(courseId: Long, courseName: String) {
        viewModelScope.launch {
            repository.deleteCourse(courseId)
            _userMessage.value = "درس «$courseName» حذف شد."
            _isErrorMessage.value = false
        }
    }

    fun deleteSection(sectionId: Long, sectionCode: String) {
        viewModelScope.launch {
            repository.deleteSection(sectionId)
            _userMessage.value = "گروه $sectionCode حذف شد."
            _isErrorMessage.value = false
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
                viewModelScope.launch {
                    repository.importItems(result.items, clearExisting = clearExisting)
                    _userMessage.value = result.message
                    _isErrorMessage.value = false
                }
            }
            is ImportResult.Failure -> {
                _userMessage.value = result.errorMessage
                _isErrorMessage.value = true
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
                    repository.importPortalItems(result.items, clearExisting = clearExisting)
                    _userMessage.value = result.message +
                        " از تب «دروس» با وارد کردن کد درس، به دروس خود اضافه کنید."
                    _isErrorMessage.value = false
                }
                is ImportResult.Failure -> {
                    _userMessage.value = result.errorMessage
                    _isErrorMessage.value = true
                }
            }
        }
    }

    /**
     * Quick-add: moves a catalog course into "my courses" so it shows up in
     * the generator and the default list. Called from the code-search card.
     */
    fun addCatalogCourseToMine(courseId: Long) {
        viewModelScope.launch {
            val target = coursesWithSections.value.firstOrNull { it.course.id == courseId }
            repository.toggleCourseSelectedForGeneration(courseId, true)
            _userMessage.value = if (target != null) {
                "درس «${target.course.name}» به دروس من اضافه شد."
            } else {
                "درس به دروس من اضافه شد."
            }
            _isErrorMessage.value = false
        }
    }

    fun loadSampleData(clearExisting: Boolean = true) {
        viewModelScope.launch {
            repository.importItems(CourseImporter.getSampleCatalog(), clearExisting = clearExisting)
            _userMessage.value = "نمونه اطلاعات دروس دانشگاهی با موفقیت بارگذاری شد."
            _isErrorMessage.value = false
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            _generationState.value = GenerationState()
            _userMessage.value = "تمامی اطلاعات با موفقیت پاکسازی شدند."
            _isErrorMessage.value = false
        }
    }

    fun clearScheduleOnly() {
        viewModelScope.launch {
            repository.clearEnrollments()
            _userMessage.value = "برنامه هفتگی خالی شد."
            _isErrorMessage.value = false
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
        viewModelScope.launch {
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
            _userMessage.value = "جزوه / سند با موفقیت ذخیره شد."
            _isErrorMessage.value = false
        }
    }

    fun updateDocument(doc: CourseDocument) {
        viewModelScope.launch {
            repository.updateDocument(doc)
            _userMessage.value = "اطلاعات جزوه به‌روزرسانی شد."
            _isErrorMessage.value = false
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            repository.deleteDocument(id)
            _userMessage.value = "جزوه / سند حذف گردید."
            _isErrorMessage.value = false
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
