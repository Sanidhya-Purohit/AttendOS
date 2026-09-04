package com.example.ui.screens.settings

import android.app.TimePickerDialog
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.UserProfileEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.AttendanceViewModel
import com.example.ui.viewmodel.InAppNotification

@Composable
fun SettingsDialog(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsState()

    var name by remember(profile) { mutableStateOf(profile?.studentName ?: "DTUite") }
    var rollNo by remember(profile) { mutableStateOf(profile?.rollNumber ?: "2K23/SE/101") }
    var branch by remember(profile) { mutableStateOf(profile?.branch ?: "Software Engineering (SE)") }
    var threshold by remember(profile) { mutableDoubleStateOf(profile?.globalThreshold ?: 75.0) }
    var buffer by remember(profile) { mutableIntStateOf(profile?.globalEmergencyBuffer ?: 3) }
    var includeBuffer by remember(profile) { mutableStateOf(profile?.includeBufferInCalculations ?: true) }
    var reminder30m by remember(profile) { mutableStateOf(profile?.reminder30MinEnabled ?: true) }
    var dropNotif by remember(profile) { mutableStateOf(profile?.levelDropNotificationEnabled ?: true) }
    var midsemDate by remember(profile) { mutableStateOf(profile?.midsemDate ?: "2026-10-15") }
    var endsemDate by remember(profile) { mutableStateOf(profile?.endsemDate ?: "2026-12-08") }
    var calculationMode by remember(profile) { mutableStateOf(profile?.calculationMode ?: "NEXT_EXAM") }
    var examCutoffLeadDays by remember(profile) { mutableIntStateOf(profile?.examCutoffLeadDays ?: 2) }
    var nextExamName by remember(profile) { mutableStateOf(profile?.nextExamName ?: "Midsem Exams") }

    // Alarm state
    var alarmEnabled by remember(profile) { mutableStateOf(profile?.alarmEnabled ?: true) }
    var alarmLeadHours by remember(profile) { mutableStateOf(profile?.alarmLeadHours ?: 2.0) }
    var alarmMode by remember(profile) { mutableStateOf(profile?.alarmMode ?: "RELATIVE") }
    var monAlarm by remember(profile) { mutableStateOf(profile?.monAlarmTime ?: "07:00 AM") }
    var tueAlarm by remember(profile) { mutableStateOf(profile?.tueAlarmTime ?: "07:00 AM") }
    var wedAlarm by remember(profile) { mutableStateOf(profile?.wedAlarmTime ?: "07:00 AM") }
    var thuAlarm by remember(profile) { mutableStateOf(profile?.thuAlarmTime ?: "07:00 AM") }
    var friAlarm by remember(profile) { mutableStateOf(profile?.friAlarmTime ?: "08:00 AM") }
    var satAlarm by remember(profile) { mutableStateOf(profile?.satAlarmTime ?: "08:00 AM") }

    var showSemesterDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = NeonCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Settings & Preferences", fontWeight = FontWeight.Black, color = TextPrimary, fontSize = 17.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // FIRST CLASS ALARM SYSTEM CARD
                item {
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (alarmEnabled) NeonCyan.copy(alpha = 0.5f) else DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Alarm, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("First Class Wake-Up Alarm", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                        Text("Auto-triggers before earliest lecture", color = TextMuted, fontSize = 11.sp)
                                    }
                                }
                                Switch(
                                    checked = alarmEnabled,
                                    onCheckedChange = { alarmEnabled = it },
                                    colors = SwitchDefaults.colors(checkedTrackColor = NeonCyan)
                                )
                            }

                            if (alarmEnabled) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = alarmMode == "RELATIVE",
                                        onClick = { alarmMode = "RELATIVE" },
                                        label = { Text("Hours Before", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(alpha = 0.2f), selectedLabelColor = NeonCyan)
                                    )
                                    FilterChip(
                                        selected = alarmMode == "DAY_SPECIFIC",
                                        onClick = { alarmMode = "DAY_SPECIFIC" },
                                        label = { Text("Per Day Custom", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(alpha = 0.2f), selectedLabelColor = NeonCyan)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (alarmMode == "RELATIVE") {
                                    Text(
                                        text = "Alarm set for ${if (alarmLeadHours == alarmLeadHours.toInt().toDouble()) "${alarmLeadHours.toInt()}" else "$alarmLeadHours"} hours before first class",
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val options = listOf(1.0, 1.5, 2.0, 2.5, 3.0)
                                        items(options) { hrs ->
                                            val isSel = alarmLeadHours == hrs
                                            Surface(
                                                color = if (isSel) NeonCyan else DarkBackground,
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) NeonCyan else DarkBorder),
                                                modifier = Modifier.clickable { alarmLeadHours = hrs }
                                            ) {
                                                Text(
                                                    text = "${hrs}h",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSel) DarkBackground else TextSecondary,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        DayTimeChip("Mon", monAlarm) { monAlarm = it }
                                        DayTimeChip("Tue", tueAlarm) { tueAlarm = it }
                                        DayTimeChip("Wed", wedAlarm) { wedAlarm = it }
                                        DayTimeChip("Thu", thuAlarm) { thuAlarm = it }
                                        DayTimeChip("Fri", friAlarm) { friAlarm = it }
                                        DayTimeChip("Sat", satAlarm) { satAlarm = it }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.triggerTestAlarmNow() },
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonAmber),
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        Text("Test Alarm 🔔", color = NeonAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.syncWithSystemClock(7, 0) },
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        Text("Sync Clock App", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // NOTIFICATION CONTROLS
                item {
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("PHONE NOTIFICATIONS", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("30-Min Prior Class Alerts", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                    Text("Alerts with Pragya Bhavan / AB-4 room info", color = TextSecondary, fontSize = 11.sp)
                                }
                                Switch(checked = reminder30m, onCheckedChange = { reminder30m = it })
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Level Drop Shortage Alarms", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                    Text("Warns when attendance drops 5% level or <75%", color = TextSecondary, fontSize = 11.sp)
                                }
                                Switch(checked = dropNotif, onCheckedChange = { dropNotif = it })
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    viewModel.triggerTestPhoneNotification()
                                    Toast.makeText(context, "Test Push Notification Sent!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCrimson),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Send Test Phone Push Notification", color = NeonCrimson, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // STUDENT PROFILE
                item {
                    Text("STUDENT PROFILE", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 11.sp)
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Student Name") },
                        modifier = Modifier.fillMaxWidth().testTag("input_student_name")
                    )
                }

                item {
                    OutlinedTextField(
                        value = rollNo,
                        onValueChange = { rollNo = it },
                        label = { Text("Roll Number (e.g. 2K23/SE/101)") },
                        modifier = Modifier.fillMaxWidth().testTag("input_roll_number")
                    )
                }

                item {
                    OutlinedTextField(
                        value = branch,
                        onValueChange = { branch = it },
                        label = { Text("Branch / Department") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // CRITERIA & BUFFER
                item {
                    Divider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))
                    Text("CRITERIA & THRESHOLDS", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 11.sp)
                }

                item {
                    Text("Minimum Attendance Criteria: ${threshold.toInt()}%", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Slider(
                        value = threshold.toFloat(),
                        onValueChange = { threshold = it.toDouble() },
                        valueRange = 60f..90f,
                        steps = 5,
                        colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                    )
                }

                item {
                    Text("Emergency Leave Buffer: $buffer classes per subject", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Slider(
                        value = buffer.toFloat(),
                        onValueChange = { buffer = it.toInt() },
                        valueRange = 1f..6f,
                        steps = 4,
                        colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                    )
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Include Buffer in Safe Bunk Counts", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                            Text("Leaves $buffer classes reserved for health/emergency", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(checked = includeBuffer, onCheckedChange = { includeBuffer = it })
                    }
                }

                // EXAM CUTOFF & MILESTONE TIMEFRAME CONFIGURATION
                item {
                    Divider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))
                    Text("EXAM CUTOFF & CALCULATION TIMEFRAME", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 11.sp)
                }

                item {
                    Surface(
                        color = DarkSurfaceCard,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Default Attendance Calculation Mode",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Choose which timeframe your dashboard numbers target by default",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilterChip(
                                    selected = calculationMode == "NEXT_EXAM",
                                    onClick = { calculationMode = "NEXT_EXAM" },
                                    label = { Text("🎯 Till Next Exam", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = NeonCyan
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                FilterChip(
                                    selected = calculationMode == "FULL_SEMESTER",
                                    onClick = { calculationMode = "FULL_SEMESTER" },
                                    label = { Text("🎓 Full Semester", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = NeonCyan
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Exam Cutoff Lead Time: $examCutoffLeadDays days before exam",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Calculations freeze/target attendance up to $examCutoffLeadDays days prior to exam start",
                                color = TextMuted,
                                fontSize = 11.sp
                            )

                            Slider(
                                value = examCutoffLeadDays.toFloat(),
                                onValueChange = { examCutoffLeadDays = it.toInt() },
                                valueRange = 0f..10f,
                                steps = 9,
                                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = nextExamName,
                                onValueChange = { nextExamName = it },
                                label = { Text("Next Exam Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = midsemDate,
                                    onValueChange = { midsemDate = it },
                                    label = { Text("Midsem Date (yyyy-MM-dd)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = endsemDate,
                                    onValueChange = { endsemDate = it },
                                    label = { Text("Endsem Date (yyyy-MM-dd)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // ACADEMIC LIFECYCLE & DATA ARCHIVES
                item {
                    Divider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))
                    Text("ACADEMIC LIFECYCLE & DATA ARCHIVES", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 11.sp)
                }

                item {
                    Button(
                        onClick = { showSemesterDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.15f), contentColor = NeonCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Semester Lifecycle, PDF / CSV & Backups", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // RE-UPLOAD OR RESET SETUP
                item {
                    Divider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))
                    Text("SCHEDULE SETUP MANAGEMENT", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 11.sp)
                }

                item {
                    OutlinedButton(
                        onClick = {
                            viewModel.resetAppToSetup()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAmber),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonAmber),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-Upload Timetable / Re-Run Setup", fontWeight = FontWeight.Bold)
                    }
                }


            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = (profile ?: UserProfileEntity()).copy(
                        studentName = name,
                        rollNumber = rollNo,
                        branch = branch,
                        globalThreshold = threshold,
                        globalEmergencyBuffer = buffer,
                        includeBufferInCalculations = includeBuffer,
                        reminder30MinEnabled = reminder30m,
                        levelDropNotificationEnabled = dropNotif,
                        midsemDate = midsemDate,
                        endsemDate = endsemDate,
                        calculationMode = calculationMode,
                        examCutoffLeadDays = examCutoffLeadDays,
                        nextExamName = nextExamName,
                        alarmEnabled = alarmEnabled,
                        alarmLeadHours = alarmLeadHours,
                        alarmMode = alarmMode,
                        monAlarmTime = monAlarm,
                        tueAlarmTime = tueAlarm,
                        wedAlarmTime = wedAlarm,
                        thuAlarmTime = thuAlarm,
                        friAlarmTime = friAlarm,
                        satAlarmTime = satAlarm,
                        isSetupComplete = true
                    )
                    viewModel.updateProfile(updated)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ButtonTextOnPrimary),
                modifier = Modifier.testTag("btn_save_settings")
            ) {
                Text("Save Settings", fontWeight = FontWeight.Bold)
            }
        }
    )

    if (showSemesterDialog) {
        SemesterManagementDialog(
            viewModel = viewModel,
            onDismiss = { showSemesterDialog = false }
        )
    }
}

@Composable
private fun DayTimeChip(dayName: String, time: String, onCycle: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(dayName, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Surface(
            color = DarkBackground,
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.clickable {
                val next = when (time) {
                    "06:30 AM" -> "07:00 AM"
                    "07:00 AM" -> "07:30 AM"
                    "07:30 AM" -> "08:00 AM"
                    else -> "06:30 AM"
                }
                onCycle(next)
            }
        ) {
            Text(
                text = "$time ✏️",
                color = NeonCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun NotificationsListDialog(
    notifications: List<InAppNotification>,
    onDismissItem: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = NeonCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Attendance Alerts", fontWeight = FontWeight.Black, color = TextPrimary, fontSize = 17.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }
        },
        text = {
            if (notifications.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                ) {
                    Text("No pending alerts. All clear!", color = TextMuted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications, key = { it.id }) { notif ->
                        val isDanger = notif.type == "DANGER"
                        Surface(
                            color = if (isDanger) NeonCrimson.copy(alpha = 0.15f) else DarkSurfaceCard,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDanger) NeonCrimson else DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    if (isDanger) Icons.Default.Warning else Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = if (isDanger) NeonCrimson else NeonCyan,
                                    modifier = Modifier.size(20.dp).padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(notif.title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                    Text(notif.message, color = TextSecondary, fontSize = 11.sp)
                                }
                                IconButton(onClick = { onDismissItem(notif.id) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated)
            ) {
                Text("Close", color = NeonCyan)
            }
        }
    )
}
