package com.example.ui.screens.attendance

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.dao.SlotWithSubject
import com.example.data.local.entity.AttendanceRecordEntity
import com.example.data.local.entity.SubjectEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.AttendanceViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun CalendarAttendanceDialog(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val allSubjects by viewModel.allSubjects.collectAsState()
    val allSlotsWithSubs by viewModel.allSlotsWithSubjects.collectAsState()
    val profile by viewModel.userProfile.collectAsState()

    val recordsByDate by viewModel.attendanceRecordsByDate.collectAsState()

    // Calendar state
    val cal = remember { Calendar.getInstance() }
    var currentYear by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableIntStateOf(cal.get(Calendar.MONTH)) } // 0-based

    val todayStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    }

    var selectedDateStr by remember { mutableStateOf(todayStr) }

    // Session Start Date state (default to semester start or mid-August)
    var sessionStartDate by remember(profile) {
        mutableStateOf(profile?.midsemDate?.let { "2026-08-01" } ?: "2026-08-01")
    }

    var isSessionDatePickerOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = DarkSurfaceCard,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .testTag("dialog_calendar_attendance")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(NeonCyan.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = NeonCyan)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Calendar Attendance",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Date & Time Wise Class Attendance",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_calendar_attendance")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Session Start Date banner
                Surface(
                    color = DarkSurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Flag, contentDescription = null, tint = NeonGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Session Start: $sessionStartDate",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Surface(
                            color = NeonCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { isSessionDatePickerOpen = true }
                        ) {
                            Text(
                                text = "Change Date",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Month Navigator
                val monthNames = listOf(
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            if (currentMonth == 0) {
                                currentMonth = 11
                                currentYear--
                            } else {
                                currentMonth--
                            }
                        }
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = NeonCyan)
                    }

                    Text(
                        text = "${monthNames[currentMonth]} $currentYear",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )

                    IconButton(
                        onClick = {
                            if (currentMonth == 11) {
                                currentMonth = 0
                                currentYear++
                            } else {
                                currentMonth++
                            }
                        }
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = NeonCyan)
                    }
                }

                // Month Days Grid (Weekdays Mon - Sun)
                val weekdayHeaders = listOf("M", "T", "W", "T", "F", "S", "S")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    weekdayHeaders.forEach { w ->
                        Text(
                            text = w,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Generate Month Days
                val daysInMonth = remember(currentYear, currentMonth) {
                    val c = Calendar.getInstance()
                    c.set(currentYear, currentMonth, 1)
                    val maxDay = c.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val firstDayOfWeek = c.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday
                    val offset = (firstDayOfWeek + 5) % 7 // Monday = 0, Sunday = 6
                    val list = mutableListOf<Int?>()
                    for (i in 0 until offset) list.add(null)
                    for (d in 1..maxDay) list.add(d)
                    list
                }

                // Mini Calendar Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 175.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(daysInMonth) { day ->
                        if (day == null) {
                            Spacer(modifier = Modifier.size(34.dp))
                        } else {
                            val dateStr = String.format(Locale.US, "%04d-%02d-%02d", currentYear, currentMonth + 1, day)
                            val isSelected = dateStr == selectedDateStr
                            val isToday = dateStr == todayStr
                            val dayRecords = recordsByDate[dateStr] ?: emptyList()
                            val hasPresent = dayRecords.any { it.status == "PRESENT" }
                            val hasAbsent = dayRecords.any { it.status == "ABSENT" }

                            Surface(
                                color = when {
                                    isSelected -> NeonCyan
                                    isToday -> DarkSurfaceElevated
                                    else -> DarkBackground
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    when {
                                        isSelected -> NeonCyan
                                        isToday -> NeonCyan.copy(alpha = 0.5f)
                                        hasAbsent -> NeonCrimson.copy(alpha = 0.5f)
                                        hasPresent -> NeonEmerald.copy(alpha = 0.5f)
                                        else -> DarkBorder
                                    }
                                ),
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedDateStr = dateStr }
                                    .testTag("cal_day_$day")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$day",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.Normal,
                                        color = if (isSelected) Color.Black else TextPrimary
                                    )
                                    // Dot indicators for attendance logged
                                    if (dayRecords.isNotEmpty() && !isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 2.dp)
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(if (hasAbsent) NeonCrimson else NeonEmerald)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Divider(color = DarkBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))

                // Date Details Section Header
                val formattedSelectedDate = remember(selectedDateStr) {
                    try {
                        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedDateStr)
                        SimpleDateFormat("EEE, MMM dd, yyyy", Locale.US).format(parsed ?: Calendar.getInstance().time)
                    } catch (e: Exception) {
                        selectedDateStr
                    }
                }

                // Calculate Day of Week for selected date (1=Mon..6=Sat)
                val selectedDayOfWeek = remember(selectedDateStr) {
                    try {
                        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedDateStr)
                        val c = Calendar.getInstance()
                        if (parsed != null) c.time = parsed
                        when (c.get(Calendar.DAY_OF_WEEK)) {
                            Calendar.MONDAY -> 1
                            Calendar.TUESDAY -> 2
                            Calendar.WEDNESDAY -> 3
                            Calendar.THURSDAY -> 4
                            Calendar.FRIDAY -> 5
                            Calendar.SATURDAY -> 6
                            else -> 1 // Sunday
                        }
                    } catch (e: Exception) {
                        1
                    }
                }

                // Slots configured for that day
                val dayTimetableSlots = remember(allSlotsWithSubs, selectedDayOfWeek) {
                    allSlotsWithSubs.filter { it.slot.dayOfWeek == selectedDayOfWeek }
                }

                // Existing logged records for selected date
                val dateRecords = recordsByDate[selectedDateStr] ?: emptyList()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "SCHEDULE FOR $formattedSelectedDate",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = NeonCyan
                            )
                        )
                        Text(
                            text = "${dayTimetableSlots.size} classes on this day",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }

                    Surface(
                        color = DarkSurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = if (selectedDateStr == todayStr) "TODAY" else selectedDateStr,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonMint,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // List of Classes for this Date
                if (dayTimetableSlots.isEmpty() && allSubjects.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Text("No subjects or slots configured.", color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Scheduled slots for this day of week
                        if (dayTimetableSlots.isNotEmpty()) {
                            items(dayTimetableSlots, key = { "slot_${it.slot.id}" }) { slotWithSub ->
                                val slotTime = "${slotWithSub.slot.startTime} - ${slotWithSub.slot.endTime}"
                                val existingRecord = dateRecords.find {
                                    it.subjectId == slotWithSub.subject.id &&
                                            (it.slotTime == slotTime || it.slotTime.isBlank())
                                }

                                DateSlotAttendanceCard(
                                    subject = slotWithSub.subject,
                                    slotTime = slotTime,
                                    room = slotWithSub.slot.location.ifBlank { slotWithSub.subject.defaultRoom },
                                    slotType = slotWithSub.slot.slotType,
                                    currentStatus = existingRecord?.status,
                                    onMarkStatus = { newStatus ->
                                        viewModel.markAttendanceForDate(
                                            subjectId = slotWithSub.subject.id,
                                            status = newStatus,
                                            date = selectedDateStr,
                                            slotTime = slotTime
                                        )
                                    }
                                )
                            }
                        }

                        // 2. Extra/All Subjects Quick Picker (if not in timetable for this day)
                        val otherSubjects = allSubjects.filter { sub ->
                            dayTimetableSlots.none { it.subject.id == sub.id }
                        }

                        if (otherSubjects.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Other Registered Subjects",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )
                            }

                            items(otherSubjects, key = { "other_${it.id}" }) { sub ->
                                val existingRecord = dateRecords.find { it.subjectId == sub.id }
                                DateSlotAttendanceCard(
                                    subject = sub,
                                    slotTime = "Special/Extra Lecture",
                                    room = sub.defaultRoom,
                                    slotType = "Extra",
                                    currentStatus = existingRecord?.status,
                                    onMarkStatus = { newStatus ->
                                        viewModel.markAttendanceForDate(
                                            subjectId = sub.id,
                                            status = newStatus,
                                            date = selectedDateStr,
                                            slotTime = "Extra Lecture"
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_done_calendar_attendance")
                ) {
                    Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Session Start Date Picker Dialog
    if (isSessionDatePickerOpen) {
        AlertDialog(
            onDismissRequest = { isSessionDatePickerOpen = false },
            containerColor = DarkSurface,
            title = {
                Text("Set Session Start Date", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column {
                    Text(
                        "Enter the starting date of your academic session / semester (YYYY-MM-DD):",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sessionStartDate,
                        onValueChange = { sessionStartDate = it },
                        placeholder = { Text("2026-08-01", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setSessionStartDate(sessionStartDate)
                        isSessionDatePickerOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isSessionDatePickerOpen = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
fun DateSlotAttendanceCard(
    subject: SubjectEntity,
    slotTime: String,
    room: String,
    slotType: String,
    currentStatus: String?,
    onMarkStatus: (String) -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (currentStatus) {
                "PRESENT" -> NeonEmerald.copy(alpha = 0.6f)
                "ABSENT" -> NeonCrimson.copy(alpha = 0.6f)
                "CANCELLED" -> NeonGold.copy(alpha = 0.4f)
                else -> DarkBorder
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$slotTime • $room ($slotType)",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                // Current status badge
                if (currentStatus != null) {
                    Surface(
                        color = when (currentStatus) {
                            "PRESENT" -> NeonEmerald.copy(alpha = 0.18f)
                            "ABSENT" -> NeonCrimson.copy(alpha = 0.18f)
                            else -> DarkSurfaceElevated
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when (currentStatus) {
                                "PRESENT" -> NeonEmerald
                                "ABSENT" -> NeonCrimson
                                else -> DarkBorder
                            }
                        ),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = currentStatus,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = when (currentStatus) {
                                "PRESENT" -> NeonEmerald
                                "ABSENT" -> NeonCrimson
                                else -> TextSecondary
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Present / Absent / Cancelled selection buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { onMarkStatus("PRESENT") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentStatus == "PRESENT") NeonEmerald else NeonEmerald.copy(alpha = 0.15f),
                        contentColor = if (currentStatus == "PRESENT") Color.Black else NeonEmerald
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Present", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onMarkStatus("ABSENT") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentStatus == "ABSENT") NeonCrimson else NeonCrimson.copy(alpha = 0.15f),
                        contentColor = if (currentStatus == "ABSENT") Color.White else NeonCrimson
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCrimson),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Absent", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onMarkStatus("CANCELLED") },
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (currentStatus == "CANCELLED") NeonGold else DarkBorder),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1.1f).height(34.dp)
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(13.dp), tint = if (currentStatus == "CANCELLED") NeonGold else TextMuted)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Cancelled", fontSize = 10.sp, color = if (currentStatus == "CANCELLED") NeonGold else TextMuted, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
