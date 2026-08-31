package com.example.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.dao.SlotWithSubject
import com.example.domain.AttendanceCalculator
import com.example.domain.AttendanceTier
import com.example.ui.components.*
import com.example.ui.screens.attendance.AcademicBreaksDialog
import com.example.ui.screens.attendance.CalendarAttendanceDialog
import com.example.ui.screens.attendance.MissedClassesDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.AttendanceViewModel
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: AttendanceViewModel,
    onNavigateToSubjects: () -> Unit,
    onNavigateToTimetable: () -> Unit,
    onNavigateToAI: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.userProfile.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val allSlotsWithSubs by viewModel.allSlotsWithSubjects.collectAsState()
    val statsList by viewModel.subjectStatsList.collectAsState()
    val overallPct by viewModel.overallAttendance.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val activeBreakToday by viewModel.activeBreakToday.collectAsState()

    var isCalendarAttendanceOpen by remember { mutableStateOf(false) }
    var isMissedClassesOpen by remember { mutableStateOf(false) }
    var isAcademicBreaksOpen by remember { mutableStateOf(false) }

    val daySlots = remember(allSlotsWithSubs, selectedDay) {
        allSlotsWithSubs.filter { it.slot.dayOfWeek == selectedDay }
    }

    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val fullDayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    var showSuccessSnackbar by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            DashboardTopBar(
                studentName = profile?.studentName ?: "DTUite",
                branch = profile?.branch ?: "Software Engineering (SE)",
                streak = profile?.currentStreak ?: 0,
                notificationCount = notifications.size,
                onOpenNotifications = onOpenNotifications,
                onOpenSettings = onOpenSettings
            )
        },
        containerColor = DarkBackground,
        modifier = modifier.testTag("screen_dashboard")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // 1. Overall DTU Attendance Matrix Card
            item {
                OverallAttendanceHero(
                    overallPercentage = overallPct,
                    targetThreshold = profile?.globalThreshold ?: 75.0,
                    bufferCount = profile?.globalEmergencyBuffer ?: 3,
                    isBufferEnabled = profile?.includeBufferInCalculations ?: true,
                    calculationMode = profile?.calculationMode ?: "NEXT_EXAM",
                    leadDays = profile?.examCutoffLeadDays ?: 2,
                    statsList = statsList,
                    onSelectMode = { mode -> viewModel.setCalculationMode(mode) },
                    onToggleBuffer = { viewModel.toggleEmergencyBuffer() },
                    onAdjustBuffer = { viewModel.setEmergencyBufferCount(it) },
                    onViewSubjects = onNavigateToSubjects
                )
            }

            // Active College Break & Fest Banner (if today has an active break/fest)
            if (activeBreakToday != null) {
                item {
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonPurple),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { isAcademicBreaksOpen = true }
                            .testTag("banner_active_fest_break")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(NeonPurple.copy(alpha = 0.2f))
                            ) {
                                Text("🎉", fontSize = 22.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = NeonPurple.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "COLLEGE BREAK / FEST ACTIVE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = NeonPurple,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = activeBreakToday!!.title,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Classes are suspended. No attendance penalty!",
                                    fontSize = 11.sp,
                                    color = NeonCyan
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = NeonPurple)
                        }
                    }
                }
            }

            // Quick Tools Bar: Calendar Attendance, Missed Classes Log & College Fests/Breaks
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    // Calendar Log
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { isCalendarAttendanceOpen = true }
                            .testTag("btn_dashboard_calendar_attendance")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(NeonCyan.copy(alpha = 0.15f))
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Calendar Log", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextPrimary, maxLines = 1)
                            Text("Date & Time", fontSize = 9.sp, color = TextSecondary)
                        }
                    }

                    // Missed Classes
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { isMissedClassesOpen = true }
                            .testTag("btn_dashboard_missed_classes")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(NeonCrimson.copy(alpha = 0.15f))
                            ) {
                                Icon(Icons.Default.EventBusy, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Missed Log", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextPrimary, maxLines = 1)
                            Text("Filter & Fix", fontSize = 9.sp, color = TextSecondary)
                        }
                    }

                    // College Fests & Breaks
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { isAcademicBreaksOpen = true }
                            .testTag("btn_dashboard_fests_breaks")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(NeonPurple.copy(alpha = 0.15f))
                            ) {
                                Icon(Icons.Default.Celebration, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Fests & Breaks", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextPrimary, maxLines = 1)
                            Text("Tech/Holidays", fontSize = 9.sp, color = TextSecondary)
                        }
                    }
                }
            }

            // 2. Urgent / 30-Min Alert Banner (if any)
            if (notifications.isNotEmpty()) {
                item {
                    UrgentAlertHeader(
                        notification = notifications.first(),
                        onDismiss = { viewModel.dismissNotification(notifications.first().id) },
                        onOpenChat = onNavigateToAI
                    )
                }
            }

            // 3. Today's Schedule Header & Day Selector (Prominently first thing visible)
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "TODAY'S SCHEDULE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp,
                                    color = NeonCyan
                                )
                            )
                            Text(
                                text = "${fullDayNames.getOrElse(selectedDay - 1) { "Today" }}'s Lectures",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }

                        // Full Schedule view button
                        TextButton(
                            onClick = onNavigateToTimetable,
                            modifier = Modifier.testTag("btn_view_full_schedule")
                        ) {
                            Text("Full Week", color = NeonCyan, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = NeonCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Day Switcher Tabs
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("day_switcher_row")
                    ) {
                        items((1..6).toList()) { dayIndex ->
                            val isSelected = dayIndex == selectedDay
                            Surface(
                                color = if (isSelected) NeonCyan else DarkSurfaceCard,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) NeonCyan else DarkBorder
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.setSelectedDay(dayIndex) }
                                    .testTag("day_tab_$dayIndex")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = dayNames[dayIndex - 1],
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color.Black else TextPrimary
                                    )
                                    Text(
                                        text = "Day $dayIndex",
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color.Black.copy(alpha = 0.8f) else TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. List of Slots for Selected Day with 1-Tap Attendance Action
            if (daySlots.isEmpty()) {
                item {
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Default.Celebration, contentDescription = null, tint = NeonMint, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Scheduled Classes for ${fullDayNames.getOrElse(selectedDay - 1) { "this day" }}!",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Enjoy your college off or study for Midsems & Endsems.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(daySlots, key = { it.slot.id }) { slotWithSub ->
                    val stat = statsList.find { it.subject.id == slotWithSub.subject.id }
                    val tier = stat?.tier ?: AttendanceCalculator.getTier(75.0)

                    TodaySlotCard(
                        slotWithSub = slotWithSub,
                        stats = stat,
                        tier = tier,
                        onPresent = {
                            viewModel.markAttendance(
                                subjectId = slotWithSub.subject.id,
                                status = "PRESENT",
                                slotTime = "${slotWithSub.slot.startTime} - ${slotWithSub.slot.endTime}"
                            )
                            showSuccessSnackbar = "Logged Present for ${slotWithSub.subject.name}!"
                        },
                        onAbsent = {
                            viewModel.markAttendance(
                                subjectId = slotWithSub.subject.id,
                                status = "ABSENT",
                                slotTime = "${slotWithSub.slot.startTime} - ${slotWithSub.slot.endTime}"
                            )
                            showSuccessSnackbar = "Logged Absent for ${slotWithSub.subject.name}."
                        },
                        onCancelled = {
                            viewModel.markAttendance(
                                subjectId = slotWithSub.subject.id,
                                status = "CANCELLED",
                                slotTime = "${slotWithSub.slot.startTime} - ${slotWithSub.slot.endTime}"
                            )
                            showSuccessSnackbar = "Class marked Free/Cancelled."
                        }
                    )
                }
            }

            // 5. AI Quick Assistant Banner
            item {
                Surface(
                    color = DarkSurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clickable { onNavigateToAI() }
                        .testTag("card_ai_quick_prompt")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(GeminiBlue, GeminiPurple)
                                    )
                                )
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Draco AI", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ask Draco AI",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "“Can I bunk DBMS today?” • “Best day for leave?”",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = GeminiCyan)
                    }
                }
            }
        }
    }

    // Calendar Attendance Log Dialog
    if (isCalendarAttendanceOpen) {
        CalendarAttendanceDialog(
            viewModel = viewModel,
            onDismiss = { isCalendarAttendanceOpen = false }
        )
    }

    // Missed Classes Dialog
    if (isMissedClassesOpen) {
        MissedClassesDialog(
            viewModel = viewModel,
            onDismiss = { isMissedClassesOpen = false }
        )
    }

    // Academic Breaks & Fests Dialog
    if (isAcademicBreaksOpen) {
        AcademicBreaksDialog(
            viewModel = viewModel,
            onDismiss = { isAcademicBreaksOpen = false }
        )
    }
}

@Composable
fun DashboardTopBar(
    studentName: String,
    branch: String,
    streak: Int,
    notificationCount: Int,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = DarkBackground,
        border = androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent),
        modifier = Modifier.statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = studentName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = NeonCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "DTU SE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = NeonCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = branch,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Streak Badge
                Surface(
                    color = NeonGold.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGold.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("🔥", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "$streak Streak",
                            fontWeight = FontWeight.Bold,
                            color = NeonGold,
                            fontSize = 11.sp
                        )
                    }
                }

                // Notification Bell
                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier.testTag("btn_notifications_bell")
                ) {
                    BadgedBox(
                        badge = {
                            if (notificationCount > 0) {
                                Badge(containerColor = NeonCrimson) {
                                    Text("$notificationCount")
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "Alerts", tint = TextPrimary)
                    }
                }

                // Settings Cog
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("btn_dashboard_settings")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun OverallAttendanceHero(
    overallPercentage: Double,
    targetThreshold: Double,
    bufferCount: Int,
    isBufferEnabled: Boolean,
    calculationMode: String,
    leadDays: Int,
    statsList: List<com.example.domain.SubjectStats>,
    onSelectMode: (String) -> Unit,
    onToggleBuffer: () -> Unit,
    onAdjustBuffer: (Int) -> Unit,
    onViewSubjects: () -> Unit
) {
    val tier = AttendanceCalculator.getTier(overallPercentage, targetThreshold)
    val criticalCount = statsList.count { it.tier.isDanger }
    val isNextExam = calculationMode != "FULL_SEMESTER"
    val sampleStat = statsList.firstOrNull()
    val cutoffLabel = sampleStat?.cutoffDateStr ?: (if (isNextExam) "Oct 13, 2026" else "Dec 06, 2026")

    Surface(
        color = DarkSurfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            Brush.linearGradient(
                listOf(tier.color.copy(alpha = 0.8f), DarkBorder)
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("card_overall_attendance_hero")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Milestone Mode Switcher Bar
            Surface(
                color = DarkSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (isNextExam) NeonCyan else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectMode("NEXT_EXAM") }
                                .testTag("btn_mode_next_exam")
                        ) {
                            Text(
                                text = "🎯 Till Next Exam",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNextExam) Color.Black else TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }

                        Surface(
                            color = if (!isNextExam) NeonCyan else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectMode("FULL_SEMESTER") }
                                .testTag("btn_mode_full_sem")
                        ) {
                            Text(
                                text = "🎓 Full Semester",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isNextExam) Color.Black else TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = if (isNextExam) "Targeting: Midsem ($leadDays days cutoff)" else "Targeting: Endsem ($leadDays days cutoff)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeonCyan
                        )
                        Text(
                            text = "Cutoff: $cutoffLabel",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isNextExam) "NEXT EXAM STANDING" else "FULL SEMESTER STANDING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = tier.color,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${String.format(Locale.US, "%.1f", overallPercentage)}%",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TierBadge(tier = tier)
                }

                CircularAttendanceGauge(
                    percentage = overallPercentage,
                    targetPercentage = targetThreshold,
                    tier = tier,
                    size = 92.dp,
                    strokeWidth = 9.dp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Critical Shortage Alert Bar if any subject below 75%
            if (criticalCount > 0) {
                Surface(
                    color = NeonCrimson.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$criticalCount subject(s) below $targetThreshold% exam criteria!",
                            fontWeight = FontWeight.Bold,
                            color = NeonCrimson,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Emergency Leave Buffer Pill
            EmergencyBufferPill(
                bufferCount = bufferCount,
                isEnabled = isBufferEnabled,
                onToggle = onToggleBuffer,
                onAdjustCount = onAdjustBuffer
            )
        }
    }
}

@Composable
fun UrgentAlertHeader(
    notification: com.example.ui.viewmodel.InAppNotification,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit
) {
    Surface(
        color = if (notification.type == "DANGER") NeonCrimson.copy(alpha = 0.15f) else NeonCyan.copy(alpha = 0.15f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (notification.type == "DANGER") NeonCrimson else NeonCyan
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                if (notification.type == "DANGER") Icons.Default.Warning else Icons.Default.AccessTime,
                contentDescription = null,
                tint = if (notification.type == "DANGER") NeonCrimson else NeonCyan,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 13.sp
                )
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun TodaySlotCard(
    slotWithSub: SlotWithSubject,
    stats: com.example.domain.SubjectStats?,
    tier: AttendanceTier,
    onPresent: () -> Unit,
    onAbsent: () -> Unit,
    onCancelled: () -> Unit
) {
    Surface(
        color = DarkSurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (tier.isDanger) NeonCrimson.copy(alpha = 0.6f) else DarkBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("slot_card_${slotWithSub.slot.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (slotWithSub.slot.slotType == "Lab") NeonPurple.copy(alpha = 0.2f) else NeonCyan.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = slotWithSub.slot.slotType.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = if (slotWithSub.slot.slotType == "Lab") NeonPurple else NeonCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${slotWithSub.slot.startTime} - ${slotWithSub.slot.endTime}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = slotWithSub.subject.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Classroom Location Pill (e.g. Pragya Bhavan, AB-4)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "Room Location",
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = slotWithSub.slot.location.ifBlank { slotWithSub.subject.defaultRoom },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextAccent,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Current Subject % & Tier Badge
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${String.format("%.1f", stats?.percentage ?: 0.0)}%",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = tier.color
                        )
                    )
                    TierBadge(tier = tier)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action / Strategy Tip with Exam Milestone Window
            if (stats != null) {
                Surface(
                    color = if (stats.isDebarmentRiskAtMilestone) NeonCrimson.copy(alpha = 0.15f) else DarkSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (stats.isDebarmentRiskAtMilestone) NeonCrimson.copy(alpha = 0.5f) else DarkBorder
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🎯 ${stats.milestoneName} Cutoff (${stats.cutoffDateStr})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (stats.isDebarmentRiskAtMilestone) NeonCrimson else NeonCyan
                            )
                            Text(
                                text = "${stats.remainingClassesInWindow} lectures left",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        if (stats.percentage < 75.0) {
                            Text(
                                text = "⚠️ Must attend ${stats.mustAttendInWindow} of ${stats.remainingClassesInWindow} remaining classes before cutoff to hit 75%.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = NeonCrimson,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        } else {
                            Text(
                                text = "✨ Safe bunk in window: ${stats.safeBunksInWindow} class(es) (Keeping ${stats.effectiveBufferUsed} emergency buffer safe).",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = NeonMint,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            // Quick 1-Tap Attendance Buttons
            QuickAttendanceActions(
                onPresent = onPresent,
                onAbsent = onAbsent,
                onCancelled = onCancelled
            )
        }
    }
}
