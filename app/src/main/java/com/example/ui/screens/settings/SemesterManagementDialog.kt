package com.example.ui.screens.settings

import android.widget.Toast
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
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.AttendOSDatePickerDialog
import com.example.ui.components.AttendOSDateSelectorCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.AttendanceViewModel

@Composable
fun SemesterManagementDialog(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentSemester by viewModel.currentSemester.collectAsState()
    val allSemesters by viewModel.allSemesters.collectAsState()
    val isLocked by viewModel.isCurrentSemesterLocked.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showLockConfirmDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = DarkSurfaceCard,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("dialog_semester_management")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
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
                            Icon(Icons.Default.School, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Semester & Data Archives",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                "Academic Lifecycle, Locking & Backups",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Current Semester Status Card
                    item {
                        Surface(
                            color = DarkBackground,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isLocked) NeonAmber.copy(alpha = 0.5f) else NeonCyan.copy(alpha = 0.4f)
                            ),
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
                                            text = "ACTIVE SEMESTER",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLocked) NeonAmber else NeonCyan
                                        )
                                        Text(
                                            text = currentSemester?.title ?: "Semester 5 (Autumn 2026)",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "${currentSemester?.startDate ?: "2026-08-01"} to ${currentSemester?.endDate ?: "2026-12-15"}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    Surface(
                                        color = if (isLocked) NeonAmber.copy(alpha = 0.15f) else NeonMint.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isLocked) NeonAmber else NeonMint
                                        )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                if (isLocked) Icons.Default.Lock else Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = if (isLocked) NeonAmber else NeonMint,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isLocked) "LOCKED & ARCHIVED" else "ACTIVE & EDITABLE",
                                                color = if (isLocked) NeonAmber else NeonMint,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (!isLocked) {
                                        Button(
                                            onClick = { showLockConfirmDialog = true },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = NeonAmber.copy(alpha = 0.2f),
                                                contentColor = NeonAmber
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Lock Semester", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                currentSemester?.let { viewModel.unlockSemester(it.id) }
                                                Toast.makeText(context, "Semester unlocked for revisions.", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Unlock Revisions", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Button(
                                        onClick = { showCreateDialog = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonCyan,
                                            contentColor = ButtonTextOnPrimary
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("New Semester", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // 2. Export and Backup Actions Card
                    item {
                        Text(
                            text = "OFFICIAL EXPORT & LOCAL BACKUP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            color = DarkBackground,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // PDF Export
                                ExportActionRow(
                                    icon = Icons.Outlined.PictureAsPdf,
                                    title = "Export Official PDF Report",
                                    subtitle = "Formal A4 document with signatures for professors & HOD",
                                    actionLabel = "Export PDF",
                                    accentColor = NeonCrimson,
                                    onClick = {
                                        viewModel.exportPdfReport(context)
                                        Toast.makeText(context, "Generating official attendance PDF...", Toast.LENGTH_SHORT).show()
                                    }
                                )

                                Divider(color = DarkBorder)

                                // CSV / Excel Export
                                ExportActionRow(
                                    icon = Icons.Outlined.TableChart,
                                    title = "Export Excel / CSV Audit Sheet",
                                    subtitle = "Spreadsheet compatible with Microsoft Excel & Google Sheets",
                                    actionLabel = "Export CSV",
                                    accentColor = NeonMint,
                                    onClick = {
                                        viewModel.exportCsvSheet(context)
                                        Toast.makeText(context, "Exporting attendance CSV...", Toast.LENGTH_SHORT).show()
                                    }
                                )

                                Divider(color = DarkBorder)

                                // Full JSON Backup
                                ExportActionRow(
                                    icon = Icons.Outlined.CloudDownload,
                                    title = "Export Full Backup File (.json)",
                                    subtitle = "Contains all subjects, timetable slots, semesters, and attendance logs",
                                    actionLabel = "Save Backup",
                                    accentColor = NeonCyan,
                                    onClick = {
                                        viewModel.exportFullBackup(context)
                                        Toast.makeText(context, "Creating encrypted local backup...", Toast.LENGTH_SHORT).show()
                                    }
                                )

                                Divider(color = DarkBorder)

                                // Restore from Backup
                                ExportActionRow(
                                    icon = Icons.Outlined.SettingsBackupRestore,
                                    title = "Restore Data from Backup File",
                                    subtitle = "Restore your attendance data from a saved JSON backup",
                                    actionLabel = "Restore Data",
                                    accentColor = NeonAmber,
                                    onClick = { showRestoreDialog = true }
                                )
                            }
                        }
                    }

                    // 3. Historical Semesters Switcher
                    if (allSemesters.size > 1) {
                        item {
                            Text(
                                text = "HISTORICAL SEMESTERS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                allSemesters.forEach { sem ->
                                    val isCurrent = sem.isCurrent
                                    Surface(
                                        color = if (isCurrent) NeonCyan.copy(alpha = 0.1f) else DarkSurfaceElevated,
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isCurrent) NeonCyan else DarkBorder
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (!isCurrent) {
                                                    viewModel.switchCurrentSemester(sem.id)
                                                    Toast.makeText(context, "Switched to ${sem.title}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    if (sem.isLocked) Icons.Default.Lock else Icons.Default.School,
                                                    contentDescription = null,
                                                    tint = if (sem.isLocked) NeonAmber else NeonCyan,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(sem.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                                    Text("${sem.startDate} - ${sem.endDate}", fontSize = 10.sp, color = TextSecondary)
                                                }
                                            }

                                            if (isCurrent) {
                                                Text("CURRENT", color = NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            } else {
                                                Text("Tap to View", color = TextMuted, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Data Loss & Cloud Reminder Note
                    item {
                        Surface(
                            color = DarkSurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("MONTHLY BACKUP & DATA RETENTION NOTE", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "AttendOS stores all records locally on your device for absolute privacy and zero tracking. " +
                                            "If the application is uninstalled or storage is cleared, all un-exported data will be lost permanently. " +
                                            "Please export a full backup or PDF report at least once every month and store it securely on Google Drive.",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Lock Semester Confirmation Dialog
    if (showLockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLockConfirmDialog = false },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = NeonAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lock Current Semester?", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        "Once locked, all attendance records for this semester will become read-only to preserve academic integrity. " +
                                "You will still be able to export PDFs and CSVs at any time.\n\n" +
                                "Important: Make sure to export your records now so they are safely backed up.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.lockCurrentSemester {
                            Toast.makeText(context, "Semester locked successfully. Data is now read-only.", Toast.LENGTH_LONG).show()
                            showLockConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonAmber,
                        contentColor = if (AppTheme.colors.isDark) Color.Black else Color.White
                    )
                ) {
                    Text("Confirm & Lock", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLockConfirmDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Create New Semester Dialog
    if (showCreateDialog) {
        CreateNewSemesterDialog(
            currentSemesterNumber = currentSemester?.semesterNumber ?: 5,
            onDismiss = { showCreateDialog = false },
            onCreate = { number, title, year, start, end, lockPrev ->
                viewModel.createNewSemester(
                    semesterNumber = number,
                    title = title,
                    academicYear = year,
                    startDate = start,
                    endDate = end,
                    lockPrevious = lockPrev,
                    onComplete = {
                        Toast.makeText(context, "Created $title. You can now configure subjects and timetable.", Toast.LENGTH_LONG).show()
                        showCreateDialog = false
                    }
                )
            }
        )
    }

    // Restore from Backup Dialog
    if (showRestoreDialog) {
        RestoreBackupDialog(
            viewModel = viewModel,
            onDismiss = { showRestoreDialog = false }
        )
    }
}

@Composable
private fun ExportActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f))
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                Text(subtitle, fontSize = 10.sp, color = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.2f), contentColor = accentColor),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CreateNewSemesterDialog(
    currentSemesterNumber: Int,
    onDismiss: () -> Unit,
    onCreate: (Int, String, String, String, String, Boolean) -> Unit
) {
    var semNum by remember { mutableIntStateOf(currentSemesterNumber + 1) }
    var semTitle by remember { mutableStateOf("Semester ${currentSemesterNumber + 1} (Spring 2027)") }
    var academicYear by remember { mutableStateOf("2026-2027") }
    var startDate by remember { mutableStateOf("2027-01-05") }
    var endDate by remember { mutableStateOf("2027-05-20") }
    var lockPrevious by remember { mutableStateOf(true) }

    var isStartDatePickerOpen by remember { mutableStateOf(false) }
    var isEndDatePickerOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text("Initialize New Semester", fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("Configure your upcoming semester. Previous semester data will be safely isolated.", fontSize = 12.sp, color = TextSecondary)
                }
                item {
                    OutlinedTextField(
                        value = semTitle,
                        onValueChange = { semTitle = it },
                        label = { Text("Semester Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = academicYear,
                        onValueChange = { academicYear = it },
                        label = { Text("Academic Year") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        AttendOSDateSelectorCard(
                            label = "Start Date",
                            selectedDate = startDate,
                            onDateSelected = { startDate = it },
                            pickerTitle = "Select Semester Start Date",
                            modifier = Modifier.weight(1f)
                        )
                        AttendOSDateSelectorCard(
                            label = "End Date",
                            selectedDate = endDate,
                            onDateSelected = { endDate = it },
                            pickerTitle = "Select Semester End Date",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lock Previous Semester", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                            Text("Finalizes and locks earlier attendance records", fontSize = 10.sp, color = TextSecondary)
                        }
                        Switch(checked = lockPrevious, onCheckedChange = { lockPrevious = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(semNum, semTitle, academicYear, startDate, endDate, lockPrevious)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ButtonTextOnPrimary)
            ) {
                Text("Start Semester", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )

    if (isStartDatePickerOpen) {
        AttendOSDatePickerDialog(
            title = "Semester Start Date",
            initialDate = startDate,
            onDateSelected = { startDate = it; isStartDatePickerOpen = false },
            onDismiss = { isStartDatePickerOpen = false }
        )
    }

    if (isEndDatePickerOpen) {
        AttendOSDatePickerDialog(
            title = "Semester End Date",
            initialDate = endDate,
            onDateSelected = { endDate = it; isEndDatePickerOpen = false },
            onDismiss = { isEndDatePickerOpen = false }
        )
    }
}

@Composable
private fun RestoreBackupDialog(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var backupJson by remember { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, tint = NeonAmber)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restore from Backup", fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column {
                Text(
                    "Paste the contents of your AttendOS backup JSON file below to restore your subjects, semesters, and records:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = backupJson,
                    onValueChange = { backupJson = it },
                    placeholder = { Text("{\"app\":\"AttendOS\"...}", color = TextMuted, fontSize = 11.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (backupJson.isBlank()) {
                        Toast.makeText(context, "Please paste JSON backup contents.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isRestoring = true
                    viewModel.restoreFromBackup(context, backupJson) { res ->
                        isRestoring = false
                        if (res.success) {
                            Toast.makeText(
                                context,
                                "Restored: ${res.subjectsCount} subjects, ${res.slotsCount} slots, ${res.recordsCount} records!",
                                Toast.LENGTH_LONG
                            ).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Restore failed: ${res.errorMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !isRestoring,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonAmber,
                    contentColor = if (AppTheme.colors.isDark) Color.Black else Color.White
                )
            ) {
                Text(if (isRestoring) "Restoring..." else "Restore Data", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
