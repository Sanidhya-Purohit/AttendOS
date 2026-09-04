package com.example.ui.screens.timetable

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.ParsedSubjectSlot
import com.example.data.local.dao.SlotWithSubject
import com.example.data.local.entity.ScheduleSlotEntity
import com.example.data.local.entity.SubjectEntity
import com.example.ui.screens.attendance.AcademicBreaksDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.AttendanceViewModel
import com.example.util.FileUtils
import kotlinx.coroutines.launch

@Composable
fun TimetableScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allSlotsWithSubs by viewModel.allSlotsWithSubjects.collectAsState()
    val allSubjects by viewModel.allSubjects.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val isScanning by viewModel.isScanningTimetable.collectAsState()
    val parsedSlotsReview by viewModel.parsedSlotsReview.collectAsState()

    var isAddSlotDialogOpen by remember { mutableStateOf(false) }
    var isBreaksDialogOpen by remember { mutableStateOf(false) }
    var isManualBuilderOpen by remember { mutableStateOf(false) }
    var isScanOptionsOpen by remember { mutableStateOf(false) }
    var slotToEdit by remember { mutableStateOf<SlotWithSubject?>(null) }
    var isFullWeekMatrixView by remember { mutableStateOf(false) }

    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val shortDayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    // Image Picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val bitmap = FileUtils.uriToBitmap(context, uri)
                if (bitmap != null) {
                    viewModel.analyzeTimetableImage(bitmap)
                }
            }
        }
    }

    // PDF Picker launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val bitmap = FileUtils.uriToBitmap(context, uri)
                if (bitmap != null) {
                    viewModel.analyzeTimetableImage(bitmap)
                }
            }
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
                                text = "SCHEDULE & LOCATIONS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp,
                                    color = NeonCyan
                                )
                            )
                            Text(
                                text = "DTU SE Timetable",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Manual Builder Action
                            FilledTonalButton(
                                onClick = { isManualBuilderOpen = true },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = DarkSurfaceElevated,
                                    contentColor = NeonCyan
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_manual_schedule_builder")
                            ) {
                                Icon(Icons.Default.EditCalendar, contentDescription = "Manual Setup", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Manual", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Scan Image/PDF Options Button
                            Button(
                                onClick = { isScanOptionsOpen = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonPurple,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_ai_scan_timetable")
                            ) {
                                Icon(Icons.Default.DocumentScanner, contentDescription = "Scan", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("AI Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Fests & Breaks Button
                            IconButton(
                                onClick = { isBreaksDialogOpen = true },
                                modifier = Modifier.size(36.dp).background(DarkSurfaceElevated, CircleShape).testTag("btn_timetable_breaks")
                            ) {
                                Icon(Icons.Default.Celebration, contentDescription = "Fests & Breaks", tint = NeonPurple, modifier = Modifier.size(18.dp))
                            }

                            // Add Slot Button
                            IconButton(
                                onClick = { isAddSlotDialogOpen = true },
                                modifier = Modifier.size(36.dp).background(DarkSurfaceElevated, CircleShape).testTag("btn_add_slot")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Slot", tint = NeonCyan, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // View Mode Toggle (Day-by-Day vs Full Week Grid)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Day selector chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items((1..6).toList()) { dayIdx ->
                                val isSelected = dayIdx == selectedDay && !isFullWeekMatrixView
                                Surface(
                                    color = if (isSelected) AppTheme.colors.primary else DarkSurfaceCard,
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) AppTheme.colors.primary else DarkBorder),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            isFullWeekMatrixView = false
                                            viewModel.setSelectedDay(dayIdx)
                                        }
                                ) {
                                    Text(
                                        text = shortDayNames[dayIdx - 1],
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isSelected) Color.White else TextPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Full Week Matrix Toggle
                        IconButton(
                            onClick = { isFullWeekMatrixView = !isFullWeekMatrixView },
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Icon(
                                if (isFullWeekMatrixView) Icons.Default.ViewDay else Icons.Default.GridView,
                                contentDescription = "Matrix View",
                                tint = if (isFullWeekMatrixView) NeonCyan else TextSecondary
                            )
                        }
                    }
                }
            }
        },
        containerColor = DarkBackground,
        modifier = modifier.testTag("screen_timetable")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
        ) {
            // Scanning Progress Indicator
            if (isScanning) {
                item {
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CircularProgressIndicator(color = NeonPurple, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("AttendOS AI Analyzing Timetable...", fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Extracting DTU subjects, classrooms & slots...", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // AI Scan banner
            item {
                Surface(
                    color = DarkSurfaceCard,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Upload Your DTU Timetable Image", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 12.sp)
                            Text("Auto-configures slots & classrooms from photo", color = TextSecondary, fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp).testTag("btn_upload_timetable_image")
                        ) {
                            Text("Gallery", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (!isFullWeekMatrixView) {
                // Day View
                val currentDaySlots = allSlotsWithSubs.filter { it.slot.dayOfWeek == selectedDay }
                item {
                    Text(
                        text = "${dayNames[selectedDay - 1]} Slots (${currentDaySlots.size})",
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (currentDaySlots.isEmpty()) {
                    item {
                        Surface(
                            color = DarkSurfaceCard,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Text(
                                text = "No slots on ${dayNames[selectedDay - 1]}. Tap '+' above to add a slot.",
                                color = TextMuted,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                } else {
                    items(currentDaySlots, key = { it.slot.id }) { slotWithSub ->
                        TimetableSlotItem(
                            slotWithSub = slotWithSub,
                            onEdit = { slotToEdit = slotWithSub },
                            onDelete = { viewModel.deleteSlot(slotWithSub.slot) }
                        )
                    }
                }
            } else {
                // Full Week Matrix
                (1..6).forEach { dayIdx ->
                    val daySlots = allSlotsWithSubs.filter { it.slot.dayOfWeek == dayIdx }
                    item {
                        Text(
                            text = dayNames[dayIdx - 1],
                            fontWeight = FontWeight.Black,
                            color = NeonCyan,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    if (daySlots.isEmpty()) {
                        item {
                            Text(
                                text = "No classes",
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        items(daySlots, key = { "matrix_${it.slot.id}" }) { slotWithSub ->
                            TimetableSlotItem(
                                slotWithSub = slotWithSub,
                                onEdit = { slotToEdit = slotWithSub },
                                onDelete = { viewModel.deleteSlot(slotWithSub.slot) }
                            )
                        }
                    }
                }
            }
        }
    }

    // AI Parsed Schedule Review Dialog
    if (parsedSlotsReview != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissParsedReview() },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Timetable Analysis Results", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    item {
                        Text(
                            text = "Gemini identified ${parsedSlotsReview!!.size} class slots with classroom locations (Pragya Bhavan, AB-4, SPS):",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(parsedSlotsReview!!) { parsed ->
                        Surface(
                            color = DarkSurfaceCard,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${parsed.subjectName} (${parsed.subjectCode})", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                Text("• ${dayNames.getOrElse(parsed.dayOfWeek - 1) { "Day" }}: ${parsed.startTime} - ${parsed.endTime} (${parsed.slotType})", color = NeonCyan, fontSize = 11.sp)
                                Text("• Location: ${parsed.location}", color = TextAccent, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.applyParsedSchedule(parsedSlotsReview!!) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ButtonTextOnPrimary)
                ) {
                    Text("Apply to My Timetable", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissParsedReview() }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Add / Edit Slot Dialog
    if (isAddSlotDialogOpen || slotToEdit != null) {
        AddOrEditSlotDialog(
            slotToEdit = slotToEdit?.slot,
            subjects = allSubjects,
            defaultDay = selectedDay,
            onDismiss = {
                isAddSlotDialogOpen = false
                slotToEdit = null
            },
            onSave = { newSlot ->
                viewModel.addOrUpdateSlot(newSlot)
                isAddSlotDialogOpen = false
                slotToEdit = null
            }
        )
    }

    // Academic Breaks & Fests Dialog
    if (isBreaksDialogOpen) {
        AcademicBreaksDialog(
            viewModel = viewModel,
            onDismiss = { isBreaksDialogOpen = false }
        )
    }

    // AI Scan Source Selection Dialog
    if (isScanOptionsOpen) {
        AlertDialog(
            onDismissRequest = { isScanOptionsOpen = false },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = NeonPurple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Timetable Source", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose how you want to import your schedule. Gemini AI will parse course names, credits, classroom rooms, and weekly slots.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    // Option 1: Pick Image
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isScanOptionsOpen = false
                                imagePickerLauncher.launch("image/*")
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Upload Image (JPG / PNG)", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                Text("Select a photo of your schedule", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }

                    // Option 2: Pick PDF
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isScanOptionsOpen = false
                                pdfPickerLauncher.launch("application/pdf")
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Upload PDF Document", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                Text("Official DTU department PDF timetable", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }

                    // Option 3: Use Sample
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isScanOptionsOpen = false
                                val sampleBitmap = createSampleDTUTimetableBitmap()
                                viewModel.analyzeTimetableImage(sampleBitmap)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Scan DTU Sample Timetable", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                Text("Test AI parsing with standard schedule", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { isScanOptionsOpen = false }) {
                    Text("Close", color = TextMuted)
                }
            }
        )
    }

    // Manual Schedule Builder Dialog
    if (isManualBuilderOpen) {
        val initialSlotsWithIndex = allSlotsWithSubs.mapNotNull { slotWithSub ->
            val subIndex = allSubjects.indexOfFirst { it.id == slotWithSub.subject.id }
            if (subIndex != -1) {
                Pair(subIndex, slotWithSub.slot)
            } else null
        }

        ManualScheduleBuilderDialog(
            initialSubjects = allSubjects,
            initialSlots = initialSlotsWithIndex,
            onDismiss = { isManualBuilderOpen = false },
            onSaveSchedule = { updatedSubs, updatedSlots ->
                viewModel.updateSchedulePreservingAttendance(updatedSubs, updatedSlots)
                isManualBuilderOpen = false
            }
        )
    }
}

@Composable
fun TimetableSlotItem(
    slotWithSub: SlotWithSubject,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = DarkSurfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onEdit() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(modifier = Modifier.width(90.dp)) {
                Text(
                    text = slotWithSub.slot.startTime,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 12.sp
                )
                Text(
                    text = slotWithSub.slot.endTime,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .background(if (slotWithSub.slot.slotType == "Lab") NeonPurple else NeonCyan, CircleShape)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = slotWithSub.subject.name,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 13.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = slotWithSub.slot.location.ifBlank { slotWithSub.subject.defaultRoom },
                        color = TextAccent,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun AddOrEditSlotDialog(
    slotToEdit: ScheduleSlotEntity?,
    subjects: List<com.example.data.local.entity.SubjectEntity>,
    defaultDay: Int,
    onDismiss: () -> Unit,
    onSave: (ScheduleSlotEntity) -> Unit
) {
    var selectedSubjectId by remember { mutableStateOf(slotToEdit?.subjectId ?: (subjects.firstOrNull()?.id ?: 1L)) }
    var dayOfWeek by remember { mutableIntStateOf(slotToEdit?.dayOfWeek ?: defaultDay) }
    var startTime by remember { mutableStateOf(slotToEdit?.startTime ?: "09:00 AM") }
    var endTime by remember { mutableStateOf(slotToEdit?.endTime ?: "10:00 AM") }
    var slotType by remember { mutableStateOf(slotToEdit?.slotType ?: "Lecture") }
    var location by remember { mutableStateOf(slotToEdit?.location ?: "Pragya Bhavan PB-02") }

    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = if (slotToEdit == null) "Add Timetable Slot" else "Edit Slot",
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
                    Text("Select Subject:", color = TextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(subjects) { sub ->
                            val isSelected = sub.id == selectedSubjectId
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedSubjectId = sub.id
                                    location = sub.defaultRoom
                                },
                                label = { Text(sub.name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonCyan
                                )
                            )
                        }
                    }
                }

                item {
                    Text("Day of Week:", color = TextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items((1..6).toList()) { dayIdx ->
                            val isSelected = dayIdx == dayOfWeek
                            FilterChip(
                                selected = isSelected,
                                onClick = { dayOfWeek = dayIdx },
                                label = { Text(dayNames[dayIdx - 1].take(3)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonCyan
                                )
                            )
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = it },
                            label = { Text("Start Time") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = { endTime = it },
                            label = { Text("End Time") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Location (e.g. Pragya Bhavan, AB-4)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text("Slot Type:", color = TextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Lecture", "Lab", "Tutorial").forEach { type ->
                            FilterChip(
                                selected = slotType == type,
                                onClick = { slotType = type },
                                label = { Text(type) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonCyan
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ScheduleSlotEntity(
                            id = slotToEdit?.id ?: 0L,
                            subjectId = selectedSubjectId,
                            dayOfWeek = dayOfWeek,
                            startTime = startTime,
                            endTime = endTime,
                            location = location,
                            slotType = slotType
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ButtonTextOnPrimary)
            ) {
                Text("Save Slot", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

fun createSampleDTUTimetableBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawRect(0f, 0f, 600f, 400f, paint)

    val textPaint = Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 20f
        isAntiAlias = true
    }

    canvas.drawText("DELHI TECHNOLOGICAL UNIVERSITY - SE TIMETABLE", 30f, 40f, textPaint)
    canvas.drawText("Mon 09:00: Software Eng (Pragya Bhavan PB-02)", 30f, 80f, textPaint)
    canvas.drawText("Mon 10:00: Machine Learning (Pragya Bhavan PB-04)", 30f, 110f, textPaint)
    canvas.drawText("Mon 14:00: SE Lab (AB-4 Software Lab-1)", 30f, 140f, textPaint)
    canvas.drawText("Tue 09:00: DBMS (AB-4 Room 204)", 30f, 170f, textPaint)
    canvas.drawText("Wed 09:00: Operating Systems (SPS-09)", 30f, 200f, textPaint)
    canvas.drawText("Wed 14:00: ML & DS Lab (Pragya Bhavan AI Lab 302)", 30f, 230f, textPaint)
    canvas.drawText("Thu 09:00: Software Engineering (PB-02)", 30f, 260f, textPaint)
    canvas.drawText("Fri 10:00: AEC / VAC (Selected) (Tw-1)", 30f, 290f, textPaint)

    return bitmap
}
