package com.example.clawpaw.presentation

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.clawpaw.ui.theme.clawpaw_primary
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.clawpaw.data.storage.AppPrefs
import com.example.clawpaw.data.storage.MainPrefs
import com.example.clawpaw.data.storage.ChatFontSize
import com.example.clawpaw.data.storage.ChatRefreshInterval
import com.example.clawpaw.data.storage.ReconnectCheckInterval
import com.example.clawpaw.gateway.GatewayConnection
import com.example.clawpaw.service.NotificationListener
import com.example.clawpaw.util.CommandLogEntry
import com.example.clawpaw.util.Logger
import com.example.clawpaw.service.ClawPawAccessibilityService
import com.example.clawpaw.ui.theme.ClawPawTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.clawpaw.data.storage.OnboardingPrefs.init(this)
        // 仅首次打开显示引导；已完成或从引导内「开始使用」进入则直接进主页
        val fromOnboarding = intent.getBooleanExtra("from_onboarding", false)
        val completed = com.example.clawpaw.data.storage.OnboardingPrefs.isCompleted()
        if (!completed && !fromOnboarding) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        setContent {
            ClawPawTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .then(Modifier.background(clawpaw_primary))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StatusChip(ready: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    ) {
        Text(
            text = if (ready) "就绪" else "未就绪",
            style = MaterialTheme.typography.labelSmall,
            color = if (ready) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SmallCircleCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(1.5.dp, color, CircleShape)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    MainPrefs.init(context)
    var selectedTab by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) viewModel.refreshInitChecks() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val connectionState by viewModel.gatewayConnectionState.collectAsStateWithLifecycle()
    val operatorState by viewModel.operatorConnectionState.collectAsStateWithLifecycle(initialValue = GatewayConnection.ConnectionState.Disconnected)
    var showAuthGuide by remember { mutableStateOf(false) }
    var showPairingDialog by remember { mutableStateOf(false) }

    LaunchedEffect(connectionState) {
        if (connectionState is GatewayConnection.ConnectionState.Connected && !MainPrefs.getAuthGuideShownAfterConnect()) {
            showAuthGuide = true
        }
    }
    LaunchedEffect(operatorState) {
        when (operatorState) {
            is GatewayConnection.ConnectionState.Connected -> showPairingDialog = false
            is GatewayConnection.ConnectionState.Error -> {
                if ((operatorState as GatewayConnection.ConnectionState.Error).message.contains("配对")) showPairingDialog = true
            }
            else -> { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ClawPaw", style = MaterialTheme.typography.titleMedium) },
                modifier = Modifier.heightIn(min = 32.dp, max = 40.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.heightIn(max = 56.dp)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    label = { Text("连接状态", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    label = { Text("对话", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    label = { Text("设置", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues).fillMaxSize()) {
            when (selectedTab) {
                0 -> ConnectionTabContent(viewModel = viewModel)
                1 -> ChatTabContent(viewModel = viewModel)
                2 -> SettingsTabContent(viewModel = viewModel)
            }
        }
    }

    if (showAuthGuide) {
        AuthGuideDialog(
            onDismiss = {
                showAuthGuide = false
                viewModel.markAuthGuideShownAfterConnect()
            },
            viewModel = viewModel
        )
    }
    if (showPairingDialog) {
        PairingRequiredDialog(
            onDismiss = {
                viewModel.disconnectGateway()
                showPairingDialog = false
            },
            context = context
        )
    }
}

/** 会话 key 的展示名：displayName 或 key 末段 */
private fun friendlySessionName(key: String, displayName: String?): String =
    displayName?.takeIf { it.isNotBlank() } ?: key.substringAfterLast(":").removePrefix("g-").ifBlank { key }

/** 近 10 分钟：直接显示在「近10分钟」分组 */
private const val RECENT_SESSION_MS = 10 * 60 * 1000L
/** 10m～120m 需展开；超过 120 分钟的不显示 */
private const val SESSION_CUTOFF_MS = 120 * 60 * 1000L
private const val MAX_CHAT_ATTACHMENTS = 8
/** 单个附件最大约 3MB（过大会导致发送超时或 Gateway 拒绝） */
private const val MAX_ATTACHMENT_BYTES = 3 * 1024 * 1024

private data class PendingChatAttachment(val id: String, val fileName: String, val mimeType: String, val base64: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatTabContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val operatorConnected = viewModel.operatorConnectionState.collectAsStateWithLifecycle(initialValue = GatewayConnection.ConnectionState.Disconnected).value is GatewayConnection.ConnectionState.Connected
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle(initialValue = emptyList())
    val chatBusy by viewModel.chatBusy.collectAsStateWithLifecycle(initialValue = false)
    val chatError by viewModel.chatError.collectAsStateWithLifecycle(initialValue = null)
    val sessionKey by viewModel.chatSessionKey.collectAsStateWithLifecycle(initialValue = "main")
    val sessions by viewModel.chatSessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val streamingText by viewModel.chatStreamingAssistantText.collectAsStateWithLifecycle(initialValue = null)
    val showRaw by viewModel.showRawChatMessage.collectAsStateWithLifecycle(initialValue = false)
    val roleFilter by viewModel.chatRoleFilter.collectAsStateWithLifecycle(initialValue = setOf("user", "assistant"))
    val inputText by viewModel.chatInputDraft.collectAsStateWithLifecycle(initialValue = "")
    val chatAttachments = remember { mutableStateListOf<PendingChatAttachment>() }
    var attachmentsLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val addUrisToAttachments: (List<Uri>?) -> Unit = label@ { uris ->
        if (uris.isNullOrEmpty()) return@label
        scope.launch {
            attachmentsLoading = true
            val (list, skipped) = withContext(Dispatchers.IO) {
                var skipCount = 0
                val out = uris.take(MAX_CHAT_ATTACHMENTS - chatAttachments.size).mapNotNull { uri ->
                    try {
                        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        val name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "file"
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapNotNull null
                        if (bytes.isEmpty()) return@mapNotNull null
                        if (bytes.size > MAX_ATTACHMENT_BYTES) { skipCount++; return@mapNotNull null }
                        PendingChatAttachment(UUID.randomUUID().toString(), name, mime, Base64.encodeToString(bytes, Base64.NO_WRAP))
                    } catch (_: Throwable) { null }
                }
                Pair(out, skipCount)
            }
            chatAttachments.addAll(list)
            attachmentsLoading = false
            if (skipped > 0) Toast.makeText(context, "有 $skipped 个文件超过 3MB 已跳过", Toast.LENGTH_SHORT).show()
        }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { addUrisToAttachments(it) }
    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { addUrisToAttachments(it) }
    var attachMenuExpanded by remember { mutableStateOf(false) }
    var sessionMenuExpanded by remember { mutableStateOf(false) }
    var sessionExpandMore by remember { mutableStateOf(false) }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    var timestampVisibleIndex by remember { mutableStateOf<Int?>(null) }
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val listState = rememberLazyListState()
    val now = System.currentTimeMillis()
    val sessionOptions = if (sessions.isNotEmpty()) sessions else if (operatorConnected) listOf(ChatSessionEntry("main", "主会话", null)) else emptyList()
    val validSessions = sessionOptions.filter { it.updatedAtMs == null || it.updatedAtMs >= now - SESSION_CUTOFF_MS }
    val recentSessions = validSessions.filter { it.updatedAtMs != null && it.updatedAtMs >= now - RECENT_SESSION_MS }
    val otherSessions = validSessions.filter { it.updatedAtMs == null || it.updatedAtMs < now - RECENT_SESSION_MS }
    val currentSessionLabel = validSessions.firstOrNull { it.key == sessionKey }?.let { friendlySessionName(it.key, it.displayName) } ?: sessionOptions.firstOrNull { it.key == sessionKey }?.let { friendlySessionName(it.key, it.displayName) } ?: friendlySessionName(sessionKey, null)
    val displayedMessages = remember(messages, roleFilter) { messages.filter { it.role in roleFilter } }

    AppPrefs.init(context)
    val chatFontSizeSp = AppPrefs.getChatFontSize().sp
    var chatInputFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = chatInputFocused) { focusManager.clearFocus() }
    LaunchedEffect(Unit) {
        viewModel.loadChatHistory()
        viewModel.loadChatSessions(200)
    }
    LaunchedEffect(operatorConnected) {
        if (!operatorConnected) return@LaunchedEffect
        while (true) {
            delay(AppPrefs.getChatRefreshInterval().delayMs)
            viewModel.loadChatHistory(silent = true)
            viewModel.loadChatSessions(200)
        }
    }
    LaunchedEffect(displayedMessages.size, streamingText) {
        val last = displayedMessages.size + if (streamingText != null) 1 else 0
        if (last > 0) listState.scrollToItem(last - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!chatInputFocused) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (operatorConnected && (validSessions.isNotEmpty() || sessionOptions.isNotEmpty())) {
                    Box {
                        Row(
                            modifier = Modifier
                                .clickable { sessionMenuExpanded = true }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "与 $currentSessionLabel 对话",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Default.ExpandMore, contentDescription = "选择会话", modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = sessionMenuExpanded,
                            onDismissRequest = { sessionMenuExpanded = false }
                        ) {
                            if (recentSessions.isNotEmpty()) {
                                Text("近10分钟", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                recentSessions.forEach { entry ->
                                    DropdownMenuItem(
                                        text = { Text(friendlySessionName(entry.key, entry.displayName), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        onClick = {
                                            viewModel.switchChatSession(entry.key)
                                            sessionMenuExpanded = false
                                        }
                                    )
                                }
                            }
                            if (otherSessions.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { sessionExpandMore = !sessionExpandMore },
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        if (sessionExpandMore) "收起" else "展开更多 (${otherSessions.size})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                                if (sessionExpandMore) {
                                    Text("10分钟～2小时", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                    otherSessions.forEach { entry ->
                                        DropdownMenuItem(
                                            text = { Text(friendlySessionName(entry.key, entry.displayName), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            onClick = {
                                                viewModel.switchChatSession(entry.key)
                                                sessionMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("与 OpenClaw 对话", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    FilterChip(
                        selected = false,
                        onClick = { roleMenuExpanded = true },
                        label = { Text("角色", style = MaterialTheme.typography.labelSmall) }
                    )
                    DropdownMenu(
                        expanded = roleMenuExpanded,
                        onDismissRequest = { roleMenuExpanded = false }
                    ) {
                        val showChat = "user" in roleFilter && "assistant" in roleFilter
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = showChat, onCheckedChange = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("只显示聊天")
                                }
                            },
                            onClick = {
                                viewModel.setChatRoleFilter(
                                    if (showChat) roleFilter - "user" - "assistant"
                                    else roleFilter + "user" + "assistant"
                                )
                            }
                        )
                        val showTool = "toolResult" in roleFilter || "toolCall" in roleFilter
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = showTool, onCheckedChange = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("显示工具调用")
                                }
                            },
                            onClick = {
                                viewModel.setChatRoleFilter(
                                    if (showTool) roleFilter - "toolResult" - "toolCall"
                                    else roleFilter + "toolResult" + "toolCall"
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { viewModel.toggleShowRawChatMessage() }) {
                    Text(if (showRaw) "可读" else "原文", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        }
        if (!operatorConnected) {
            Text(
                "请先在「连接状态」中连接 Gateway（将同时建立 Node + Operator 连接）后再发送消息。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        if (chatBusy) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Text(
                    "获取中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (chatError != null) {
            Text(
                chatError!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            items(displayedMessages.size, key = { i -> displayedMessages.getOrNull(i)?.id ?: "msg_$i" }) { index ->
                val msg = displayedMessages[index]
                val isUser = msg.role == "user"
                val showTool = "toolResult" in roleFilter || "toolCall" in roleFilter
                var displayContent = when {
                    showRaw && !msg.rawContent.isNullOrBlank() -> msg.rawContent
                    showTool -> msg.content
                    else -> (msg.contentChatOnly ?: msg.content)
                }
                val showTs = timestampVisibleIndex == index && msg.timestampMs != null
                val toCopy = if (displayContent.isNotEmpty()) displayContent else msg.content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                focusManager.clearFocus()
                                if (msg.timestampMs != null) timestampVisibleIndex = if (timestampVisibleIndex == index) null else index
                            },
                            onLongClick = {
                                if (toCopy.isNotEmpty()) {
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText(null, toCopy))
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    if (showTs) {
                        Text(
                            timeFormat.format(Date(msg.timestampMs!!)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    if (displayContent.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isUser) clawpaw_primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    displayContent,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = chatFontSizeSp.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (streamingText != null) {
                item(key = "streaming") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    streamingText!!,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = chatFontSizeSp.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        if (attachmentsLoading) {
            Text(
                "附件加载中…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (chatAttachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                chatAttachments.forEach { att ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                att.fileName.take(12).let { if (it.length < att.fileName.length) "$it…" else it },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { chatAttachments.removeAll { it.id == att.id } },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Text("×", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(
                    onClick = { if (chatAttachments.size < MAX_CHAT_ATTACHMENTS) attachMenuExpanded = true },
                    enabled = !chatBusy && operatorConnected && chatAttachments.size < MAX_CHAT_ATTACHMENTS,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加附件", modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = attachMenuExpanded,
                    onDismissRequest = { attachMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("上传图片") },
                        onClick = {
                            pickImageLauncher.launch("image/*")
                            attachMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("上传附件") },
                        onClick = {
                            pickFileLauncher.launch("*/*")
                            attachMenuExpanded = false
                        }
                    )
                }
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = { viewModel.setChatInputDraft(it) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 84.dp)
                    .onFocusChanged { chatInputFocused = it.isFocused },
                placeholder = { Text("输入消息…", style = MaterialTheme.typography.bodySmall) },
                enabled = true,
                singleLine = false,
                minLines = 1,
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    viewModel.sendChatMessage(
                        inputText,
                        chatAttachments.map { GatewayConnection.ChatAttachment(if (it.mimeType.startsWith("image/")) "image" else "file", it.mimeType, it.fileName, it.base64) }
                    )
                    viewModel.setChatInputDraft("")
                    chatAttachments.clear()
                },
                enabled = (inputText.isNotBlank() || chatAttachments.isNotEmpty()) && !attachmentsLoading && !chatBusy && operatorConnected,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "发送", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ConnectionTabContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    AppPrefs.init(context)
    val connectionState by viewModel.gatewayConnectionState.collectAsStateWithLifecycle()
    val nodeHandshakeDone by viewModel.nodeHandshakeDone.collectAsStateWithLifecycle()
    val isSshConnected by viewModel.isSshConnected.collectAsStateWithLifecycle()
    val httpServiceEnabled by viewModel.httpServiceEnabled.collectAsStateWithLifecycle()
    val httpPort by viewModel.httpPort.collectAsStateWithLifecycle()
    val localIpAddress by viewModel.localIpAddress.collectAsStateWithLifecycle()
    val tailscaleIpAddress by viewModel.tailscaleIpAddress.collectAsStateWithLifecycle()
    val logEntries by viewModel.commandLogEntries.collectAsStateWithLifecycle(initialValue = emptyList())
    var autoReconnectSsh by remember { mutableStateOf(AppPrefs.getAutoReconnectSsh()) }
    var autoReconnectNode by remember { mutableStateOf(AppPrefs.getAutoReconnectNode()) }

    LaunchedEffect(Unit) { viewModel.refreshInitChecks() }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000L)
            viewModel.refreshInitChecks()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionTitle(title = "连接状态")
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("SSH 隧道", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    StatusChip(ready = isSshConnected)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    SmallCircleCheckbox(checked = autoReconnectSsh, onCheckedChange = { autoReconnectSsh = it; AppPrefs.setAutoReconnectSsh(it) })
                    Spacer(Modifier.width(6.dp))
                    Text("自动重连", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${if (isSshConnected) "已连接" else "未连接"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isSshConnected) {
                        OutlinedButton(onClick = { viewModel.disconnectSsh() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("断开") }
                    } else {
                        Button(onClick = { viewModel.connectSsh() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("连接") }
                    }
                    OutlinedButton(onClick = { context.startActivity(Intent(context, SshTunnelActivity::class.java)) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("SSH 设置") }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Node (Gateway)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    StatusChip(ready = connectionState is GatewayConnection.ConnectionState.Connected && nodeHandshakeDone)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    SmallCircleCheckbox(checked = autoReconnectNode, onCheckedChange = { autoReconnectNode = it; AppPrefs.setAutoReconnectNode(it) })
                    Spacer(Modifier.width(6.dp))
                    Text("自动重连", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    when {
                        connectionState is GatewayConnection.ConnectionState.Connected && nodeHandshakeDone -> "已连接（Gateway 已注册）"
                        connectionState is GatewayConnection.ConnectionState.Connected && !nodeHandshakeDone -> "已连接（等待 Gateway 注册…）"
                        connectionState is GatewayConnection.ConnectionState.Connecting -> "连接中…"
                        connectionState is GatewayConnection.ConnectionState.Error -> "连接失败"
                        else -> "未连接"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (connectionState is GatewayConnection.ConnectionState.Connected) {
                        OutlinedButton(onClick = { viewModel.disconnectGateway() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("断开") }
                    } else {
                        Button(onClick = { viewModel.connectGateway() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("连接") }
                    }
                    OutlinedButton(onClick = { context.startActivity(Intent(context, GatewaySettingsActivity::class.java)) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("Node 设置") }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("HTTP 服务", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("端口 ${httpPort ?: 8765}，Tailscale 可访问", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = httpServiceEnabled, onCheckedChange = { viewModel.setHttpServiceEnabled(it) })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("本机 IP: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (localIpAddress.isNotEmpty()) {
                Text(
                    localIpAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText(null, localIpAddress))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tailscaleIpAddress.isNotEmpty()) {
                Text("Tailscale: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    tailscaleIpAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText(null, tailscaleIpAddress))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("建议省电配置使用 无限制，即可在后台持续使用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(title = "执行日志")
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 400.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text("ADB / WebSocket / HTTP 命令记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                if (logEntries.isEmpty()) {
                    Text("暂无记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(logEntries.reversed()) { entry ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("[${entry.source}]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                                Text(entry.action, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth(), softWrap = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTabContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsStateWithLifecycle()
    val isOurImeDefault by viewModel.isOurImeDefault.collectAsStateWithLifecycle()
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showImeTipDialog by remember { mutableStateOf(false) }
    var permissionRefreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.refreshInitChecks() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshInitChecks() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val calendarGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    val storageOrMediaImagesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    } else {
        @Suppress("DEPRECATION")
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
    val activityRecognitionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    } else true
    val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else true
    val smsReadGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    val smsSendGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    val smsGranted = smsReadGranted && smsSendGranted
    val phoneGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    val notificationListenerEnabled by viewModel.notificationListenerEnabled.collectAsStateWithLifecycle()
    val allPermissionsGranted = locationGranted && cameraGranted && notificationGranted && isAccessibilityEnabled

    LaunchedEffect(allPermissionsGranted, notificationListenerEnabled) {
        if (allPermissionsGranted && notificationListenerEnabled) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(500L)
            viewModel.refreshInitChecks()
            permissionRefreshTick++
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.openAppSettings()
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.openAppSettings()
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.openAppSettings()
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.openAppSettings()
    }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.openAppSettings()
    }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.openAppSettings()
    }
    val activityRecognitionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.openAppSettings()
    }
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.openAppSettings()
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.any { !it }) viewModel.openAppSettings()
    }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.openAppSettings()
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        AppPrefs.init(context)
        SectionTitle(title = "行为与显示")
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(20.dp)) {
                var persistentNotification by remember { mutableStateOf(AppPrefs.getPersistentNotification()) }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("常驻通知", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Gateway 连接时状态栏通知（不影响推送通知）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = persistentNotification, onCheckedChange = { persistentNotification = it; AppPrefs.setPersistentNotification(it) })
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                var reconnectInterval by remember { mutableStateOf(AppPrefs.getReconnectCheckInterval()) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("重连检查间隔", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ReconnectCheckInterval.entries.forEach { opt ->
                            val sec = (opt.delayMs / 1000).toInt()
                            FilterChip(selected = reconnectInterval == opt, onClick = { reconnectInterval = opt; AppPrefs.setReconnectCheckInterval(opt) }, label = { Text("${sec}秒", style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                var chatRefreshInterval by remember { mutableStateOf(AppPrefs.getChatRefreshInterval()) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("对话拉取间隔", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        ChatRefreshInterval.entries.forEach { opt ->
                            val sec = (opt.delayMs / 1000).toInt()
                            FilterChip(selected = chatRefreshInterval == opt, onClick = { chatRefreshInterval = opt; AppPrefs.setChatRefreshInterval(opt) }, label = { Text("${sec}秒", style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                var chatFontSize by remember { mutableStateOf(AppPrefs.getChatFontSize()) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("对话字号", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChatFontSize.entries.forEach { opt ->
                            val label = when (opt) {
                                ChatFontSize.LARGE -> "大"
                                ChatFontSize.MEDIUM -> "中"
                                ChatFontSize.SMALL -> "小"
                            }
                            FilterChip(selected = chatFontSize == opt, onClick = { chatFontSize = opt; AppPrefs.setChatFontSize(opt) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(title = "权限类设置")
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(20.dp)) {
                key(permissionRefreshTick) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("无障碍服务", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.clickable { viewModel.openAccessibilitySettings() }.minimumInteractiveComponentSize()) {
                            StatusChip(ready = isAccessibilityEnabled)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("ClawPaw 输入法", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        IconButton(
                            onClick = { showImeTipDialog = true },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "说明", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.clickable { viewModel.openInputMethodSettings() }.minimumInteractiveComponentSize()) {
                            StatusChip(ready = isOurImeDefault)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    PermissionSettingRow(context = context, permission = Manifest.permission.ACCESS_FINE_LOCATION, label = "定位", desc = "获取信息", onRequestPermission = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) })
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    PermissionSettingRow(context = context, permission = Manifest.permission.CAMERA, label = "相机", desc = "拍照/录像", onRequestPermission = { cameraLauncher.launch(Manifest.permission.CAMERA) })
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        PermissionSettingRow(context = context, permission = Manifest.permission.POST_NOTIFICATIONS, label = "通知", desc = "调试与提醒", onRequestPermission = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("通知监听", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("获取通知列表（需在系统设置中开启）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (notificationListenerEnabled) {
                            Text("已开启", style = MaterialTheme.typography.labelSmall, color = clawpaw_primary)
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.openNotificationListenerSettings() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(36.dp)
                            ) { Text("去授权") }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    PermissionSettingRow(context = context, permission = Manifest.permission.READ_CALENDAR, label = "日历", desc = "读取日历事件", onRequestPermission = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) })
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    PermissionSettingRow(
                        context = context,
                        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE,
                        label = "照片/存储",
                        desc = "读取照片与文件",
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) storageLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                            else storageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        PermissionSettingRow(context = context, permission = Manifest.permission.ACTIVITY_RECOGNITION, label = "运动/步数", desc = "传感器步数", onRequestPermission = { activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) })
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        PermissionSettingRow(context = context, permission = Manifest.permission.BLUETOOTH_CONNECT, label = "蓝牙", desc = "附近设备列表", onRequestPermission = { bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) })
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    PermissionSettingRow(context = context, permission = Manifest.permission.READ_CONTACTS, label = "联系人", desc = "读取联系人", onRequestPermission = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) })
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("短信", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("读取与发送短信", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (smsGranted) Text("已授权", style = MaterialTheme.typography.labelSmall, color = clawpaw_primary)
                        else OutlinedButton(onClick = { smsLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(36.dp)) { Text("去授权") }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    PermissionSettingRow(context = context, permission = Manifest.permission.CALL_PHONE, label = "电话", desc = "拨打电话", onRequestPermission = { phoneLauncher.launch(Manifest.permission.CALL_PHONE) })
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(onClick = { viewModel.openAppSettings() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Text("应用详情与权限")
        }
    }

    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("不开启无障碍会影响哪些功能？", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "未开启时，以下功能均不可用：\n\n• OpenClaw 远程控制（点击、滑动、长按、输入文字、获取界面布局等）\n• 通过 ADB / HTTP 执行的界面操作\n\n开启后本机可作为 OpenClaw 节点被电脑端控制。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showAccessibilityDialog = false; viewModel.openAccessibilitySettings() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDialog = false }) {
                    Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
    if (showImeTipDialog) {
        AlertDialog(
            onDismissRequest = { showImeTipDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("ClawPaw 输入法", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "在无障碍输入失败的情况下，可将 ClawPaw 设为默认输入法，作为备用输入方式。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showImeTipDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun PermissionSettingRow(
    context: android.content.Context,
    permission: String,
    label: String,
    desc: String,
    onRequestPermission: () -> Unit
) {
    val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) {
            Text("已授权", style = MaterialTheme.typography.labelSmall, color = clawpaw_primary)
        } else {
            OutlinedButton(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(36.dp)
            ) { Text("去授权") }
        }
    }
}

@Composable
private fun AuthGuideDialog(onDismiss: () -> Unit, viewModel: MainViewModel) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("系统授权", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("为完整使用节点能力，建议授予以下权限：", style = MaterialTheme.typography.bodyMedium)
                Text("• 获取信息：定位、WiFi 状态", style = MaterialTheme.typography.bodySmall)
                Text("• 相机：拍照、录像", style = MaterialTheme.typography.bodySmall)
                Text("• 通知：调试与提醒", style = MaterialTheme.typography.bodySmall)
                Text("• 无障碍：远程操作手机（若需）", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.openAppSettings(); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("去设置") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun PairingRequiredDialog(onDismiss: () -> Unit, context: Context) {
    val deviceId = remember { GatewayConnection.getStoredDeviceId(context.applicationContext) }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    fun copyAndToast(text: String) {
        clipboard?.setPrimaryClip(ClipData.newPlainText(null, text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("需要完成配对", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { copyAndToast("openclaw devices list") }
                ) {
                    Text("openclaw devices list", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { copyAndToast("openclaw devices approve --latest") }
                ) {
                    Text("openclaw devices approve --latest", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
                }
                if (!deviceId.isNullOrBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { copyAndToast(deviceId) }
                    ) {
                        Text("本机 deviceId: $deviceId", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}
