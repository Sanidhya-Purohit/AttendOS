package com.example.ui.screens.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.ChatConversation
import com.example.data.ai.ChatMessage
import com.example.ui.theme.*
import com.example.ui.viewmodel.AttendanceViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAssistantScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.chatMessages.collectAsState()
    val isLoading by viewModel.isChatLoading.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val subjectStats by viewModel.subjectStatsList.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val activeConversationId by viewModel.activeConversationId.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var showClearDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showMenuDropdown by remember { mutableStateOf(false) }

    // Modern Zero-Permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val original = BitmapFactory.decodeStream(stream)
                    if (original != null) {
                        val maxDim = 1024
                        val scale = if (original.width > maxDim || original.height > maxDim) {
                            val maxOriginal = maxOf(original.width, original.height)
                            maxDim.toFloat() / maxOriginal
                        } else 1.0f
                        selectedImageBitmap = if (scale < 1.0f) {
                            Bitmap.createScaledBitmap(
                                original,
                                (original.width * scale).toInt(),
                                (original.height * scale).toInt(),
                                true
                            )
                        } else {
                            original
                        }
                        Toast.makeText(context, "Attachment added to Gemini", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Could not load image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val studentName = profile?.studentName?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() && it != "DTU" } ?: "Student"

    // Gemini signature multi-color gradient
    val geminiGradient = remember {
        Brush.linearGradient(
            listOf(
                Color(0xFF00E5FF),
                Color(0xFF8B5CF6),
                Color(0xFFEC4899)
            )
        )
    }

    // Auto-scroll on new message
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Modal Drawer for Multiple Chat Conversations (Gemini App style)
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = AppTheme.colors.background,
                drawerContentColor = AppTheme.colors.textPrimary,
                modifier = Modifier.width(310.dp)
            ) {
                GeminiDrawerContent(
                    conversations = conversations,
                    activeId = activeConversationId,
                    onSelectConversation = { id ->
                        viewModel.selectConversation(id)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        viewModel.startNewChat()
                        coroutineScope.launch { drawerState.close() }
                    },
                    onDeleteConversation = { id ->
                        viewModel.deleteConversation(id)
                    },
                    onCloseDrawer = {
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                GeminiTopAppBar(
                    onOpenDrawer = {
                        coroutineScope.launch { drawerState.open() }
                    },
                    onNewChat = {
                        viewModel.startNewChat()
                        Toast.makeText(context, "New chat started", Toast.LENGTH_SHORT).show()
                    },
                    showMenu = showMenuDropdown,
                    onToggleMenu = { showMenuDropdown = it },
                    onRenameChat = {
                        val current = conversations.find { it.id == activeConversationId }
                        renameText = current?.title ?: ""
                        showRenameDialog = true
                    },
                    onClearChat = { showClearDialog = true }
                )
            },
            bottomBar = {
                GeminiInputBar(
                    inputText = inputText,
                    onInputTextChange = { inputText = it },
                    selectedImageBitmap = selectedImageBitmap,
                    onClearImage = { selectedImageBitmap = null },
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onSendMessage = {
                        val query = inputText.trim()
                        val img = selectedImageBitmap
                        if (query.isNotEmpty() || img != null) {
                            viewModel.sendMultimodalChatMessage(
                                query = query.ifBlank { "Please analyze this attached schedule or document." },
                                imageBitmap = img
                            )
                            inputText = ""
                            selectedImageBitmap = null
                        }
                    },
                    geminiGradient = geminiGradient
                )
            },
            containerColor = AppTheme.colors.background,
            modifier = modifier.testTag("screen_ai_assistant")
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (messages.isEmpty()) {
                    // Gemini App Empty Greeting View
                    val firstSubject = subjectStats.firstOrNull()
                    GeminiEmptyStateView(
                        studentName = studentName,
                        sampleSubjectName = firstSubject?.subject?.name,
                        sampleSubjectCode = firstSubject?.subject?.code,
                        geminiGradient = geminiGradient,
                        onSelectPrompt = { prompt ->
                            viewModel.sendChatMessage(prompt)
                        }
                    )
                } else {
                    // Chat messages list
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            if (message.sender == "user") {
                                GeminiUserMessageBubble(message = message)
                            } else {
                                GeminiAiResponseItem(
                                    message = message,
                                    onCopy = { text ->
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Gemini Response", text))
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    onFeedback = { isLiked ->
                                        viewModel.toggleMessageLike(message.id, isLiked)
                                    },
                                    onShare = { text ->
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Strategy"))
                                    },
                                    onRetry = {
                                        viewModel.retryLastChatMessage()
                                    }
                                )
                            }
                        }

                        if (isLoading) {
                            item {
                                GeminiThinkingIndicator(geminiGradient = geminiGradient)
                            }
                        }

                        // Bottom space so content doesn't hide behind floating pill
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }

    // Rename Chat Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Chat", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    placeholder = { Text("Chat title...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppTheme.colors.primary,
                        unfocusedBorderColor = AppTheme.colors.border,
                        focusedTextColor = AppTheme.colors.textPrimary,
                        unfocusedTextColor = AppTheme.colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameConversation(activeConversationId, renameText)
                        showRenameDialog = false
                    }
                ) {
                    Text("Save", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceCard
        )
    }

    // Clear Conversation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Conversation", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to clear all messages in this conversation?",
                    color = AppTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChatMessages()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceCard
        )
    }
}

/**
 * Gemini App Top App Bar with Drawer Hamburger, Sparkle Logo, and New Chat Action
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiTopAppBar(
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    showMenu: Boolean,
    onToggleMenu: (Boolean) -> Unit,
    onRenameChat: () -> Unit,
    onClearChat: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Gemini Sparkle Icon
                GeminiSparkleIcon(size = 22.dp)

                Text(
                    text = "Gemini",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        letterSpacing = (-0.5).sp
                    )
                )

                Surface(
                    color = AppTheme.colors.surfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AppTheme.colors.border)
                ) {
                    Text(
                        text = "3.5 Flash",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier.testTag("btn_gemini_drawer")
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Open Chat History",
                    tint = AppTheme.colors.textPrimary
                )
            }
        },
        actions = {
            // New chat icon button
            IconButton(
                onClick = onNewChat,
                modifier = Modifier.testTag("btn_gemini_new_chat")
            ) {
                Icon(
                    Icons.Outlined.EditNote,
                    contentDescription = "New Chat",
                    tint = AppTheme.colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 3-dots overflow menu
            Box {
                IconButton(onClick = { onToggleMenu(true) }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = AppTheme.colors.textSecondary
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { onToggleMenu(false) },
                    modifier = Modifier.background(AppTheme.colors.surfaceCard)
                ) {
                    DropdownMenuItem(
                        text = { Text("New chat", color = AppTheme.colors.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = AppTheme.colors.primary) },
                        onClick = {
                            onToggleMenu(false)
                            onNewChat()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename chat", color = AppTheme.colors.textPrimary) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = AppTheme.colors.textSecondary) },
                        onClick = {
                            onToggleMenu(false)
                            onRenameChat()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear conversation", color = Color(0xFFF43F5E)) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = Color(0xFFF43F5E)) },
                        onClick = {
                            onToggleMenu(false)
                            onClearChat()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AppTheme.colors.background,
            scrolledContainerColor = AppTheme.colors.background
        )
    )
}

/**
 * Gemini Side Drawer for Multiple Conversations
 */
@Composable
private fun GeminiDrawerContent(
    conversations: List<ChatConversation>,
    activeId: String,
    onSelectConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteConversation: (String) -> Unit,
    onCloseDrawer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GeminiSparkleIcon(size = 24.dp)
                Text(
                    text = "Gemini",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = AppTheme.colors.textPrimary
                    )
                )
            }

            IconButton(onClick = onCloseDrawer) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close Drawer",
                    tint = AppTheme.colors.textMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // "+ New chat" Button
        Surface(
            color = AppTheme.colors.surfaceCard,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, AppTheme.colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable { onNewChat() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = AppTheme.colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "New chat",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.textPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // "Recent" Section Title
        Text(
            text = "RECENT",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textMuted,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )

        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recent chats yet",
                    fontSize = 13.sp,
                    color = AppTheme.colors.textMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val isSelected = conv.id == activeId
                    Surface(
                        color = if (isSelected) AppTheme.colors.surfaceCard else Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                        border = if (isSelected) BorderStroke(1.dp, AppTheme.colors.primary.copy(alpha = 0.35f)) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectConversation(conv.id) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = if (isSelected) AppTheme.colors.primary else AppTheme.colors.textSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = conv.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isSelected) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { onDeleteConversation(conv.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "Delete Chat",
                                    tint = AppTheme.colors.textMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom Academic Context Info
        Surface(
            color = AppTheme.colors.surfaceCard.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = AppTheme.colors.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "AttendOS Intelligence • 75% Criteria",
                    fontSize = 11.sp,
                    color = AppTheme.colors.textMuted
                )
            }
        }
    }
}

/**
 * Gemini Welcome Screen when active conversation has no messages yet
 */
@Composable
private fun GeminiEmptyStateView(
    studentName: String,
    sampleSubjectName: String? = null,
    sampleSubjectCode: String? = null,
    geminiGradient: Brush,
    onSelectPrompt: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        // Gemini Signature Gradient Greeting
        Text(
            text = "Hello, $studentName",
            style = TextStyle(
                brush = geminiGradient,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "How can I help you today?",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Normal,
                color = AppTheme.colors.textSecondary,
                fontSize = 20.sp
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Dynamic Suggestion Cards grounded in student data
        val firstCardTitle = if (!sampleSubjectName.isNullOrBlank()) {
            "Can I skip $sampleSubjectName tomorrow?"
        } else {
            "Can I bunk classes today?"
        }
        val firstCardSub = if (!sampleSubjectName.isNullOrBlank()) {
            "Check ${sampleSubjectCode ?: sampleSubjectName} attendance & safety buffer"
        } else {
            "Check today's scheduled classes and risk"
        }
        val firstCardPrompt = if (!sampleSubjectName.isNullOrBlank()) {
            "Can I skip $sampleSubjectName tomorrow? Calculate my current attendance percentage and tell me exact impact."
        } else {
            "Can I bunk classes today? Check my schedule and calculate attendance risk."
        }

        val prompts = listOf(
            PromptCardData(
                icon = Icons.Outlined.FlashOn,
                title = firstCardTitle,
                subtitle = firstCardSub,
                prompt = firstCardPrompt
            ),
            PromptCardData(
                icon = Icons.Outlined.DateRange,
                title = "Summarize my week",
                subtitle = "Weekly lecture overview and safest leave day",
                prompt = "Summarize my week! What lectures and labs do I have, and which day has the least attendance risk?"
            ),
            PromptCardData(
                icon = Icons.Outlined.TrackChanges,
                title = "Midsem 75% cutoff targets",
                subtitle = "Calculate required consecutive attendances",
                prompt = "Calculate the exact number of classes I need to attend to guarantee >= 75% for Midsem and Endsem exams."
            ),
            PromptCardData(
                icon = Icons.Outlined.Biotech,
                title = "Lab absence penalty impact",
                subtitle = "Evaluate 2-hour practical weightage",
                prompt = "How severely will missing a 2-hour practical lab affect my percentage compared to regular lectures?"
            )
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            prompts.forEach { item ->
                Surface(
                    color = AppTheme.colors.surfaceCard,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, AppTheme.colors.border),
                    shadowElevation = if (AppTheme.colors.isDark) 0.dp else 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelectPrompt(item.prompt) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Surface(
                            color = AppTheme.colors.primary.copy(alpha = 0.12f),
                            shape = CircleShape,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    item.icon,
                                    contentDescription = null,
                                    tint = AppTheme.colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = AppTheme.colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = AppTheme.colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class PromptCardData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val prompt: String
)

/**
 * User Chat Bubble (Gemini style)
 */
@Composable
private fun GeminiUserMessageBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            color = AppTheme.colors.surfaceCard,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp),
            border = BorderStroke(1.dp, AppTheme.colors.border),
            shadowElevation = if (AppTheme.colors.isDark) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (message.imageBitmap != null) {
                    Image(
                        bitmap = message.imageBitmap.asImageBitmap(),
                        contentDescription = "Uploaded Timetable",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AppTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                )
            }
        }
    }
}

/**
 * Gemini AI Response Item (Unboxed, streaming markdown directly onto canvas like official Gemini app)
 */
@Composable
private fun GeminiAiResponseItem(
    message: ChatMessage,
    onCopy: (String) -> Unit,
    onFeedback: (Boolean) -> Unit,
    onShare: (String) -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Gemini Response Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            GeminiSparkleIcon(size = 18.dp)
            Text(
                text = "Gemini",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary,
                    fontSize = 13.sp
                )
            )
        }

        // Formatted Markdown Content
        GeminiMarkdownContent(text = message.text)

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Action Bar: Copy, Thumbs Up, Thumbs Down, Share, Regenerate
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { onCopy(message.text) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Copy Response",
                    tint = AppTheme.colors.textMuted,
                    modifier = Modifier.size(15.dp)
                )
            }

            IconButton(
                onClick = { onFeedback(true) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (message.isLiked == true) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = "Good response",
                    tint = if (message.isLiked == true) AppTheme.colors.primary else AppTheme.colors.textMuted,
                    modifier = Modifier.size(15.dp)
                )
            }

            IconButton(
                onClick = { onFeedback(false) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (message.isLiked == false) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = "Bad response",
                    tint = if (message.isLiked == false) Color(0xFFF43F5E) else AppTheme.colors.textMuted,
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
                    tint = AppTheme.colors.textMuted,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onRetry,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "Regenerate",
                    tint = AppTheme.colors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Gemini Clean Markdown Formatter
 */
@Composable
private fun GeminiMarkdownContent(text: String) {
    val paragraphs = remember(text) { text.split("\n\n") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        paragraphs.forEach { paragraph ->
            val trimmed = paragraph.trim()
            if (trimmed.startsWith("### ")) {
                Text(
                    text = trimmed.removePrefix("### ").trim(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                )
            } else if (trimmed.startsWith("## ")) {
                Text(
                    text = trimmed.removePrefix("## ").trim(),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                )
            } else if (trimmed.startsWith("# ")) {
                Text(
                    text = trimmed.removePrefix("# ").trim(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                )
            } else if (trimmed.lines().any { it.trim().startsWith("•") || it.trim().startsWith("- ") || it.trim().startsWith("* ") }) {
                // Bullet List
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    trimmed.lines().forEach { line ->
                        val lineTrimmed = line.trim()
                        if (lineTrimmed.isNotBlank()) {
                            val cleanLine = lineTrimmed.removePrefix("•").removePrefix("-").removePrefix("*").trim()
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "•",
                                    color = AppTheme.colors.primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = cleanLine,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = AppTheme.colors.textPrimary,
                                        fontSize = 14.5.sp,
                                        lineHeight = 22.sp
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = trimmed,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AppTheme.colors.textPrimary,
                        fontSize = 14.5.sp,
                        lineHeight = 22.sp
                    )
                )
            }
        }
    }
}

/**
 * Gemini Floating Bottom Capsule Dock
 */
@Composable
private fun GeminiInputBar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    selectedImageBitmap: Bitmap?,
    onClearImage: () -> Unit,
    onPickImage: () -> Unit,
    onSendMessage: () -> Unit,
    geminiGradient: Brush
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Thumbnail preview if an image was selected
        if (selectedImageBitmap != null) {
            Surface(
                color = AppTheme.colors.surfaceCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AppTheme.colors.border),
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .size(width = 110.dp, height = 70.dp)
            ) {
                Box {
                    Image(
                        bitmap = selectedImageBitmap.asImageBitmap(),
                        contentDescription = "Selected Document",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = onClearImage,
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.TopEnd)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Floating Pill Capsule
        Surface(
            color = AppTheme.colors.surfaceCard,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, AppTheme.colors.border),
            shadowElevation = if (AppTheme.colors.isDark) 4.dp else 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Attach image button
                IconButton(
                    onClick = onPickImage,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Outlined.AddPhotoAlternate,
                        contentDescription = "Attach Schedule",
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Text Input
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    textStyle = TextStyle(
                        color = AppTheme.colors.textPrimary,
                        fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(AppTheme.colors.primary),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inputText.isEmpty() && selectedImageBitmap == null) {
                                Text(
                                    text = "Ask Gemini...",
                                    style = TextStyle(
                                        color = AppTheme.colors.textMuted,
                                        fontSize = 15.sp
                                    )
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                // Send or Mic Button
                val hasContent = inputText.isNotBlank() || selectedImageBitmap != null
                if (hasContent) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(geminiGradient)
                            .clickable { onSendMessage() }
                            .testTag("btn_send_ai_chat")
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { /* Ready for speech or prompt */ },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Mic,
                            contentDescription = "Voice Input",
                            tint = AppTheme.colors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Gemini Thinking Indicator with pulsing gradient shimmer
 */
@Composable
private fun GeminiThinkingIndicator(geminiGradient: Brush) {
    val infiniteTransition = rememberInfiniteTransition(label = "gemini_thinking")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinking_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        GeminiSparkleIcon(size = 18.dp)
        Text(
            text = "Gemini is thinking...",
            style = TextStyle(
                brush = geminiGradient,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

/**
 * Custom Iconic Gemini 4-pointed Sparkle Icon
 */
@Composable
private fun GeminiSparkleIcon(size: androidx.compose.ui.unit.Dp) {
    Icon(
        imageVector = Icons.Default.AutoAwesome,
        contentDescription = "Gemini Sparkle",
        tint = AppTheme.colors.primary,
        modifier = Modifier.size(size)
    )
}
