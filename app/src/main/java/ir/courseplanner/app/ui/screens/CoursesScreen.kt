package ir.courseplanner.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseWithSections
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.WeekType
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.CourseDegreeFilter
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.CourseSortOrder
import ir.courseplanner.app.ui.CourseStatusFilter
import ir.courseplanner.app.ui.CourseUnitsFilter
import ir.courseplanner.app.ui.FILTERABLE_DAYS
import ir.courseplanner.app.ui.components.AddCourseDialog
import ir.courseplanner.app.ui.components.AddSectionDialog

@Composable
fun CoursesScreen(
    viewModel: CoursePlannerViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedDept by viewModel.selectedDepartment.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val filteredCourses by viewModel.filteredCourses.collectAsStateWithLifecycle()
    val allCoursesWithSections by viewModel.coursesWithSections.collectAsStateWithLifecycle()
    val allSections by viewModel.allSections.collectAsStateWithLifecycle()
    val allDocs by viewModel.documentsWithCourse.collectAsStateWithLifecycle()

    var showAddCourseDialog by remember { mutableStateOf(false) }
    var courseForAddSection by remember { mutableStateOf<Course?>(null) }
    var courseToDelete by remember { mutableStateOf<Course?>(null) }

    val departments = listOf("همه") + allCoursesWithSections.map { it.course.department }.filter { it.isNotBlank() }.distinct()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddCourseDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Course") },
                text = { Text("افزودن درس دستی", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(4.dp),
                modifier = Modifier.testTag("fab_add_course")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Search bar (Offline search)
            Surface(
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 1.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("course_search_input"),
                    placeholder = {
                        Text(
                            "جست‌وجوی درس، کد، دانشکده یا استاد...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick-add by course code: matches inside the hidden portal catalog
            // (courses not yet in "my courses") appear as compact cards here so the
            // user never has to scroll through 200+ catalog rows.
            val myCourseIds = remember(allCoursesWithSections) {
                allCoursesWithSections.filter { cws ->
                    cws.course.isSelectedForGeneration ||
                        cws.sections.any { it.section.isEnrolled }
                }.map { it.course.id }.toSet()
            }
            val catalogMatches = remember(searchQuery, allCoursesWithSections) {
                val q = searchQuery.trim()
                if (q.isEmpty()) emptyList()
                else allCoursesWithSections.filter { cws ->
                    cws.course.id !in myCourseIds &&
                        (cws.course.code.contains(q, ignoreCase = true) ||
                            cws.course.name.contains(q, ignoreCase = true))
                }.take(6)
            }

            AnimatedVisibility(
                visible = catalogMatches.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "یافته‌ها در کاتالوگ پرتال (${catalogMatches.size} مورد) — برای افزودن لمس کنید:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    catalogMatches.forEach { cws ->
                        CatalogQuickAddCard(
                            courseWithSections = cws,
                            sections = allSections.filter { it.course.id == cws.course.id },
                            onAdd = { viewModel.addCatalogCourseToMine(cws.course.id) }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Collapsible filter panel: keeps the list compact while the catalog
            // grows (200+ portal rows). Badge shows the active-filter count.
            val unitsFilter by viewModel.unitsFilter.collectAsStateWithLifecycle()
            val degreeFilter by viewModel.degreeFilter.collectAsStateWithLifecycle()
            val dayFilter by viewModel.dayFilter.collectAsStateWithLifecycle()
            val onlyWithSessions by viewModel.onlyWithSessions.collectAsStateWithLifecycle()
            var filtersExpanded by remember { mutableStateOf(false) }
            val activeFilterCount =
                (if (selectedDept != null) 1 else 0) +
                    (if (statusFilter != CourseStatusFilter.MY_COURSES) 1 else 0) +
                    (if (unitsFilter != CourseUnitsFilter.ALL) 1 else 0) +
                    (if (degreeFilter != CourseDegreeFilter.ALL) 1 else 0) +
                    (if (dayFilter != null) 1 else 0) +
                    (if (onlyWithSessions) 1 else 0)

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { filtersExpanded = !filtersExpanded }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "فیلترها",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (activeFilterCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 7.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "$activeFilterCount",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (activeFilterCount > 0) {
                        TextButton(onClick = { viewModel.clearCourseFilters() }) {
                            Text("حذف همه", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Icon(
                        if (filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = filtersExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Department filter chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        departments.forEach { dept ->
                            val isSelected = if (dept == "همه") selectedDept == null else selectedDept == dept
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.onDepartmentSelected(if (dept == "همه") null else dept)
                                },
                                label = {
                                    Text(
                                        text = dept,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    // Degree level chips (کاردانی / کارشناسی / ارشد)
                    FilterLabelRow(label = "مقطع:")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CourseDegreeFilter.values().forEach { filter ->
                            val isSelected = degreeFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setDegreeFilter(filter) },
                                label = {
                                    Text(
                                        text = filter.titleFa,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            )
                        }
                    }

                    // Units chips
                    FilterLabelRow(label = "واحد:")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CourseUnitsFilter.values().forEach { filter ->
                            val isSelected = unitsFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setUnitsFilter(filter) },
                                label = {
                                    Text(
                                        text = filter.titleFa,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                        FilterChip(
                            selected = onlyWithSessions,
                            onClick = { viewModel.setOnlyWithSessions(!onlyWithSessions) },
                            label = {
                                Text(
                                    "فقط دارای ساعت کلاسی",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (onlyWithSessions) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    // Class-day chips
                    FilterLabelRow(label = "روز کلاس:")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = dayFilter == null,
                            onClick = { viewModel.setDayFilter(null) },
                            label = { Text("همه روزها", fontSize = 11.5.sp) },
                            shape = RoundedCornerShape(10.dp)
                        )
                        FILTERABLE_DAYS.forEach { day ->
                            val isSelected = dayFilter == day
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setDayFilter(if (isSelected) null else day) },
                                label = {
                                    Text(
                                        text = ClassSession.getDayName(day),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    // Status filter chips & Sort selector row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CourseStatusFilter.values().forEach { filter ->
                                val isSelected = statusFilter == filter
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setStatusFilter(filter) },
                                    label = {
                                        Text(
                                            text = filter.titleFa,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable {
                                val next = when (sortOrder) {
                                    CourseSortOrder.NAME -> CourseSortOrder.CREDITS_DESC
                                    CourseSortOrder.CREDITS_DESC -> CourseSortOrder.CODE
                                    CourseSortOrder.CODE -> CourseSortOrder.NAME
                                }
                                viewModel.setSortOrder(next)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                 Icon(
                                     Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = sortOrder.titleFa,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Info Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredCourses.size} درس ثبت شده",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val selectedCount = allCoursesWithSections.count { it.course.isSelectedForGeneration }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "$selectedCount درس در برنامه‌ساز",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredCourses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            // Guide the user when the portal catalog exists but
                            // "my courses" is still empty: search a code to add.
                            val catalogOnlyCount = allCoursesWithSections.count { cws ->
                                !cws.course.isSelectedForGeneration &&
                                    cws.sections.none { it.section.isEnrolled }
                            }
                            if (allCoursesWithSections.isEmpty()) {
                                Text(
                                    text = "کاتالوگ دروس هنوز خالی است.",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "می‌توانید اولین درس خود را دستی ثبت نمایید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { showAddCourseDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("افزودن درس دستی")
                                }
                            } else if (searchQuery.isBlank() && catalogOnlyCount > 0 &&
                                statusFilter == CourseStatusFilter.MY_COURSES
                            ) {
                                Text(
                                    text = "کاتالوگ پرتال با $catalogOnlyCount درس آماده است.",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "کد درس (مثلاً 10559) را در کادر جست‌وجوی بالا وارد کنید تا از کاتالوگ به دروس شما اضافه شود.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "هیچ درسی با این مشخصات یافت نشد.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "فیلترها یا عبارت جست‌وجو را بررسی کنید، یا درس جدید اضافه نمایید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { showAddCourseDialog = true },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("افزودن درس دستی")
                                    }
                                    TextButton(onClick = { viewModel.clearCourseFilters() }) {
                                        Text("حذف همه فیلترها", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredCourses, key = { it.course.id }) { cws ->
                        val docCount = allDocs.count { it.course.id == cws.course.id }
                        CourseCard(
                            courseWithSections = cws,
                            allSections = allSections,
                            docCount = docCount,
                            onOpenDocuments = {
                                viewModel.navigateToCourseDocuments(cws.course.id)
                            },
                            onToggleSelectedForGeneration = { isSelected ->
                                viewModel.toggleCourseSelectedForGeneration(cws.course.id, isSelected)
                            },
                            onSelectSection = { sec, isEnrolled ->
                                viewModel.toggleSectionEnrolled(sec, isEnrolled)
                            },
                            onAddSectionClick = {
                                courseForAddSection = cws.course
                            },
                            onDeleteCourseClick = {
                                courseToDelete = cws.course
                            },
                            onDeleteSectionClick = { sec ->
                                viewModel.deleteSection(sec.section.id, sec.sectionCode)
                            },
                            checkConflict = { sec ->
                                viewModel.checkConflictForCandidate(sec)
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Course Dialog
    if (showAddCourseDialog) {
        AddCourseDialog(
            onDismiss = { showAddCourseDialog = false },
            onConfirm = { name, code, dept, credits, secCode, instructor, examDate, examStart, examEnd, sessions ->
                viewModel.addManualCourse(
                    name = name,
                    code = code,
                    department = dept,
                    credits = credits,
                    sectionCode = secCode,
                    instructor = instructor,
                    examDate = examDate,
                    examStartTime = examStart,
                    examEndTime = examEnd,
                    sessions = sessions
                )
                showAddCourseDialog = false
            }
        )
    }

    // Add Section Dialog
    courseForAddSection?.let { targetCourse ->
        AddSectionDialog(
            course = targetCourse,
            onDismiss = { courseForAddSection = null },
            onConfirm = { secCode, instructor, examDate, examStart, examEnd, sessions ->
                viewModel.addSectionToCourse(
                    courseId = targetCourse.id,
                    sectionCode = secCode,
                    instructor = instructor,
                    examDate = examDate,
                    examStartTime = examStart,
                    examEndTime = examEnd,
                    sessions = sessions
                )
                courseForAddSection = null
            }
        )
    }

    // Delete Course Confirmation
    courseToDelete?.let { targetCourse ->
        AlertDialog(
            onDismissRequest = { courseToDelete = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("حذف درس «${targetCourse.name}»", fontWeight = FontWeight.Bold) },
            text = { Text("آیا مطمئن هستید که می‌خواهید این درس و تمام گروه‌های آن را حذف کنید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCourse(targetCourse.id, targetCourse.name)
                        courseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("بله، حذف کن")
                }
            },
            dismissButton = {
                TextButton(onClick = { courseToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
private fun CourseCard(
    courseWithSections: CourseWithSections,
    allSections: List<SectionWithDetails>,
    docCount: Int = 0,
    onOpenDocuments: () -> Unit = {},
    onToggleSelectedForGeneration: (Boolean) -> Unit,
    onSelectSection: (SectionWithDetails, Boolean) -> Unit,
    onAddSectionClick: () -> Unit,
    onDeleteCourseClick: () -> Unit,
    onDeleteSectionClick: (SectionWithDetails) -> Unit,
    checkConflict: (SectionWithDetails) -> ir.courseplanner.app.data.model.Conflict?
) {
    val course = courseWithSections.course
    val sectionsWithDetails = allSections.filter { it.course.id == course.id }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.5.dp, RoundedCornerShape(18.dp))
            .testTag("course_card_${course.code}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Course Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = course.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${course.credits} واحد",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }

                        // Documents & Pamphlets shortcut chip
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.clickable { onOpenDocuments() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "اسناد درس",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (docCount > 0) "$docCount جزوه" else "+ جزوه",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "کد: ${course.code}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                        if (course.department.isNotBlank()) {
                            Text(
                                text = "•  دانشکده: ${course.department}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }
                    }
                }

                // Top Actions: Checkbox for Schedule Generator + Delete Course
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
                        ) {
                            Text(
                                text = "تولید خودکار",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                            Checkbox(
                                checked = course.isSelectedForGeneration,
                                onCheckedChange = onToggleSelectedForGeneration,
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDeleteCourseClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete course",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sections List Header & Add Group Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "گروه‌های ارائه شده (${sectionsWithDetails.size}):",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )

                TextButton(
                    onClick = onAddSectionClick,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "افزودن گروه دیگر",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sectionsWithDetails.forEach { sec ->
                    val isEnrolled = sec.section.isEnrolled
                    val conflict = if (!isEnrolled) checkConflict(sec) else null

                    SectionItem(
                        section = sec,
                        isEnrolled = isEnrolled,
                        conflict = conflict,
                        canDeleteSection = sectionsWithDetails.size > 1,
                        onToggleEnroll = { onSelectSection(sec, !isEnrolled) },
                        onDeleteSection = { onDeleteSectionClick(sec) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionItem(
    section: SectionWithDetails,
    isEnrolled: Boolean,
    conflict: ir.courseplanner.app.data.model.Conflict?,
    canDeleteSection: Boolean,
    onToggleEnroll: () -> Unit,
    onDeleteSection: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isEnrolled -> MaterialTheme.colorScheme.primary
            conflict != null -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        },
        label = "borderColor"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isEnrolled -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
            conflict != null -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        label = "bgColor"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isEnrolled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "گروه ${section.sectionCode}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isEnrolled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = section.instructor.ifBlank { "استاد نامشخص" },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isEnrolled) {
                        Button(
                            onClick = onToggleEnroll,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "انتخاب شده",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onToggleEnroll,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = "انتخاب گروه",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }
                    }

                    if (canDeleteSection) {
                        IconButton(
                            onClick = onDeleteSection,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete group",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Class Sessions with individual week badges
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                section.sessions.forEach { sess ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${ClassSession.getDayName(sess.dayOfWeek)} ${sess.startTime} تا ${sess.endTime}${if (sess.location.isNotBlank()) " (${sess.location})" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Week recurrence badge
                        if (sess.weekType != WeekType.EVERY_WEEK) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (sess.weekType == WeekType.EVEN_WEEKS) MaterialTheme.colorScheme.tertiaryContainer
                                        else MaterialTheme.colorScheme.secondaryContainer
                                    )
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = sess.weekType.titleFa,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = if (sess.weekType == WeekType.EVEN_WEEKS) MaterialTheme.colorScheme.onTertiaryContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // Exam time
            if (section.examDate.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "امتحان: ${section.examDate} ${section.examTimeRange}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
            }

            // Conflict Warning Pill if candidate conflicts with already enrolled courses
            if (conflict != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "هشدار تداخل: ${conflict.descriptionFa}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/** Tiny section label used inside the collapsible filter panel. */
@Composable
private fun FilterLabelRow(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Compact catalog result card for "add by course code".
 * Shows just enough detail (groups, instructor, first session, capacity)
 * for the user to pick the right course without opening the full catalog.
 */
@Composable
private fun CatalogQuickAddCard(
    courseWithSections: CourseWithSections,
    sections: List<SectionWithDetails>,
    onAdd: () -> Unit
) {
    val course = courseWithSections.course
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("catalog_quick_add_${course.code}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "کد: ${course.code}  •  ${course.credits} واحد  •  ${sections.size} گروه",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 4.dp
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("افزودن", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            sections.take(2).forEach { sec ->
                val first = sec.sessions.minByOrNull { it.dayOfWeek * 1440 + it.startMinutes }
                val preview = if (first != null) {
                    val extra = if (sec.sessions.size > 1) " +${sec.sessions.size - 1} جلسه دیگر" else ""
                    "${ClassSession.getDayName(first.dayOfWeek)} ${first.startTime} تا ${first.endTime}" +
                        (if (first.location.isNotBlank()) " (${first.location})" else "") + extra
                } else {
                    "بدون ساعت کلاسی ثبت‌شده"
                }
                Text(
                    text = "گروه ${sec.sectionCode} • ${sec.instructor.ifBlank { "استاد نامشخص" }} • $preview • ظرفیت ${sec.section.capacity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (sections.size > 2) {
                Text(
                    text = "و ${sections.size - 2} گروه دیگر…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
