package com.example.ui.screens.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.ChatMessage
import com.example.ui.theme.*
import com.example.ui.viewmodel.AttendanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAssistantScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.chatMessages.collectAsState()
    val isLoading by viewModel.isChatLoading.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var showModelMenu by remember { mutableStateOf(false) }

    val studentName = profile?.studentName?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() && it != "DTU" } ?: "Student"

    // Gemini Signature Multi-Stop Gradients
    val geminiBrandGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF4285F4), // Google Blue
            Color(0xFF9B72CB), // Purple
            Color(0xFFD96570), // Pink-Coral
            Color(0xFF00E5FF)  // Cyan
        )
    )

    val geminiTextGreetingGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF4285F4),
            Color(0xFF9B72CB),
            Color(0xFFD96570)
        )
    )

    val geminiSparkleAvatarBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF4285F4),
            Color(0xFF8AB4F8),
            Color(0xFFC58AF9),
            Color(0xFFFF8BCB)
        )
    )

    val starterPromptCards = listOf(
        GeminiPromptCardData(
            icon = Icons.Outlined.Calculate,
            title = "Exam 75% Cutoff Plan",
            subtitle = "Calculate exact class targets for Midsem & Endsem",
            prompt = "Calculate my exact attendance and required classes to safely sit in Midsem and Endsem exams."
        ),
        GeminiPromptCardData(
            icon = Icons.Outlined.EventAvailable,
            title = "Safe Bunk Margin",
            subtitle = "Which classes can I safely skip this week?",
            prompt = "Analyze my current attendance and tell me how many safe bunks I have across all subjects."
        ),
        GeminiPromptCardData(
            icon = Icons.Outlined.Science,
            title = "Lab Attendance Risk",
            subtitle = "Evaluate impact of missing 2-hour practical labs",
            prompt = "How severely will missing a 2-hour lab affect my attendance percentage and exam eligibility?"
        ),
        GeminiPromptCardData(
            icon = Icons.Outlined.Shield,
            title = "Medical & Buffer Protection",
            subtitle = "Simulate emergency leave & duty leave margin",
            prompt = "How does my emergency leave buffer protect me if I get sick or participate in college societies?"
        )
    )

    val quickChipSuggestions = listOf(
        "📊 Can I bunk today?",
        "🎯 Midsem cutoff target",
        "🗓️ Best day for leave",
        "🛡️ Emergency buffer check",
        "💡 Attendance recovery tip"
    )

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = GeminiDarkBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Left: Gemini Sparkle Icon & Brand Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showModelMenu = true }
                    ) {
                        // 4-point Gemini Star Icon
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(geminiBrandGradient)
                                .padding(1.5.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(GeminiDarkBg)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Gemini",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Gemini",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Model selector pill
                                Surface(
                                    color = GeminiDarkSurface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GeminiBorder)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "2.5 Flash",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = GeminiBlue
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = GeminiBlue,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Academic & Attendance Strategist",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    // Right: New Chat / Clear Action Button
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                viewModel.clearChatMessages()
                                Toast.makeText(context, "Started fresh chat session", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GeminiDarkSurface)
                                .border(1.dp, GeminiBorder, CircleShape)
                        ) {
                            Icon(
                                Icons.Outlined.EditNote,
                                contentDescription = "New Chat",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GeminiDarkBg)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .padding(bottom = 76.dp) // Clearance for bottom navigation
            ) {
                // Quick Suggestion Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    items(quickChipSuggestions) { suggestionText ->
                        Surface(
                            color = GeminiChipBg,
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GeminiBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    viewModel.sendChatMessage(suggestionText)
                                }
                        ) {
                            Text(
                                text = suggestionText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // Signature Gemini Prompt Pill Composer
                Surface(
                    color = GeminiDarkInput,
                    shape = RoundedCornerShape(28.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GeminiBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(28.dp), spotColor = Color(0x22000000))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        // Gemini Sparkle / Attachment Prefix
                        IconButton(
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "Timetable context is continuously synced with Gemini AI.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.AddCircleOutline,
                                contentDescription = "Add context",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Text Field Composer
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    "Ask AttendOS Gemini...",
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                                errorBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            maxLines = 4,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_ai_chat")
                        )

                        val hasText = inputText.isNotBlank()

                        if (hasText) {
                            // Send Button with Gemini Gradient
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(geminiBrandGradient)
                                    .clickable {
                                        viewModel.sendChatMessage(inputText)
                                        inputText = ""
                                    }
                                    .testTag("btn_send_ai_chat")
                            ) {
                                Icon(
                                    Icons.Default.ArrowUpward,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            // Mic / Voice Prompt Button (Gemini Style)
                            IconButton(
                                onClick = {
                                    Toast.makeText(
                                        context,
                                        "Voice input: Tap to type or select quick suggestions.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Mic,
                                    contentDescription = "Voice Input",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = GeminiDarkBg,
        modifier = modifier.testTag("screen_ai_assistant")
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Welcome Section if chat history is empty
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 16.dp)
                    ) {
                        Text(
                            text = "Hello, $studentName",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                brush = geminiTextGreetingGradient,
                                fontSize = 32.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "How can I help strategize your attendance today?",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = TextSecondary,
                                fontSize = 15.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // 4 Interactive Gemini Prompt Cards
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            starterPromptCards.forEach { card ->
                                Surface(
                                    color = GeminiDarkSurface,
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GeminiBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            viewModel.sendChatMessage(card.prompt)
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        Surface(
                                            color = GeminiBubbleUser,
                                            shape = CircleShape,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    card.icon,
                                                    contentDescription = null,
                                                    tint = GeminiBlue,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = card.title,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = card.subtitle,
                                                fontSize = 12.sp,
                                                color = TextMuted
                                            )
                                        }

                                        Icon(
                                            Icons.Default.ArrowOutward,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Message Thread
            items(messages, key = { it.id }) { msg ->
                GeminiChatBubble(
                    message = msg,
                    sparkleBrush = geminiSparkleAvatarBrush,
                    onCopy = { text ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Gemini Attendance Advice", text)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied response to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onShare = { text ->
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, text)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share Gemini Advice")
                        context.startActivity(shareIntent)
                    },
                    onRetry = {
                        viewModel.retryLastChatMessage()
                    }
                )
            }

            if (isLoading) {
                item {
                    GeminiThinkingPulse(sparkleBrush = geminiSparkleAvatarBrush)
                }
            }
        }
    }
}

private data class GeminiPromptCardData(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val prompt: String
)

@Composable
fun GeminiChatBubble(
    message: ChatMessage,
    sparkleBrush: Brush,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onRetry: () -> Unit
) {
    val isUser = message.sender == "user"
    var feedbackState by remember { mutableStateOf<String?>(null) } // "good" or "bad"

    if (isUser) {
        // User Bubble: Rounded Gemini Pill on Right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = GeminiBubbleUser,
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 4.dp
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, GeminiBorder),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    } else {
        // Gemini Response: Fluent Left Layout with Sparkle Avatar & Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            // Sparkle 4-point Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(sparkleBrush)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Formatted Response Text
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 23.sp
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Gemini Action Bar: Copy, Thumbs Up, Thumbs Down, Share, Retry
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { onCopy(message.text) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "Copy response",
                            tint = TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            feedbackState = if (feedbackState == "good") null else "good"
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (feedbackState == "good") Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Helpful",
                            tint = if (feedbackState == "good") GeminiBlue else TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            feedbackState = if (feedbackState == "bad") null else "bad"
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (feedbackState == "bad") Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Not helpful",
                            tint = if (feedbackState == "bad") NeonCrimson else TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = { onShare(message.text) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Regenerate",
                            tint = TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GeminiThinkingPulse(sparkleBrush: Brush) {
    val infiniteTransition = rememberInfiniteTransition(label = "gemini_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(sparkleBrush)
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(GeminiBlue.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Gemini is thinking...",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = GeminiBlue.copy(alpha = alpha),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}
