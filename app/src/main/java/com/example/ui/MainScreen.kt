package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
    AI_ASSISTANT("Attend AI", Icons.Default.AutoAwesome)
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
                color = DarkSurfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(64.dp)
                ) {
                    MainTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        val tabColor = if (tab == MainTab.AI_ASSISTANT) NeonPurple else NeonCyan

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.title,
                                    tint = if (isSelected) tabColor else TextMuted
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) tabColor else TextMuted
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = tabColor.copy(alpha = 0.18f)
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        },
        containerColor = DarkBackground,
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
