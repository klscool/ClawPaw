package com.example.clawpaw.presentation

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.clawpaw.R
import com.example.clawpaw.build.FlavorCommandGate
import com.example.clawpaw.ui.theme.clawpaw_primary
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import android.widget.Toast
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.clawpaw.data.api.RetrofitClient
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
import com.example.clawpaw.ui.chat.ChatMessageRichContent
import com.example.clawpaw.ui.theme.ClawPawTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : LocaleAwareActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.clawpaw.data.storage.OnboardingPrefs.init(this)
        com.example.clawpaw.data.storage.DebugPrefs.init(this)
        val fromOnboarding = intent.getBooleanExtra("from_onboarding", false)
        val completed = com.example.clawpaw.data.storage.OnboardingPrefs.isCompleted()
        val showEveryTime = com.example.clawpaw.data.storage.DebugPrefs.getShowOnboardingEveryTime()
        val needOnboarding = !fromOnboarding && (!completed || showEveryTime)
        if (needOnboarding) {
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
            text = if (ready) stringResource(R.string.common_ready) else stringResource(R.string.common_not_ready),
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

    var titleClickCount by remember { mutableIntStateOf(0) }
    var titleLastClickMs by remember { mutableStateOf(0L) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.main_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            val now = System.currentTimeMillis()
                            if (now - titleLastClickMs > 2000L) titleClickCount = 0
                            titleLastClickMs = now
                            titleClickCount++
                            if (titleClickCount >= 5) {
                                titleClickCount = 0
                                context.startActivity(Intent(context, DebugSettingsActivity::class.java))
                            }
                        }
                    )
                },
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
                    icon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    label = { Text(stringResource(R.string.main_tab_connection), style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Mail, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    label = { Text(stringResource(R.string.main_tab_chat), style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    label = { Text(stringResource(R.string.main_tab_settings), style = MaterialTheme.typography.labelSmall) }
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

/** 非「主会话」超过该时间未活跃则从选单隐藏；全局 main 与各 agent 的 *:main 始终保留 */
private const val SESSION_STALE_HIDE_MS = 15 * 60 * 1000L

/** 全局 main，或某 agent 下的主会话（key 最后一段为 main，如 foo:main） */
private fun isAgentMainSessionKey(key: String): Boolean =
    key == "main" || (key.contains(':') && key.substringAfterLast(':') == "main")

private fun shouldShowSessionInPicker(entry: ChatSessionEntry, now: Long, staleMs: Long = SESSION_STALE_HIDE_MS): Boolean {
    if (isAgentMainSessionKey(entry.key)) return true
    val t = entry.updatedAtMs ?: return true
    return t >= now - staleMs
}
private const val MAX_CHAT_ATTACHMENTS = 8
/** 单个附件最大约 3MB（过大会导致发送超时或 Gateway 拒绝） */
private const val MAX_ATTACHMENT_BYTES = 3 * 1024 * 1024
/** 会话选单默认展开条数，其余折叠在「显示更多」下 */
private const val CHAT_SESSION_MENU_MAX_INITIAL = 3
private const val SHOW_CHAT_IMAGE_UPLOAD = true
/** 通用文件附件入口隐藏，仅保留图片 */
private const val SHOW_CHAT_FILE_ATTACHMENT_UPLOAD = false

@Composable
private fun NodeLinkExpandableContent(context: Context, viewModel: MainViewModel) {
    RetrofitClient.init(context)
    var linkOrCode by remember { mutableStateOf("") }
    var pendingParsed by remember { mutableStateOf<Triple<String, Int, String>?>(null) }
    val activity = context as? Activity
    fun tryApplyContent(content: String) {
        val (parsed, reason) = com.example.clawpaw.util.GatewayPairingHelper.parseWithReason(content.trim())
        if (parsed == null) {
            Toast.makeText(context, context.getString(R.string.main_link_qr_parse_error_detail, reason ?: ""), Toast.LENGTH_LONG).show()
            return
        }
        pendingParsed = parsed
    }
    val qrScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(com.example.clawpaw.presentation.QrScanActivity.EXTRA_QR_CONTENT)?.let { content ->
                if (!content.isNullOrEmpty()) tryApplyContent(content)
            }
        }
    }
    if (pendingParsed != null) {
        val (host, port, token) = pendingParsed!!
        PairRoleConfirmDialog(
            host = host,
            port = port,
            token = token,
            onDismiss = { pendingParsed = null },
            onChooseNode = { h, p, t ->
                com.example.clawpaw.util.GatewayPairingHelper.saveParsedAsNode(context, h, p, t)
                viewModel.updateGatewayHost(h)
                viewModel.updateGatewayPort(p)
                viewModel.connectGateway()
                linkOrCode = ""
                pendingParsed = null
                Toast.makeText(context, context.getString(R.string.main_link_success), Toast.LENGTH_SHORT).show()
            },
            onChooseOperator = { h, p, t ->
                com.example.clawpaw.util.GatewayPairingHelper.saveParsedAsOperator(context, h, p, t)
                viewModel.updateGatewayHost(h)
                viewModel.updateGatewayPort(p)
                viewModel.connectGateway()
                linkOrCode = ""
                pendingParsed = null
                Toast.makeText(context, context.getString(R.string.main_link_success), Toast.LENGTH_SHORT).show()
            }
        )
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.main_link_setup_code), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = linkOrCode,
            onValueChange = { linkOrCode = it },
            label = { Text(stringResource(R.string.main_link_setup_code_placeholder)) },
            placeholder = { Text(stringResource(R.string.main_link_setup_code_placeholder)) },
            singleLine = false,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { tryApplyContent(linkOrCode.trim()) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            enabled = linkOrCode.isNotBlank()
        ) {
            Text(stringResource(R.string.main_link_connect_with_code))
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.main_link_scan_qr), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                activity?.let { act ->
                    try {
                        qrScanLauncher.launch(Intent(act, com.example.clawpaw.presentation.QrScanActivity::class.java))
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.main_link_qr_parse_error), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(stringResource(R.string.main_link_scan_qr_btn))
        }
    }
}

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
            if (skipped > 0) Toast.makeText(context, context.getString(R.string.main_toast_files_skipped, skipped), Toast.LENGTH_SHORT).show()
        }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { addUrisToAttachments(it) }
    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { addUrisToAttachments(it) }
    var attachMenuExpanded by remember { mutableStateOf(false) }
    var sessionMenuExpanded by remember { mutableStateOf(false) }
    var sessionMenuShowMore by remember { mutableStateOf(false) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var timestampVisibleIndex by remember { mutableStateOf<Int?>(null) }
    var messageContextMenuIndex by remember { mutableStateOf<Int?>(null) }
    var selectTextDialogContent by remember { mutableStateOf<String?>(null) }
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val listState = rememberLazyListState()
    val now = System.currentTimeMillis()
    val defaultMainEntry = ChatSessionEntry("main", stringResource(R.string.main_chat_session_default), null)
    val sessionOptions = if (sessions.isNotEmpty()) sessions else if (operatorConnected) listOf(defaultMainEntry) else emptyList()
    val visibleSessionsRaw = sessionOptions.filter { shouldShowSessionInPicker(it, now) }
    val sessionsForMenu = run {
        val base = if (visibleSessionsRaw.isNotEmpty()) visibleSessionsRaw else if (operatorConnected) listOf(defaultMainEntry) else emptyList()
        base.distinctBy { it.key }.sortedWith(
            compareBy<ChatSessionEntry> { e ->
                when {
                    e.key == "main" -> 0
                    isAgentMainSessionKey(e.key) -> 1
                    else -> 2
                }
            }.thenByDescending { it.updatedAtMs ?: 0L }
        )
    }
    val currentSessionLabel = sessionsForMenu.firstOrNull { it.key == sessionKey }?.let { friendlySessionName(it.key, it.displayName) }
        ?: sessionOptions.firstOrNull { it.key == sessionKey }?.let { friendlySessionName(it.key, it.displayName) }
        ?: friendlySessionName(sessionKey, null)
    val sessionsMenuKeySig = sessionsForMenu.joinToString("\u0001") { "${it.key}\u0002${it.updatedAtMs ?: -1L}" }
    val sessionMenuHead = remember(sessionsMenuKeySig) { sessionsForMenu.take(CHAT_SESSION_MENU_MAX_INITIAL) }
    val sessionMenuTail = remember(sessionsMenuKeySig) { sessionsForMenu.drop(CHAT_SESSION_MENU_MAX_INITIAL) }
    val chatAttachmentUploadEnabled = SHOW_CHAT_IMAGE_UPLOAD || SHOW_CHAT_FILE_ATTACHMENT_UPLOAD
    LaunchedEffect(sessionMenuExpanded) { if (!sessionMenuExpanded) sessionMenuShowMore = false }
    val displayedMessages = remember(messages, roleFilter) { messages.filter { it.role in roleFilter } }

    AppPrefs.init(context)
    val chatFontSizeSp = AppPrefs.getChatFontSize().sp
    var chatInputFocused by remember { mutableStateOf(false) }
    // 用布局高度判断紧凑/展开：键盘弹出时高度变小则收起顶栏，键盘收起后高度恢复则自动展开，避免仅依赖 focus 导致收起键盘后不展开
    var chatAreaHeightPx by remember { mutableStateOf(0) }
    var chatAreaMaxHeightPx by remember { mutableStateOf(0) }
    val chatAreaCompact = remember(chatAreaHeightPx, chatAreaMaxHeightPx) {
        chatAreaMaxHeightPx > 0 && chatAreaHeightPx < chatAreaMaxHeightPx * 0.65
    }
    val showChatHeader = !chatAreaCompact
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    BackHandler(enabled = chatInputFocused) { focusManager.clearFocus() }
    LaunchedEffect(Unit) {
        viewModel.loadChatHistory()
        viewModel.loadChatSessions(200)
    }
    // 仅在前台时定时拉取 history/sessions，后台不拉取以省电
    LaunchedEffect(operatorConnected, lifecycleOwner) {
        if (!operatorConnected) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(AppPrefs.getChatRefreshInterval().delayMs)
                viewModel.loadChatHistory(silent = true)
                viewModel.loadChatSessions(200)
            }
        }
    }
    // 仅在列表条数变化或流式「出现/消失」时滚到底部；流式正文逐字更新不参与，避免一直打断阅读
    val streamingBubbleVisible = streamingText != null
    LaunchedEffect(displayedMessages.size, streamingBubbleVisible) {
        val last = displayedMessages.size + if (streamingBubbleVisible) 1 else 0
        if (last > 0) listState.scrollToItem(last - 1)
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                chatAreaHeightPx = size.height
                if (size.height > chatAreaMaxHeightPx) chatAreaMaxHeightPx = size.height
            }
    ) {
        if (showChatHeader) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (operatorConnected && sessionsForMenu.isNotEmpty()) {
                    Box {
                        Row(
                            modifier = Modifier
                                .clickable { sessionMenuExpanded = true }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.main_chat_with_session, currentSessionLabel),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Default.ExpandMore, contentDescription = stringResource(R.string.main_select_session), modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = sessionMenuExpanded,
                            onDismissRequest = { sessionMenuExpanded = false }
                        ) {
                            sessionMenuHead.forEach { entry ->
                                DropdownMenuItem(
                                    text = { Text(friendlySessionName(entry.key, entry.displayName), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        viewModel.switchChatSession(entry.key)
                                        sessionMenuExpanded = false
                                    }
                                )
                            }
                            if (sessionMenuTail.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                if (!sessionMenuShowMore) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { sessionMenuShowMore = true },
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            stringResource(R.string.main_expand_more, sessionMenuTail.size),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { sessionMenuShowMore = false },
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            stringResource(R.string.main_collapse),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                        )
                                    }
                                    sessionMenuTail.forEach { entry ->
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
                    Text(stringResource(R.string.main_chat_with_openclaw), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val defaultRoleFilter = remember { setOf("user", "assistant") }
                val filterActive = showRaw || roleFilter != defaultRoleFilter
                Box {
                    FilterChip(
                        selected = filterActive,
                        onClick = { filterMenuExpanded = true },
                        label = { Text(stringResource(R.string.main_filter), style = MaterialTheme.typography.labelSmall) }
                    )
                    DropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false }
                    ) {
                        val showChat = "user" in roleFilter && "assistant" in roleFilter
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = showChat, onCheckedChange = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.main_show_chat_only))
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
                                    Text(stringResource(R.string.main_show_tool_calls))
                                }
                            },
                            onClick = {
                                viewModel.setChatRoleFilter(
                                    if (showTool) roleFilter - "toolResult" - "toolCall"
                                    else roleFilter + "toolResult" + "toolCall"
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = showRaw, onCheckedChange = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.main_raw))
                                }
                            },
                            onClick = { viewModel.toggleShowRawChatMessage() }
                        )
                    }
                }
            }
        }
        }
        if (!operatorConnected) {
            Text(
                stringResource(R.string.main_connect_gateway_first),
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
                    stringResource(R.string.main_loading),
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
                Box(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                focusManager.clearFocus()
                                if (msg.timestampMs != null) timestampVisibleIndex = if (timestampVisibleIndex == index) null else index
                            },
                            onLongClick = {
                                if (toCopy.isNotEmpty()) messageContextMenuIndex = index
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
                                ChatMessageRichContent(
                                    text = displayContent,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = chatFontSizeSp.sp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
                DropdownMenu(
                    expanded = messageContextMenuIndex == index,
                    onDismissRequest = { messageContextMenuIndex = null }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.main_chat_action_select_text)) },
                        onClick = {
                            messageContextMenuIndex = null
                            selectTextDialogContent = toCopy
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_copy)) },
                        onClick = {
                            messageContextMenuIndex = null
                            if (toCopy.isNotEmpty()) {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText(null, toCopy))
                                Toast.makeText(context, context.getString(R.string.common_copied), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
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
                                verticalAlignment = Alignment.Top
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .padding(top = 3.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                ChatMessageRichContent(
                                    text = streamingText!!,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = chatFontSizeSp.sp),
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        if (chatAttachmentUploadEnabled) {
            if (attachmentsLoading) {
                Text(
                    stringResource(R.string.main_attachment_loading),
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
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (SHOW_CHAT_IMAGE_UPLOAD && SHOW_CHAT_FILE_ATTACHMENT_UPLOAD) {
                Box {
                    IconButton(
                        onClick = { if (chatAttachments.size < MAX_CHAT_ATTACHMENTS) attachMenuExpanded = true },
                        enabled = !chatBusy && operatorConnected && chatAttachments.size < MAX_CHAT_ATTACHMENTS,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.main_add_attachment), modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(
                        expanded = attachMenuExpanded,
                        onDismissRequest = { attachMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_upload_image)) },
                            onClick = {
                                pickImageLauncher.launch("image/*")
                                attachMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.main_upload_file)) },
                            onClick = {
                                pickFileLauncher.launch("*/*")
                                attachMenuExpanded = false
                            }
                        )
                    }
                }
            } else if (SHOW_CHAT_IMAGE_UPLOAD) {
                IconButton(
                    onClick = { if (chatAttachments.size < MAX_CHAT_ATTACHMENTS) pickImageLauncher.launch("image/*") },
                    enabled = !chatBusy && operatorConnected && chatAttachments.size < MAX_CHAT_ATTACHMENTS,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.main_upload_image), modifier = Modifier.size(20.dp))
                }
            } else if (SHOW_CHAT_FILE_ATTACHMENT_UPLOAD) {
                IconButton(
                    onClick = { if (chatAttachments.size < MAX_CHAT_ATTACHMENTS) pickFileLauncher.launch("*/*") },
                    enabled = !chatBusy && operatorConnected && chatAttachments.size < MAX_CHAT_ATTACHMENTS,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.main_upload_file), modifier = Modifier.size(20.dp))
                }
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = { viewModel.setChatInputDraft(it) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 84.dp)
                    .onFocusChanged { chatInputFocused = it.isFocused },
                placeholder = { Text(stringResource(R.string.main_input_placeholder), style = MaterialTheme.typography.bodySmall) },
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
                    val atts = if (chatAttachmentUploadEnabled) {
                        chatAttachments.mapNotNull { att ->
                            val isImage = att.mimeType.startsWith("image/")
                            when {
                                isImage -> GatewayConnection.ChatAttachment("image", att.mimeType, att.fileName, att.base64)
                                SHOW_CHAT_FILE_ATTACHMENT_UPLOAD -> GatewayConnection.ChatAttachment("file", att.mimeType, att.fileName, att.base64)
                                else -> null
                            }
                        }
                    } else emptyList()
                    viewModel.sendChatMessage(inputText, atts)
                    viewModel.setChatInputDraft("")
                    chatAttachments.clear()
                },
                enabled = run {
                    val hasPayload = inputText.isNotBlank() || (chatAttachmentUploadEnabled && chatAttachments.isNotEmpty())
                    val notLoading = !chatAttachmentUploadEnabled || !attachmentsLoading
                    hasPayload && notLoading && !chatBusy && operatorConnected
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = stringResource(R.string.main_send), modifier = Modifier.size(20.dp))
            }
        }
    }
    selectTextDialogContent?.let { text ->
        AlertDialog(
            onDismissRequest = { selectTextDialogContent = null },
            title = { Text(stringResource(R.string.main_chat_select_text_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = chatFontSizeSp.sp),
                    maxLines = 18
                )
            },
            confirmButton = {
                TextButton(onClick = { selectTextDialogContent = null }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }
    }
}

@Composable
private fun ConnectionTabContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    AppPrefs.init(context)
    val connectionState by viewModel.gatewayConnectionState.collectAsStateWithLifecycle()
    val nodeHandshakeDone by viewModel.nodeHandshakeDone.collectAsStateWithLifecycle()
    val operatorState by viewModel.operatorConnectionState.collectAsStateWithLifecycle(initialValue = GatewayConnection.ConnectionState.Disconnected)
    val isSshConnected by viewModel.isSshConnected.collectAsStateWithLifecycle()
    val httpServiceEnabled by viewModel.httpServiceEnabled.collectAsStateWithLifecycle()
    val httpPort by viewModel.httpPort.collectAsStateWithLifecycle()
    val localIpAddress by viewModel.localIpAddress.collectAsStateWithLifecycle()
    val tailscaleIpAddress by viewModel.tailscaleIpAddress.collectAsStateWithLifecycle()
    val logEntries by viewModel.commandLogEntries.collectAsStateWithLifecycle(initialValue = emptyList())
    var autoReconnectSsh by remember { mutableStateOf(AppPrefs.getAutoReconnectSsh()) }
    var autoReconnectNode by remember { mutableStateOf(AppPrefs.getAutoReconnectNode()) }

    LaunchedEffect(Unit) { viewModel.refreshInitChecks() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionTitle(title = stringResource(R.string.main_connection_status))
        Spacer(modifier = Modifier.height(8.dp))
        val nodeRegistered = connectionState is GatewayConnection.ConnectionState.Connected && nodeHandshakeDone
        val operatorConnected = operatorState is GatewayConnection.ConnectionState.Connected
        val gatewayReadyForChat = nodeRegistered && operatorConnected
        val showGatewayDisconnect =
            connectionState is GatewayConnection.ConnectionState.Connecting ||
                connectionState is GatewayConnection.ConnectionState.Connected ||
                operatorState is GatewayConnection.ConnectionState.Connecting ||
                operatorState is GatewayConnection.ConnectionState.Connected
        // Node (Gateway) 卡片：状态 + 配对码/扫码（默认收起）
        var nodeLinkExpanded by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.main_gateway_connection_card), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    StatusChip(ready = gatewayReadyForChat)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    SmallCircleCheckbox(checked = autoReconnectNode, onCheckedChange = { autoReconnectNode = it; AppPrefs.setAutoReconnectNode(it) })
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.main_auto_reconnect), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    when {
                        connectionState is GatewayConnection.ConnectionState.Error -> stringResource(R.string.main_gateway_connect_failed)
                        connectionState is GatewayConnection.ConnectionState.Connecting ||
                            operatorState is GatewayConnection.ConnectionState.Connecting -> stringResource(R.string.common_connecting)
                        nodeRegistered && operatorConnected -> stringResource(R.string.main_gateway_connected_registered)
                        nodeRegistered && !operatorConnected -> stringResource(R.string.main_gateway_operator_channel_down)
                        connectionState is GatewayConnection.ConnectionState.Connected && !nodeHandshakeDone -> stringResource(R.string.main_gateway_connected_waiting)
                        else -> stringResource(R.string.common_disconnected)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showGatewayDisconnect) {
                        OutlinedButton(onClick = { viewModel.disconnectGateway() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.common_disconnect)) }
                    } else {
                        Button(onClick = { viewModel.connectGateway() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.common_connect)) }
                    }
                    OutlinedButton(onClick = { context.startActivity(Intent(context, GatewaySettingsActivity::class.java)) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.main_node_settings)) }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { nodeLinkExpanded = !nodeLinkExpanded },
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.main_link_methods), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        Icon(
                            if (nodeLinkExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (nodeLinkExpanded) stringResource(R.string.main_collapse) else stringResource(R.string.main_expand),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (nodeLinkExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    NodeLinkExpandableContent(context = context, viewModel = viewModel)
                }
            }
        }
        if (FlavorCommandGate.hasSshFlavor()) {
            Spacer(modifier = Modifier.height(12.dp))
            // SSH 卡片：放在 Node 下面，独立卡片（仅 full 构建）
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.main_ssh_tunnel), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        StatusChip(ready = isSshConnected)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        SmallCircleCheckbox(checked = autoReconnectSsh, onCheckedChange = { autoReconnectSsh = it; AppPrefs.setAutoReconnectSsh(it) })
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.main_auto_reconnect), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (isSshConnected) stringResource(R.string.common_connected) else stringResource(R.string.common_disconnected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isSshConnected) {
                            OutlinedButton(onClick = { viewModel.disconnectSsh() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.common_disconnect)) }
                        } else {
                            Button(onClick = { viewModel.connectSsh() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.common_connect)) }
                        }
                        OutlinedButton(onClick = { context.startActivity(Intent(context, SshTunnelActivity::class.java)) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.main_ssh_settings)) }
                    }
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
                    Text(stringResource(R.string.main_http_service), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.main_http_port_tailscale, httpPort ?: 8765), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = httpServiceEnabled, onCheckedChange = { viewModel.setHttpServiceEnabled(it) })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.main_local_ip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (localIpAddress.isNotEmpty()) {
                Text(
                    localIpAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText(null, localIpAddress))
                        Toast.makeText(context, context.getString(R.string.common_copied), Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, context.getString(R.string.common_copied), Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.main_battery_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(title = stringResource(R.string.main_command_log))
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 400.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(stringResource(R.string.main_command_log_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                if (logEntries.isEmpty()) {
                    Text(stringResource(R.string.main_no_logs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
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
    val flavorTier = FlavorCommandGate.currentTier()
    val allPermissionsGranted = when (flavorTier) {
        FlavorCommandGate.Tier.BASIC ->
            locationGranted && cameraGranted && notificationGranted && activityRecognitionGranted
        FlavorCommandGate.Tier.SENSITIVE ->
            locationGranted && cameraGranted && notificationGranted && activityRecognitionGranted &&
                contactsGranted && calendarGranted && storageOrMediaImagesGranted && smsGranted && phoneGranted &&
                bluetoothGranted && notificationListenerEnabled
        FlavorCommandGate.Tier.FULL ->
            locationGranted && cameraGranted && notificationGranted && isAccessibilityEnabled &&
                contactsGranted && calendarGranted && storageOrMediaImagesGranted && activityRecognitionGranted &&
                bluetoothGranted && smsGranted && phoneGranted && notificationListenerEnabled
    }

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
        SectionTitle(title = stringResource(R.string.main_behavior_display))
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(20.dp)) {
                var languageMenuExpanded by remember { mutableStateOf(false) }
                val currentLang = MainPrefs.getAppLanguage()
                val languageLabel = when (currentLang) {
                    "zh" -> stringResource(R.string.language_zh)
                    "en" -> stringResource(R.string.language_en)
                    else -> stringResource(R.string.language_system)
                }
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { languageMenuExpanded = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.main_language), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.language_switch_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(languageLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = languageMenuExpanded, onDismissRequest = { languageMenuExpanded = false }) {
                        listOf("system" to R.string.language_system, "zh" to R.string.language_zh, "en" to R.string.language_en).forEach { (tag, labelRes) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = {
                                    languageMenuExpanded = false
                                    if (tag != currentLang) {
                                        MainPrefs.setAppLanguage(tag)
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                }
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                var persistentNotification by remember { mutableStateOf(AppPrefs.getPersistentNotification()) }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.main_persistent_notification), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.main_persistent_notification_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = persistentNotification, onCheckedChange = { persistentNotification = it; AppPrefs.setPersistentNotification(it) })
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                var reconnectInterval by remember { mutableStateOf(AppPrefs.getReconnectCheckInterval()) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.main_reconnect_interval), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ReconnectCheckInterval.entries.forEach { opt ->
                            val sec = (opt.delayMs / 1000).toInt()
                            FilterChip(selected = reconnectInterval == opt, onClick = { reconnectInterval = opt; AppPrefs.setReconnectCheckInterval(opt) }, label = { Text(stringResource(R.string.main_seconds, sec), style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                var chatRefreshInterval by remember { mutableStateOf(AppPrefs.getChatRefreshInterval()) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.main_chat_refresh_interval), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        ChatRefreshInterval.entries.forEach { opt ->
                            val sec = (opt.delayMs / 1000).toInt()
                            FilterChip(selected = chatRefreshInterval == opt, onClick = { chatRefreshInterval = opt; AppPrefs.setChatRefreshInterval(opt) }, label = { Text(stringResource(R.string.main_seconds, sec), style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                var chatFontSize by remember { mutableStateOf(AppPrefs.getChatFontSize()) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.main_chat_font_size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChatFontSize.entries.forEach { opt ->
                            val label = when (opt) {
                                ChatFontSize.LARGE -> stringResource(R.string.chat_font_large)
                                ChatFontSize.MEDIUM -> stringResource(R.string.chat_font_medium)
                                ChatFontSize.SMALL -> stringResource(R.string.chat_font_small)
                            }
                            FilterChip(selected = chatFontSize == opt, onClick = { chatFontSize = opt; AppPrefs.setChatFontSize(opt) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(title = stringResource(R.string.main_permission_settings))
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(20.dp)) {
                key(permissionRefreshTick) {
                    if (FlavorCommandGate.hasAccessibilityFlavor()) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.main_accessibility_service), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.clickable { viewModel.openAccessibilitySettings() }.minimumInteractiveComponentSize()) {
                                StatusChip(ready = isAccessibilityEnabled)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.main_input_method), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            IconButton(
                                onClick = { showImeTipDialog = true },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = stringResource(R.string.main_info), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.clickable { viewModel.openInputMethodSettings() }.minimumInteractiveComponentSize()) {
                                StatusChip(ready = isOurImeDefault)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    }
                    PermissionSettingRow(context = context, permission = Manifest.permission.ACCESS_FINE_LOCATION, label = stringResource(R.string.main_location), desc = stringResource(R.string.main_location_desc), onRequestPermission = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) })
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    PermissionSettingRow(context = context, permission = Manifest.permission.CAMERA, label = stringResource(R.string.main_camera), desc = stringResource(R.string.main_camera_desc), onRequestPermission = { cameraLauncher.launch(Manifest.permission.CAMERA) })
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        PermissionSettingRow(context = context, permission = Manifest.permission.POST_NOTIFICATIONS, label = stringResource(R.string.main_notification), desc = stringResource(R.string.main_notification_desc), onRequestPermission = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
                    }
                    if (flavorTier != FlavorCommandGate.Tier.BASIC) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.main_notification_listener), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.main_notification_listener_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (notificationListenerEnabled) {
                                Text(stringResource(R.string.main_enabled), style = MaterialTheme.typography.labelSmall, color = clawpaw_primary)
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.openNotificationListenerSettings() },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(36.dp)
                                ) { Text(stringResource(R.string.common_go_auth)) }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        PermissionSettingRow(context = context, permission = Manifest.permission.READ_CALENDAR, label = stringResource(R.string.main_calendar), desc = stringResource(R.string.main_calendar_desc), onRequestPermission = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) })
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        PermissionSettingRow(
                            context = context,
                            permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE,
                            label = stringResource(R.string.main_photos_storage),
                            desc = stringResource(R.string.main_photos_storage_desc),
                            onRequestPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) storageLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                                else storageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            PermissionSettingRow(context = context, permission = Manifest.permission.ACTIVITY_RECOGNITION, label = stringResource(R.string.main_activity_recognition), desc = stringResource(R.string.main_activity_recognition_desc), onRequestPermission = { activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) })
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            PermissionSettingRow(context = context, permission = Manifest.permission.BLUETOOTH_CONNECT, label = stringResource(R.string.main_bluetooth), desc = stringResource(R.string.main_bluetooth_desc), onRequestPermission = { bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) })
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        PermissionSettingRow(context = context, permission = Manifest.permission.READ_CONTACTS, label = stringResource(R.string.main_contacts), desc = stringResource(R.string.main_contacts_desc), onRequestPermission = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) })
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.main_sms), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.main_sms_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (smsGranted) Text(stringResource(R.string.main_permission_granted), style = MaterialTheme.typography.labelSmall, color = clawpaw_primary)
                            else OutlinedButton(onClick = { smsLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(36.dp)) { Text(stringResource(R.string.common_go_auth)) }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        PermissionSettingRow(context = context, permission = Manifest.permission.CALL_PHONE, label = stringResource(R.string.main_phone), desc = stringResource(R.string.main_phone_desc), onRequestPermission = { phoneLauncher.launch(Manifest.permission.CALL_PHONE) })
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        PermissionSettingRow(context = context, permission = Manifest.permission.ACTIVITY_RECOGNITION, label = stringResource(R.string.main_activity_recognition), desc = stringResource(R.string.main_activity_recognition_desc), onRequestPermission = { activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) })
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(onClick = { viewModel.openAppSettings() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Text(stringResource(R.string.main_app_detail_permission))
        }
    }

    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.main_accessibility_dialog_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    stringResource(R.string.main_accessibility_dialog_text),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showAccessibilityDialog = false; viewModel.openAccessibilitySettings() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text(stringResource(R.string.common_go_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDialog = false }) {
                    Text(stringResource(R.string.common_close), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            title = { Text(stringResource(R.string.main_ime_dialog_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    stringResource(R.string.main_ime_dialog_text),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showImeTipDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text(stringResource(R.string.common_got_it)) }
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
            Text(stringResource(R.string.main_permission_granted), style = MaterialTheme.typography.labelSmall, color = clawpaw_primary)
        } else {
            OutlinedButton(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(36.dp)
            ) { Text(stringResource(R.string.common_go_auth)) }
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
        title = { Text(stringResource(R.string.main_auth_guide_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.main_auth_guide_text), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.main_auth_guide_location), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.main_auth_guide_camera), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.main_auth_guide_notification), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.main_auth_guide_accessibility), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.openAppSettings(); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text(stringResource(R.string.common_go_settings)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_later), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Toast.makeText(context, context.getString(R.string.common_copied), Toast.LENGTH_SHORT).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(R.string.main_pairing_title), style = MaterialTheme.typography.titleLarge) },
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
                        Text(stringResource(R.string.main_pairing_device_id, deviceId), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_got_it), color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}
