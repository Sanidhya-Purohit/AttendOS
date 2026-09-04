package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.ai.AIAssistantScreen
import com.example.ui.screens.analyzer.AnalyzerScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.settings.NotificationsListDialog
import com.example.ui.screens.settings.SettingsDialog
import com.example.ui.screens.setup.OnboardingSetupScreen
import com.example.ui.screens.subjects.SubjectsScreen
import com.example.ui.screens.timetable.TimetableScreen
import com.example.ui.theme.*
import com.example.ui.viewmodel.AttendanceViewModel

enum class MainTab(val title: String, val icon: ImageVector) {
    TODAY("Today", Icons.Default.Dashboard),
    SUBJECTS("Subjects", Icons.Default.BarChart),
    SCHEDULE("Schedule", Icons.Default.CalendarMonth),
    STRATEGIST("Strategist", Icons.Default.Psychology),
    AI_ASSISTANT("AttendOS", Icons.Default.AutoAwesome)
}

@Composable
fun MainScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.userProfile.collectAsState()

    // Gate full app behind initial timetable upload & setup completion
    if (profile != null && !profile!!.isSetupComplete) {
        OnboardingSetupScreen(viewModel = viewModel, modifier = modifier)
        return
    }

    var currentTab by remember { mutableStateOf(MainTab.TODAY) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isNotificationsOpen by remember { mutableStateOf(false) }

    val notifications by viewModel.notifications.collectAsState()

    Scaffold(
        bottomBar = {
            Surface(
                color = AppTheme.colors.surfaceCard,
                shape = RoundedCornerShape(26.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                shadowElevation = if (AppTheme.colors.isDark) 12.dp else 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .navigationBarsPadding()
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(66.dp)
                ) {
                    MainTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        val tabColor = when {
                            tab == MainTab.AI_ASSISTANT -> AppTheme.colors.accentPurple
                            isSelected -> AppTheme.colors.primary
                            else -> AppTheme.colors.textMuted
                        }

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = if (isSelected) {
                                        Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(tabColor.copy(alpha = 0.16f))
                                    } else {
                                        Modifier.size(36.dp)
                                    }
                                ) {
                                    Icon(
                                        tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) tabColor else AppTheme.colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) tabColor else AppTheme.colors.textMuted
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        },
        containerColor = AppTheme.colors.background,
        modifier = modifier
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentTab) {
                MainTab.TODAY -> {
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToSubjects = { currentTab = MainTab.SUBJECTS },
                        onNavigateToTimetable = { currentTab = MainTab.SCHEDULE },
                        onNavigateToAI = { currentTab = MainTab.AI_ASSISTANT },
                        onOpenNotifications = { isNotificationsOpen = true },
                        onOpenSettings = { isSettingsOpen = true }
                    )
                }
                MainTab.SUBJECTS -> {
                    SubjectsScreen(viewModel = viewModel)
                }
                MainTab.SCHEDULE -> {
                    TimetableScreen(viewModel = viewModel)
                }
                MainTab.STRATEGIST -> {
                    AnalyzerScreen(viewModel = viewModel)
                }
                MainTab.AI_ASSISTANT -> {
                    AIAssistantScreen(viewModel = viewModel)
                }
            }
        }
    }

    if (isSettingsOpen) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { isSettingsOpen = false }
        )
    }

    if (isNotificationsOpen) {
        NotificationsListDialog(
            notifications = notifications,
            onDismissItem = { viewModel.dismissNotification(it) },
            onDismiss = { isNotificationsOpen = false }
        )
    }
}
