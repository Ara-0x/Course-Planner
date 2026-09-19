package ir.courseplanner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.WeekType
import ir.courseplanner.app.ui.ManualSessionInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSectionDialog(
    course: Course,
    onDismiss: () -> Unit,
    onConfirm: (
        sectionCode: String,
        instructor: String,
        examDate: String,
        examStartTime: String,
        examEndTime: String,
        sessions: List<ManualSessionInput>
    ) -> Unit
) {
    var sectionCode by remember { mutableStateOf("02") }
    var instructor by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf("") }
    var examTimeRange by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val sessions = remember {
        mutableStateListOf(
            ManualSessionInput(dayOfWeek = 0, startTime = "08:00", endTime = "10:00", location = "", weekType = WeekType.EVERY_WEEK)
        )
    }

    val daysList = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه")

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(vertical = 16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.GroupAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "افزودن گروه جدید به «${course.name}»",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                errorMessage?.let { error ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sectionCode,
                        onValueChange = { sectionCode = it },
                        label = { Text("شماره گروه", fontSize = 12.sp) },
                        placeholder = { Text("02") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = instructor,
                        onValueChange = { instructor = it },
                        label = { Text("نام استاد", fontSize = 12.sp) },
                        placeholder = { Text("دکتر ...") },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = examDate,
                        onValueChange = { examDate = it },
                        label = { Text("تاریخ امتحان", fontSize = 12.sp) },
                        placeholder = { Text("1403/10/28") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = examTimeRange,
                        onValueChange = { examTimeRange = it },
                        label = { Text("ساعت امتحان", fontSize = 12.sp) },
                        placeholder = { Text("09:00 - 12:00") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Text(
                    text = "ساعات و جلسات کلاس این گروه:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                sessions.forEachIndexed { index, sessionInput ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "جلسه ${index + 1}:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )

                                if (sessions.size > 1) {
                                    IconButton(
                                        onClick = { sessions.removeAt(index) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete session",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            var dayExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = dayExpanded,
                                onExpandedChange = { dayExpanded = !dayExpanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = daysList.getOrElse(sessionInput.dayOfWeek) { "شنبه" },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("روز هفته", fontSize = 11.sp) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayExpanded) },
                                     modifier = Modifier
                                         .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                         .fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                ExposedDropdownMenu(
                                    expanded = dayExpanded,
                                    onDismissRequest = { dayExpanded = false }
                                ) {
                                    daysList.forEachIndexed { dIndex, dName ->
                                        DropdownMenuItem(
                                            text = { Text(dName) },
                                            onClick = {
                                                sessions[index] = sessionInput.copy(dayOfWeek = dIndex)
                                                dayExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = sessionInput.startTime,
                                    onValueChange = { sessions[index] = sessionInput.copy(startTime = it) },
                                    label = { Text("شروع (HH:mm)", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )

                                OutlinedTextField(
                                    value = sessionInput.endTime,
                                    onValueChange = { sessions[index] = sessionInput.copy(endTime = it) },
                                    label = { Text("پایان (HH:mm)", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }

                            OutlinedTextField(
                                value = sessionInput.location,
                                onValueChange = { sessions[index] = sessionInput.copy(location = it) },
                                label = { Text("محل کلاس", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )

                            // Week recurrence selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val weekOptions = listOf(
                                    WeekType.EVERY_WEEK to "هر هفته",
                                    WeekType.EVEN_WEEKS to "هفته زوج",
                                    WeekType.ODD_WEEKS to "هفته فرد"
                                )
                                weekOptions.forEach { (type, label) ->
                                    val isSelected = sessionInput.weekType == type
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surface
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { sessions[index] = sessionInput.copy(weekType = type) }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            ),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        sessions.add(
                            ManualSessionInput(
                                dayOfWeek = (sessions.lastOrNull()?.dayOfWeek?.plus(2))?.rem(6) ?: 0,
                                startTime = sessions.lastOrNull()?.startTime ?: "08:00",
                                endTime = sessions.lastOrNull()?.endTime ?: "10:00",
                                location = sessions.lastOrNull()?.location ?: "",
                                weekType = WeekType.EVERY_WEEK
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("افزودن جلسه دیگر به این گروه")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    for ((idx, sess) in sessions.withIndex()) {
                        if (sess.startTime.isBlank() || sess.endTime.isBlank()) {
                            errorMessage = "ساعت شروع و پایان جلسه ${idx + 1} الزامی است."
                            return@Button
                        }
                        if (ClassSession.timeToMinutes(sess.startTime) >= ClassSession.timeToMinutes(sess.endTime)) {
                            errorMessage = "در جلسه ${idx + 1}، ساعت شروع باید قبل از ساعت پایان باشد."
                            return@Button
                        }
                    }

                    var examStart = ""
                    var examEnd = ""
                    if (examTimeRange.contains("-")) {
                        val parts = examTimeRange.split("-")
                        examStart = parts[0].trim()
                        examEnd = parts.getOrNull(1)?.trim() ?: ""
                    }

                    onConfirm(
                        sectionCode,
                        instructor,
                        examDate,
                        examStart,
                        examEnd,
                        sessions.toList()
                    )
                }
            ) {
                Text("افزودن این گروه")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}
