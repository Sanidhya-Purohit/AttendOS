package com.example.ui.screens.attendance

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.AttendanceRecordEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.AttendanceViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class MissedClassesFilter(val label: String) {
    TODAY("Today"),
    LAST_WEEK("Last Week"),
    LAST_MONTH("Last Month"),
    ALL_TIME("All Time")
}

@Composable
fun MissedClassesDialog(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val allRecords by viewModel.allAttendanceRecords.collectAsState()
    val allSubjects by viewModel.allSubjects.collectAsState()

    var selectedFilter by remember { mutableStateOf(MissedClassesFilter.TODAY) }
    var selectedSubjectId by remember { mutableStateOf<Long?>(null) }

    // Date calculations
    val todayStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    }

    val oneWeekAgoDateStr = remember {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -7)
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }

    val oneMonthAgoDateStr = remember {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -30)
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }

    // Filter only ABSENT records matching the time range and subject
    val missedRecords = remember(allRecords, selectedFilter, selectedSubjectId) {
        allRecords.filter { record ->
            val isAbsent = record.status == "ABSENT"
            val matchesSubject = selectedSubjectId == null || record.subjectId == selectedSubjectId
            val matchesTime = when (selectedFilter) {
                MissedClassesFilter.TODAY -> record.date == todayStr
                MissedClassesFilter.LAST_WEEK -> record.date >= oneWeekAgoDateStr
                MissedClassesFilter.LAST_MONTH -> record.date >= oneMonthAgoDateStr
                MissedClassesFilter.ALL_TIME -> true
            }
            isAbsent && matchesSubject && matchesTime
        }.sortedByDescending { it.timestamp }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = DarkSurfaceCard,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f)
                .testTag("dialog_missed_classes")
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
                                .background(NeonCrimson.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.EventBusy, contentDescription = null, tint = NeonCrimson)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Missed Classes Log",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Track all missed sessions & bunk impact",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_missed_classes")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Time Filter Tabs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MissedClassesFilter.values().forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            color = if (isSelected) NeonCrimson else DarkSurfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) NeonCrimson else DarkBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedFilter = filter }
                        ) {
                            Text(
                                text = filter.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else TextSecondary,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Subject Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedSubjectId == null,
                            onClick = { selectedSubjectId = null },
                            label = { Text("All Subjects", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan,
                                containerColor = DarkSurfaceElevated,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedSubjectId == null,
                                borderColor = if (selectedSubjectId == null) NeonCyan else DarkBorder
                            )
                        )
                    }

                    items(allSubjects) { sub ->
                        val isSelected = selectedSubjectId == sub.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSubjectId = if (isSelected) null else sub.id },
                            label = { Text(sub.name, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan,
                                containerColor = DarkSurfaceElevated,
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

                Spacer(modifier = Modifier.height(10.dp))

                // Summary Pill
                Surface(
                    color = NeonCrimson.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Total Missed: ${missedRecords.size} class(es)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = NeonCrimson
                        )
                        Text(
                            text = "Filter: ${selectedFilter.label}",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // List of Missed Records
                if (missedRecords.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Zero Missed Classes in ${selectedFilter.label}!",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "100% attendance maintained for this period.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(missedRecords, key = { it.id }) { record ->
                            val sub = allSubjects.find { it.id == record.subjectId }
                            val subName = sub?.name ?: "Subject #${record.subjectId}"
                            val subRoom = sub?.defaultRoom ?: "DTU Classroom"

                            // Format timestamp/date
                            val formattedDate = remember(record.date, record.timestamp) {
                                try {
                                    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(record.date)
                                    SimpleDateFormat("EEE, MMM dd, yyyy", Locale.US).format(parsed ?: Date(record.timestamp))
                                } catch (e: Exception) {
                                    record.date
                                }
                            }

                            Surface(
                                color = DarkSurface,
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(NeonCrimson.copy(alpha = 0.15f))
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(20.dp))
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = subName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$formattedDate ${if (record.slotTime.isNotBlank()) "• ${record.slotTime}" else ""}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = "Location: $subRoom",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }

                                    // Undo / Mark Present button
                                    Surface(
                                        color = NeonEmerald.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.markAttendanceForDate(
                                                    subjectId = record.subjectId,
                                                    status = "PRESENT",
                                                    date = record.date,
                                                    slotTime = record.slotTime
                                                )
                                            }
                                    ) {
                                        Text(
                                            text = "Attended",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonEmerald,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
