package com.example.clawpaw.presentation

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Settings.Secure
import android.view.inputmethod.InputMethodManager
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.clawpaw.data.api.RetrofitClient
import com.example.clawpaw.gateway.GatewayConnection
import com.example.clawpaw.service.ClawPawAccessibilityService
import com.example.clawpaw.service.NotificationListener
import com.example.clawpaw.data.storage.AppPrefs
import com.example.clawpaw.data.storage.MainPrefs
import com.example.clawpaw.service.GatewayConnectionService
import com.example.clawpaw.service.NodeHttpService
import com.example.clawpaw.ssh.SshPrefs
import com.example.clawpaw.ssh.SshTunnelManager
import com.example.clawpaw.util.CommandLog
import com.example.clawpaw.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

/** 对话页单条消息；content 含工具调用；contentChatOnly 不含 toolCall/toolResult，勾选「显示工具调用」时用 content 否则用 contentChatOnly。 */
data class ChatMessage(val role: String, val content: String, val contentChatOnly: String? = null, val id: String? = null, val rawContent: String? = null, val timestampMs: Long? = null, val imageBase64s: List<String> = emptyList())

/** 会话列表项，与官方 sessions.list 一致 */
data class ChatSessionEntry(val key: String, val displayName: String?, val updatedAtMs: Long?)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val gatewayHost = RetrofitClient.serverHost.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ""
    )

    val maskedGatewayHost = gatewayHost.map { host ->
        if (host.isEmpty()) return@map ""
        if (host.length <= 6) return@map host
        val prefix = host.take(4)
        val suffix = host.takeLast(2)
        val mask = "*".repeat((host.length - 6).coerceAtLeast(1))
        "$prefix$mask$suffix"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val gatewayToken = RetrofitClient.gatewayToken.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ""
    )

    /** Token 脱敏显示：有值时显示「••••」+ 尾 4 位，无值时空串 */
    val maskedGatewayToken = gatewayToken.map { t ->
        if (t.isEmpty()) "" else "••••${t.takeLast(4)}"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val gatewayConnectionState = GatewayConnectionService.connectionState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        GatewayConnection.ConnectionState.Disconnected
    )

    init {
        viewModelScope.launch {
            var prev: GatewayConnection.ConnectionState = GatewayConnection.ConnectionState.Disconnected
            gatewayConnectionState.collect { state ->
                val disconnected = state is GatewayConnection.ConnectionState.Disconnected || state is GatewayConnection.ConnectionState.Error
                val wasConnected = prev is GatewayConnection.ConnectionState.Connected || prev is GatewayConnection.ConnectionState.Connecting
                if (disconnected && wasConnected) refreshInitChecks()
                prev = state
            }
        }
    }

    /** Node 是否已完成握手（仅此时 Gateway 会认为 node 在线） */
    val nodeHandshakeDone = GatewayConnectionService.nodeHandshakeDone.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    /** Operator 连接状态，对话页用此判断是否可发消息/拉历史 */
    val operatorConnectionState = GatewayConnectionService.operatorConnectionState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        GatewayConnection.ConnectionState.Disconnected
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    /** 初始化时 OpenClaw 连接失败后的提示（非必须），由 runInitOpenClawCheck 设置 */
    private val _initOpenClawMessage = MutableStateFlow<String?>(null)
    val initOpenClawMessage = _initOpenClawMessage.asStateFlow()

    /** 无障碍已开启 */
    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled = _isAccessibilityEnabled.asStateFlow()

    /** 当前默认输入法是否为本应用输入法 */
    private val _isOurImeDefault = MutableStateFlow(false)
    val isOurImeDefault = _isOurImeDefault.asStateFlow()

    /** 通知监听（通知使用权）是否已开启 */
    private val _notificationListenerEnabled = MutableStateFlow(false)
    val notificationListenerEnabled = _notificationListenerEnabled.asStateFlow()

    /** SSH 隧道是否已连接 */
    private val _isSshConnected = MutableStateFlow(false)
    val isSshConnected = _isSshConnected.asStateFlow()

    private var sshReconnectJob: Job? = null
    private var lastSshReconnectAttemptMs: Long = 0

    /** 外部执行命令日志（ADB/WS/HTTP），供主界面「执行日志」展示 */
    val commandLogEntries = CommandLog.entries

    /** 对话页消息列表（历史 + 实时 chat 事件） */
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()

    /** 对话输入框草稿，切换 tab 时保留 */
    private val _chatInputDraft = MutableStateFlow("")
    val chatInputDraft = _chatInputDraft.asStateFlow()
    fun setChatInputDraft(text: String) { _chatInputDraft.value = text }

    /** 拉取历史中 / 发送中 */
    private val _chatBusy = MutableStateFlow(false)
    val chatBusy = _chatBusy.asStateFlow()

    /** 对话页错误提示（如未连接、发送失败） */
    private val _chatError = MutableStateFlow<String?>(null)
    val chatError = _chatError.asStateFlow()

    /** 当前选中的会话 key，用于 history/send/subscribe */
    private val _chatSessionKey = MutableStateFlow("main")
    val chatSessionKey = _chatSessionKey.asStateFlow()

    /** 会话列表（来自 sessions.list） */
    private val _chatSessions = MutableStateFlow<List<ChatSessionEntry>>(emptyList())
    val chatSessions = _chatSessions.asStateFlow()

    /** 对方正在输入的流式文案（state=delta 时更新，final 时清空） */
    private val _streamingAssistantText = MutableStateFlow<String?>(null)
    val chatStreamingAssistantText = _streamingAssistantText.asStateFlow()

    /** 是否显示消息原文（原始 JSON），否则显示可读文案 */
    private val _showRawChatMessage = MutableStateFlow(false)
    val showRawChatMessage = _showRawChatMessage.asStateFlow()

    /** 可见消息 role 多选：user、assistant、toolResult 等，默认只显示聊天(user+assistant) */
    private val _chatRoleFilter = MutableStateFlow(setOf("user", "assistant"))
    val chatRoleFilter = _chatRoleFilter.asStateFlow()

    /** 本机 deviceId（握手后可用），用于对话页配对提示与示例命令 */
    private val _pairingDeviceId = MutableStateFlow<String?>(null)
    val pairingDeviceId = _pairingDeviceId.asStateFlow()

    fun setChatRoleFilter(roles: Set<String>) { _chatRoleFilter.value = roles }

    fun toggleShowRawChatMessage() { _showRawChatMessage.value = !_showRawChatMessage.value }

    init {
        MainPrefs.init(getApplication())
        viewModelScope.launch {
            var chatCollectJob: Job? = null
            operatorConnectionState.collect { state ->
                chatCollectJob?.cancel()
                if (state is GatewayConnection.ConnectionState.Connected) {
                    _pairingDeviceId.value = GatewayConnection.getStoredDeviceId(getApplication())
                    val c = GatewayConnectionService.operatorConnection
                    if (c != null) {
                        chatCollectJob = launch {
                            c.chatEvents.collect { payload -> addChatEvent(payload) }
                        }
                    }
                } else {
                    _pairingDeviceId.value = null
                }
            }
        }
    }

    /** 解析结果：displayText 含工具调用；displayTextChatOnly 不含 toolCall/toolResult。 */
    private data class ParsedMessage(val role: String, val displayText: String, val displayTextChatOnly: String, val imageBase64s: List<String>)

    /** 从 content / data / base64 / image_url 取 base64；bytes 仅当为长字符串时当作 base64（服务端常把 bytes 当大小如 309460 返回）。 */
    private fun extractImageBase64(part: org.json.JSONObject): String? {
        for (key in listOf("content", "data", "base64")) {
            val v = part.optString(key, "").trim().takeIf { it.isNotEmpty() }
            if (!v.isNullOrEmpty()) return v
        }
        val bytesVal = part.opt("bytes")
        if (bytesVal is String) {
            val s = bytesVal.trim()
            if (s.length > 200) return s
        }
        var url = part.optString("image_url", "").trim()
        if (url.isEmpty()) {
            val urlObj = part.optJSONObject("image_url")
            url = urlObj?.optString("url", "")?.trim() ?: ""
        }
        if (url.isNotEmpty()) {
            if (url.startsWith("data:", ignoreCase = true)) {
                val base64 = url.substringAfter("base64,", "").trim()
                if (base64.isNotEmpty()) return base64
            }
            return url
        }
        val source = part.optJSONObject("source")
        if (source != null) {
            val data = source.optString("data", "").trim().ifEmpty { source.optString("base64", "").trim() }
            if (data.isNotEmpty()) return data
        }
        return null
    }

    /** 与官方一致：从 Gateway 消息里提取可展示文本与图片；无 role 时用 delivery 标识。content 可为字符串或 content[] 数组；也尝试 message.content、parts。 */
    private fun extractMessageDisplayText(msgObj: org.json.JSONObject): ParsedMessage? {
        val role = msgObj.optString("role", "").trim()
            .ifEmpty { msgObj.optString("delivery", "").trim().ifEmpty { "assistant" } }
        var contentEl = msgObj.opt("content")
        if (contentEl == null) contentEl = msgObj.optJSONObject("message")?.opt("content")
        if (contentEl == null) contentEl = msgObj.optJSONArray("parts")
        val displayText: String
        var displayTextChatOnly: String = ""
        val imageBase64s = mutableListOf<String>()
        when (contentEl) {
            is String -> {
                displayText = contentEl.trim()
                displayTextChatOnly = displayText
            }
            is org.json.JSONArray -> {
                val sb = StringBuilder()
                val sbChatOnly = StringBuilder()
                for (j in 0 until contentEl.length()) {
                    val part = contentEl.optJSONObject(j) ?: continue
                    when (part.optString("type", "")) {
                        "text" -> {
                            part.optString("text", "").trim().let { t ->
                                if (t.isNotEmpty()) { sb.append(t); sbChatOnly.append(t) }
                            }
                        }
                        "image" -> {
                            val omitted = part.optBoolean("omitted", false)
                            val b64 = if (omitted) null else extractImageBase64(part)
                            if (b64 != null) {
                                imageBase64s.add(b64)
                            } else {
                                val keys = buildList { part.keys().forEachRemaining { add(it) } }
                                Logger.d("Chat", "image 部分未解析到 base64，omitted=$omitted part.keys=${keys.joinToString()}")
                            }
                            sb.append("[图片]")
                            sbChatOnly.append("[图片]")
                        }
                        "thinking" -> { /* 不显示 */ }
                        "toolCall" -> {
                            val name = part.optString("name", "").trim().ifEmpty { part.optJSONObject("function")?.optString("name", "")?.trim() ?: "" }
                            val text = part.optString("text", "").trim().ifEmpty { part.optString("arguments", "").trim() }
                            if (name.isNotEmpty() || text.isNotEmpty()) {
                                if (sb.isNotEmpty()) sb.append("\n")
                                if (name.isNotEmpty()) sb.append("[toolCall $name]")
                                if (text.isNotEmpty()) sb.append(if (sb.toString().endsWith("]")) "\n" else "").append(text)
                            }
                            /* sbChatOnly 不追加，仅勾选工具调用时显示 */
                        }
                        "toolResult" -> {
                            val name = part.optString("name", "").trim()
                            val text = part.optString("text", "").trim().ifEmpty { part.optString("content", "").trim() }
                            if (name.isNotEmpty() || text.isNotEmpty()) {
                                if (sb.isNotEmpty()) sb.append("\n")
                                if (name.isNotEmpty()) sb.append("[toolResult $name]")
                                if (text.isNotEmpty()) sb.append(if (sb.toString().endsWith("]")) "\n" else "").append(text)
                            }
                            /* sbChatOnly 不追加 */
                        }
                        else -> {
                            val tag = part.optString("type", "").trim().ifEmpty { "attachment" }
                            sb.append("[$tag]")
                            sbChatOnly.append("[$tag]")
                        }
                    }
                }
                displayText = sb.toString().trim()
                displayTextChatOnly = sbChatOnly.toString().trim()
            }
            else -> {
                displayText = msgObj.optString("text", "").trim()
                displayTextChatOnly = displayText
            }
        }
        val imagesFromTop = msgObj.optJSONArray("images")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optString(i, "").trim().takeIf { it.isNotEmpty() } }
        }.orEmpty()
        val allImages = (imageBase64s + imagesFromTop).distinct()
        if (displayText.isEmpty() && allImages.isEmpty()) return null
        if (allImages.isNotEmpty()) {
            val keys = buildList { msgObj.keys().forEachRemaining { add(it) } }
            Logger.d("Chat", "parseMessage: role=$role imageCount=${allImages.size} firstLen=${allImages.firstOrNull()?.length ?: 0} contentKeys=${keys.joinToString()}")
        }
        val finalText = displayText.ifEmpty { "[图片]" }
        val finalChatOnly = when {
            displayTextChatOnly.isNotEmpty() -> displayTextChatOnly
            allImages.isNotEmpty() -> "[图片]"
            else -> ""
        }
        return ParsedMessage(role, finalText, finalChatOnly, allImages)
    }

    private fun addChatEvent(payload: org.json.JSONObject) {
        val eventSessionKey = payload.optString("sessionKey", "").trim().ifEmpty { null }
        if (eventSessionKey != null && eventSessionKey != _chatSessionKey.value) {
            return
        }
        val state = payload.optString("state", "")
        when (state) {
            "delta" -> {
                val message = payload.optJSONObject("message")
                val msgObj = message ?: payload
                val parsed = extractMessageDisplayText(msgObj) ?: return
                if (parsed.displayText.isNotBlank()) _streamingAssistantText.value = parsed.displayText
            }
            "final", "aborted", "error" -> {
                _streamingAssistantText.value = null
                viewModelScope.launch { loadChatHistory() }
            }
            else -> {
                val message = payload.optJSONObject("message")
                val msgObj = message ?: payload
                val parsed = extractMessageDisplayText(msgObj)
                val raw = msgObj.toString()
                parsed?.let { p ->
                    if (p.displayText.isNotBlank() || p.imageBase64s.isNotEmpty()) _chatMessages.value = _chatMessages.value + ChatMessage(role = p.role, content = p.displayText, contentChatOnly = p.displayTextChatOnly, id = payload.optString("id", "").takeIf { it.isNotBlank() }, rawContent = raw, timestampMs = parseMessageTimestampMs(payload), imageBase64s = p.imageBase64s)
                }
            }
        }
    }

    /** 发送一条消息到 OpenClaw；attachments 为空则仅发文本；成功后仅依赖 chat 事件追加回复。 */
    fun sendChatMessage(content: String, attachments: List<GatewayConnection.ChatAttachment> = emptyList()) {
        val text = content.trim()
        if (text.isEmpty() && attachments.isEmpty()) return
        val displayText = if (text.isNotEmpty()) text else "见附件。"
        val rawForSent = if (attachments.isEmpty()) null else {
            val contentArr = org.json.JSONArray()
            contentArr.put(org.json.JSONObject().put("type", "text").put("text", displayText))
            attachments.forEach { a ->
                contentArr.put(org.json.JSONObject().put("type", a.type).put("fileName", a.fileName).put("mimeType", a.mimeType))
            }
            org.json.JSONObject().put("content", contentArr).toString()
        }
        viewModelScope.launch {
            _chatError.value = null
            _chatMessages.value = _chatMessages.value + ChatMessage(role = "user", content = displayText, rawContent = rawForSent, timestampMs = System.currentTimeMillis())
            val conn = GatewayConnectionService.operatorConnection
            if (conn == null) {
                _chatError.value = "Operator 未连接，无法发送"
                return@launch
            }
            _chatBusy.value = true
            val res = kotlin.runCatching { conn.sendChatMessage(text, _chatSessionKey.value, "off", attachments).getOrThrow() }
            _chatBusy.value = false
            res.fold(
                onSuccess = { if (!it.ok) _chatError.value = "发送失败: ${it.error ?: "未知错误"}" },
                onFailure = { _chatError.value = "发送失败: ${it.message ?: "未知错误"}" }
            )
        }
    }

    /** 拉取当前会话历史并替换对话列表。[silent] 为 true 时不显示加载状态、不覆盖错误信息，用于后台定时刷新。 */
    fun loadChatHistory(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _chatError.value = null
            val conn = GatewayConnectionService.operatorConnection
            if (conn == null) {
                if (!silent) _chatError.value = "Operator 未连接，无法拉取历史"
                return@launch
            }
            if (!silent) _chatBusy.value = true
            val res = kotlin.runCatching { conn.getChatHistory(_chatSessionKey.value).getOrThrow() }
            if (!silent) _chatBusy.value = false
            res.fold(
                onSuccess = { result ->
                    if (!result.ok) {
                        if (!silent) _chatError.value = "chat.history: ${result.error ?: "拉取失败"}"
                        return@launch
                    }
                    val payload = result.payload ?: return@launch
                    val arr = payload.optJSONArray("messages") ?: payload.optJSONArray("items")
                    val list = mutableListOf<ChatMessage>()
                    if (arr != null) {
                        val payloadKeys = buildList { payload.keys().forEachRemaining { add(it) } }
                        if (arr.length() > 0) {
                            val first = arr.optJSONObject(0)
                            val firstKeys = first?.let { buildList { it.keys().forEachRemaining { add(it) } } } ?: emptyList()
                            Logger.d("Chat", "loadChatHistory: payload.keys=$payloadKeys firstMsg.keys=$firstKeys")
                        }
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            extractMessageDisplayText(o)?.let { p ->
                                list.add(ChatMessage(role = p.role, content = p.displayText, contentChatOnly = p.displayTextChatOnly, id = o.optString("id", "").takeIf { it.isNotBlank() }, rawContent = o.toString(), timestampMs = parseMessageTimestampMs(o), imageBase64s = p.imageBase64s))
                            }
                        }
                    }
                    val totalImages = list.sumOf { it.imageBase64s.size }
                    if (totalImages > 0) Logger.d("Chat", "loadChatHistory: messages=${list.size} totalImageParts=$totalImages")
                    _chatMessages.value = list
                },
                onFailure = { if (!silent) _chatError.value = "chat.history 请求失败: ${it.message ?: "拉取失败"}" }
            )
        }
    }

    private fun parseMessageTimestampMs(o: org.json.JSONObject): Long? {
        var raw = o.optLong("timestampMs", 0L)
        if (raw == 0L) raw = o.optLong("timestamp", 0L)
        if (raw <= 0L) return null
        return if (raw in 1L..9999999999L) raw * 1000L else raw
    }

    fun clearChatError() { _chatError.value = null }

    /** 解析会话更新时间：支持 updatedAtMs/updatedAt，秒级时间戳自动转毫秒 */
    private fun parseSessionUpdatedAtMs(o: org.json.JSONObject): Long? {
        var raw = o.optLong("updatedAtMs", 0L)
        if (raw == 0L) raw = o.optLong("updatedAt", 0L)
        if (raw <= 0L) return null
        return if (raw in 1L..9999999999L) raw * 1000L else raw
    }

    /** 拉取会话列表并更新 chatSessions；与官方 sessions.list 一致 */
    fun loadChatSessions(limit: Int = 200) {
        viewModelScope.launch {
            val conn = GatewayConnectionService.operatorConnection ?: return@launch
            val res = kotlin.runCatching { conn.requestSessionsList(limit).getOrThrow() }
            res.fold(
                onSuccess = { result ->
                    if (!result.ok) return@launch
                    val payload = result.payload ?: return@launch
                    val arr = payload.optJSONArray("sessions") ?: return@launch
                    val list = mutableListOf<ChatSessionEntry>()
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val key = o.optString("key", "").takeIf { it.isNotBlank() } ?: continue
                        val updatedAtMs = parseSessionUpdatedAtMs(o)
                        list.add(ChatSessionEntry(
                            key = key,
                            displayName = o.optString("displayName", "").takeIf { it.isNotBlank() },
                            updatedAtMs = updatedAtMs
                        ))
                    }
                    _chatSessions.value = list
                },
                onFailure = { }
            )
        }
    }

    /** 切换当前会话：重新订阅并拉取该会话历史 */
    fun switchChatSession(key: String) {
        val k = key.trim().ifEmpty { "main" }
        if (_chatSessionKey.value == k) return
        _chatSessionKey.value = k
        _streamingAssistantText.value = null
        viewModelScope.launch {
            val conn = GatewayConnectionService.operatorConnection ?: return@launch
            _chatBusy.value = true
            kotlin.runCatching { conn.sendChatSubscribe(k).getOrThrow() }
            kotlin.runCatching { conn.getChatHistory(k).getOrThrow() }.fold(
                onSuccess = { result ->
                    if (!result.ok) return@fold
                    val payload = result.payload ?: return@fold
                    val arr = payload.optJSONArray("messages") ?: payload.optJSONArray("items")
                    val list = mutableListOf<ChatMessage>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            extractMessageDisplayText(o)?.let { p ->
                                list.add(ChatMessage(role = p.role, content = p.displayText, contentChatOnly = p.displayTextChatOnly, id = o.optString("id", "").takeIf { it.isNotBlank() }, rawContent = o.toString(), timestampMs = parseMessageTimestampMs(o), imageBase64s = p.imageBase64s))
                            }
                        }
                    }
                    val totalImages = list.sumOf { it.imageBase64s.size }
                    Logger.d("Chat", "switchChatSession: messages=${list.size} totalImageParts=$totalImages")
                    _chatMessages.value = list
                },
                onFailure = { _chatError.value = "chat.history 请求失败: ${it.message ?: "拉取失败"}" }
            )
            _chatBusy.value = false
        }
    }

    /** HTTP 服务开关（默认开启） */
    private val _httpServiceEnabled = MutableStateFlow(MainPrefs.getHttpServiceEnabled())
    val httpServiceEnabled = _httpServiceEnabled.asStateFlow()

    fun setHttpServiceEnabled(enabled: Boolean) {
        MainPrefs.setHttpServiceEnabled(enabled)
        _httpServiceEnabled.value = enabled
        val app = getApplication<Application>()
        val intent = Intent(app, NodeHttpService::class.java)
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent) else app.startService(intent)
        } else {
            app.stopService(intent)
        }
    }

    /** 刷新初始化检查项（无障碍、输入法）。立即刷新一次，并延迟再刷一次以应对从设置页返回后系统状态尚未更新的情况 */
    fun refreshInitChecks() {
        doRefreshInitChecks()
        viewModelScope.launch {
            delay(600L)
            doRefreshInitChecks()
        }
    }

    private fun doRefreshInitChecks() {
        val app = getApplication<Application>()
        _isAccessibilityEnabled.value = ClawPawAccessibilityService.getInstance() != null
        val defaultImi = Secure.getString(app.contentResolver, Secure.DEFAULT_INPUT_METHOD)
        _isOurImeDefault.value = defaultImi?.contains(app.packageName) == true
        _isSshConnected.value = SshTunnelManager.isConnected()
        if (!_isSshConnected.value) {
            SshPrefs.init(app)
            AppPrefs.init(app)
            val now = System.currentTimeMillis()
            val cooldownOk = now - lastSshReconnectAttemptMs > 30_000
            if (AppPrefs.getAutoReconnectSsh() && SshPrefs.getWantedSshConnected() && cooldownOk && (sshReconnectJob == null || !sshReconnectJob!!.isActive)) {
                lastSshReconnectAttemptMs = now
                CommandLog.addEntry("SSH", "5 秒后尝试重连…")
                val j = viewModelScope.launch {
                    delay(5000)
                    connectSsh()
                }
                j.invokeOnCompletion { sshReconnectJob = null }
                sshReconnectJob = j
            }
        }
        refreshLocalNetworkInfo()
    }

    private val _httpPort = MutableStateFlow<Int?>(8765)
    val httpPort = _httpPort.asStateFlow()

    private val _localIpAddress = MutableStateFlow<String>("")
    val localIpAddress = _localIpAddress.asStateFlow()

    /** Tailscale 等 CGNAT 网段（100.x.x.x）的 IP，未检测到时为空 */
    private val _tailscaleIpAddress = MutableStateFlow<String>("")
    val tailscaleIpAddress = _tailscaleIpAddress.asStateFlow()

    /** 刷新本机主要网络 IP（用于展示），在 refreshInitChecks 时一并调用；同时检测 Tailscale IP */
    fun refreshLocalNetworkInfo() {
        val app = getApplication<Application>()
        _localIpAddress.value = getLocalIpAddress(app)
        _tailscaleIpAddress.value = getTailscaleIpAddress(app)
    }

    private fun getLocalIpAddress(context: Context): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (ni in java.util.Collections.list(interfaces)) {
                if (ni.isLoopback || !ni.isUp) continue
                for (addr in java.util.Collections.list(ni.inetAddresses)) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: ""
                        if (!host.startsWith("100.")) return host
                    }
                }
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    /** 检测 Tailscale 等 100.x.x.x（CGNAT）网段 IP；设备已安装并连接 Tailscale 时能检测到 */
    private fun getTailscaleIpAddress(context: Context): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (ni in java.util.Collections.list(interfaces)) {
                if (ni.isLoopback || !ni.isUp) continue
                for (addr in java.util.Collections.list(ni.inetAddresses)) {
                    if (addr is java.net.Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.startsWith("100.")) return host
                    }
                }
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    fun updateGatewayHost(host: String) {
        RetrofitClient.setServerHost(host.trim())
        _uiState.value = UiState.Success("Gateway 地址已更新")
    }

    fun updateGatewayToken(token: String) {
        RetrofitClient.setGatewayToken(token)
        _uiState.value = UiState.Success("Gateway Token 已保存")
    }

    fun connectGateway() {
        val host = RetrofitClient.serverHost.value
        if (host.isBlank()) {
            _uiState.value = UiState.Error("请先设置 Gateway 地址")
            return
        }
        val intent = Intent(getApplication(), GatewayConnectionService::class.java).apply {
            putExtra(GatewayConnectionService.EXTRA_GATEWAY_HOST, host.trim())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
        _uiState.value = UiState.Success("正在连接…（退到后台也会保持）")
    }

    fun disconnectGateway() {
        getApplication<Application>().stopService(Intent(getApplication(), GatewayConnectionService::class.java))
        _uiState.value = UiState.Success("已断开")
    }

    /** SSH 隧道连接（在 IO 线程执行，完成后刷新状态） */
    fun connectSsh() {
        SshPrefs.init(getApplication())
        SshPrefs.setWantedSshConnected(true)
        viewModelScope.launch {
            val config = SshPrefs.getConfig()
            val mappings = SshPrefs.getPortMappings()
            val reverseMappings = SshPrefs.getReversePortMappings()
            if (config.host.isBlank() || config.username.isBlank()) {
                CommandLog.addEntry("SSH", "连接失败: 请先在 SSH 设置中填写地址和用户名")
                _uiState.value = UiState.Error("请先在 SSH 设置中填写地址和用户名")
                return@launch
            }
            if (mappings.isEmpty() && reverseMappings.isEmpty()) {
                CommandLog.addEntry("SSH", "连接失败: 请至少在 SSH 设置中添加一条端口映射")
                _uiState.value = UiState.Error("请至少在 SSH 设置中添加一条端口映射")
                return@launch
            }
            CommandLog.addEntry("SSH", "连接中…")
            val err = withContext(Dispatchers.IO) {
                SshTunnelManager.connect(config, mappings, reverseMappings)
            }
            doRefreshInitChecks()
            if (err != null) {
                CommandLog.addEntry("SSH", "连接失败: $err")
                _uiState.value = UiState.Error("SSH 连接失败: $err")
            } else {
                CommandLog.addEntry("SSH", "连接成功，隧道已建立")
                _uiState.value = UiState.Success("SSH 已连接")
            }
        }
    }

    fun disconnectSsh() {
        SshPrefs.init(getApplication())
        SshPrefs.setWantedSshConnected(false)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SshTunnelManager.disconnect() }
            CommandLog.addEntry("SSH", "已断开（主动断开）")
            doRefreshInitChecks()
            _uiState.value = UiState.Success("SSH 已断开")
        }
    }

    /** 清除本地设备身份，解决 device identity mismatch。下次连接会生成新 deviceId，需在主机执行 openclaw nodes approve。 */
    fun clearDeviceIdentity() {
        GatewayConnection.clearStoredDeviceIdentity(getApplication())
        _uiState.value = UiState.Success("已清除设备身份，请先断开再重新连接，并在主机执行 openclaw nodes approve")
    }

    fun openInputMethodSettings() {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        getApplication<Application>().startActivity(intent)
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        getApplication<Application>().startActivity(intent)
    }

    /** 打开「通知使用权」设置页，用户需手动开启本应用以获取通知列表。 */
    fun openNotificationListenerSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        getApplication<Application>().startActivity(intent)
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            data = android.net.Uri.parse("package:${getApplication<Application>().packageName}")
        }
        getApplication<Application>().startActivity(intent)
    }

    fun markAuthGuideShownAfterConnect() {
        MainPrefs.setAuthGuideShownAfterConnect(true)
    }

    /** 初始化流程中尝试连接 OpenClaw，约 6 秒后若未连接则设置「不是必须」提示 */
    fun runInitOpenClawCheck() {
        _initOpenClawMessage.value = null
        connectGateway()
        viewModelScope.launch {
            delay(6000L)
            when (gatewayConnectionState.value) {
                is GatewayConnection.ConnectionState.Connected -> _initOpenClawMessage.value = null
                else -> _initOpenClawMessage.value = "OpenClaw 连接失败，不是必须（可稍后在设置中重试）"
            }
        }
    }

    fun clearInitOpenClawMessage() {
        _initOpenClawMessage.value = null
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val message: String) : UiState()
        data class Error(val message: String) : UiState()
        data class NeedInstallInputMethod(val message: String) : UiState()
        data class NeedEnableInputMethod(val message: String) : UiState()
    }
}
