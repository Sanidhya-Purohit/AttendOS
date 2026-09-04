package com.example.ui.screens.setup

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.GeminiService
import com.example.data.ai.ParsedSubjectSlot
import com.example.data.local.entity.AttendanceRecordEntity
import com.example.data.local.entity.ScheduleSlotEntity
import com.example.data.local.entity.SubjectEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.domain.AttendanceCalculator
import com.example.ui.screens.timetable.ManualClassesPhase
import com.example.ui.screens.timetable.ManualCoursesPhase
import com.example.ui.components.AttendOSDatePickerDialog
import com.example.ui.components.AttendOSDateSelectorCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.AttendanceViewModel
import com.example.util.FileUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class PastClassItem(
    val id: String,
    val dateStr: String,
    val dateDisplay: String,
    val subjectIndex: Int,
    val subjectName: String,
    val subjectCode: String,
    val slotTime: String,
    val location: String,
    val slotType: String,
    var status: String
)

@Composable
fun OnboardingSetupScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val geminiService = remember { GeminiService() }

    var currentStep by remember { mutableIntStateOf(1) } // 1 = Upload, 2 = Customize Schedule, 3 = Session Date & Past Attendance, 4 = Alarm & Finish

    // Upload & Parsing State
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var rawTextTimetable by remember { mutableStateOf("") }

    // Extracted Schedule & Subjects
    var configuredSubjects by remember {
        mutableStateOf<List<SubjectEntity>>(emptyList())
    }
    var slotAssignments by remember {
        mutableStateOf<List<Pair<Int, ScheduleSlotEntity>>>(emptyList())
    }

    // Session Start Date & Past Attendance
    var sessionStartDate by remember { mutableStateOf("2026-08-01") }
    var pastClassesList by remember { mutableStateOf<List<PastClassItem>>(emptyList()) }

    // Student Profile Configuration
    var studentName by remember { mutableStateOf("DTU Student") }
    var branch by remember { mutableStateOf("Software Engineering (SE)") }
    var targetThreshold by remember { mutableDoubleStateOf(75.0) }
    var emergencyBuffer by remember { mutableIntStateOf(3) }

    // Alarm Configuration
    var alarmEnabled by remember { mutableStateOf(true) }
    var alarmLeadHours by remember { mutableDoubleStateOf(2.0) }
    var alarmMode by remember { mutableStateOf("RELATIVE") }
    var monAlarm by remember { mutableStateOf("07:00 AM") }
    var tueAlarm by remember { mutableStateOf("07:00 AM") }
    var wedAlarm by remember { mutableStateOf("07:00 AM") }
    var thuAlarm by remember { mutableStateOf("07:00 AM") }
    var friAlarm by remember { mutableStateOf("08:00 AM") }
    var satAlarm by remember { mutableStateOf("08:00 AM") }
    var postClassPromptsEnabled by remember { mutableStateOf(true) }

    // File pickers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileName = "Timetable_Image.jpg"
            coroutineScope.launch {
                isAnalyzing = true
                val bitmap = FileUtils.uriToBitmap(context, uri)
                if (bitmap != null) {
                    val result = geminiService.analyzeTimetableImage(bitmap)
                    val slots = result.getOrNull() ?: emptyList()
                    processExtractedSlots(slots, { subs, assn ->
                        configuredSubjects = subs
                        slotAssignments = assn
                        currentStep = 2
                    })
                }
                isAnalyzing = false
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileName = "DTU_Timetable.pdf"
            coroutineScope.launch {
                isAnalyzing = true
                val bitmap = FileUtils.uriToBitmap(context, uri)
                if (bitmap != null) {
                    val result = geminiService.analyzeTimetableImage(bitmap)
                    val slots = result.getOrNull() ?: emptyList()
                    processExtractedSlots(slots, { subs, assn ->
                        configuredSubjects = subs
                        slotAssignments = assn
                        currentStep = 2
                    })
                } else {
                    loadDTUStarterTemplate { subs, assn ->
                        configuredSubjects = subs
                        slotAssignments = assn
                        currentStep = 2
                    }
                }
                isAnalyzing = false
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Notifications enabled for AttendOS!", Toast.LENGTH_SHORT).show()
        }
    }

    Surface(
        color = DarkBackground,
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .testTag("screen_onboarding_setup")
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Top Step Progress Indicator
            StepProgressHeader(
                currentStep = currentStep,
                totalSteps = 4,
                stepTitle = when (currentStep) {
                    1 -> "Step 1: Timetable Extraction"
                    2 -> "Step 2: Review & Edit Schedule"
                    3 -> "Step 3: Session Date & Past Attendance"
                    else -> "Step 4: Smart Alarms & Notifications"
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Step Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (currentStep) {
                    1 -> {
                        UploadScheduleStep(
                            isAnalyzing = isAnalyzing,
                            rawText = rawTextTimetable,
                            onRawTextChange = { rawTextTimetable = it },
                            onPickImage = { imagePickerLauncher.launch("image/*") },
                            onPickPdf = { pdfPickerLauncher.launch("application/pdf") },
                            onAnalyzeText = { text ->
                                coroutineScope.launch {
                                    isAnalyzing = true
                                    val result = geminiService.analyzeTimetableText(text)
                                    val slots = result.getOrNull() ?: emptyList()
                                    processExtractedSlots(slots, { subs, assn ->
                                        configuredSubjects = subs
                                        slotAssignments = assn
                                        currentStep = 2
                                    })
                                    isAnalyzing = false
                                }
                            },
                            onUseSample = {
                                loadDTUStarterTemplate { subs, assn ->
                                    configuredSubjects = subs
                                    slotAssignments = assn
                                    currentStep = 2
                                }
                            },
                            onManualScheduleConfigured = { subs, assn ->
                                configuredSubjects = subs
                                slotAssignments = assn
                                currentStep = 2
                            }
                        )
                    }
                    2 -> {
                        ReviewAndEditScheduleStep(
                            subjects = configuredSubjects,
                            slots = slotAssignments,
                            onUpdateSubjects = { configuredSubjects = it },
                            onUpdateSlots = { slotAssignments = it },
                            onProceed = {
                                pastClassesList = generatePastClasses(
                                    startDateStr = sessionStartDate,
                                    subjects = configuredSubjects,
                                    slots = slotAssignments
                                )
                                currentStep = 3
                            },
                            onBack = { currentStep = 1 }
                        )
                    }
                    3 -> {
                        SessionDateAndPastAttendanceStep(
                            sessionStartDate = sessionStartDate,
                            onStartDateChange = { newDate ->
                                sessionStartDate = newDate
                                pastClassesList = generatePastClasses(
                                    startDateStr = newDate,
                                    subjects = configuredSubjects,
                                    slots = slotAssignments
                                )
                            },
                            pastClasses = pastClassesList,
                            subjects = configuredSubjects,
                            onUpdatePastClasses = { updatedList ->
                                pastClassesList = updatedList
                            },
                            onProceed = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                currentStep = 4
                            },
                            onBack = { currentStep = 2 }
                        )
                    }
                    4 -> {
                        AlarmAndFinishStep(
                            alarmEnabled = alarmEnabled,
                            onToggleAlarm = { alarmEnabled = it },
                            leadHours = alarmLeadHours,
                            onChangeLeadHours = { alarmLeadHours = it },
                            alarmMode = alarmMode,
                            onChangeAlarmMode = { alarmMode = it },
                            monAlarm = monAlarm,
                            tueAlarm = tueAlarm,
                            wedAlarm = wedAlarm,
                            thuAlarm = thuAlarm,
                            friAlarm = friAlarm,
                            satAlarm = satAlarm,
                            onChangeDayTime = { day, time ->
                                when (day) {
                                    1 -> monAlarm = time
                                    2 -> tueAlarm = time
                                    3 -> wedAlarm = time
                                    4 -> thuAlarm = time
                                    5 -> friAlarm = time
                                    6 -> satAlarm = time
                                }
                            },
                            postClassPromptsEnabled = postClassPromptsEnabled,
                            onTogglePostClassPrompts = { postClassPromptsEnabled = it },
                            studentName = studentName,
                            onChangeName = { studentName = it },
                            branch = branch,
                            onChangeBranch = { branch = it },
                            targetThreshold = targetThreshold,
                            onChangeThreshold = { targetThreshold = it },
                            emergencyBuffer = emergencyBuffer,
                            onChangeBuffer = { emergencyBuffer = it },
                            onBack = { currentStep = 3 },
                            onFinish = {
                                val finalSubjects = configuredSubjects.mapIndexed { idx, sub ->
                                    val subjectClasses = pastClassesList.filter { it.subjectIndex == idx }
                                    val conducted = subjectClasses.count { it.status == "PRESENT" || it.status == "ABSENT" || it.status == "DUTY_LEAVE" }
                                    val attended = subjectClasses.count { it.status == "PRESENT" || it.status == "DUTY_LEAVE" }
                                    sub.copy(
                                        conductedClasses = conducted,
                                        attendedClasses = attended,
                                        targetPercentage = targetThreshold,
                                        emergencyBuffer = emergencyBuffer
                                    )
                                }

                                val pastRecords = pastClassesList.map { item ->
                                    Pair(
                                        item.subjectIndex,
                                        AttendanceRecordEntity(
                                            id = 0,
                                            subjectId = 0,
                                            date = item.dateStr,
                                            status = item.status,
                                            slotTime = item.slotTime,
                                            note = "Initial session setup (${item.location})"
                                        )
                                    )
                                }

                                val profile = UserProfileEntity(
                                    studentName = studentName,
                                    branch = branch,
                                    semester = 5,
                                    rollNumber = "DTU-2026",
                                    globalThreshold = targetThreshold,
                                    globalEmergencyBuffer = emergencyBuffer,
                                    includeBufferInCalculations = true,
                                    isSetupComplete = true,
                                    alarmEnabled = alarmEnabled,
                                    alarmLeadHours = alarmLeadHours,
                                    alarmMode = alarmMode,
                                    monAlarmTime = monAlarm,
                                    tueAlarmTime = tueAlarm,
                                    wedAlarmTime = wedAlarm,
                                    thuAlarmTime = thuAlarm,
                                    friAlarmTime = friAlarm,
                                    satAlarmTime = satAlarm,
                                    semesterStartDate = sessionStartDate
                                )

                                viewModel.completeOnboardingSetupWithHistory(
                                    subjects = finalSubjects,
                                    slotAssignments = slotAssignments,
                                    records = pastRecords,
                                    profile = profile
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepProgressHeader(
    currentStep: Int,
    totalSteps: Int,
    stepTitle: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "ATTENDOS SETUP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        color = NeonCyan
                    )
                )
                Text(
                    text = stepTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }

            Surface(
                color = NeonCyan.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "Step $currentStep of $totalSteps",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            (1..totalSteps).forEach { stepIndex ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (stepIndex <= currentStep) NeonCyan else DarkBorder)
                )
            }
        }
    }
}

@Composable
private fun UploadScheduleStep(
    isAnalyzing: Boolean,
    rawText: String,
    onRawTextChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onPickPdf: () -> Unit,
    onAnalyzeText: (String) -> Unit,
    onUseSample: () -> Unit,
    onManualScheduleConfigured: (List<SubjectEntity>, List<Pair<Int, ScheduleSlotEntity>>) -> Unit
) {
    var setupMode by remember { mutableIntStateOf(1) }
    var manualPhase by remember { mutableIntStateOf(1) }
    var manualCourses by remember { mutableStateOf<List<SubjectEntity>>(emptyList()) }
    var manualSlots by remember { mutableStateOf<List<Pair<Int, ScheduleSlotEntity>>>(emptyList()) }
    var showTextDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Surface(
                color = if (setupMode == 1) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceCard,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (setupMode == 1) NeonCyan else DarkBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable { setupMode = 1 }
                    .testTag("tab_ai_scanner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (setupMode == 1) NeonCyan else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI Auto-Extract",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (setupMode == 1) NeonCyan else TextMuted
                    )
                }
            }

            Surface(
                color = if (setupMode == 2) NeonPurple.copy(alpha = 0.2f) else DarkSurfaceCard,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (setupMode == 2) NeonPurple else DarkBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable { setupMode = 2 }
                    .testTag("tab_manual_schedule")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Default.EditCalendar,
                        contentDescription = null,
                        tint = if (setupMode == 2) NeonPurple else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Manual Entry",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (setupMode == 2) NeonPurple else TextMuted
                    )
                }
            }
        }

        if (setupMode == 1) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(22.dp)
                        ) {
                            Surface(
                                color = NeonCyan.copy(alpha = 0.12f),
                                shape = CircleShape,
                                modifier = Modifier.size(60.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Extract Full Schedule with Gemini AI",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "AI automatically extracts everything: subjects, course credits, lecture/lab types, exact timings, and per-day classroom locations.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                textAlign = TextAlign.Center
                            )

                            if (isAnalyzing) {
                                Spacer(modifier = Modifier.height(18.dp))
                                CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Analyzing timetable with Gemini 2.5 Flash...",
                                    style = MaterialTheme.typography.bodySmall.copy(color = NeonCyan, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onPickImage,
                            enabled = !isAnalyzing,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("btn_upload_image")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = ButtonTextOnPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Upload Timetable Image (JPG / PNG)",
                                color = ButtonTextOnPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        OutlinedButton(
                            onClick = onPickPdf,
                            enabled = !isAnalyzing,
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("btn_upload_pdf")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = NeonPurple)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Upload Timetable PDF Document",
                                color = NeonPurple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        OutlinedButton(
                            onClick = { showTextDialog = true },
                            enabled = !isAnalyzing,
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("btn_paste_timetable_text")
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = null, tint = TextPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Paste Timetable Text / Copy-Paste",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Surface(
                            color = DarkSurfaceCard,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onUseSample)
                                .testTag("btn_use_dtu_preset")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Surface(
                                    color = NeonAmber.copy(alpha = 0.15f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = NeonAmber)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Quick Preset: DTU CSE/SE Schedule",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Software Eng, ML, DBMS, OS, Labs & Pragya Bhavan locations",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                            }
                        }
                    }
                }
            }

            if (showTextDialog) {
                AlertDialog(
                    onDismissRequest = { showTextDialog = false },
                    title = { Text("Paste Timetable Text", color = TextPrimary, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                "Paste your course names, days, timings, and rooms. Gemini will extract full structure:",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = rawText,
                                onValueChange = onRawTextChange,
                                placeholder = { Text("e.g. Mon 9-10 AM: Software Eng (PB-02, 4 Cr), Tue 2-4 PM: ML Lab (PB AI Lab 302)...") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth().height(150.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showTextDialog = false
                                if (rawText.isNotBlank()) {
                                    onAnalyzeText(rawText)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Text("Extract with AI", color = ButtonTextOnPrimary, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTextDialog = false }) {
                            Text("Cancel", color = TextMuted)
                        }
                    },
                    containerColor = DarkSurfaceElevated
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Surface(
                        color = if (manualPhase == 1) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceCard,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (manualPhase == 1) NeonCyan else DarkBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { manualPhase = 1 }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "1. Courses (${manualCourses.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (manualPhase == 1) NeonCyan else TextMuted
                            )
                        }
                    }

                    Surface(
                        color = if (manualPhase == 2) NeonPurple.copy(alpha = 0.2f) else DarkSurfaceCard,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (manualPhase == 2) NeonPurple else DarkBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (manualCourses.isNotEmpty()) {
                                    manualPhase = 2
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "2. Day Classes (${manualSlots.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (manualPhase == 2) NeonPurple else TextMuted
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (manualPhase == 1) {
                        ManualCoursesPhase(
                            courses = manualCourses,
                            onCoursesUpdated = { manualCourses = it },
                            onProceedToClasses = { manualPhase = 2 }
                        )
                    } else {
                        ManualClassesPhase(
                            courses = manualCourses,
                            slots = manualSlots,
                            onSlotsUpdated = { manualSlots = it },
                            onBackToCourses = { manualPhase = 1 },
                            onSave = {
                                onManualScheduleConfigured(manualCourses, manualSlots)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewAndEditScheduleStep(
    subjects: List<SubjectEntity>,
    slots: List<Pair<Int, ScheduleSlotEntity>>,
    onUpdateSubjects: (List<SubjectEntity>) -> Unit,
    onUpdateSlots: (List<Pair<Int, ScheduleSlotEntity>>) -> Unit,
    onProceed: () -> Unit,
    onBack: () -> Unit
) {
    var activeTab by remember { mutableIntStateOf(1) } // 1 = Courses & Electives, 2 = Day-by-Day Classes
    var editingSubjectIndex by remember { mutableStateOf<Int?>(null) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        ) {
            Surface(
                color = if (activeTab == 1) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceCard,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (activeTab == 1) NeonCyan else DarkBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeTab = 1 }
                    .testTag("tab_edit_courses")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Courses & Electives",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (activeTab == 1) NeonCyan else TextPrimary
                    )
                    Text(
                        text = "${subjects.size} subjects (tap to edit/VAC)",
                        fontSize = 10.sp,
                        color = if (activeTab == 1) NeonCyan.copy(alpha = 0.8f) else TextMuted
                    )
                }
            }

            Surface(
                color = if (activeTab == 2) NeonPurple.copy(alpha = 0.2f) else DarkSurfaceCard,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (activeTab == 2) NeonPurple else DarkBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeTab = 2 }
                    .testTag("tab_edit_day_classes")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Day-by-Day Classes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (activeTab == 2) NeonPurple else TextPrimary
                    )
                    Text(
                        text = "${slots.size} scheduled slots",
                        fontSize = 10.sp,
                        color = if (activeTab == 2) NeonPurple.copy(alpha = 0.8f) else TextMuted
                    )
                }
            }
        }

        if (activeTab == 1) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Surface(
                        color = NeonPurple.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Editable Extracted Courses: Tap any course to rename elective/VAC, change credits, or default room.",
                                color = TextPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                itemsIndexed(subjects) { index, sub ->
                    val subSlotsCount = slots.count { it.first == index }
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingSubjectIndex = index }
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
                                        text = sub.code.take(2),
                                        fontWeight = FontWeight.Black,
                                        color = NeonCyan,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = sub.name,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 14.sp
                                    )
                                    if (sub.category.contains("Elective", ignoreCase = true) || sub.category.contains("VAC", ignoreCase = true) || sub.name.contains("Elective", ignoreCase = true) || sub.name.contains("VAC", ignoreCase = true)) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = NeonAmber.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "ELECTIVE / VAC",
                                                color = NeonAmber,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                val subjectDays = slots.filter { it.first == index }
                                    .map { slot ->
                                        when (slot.second.dayOfWeek) {
                                            1 -> "Mon"
                                            2 -> "Tue"
                                            3 -> "Wed"
                                            4 -> "Thu"
                                            5 -> "Fri"
                                            6 -> "Sat"
                                            else -> ""
                                        }
                                    }
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                val daysText = if (subjectDays.isNotEmpty()) "Days: ${subjectDays.joinToString(", ")}" else "⚠️ No weekly days set"

                                Text(
                                    text = "${sub.code} • ${sub.credits} Credits • ${sub.defaultRoom}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "$daysText ($subSlotsCount slots/wk)",
                                    color = if (subSlotsCount > 0) NeonCyan else NeonAmber,
                                    fontSize = 11.sp,
                                    fontWeight = if (subSlotsCount == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            IconButton(
                                onClick = { editingSubjectIndex = index },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = NeonCyan, modifier = Modifier.size(16.dp))
                            }

                            IconButton(
                                onClick = {
                                    val updated = subjects.toMutableList()
                                    updated.removeAt(index)
                                    val updatedSlots = slots.filter { it.first != index }.map {
                                        if (it.first > index) Pair(it.first - 1, it.second) else it
                                    }
                                    onUpdateSubjects(updated)
                                    onUpdateSlots(updatedSlots)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { showAddSubjectDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = NeonCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add New Subject / Course", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                ManualClassesPhase(
                    courses = subjects,
                    slots = slots,
                    onSlotsUpdated = onUpdateSlots,
                    onBackToCourses = { activeTab = 1 },
                    onSave = onProceed
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Text("Back", color = TextMuted)
            }

            Button(
                onClick = onProceed,
                enabled = subjects.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(2f).height(48.dp).testTag("btn_proceed_to_attendance")
            ) {
                Text("Continue to Session Date →", color = ButtonTextOnPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (editingSubjectIndex != null) {
        val idx = editingSubjectIndex!!
        val currentSub = subjects.getOrNull(idx)
        if (currentSub != null) {
            var name by remember(idx) { mutableStateOf(currentSub.name) }
            var code by remember(idx) { mutableStateOf(currentSub.code) }
            var credits by remember(idx) { mutableIntStateOf(currentSub.credits) }
            var room by remember(idx) { mutableStateOf(currentSub.defaultRoom) }
            var category by remember(idx) { mutableStateOf(currentSub.category) }

            // Day slot addition state
            var newSlotDay by remember(idx) { mutableIntStateOf(1) }
            var newSlotTime by remember(idx) { mutableStateOf(if (currentSub.category == "Lab" || currentSub.category == "VAC") "02:00 PM - 04:00 PM" else "09:00 AM - 10:00 AM") }
            var newSlotType by remember(idx) { mutableStateOf(if (currentSub.category == "Lab") "Lab" else "Lecture") }

            AlertDialog(
                onDismissRequest = { editingSubjectIndex = null },
                title = { Text("Edit Course & Class Schedule", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Course Name (e.g. AEC / VAC)") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = code,
                                    onValueChange = { code = it },
                                    label = { Text("Code") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonCyan,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = credits.toString(),
                                    onValueChange = { credits = it.toIntOrNull() ?: 4 },
                                    label = { Text("Credits") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonCyan,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = room,
                                onValueChange = { room = it },
                                label = { Text("Default Classroom / Hall") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Column {
                                Text("Category:", color = TextSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("Core", "Elective", "VAC", "Lab").forEach { cat ->
                                        FilterChip(
                                            selected = category == cat,
                                            onClick = { category = cat },
                                            label = { Text(cat, fontSize = 11.sp, maxLines = 1) },
                                            modifier = Modifier.weight(1f),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = NeonPurple.copy(alpha = 0.3f),
                                                selectedLabelColor = NeonPurple
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            HorizontalDivider(color = DarkBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                "📅 Scheduled Weekly Class Days:",
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                "Classes will be generated on these days. Delete any slot you don't attend (e.g. choose between Wed or Fri AEC/VAC).",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }

                        val subjectSlots = slots.filter { it.first == idx }
                        if (subjectSlots.isEmpty()) {
                            item {
                                Surface(
                                    color = NeonAmber.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        "⚠️ No weekly slots assigned! Add at least one day below so this course generates classes.",
                                        color = NeonAmber,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        } else {
                            items(subjectSlots) { pair ->
                                val slot = pair.second
                                val dayName = when (slot.dayOfWeek) {
                                    1 -> "Monday"
                                    2 -> "Tuesday"
                                    3 -> "Wednesday"
                                    4 -> "Thursday"
                                    5 -> "Friday"
                                    6 -> "Saturday"
                                    else -> "Day ${slot.dayOfWeek}"
                                }
                                Surface(
                                    color = DarkBackground,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = NeonCyan.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        dayName,
                                                        color = NeonCyan,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    slot.slotType,
                                                    color = TextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Text(
                                                "${slot.startTime} - ${slot.endTime} • ${slot.location}",
                                                color = TextMuted,
                                                fontSize = 10.sp
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                val mutableSlots = slots.toMutableList()
                                                mutableSlots.remove(pair)
                                                onUpdateSlots(mutableSlots)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Remove Day Slot",
                                                tint = NeonCrimson,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Surface(
                                color = DarkSurfaceCard,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("+ Add Class Day & Time Slot", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(
                                            Pair(1, "Mon"),
                                            Pair(2, "Tue"),
                                            Pair(3, "Wed"),
                                            Pair(4, "Thu"),
                                            Pair(5, "Fri"),
                                            Pair(6, "Sat")
                                        ).forEach { (dayNum, dayLabel) ->
                                            val isSel = newSlotDay == dayNum
                                            Surface(
                                                color = if (isSel) NeonCyan else DarkBackground,
                                                shape = RoundedCornerShape(6.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) NeonCyan else DarkBorder),
                                                modifier = Modifier.weight(1f).clickable { newSlotDay = dayNum }
                                            ) {
                                                Text(
                                                    text = dayLabel,
                                                    color = if (isSel) DarkBackground else TextPrimary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = newSlotTime,
                                        onValueChange = { newSlotTime = it },
                                        label = { Text("Timing (e.g. 02:00 PM - 04:00 PM)") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NeonCyan,
                                            unfocusedBorderColor = DarkBorder,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf("Lecture", "Lab", "Tutorial").forEach { st ->
                                            FilterChip(
                                                selected = newSlotType == st,
                                                onClick = { newSlotType = st },
                                                label = { Text(st, fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                                    selectedLabelColor = NeonCyan
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        Button(
                                            onClick = {
                                                val parts = newSlotTime.split("-").map { it.trim() }
                                                val sTime = parts.getOrNull(0) ?: "09:00 AM"
                                                val eTime = parts.getOrNull(1) ?: "10:00 AM"
                                                val createdSlot = ScheduleSlotEntity(
                                                    id = 0,
                                                    subjectId = 0,
                                                    dayOfWeek = newSlotDay,
                                                    startTime = sTime,
                                                    endTime = eTime,
                                                    location = room.ifBlank { "Pragya Bhavan PB-02" },
                                                    slotType = newSlotType
                                                )
                                                onUpdateSlots(slots + Pair(idx, createdSlot))
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.25f)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("+ Add Slot", color = NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val updated = subjects.toMutableList()
                            updated[idx] = currentSub.copy(
                                name = name.trim(),
                                code = code.trim(),
                                credits = credits,
                                defaultRoom = room.trim(),
                                category = category
                            )
                            onUpdateSubjects(updated)
                            editingSubjectIndex = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Text("Save Changes", color = ButtonTextOnPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingSubjectIndex = null }) {
                        Text("Cancel", color = TextMuted)
                    }
                },
                containerColor = DarkSurfaceElevated
            )
        }
    }

    if (showAddSubjectDialog) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("DTU-${300 + subjects.size * 2}") }
        var credits by remember { mutableIntStateOf(4) }
        var room by remember { mutableStateOf("Pragya Bhavan PB-02") }
        var category by remember { mutableStateOf("Core") }

        // Weekly scheduled days selection
        var selectedDays by remember { mutableStateOf(setOf(1, 3, 5)) }
        var slotTime by remember { mutableStateOf("09:00 AM - 10:00 AM") }
        var slotType by remember { mutableStateOf("Lecture") }

        AlertDialog(
            onDismissRequest = { showAddSubjectDialog = false },
            title = { Text("+ Add Course with Schedule", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Course Name (e.g. AEC / VAC or Machine Learning)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = code,
                                onValueChange = { code = it },
                                label = { Text("Code") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = credits.toString(),
                                onValueChange = { credits = it.toIntOrNull() ?: 4 },
                                label = { Text("Credits") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = room,
                            onValueChange = { room = it },
                            label = { Text("Default Classroom / Hall") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Column {
                            Text("Category:", color = TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Core", "Elective", "VAC", "Lab").forEach { cat ->
                                    FilterChip(
                                        selected = category == cat,
                                        onClick = {
                                            category = cat
                                            if (cat == "Lab" || cat == "VAC") {
                                                slotTime = "02:00 PM - 04:00 PM"
                                                if (cat == "Lab") slotType = "Lab"
                                                if (cat == "VAC") selectedDays = setOf(3, 5)
                                            }
                                        },
                                        label = { Text(cat, fontSize = 11.sp, maxLines = 1) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonPurple.copy(alpha = 0.3f),
                                            selectedLabelColor = NeonPurple
                                        )
                                    )
                                }
                            }
                        }
                    }

                    item {
                        HorizontalDivider(color = DarkBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            "🗓️ Select Which Days Class is Due:",
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            "Tap days to include. Attendance for this course will be scheduled on selected days.",
                            color = TextMuted,
                            fontSize = 10.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                Pair(1, "Mon"),
                                Pair(2, "Tue"),
                                Pair(3, "Wed"),
                                Pair(4, "Thu"),
                                Pair(5, "Fri"),
                                Pair(6, "Sat")
                            ).forEach { (dayNum, dayLabel) ->
                                val isSel = selectedDays.contains(dayNum)
                                Surface(
                                    color = if (isSel) NeonCyan else DarkBackground,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) NeonCyan else DarkBorder),
                                    modifier = Modifier.weight(1f).clickable {
                                        selectedDays = if (isSel) selectedDays - dayNum else selectedDays + dayNum
                                    }
                                ) {
                                    Text(
                                        text = dayLabel,
                                        color = if (isSel) DarkBackground else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = slotTime,
                            onValueChange = { slotTime = it },
                            label = { Text("Timing (e.g. 09:00 AM - 10:00 AM)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Lecture", "Lab", "Tutorial").forEach { st ->
                                FilterChip(
                                    selected = slotType == st,
                                    onClick = { slotType = st },
                                    label = { Text(st, fontSize = 10.sp) },
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
                        if (name.isNotBlank()) {
                            val colors = listOf("#00E5FF", "#10B981", "#F59E0B", "#EA580C", "#A855F7", "#3B82F6", "#14B8A6")
                            val newSub = SubjectEntity(
                                id = 0,
                                name = name.trim(),
                                code = code.trim(),
                                conductedClasses = 0,
                                attendedClasses = 0,
                                targetPercentage = 75.0,
                                emergencyBuffer = 3,
                                colorHex = colors[subjects.size % colors.size],
                                defaultRoom = room.trim(),
                                category = category,
                                credits = credits
                            )
                            val newSubIndex = subjects.size
                            val parts = slotTime.split("-").map { it.trim() }
                            val sTime = parts.getOrNull(0) ?: "09:00 AM"
                            val eTime = parts.getOrNull(1) ?: "10:00 AM"

                            val newSlots = selectedDays.map { dayNum ->
                                Pair(
                                    newSubIndex,
                                    ScheduleSlotEntity(
                                        id = 0,
                                        subjectId = 0,
                                        dayOfWeek = dayNum,
                                        startTime = sTime,
                                        endTime = eTime,
                                        location = room.trim().ifBlank { "Pragya Bhavan PB-02" },
                                        slotType = slotType
                                    )
                                )
                            }

                            onUpdateSubjects(subjects + newSub)
                            onUpdateSlots(slots + newSlots)
                            showAddSubjectDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Add Course & Schedule", color = ButtonTextOnPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubjectDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}

@Composable
private fun SessionDateAndPastAttendanceStep(
    sessionStartDate: String,
    onStartDateChange: (String) -> Unit,
    pastClasses: List<PastClassItem>,
    subjects: List<SubjectEntity>,
    onUpdatePastClasses: (List<PastClassItem>) -> Unit,
    onProceed: () -> Unit,
    onBack: () -> Unit
) {
    var isCustomDateDialogOpen by remember { mutableStateOf(false) }
    val distinctDates = remember(pastClasses) { pastClasses.map { it.dateStr }.distinct() }
    var selectedDateIndex by remember(distinctDates) { mutableIntStateOf(0) }
    var viewMode by remember { mutableStateOf("WINDOW") } // "WINDOW" or "LIST"
    val calendarStripState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Ensure selected index is within bounds
    LaunchedEffect(distinctDates.size) {
        if (selectedDateIndex >= distinctDates.size && distinctDates.isNotEmpty()) {
            selectedDateIndex = distinctDates.size - 1
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                            Column {
                                Text(
                                    text = "Academic Session Start Date",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Full date-by-date schedule automatically generated from start date to today.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Quick Select Session Start:", color = TextMuted, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            val presets = listOf(
                                Pair("2026-08-01", "Aug 1 (Sem Start)"),
                                Pair("2026-08-16", "Aug 16 (Mid-Aug)"),
                                Pair("2026-07-20", "Jul 20 (Early Start)"),
                                Pair(todayStr, "Today (Fresh 0/0)")
                            )
                            items(presets) { preset ->
                                val isSelected = sessionStartDate == preset.first
                                Surface(
                                    color = if (isSelected) NeonCyan else DarkBackground,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) NeonCyan else DarkBorder),
                                    modifier = Modifier.clickable {
                                        onStartDateChange(preset.first)
                                    }
                                ) {
                                    Text(
                                        text = preset.second,
                                        color = if (isSelected) DarkBackground else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        AttendOSDateSelectorCard(
                            label = "Active Semester Start Date",
                            selectedDate = sessionStartDate,
                            onDateSelected = onStartDateChange,
                            pickerTitle = "Select Semester Start Date"
                        )
                    }
                }
            }

            if (pastClasses.isNotEmpty()) {
                item {
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Generated Past Classes (${pastClasses.size})",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Tap any class to toggle",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        onUpdatePastClasses(pastClasses.map { it.copy(status = "PRESENT") })
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Mark All 100% Present", color = NeonEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        onUpdatePastClasses(pastClasses.mapIndexed { idx, it ->
                                            it.copy(status = if (idx % 5 == 0) "ABSENT" else "PRESENT")
                                        })
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Mark ~80% Present", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    color = DarkSurfaceCard,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Live Calculated Attendance Preview",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        subjects.forEachIndexed { idx, sub ->
                            val subClasses = pastClasses.filter { it.subjectIndex == idx }
                            val conducted = subClasses.count { it.status == "PRESENT" || it.status == "ABSENT" || it.status == "DUTY_LEAVE" }
                            val attended = subClasses.count { it.status == "PRESENT" || it.status == "DUTY_LEAVE" }
                            val pct = if (conducted > 0) (attended.toDouble() / conducted * 100.0) else 100.0
                            val tier = AttendanceCalculator.getTier(pct, 75.0)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier.size(8.dp).background(
                                            try { Color(android.graphics.Color.parseColor(sub.colorHex)) } catch (e: Exception) { NeonCyan },
                                            CircleShape
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(sub.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "$attended / $conducted",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Surface(
                                        color = tier.color.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "%.1f%%".format(pct),
                                            color = tier.color,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (pastClasses.isEmpty()) {
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
                                text = "No past classes generated",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "If your session starts today, you will start with 0/0 fresh attendance. Or pick an earlier start date above to log past classes.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // View Mode Switcher
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Date-by-Date Attendance",
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            fontSize = 14.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                color = if (viewMode == "WINDOW") NeonCyan.copy(alpha = 0.2f) else DarkBackground,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (viewMode == "WINDOW") NeonCyan else DarkBorder),
                                modifier = Modifier.clickable { viewMode = "WINDOW" }
                            ) {
                                Text(
                                    text = "Interactive Window",
                                    color = if (viewMode == "WINDOW") NeonCyan else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                color = if (viewMode == "LIST") NeonPurple.copy(alpha = 0.2f) else DarkBackground,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (viewMode == "LIST") NeonPurple else DarkBorder),
                                modifier = Modifier.clickable { viewMode = "LIST" }
                            ) {
                                Text(
                                    text = "All Days List",
                                    color = if (viewMode == "LIST") NeonPurple else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                if (viewMode == "WINDOW") {
                    val activeDateStr = distinctDates.getOrNull(selectedDateIndex) ?: ""
                    val classesOnSelectedDate = pastClasses.filter { it.dateStr == activeDateStr }
                    val activeDateDisplay = classesOnSelectedDate.firstOrNull()?.dateDisplay ?: activeDateStr

                    // Calendar Ribbon Strip
                    item {
                        Surface(
                            color = DarkSurfaceCard,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                // Shortcuts Bar
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // First Day shortcut
                                    Surface(
                                        color = if (selectedDateIndex == 0) NeonCyan.copy(alpha = 0.25f) else DarkBackground,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedDateIndex == 0) NeonCyan else DarkBorder),
                                        modifier = Modifier.clickable {
                                            selectedDateIndex = 0
                                            coroutineScope.launch { calendarStripState.animateScrollToItem(0) }
                                        }
                                    ) {
                                        Text(
                                            text = "⏮ Sem Start",
                                            color = if (selectedDateIndex == 0) NeonCyan else TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    // Today shortcut
                                    val lastIdx = (distinctDates.size - 1).coerceAtLeast(0)
                                    Surface(
                                        color = if (selectedDateIndex == lastIdx) NeonEmerald.copy(alpha = 0.25f) else DarkBackground,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedDateIndex == lastIdx) NeonEmerald else DarkBorder),
                                        modifier = Modifier.clickable {
                                            selectedDateIndex = lastIdx
                                            coroutineScope.launch { calendarStripState.animateScrollToItem(lastIdx) }
                                        }
                                    ) {
                                        Text(
                                            text = "⚡ Today",
                                            color = if (selectedDateIndex == lastIdx) NeonEmerald else TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    // Mark Day All Present
                                    Surface(
                                        color = Color(0xFF00FF9D).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00FF9D).copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable {
                                            val updated = pastClasses.map {
                                                if (it.dateStr == activeDateStr) it.copy(status = "PRESENT") else it
                                            }
                                            onUpdatePastClasses(updated)
                                        }
                                    ) {
                                        Text(
                                            text = "Day ✅ All",
                                            color = Color(0xFF00FF9D),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    // Mark Day All Absent
                                    Surface(
                                        color = Color(0xFFFF2A6D).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF2A6D).copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable {
                                            val updated = pastClasses.map {
                                                if (it.dateStr == activeDateStr) it.copy(status = "ABSENT") else it
                                            }
                                            onUpdatePastClasses(updated)
                                        }
                                    ) {
                                        Text(
                                            text = "Day ❌ All",
                                            color = Color(0xFFFF2A6D),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Horizontal Calendar Ribbon
                                LazyRow(
                                    state = calendarStripState,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(distinctDates) { idx, dateStr ->
                                        val isSelected = idx == selectedDateIndex
                                        val classesForThisDate = pastClasses.filter { it.dateStr == dateStr }
                                        val display = classesForThisDate.firstOrNull()?.dateDisplay ?: dateStr
                                        // Parse components: e.g. "Mon, 12 Aug"
                                        val parts = display.split(" ")
                                        val dayOfWeek = parts.getOrNull(0)?.replace(",", "") ?: ""
                                        val dayNum = parts.getOrNull(1) ?: ""
                                        val month = parts.getOrNull(2) ?: ""

                                        val allPresent = classesForThisDate.isNotEmpty() && classesForThisDate.all { it.status == "PRESENT" }
                                        val anyAbsent = classesForThisDate.any { it.status == "ABSENT" }

                                        Surface(
                                            color = if (isSelected) AppTheme.colors.surfaceElevated else AppTheme.colors.surfaceCard,
                                            shape = RoundedCornerShape(12.dp),
                                            border = androidx.compose.foundation.BorderStroke(
                                                if (isSelected) 1.5.dp else 1.dp,
                                                if (isSelected) NeonCyan else DarkBorder
                                            ),
                                            modifier = Modifier
                                                .width(56.dp)
                                                .clickable {
                                                    selectedDateIndex = idx
                                                }
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp)
                                            ) {
                                                Text(
                                                    text = dayOfWeek.uppercase(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) NeonCyan else TextMuted
                                                )
                                                Text(
                                                    text = dayNum,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (isSelected) Color.White else TextPrimary
                                                )
                                                Text(
                                                    text = month,
                                                    fontSize = 9.sp,
                                                    color = if (isSelected) NeonCyan else TextMuted
                                                )

                                                Spacer(modifier = Modifier.height(3.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            when {
                                                                allPresent -> Color(0xFF00FF9D)
                                                                anyAbsent -> Color(0xFFFF2A6D)
                                                                else -> Color(0xFFFFB800)
                                                            }
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Active Date Card Header
                    item {
                        Surface(
                            color = NeonPurple.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column {
                                    Text(
                                        text = activeDateDisplay,
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${classesOnSelectedDate.size} classes scheduled",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                Surface(
                                    color = DarkBackground,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                                ) {
                                    Text(
                                        text = "Day ${selectedDateIndex + 1} of ${distinctDates.size}",
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Classes for Active Date
                    items(classesOnSelectedDate) { item ->
                        Surface(
                            color = AppTheme.colors.surfaceCard,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when (item.status) {
                                    "PRESENT" -> Color(0xFF00FF9D).copy(alpha = 0.5f)
                                    "ABSENT" -> Color(0xFFFF2A6D).copy(alpha = 0.5f)
                                    else -> Color(0xFFFFB800).copy(alpha = 0.5f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.subjectName,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${item.slotTime} • ${item.location} • ${item.slotType}",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Present Button
                                    Surface(
                                        color = if (item.status == "PRESENT") Color(0xFF00FF9D) else DarkBackground,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (item.status == "PRESENT") Color(0xFF00FF9D) else DarkBorder),
                                        modifier = Modifier.clickable {
                                            val updated = pastClasses.map {
                                                if (it.id == item.id) it.copy(status = "PRESENT") else it
                                            }
                                            onUpdatePastClasses(updated)
                                        }
                                    ) {
                                        Text(
                                            text = "Present ✅",
                                            color = if (item.status == "PRESENT") DarkBackground else Color(0xFF00FF9D),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }

                                    // Absent Button
                                    Surface(
                                        color = if (item.status == "ABSENT") Color(0xFFFF2A6D) else DarkBackground,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (item.status == "ABSENT") Color(0xFFFF2A6D) else DarkBorder),
                                        modifier = Modifier.clickable {
                                            val updated = pastClasses.map {
                                                if (it.id == item.id) it.copy(status = "ABSENT") else it
                                            }
                                            onUpdatePastClasses(updated)
                                        }
                                    ) {
                                        Text(
                                            text = "Absent ❌",
                                            color = if (item.status == "ABSENT") Color.White else Color(0xFFFF2A6D),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }

                                    // Cancelled Button
                                    Surface(
                                        color = if (item.status == "CANCELLED") Color(0xFFFFB800) else DarkBackground,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (item.status == "CANCELLED") Color(0xFFFFB800) else DarkBorder),
                                        modifier = Modifier.clickable {
                                            val updated = pastClasses.map {
                                                if (it.id == item.id) it.copy(status = "CANCELLED") else it
                                            }
                                            onUpdatePastClasses(updated)
                                        }
                                    ) {
                                        Text(
                                            text = "Off ⚪",
                                            color = if (item.status == "CANCELLED") DarkBackground else Color(0xFFFFB800),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Next Day / Prev Day Navigation Stepper
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (selectedDateIndex > 0) {
                                        selectedDateIndex -= 1
                                        coroutineScope.launch {
                                            calendarStripState.animateScrollToItem(selectedDateIndex)
                                        }
                                    }
                                },
                                enabled = selectedDateIndex > 0,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Prev Day", fontSize = 12.sp, color = TextPrimary)
                            }

                            val isLastDay = selectedDateIndex >= distinctDates.size - 1
                            Button(
                                onClick = {
                                    if (!isLastDay) {
                                        selectedDateIndex += 1
                                        coroutineScope.launch {
                                            calendarStripState.animateScrollToItem(selectedDateIndex)
                                        }
                                    } else {
                                        onProceed()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isLastDay) NeonEmerald else Color(0xFFB537F2)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.5f).height(44.dp)
                            ) {
                                Text(
                                    text = if (isLastDay) "Finish Past Days ✅" else "Next Day →",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (!isLastDay) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                } else {
                    // Full List Mode
                    val groupedByDate = pastClasses.groupBy { it.dateDisplay }
                    groupedByDate.forEach { (dateHeader, classesForDate) ->
                        item {
                            Surface(
                                color = NeonPurple.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) {
                                Text(
                                    text = dateHeader,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        items(classesForDate) { item ->
                            Surface(
                                color = DarkSurfaceCard,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    when (item.status) {
                                        "PRESENT" -> NeonEmerald.copy(alpha = 0.5f)
                                        "ABSENT" -> NeonCrimson.copy(alpha = 0.5f)
                                        else -> DarkBorder
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.subjectName,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "${item.slotTime} • ${item.location} • ${item.slotType}",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Surface(
                                            color = if (item.status == "PRESENT") NeonEmerald else DarkBackground,
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (item.status == "PRESENT") NeonEmerald else DarkBorder),
                                            modifier = Modifier.clickable {
                                                val updated = pastClasses.map {
                                                    if (it.id == item.id) it.copy(status = "PRESENT") else it
                                                }
                                                onUpdatePastClasses(updated)
                                            }
                                        ) {
                                            Text(
                                                text = "Present ✅",
                                                color = if (item.status == "PRESENT") DarkBackground else NeonEmerald,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        Surface(
                                            color = if (item.status == "ABSENT") NeonCrimson else DarkBackground,
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (item.status == "ABSENT") NeonCrimson else DarkBorder),
                                            modifier = Modifier.clickable {
                                                val updated = pastClasses.map {
                                                    if (it.id == item.id) it.copy(status = "ABSENT") else it
                                                }
                                                onUpdatePastClasses(updated)
                                            }
                                        ) {
                                            Text(
                                                text = "Absent ❌",
                                                color = if (item.status == "ABSENT") Color.White else NeonCrimson,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        Surface(
                                            color = if (item.status == "CANCELLED") TextMuted else DarkBackground,
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (item.status == "CANCELLED") TextMuted else DarkBorder),
                                            modifier = Modifier.clickable {
                                                val updated = pastClasses.map {
                                                    if (it.id == item.id) it.copy(status = "CANCELLED") else it
                                                }
                                                onUpdatePastClasses(updated)
                                            }
                                        ) {
                                            Text(
                                                text = "Cancelled ⚪",
                                                color = if (item.status == "CANCELLED") DarkBackground else TextMuted,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
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

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Text("Back", color = TextMuted)
            }

            Button(
                onClick = onProceed,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(2f).height(48.dp).testTag("btn_proceed_to_alarms")
            ) {
                Text("Continue to Alarms & Finish →", color = ButtonTextOnPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (isCustomDateDialogOpen) {
        AttendOSDatePickerDialog(
            title = "Select Semester Start Date",
            initialDate = sessionStartDate,
            onDateSelected = { selectedDate ->
                onStartDateChange(selectedDate)
                isCustomDateDialogOpen = false
            },
            onDismiss = { isCustomDateDialogOpen = false }
        )
    }
}

@Composable
private fun AlarmAndFinishStep(
    alarmEnabled: Boolean,
    onToggleAlarm: (Boolean) -> Unit,
    leadHours: Double,
    onChangeLeadHours: (Double) -> Unit,
    alarmMode: String,
    onChangeAlarmMode: (String) -> Unit,
    monAlarm: String,
    tueAlarm: String,
    wedAlarm: String,
    thuAlarm: String,
    friAlarm: String,
    satAlarm: String,
    onChangeDayTime: (Int, String) -> Unit,
    postClassPromptsEnabled: Boolean,
    onTogglePostClassPrompts: (Boolean) -> Unit,
    studentName: String,
    onChangeName: (String) -> Unit,
    branch: String,
    onChangeBranch: (String) -> Unit,
    targetThreshold: Double,
    onChangeThreshold: (Double) -> Unit,
    emergencyBuffer: Int,
    onChangeBuffer: (Int) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                color = DarkSurfaceCard,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                color = NeonEmerald.copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Post-Class Attendance Notifications",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Instant prompt after each class ends with 1-tap Present / Absent buttons.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Switch(
                            checked = postClassPromptsEnabled,
                            onCheckedChange = onTogglePostClassPrompts,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonEmerald,
                                checkedTrackColor = NeonEmerald.copy(alpha = 0.3f)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "💡 You can also update or adjust any class attendance at any time later in the AttendOS logs.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        item {
            Surface(
                color = DarkSurfaceCard,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                color = NeonCyan.copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Alarm, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "First-Class Smart Wake-Up Alarm",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Calculates earliest class of the day and wakes you up on time.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Switch(
                            checked = alarmEnabled,
                            onCheckedChange = onToggleAlarm,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonCyan,
                                checkedTrackColor = NeonCyan.copy(alpha = 0.3f)
                            )
                        )
                    }

                    if (alarmEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = DarkBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = alarmMode == "RELATIVE",
                                onClick = { onChangeAlarmMode("RELATIVE") },
                                label = { Text("Hours Before First Class", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonCyan
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = alarmMode == "DAY_SPECIFIC",
                                onClick = { onChangeAlarmMode("DAY_SPECIFIC") },
                                label = { Text("Day-by-Day Custom", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonCyan
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (alarmMode == "RELATIVE") {
                            Text(
                                text = "Alarm Timing: ${if (leadHours == leadHours.toInt().toDouble()) "${leadHours.toInt()}" else "$leadHours"} Hours Before First Class",
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val options = listOf(1.0, 1.5, 2.0, 2.5, 3.0)
                                items(options) { hrs ->
                                    val isSelected = leadHours == hrs
                                    Surface(
                                        color = if (isSelected) NeonCyan else DarkBackground,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) NeonCyan else DarkBorder
                                        ),
                                        modifier = Modifier.clickable { onChangeLeadHours(hrs) }
                                    ) {
                                        Text(
                                            text = if (hrs == 2.0) "2.0h (Default)" else "${hrs}h",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) DarkBackground else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DayTimeRow("Mon", monAlarm) { onChangeDayTime(1, it) }
                                DayTimeRow("Tue", tueAlarm) { onChangeDayTime(2, it) }
                                DayTimeRow("Wed", wedAlarm) { onChangeDayTime(3, it) }
                                DayTimeRow("Thu", thuAlarm) { onChangeDayTime(4, it) }
                                DayTimeRow("Fri", friAlarm) { onChangeDayTime(5, it) }
                                DayTimeRow("Sat", satAlarm) { onChangeDayTime(6, it) }
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(
                color = DarkSurfaceCard,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Student Profile & Target Threshold",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = studentName,
                        onValueChange = onChangeName,
                        label = { Text("Student Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = branch,
                        onValueChange = onChangeBranch,
                        label = { Text("Department / Branch") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Exam Eligibility Threshold: ${targetThreshold.toInt()}%", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Emergency Buffer: $emergencyBuffer classes", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                ) {
                    Text("Back", color = TextMuted)
                }

                Button(
                    onClick = onFinish,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(2f).height(50.dp).testTag("btn_complete_setup")
                ) {
                    Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = ButtonTextOnPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Complete & Launch AttendOS",
                        color = ButtonTextOnPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DayTimeRow(dayName: String, currentTime: String, onCycle: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(text = dayName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Surface(
            color = DarkBackground,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.clickable {
                val next = when (currentTime) {
                    "06:30 AM" -> "07:00 AM"
                    "07:00 AM" -> "07:30 AM"
                    "07:30 AM" -> "08:00 AM"
                    else -> "06:30 AM"
                }
                onCycle(next)
            }
        ) {
            Text(
                text = "$currentTime ✏️",
                color = NeonCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

private fun generatePastClasses(
    startDateStr: String,
    subjects: List<SubjectEntity>,
    slots: List<Pair<Int, ScheduleSlotEntity>>
): List<PastClassItem> {
    val items = mutableListOf<PastClassItem>()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val displayFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())

    val startDate = try { sdf.parse(startDateStr) } catch (e: Exception) { null } ?: return emptyList()
    val today = Date()

    if (startDate.after(today)) return emptyList()

    val cal = Calendar.getInstance().apply { time = startDate }
    val todayCal = Calendar.getInstance().apply { time = today }

    var counter = 0
    while (!cal.after(todayCal)) {
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val dayNum = when (dow) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }

        if (dayNum in 1..6) {
            val daySlots = slots.filter { it.second.dayOfWeek == dayNum }
            val curDateStr = sdf.format(cal.time)
            val curDateDisplay = displayFormat.format(cal.time)

            daySlots.forEach { (subIndex, slotEntity) ->
                val sub = subjects.getOrNull(subIndex)
                if (sub != null) {
                    items.add(
                        PastClassItem(
                            id = "past_${counter++}_${curDateStr}",
                            dateStr = curDateStr,
                            dateDisplay = curDateDisplay,
                            subjectIndex = subIndex,
                            subjectName = sub.name,
                            subjectCode = sub.code,
                            slotTime = "${slotEntity.startTime} - ${slotEntity.endTime}",
                            location = slotEntity.location,
                            slotType = slotEntity.slotType,
                            status = "PRESENT"
                        )
                    )
                }
            }
        }
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }

    return items
}

private fun processExtractedSlots(
    slots: List<ParsedSubjectSlot>,
    onDone: (List<SubjectEntity>, List<Pair<Int, ScheduleSlotEntity>>) -> Unit
) {
    if (slots.isEmpty()) {
        loadDTUStarterTemplate(onDone)
        return
    }

    val distinctSubs = slots.distinctBy { it.subjectName }
    val subjects = distinctSubs.mapIndexed { idx, p ->
        val colors = listOf("#00E5FF", "#10B981", "#F59E0B", "#EA580C", "#A855F7", "#3B82F6", "#14B8A6")
        SubjectEntity(
            id = 0,
            name = p.subjectName,
            code = p.subjectCode.ifBlank { "DTU-${300 + idx}" },
            conductedClasses = 0,
            attendedClasses = 0,
            targetPercentage = 75.0,
            emergencyBuffer = 3,
            colorHex = colors[idx % colors.size],
            defaultRoom = p.location.ifBlank { "Pragya Bhavan PB-02" },
            category = p.category.ifBlank { "Core" },
            credits = if (p.credits > 0) p.credits else 4
        )
    }

    val assignments = slots.map { slot ->
        val subIndex = distinctSubs.indexOfFirst { it.subjectName == slot.subjectName }.coerceAtLeast(0)
        Pair(
            subIndex,
            ScheduleSlotEntity(
                id = 0,
                subjectId = 0,
                dayOfWeek = slot.dayOfWeek,
                startTime = slot.startTime,
                endTime = slot.endTime,
                location = slot.location,
                slotType = slot.slotType
            )
        )
    }

    onDone(subjects, assignments)
}

private fun loadDTUStarterTemplate(
    onDone: (List<SubjectEntity>, List<Pair<Int, ScheduleSlotEntity>>) -> Unit
) {
    val subjects = listOf(
        SubjectEntity(name = "Software Engineering", code = "SE-301", conductedClasses = 0, attendedClasses = 0, defaultRoom = "Pragya Bhavan PB-02", colorHex = "#00E5FF", credits = 4),
        SubjectEntity(name = "Machine Learning", code = "SE-303", conductedClasses = 0, attendedClasses = 0, defaultRoom = "Pragya Bhavan PB-04", colorHex = "#10B981", credits = 4),
        SubjectEntity(name = "Operating Systems", code = "SE-305", conductedClasses = 0, attendedClasses = 0, defaultRoom = "SPS-09", colorHex = "#F59E0B", credits = 4),
        SubjectEntity(name = "Database Management Systems", code = "SE-307", conductedClasses = 0, attendedClasses = 0, defaultRoom = "AB-4 Room 204", colorHex = "#EA580C", credits = 4),
        SubjectEntity(name = "Software Engineering Lab", code = "SE-309", conductedClasses = 0, attendedClasses = 0, defaultRoom = "AB-4 Lab-1", category = "Lab", colorHex = "#A855F7", credits = 2),
        SubjectEntity(name = "ML & Data Science Lab", code = "SE-311", conductedClasses = 0, attendedClasses = 0, defaultRoom = "PB AI Lab 302", category = "Lab", colorHex = "#3B82F6", credits = 2),
        SubjectEntity(name = "AEC / VAC (Selected)", code = "VAC-101", conductedClasses = 0, attendedClasses = 0, defaultRoom = "Tw-1 Tech Wing", category = "VAC", colorHex = "#14B8A6", credits = 3)
    )

    val assignments = listOf(
        Pair(0, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 1, startTime = "09:00 AM", endTime = "10:00 AM", location = "Pragya Bhavan PB-02")),
        Pair(1, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 1, startTime = "10:00 AM", endTime = "11:00 AM", location = "Pragya Bhavan PB-04")),
        Pair(2, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 1, startTime = "11:00 AM", endTime = "12:00 PM", location = "SPS-09")),
        Pair(4, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 1, startTime = "02:00 PM", endTime = "04:00 PM", location = "AB-4 Lab-1", slotType = "Lab")),

        Pair(3, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 2, startTime = "09:00 AM", endTime = "10:00 AM", location = "AB-4 Room 204")),
        Pair(0, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 2, startTime = "10:00 AM", endTime = "11:00 AM", location = "Pragya Bhavan PB-02")),
        Pair(6, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 2, startTime = "11:00 AM", endTime = "12:00 PM", location = "Tw-1 Tech Wing")),
        Pair(1, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 2, startTime = "01:00 PM", endTime = "02:00 PM", location = "Pragya Bhavan PB-04")),

        Pair(2, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 3, startTime = "09:00 AM", endTime = "10:00 AM", location = "SPS-09")),
        Pair(3, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 3, startTime = "10:00 AM", endTime = "11:00 AM", location = "AB-4 Room 204")),
        Pair(5, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 3, startTime = "02:00 PM", endTime = "04:00 PM", location = "PB AI Lab 302", slotType = "Lab")),

        Pair(0, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 4, startTime = "09:00 AM", endTime = "10:00 AM", location = "Pragya Bhavan PB-02")),
        Pair(1, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 4, startTime = "10:00 AM", endTime = "11:00 AM", location = "Pragya Bhavan PB-04")),
        Pair(2, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 4, startTime = "11:00 AM", endTime = "12:00 PM", location = "SPS-09")),
        Pair(3, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 4, startTime = "12:00 PM", endTime = "01:00 PM", location = "AB-4 Room 204")),

        Pair(6, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 5, startTime = "10:00 AM", endTime = "11:00 AM", location = "Tw-1 Tech Wing")),
        Pair(3, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 5, startTime = "11:00 AM", endTime = "12:00 PM", location = "AB-4 Room 204")),
        Pair(0, ScheduleSlotEntity(id = 0, subjectId = 0, dayOfWeek = 5, startTime = "02:00 PM", endTime = "03:00 PM", location = "Pragya Bhavan PB-02", slotType = "Tutorial"))
    )

    onDone(subjects, assignments)
}
