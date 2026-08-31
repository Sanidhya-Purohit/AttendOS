package com.example.ui.screens.subjects

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.SubjectEntity
import com.example.domain.AttendanceCalculator
import com.example.domain.SubjectStats
import com.example.ui.components.CircularAttendanceGauge
import com.example.ui.components.TierBadge
import com.example.ui.screens.attendance.CalendarAttendanceDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.AttendanceViewModel

@Composable
fun SubjectsScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val statsList by viewModel.subjectStatsList.collectAsState()
    val profile by viewModel.userProfile.collectAsState()

    var selectedSubjectForDetails by remember { mutableStateOf<SubjectStats?>(null) }
    var isAddSubjectDialogOpen by remember { mutableStateOf(false) }
    var isBulkUpdateOpen by remember { mutableStateOf(false) }
    var isCalendarAttendanceOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf("All") }

    val filteredStats = remember(statsList, searchQuery, filterCategory) {
        statsList.filter { stat ->
            val matchesSearch = stat.subject.name.contains(searchQuery, ignoreCase = true) ||
                    stat.subject.code.contains(searchQuery, ignoreCase = true)
            val matchesCat = if (filterCategory == "All") true else stat.subject.category.equals(filterCategory, ignoreCase = true)
            matchesSearch && matchesCat
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = DarkBackground,
                modifier = Modifier.statusBarsPadding()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "SUBJECT INTELLIGENCE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp,
                                    color = NeonCyan
                                )
                            )
                            Text(
                                text = "All DTU Subjects",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { isCalendarAttendanceOpen = true },
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("btn_calendar_attendance_subjects")
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Calendar", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { isBulkUpdateOpen = true },
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("btn_update_attendance_bulk")
                            ) {
                                Icon(Icons.Default.EditCalendar, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bulk", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { isAddSubjectDialogOpen = true },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("btn_add_subject")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Subject", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Mode Selector Bar (Next Exam vs Full Semester)
                    val currentMode = profile?.calculationMode ?: "NEXT_EXAM"
                    val isNextExam = currentMode != "FULL_SEMESTER"
                    val sampleCutoff = statsList.firstOrNull()?.cutoffDateStr ?: (if (isNextExam) "Oct 13, 2026" else "Dec 06, 2026")
                    
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (isNextExam) NeonCyan else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { viewModel.setCalculationMode("NEXT_EXAM") }
                                ) {
                                    Text(
                                        text = "🎯 Next Exam",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isNextExam) Color.Black else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    color = if (!isNextExam) NeonCyan else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { viewModel.setCalculationMode("FULL_SEMESTER") }
                                ) {
                                    Text(
                                        text = "🎓 Full Sem",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (!isNextExam) Color.Black else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Cutoff: $sampleCutoff",
                                color = NeonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search & Filter
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by Subject name or code (e.g. SE-301)", color = TextMuted, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkSurfaceCard,
                            unfocusedContainerColor = DarkSurfaceCard,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("input_search_subjects")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Core", "Lab", "Elective").forEach { cat ->
                            val isSelected = filterCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { filterCategory = cat },
                                label = { Text(cat, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonCyan,
                                    containerColor = DarkSurfaceCard,
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) NeonCyan else DarkBorder
                                )
                            )
                        }
                    }
                }
            }
        },
        containerColor = DarkBackground,
        modifier = modifier.testTag("screen_subjects")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
        ) {
            items(filteredStats, key = { it.subject.id }) { stat ->
                SubjectDetailedCard(
                    stat = stat,
                    onClick = { selectedSubjectForDetails = stat },
                    onQuickPresent = { viewModel.markAttendance(stat.subject.id, "PRESENT") },
                    onQuickAbsent = { viewModel.markAttendance(stat.subject.id, "ABSENT") }
                )
            }
        }
    }

    // Single Subject Space / Detail BottomSheet or Dialog
    if (selectedSubjectForDetails != null) {
        val currentStat = statsList.find { it.subject.id == selectedSubjectForDetails!!.subject.id } ?: selectedSubjectForDetails!!
        SubjectDetailDialog(
            stat = currentStat,
            onDismiss = { selectedSubjectForDetails = null },
            onUpdateSubject = { updatedSub ->
                viewModel.addOrUpdateSubject(updatedSub)
            },
            onDeleteSubject = {
                viewModel.deleteSubject(currentStat.subject)
                selectedSubjectForDetails = null
            },
            onMarkPresent = { viewModel.markAttendance(currentStat.subject.id, "PRESENT") },
            onMarkAbsent = { viewModel.markAttendance(currentStat.subject.id, "ABSENT") }
        )
    }

    // Add Subject Dialog
    if (isAddSubjectDialogOpen) {
        AddOrEditSubjectDialog(
            subjectToEdit = null,
            onDismiss = { isAddSubjectDialogOpen = false },
            onSave = { newSub ->
                viewModel.addOrUpdateSubject(newSub)
                isAddSubjectDialogOpen = false
            }
        )
    }

    // Bulk Update Attendance Dialog
    if (isBulkUpdateOpen) {
        BulkUpdateAttendanceDialog(
            viewModel = viewModel,
            onDismiss = { isBulkUpdateOpen = false }
        )
    }

    // Calendar Attendance Dialog
    if (isCalendarAttendanceOpen) {
        CalendarAttendanceDialog(
            viewModel = viewModel,
            onDismiss = { isCalendarAttendanceOpen = false }
        )
    }
}

@Composable
fun SubjectDetailedCard(
    stat: SubjectStats,
    onClick: () -> Unit,
    onQuickPresent: () -> Unit,
    onQuickAbsent: () -> Unit
) {
    val tier = stat.tier
    val isDanger = tier.isDanger

    Surface(
        color = DarkSurfaceCard,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDanger) NeonCrimson.copy(alpha = 0.7f) else DarkBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() }
            .testTag("subject_card_${stat.subject.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = tier.color.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = stat.subject.code,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                color = tier.color,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stat.subject.category,
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stat.subject.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Classroom Location & Prof
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = stat.subject.defaultRoom,
                            style = MaterialTheme.typography.bodySmall.copy(color = TextAccent, fontSize = 11.sp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${stat.subject.professorName}",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                        )
                    }
                }

                CircularAttendanceGauge(
                    percentage = stat.percentage,
                    targetPercentage = stat.subject.targetPercentage,
                    tier = tier,
                    size = 78.dp,
                    strokeWidth = 8.dp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Attendance Count Bar: Attended / Conducted
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Attended: ${stat.subject.attendedClasses} / ${stat.subject.conductedClasses} classes",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                TierBadge(tier = tier)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mathematical Intelligence Breakdown Banner
            Surface(
                color = if (isDanger || stat.isDebarmentRiskAtMilestone) NeonCrimson.copy(alpha = 0.12f) else DarkSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (stat.isDebarmentRiskAtMilestone) NeonCrimson.copy(alpha = 0.4f) else DarkBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🎯 ${stat.milestoneName} (Cutoff: ${stat.cutoffDateStr})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (stat.isDebarmentRiskAtMilestone) NeonCrimson else NeonCyan
                        )
                        Text(
                            text = "${stat.remainingClassesInWindow} lectures remaining",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = stat.statusMessage,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (isDanger) FontWeight.Bold else FontWeight.Normal,
                            color = if (isDanger) NeonCrimson else TextPrimary,
                            fontSize = 11.sp
                        )
                    )

                    if (stat.percentage >= 75.0) {
                        Text(
                            text = "Safe bunk capacity: ${stat.safeBunkWithBuffer} class(es) (Window allowance: ${stat.safeBunksInWindow}, keeping ${stat.effectiveBufferUsed} buffer)",
                            style = MaterialTheme.typography.labelSmall.copy(color = NeonMint, fontSize = 10.sp),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        Text(
                            text = "Must attend ${stat.mustAttendInWindow} of ${stat.remainingClassesInWindow} classes before cutoff to hit 75%",
                            style = MaterialTheme.typography.labelSmall.copy(color = NeonGold, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick increment / decrement buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClick) {
                    Text("Details & History", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onQuickPresent,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald.copy(alpha = 0.2f), contentColor = NeonEmerald),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("+1 Present", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onQuickAbsent,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson.copy(alpha = 0.2f), contentColor = NeonCrimson),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCrimson),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("-1 Absent", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SubjectDetailDialog(
    stat: SubjectStats,
    onDismiss: () -> Unit,
    onUpdateSubject: (SubjectEntity) -> Unit,
    onDeleteSubject: () -> Unit,
    onMarkPresent: () -> Unit,
    onMarkAbsent: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    val subject = stat.subject
    val tier = stat.tier

    var name by remember { mutableStateOf(subject.name) }
    var code by remember { mutableStateOf(subject.code) }
    var room by remember { mutableStateOf(subject.defaultRoom) }
    var prof by remember { mutableStateOf(subject.professorName) }
    var attended by remember { mutableIntStateOf(subject.attendedClasses) }
    var conducted by remember { mutableIntStateOf(subject.conductedClasses) }
    var targetPct by remember { mutableDoubleStateOf(subject.targetPercentage) }
    var emergencyBuffer by remember { mutableIntStateOf(subject.emergencyBuffer) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isEditing) "Edit Subject Details" else "${subject.name} Space",
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    fontSize = 18.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                if (!isEditing) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            CircularAttendanceGauge(
                                percentage = stat.percentage,
                                targetPercentage = subject.targetPercentage,
                                tier = tier,
                                size = 110.dp,
                                strokeWidth = 11.dp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            TierBadge(tier = tier)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Stats Summary Grid
                        Surface(
                            color = DarkSurfaceCard,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Classroom Location: ${subject.defaultRoom}", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 12.sp)
                                Text("Faculty: ${subject.professorName}", color = TextSecondary, fontSize = 12.sp)
                                Text("Code: ${subject.code} (${subject.category})", color = TextSecondary, fontSize = 12.sp)
                                Divider(color = DarkBorder, modifier = Modifier.padding(vertical = 6.dp))
                                Text("Attended: ${subject.attendedClasses} of ${subject.conductedClasses} classes", fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Target Criteria: ${subject.targetPercentage}%", color = TextSecondary, fontSize = 12.sp)
                                Text("Emergency Buffer: ${subject.emergencyBuffer} classes", color = TextSecondary, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Mathematical Recovery Breakdown
                        Surface(
                            color = if (tier.isDanger) NeonCrimson.copy(alpha = 0.15f) else NeonCyan.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (tier.isDanger) NeonCrimson else NeonCyan.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("📊 Predictive Strategy", fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(stat.statusMessage, color = if (tier.isDanger) NeonCrimson else TextPrimary, fontSize = 12.sp)
                                if (stat.percentage >= 75.0) {
                                    Text("• Bunkable classes without penalty: ${stat.safeBunkWithBuffer}", color = NeonMint, fontSize = 11.sp)
                                } else {
                                    Text("• Classes to hit 75%: ${stat.classesToReachTarget}", color = NeonGold, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Adjust attendance buttons
                        Text("Log Attendance Session:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onMarkPresent,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Mark Present", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onMarkAbsent,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Mark Absent", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Edit Form
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Subject Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = code,
                                onValueChange = { code = it },
                                label = { Text("Subject Code") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = room,
                                onValueChange = { room = it },
                                label = { Text("Classroom (e.g. Pragya Bhavan PB-02, AB-4)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = prof,
                                onValueChange = { prof = it },
                                label = { Text("Professor / Faculty Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = "$attended",
                                    onValueChange = { attended = it.toIntOrNull() ?: 0 },
                                    label = { Text("Attended") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = "$conducted",
                                    onValueChange = { conducted = it.toIntOrNull() ?: 0 },
                                    label = { Text("Conducted") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isEditing) {
                Button(
                    onClick = { isEditing = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated)
                ) {
                    Text("Edit Subject", color = NeonCyan)
                }
            } else {
                Button(
                    onClick = {
                        onUpdateSubject(
                            subject.copy(
                                name = name,
                                code = code,
                                defaultRoom = room,
                                professorName = prof,
                                attendedClasses = attended,
                                conductedClasses = conducted,
                                targetPercentage = targetPct,
                                emergencyBuffer = emergencyBuffer
                            )
                        )
                        isEditing = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Save Changes", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (isEditing) {
                TextButton(
                    onClick = onDeleteSubject,
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonCrimson)
                ) {
                    Text("Delete Subject")
                }
            }
        }
    )
}

@Composable
fun AddOrEditSubjectDialog(
    subjectToEdit: SubjectEntity?,
    onDismiss: () -> Unit,
    onSave: (SubjectEntity) -> Unit
) {
    var name by remember { mutableStateOf(subjectToEdit?.name ?: "") }
    var code by remember { mutableStateOf(subjectToEdit?.code ?: "") }
    var room by remember { mutableStateOf(subjectToEdit?.defaultRoom ?: "Pragya Bhavan PB-02") }
    var prof by remember { mutableStateOf(subjectToEdit?.professorName ?: "DTU Faculty") }
    var category by remember { mutableStateOf(subjectToEdit?.category ?: "Core") }
    var attended by remember { mutableIntStateOf(subjectToEdit?.attendedClasses ?: 0) }
    var conducted by remember { mutableIntStateOf(subjectToEdit?.conductedClasses ?: 0) }
    var targetPct by remember { mutableDoubleStateOf(subjectToEdit?.targetPercentage ?: 75.0) }
    var emergencyBuffer by remember { mutableIntStateOf(subjectToEdit?.emergencyBuffer ?: 3) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = if (subjectToEdit == null) "Add DTU Subject" else "Edit Subject",
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Subject Name (e.g. Software Testing)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Course Code (e.g. SE-308)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it },
                        label = { Text("Location (e.g. Pragya Bhavan PB-02, AB-4)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = prof,
                        onValueChange = { prof = it },
                        label = { Text("Faculty / Professor Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = "$attended",
                            onValueChange = { attended = it.toIntOrNull() ?: 0 },
                            label = { Text("Attended") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = "$conducted",
                            onValueChange = { conducted = it.toIntOrNull() ?: 0 },
                            label = { Text("Conducted") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            SubjectEntity(
                                id = subjectToEdit?.id ?: 0L,
                                name = name,
                                code = code.ifBlank { "DTU-SE" },
                                defaultRoom = room,
                                professorName = prof,
                                category = category,
                                attendedClasses = attended,
                                conductedClasses = conducted,
                                targetPercentage = targetPct,
                                emergencyBuffer = emergencyBuffer
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
            ) {
                Text("Save Subject", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
