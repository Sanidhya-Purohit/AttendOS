package com.example.ui.screens.timetable

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.ScheduleSlotEntity
import com.example.data.local.entity.SubjectEntity
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScheduleBuilderDialog(
    initialSubjects: List<SubjectEntity> = emptyList(),
    initialSlots: List<Pair<Int, ScheduleSlotEntity>> = emptyList(),
    onDismiss: () -> Unit,
    onSaveSchedule: (List<SubjectEntity>, List<Pair<Int, ScheduleSlotEntity>>) -> Unit
) {
    var phase by remember { mutableIntStateOf(1) } // 1 = Courses & Credits, 2 = Day-by-Day Classes
    var courseList by remember {
        mutableStateOf<List<SubjectEntity>>(
            if (initialSubjects.isNotEmpty()) initialSubjects.map { it.copy() }
            else emptyList()
        )
    }
    var slotList by remember {
        mutableStateOf<List<Pair<Int, ScheduleSlotEntity>>>(
            if (initialSlots.isNotEmpty()) initialSlots.map { Pair(it.first, it.second.copy()) }
            else emptyList()
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = DarkBackground,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header with Phase Tabs
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "MANUAL SCHEDULE BUILDER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                color = NeonCyan
                            )
                        )
                        Text(
                            text = if (phase == 1) "Phase 1: Courses & Credits" else "Phase 2: Day-by-Day Schedule",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(DarkSurfaceElevated, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Phase Selector Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = if (phase == 1) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (phase == 1) NeonCyan else DarkBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { phase = 1 }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "1. Courses (${courseList.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (phase == 1) NeonCyan else TextMuted
                            )
                        }
                    }

                    Surface(
                        color = if (phase == 2) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (phase == 2) NeonCyan else DarkBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (courseList.isNotEmpty()) {
                                    phase = 2
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "2. Day-by-Day (${slotList.size} slots)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (phase == 2) NeonCyan else TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Main Content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (phase == 1) {
                        ManualCoursesPhase(
                            courses = courseList,
                            onCoursesUpdated = { courseList = it },
                            onProceedToClasses = { phase = 2 }
                        )
                    } else {
                        ManualClassesPhase(
                            courses = courseList,
                            slots = slotList,
                            onSlotsUpdated = { slotList = it },
                            onBackToCourses = { phase = 1 },
                            onSave = {
                                onSaveSchedule(courseList, slotList)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ManualCoursesPhase(
    courses: List<SubjectEntity>,
    onCoursesUpdated: (List<SubjectEntity>) -> Unit,
    onProceedToClasses: () -> Unit
) {
    var courseName by remember { mutableStateOf("") }
    var courseCode by remember { mutableStateOf("") }
    var credits by remember { mutableIntStateOf(4) }
    var location by remember { mutableStateOf("Pragya Bhavan PB-02") }
    var category by remember { mutableStateOf("Core") }
    var isAddingCustom by remember { mutableStateOf(false) }

    val presetDTUCourses = listOf(
        SubjectEntity(name = "Software Engineering", code = "SE-301", credits = 4, defaultRoom = "Pragya Bhavan PB-02", colorHex = "#00E5FF", category = "Core"),
        SubjectEntity(name = "Machine Learning", code = "SE-303", credits = 4, defaultRoom = "Pragya Bhavan PB-04", colorHex = "#10B981", category = "Core"),
        SubjectEntity(name = "Operating Systems", code = "SE-305", credits = 4, defaultRoom = "SPS-09", colorHex = "#F59E0B", category = "Core"),
        SubjectEntity(name = "Database Management Systems", code = "SE-307", credits = 4, defaultRoom = "AB-4 Room 204", colorHex = "#EA580C", category = "Core"),
        SubjectEntity(name = "Software Engineering Lab", code = "SE-309", credits = 2, defaultRoom = "AB-4 Lab-1", colorHex = "#A855F7", category = "Lab"),
        SubjectEntity(name = "ML & Data Science Lab", code = "SE-311", credits = 2, defaultRoom = "PB AI Lab 302", colorHex = "#3B82F6", category = "Lab"),
        SubjectEntity(name = "AEC / VAC (Selected)", code = "VAC-101", credits = 3, defaultRoom = "Tw-1 Tech Wing", colorHex = "#14B8A6", category = "VAC")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Add Course Form Card
        item {
            Surface(
                color = DarkSurfaceCard,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Add Course / Subject",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 16.sp
                        )
                        if (courses.isEmpty()) {
                            TextButton(
                                onClick = {
                                    onCoursesUpdated(presetDTUCourses)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Prefill DTU Courses", color = NeonAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = courseName,
                        onValueChange = { courseName = it },
                        label = { Text("Course Name (e.g. Software Engineering)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("input_course_name")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = courseCode,
                            onValueChange = { courseCode = it },
                            label = { Text("Course Code (e.g. SE-301)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1f).testTag("input_course_code")
                        )

                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("Class Location") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1.2f).testTag("input_course_location")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Credits Selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Course Credits",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Academic weightage",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (1..5).forEach { creditVal ->
                                Surface(
                                    color = if (credits == creditVal) NeonCyan else DarkBackground,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (credits == creditVal) NeonCyan else DarkBorder
                                    ),
                                    modifier = Modifier
                                        .clickable { credits = creditVal }
                                        .size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$creditVal",
                                            fontWeight = FontWeight.Bold,
                                            color = if (credits == creditVal) DarkBackground else TextPrimary,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category Selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Type:", color = TextSecondary, fontSize = 12.sp)
                        listOf("Core", "Elective", "Lab", "MOOC").forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonCyan
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Add Button
                    Button(
                        onClick = {
                            if (courseName.isNotBlank()) {
                                val colors = listOf("#00E5FF", "#10B981", "#F59E0B", "#EA580C", "#A855F7", "#3B82F6", "#14B8A6")
                                val newSub = SubjectEntity(
                                    id = 0,
                                    name = courseName.trim(),
                                    code = courseCode.trim().ifBlank { "DTU-${300 + courses.size}" },
                                    credits = credits,
                                    defaultRoom = location.trim().ifBlank { "Pragya Bhavan PB-02" },
                                    category = category,
                                    colorHex = colors[courses.size % colors.size]
                                )
                                onCoursesUpdated(courses + newSub)
                                courseName = ""
                                courseCode = ""
                                credits = 4
                            }
                        },
                        enabled = courseName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("btn_add_course_to_list")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = ButtonTextOnPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Course to Schedule", color = ButtonTextOnPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Added Courses Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Configured Courses (${courses.size})",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 15.sp
                )
                if (courses.isNotEmpty()) {
                    TextButton(onClick = { onCoursesUpdated(emptyList()) }) {
                        Text("Clear All", color = NeonCrimson, fontSize = 12.sp)
                    }
                }
            }
        }

        if (courses.isEmpty()) {
            item {
                Surface(
                    color = DarkSurfaceCard,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No courses added yet",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Add your subject name, credits and code above, or tap 'Prefill DTU Courses'.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            itemsIndexed(courses) { index, sub ->
                Surface(
                    color = DarkSurfaceCard,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Surface(
                            color = NeonCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = sub.code.take(3),
                                    fontWeight = FontWeight.Black,
                                    color = NeonCyan,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sub.name,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = NeonPurple.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "${sub.credits} Credits",
                                        color = NeonPurple,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = "• ${sub.defaultRoom} • ${sub.category}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val updated = courses.toMutableList()
                                updated.removeAt(index)
                                onCoursesUpdated(updated)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Bottom Action Button
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onProceedToClasses,
                enabled = courses.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("btn_proceed_to_classes")
            ) {
                Text("Proceed to Day-by-Day Scheduling", color = ButtonTextOnPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = ButtonTextOnPrimary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualClassesPhase(
    courses: List<SubjectEntity>,
    slots: List<Pair<Int, ScheduleSlotEntity>>,
    onSlotsUpdated: (List<Pair<Int, ScheduleSlotEntity>>) -> Unit,
    onBackToCourses: () -> Unit,
    onSave: () -> Unit
) {
    var selectedDay by remember { mutableIntStateOf(1) } // 1 = Mon .. 6 = Sat
    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val shortDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    var selectedCourseIndex by remember { mutableIntStateOf(0) }
    var startTime by remember { mutableStateOf("09:00 AM") }
    var endTime by remember { mutableStateOf("10:00 AM") }
    var location by remember {
        mutableStateOf(courses.firstOrNull()?.defaultRoom ?: "Pragya Bhavan PB-02")
    }
    var slotType by remember { mutableStateOf("Lecture") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val currentDaySlots = slots.filter { it.second.dayOfWeek == selectedDay }

    val timePresets = listOf("08:00 AM", "09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM", "01:00 PM", "02:00 PM", "03:00 PM", "04:00 PM", "05:00 PM")
    val endTimePresets = listOf("09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM", "01:00 PM", "02:00 PM", "03:00 PM", "04:00 PM", "05:00 PM", "06:00 PM")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Day Selector Tabs
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(shortDays) { index, dayStr ->
                    val dayNum = index + 1
                    val isSelected = selectedDay == dayNum
                    val daySlotsCount = slots.count { it.second.dayOfWeek == dayNum }
                    Surface(
                        color = if (isSelected) NeonCyan else DarkSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) NeonCyan else DarkBorder
                        ),
                        modifier = Modifier
                            .clickable { selectedDay = dayNum }
                            .padding(vertical = 2.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = dayStr,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) DarkBackground else TextPrimary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "$daySlotsCount classes",
                                color = if (isSelected) DarkBackground.copy(alpha = 0.8f) else TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // Add Class to Selected Day Card
        item {
            Surface(
                color = DarkSurfaceCard,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "+ Add Class to ${dayNames[selectedDay - 1]}",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Course Dropdown Selector
                    Text("Select Course / Subject:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))

                    ExposedDropdownMenuBox(
                        expanded = isDropdownExpanded,
                        onExpandedChange = { isDropdownExpanded = !isDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val currentSub = courses.getOrNull(selectedCourseIndex)
                        OutlinedTextField(
                            value = currentSub?.let { "${it.name} (${it.code}) - ${it.credits} Credits" } ?: "Select Course",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("dropdown_select_course")
                        )

                        ExposedDropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false },
                            modifier = Modifier.background(DarkSurfaceElevated)
                        ) {
                            courses.forEachIndexed { idx, sub ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(sub.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("${sub.code} • ${sub.credits} Credits • ${sub.defaultRoom}", color = NeonCyan, fontSize = 12.sp)
                                        }
                                    },
                                    onClick = {
                                        selectedCourseIndex = idx
                                        location = sub.defaultRoom
                                        if (sub.category.equals("Lab", ignoreCase = true)) {
                                            slotType = "Lab"
                                        }
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Start and End Times
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = it },
                            label = { Text("Start Time") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = endTime,
                            onValueChange = { endTime = it },
                            label = { Text("End Time") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick Preset Times Row
                    Text("Quick Presets:", color = TextMuted, fontSize = 11.sp)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        val hourlyPresets = listOf(
                            "08:00 AM - 09:00 AM",
                            "09:00 AM - 10:00 AM",
                            "10:00 AM - 11:00 AM",
                            "11:00 AM - 12:00 PM",
                            "12:00 PM - 01:00 PM",
                            "01:00 PM - 02:00 PM",
                            "02:00 PM - 03:00 PM",
                            "03:00 PM - 04:00 PM",
                            "04:00 PM - 05:00 PM",
                            "02:00 PM - 04:00 PM"
                        )
                        items(hourlyPresets) { preset ->
                            val parts = preset.split(" - ")
                            Surface(
                                color = DarkBackground,
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.clickable {
                                    if (parts.size == 2) {
                                        startTime = parts[0]
                                        endTime = parts[1]
                                    }
                                }
                            ) {
                                Text(
                                    text = preset,
                                    color = NeonCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Class Location / Room") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Slot Type Chips
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Type:", color = TextSecondary, fontSize = 12.sp)
                        listOf("Lecture", "Lab", "Tutorial").forEach { type ->
                            FilterChip(
                                selected = slotType == type,
                                onClick = { slotType = type },
                                label = { Text(type, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonPurple.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonPurple
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Add Class Button
                    Button(
                        onClick = {
                            if (courses.isNotEmpty()) {
                                val newSlot = ScheduleSlotEntity(
                                    id = 0,
                                    subjectId = 0,
                                    dayOfWeek = selectedDay,
                                    startTime = startTime.trim(),
                                    endTime = endTime.trim(),
                                    location = location.trim().ifBlank { "Pragya Bhavan PB-02" },
                                    slotType = slotType
                                )
                                onSlotsUpdated(slots + Pair(selectedCourseIndex, newSlot))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("btn_add_class_to_day")
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Class to ${dayNames[selectedDay - 1]}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Classes for Selected Day List
        item {
            Text(
                text = "${dayNames[selectedDay - 1]} Classes (${currentDaySlots.size})",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 15.sp
            )
        }

        if (currentDaySlots.isEmpty()) {
            item {
                Surface(
                    color = DarkSurfaceCard,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "No classes added for ${dayNames[selectedDay - 1]}",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Pick a course from the dropdown above and tap Add Class.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        } else {
            items(currentDaySlots) { slotPair ->
                val sub = courses.getOrNull(slotPair.first)
                Surface(
                    color = DarkSurfaceCard,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Column(modifier = Modifier.width(85.dp)) {
                            Text(
                                text = slotPair.second.startTime,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = slotPair.second.endTime,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .background(if (slotPair.second.slotType == "Lab") NeonPurple else NeonCyan, CircleShape)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sub?.name ?: "Course",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${slotPair.second.location} • ${slotPair.second.slotType} (${sub?.credits ?: 4} Cr)",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        IconButton(
                            onClick = {
                                onSlotsUpdated(slots.filter { it != slotPair })
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Action Buttons (Back & Save)
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                OutlinedButton(
                    onClick = onBackToCourses,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("← Courses", color = TextMuted)
                }

                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(2f).height(48.dp).testTag("btn_save_manual_schedule")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ButtonTextOnPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply Schedule (${slots.size} Slots)", color = ButtonTextOnPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
