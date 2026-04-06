package com.example.clawpaw.gateway

import android.content.Context
import android.os.Build
import android.util.Base64
import com.example.clawpaw.data.api.RetrofitClient
import com.example.clawpaw.data.storage.DebugPrefs
import com.example.clawpaw.util.CommandLog
import com.example.clawpaw.build.FlavorCommandGate
import com.example.clawpaw.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OpenClaw Gateway WebSocket 客户端。
 * 协议参考：https://docs.openclaw.ai/gateway/protocol
 * 握手：等 Gateway 发 event(connect.challenge)，再发 req(connect)，params 含 device(id/publicKey/signature/signedAt/nonce)。
 * device.id 须为公钥指纹推导的稳定身份，否则 Gateway 返回 DEVICE_AUTH_DEVICE_ID_MISMATCH。
 */
class GatewayConnection(
    private val gatewayUrl: String,
    private val gatewayPort: Int = 18789,
    private val scope: CoroutineScope,
    private val requestHandler: suspend (method: String, params: JSONObject) -> Result<Any?>,
    private val context: Context? = null,
    private val gatewayToken: String? = null,
    private val displayName: String = "",
    private val role: String = "node",
    private val scopes: List<String> = emptyList()
) {
    /** logcat 中可据此区分 node / operator 两条连接，如 adb logcat -s ClawPaw/GatewayConnection[node] */
    private val logTag get() = "$TAG[$role]"
    /** 执行日志里显示的连接名称，便于区分 Node / Operator */
    private val logSource get() = "Gateway(${if (role == "node") "Node" else "Operator"})"
    private val wsLogSource get() = "WS(${if (role == "node") "Node" else "Operator"})"

    companion object {
        private const val TAG = "GatewayConnection"
        private const val PROTOCOL_VERSION = 3
        private const val PREFS_DEVICE_IDENTITY = "clawpaw_device_identity"

        /** 清除本地保存的设备密钥，下次连接会生成新 deviceId，需在 Gateway 端重新 approve。用于解决 device identity mismatch。 */
        fun clearStoredDeviceIdentity(context: Context) {
            context.applicationContext.getSharedPreferences(PREFS_DEVICE_IDENTITY, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            Logger.i(TAG, "已清除设备身份，下次连接将生成新 deviceId")
        }

        /** 读取已保存的设备公钥并返回 deviceId（SHA256(公钥).hex），用于配对提示。未配对过返回 null。 */
        fun getStoredDeviceId(context: Context): String? {
            return try {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_DEVICE_IDENTITY, Context.MODE_PRIVATE)
                val pubB64 = prefs.getString("ed25519_public", null) ?: return null
                val pubRaw = Base64.decode(pubB64, Base64.NO_WRAP)
                if (pubRaw.size != 32) return null
                Ed25519DeviceIdentity.fingerprint(pubRaw)
            } catch (_: Exception) {
                null
            }
        }
    }

    private val okHttp = OkHttpClient.Builder()
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .writeTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        // 更短 ping：降低 NAT/蜂窝网络 idle 断连概率，减轻退后台后被掐线导致对话 aborted
        .pingInterval(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private var handshakeDone = false

    /** 握手是否已完成（Gateway 已接受 connect），仅在此为 true 时 Gateway 会认为 node 在线 */
    private val _handshakeDone = MutableStateFlow(false)
    val handshakeDoneFlow = _handshakeDone.asStateFlow()

    /** 握手成功后从 payload.snapshot.sessionDefaults.mainSessionKey 解析，chat.send / chat.history 必传 */
    @Volatile
    private var mainSessionKey: String? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    /** 我方发出的 req（如 chat.send / chat.history）等待 res 时使用 */
    private val pendingReqs = ConcurrentHashMap<String, CompletableDeferred<ChatResResult>>()

    /** Gateway 下发的 event: chat 推送，供对话页展示新消息 */
    private val _chatEvents = MutableSharedFlow<JSONObject>(replay = 0, extraBufferCapacity = 32)
    val chatEvents: SharedFlow<JSONObject> = _chatEvents.asSharedFlow()

    /** chat.send / chat.history 的 res 结果 */
    data class ChatResResult(val ok: Boolean, val payload: JSONObject?, val error: String?)

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    fun connect() {
        if (isConnected.get()) {
            Logger.w(logTag, "已连接，忽略重复连接")
            return
        }
        handshakeDone = false
        _handshakeDone.value = false
        val url = if (gatewayUrl.startsWith("ws://") || gatewayUrl.startsWith("wss://")) {
            gatewayUrl
        } else {
            "ws://${gatewayUrl.trim()}:$gatewayPort"
        }
        _connectionState.value = ConnectionState.Connecting
        CommandLog.addEntry(logSource, "连接中…")
        Logger.i(logTag, "连接 Gateway: $url")
        webSocket = okHttp.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    isConnected.set(true)
                    _connectionState.value = ConnectionState.Connected
                    CommandLog.addEntry(logSource, "已连接（等待握手）")
                    Logger.i(logTag, "WebSocket 已连接，等待 connect.challenge")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Logger.i(logTag, "连接关闭中: $code $reason")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    isConnected.set(false)
                    handshakeDone = false
                    _handshakeDone.value = false
                    mainSessionKey = null
                    _connectionState.value = ConnectionState.Disconnected
                    CommandLog.addEntry(logSource, "已断开 (code=$code $reason)")
                    Logger.i(logTag, "WebSocket 已关闭: $code $reason")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    isConnected.set(false)
                    handshakeDone = false
                    _handshakeDone.value = false
                    mainSessionKey = null
                    val msg = t.message ?: "连接失败"
                    _connectionState.value = ConnectionState.Error(msg)
                    CommandLog.addEntry(logSource, "连接失败: $msg")
                    Logger.error(logTag, "Gateway 连接失败", t)
                }
            }
        )
    }

    fun disconnect() {
        webSocket?.close(1000, "client disconnect")
        webSocket = null
        isConnected.set(false)
        handshakeDone = false
        _handshakeDone.value = false
        _connectionState.value = ConnectionState.Disconnected
        CommandLog.addEntry(logSource, "已断开（主动断开）")
        Logger.i(logTag, "已断开 Gateway")
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")
            Logger.i(logTag, "收到 WS 消息 type=$type")

            when (type) {
                "event" -> {
                    val event = json.optString("event")
                    when (event) {
                        "connect.challenge" -> {
                            val payload = json.optJSONObject("payload") ?: JSONObject()
                            val nonce = payload.optString("nonce")
                            val ts = payload.optLong("ts", System.currentTimeMillis())
                            Logger.i(logTag, "收到 connect.challenge，发送 connect 注册为 node")
                            sendConnectRequest(nonce, ts)
                            return
                        }
                        "node.invoke.request" -> {
                            val payload = json.optJSONObject("payload") ?: JSONObject()
                            val requestId = payload.opt("id")
                            val nodeId = payload.optString("nodeId")
                            val command = payload.optString("command")
                            val paramsFromPayload = when (val p = payload.opt("params")) {
                                is JSONObject -> p
                                is String -> try { JSONObject(p) } catch (_: Exception) { JSONObject() }
                                else -> JSONObject()
                            }
                            val argsFromPayload = when (val a = payload.opt("args")) {
                                is JSONObject -> a
                                is String -> try { JSONObject(a) } catch (_: Exception) { JSONObject() }
                                else -> JSONObject()
                            }
                            val params = JSONObject()
                            val reserved = setOf("id", "nodeId", "command", "params", "args", "paramsJSON")
                            paramsFromPayload.keys().asSequence().forEach { k -> params.put(k, paramsFromPayload.opt(k)) }
                            argsFromPayload.keys().asSequence().forEach { k -> params.put(k, argsFromPayload.opt(k)) }
                            payload.keys().asSequence().filter { it !in reserved }.forEach { k -> params.put(k, payload.opt(k)) }
                            val paramsJsonStr = payload.optString("paramsJSON", "").trim()
                            if (paramsJsonStr.isNotEmpty()) {
                                try {
                                    val inner = JSONObject(paramsJsonStr)
                                    inner.keys().asSequence().forEach { k -> params.put(k, inner.opt(k)) }
                                } catch (_: Exception) { }
                            }
                            if (command.isBlank()) {
                                Logger.w(logTag, "node.invoke.request 缺少 command，忽略")
                                return
                            }
                            scope.launch {
                                Logger.cmd(logTag, "执行 node.invoke: id=$requestId command=$command")
                                Logger.d(logTag, "node.invoke params: ${params.toString().take(500)}")
                                com.example.clawpaw.util.CommandLog.addEntry(wsLogSource, "执行: $command")
                                DebugPrefs.showCommandToastIfEnabled(context, command)
                                val t0 = System.currentTimeMillis()
                                val resJson = requestHandler(command, params).fold(
                                    onSuccess = { result ->
                                        Logger.i(logTag, "node.invoke $command 执行成功, 耗时 ${System.currentTimeMillis() - t0}ms")
                                        val p = when (result) {
                                            is Map<*, *> -> JSONObject(result as Map<String, Any?>)
                                            else -> result
                                        }
                                        buildNodeInvokeResult(requestId, nodeId, ok = true, payload = p)
                                    },
                                    onFailure = { e ->
                                        Logger.error(logTag, "node.invoke $command 失败, 耗时 ${System.currentTimeMillis() - t0}ms", e)
                                        buildNodeInvokeResult(requestId, nodeId, ok = false, error = e.message ?: "执行失败")
                                    }
                                )
                                val body = resJson.toString()
                                val paramsObj = resJson.optJSONObject("params")
                                val ok = paramsObj?.optBoolean("ok") == true
                                Logger.i(logTag, "node.invoke.result 准备回传: id=$requestId ok=$ok bodyLen=${body.length}")
                                if (!ok) {
                                    val errMsg = paramsObj?.optJSONObject("error")?.optString("message") ?: paramsObj?.optString("error") ?: "未知"
                                    Logger.w(logTag, "node.invoke $command 失败原因: $errMsg")
                                }
                                try {
                                    val ws = webSocket
                                    if (ws == null) {
                                        Logger.e(logTag, "node.invoke.result 无法回传: webSocket 为 null")
                                    } else {
                                        ws.send(body)
                                        Logger.i(logTag, "node.invoke.result 已发送 id=$requestId")
                                    }
                                } catch (e: Exception) {
                                    Logger.error(logTag, "node.invoke.result 发送失败 id=$requestId", e)
                                }
                            }
                            return
                        }
                        "tick", "health" -> {
                            // 心跳/健康检查，无需处理
                            return
                        }
                        "chat" -> {
                            val payload = json.optJSONObject("payload") ?: JSONObject()
                            scope.launch { _chatEvents.emit(payload) }
                            return
                        }
                        else -> Logger.w(logTag, "忽略 event: $event")
                    }
                }
                "res" -> {
                    Logger.i(logTag, "WS res 完整: $text")
                    val idObj = json.opt("id")
                    val idStr = idObj?.toString()
                    val ok = json.optBoolean("ok", false)
                    val payload = json.optJSONObject("payload")
                    val errObj = json.optJSONObject("error")
                    val errMsg = when {
                        errObj != null -> errObj.optString("message", "").ifEmpty { errObj.toString().take(200) }
                        else -> json.optString("error", "")
                    }
                    if (!ok && idStr != null) Logger.w(logTag, "req 失败: id=$idStr error=$errMsg")
                    // 我方发出的 chat.send / chat.history 等 req 的响应
                    idStr?.let { id ->
                        pendingReqs.remove(id)?.complete(ChatResResult(ok, payload, errMsg.ifEmpty { null }))
                    }
                    if (!handshakeDone && payload != null && payload.optString("type") == "hello-ok") {
                        mainSessionKey = payload.optJSONObject("snapshot")?.optJSONObject("sessionDefaults")?.optString("mainSessionKey", null)?.takeIf { it.isNotBlank() }
                        persistDeviceTokenIfPresent(json, payload)
                        handshakeDone = true
                        _handshakeDone.value = true
                        CommandLog.addEntry(logSource, "握手完成，已注册")
                        Logger.i(logTag, "握手完成 role=$role sessionKey=${mainSessionKey?.take(8) ?: "null"}…")
                        if (role == "operator") scope.launch { sendRequest("chat.subscribe", JSONObject().put("sessionKey", mainSessionKey ?: "main")) }
                        return
                    }
                    if (!handshakeDone) {
                        Logger.i(logTag, "connect 响应: ok=$ok error=$errMsg payload=$payload")
                        if (ok) {
                            if (payload != null) {
                                mainSessionKey = payload.optJSONObject("snapshot")?.optJSONObject("sessionDefaults")?.optString("mainSessionKey", null)?.takeIf { it.isNotBlank() }
                                persistDeviceTokenIfPresent(json, payload)
                            }
                            handshakeDone = true
                            _handshakeDone.value = true
                            CommandLog.addEntry(logSource, "握手完成，已注册")
                            Logger.i(logTag, "握手完成 role=$role sessionKey=${mainSessionKey?.take(8) ?: "null"}…")
                            if (role == "operator") scope.launch { sendRequest("chat.subscribe", JSONObject().put("sessionKey", mainSessionKey ?: "main")) }
                        } else {
                            Logger.w(logTag, "connect 失败，完整响应: ${text.take(500)}")
                            handshakeDone = true
                            val details = json.optJSONObject("error")?.optJSONObject("details")
                            val reason = details?.optString("reason", "") ?: ""
                            val pairingMsg = if (errMsg.contains("pairing", ignoreCase = true) && reason == "role-upgrade") {
                                "需要批准 Operator 配对：请在主机执行 openclaw devices approve --latest 或通过 Control UI 批准本设备以对话身份连接。"
                            } else null
                            if (pairingMsg != null) _connectionState.value = ConnectionState.Error(pairingMsg)
                        }
                    }
                    if (!ok && idStr == null) {
                        Logger.w(logTag, "Gateway res 错误: id=$idObj error=$errMsg")
                    }
                    return
                }
                "req" -> {
                    scope.launch {
                        val id = json.opt("id")
                        val method = json.optString("method")
                        val params = json.optJSONObject("params") ?: JSONObject()
                        Logger.cmd(logTag, "收到 req: id=$id method=$method")
                        com.example.clawpaw.util.CommandLog.addEntry(wsLogSource, "处理请求: $method")
                        DebugPrefs.showCommandToastIfEnabled(context, method)
                        val resJson = requestHandler(method, params).fold(
                            onSuccess = { payload ->
                                val p = when (payload) {
                                    is Map<*, *> -> JSONObject(payload as Map<String, Any?>)
                                    else -> payload
                                }
                                buildRes(id, ok = true, payload = p)
                            },
                            onFailure = { e ->
                                Logger.error(logTag, "执行 $method 失败", e)
                                buildRes(id, ok = false, error = e.message ?: "执行失败")
                            }
                        )
                        webSocket?.send(resJson.toString())
                    }
                }
                else -> Logger.w(logTag, "忽略消息 type=$type 原始: ${text.take(300)}")
            }
        } catch (e: Exception) {
            Logger.error(logTag, "处理消息失败", e)
        }
    }

    /** 在 JSON 子树中查找第一个非空的 `deviceToken` 字符串（兼容 Gateway 嵌套字段差异）。 */
    private fun findNestedDeviceToken(root: JSONObject?, maxDepth: Int = 12, depth: Int = 0): String? {
        if (root == null || depth > maxDepth) return null
        root.optString("deviceToken", "").takeIf { it.isNotBlank() }?.let { return it }
        val it = root.keys()
        while (it.hasNext()) {
            val key = it.next()
            when (val v = root.opt(key)) {
                is JSONObject -> findNestedDeviceToken(v, maxDepth, depth + 1)?.let { return it }
                is JSONArray -> {
                    for (i in 0 until v.length()) {
                        val el = v.opt(i)
                        if (el is JSONObject) findNestedDeviceToken(el, maxDepth, depth + 1)?.let { return it }
                    }
                }
            }
        }
        return null
    }

    /** 若 Gateway 在 hello-ok/connect 响应中下发了 deviceToken，持久化后后续连接用设备 token 而非 bootstrapToken。res 为完整 res 消息，payload 为 res.payload（部分 Gateway 把 auth 放在 res 根）。 */
    private fun persistDeviceTokenIfPresent(res: JSONObject, payload: JSONObject) {
        fun fromAuth(obj: JSONObject?): String? = obj?.let { a ->
            a.optString("deviceToken", "").takeIf { it.isNotBlank() }
                ?: a.optString("token", "").takeIf { it.isNotBlank() }
        }
        val deviceToken = fromAuth(res.optJSONObject("auth"))
            ?: fromAuth(payload.optJSONObject("auth"))
            ?: payload.optString("deviceToken", "").takeIf { it.isNotBlank() }
            ?: payload.optJSONObject("snapshot")?.optJSONObject("auth")?.optString("deviceToken", "")?.takeIf { it.isNotBlank() }
            ?: findNestedDeviceToken(payload)
            ?: findNestedDeviceToken(res)
        if (deviceToken.isNullOrEmpty()) {
            Logger.w(
                logTag,
                "握手成功但未解析到 deviceToken。若紧接着出现 bootstrap invalid：OpenClaw 的 bootstrap 为「一次性」，验证成功即从服务端删除；" +
                    "本机若未存下 deviceToken 就只能重新扫码。请对照上一条 WS res 完整。res(前500)=${res.toString().take(500)}",
            )
            return
        }
        context?.applicationContext?.let { ctx ->
            try {
                RetrofitClient.init(ctx)
                if (role == "node") {
                    RetrofitClient.setNodeToken(deviceToken)
                    RetrofitClient.setHasNodeDeviceToken(true)
                } else {
                    RetrofitClient.setOperatorToken(deviceToken)
                    RetrofitClient.setHasOperatorDeviceToken(true)
                }
                Logger.i(logTag, "已持久化 deviceToken，后续连接将使用。deviceToken=$deviceToken")
            } catch (e: Exception) {
                Logger.e(logTag, "持久化 deviceToken 失败", e)
            }
        }
    }

    private fun sendConnectRequest(challengeNonce: String, challengeTs: Long) {
        try {
            val (deviceId, publicKeyBase64Url, signPayload) = getOrCreateEd25519Identity()
            val signedAtMs = System.currentTimeMillis()
            // OpenClaw：gateway.auth.mode=token 时 authorizeTokenAuth 只认 params.auth.token（共享密钥），
            // 与握手颁发的设备 JWT 是两条线。批准后重连需同时带「网关 token」+ auth.deviceToken；
            // v3 签名用的字符串须与 device-auth.ts resolveSignatureToken 一致：token ?? deviceToken ?? bootstrapToken。
            val trip = context?.applicationContext?.let { ctx ->
                RetrofitClient.init(ctx)
                RetrofitClient.reloadFromPrefs()
                val gatewayShared = RetrofitClient.getOriginalToken().trim().takeIf { it.isNotBlank() }
                val roleSlotRaw = (if (role == "operator") RetrofitClient.getOperatorToken() else RetrofitClient.getNodeToken()).trim()
                var roleCred = roleSlotRaw.takeIf { it.isNotBlank() } ?: gatewayToken?.trim()?.takeIf { it.isNotBlank() }
                var hasDevTok = if (role == "operator") RetrofitClient.getHasOperatorDeviceToken() else RetrofitClient.getHasNodeDeviceToken()
                if (hasDevTok && roleCred.isNullOrBlank()) {
                    Logger.w(logTag, "已标记 deviceToken 已持久化但本地无 token，清除标记（避免 connect 不带有效 auth）")
                    if (role == "operator") RetrofitClient.setHasOperatorDeviceToken(false) else RetrofitClient.setHasNodeDeviceToken(false)
                    RetrofitClient.reloadFromPrefs()
                    hasDevTok = false
                    roleCred = (if (role == "operator") RetrofitClient.getOperatorToken() else RetrofitClient.getNodeToken()).trim()
                        .takeIf { it.isNotBlank() } ?: gatewayToken?.trim()?.takeIf { it.isNotBlank() }
                }
                Triple(gatewayShared, roleCred, hasDevTok)
            } ?: Triple(null, gatewayToken?.trim()?.takeIf { it.isNotBlank() }, false)
            var gatewayShared = trip.first
            var roleCred = trip.second
            var hasDevTok = trip.third
            val passwordOnly = context?.applicationContext?.let {
                RetrofitClient.getGatewayPassword().trim().takeIf { it.isNotBlank() }
            }
            val authObj = JSONObject()
            var signToken: String? = null
            when {
                gatewayShared != null && hasDevTok && !roleCred.isNullOrBlank() -> {
                    authObj.put("token", gatewayShared)
                    authObj.put("deviceToken", roleCred)
                    signToken = gatewayShared
                }
                gatewayShared != null && !hasDevTok && !roleCred.isNullOrBlank() -> {
                    authObj.put("token", gatewayShared)
                    authObj.put("bootstrapToken", roleCred)
                    signToken = gatewayShared
                }
                gatewayShared != null && roleCred.isNullOrBlank() -> {
                    authObj.put("token", gatewayShared)
                    signToken = gatewayShared
                }
                gatewayShared == null && hasDevTok && !roleCred.isNullOrBlank() -> {
                    authObj.put("deviceToken", roleCred)
                    signToken = roleCred
                }
                gatewayShared == null && !hasDevTok && !roleCred.isNullOrBlank() -> {
                    authObj.put("token", roleCred)
                    authObj.put("bootstrapToken", roleCred)
                    signToken = roleCred
                }
            }
            if (authObj.length() == 0 && passwordOnly != null) {
                authObj.put("password", passwordOnly)
            }
            // 与官方 openclaw-android-node-apk 一致：node 用 clientMode=node，operator 用 clientMode=ui；签名 payload 与 connect  params 一致
            val clientMode = if (role == "operator") "ui" else "node"
            val payloadStr = buildDeviceAuthPayloadV3(
                deviceId = deviceId,
                clientId = "openclaw-android",
                clientMode = clientMode,
                role = role,
                scopes = scopes,
                signedAtMs = signedAtMs,
                token = signToken,
                nonce = challengeNonce,
                platform = "android",
                deviceFamily = "Android"
            )
            Logger.i(logTag, "payload(前80字符): ${payloadStr.take(80)}")
            val signatureBase64Url = signPayload(payloadStr)

            val connectId = UUID.randomUUID().toString()
            val params = JSONObject().apply {
                put("minProtocol", PROTOCOL_VERSION)
                put("maxProtocol", PROTOCOL_VERSION)
                put("client", JSONObject().apply {
                    put("id", "openclaw-android")
                    put("version", "1.0")
                    put("platform", "android")
                    put("deviceFamily", "Android")
                    put("mode", clientMode)
                    if (displayName.isNotBlank()) put("displayName", displayName)
                })
                put("role", role)
                put("scopes", org.json.JSONArray().apply { scopes.forEach { put(it) } })
                if (authObj.length() > 0) {
                    val keys = authObj.keys().asSequence().toList().sorted().joinToString(",")
                    Logger.i(logTag, "connect auth 字段: $keys (网关共享 token=${if (gatewayShared != null) "有" else "无"}, deviceToken已持久化=$hasDevTok, role 槽=${if (roleCred.isNullOrBlank()) "空" else "有"})")
                    put("auth", authObj)
                } else {
                    Logger.i(logTag, "连接使用的 token: 无（未配置持久化/Node/Operator token/密码）；若 Gateway 要求 OPENCLAW_GATEWAY_TOKEN，请在设置中填写「持久化 Token」")
                }
                put("caps", if (role == "node") FlavorCommandGate.nodeCapsJsonArray() else org.json.JSONArray())
                put("commands", if (role == "node") FlavorCommandGate.nodeCommandsJsonArray() else org.json.JSONArray())
                put("permissions", JSONObject())
                put("locale", "zh-CN")
                put("userAgent", "OpenClawAndroid/1.0 (Android ${Build.VERSION.RELEASE}; SDK ${Build.VERSION.SDK_INT})")
                put("device", JSONObject().apply {
                    put("id", deviceId)
                    put("publicKey", publicKeyBase64Url)
                    put("signature", signatureBase64Url)
                    put("signedAt", signedAtMs)
                    put("nonce", challengeNonce)
                })
            }
            val req = JSONObject().apply {
                put("type", "req")
                put("id", connectId)
                put("method", "connect")
                put("params", params)
            }
            webSocket?.send(req.toString())
            Logger.i(logTag, "已发送 connect 请求，deviceId=$deviceId (Ed25519)")
        } catch (e: Exception) {
            Logger.error(logTag, "发送 connect 失败", e)
            val msg = "握手失败: ${e.message}"
            _connectionState.value = ConnectionState.Error(msg)
            CommandLog.addEntry(logSource, "握手失败: ${e.message}")
        }
    }

    /**
     * 与官方 node (infra/device-identity.ts + gateway/client.ts) 一致：Ed25519 密钥、
     * deviceId=SHA256(rawPublicKey).hex、公钥/签名为 Base64URL。返回 (deviceId, publicKeyBase64Url, signFn)。
     */
    private fun getOrCreateEd25519Identity(): Triple<String, String, (String) -> String> {
        val ctx = context?.applicationContext
        val prefs = ctx?.getSharedPreferences(PREFS_DEVICE_IDENTITY, Context.MODE_PRIVATE)
        if (prefs != null) {
            val privB64 = prefs.getString("ed25519_private", null)
            val pubB64 = prefs.getString("ed25519_public", null)
            Logger.i(logTag, "尝试加载 Ed25519 密钥: 有private=${!privB64.isNullOrEmpty()} 有public=${!pubB64.isNullOrEmpty()}")
            if (!privB64.isNullOrEmpty() && !pubB64.isNullOrEmpty()) {
                try {
                    val privRaw = Base64.decode(privB64, Base64.NO_WRAP)
                    val pubRaw = Base64.decode(pubB64, Base64.NO_WRAP)
                    if (privRaw.size == 32 && pubRaw.size == 32) {
                        val deviceId = Ed25519DeviceIdentity.fingerprint(pubRaw)
                        val publicKeyBase64Url = Ed25519DeviceIdentity.base64UrlEncode(pubRaw)
                        Logger.i(logTag, "使用已保存的 Ed25519 设备密钥，deviceId 与首次配对一致")
                        return Triple(deviceId, publicKeyBase64Url) { msg ->
                            Ed25519DeviceIdentity.base64UrlEncode(Ed25519DeviceIdentity.sign(privRaw, msg))
                        }
                    }
                } catch (e: Exception) {
                    Logger.w(logTag, "加载 Ed25519 密钥失败，将重新生成: ${e.message}")
                }
                prefs.edit().remove("ed25519_private").remove("ed25519_public").apply()
            }
        } else {
            Logger.w(logTag, "context 为空，无法持久化密钥，本次将生成临时密钥")
        }
        val (privRaw, pubRaw) = Ed25519DeviceIdentity.generateKeyPair()
        val deviceId = Ed25519DeviceIdentity.fingerprint(pubRaw)
        val publicKeyBase64Url = Ed25519DeviceIdentity.base64UrlEncode(pubRaw)
        if (prefs != null) {
            prefs.edit()
                .putString("ed25519_private", Base64.encodeToString(privRaw, Base64.NO_WRAP))
                .putString("ed25519_public", Base64.encodeToString(pubRaw, Base64.NO_WRAP))
                .apply()
            Logger.i(logTag, "已生成并保存新 Ed25519 设备密钥")
        }
        return Triple(deviceId, publicKeyBase64Url) { msg ->
            Ed25519DeviceIdentity.base64UrlEncode(Ed25519DeviceIdentity.sign(privRaw, msg))
        }
    }

    /**
     * 与 OpenClaw Gateway device-auth.ts 一致：v3 签名 payload，用 | 连接。
     * @see <a href="https://github.com/openclaw/openclaw/blob/main/src/gateway/device-auth.ts">device-auth.ts</a>
     */
    private fun buildDeviceAuthPayloadV3(
        deviceId: String,
        clientId: String,
        clientMode: String,
        role: String,
        scopes: List<String>,
        signedAtMs: Long,
        token: String?,
        nonce: String,
        platform: String,
        deviceFamily: String
    ): String {
        val scopesStr = scopes.joinToString(",")
        val tokenStr = token?.takeIf { it.isNotBlank() } ?: ""
        val platformNorm = normalizeDeviceMetadataForAuth(platform)
        val deviceFamilyNorm = normalizeDeviceMetadataForAuth(deviceFamily)
        return listOf(
            "v3",
            deviceId,
            clientId,
            clientMode,
            role,
            scopesStr,
            signedAtMs.toString(),
            tokenStr,
            nonce,
            platformNorm,
            deviceFamilyNorm
        ).joinToString("|")
    }

    private fun normalizeDeviceMetadataForAuth(value: String?): String {
        val trimmed = value?.trim() ?: return ""
        if (trimmed.isEmpty()) return ""
        return trimmed.replace(Regex("[A-Z]")) { it.value.lowercase() }
    }

    private fun buildRes(id: Any?, ok: Boolean, payload: Any? = null, error: String? = null): JSONObject {
        val res = JSONObject().put("type", "res").put("id", id).put("ok", ok)
        if (payload != null) res.put("payload", payload)
        if (error != null) res.put("error", error)
        return res
    }

    /** 构建 node.invoke 结果并作为 req 发给 Gateway（method=node.invoke.result），params 需含 nodeId；error 须为对象。 */
    private fun buildNodeInvokeResult(requestId: Any?, nodeId: String, ok: Boolean, payload: Any? = null, error: String? = null): JSONObject {
        val params = JSONObject()
            .put("id", requestId)
            .put("nodeId", nodeId)
            .put("ok", ok)
        if (payload != null) params.put("payload", payload)
        if (error != null) params.put("error", JSONObject().put("message", error))
        return JSONObject()
            .put("type", "req")
            .put("id", UUID.randomUUID().toString())
            .put("method", "node.invoke.result")
            .put("params", params)
    }

    /** 向 Gateway 发送 req 并等待 res（如 chat.send、chat.history），超时 30 秒 */
    suspend fun sendRequest(method: String, params: JSONObject): Result<ChatResResult> = kotlin.runCatching {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<ChatResResult>()
        pendingReqs[id] = deferred
        val req = JSONObject()
            .put("type", "req")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        val ws = webSocket
        if (ws == null) {
            pendingReqs.remove(id)
            throw IllegalStateException("未连接")
        }
        ws.send(req.toString())
        Logger.i(logTag, "已发送 req: method=$method id=$id")
        withTimeoutOrNull(30_000L) { deferred.await() }
            ?: run {
                pendingReqs.remove(id)
                throw java.util.concurrent.TimeoutException("req $method 超时")
            }
    }

    /** 对话附件。协议：OpenClaw Gateway WebSocket method=chat.send，params 含 message + 可选 attachments 数组（见 schema ChatSendParamsSchema）。 */
    data class ChatAttachment(val type: String, val mimeType: String, val fileName: String, val base64: String)

    /** 发送一条对话消息。协议：chat.send，params 为 sessionKey、message、thinking、timeoutMs、idempotencyKey、可选 attachments[]（每项 type/mimeType/fileName/content base64）。 */
    suspend fun sendChatMessage(
        content: String,
        sessionKey: String? = null,
        thinkingLevel: String = "off",
        attachments: List<ChatAttachment> = emptyList()
    ): Result<ChatResResult> {
        val key = sessionKey?.trim()?.ifEmpty { null } ?: mainSessionKey ?: "main"
        val messageText = content.trim().ifEmpty { if (attachments.isNotEmpty()) "见附件。" else "" }
        if (messageText.isEmpty()) return Result.failure(IllegalArgumentException("消息与附件均为空"))
        val params = JSONObject()
            .put("sessionKey", key)
            .put("message", messageText)
            .put("thinking", thinkingLevel)
            .put("timeoutMs", 30_000)
            .put("idempotencyKey", UUID.randomUUID().toString())
        if (attachments.isNotEmpty()) {
            val arr = org.json.JSONArray()
            for (a in attachments) {
                arr.put(JSONObject().put("type", a.type).put("mimeType", a.mimeType).put("fileName", a.fileName).put("content", a.base64))
            }
            params.put("attachments", arr)
            Logger.i(logTag, "chat.send 带 ${attachments.size} 个附件: ${attachments.map { "${it.type}/${it.fileName}" }}")
        }
        return sendRequest("chat.send", params)
    }

    /** 拉取指定会话历史，sessionKey 为空时用 mainSessionKey 或 "main"。 */
    suspend fun getChatHistory(sessionKey: String? = null): Result<ChatResResult> {
        val key = sessionKey?.trim()?.ifEmpty { null } ?: mainSessionKey ?: "main"
        return sendRequest("chat.history", JSONObject().put("sessionKey", key))
    }

    /** 订阅指定会话的 chat 事件（切换会话时调用），与官方一致。 */
    suspend fun sendChatSubscribe(sessionKey: String? = null): Result<ChatResResult> {
        val key = sessionKey?.trim()?.ifEmpty { null } ?: mainSessionKey ?: "main"
        return sendRequest("chat.subscribe", JSONObject().put("sessionKey", key))
    }

    /** 拉取会话列表，与官方一致：sessions.list includeGlobal、includeUnknown、limit。 */
    suspend fun requestSessionsList(limit: Int = 200): Result<ChatResResult> =
        sendRequest("sessions.list", JSONObject().put("includeGlobal", true).put("includeUnknown", false).put("limit", limit))
}
