package com.example.ui.screens.attendance

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.DayAttendanceRecordUpdate
import com.example.ui.components.AttendOSDatePickerDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.AttendanceViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Custom vibrant cyber palette (electric neon in dark mode, saturated jewel tones in light mode)
private val CyberMint: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFF00FF9D) else Color(0xFF059669)
private val CyberCrimson: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFFFF2A6D) else Color(0xFFDC2626)
private val CyberAmber: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFFFFB800) else Color(0xFFD97706)
private val CyberViolet: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFFB537F2) else Color(0xFF7C3AED)
private val CyberCyan: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
private val CyberCardBg: Color @Composable get() = AppTheme.colors.surfaceCard
private val CyberSelectedBg: Color @Composable get() = AppTheme.colors.surfaceElevated

/**
 * Day-Wise Attendance Editor Window:
 * Provides an ultra-convenient, visually stunning calendar strip interface
 * with shortcuts (Today, Yesterday), interactive date selection, class listing,
 * next/prev day buttons, and custom neon color scheme.
 */
@Composable
fun DayWiseAttendanceEditDialog(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isLocked by viewModel.isCurrentSemesterLocked.collectAsState()
    val allSlotsWithSubs by viewModel.allSlotsWithSubjects.collectAsState()
    val allRecords by viewModel.allAttendanceRecords.collectAsState()

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val dayOfWeekFmt = remember { SimpleDateFormat("EEE", Locale.US) }
    val dayNumFmt = remember { SimpleDateFormat("d", Locale.US) }
    val monthFmt = remember { SimpleDateFormat("MMM", Locale.US) }
    val displayFormat = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US) }

    val todayCal = remember { Calendar.getInstance() }
    val todayStr = remember { sdf.format(todayCal.time) }

    val yesterdayCal = remember {
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    }
    val yesterdayStr = remember { sdf.format(yesterdayCal.time) }

    var selectedDateStr by remember { mutableStateOf(todayStr) }
    var isDatePickerOpen by remember { mutableStateOf(false) }

    // Generate past 28 days + today for calendar carousel
    val calendarDays = remember {
        val list = mutableListOf<String>()
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -27)
        for (i in 0..27) {
            list.add(sdf.format(c.time))
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    val calendarListState = rememberLazyListState(initialFirstVisibleItemIndex = calendarDays.size - 1)

    // Calculate day of week (1 = Mon .. 7 = Sun)
    val selectedDayOfWeek = remember(selectedDateStr) {
        try {
            val d = sdf.parse(selectedDateStr) ?: Date()
            val c = Calendar.getInstance().apply { time = d }
            val day = c.get(Calendar.DAY_OF_WEEK)
            if (day == Calendar.SUNDAY) 7 else day - 1
        } catch (e: Exception) {
            1
        }
    }

    // Filter timetable slots for the selected day of week
    val scheduledSlotsForDay = remember(allSlotsWithSubs, selectedDayOfWeek) {
        allSlotsWithSubs.filter { it.slot.dayOfWeek == selectedDayOfWeek }
    }

    // Existing records on selected date
    val existingRecordsForDate = remember(allRecords, selectedDateStr) {
        allRecords.filter { it.date == selectedDateStr }
    }

    // Map of slotId to chosen attendance status: "PRESENT", "ABSENT", "CANCELLED", "DUTY_LEAVE"
    val slotStatusMap = remember(selectedDateStr, scheduledSlotsForDay) {
        mutableStateMapOf<Long, String>().apply {
            scheduledSlotsForDay.forEach { slotWithSub ->
                val slotTime = "${slotWithSub.slot.startTime} - ${slotWithSub.slot.endTime}"
                val existing = existingRecordsForDate.firstOrNull {
                    it.subjectId == slotWithSub.subject.id &&
                            (it.slotTime == slotTime || it.slotTime.isBlank())
                }
                put(slotWithSub.slot.id, existing?.status ?: "PRESENT")
            }
        }
    }

    // Helper functions for previous / next day navigation
    fun navigateToDate(offsetDays: Int) {
        try {
            val d = sdf.parse(selectedDateStr) ?: Date()
            val c = Calendar.getInstance().apply {
                time = d
                add(Calendar.DAY_OF_YEAR, offsetDays)
            }
            val nextDateStr = sdf.format(c.time)
            selectedDateStr = nextDateStr

            val targetIndex = calendarDays.indexOf(nextDateStr)
            if (targetIndex >= 0) {
                coroutineScope.launch {
                    calendarListState.animateScrollToItem(targetIndex)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = DarkSurfaceCard,
            shape = RoundedCornerShape(26.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                Brush.verticalGradient(listOf(CyberCyan.copy(alpha = 0.5f), CyberViolet.copy(alpha = 0.3f)))
            ),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .testTag("dialog_day_wise_attendance_edit")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header Window Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(CyberCyan.copy(alpha = 0.2f), CyberViolet.copy(alpha = 0.3f))))
                                .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Attendance Day Window",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary,
                                    fontSize = 16.sp
                                )
                            )
                            Text(
                                text = "Select any date • Mark attendance • Go next day",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceElevated)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Shortcuts Bar: [Today] [Yesterday] [📅 Custom Date Picker]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Today shortcut
                    val isToday = selectedDateStr == todayStr
                    Surface(
                        color = if (isToday) CyberCyan else DarkBackground,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isToday) CyberCyan else DarkBorder),
                        modifier = Modifier
                            .clickable {
                                selectedDateStr = todayStr
                                val idx = calendarDays.indexOf(todayStr)
                                if (idx >= 0) {
                                    coroutineScope.launch { calendarListState.animateScrollToItem(idx) }
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = if (isToday) DarkBackground else CyberCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Today",
                                color = if (isToday) DarkBackground else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Yesterday shortcut
                    val isYesterday = selectedDateStr == yesterdayStr
                    Surface(
                        color = if (isYesterday) CyberViolet else DarkBackground,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isYesterday) CyberViolet else DarkBorder),
                        modifier = Modifier
                            .clickable {
                                selectedDateStr = yesterdayStr
                                val idx = calendarDays.indexOf(yesterdayStr)
                                if (idx >= 0) {
                                    coroutineScope.launch { calendarListState.animateScrollToItem(idx) }
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = if (isYesterday) Color.White else CyberViolet,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Yesterday",
                                color = if (isYesterday) Color.White else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Calendar Picker shortcut
                    OutlinedButton(
                        onClick = { isDatePickerOpen = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan)
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Calendar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Calendar Carousel Strip
                Surface(
                    color = DarkBackground,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        state = calendarListState,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(calendarDays) { dateItemStr ->
                            val parsedDate = try { sdf.parse(dateItemStr) } catch (e: Exception) { null }
                            val isSelected = dateItemStr == selectedDateStr
                            val dayOfWeekName = parsedDate?.let { dayOfWeekFmt.format(it) } ?: ""
                            val dayNumber = parsedDate?.let { dayNumFmt.format(it) } ?: ""
                            val monthName = parsedDate?.let { monthFmt.format(it) } ?: ""

                            // Count classes on this day
                            val dCal = Calendar.getInstance().apply { time = parsedDate ?: Date() }
                            val dow = dCal.get(Calendar.DAY_OF_WEEK)
                            val dayNum = if (dow == Calendar.SUNDAY) 7 else dow - 1
                            val daySlotsCount = allSlotsWithSubs.count { it.slot.dayOfWeek == dayNum }

                            Surface(
                                color = if (isSelected) CyberSelectedBg else DarkSurfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) CyberCyan else DarkBorder.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier
                                    .width(58.dp)
                                    .clickable { selectedDateStr = dateItemStr }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = dayOfWeekName.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) CyberCyan else TextMuted
                                    )
                                    Text(
                                        text = dayNumber,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isSelected) (if (AppTheme.colors.isDark) Color.White else AppTheme.colors.primary) else TextPrimary
                                    )
                                    Text(
                                        text = monthName,
                                        fontSize = 9.sp,
                                        color = if (isSelected) CyberCyan else TextMuted
                                    )

                                    Spacer(modifier = Modifier.height(3.dp))
                                    // Class indicator dot / count
                                    if (daySlotsCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) CyberMint else CyberCyan.copy(alpha = 0.6f))
                                        )
                                    } else {
                                        Text(
                                            text = "Off",
                                            fontSize = 8.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Active Date Header & Quick Actions
                val formattedDisplayDate = try {
                    val d = sdf.parse(selectedDateStr) ?: Date()
                    displayFormat.format(d)
                } catch (e: Exception) {
                    selectedDateStr
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = formattedDisplayDate,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = CyberCyan
                        )
                        Text(
                            text = "${scheduledSlotsForDay.size} classes scheduled",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // All Present Quick Button
                        if (scheduledSlotsForDay.isNotEmpty()) {
                            Surface(
                                color = CyberMint.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CyberMint.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable {
                                    scheduledSlotsForDay.forEach {
                                        slotStatusMap[it.slot.id] = "PRESENT"
                                    }
                                }
                            ) {
                                Text(
                                    text = "All Present ✅",
                                    color = CyberMint,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Full Day Off Quick Button
                        Surface(
                            color = CyberAmber.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberAmber.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable {
                                if (isLocked) {
                                    Toast.makeText(context, "Semester is locked.", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                viewModel.markDayAsHoliday(selectedDateStr, "College Holiday / Off")
                                Toast.makeText(context, "Marked $selectedDateStr as College Holiday", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        ) {
                            Text(
                                text = "Full Off 🏖️",
                                color = CyberAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Class Cards List for this Date
                if (scheduledSlotsForDay.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.EventBusy,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(46.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No classes scheduled for this day.",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Enjoy your break or tap 'Next Day →' to review upcoming days.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(scheduledSlotsForDay, key = { it.slot.id }) { slotWithSub ->
                            val slot = slotWithSub.slot
                            val sub = slotWithSub.subject
                            val currentStatus = slotStatusMap[slot.id] ?: "PRESENT"

                            Surface(
                                color = CyberCardBg,
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    when (currentStatus) {
                                        "PRESENT" -> CyberMint.copy(alpha = 0.5f)
                                        "ABSENT" -> CyberCrimson.copy(alpha = 0.5f)
                                        "DUTY_LEAVE" -> CyberViolet.copy(alpha = 0.5f)
                                        else -> CyberAmber.copy(alpha = 0.5f)
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
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = sub.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = TextPrimary
                                                )
                                                if (sub.category.isNotBlank()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = CyberViolet.copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = sub.category,
                                                            color = CyberViolet,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = "${slot.startTime} - ${slot.endTime} • ${slot.location.ifBlank { sub.defaultRoom }} • ${slot.slotType}",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }

                                        Surface(
                                            color = when (currentStatus) {
                                                "PRESENT" -> CyberMint.copy(alpha = 0.2f)
                                                "ABSENT" -> CyberCrimson.copy(alpha = 0.2f)
                                                "DUTY_LEAVE" -> CyberViolet.copy(alpha = 0.2f)
                                                else -> CyberAmber.copy(alpha = 0.2f)
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = when (currentStatus) {
                                                    "PRESENT" -> "Present ✅"
                                                    "ABSENT" -> "Absent ❌"
                                                    "DUTY_LEAVE" -> "Duty ⭐"
                                                    else -> "Off 🏖️"
                                                },
                                                color = when (currentStatus) {
                                                    "PRESENT" -> CyberMint
                                                    "ABSENT" -> CyberCrimson
                                                    "DUTY_LEAVE" -> CyberViolet
                                                    else -> CyberAmber
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // 4-Way Status Selector Buttons with cool colors
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        CyberStatusButton(
                                            label = "Present",
                                            icon = Icons.Default.Check,
                                            isSelected = currentStatus == "PRESENT",
                                            accentColor = CyberMint,
                                            onClick = { if (!isLocked) slotStatusMap[slot.id] = "PRESENT" },
                                            modifier = Modifier.weight(1f)
                                        )
                                        CyberStatusButton(
                                            label = "Absent",
                                            icon = Icons.Default.Close,
                                            isSelected = currentStatus == "ABSENT",
                                            accentColor = CyberCrimson,
                                            onClick = { if (!isLocked) slotStatusMap[slot.id] = "ABSENT" },
                                            modifier = Modifier.weight(1f)
                                        )
                                        CyberStatusButton(
                                            label = "Duty",
                                            icon = Icons.Default.Stars,
                                            isSelected = currentStatus == "DUTY_LEAVE",
                                            accentColor = CyberViolet,
                                            onClick = { if (!isLocked) slotStatusMap[slot.id] = "DUTY_LEAVE" },
                                            modifier = Modifier.weight(1f)
                                        )
                                        CyberStatusButton(
                                            label = "Off",
                                            icon = Icons.Default.Block,
                                            isSelected = currentStatus == "CANCELLED",
                                            accentColor = CyberAmber,
                                            onClick = { if (!isLocked) slotStatusMap[slot.id] = "CANCELLED" },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Bar: [← Prev Day] [Save Attendance] [Next Day →]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Previous Day Button
                    OutlinedButton(
                        onClick = { navigateToDate(-1) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Prev", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Save Button
                    Button(
                        onClick = {
                            if (isLocked) {
                                Toast.makeText(context, "Current semester is locked and cannot be edited.", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            val updates = scheduledSlotsForDay.map { slotWithSub ->
                                val slot = slotWithSub.slot
                                val slotTime = "${slot.startTime} - ${slot.endTime}"
                                DayAttendanceRecordUpdate(
                                    subjectId = slotWithSub.subject.id,
                                    slotTime = slotTime,
                                    status = slotStatusMap[slot.id] ?: "PRESENT",
                                    note = "Day-wise correction"
                                )
                            }

                            viewModel.batchUpdateDayAttendance(
                                date = selectedDateStr,
                                updates = updates,
                                onComplete = {
                                    Toast.makeText(context, "Saved attendance for $selectedDateStr", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        enabled = !isLocked && scheduledSlotsForDay.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = if (AppTheme.colors.isDark) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.8f)
                            .height(48.dp)
                            .testTag("btn_save_day_attendance")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Day", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }

                    // Next Day Button
                    Button(
                        onClick = {
                            // Automatically save current day if there are slots
                            if (!isLocked && scheduledSlotsForDay.isNotEmpty()) {
                                val updates = scheduledSlotsForDay.map { slotWithSub ->
                                    val slot = slotWithSub.slot
                                    val slotTime = "${slot.startTime} - ${slot.endTime}"
                                    DayAttendanceRecordUpdate(
                                        subjectId = slotWithSub.subject.id,
                                        slotTime = slotTime,
                                        status = slotStatusMap[slot.id] ?: "PRESENT",
                                        note = "Day-wise correction"
                                    )
                                }
                                viewModel.batchUpdateDayAttendance(
                                    date = selectedDateStr,
                                    updates = updates,
                                    onComplete = {}
                                )
                            }
                            navigateToDate(1)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberViolet,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("btn_next_day_attendance")
                    ) {
                        Text("Next →", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Material 3 Date Picker Dialog
    if (isDatePickerOpen) {
        AttendOSDatePickerDialog(
            title = "Select Past Date",
            initialDate = selectedDateStr,
            onDateSelected = { pickedDate ->
                selectedDateStr = pickedDate
                isDatePickerOpen = false
                val idx = calendarDays.indexOf(pickedDate)
                if (idx >= 0) {
                    coroutineScope.launch { calendarListState.animateScrollToItem(idx) }
                }
            },
            onDismiss = { isDatePickerOpen = false }
        )
    }
}

@Composable
private fun CyberStatusButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) accentColor.copy(alpha = 0.22f) else DarkBackground,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) accentColor else DarkBorder.copy(alpha = 0.6f)
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) accentColor else TextMuted,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                color = if (isSelected) accentColor else TextSecondary
            )
        }
    }
}
