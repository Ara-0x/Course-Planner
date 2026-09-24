package ir.courseplanner.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.WeekType
import ir.courseplanner.app.data.preferences.TimetableDensity
import ir.courseplanner.app.ui.theme.CourseColorListDark
import ir.courseplanner.app.ui.theme.CourseColorListLight

data class TimetableItem(
    val section: SectionWithDetails,
    val session: ClassSession,
    val color: Color
)

/** Uniform 2-hour university blocks used by the weekly calendar. */
internal val TimetableTwoHourSlots = listOf(
    "08:00" to "10:00",
    "10:00" to "12:00",
    "12:00" to "14:00",
    "14:00" to "16:00",
    "16:00" to "18:00",
    "18:00" to "20:00"
)

@Composable
fun WeeklyTimetable(
    sections: List<SectionWithDetails>,
    modifier: Modifier = Modifier,
    initialDay: Int = 0,
    showThursday: Boolean = true,
    density: TimetableDensity = TimetableDensity.STANDARD
) {
    // A six-day grid needs horizontal scrolling on a phone. Start with the
    // focused day view there, while retaining the full grid as one tap away.
    val isCompactScreen = LocalConfiguration.current.screenWidthDp < 600
    var selectedDay by remember { mutableStateOf(initialDay) }
    var viewMode by remember(isCompactScreen) {
        mutableStateOf(if (isCompactScreen) "day" else "grid")
    } // "grid" or "day"
    var inspectItem by remember { mutableStateOf<TimetableItem?>(null) }

    // Map each course code to a stable distinct color
    val courseColors = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        CourseColorListDark
    } else {
        CourseColorListLight
    }
    val uniqueCodes = sections.map { it.courseCode }.distinct()
    val colorMap = uniqueCodes.mapIndexed { idx, code ->
        code to courseColors[idx % courseColors.size]
    }.toMap()

    val allItems = sections.flatMap { sec ->
        sec.sessions.map { sess ->
            TimetableItem(
                section = sec,
                session = sess,
                color = colorMap[sec.courseCode] ?: Color(0xFF2563EB)
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Mode toggle header with sleek Segmented Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تقویم برنامه هفتگی",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Segmented pill control
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(modifier = Modifier.padding(3.dp)) {
                    val isGrid = viewMode == "grid"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isGrid) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { viewMode = "grid" }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarViewWeek,
                                contentDescription = null,
                                tint = if (isGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "نمای کل هفته",
                                fontSize = 11.5.sp,
                                fontWeight = if (isGrid) FontWeight.Bold else FontWeight.Normal,
                                color = if (isGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (!isGrid) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { viewMode = "day" }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarViewDay,
                                contentDescription = null,
                                tint = if (!isGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تفکیک روزانه",
                                fontSize = 11.5.sp,
                                fontWeight = if (!isGrid) FontWeight.Bold else FontWeight.Normal,
                                color = if (!isGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (sections.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "هنوز کلاسی برای نمایش در جدول هفتگی انتخاب نشده است.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "TimetableMode"
            ) { mode ->
                if (mode == "grid") {
                    TimetableGridView(
                        items = allItems,
                        showThursday = showThursday,
                        density = density,
                        onItemClick = { inspectItem = it }
                    )
                } else {
                    TimetableDayView(
                        items = allItems,
                        selectedDay = selectedDay,
                        showThursday = showThursday,
                        onDayChange = { selectedDay = it },
                        onItemClick = { inspectItem = it }
                    )
                }
            }
        }
    }

    // Polished Detail dialog when clicking a class card
    inspectItem?.let { item ->
        AlertDialog(
            onDismissRequest = { inspectItem = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(item.color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.section.courseName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = item.color.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, item.color.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "کد درس: ${item.section.courseCode}  •  تعداد واحد: ${item.section.credits}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "گروه: ${item.section.sectionCode}  •  استاد: ${item.section.instructor.ifBlank { "نامشخص" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = item.color, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "زمان کلاس: ${ClassSession.getDayName(item.session.dayOfWeek)} ${item.session.startTime} تا ${item.session.endTime}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (item.session.weekType != WeekType.EVERY_WEEK) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(item.color.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "برگزاری: ${item.session.weekType.titleFa}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = item.color,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (item.session.location.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "محل تشکیل: ${item.session.location}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (item.section.examDate.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "تاریخ امتحان: ${item.section.examDate}  (${item.section.examTimeRange})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectItem = null }) {
                    Text("بستن", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun TimetableGridView(
    items: List<TimetableItem>,
    showThursday: Boolean = true,
    density: TimetableDensity = TimetableDensity.STANDARD,
    onItemClick: (TimetableItem) -> Unit
) {
    val hasThursdayClasses = items.any { it.session.dayOfWeek == 5 }
    val days = if (showThursday || hasThursdayClasses) (0..5).toList() else (0..4).toList()
    val scrollState = rememberScrollState()

    val timeSlots = TimetableTwoHourSlots

    val baseCellHeight = when (density) {
        TimetableDensity.COMPACT -> 76.dp
        TimetableDensity.STANDARD -> 92.dp
        TimetableDensity.SPACIOUS -> 110.dp
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(10.dp)
                .testTag("weekly_timetable_grid")
        ) {
            Column {
                // Header Row: Days of week
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    // Time column header
                    Box(
                        modifier = Modifier
                            .width(76.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ساعت",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }

                    days.forEach { dayIdx ->
                        Box(
                            modifier = Modifier
                                .width(126.dp)
                                .height(38.dp)
                                .padding(horizontal = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ClassSession.getDayName(dayIdx),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }
                    }
                }

                // Time Slots Rows
                timeSlots.forEach { (slotStart, slotEnd) ->
                    val slotStartMin = ClassSession.timeToMinutes(slotStart)
                    val slotEndMin = ClassSession.timeToMinutes(slotEnd)

                    val hasMultipleInSlot = days.any { dayIdx ->
                        items.count { item ->
                            item.session.dayOfWeek == dayIdx &&
                            maxOf(item.session.startMinutes, slotStartMin) < minOf(item.session.endMinutes, slotEndMin)
                        } > 1
                    }
                    val currentSlotHeight = if (hasMultipleInSlot) baseCellHeight + 28.dp else baseCellHeight

                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        // Time label box
                        Box(
                            modifier = Modifier
                                .width(76.dp)
                                .height(currentSlotHeight)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = slotStart,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                                Text(
                                    text = "تا",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                                Text(
                                    text = slotEnd,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                            }
                        }

                        // Cells for each day
                        days.forEach { dayIdx ->
                            val matchingItems = items.filter { item ->
                                item.session.dayOfWeek == dayIdx &&
                                maxOf(item.session.startMinutes, slotStartMin) < minOf(item.session.endMinutes, slotEndMin)
                            }

                            Box(
                                modifier = Modifier
                                    .width(126.dp)
                                    .height(currentSlotHeight)
                                    .padding(horizontal = 3.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (matchingItems.isNotEmpty()) {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (matchingItems.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(3.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        matchingItems.forEach { item ->
                                            ClassBlock(
                                                item = item,
                                                onClick = { onItemClick(item) },
                                                isMultiple = matchingItems.size > 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassBlock(
    item: TimetableItem,
    onClick: () -> Unit,
    isMultiple: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${item.section.courseName}، ${item.session.startTime} تا ${item.session.endTime}"
            }
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = item.color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, item.color.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = if (isMultiple) 3.dp else 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.section.courseName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (isMultiple) 9.5.sp else 10.5.sp
                ),
                color = item.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                softWrap = false
            )
            if (!isMultiple && item.session.location.isNotBlank()) {
                Text(
                    text = item.session.location,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    softWrap = false
                )
            }
            val weekSuffix = when (item.session.weekType) {
                WeekType.EVEN_WEEKS -> " (زوج)"
                WeekType.ODD_WEEKS -> " (فرد)"
                else -> ""
            }
            Text(
                text = "${item.session.startTime} تا ${item.session.endTime}$weekSuffix",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                softWrap = false
            )
        }
    }
}

@Composable
private fun TimetableDayView(
    items: List<TimetableItem>,
    selectedDay: Int,
    showThursday: Boolean = true,
    onDayChange: (Int) -> Unit,
    onItemClick: (TimetableItem) -> Unit
) {
    val hasThursdayClasses = items.any { it.session.dayOfWeek == 5 }
    val days = if (showThursday || hasThursdayClasses) (0..5).toList() else (0..4).toList()
    val effectiveSelectedDay = if (selectedDay in days) selectedDay else days.first()
    val dayItems = items
        .filter { it.session.dayOfWeek == effectiveSelectedDay }
        .sortedBy { it.session.startMinutes }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Horizontal Day Selector Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            days.forEach { dayIdx ->
                val count = items.count { it.session.dayOfWeek == dayIdx }
                val isSelected = selectedDay == dayIdx
                FilterChip(
                    selected = isSelected,
                    onClick = { onDayChange(dayIdx) },
                    label = {
                        Text(
                            text = "${ClassSession.getDayName(dayIdx)}${if (count > 0) " ($count)" else ""}",
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (dayItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "در این روز هیچ کلاسی تشکیل نمی‌شود (روز استراحت و مطالعه).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TimetableTwoHourSlots.forEach { (slotStart, slotEnd) ->
                    val slotStartMin = ClassSession.timeToMinutes(slotStart)
                    val slotEndMin = ClassSession.timeToMinutes(slotEnd)
                    val slotItems = dayItems.filter { item ->
                        maxOf(item.session.startMinutes, slotStartMin) < minOf(item.session.endMinutes, slotEndMin)
                    }
                    if (slotItems.isEmpty()) return@forEach
                    Text(
                        text = "$slotStart تا $slotEnd",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    slotItems.forEach { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(2.dp, RoundedCornerShape(16.dp))
                                .clickable { onItemClick(item) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.2.dp,
                                item.color.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left accent color bar
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(item.color)
                                )
                                Spacer(modifier = Modifier.width(12.dp))

                                // Course info
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = item.section.courseName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                            softWrap = false
                                        )
                                        if (item.session.weekType != WeekType.EVERY_WEEK) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = item.color.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = item.session.weekType.titleFa,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                                    color = item.color,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                                                text = item.section.instructor.ifBlank { "استاد نامشخص" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                softWrap = false
                                            )
                                        }
                                        if (item.session.location.isNotBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f, fill = false)
                                            ) {
                                                Icon(
                                                    Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = item.session.location,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Time badge on right
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = item.color.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, item.color.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = item.color
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${item.session.startTime} تا ${item.session.endTime}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = item.color,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

