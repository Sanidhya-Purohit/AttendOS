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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.AcademicBreakEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.AttendanceViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AcademicBreaksDialog(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val allBreaks by viewModel.allBreaks.collectAsState()
    var isAddBreakOpen by remember { mutableStateOf(false) }

    val todayStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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
                .fillMaxHeight(0.90f)
                .testTag("dialog_academic_breaks")
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
                                .background(NeonPurple.copy(alpha = 0.18f))
                        ) {
                            Icon(
                                Icons.Default.Celebration,
                                contentDescription = null,
                                tint = NeonPurple,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "College Fests & Breaks",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Tech Fests, Holidays & Class Suspensions",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_academic_breaks")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Info Banner
                Surface(
                    color = DarkSurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "During breaks and fests, scheduled classes are suspended and won't penalize your attendance count or streak.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Add Break Button
                Button(
                    onClick = { isAddBreakOpen = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_add_break_open")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Fest / Break / Suspension", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // List of Breaks
                if (allBreaks.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EventAvailable, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No College Breaks or Fests Logged", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Tap 'Add Fest / Break' above to record college fests or holidays.", fontSize = 12.sp, color = TextSecondary, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(allBreaks, key = { it.id }) { breakItem ->
                            val isActiveNow = breakItem.startDate <= todayStr && todayStr <= breakItem.endDate
                            val isUpcoming = todayStr < breakItem.startDate

                            AcademicBreakCard(
                                breakItem = breakItem,
                                isActiveNow = isActiveNow,
                                isUpcoming = isUpcoming,
                                onDelete = { viewModel.deleteAcademicBreak(breakItem) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Done Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = TextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (isAddBreakOpen) {
        AddBreakDialog(
            todayStr = todayStr,
            onDismiss = { isAddBreakOpen = false },
            onSave = { newBreak ->
                viewModel.addAcademicBreak(newBreak)
                isAddBreakOpen = false
            }
        )
    }
}

@Composable
fun AcademicBreakCard(
    breakItem: AcademicBreakEntity,
    isActiveNow: Boolean,
    isUpcoming: Boolean,
    onDelete: () -> Unit
) {
    val categoryIcon = when (breakItem.category) {
        "TECH_FEST" -> "🚀"
        "CULTURAL_FEST" -> "🎭"
        "EXAM_BREAK" -> "📚"
        "HOLIDAY" -> "🪔"
        "WEATHER_SUSPENSION" -> "🌧️"
        else -> "🎉"
    }

    val categoryColor = when (breakItem.category) {
        "TECH_FEST" -> NeonCyan
        "CULTURAL_FEST" -> NeonPurple
        "EXAM_BREAK" -> NeonGold
        "HOLIDAY" -> NeonEmerald
        "WEATHER_SUSPENSION" -> NeonCrimson
        else -> NeonCyan
    }

    Surface(
        color = if (isActiveNow) DarkSurfaceElevated else DarkSurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.2.dp,
            if (isActiveNow) NeonPurple else DarkBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_academic_break_${breakItem.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(categoryIcon, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = breakItem.title,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${breakItem.startDate}  →  ${breakItem.endDate}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = categoryColor
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActiveNow) {
                        Surface(
                            color = NeonPurple.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple)
                        ) {
                            Text(
                                text = "ACTIVE NOW 🔥",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonPurple,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isUpcoming) {
                        Surface(
                            color = NeonCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "UPCOMING",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Break",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (breakItem.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = breakItem.description,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                )
            }
        }
    }
}

@Composable
fun AddBreakDialog(
    todayStr: String,
    onDismiss: () -> Unit,
    onSave: (AcademicBreakEntity) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(todayStr) }
    var endDate by remember { mutableStateOf(todayStr) }
    var category by remember { mutableStateOf("TECH_FEST") }
    var description by remember { mutableStateOf("Classes suspended during fest / holiday.") }

    val presets = listOf(
        Triple("Invictus Tech Fest", "TECH_FEST", "3-Day DTU Annual Technical Fest & Hackathon"),
        Triple("Engifest Cultural Fest", "CULTURAL_FEST", "Annual Cultural Fest & Celebrity Nights"),
        Triple("Midsem Preparatory Leave", "EXAM_BREAK", "Study prep break before Mid-term examinations"),
        Triple("Diwali Festive Break", "HOLIDAY", "Official festive vacation break"),
        Triple("Heavy Rain / Weather Suspension", "WEATHER_SUSPENSION", "Classes suspended due to weather / civic advisory")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Celebration, contentDescription = null, tint = NeonPurple)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Fest / College Break", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick Presets
                item {
                    Text("Quick Presets (1-Tap):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(presets) { (presetTitle, presetCategory, presetDesc) ->
                            Surface(
                                color = DarkSurfaceCard,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        title = presetTitle
                                        category = presetCategory
                                        description = presetDesc
                                    }
                            ) {
                                Text(
                                    text = presetTitle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Title Input
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Fest / Break Title") },
                        placeholder = { Text("e.g. Invictus Tech Fest 2026") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Date Pickers Row
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = startDate,
                            onValueChange = { startDate = it },
                            label = { Text("Start (YYYY-MM-DD)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = endDate,
                            onValueChange = { endDate = it },
                            label = { Text("End (YYYY-MM-DD)") },
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

                // Category Chips
                item {
                    Text("Category:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    val categories = listOf(
                        "TECH_FEST" to "🚀 Tech Fest",
                        "CULTURAL_FEST" to "🎭 Cultural Fest",
                        "EXAM_BREAK" to "📚 Exam Prep",
                        "HOLIDAY" to "🪔 Holiday",
                        "WEATHER_SUSPENSION" to "🌧️ Suspension"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { (catKey, catLabel) ->
                            val isSelected = category == catKey
                            Surface(
                                color = if (isSelected) NeonPurple else DarkSurfaceCard,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) NeonPurple else DarkBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { category = catKey }
                            ) {
                                Text(
                                    text = catLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Description Input
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Notes / Reason") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            AcademicBreakEntity(
                                title = title,
                                startDate = startDate,
                                endDate = endDate,
                                category = category,
                                description = description,
                                affectsAllClasses = true
                            )
                        )
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
            ) {
                Text("Save Break", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
