package com.example.ui.screens.analyzer

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.AttendanceCalculator
import com.example.ui.components.TierBadge
import com.example.ui.theme.*
import com.example.ui.viewmodel.AttendanceViewModel

@Composable
fun AnalyzerScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Leave Day Optimizer, 1 = Exam Projections, 2 = Bunk Simulator
    val leaveScores = remember(viewModel.allSlotsWithSubjects, viewModel.subjectStatsList) {
        viewModel.getLeaveDayScores()
    }

    val statsList by viewModel.subjectStatsList.collectAsState()
    val profile by viewModel.userProfile.collectAsState()

    var isEndsemSelected by remember { mutableStateOf(true) }
    val examProjection = remember(isEndsemSelected, statsList, profile) {
        viewModel.getExamProjections(isEndsemSelected)
    }

    Scaffold(
        topBar = {
            Surface(
                color = DarkBackground,
                modifier = Modifier.statusBarsPadding()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "ACADEMIC STRATEGIST",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = NeonCyan
                        )
                    )
                    Text(
                        text = "DTU Attendance Intelligence",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tab Selector
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = DarkSurfaceCard,
                        contentColor = AppTheme.colors.primary,
                        indicator = {},
                        divider = {},
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            selectedContentColor = AppTheme.colors.primary,
                            unselectedContentColor = AppTheme.colors.textMuted,
                            text = { Text("Leave Days", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            selectedContentColor = AppTheme.colors.primary,
                            unselectedContentColor = AppTheme.colors.textMuted,
                            text = { Text("Exam Roadmap", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            selectedContentColor = AppTheme.colors.primary,
                            unselectedContentColor = AppTheme.colors.textMuted,
                            text = { Text("Bunk Simulator", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        },
        containerColor = DarkBackground,
        modifier = modifier.testTag("screen_analyzer")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // Leave Day Optimizer
                    item {
                        Surface(
                            color = DarkSurfaceCard,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("🎯 Smart Bunk & Leave Day Analyzer", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                Text(
                                    "Evaluates subject danger levels, practical lab presence in Pragya Bhavan/AB-4, and daily class density to score the safest days for leave.",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    items(leaveScores) { scoreItem ->
                        LeaveDayScoreCard(scoreItem = scoreItem)
                    }
                }

                1 -> {
                    // Exam Projections (Midsem / Endsem)
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { isEndsemSelected = false },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isEndsemSelected) NeonCyan else DarkSurfaceCard,
                                        contentColor = if (!isEndsemSelected) Color.White else TextSecondary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Mid-Sem Target", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = { isEndsemSelected = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isEndsemSelected) NeonCyan else DarkSurfaceCard,
                                        contentColor = if (isEndsemSelected) Color.White else TextSecondary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("End-Sem Target", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

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
                                    Icon(Icons.Default.Event, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(examProjection.examName, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                        Text("Target Date: ${examProjection.targetDate} (~${examProjection.weeksRemaining} academic weeks remaining)", color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    items(examProjection.projectedSubjectOutcomes) { outcome ->
                        ProjectedOutcomeCard(outcome = outcome, targetThreshold = profile?.globalThreshold ?: 75.0)
                    }
                }

                2 -> {
                    // Bunk Simulator
                    item {
                        BunkSimulatorSection(statsList = statsList)
                    }
                }
            }
        }
    }
}

@Composable
fun LeaveDayScoreCard(scoreItem: com.example.ui.viewmodel.LeaveDayScore) {
    val scoreColor = when {
        scoreItem.safetyScore >= 75 -> NeonEmerald
        scoreItem.safetyScore >= 50 -> NeonGold
        else -> NeonCrimson
    }

    Surface(
        color = DarkSurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, scoreColor.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(scoreColor.copy(alpha = 0.15f))
                    ) {
                        Text(
                            text = "${scoreItem.safetyScore}",
                            fontWeight = FontWeight.Black,
                            color = scoreColor,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = scoreItem.dayName,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${scoreItem.totalSlots} Slots ${if (scoreItem.hasLab) "• Includes Practical Lab" else ""}",
                            color = if (scoreItem.hasLab) NeonPurple else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (scoreItem.hasLab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Surface(
                    color = scoreColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (scoreItem.safetyScore >= 75) "RECOMMENDED OFF" else if (scoreItem.safetyScore >= 50) "MODERATE RISK" else "HIGH RISK",
                        color = scoreColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = scoreItem.recommendation,
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp)
            )
        }
    }
}

@Composable
fun ProjectedOutcomeCard(outcome: com.example.ui.viewmodel.ProjectedSubjectOutcome, targetThreshold: Double) {
    val tier = AttendanceCalculator.getTier(outcome.currentPercentage, targetThreshold)

    Surface(
        color = DarkSurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (outcome.isRecoverableBeforeExam) DarkBorder else NeonCrimson),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = outcome.subjectName,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = "Current: ${String.format("%.1f", outcome.currentPercentage)}%",
                    fontWeight = FontWeight.Bold,
                    color = tier.displayColor,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Estimated ${outcome.remainingClassesEstimate} future classes before exam.",
                color = TextMuted,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = NeonEmerald.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("If 100% Future Attended", fontSize = 10.sp, color = TextSecondary)
                        Text("${String.format("%.1f", outcome.ifAttendAllPercentage)}%", fontWeight = FontWeight.Black, color = NeonEmerald, fontSize = 14.sp)
                    }
                }

                Surface(
                    color = NeonCyan.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("If 75% Future Attended", fontSize = 10.sp, color = TextSecondary)
                        Text("${String.format("%.1f", outcome.ifAttend75Percentage)}%", fontWeight = FontWeight.Black, color = NeonCyan, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Predictive Recovery Trajectory Card
            Surface(
                color = if (outcome.currentPercentage < targetThreshold) NeonCrimson.copy(alpha = 0.12f) else NeonEmerald.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (outcome.currentPercentage < targetThreshold) NeonCrimson.copy(alpha = 0.4f) else NeonEmerald.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(10.dp)
                ) {
                    Icon(
                        imageVector = if (outcome.currentPercentage < targetThreshold) Icons.Default.TrendingUp else Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (outcome.currentPercentage < targetThreshold) NeonCrimson else NeonEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (outcome.currentPercentage < targetThreshold) "Predictive Recovery Trajectory" else "Safety Buffer Trajectory",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (outcome.currentPercentage < targetThreshold) NeonCrimson else NeonEmerald
                        )
                        Text(
                            text = if (outcome.currentPercentage < targetThreshold) {
                                "Attend ${outcome.classesToReachTarget} consecutive classes (0 bunks) to bounce back to ${targetThreshold.toInt()}% safety."
                            } else {
                                "Can safely miss ${outcome.safeBunkCapacity} classes before hitting ${targetThreshold.toInt()}% threshold."
                            },
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BunkSimulatorSection(statsList: List<com.example.domain.SubjectStats>) {
    // 0 = Single Subject Bunk, 1 = "What-If" Event Simulator (Hackathon / Fest Sandbox)
    var simulatorMode by remember { mutableIntStateOf(0) }

    // Single subject mode state
    var selectedSubjectId by remember { mutableStateOf(statsList.firstOrNull()?.subject?.id ?: 1L) }
    var simulatedMissedClasses by remember { mutableIntStateOf(2) }
    val currentStat = statsList.find { it.subject.id == selectedSubjectId } ?: statsList.firstOrNull()

    // "What-If" Event Simulator state
    var selectedEventPreset by remember { mutableStateOf("HACKATHON") } // HACKATHON, FEST, LONG_WEEKEND, CUSTOM
    var eventMissedDays by remember { mutableIntStateOf(3) } // Default 3 days for hackathon

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Surface(
            color = DarkSurfaceCard,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = if (simulatorMode == 0) "🧪 Interactive Bunk Simulator" else "🚀 \"What-If\" Event Simulator",
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (simulatorMode == 0)
                                "Test individual subject percentage impact before skipping."
                            else
                                "Sandbox mode: simulate missing an entire week/fest before packing bags.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Toggle
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { simulatorMode = 0 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (simulatorMode == 0) NeonCyan else DarkSurfaceElevated,
                            contentColor = if (simulatorMode == 0) Color(0xFF130E24) else TextSecondary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Single Subject", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { simulatorMode = 1 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (simulatorMode == 1) NeonPurple else DarkSurfaceElevated,
                            contentColor = if (simulatorMode == 1) Color.White else TextSecondary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("What-If Event Sandbox", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (simulatorMode == 0) {
                    // Single Subject Mode
                    Text("Select Subject:", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 130.dp)) {
                        items(statsList) { stat ->
                            val isSelected = stat.subject.id == selectedSubjectId
                            Surface(
                                color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) NeonCyan else DarkBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedSubjectId = stat.subject.id }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Text(stat.subject.name, color = TextPrimary, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    Text("${String.format("%.1f", stat.percentage)}%", color = stat.tier.displayColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (currentStat != null) {
                        val currentAttended = currentStat.subject.attendedClasses
                        val currentConducted = currentStat.subject.conductedClasses
                        val simulatedConducted = currentConducted + simulatedMissedClasses
                        val simulatedPct = if (simulatedConducted == 0) 100.0 else (currentAttended.toDouble() / simulatedConducted.toDouble()) * 100.0
                        val simTier = AttendanceCalculator.getTier(simulatedPct)

                        Text("Hypothetical Missed Classes: $simulatedMissedClasses", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                        Slider(
                            value = simulatedMissedClasses.toFloat(),
                            onValueChange = { simulatedMissedClasses = it.toInt() },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = if (simulatedPct < 75.0) NeonCrimson.copy(alpha = 0.15f) else NeonEmerald.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (simulatedPct < 75.0) NeonCrimson else NeonEmerald),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Simulated Result:", fontSize = 11.sp, color = TextSecondary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${String.format("%.1f", currentStat.percentage)}%  ➔  ${String.format("%.1f", simulatedPct)}%",
                                        fontWeight = FontWeight.Black,
                                        color = simTier.color,
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TierBadge(tier = simTier)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (simulatedPct >= 75.0) "✅ Safe! You will still be eligible for Endsems." else "🚨 DANGER: Dropping below 75% will trigger detention warning!",
                                    color = if (simulatedPct < 75.0) NeonCrimson else NeonMint,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )

                                // Predictive Recovery Trajectory inside Simulator
                                val classesToRecoverFromSim = AttendanceCalculator.calculateClassesToAttend(
                                    attended = currentAttended,
                                    conducted = simulatedConducted,
                                    target = 75.0
                                )
                                if (classesToRecoverFromSim > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "📈 Recovery Trajectory: Must attend $classesToRecoverFromSim consecutive classes to bounce back to 75.0%.",
                                        fontSize = 11.sp,
                                        color = NeonGold,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // "What-If" Event Simulator (Multi-Day Sandbox Mode)
                    Text("Select Event Scenario:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            Triple("HACKATHON", "💻 Hackathon", 3),
                            Triple("FEST", "🎪 College Fest", 5),
                            Triple("WEEKEND", "🏖️ Trip", 2),
                            Triple("CUSTOM", "⚙️ Custom", eventMissedDays)
                        ).forEach { (presetKey, label, days) ->
                            val isSelected = selectedEventPreset == presetKey
                            Surface(
                                color = if (isSelected) NeonPurple else DarkSurfaceElevated,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) NeonPurple else DarkBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedEventPreset = presetKey
                                        eventMissedDays = days
                                    }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (selectedEventPreset == "CUSTOM") {
                        Text("Custom Absence Duration: $eventMissedDays days", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Slider(
                            value = eventMissedDays.toFloat(),
                            onValueChange = { eventMissedDays = it.toInt() },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(thumbColor = NeonPurple, activeTrackColor = NeonPurple)
                        )
                    }

                    // Calculation across all subjects: each day misses ~1 class per subject on average (proportional)
                    val simulatedSubjectResults = statsList.map { stat ->
                        val missedClassesForSub = ((eventMissedDays * 3.0) / 5.0).toInt().coerceAtLeast(1)
                        val cond = stat.subject.conductedClasses + missedClassesForSub
                        val att = stat.subject.attendedClasses
                        val pct = if (cond == 0) 100.0 else (att.toDouble() / cond.toDouble()) * 100.0
                        Triple(stat, pct, missedClassesForSub)
                    }

                    val totalAttBefore = statsList.sumOf { it.subject.attendedClasses }
                    val totalCondBefore = statsList.sumOf { it.subject.conductedClasses }
                    val overallBefore = if (totalCondBefore == 0) 100.0 else (totalAttBefore.toDouble() / totalCondBefore.toDouble()) * 100.0

                    val totalMissedInEvent = simulatedSubjectResults.sumOf { it.third }
                    val totalCondAfter = totalCondBefore + totalMissedInEvent
                    val overallAfter = if (totalCondAfter == 0) 100.0 else (totalAttBefore.toDouble() / totalCondAfter.toDouble()) * 100.0

                    val subjectsBelowTarget = simulatedSubjectResults.count { it.second < 75.0 }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Event Impact Summary Card
                    Surface(
                        color = if (subjectsBelowTarget > 0) NeonCrimson.copy(alpha = 0.15f) else NeonEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (subjectsBelowTarget > 0) NeonCrimson else NeonEmerald),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Event Impact ($eventMissedDays Days Off):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Overall: ${String.format("%.1f", overallBefore)}% ➔ ${String.format("%.1f", overallAfter)}%",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (subjectsBelowTarget > 0) NeonCrimson else NeonEmerald
                                )
                                Surface(
                                    color = if (subjectsBelowTarget > 0) NeonCrimson.copy(alpha = 0.2f) else NeonEmerald.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (subjectsBelowTarget > 0) "🚨 $subjectsBelowTarget AT RISK" else "✅ SAFE TO GO",
                                        color = if (subjectsBelowTarget > 0) NeonCrimson else NeonEmerald,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (subjectsBelowTarget > 0)
                                    "Packing your bags will put $subjectsBelowTarget subject(s) below 75% cutoff! Plan extra recovery attendances."
                                else
                                    "Pack your bags! All subjects maintain >=75% exam compliance.",
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Per-Subject Event Impact:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                        items(simulatedSubjectResults) { (stat, simPct, missed) ->
                            val isDanger = simPct < 75.0
                            Surface(
                                color = DarkSurfaceElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isDanger) NeonCrimson.copy(alpha = 0.5f) else DarkBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Column {
                                        Text(stat.subject.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text("-$missed lectures missed", fontSize = 10.sp, color = TextMuted)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${String.format("%.1f", stat.percentage)}% ➔ ${String.format("%.1f", simPct)}%",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDanger) NeonCrimson else NeonEmerald
                                        )
                                        if (isDanger) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
