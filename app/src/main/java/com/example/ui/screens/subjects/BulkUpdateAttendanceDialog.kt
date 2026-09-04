package com.example.ui.screens.subjects

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.SubjectEntity
import com.example.domain.AttendanceCalculator
import com.example.ui.theme.*
import com.example.ui.viewmodel.AttendanceViewModel

@Composable
fun BulkUpdateAttendanceDialog(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val allSubjects by viewModel.allSubjects.collectAsState()
    var editableList by remember(allSubjects) {
        mutableStateOf(allSubjects.map { it.copy() })
    }

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
                .fillMaxHeight(0.88f)
                .testTag("dialog_bulk_update_attendance")
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
                        Surface(
                            color = NeonCyan.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.EditCalendar, contentDescription = null, tint = NeonCyan)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "UPDATE ATTENDANCE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp,
                                    color = NeonCyan
                                )
                            )
                            Text(
                                text = "Subject-Wise Records",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Text(
                    text = "Update your total attended and conducted counts below. Percentages and tiers update instantly.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Divider(color = DarkBorder, thickness = 1.dp, modifier = Modifier.padding(bottom = 8.dp))

                // List of editable subjects
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(editableList) { index, subject ->
                        val conducted = subject.conductedClasses
                        val attended = subject.attendedClasses
                        val pct = if (conducted == 0) 100.0 else (attended.toDouble() / conducted.toDouble()) * 100.0
                        val tier = AttendanceCalculator.getTier(pct, subject.targetPercentage)

                        Surface(
                            color = DarkBackground,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (tier.isDanger) TierDangerColor.copy(alpha = 0.5f) else DarkBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = subject.name,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        )
                                        Text(
                                            text = "${subject.code} • ${subject.category}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                                        )
                                    }

                                    // Calculated Percentage Badge
                                    Surface(
                                        color = tier.badgeBgColor,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, tier.displayColor.copy(alpha = 0.5f)),
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Text(
                                            text = "${String.format("%.1f", pct)}% • ${tier.badgeLabel}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tier.displayColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Attended counter
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Attended", fontSize = 11.sp, color = TextMuted)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (attended > 0) {
                                                        val updated = editableList.toMutableList()
                                                        updated[index] = subject.copy(attendedClasses = attended - 1)
                                                        editableList = updated
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease", tint = NeonCyan)
                                            }
                                            Text(
                                                text = "$attended",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = TextPrimary,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    val updated = editableList.toMutableList()
                                                    val newAttended = attended + 1
                                                    val newConducted = if (newAttended > conducted) newAttended else conducted
                                                    updated[index] = subject.copy(
                                                        attendedClasses = newAttended,
                                                        conductedClasses = newConducted
                                                    )
                                                    editableList = updated
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase", tint = NeonCyan)
                                            }
                                        }
                                    }

                                    // Conducted counter
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Total Conducted", fontSize = 11.sp, color = TextMuted)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (conducted > 0 && conducted > attended) {
                                                        val updated = editableList.toMutableList()
                                                        updated[index] = subject.copy(conductedClasses = conducted - 1)
                                                        editableList = updated
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease", tint = TextMuted)
                                            }
                                            Text(
                                                text = "$conducted",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = TextPrimary,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    val updated = editableList.toMutableList()
                                                    updated[index] = subject.copy(conductedClasses = conducted + 1)
                                                    editableList = updated
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase", tint = TextMuted)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", color = TextMuted)
                    }

                    Button(
                        onClick = {
                            viewModel.bulkUpdateAttendance(editableList)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f).testTag("btn_save_bulk_attendance")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = ButtonTextOnPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Changes", color = ButtonTextOnPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
