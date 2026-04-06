package com.example.clawpaw.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.clawpaw.R
import com.example.clawpaw.data.api.RetrofitClient
import com.example.clawpaw.data.storage.AppPrefs
import com.example.clawpaw.data.CalendarHelper
import com.example.clawpaw.data.ContactsHelper
import com.example.clawpaw.data.FileReadHelper
import com.example.clawpaw.data.NotificationsHelper
import com.example.clawpaw.data.PhotosHelper
import com.example.clawpaw.data.SmsHelper
import com.example.clawpaw.gateway.GatewayConnection
import com.example.clawpaw.gateway.GatewayProtocol
import com.example.clawpaw.hardware.BluetoothHelper
import com.example.clawpaw.hardware.CameraCaptureService
import com.example.clawpaw.hardware.HardwareHelper
import com.example.clawpaw.hardware.SensorsHelper
import com.example.clawpaw.hardware.VolumeHelper
import com.example.clawpaw.presentation.MainActivity
import com.example.clawpaw.state.DeviceHealthHelper
import com.example.clawpaw.state.DevicePermissionsHelper
import com.example.clawpaw.state.PhoneStateHelper
import com.example.clawpaw.state.WifiHelper
import com.example.clawpaw.hardware.MotionHelper
import com.example.clawpaw.hardware.PhoneHelper
import com.example.clawpaw.hardware.RingerHelper
import com.example.clawpaw.ssh.SshPrefs
import com.example.clawpaw.ssh.SshTunnelManager
import org.json.JSONObject
import com.example.clawpaw.build.FlavorCommandGate
import com.example.clawpaw.util.CommandLog
import com.example.clawpaw.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 前台 Service 保活 Gateway WebSocket 连接，切到后台不断开。
 */
class GatewayConnectionService : Service() {

    companion object {
        private const val TAG = "GatewayConnectionService"
        const val EXTRA_GATEWAY_HOST = "gateway_host"
        private const val CHANNEL_ID = "gateway_ws"
        private const val NOTIFICATION_ID = 10

        private val _connectionState = MutableStateFlow<GatewayConnection.ConnectionState>(GatewayConnection.ConnectionState.Disconnected)
        val connectionState: StateFlow<GatewayConnection.ConnectionState> = _connectionState.asStateFlow()

        private val _operatorConnectionState = MutableStateFlow<GatewayConnection.ConnectionState>(GatewayConnection.ConnectionState.Disconnected)
        val operatorConnectionState: StateFlow<GatewayConnection.ConnectionState> = _operatorConnectionState.asStateFlow()

        /** Node 是否已完成握手（Gateway 已接受 connect），仅在此为 true 时 Gateway 会认为 node 在线 */
        private val _nodeHandshakeDone = MutableStateFlow(false)
        val nodeHandshakeDone: StateFlow<Boolean> = _nodeHandshakeDone.asStateFlow()

        /** Node 连接（接收 node.invoke 等），供兼容或状态展示 */
        @Volatile
        var currentConnection: GatewayConnection? = null
            private set

        /** Operator 连接（对话 chat.send / chat.history / chat.subscribe），对话页使用此连接 */
        @Volatile
        var operatorConnection: GatewayConnection? = null
            private set
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + job)
    private var gatewayConnection: GatewayConnection? = null
    private var operatorGatewayConnection: GatewayConnection? = null
    private var collectJob: Job? = null
    private var reconnectJob: Job? = null
    /** 后台周期检查：Gateway 与 SSH 断线时自动重连（解决切到后台后 delay 被系统推迟或不执行的问题） */
    private var backgroundCheckJob: Job? = null
    /** 当前 Gateway 地址，Node 断线时若为 localhost 则先检查 SSH 是否实际已断 */
    private var currentGatewayHost: String? = null
    /** 与 currentGatewayHost 成对，用于判断 onStartCommand 是否为重复启动（避免无谓 disconnect 中止对话） */
    private var lastStartedPort: Int = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val host = intent?.getStringExtra(EXTRA_GATEWAY_HOST)?.trim()?.takeIf { it.isNotBlank() }
        Logger.i(TAG, "onStartCommand 收到, host=${host?.take(50) ?: "null/空"}")
        if (host.isNullOrBlank()) {
            Logger.w(TAG, "停止 Service: Gateway 地址为空")
            stopSelf()
            return START_NOT_STICKY
        }
        AppPrefs.init(applicationContext)
        RetrofitClient.init(applicationContext)
        RetrofitClient.reloadFromPrefs()
        val port = RetrofitClient.getGatewayPort()
        // 已连接且地址未变：不要再 disconnect，否则 Operator 掉线会让 Gateway 中止正在生成的对话
        val nodeOk = _connectionState.value is GatewayConnection.ConnectionState.Connected
        val opOk = _operatorConnectionState.value is GatewayConnection.ConnectionState.Connected
        if (host == currentGatewayHost && port == lastStartedPort && nodeOk && opOk && _nodeHandshakeDone.value &&
            gatewayConnection != null && operatorGatewayConnection != null) {
            Logger.i(TAG, "已有活跃连接，跳过重建 (host:port 未变)")
            refreshGatewayForegroundNotification(getString(R.string.notification_gateway_connected))
            return START_STICKY
        }
        currentConnection = null
        operatorConnection = null
        gatewayConnection?.disconnect()
        operatorGatewayConnection?.disconnect()
        Logger.i(TAG, "创建 GatewayConnection, host=$host, port=$port（无障碍非必须，仅操作类命令需要）")
        currentGatewayHost = host
        lastStartedPort = port
        // 必须保持前台：否则息屏后进程被挂起，WebSocket ping 发不出去，约 3–5 分钟被对端/NAT 断连
        refreshGatewayForegroundNotification(getString(R.string.notification_gateway_connecting))
        val nodeAuth = RetrofitClient.getAuthForConnect("node") ?: ""
        val operatorAuth = RetrofitClient.getAuthForConnect("operator") ?: ""
        val displayName = RetrofitClient.getNodeDisplayName()
        val ctx = applicationContext
        val connection = GatewayConnection(
            gatewayUrl = host,
            gatewayPort = port,
            scope = scope,
            requestHandler = { method, params ->
                withContext(Dispatchers.Default) {
                    if (!FlavorCommandGate.isNodeInvokeAllowed(method)) {
                        return@withContext Result.failure(IllegalStateException("command_not_in_build: $method"))
                    }
                    when (method) {
                        "location_get", "location.get" -> kotlin.runCatching { JSONObject(PhoneStateHelper.getLocation(ctx)) }
                        "get_wifi_name" -> kotlin.runCatching { PhoneStateHelper.getWifiName(ctx) }
                        "get_screen_state" -> kotlin.runCatching { if (PhoneStateHelper.getScreenOn(ctx)) "on" else "off" }
                        "get_state" -> kotlin.runCatching { JSONObject(PhoneStateHelper.getStateSnapshot(ctx)) }
                        "device_status", "device.status" -> kotlin.runCatching {
                            JSONObject(PhoneStateHelper.getStateSnapshot(ctx)).apply { put("ok", true) }
                        }
                        "device_info", "device.info" -> kotlin.runCatching {
                            val name = RetrofitClient.getNodeDisplayName().trim().ifEmpty { Build.MODEL }
                            JSONObject().apply {
                                put("model", Build.MODEL)
                                put("manufacturer", Build.MANUFACTURER)
                                put("androidVersion", Build.VERSION.RELEASE)
                                put("sdkInt", Build.VERSION.SDK_INT)
                                put("displayName", name)
                            }
                        }
                        "device.health" -> kotlin.runCatching { DeviceHealthHelper.getHealth(ctx) }
                        "device.permissions" -> kotlin.runCatching { DevicePermissionsHelper.getPermissions(ctx) }
                        "vibrate" -> kotlin.runCatching {
                            HardwareHelper.vibrate(ctx, params.optLong("duration_ms", 200))
                            "ok"
                        }
                        "camera_rear" -> kotlin.runCatching {
                            val intent = Intent(ctx, CameraCaptureService::class.java).putExtra(CameraCaptureService.EXTRA_FACING, 0)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent) else ctx.startService(intent)
                            mapOf("status" to "started")
                        }
                        "camera_front" -> kotlin.runCatching {
                            val intent = Intent(ctx, CameraCaptureService::class.java).putExtra(CameraCaptureService.EXTRA_FACING, 1)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent) else ctx.startService(intent)
                            mapOf("status" to "started")
                        }
                        "screen_on" -> kotlin.runCatching {
                            if (HardwareHelper.wakeScreen(ctx)) "ok" else throw IllegalStateException("wake failed")
                        }
                        "notifications_list", "notifications.list" -> kotlin.runCatching { NotificationsHelper.getNotifications(ctx) }
                        "notifications.actions" -> kotlin.runCatching {
                            val action = params.optString("action", "dismiss")
                            val key = params.optString("key", "").takeIf { it.isNotBlank() }
                            NotificationsHelper.performActions(ctx, action, key)
                        }
                        "notification.show", "notifications.push", "system.notify" -> kotlin.runCatching {
                            Logger.d(TAG, "nodes notify 收到 params: ${params.toString().take(500)}")
                            val (title, text) = run {
                                val paramsJsonStr = params.optString("paramsJSON", "").trim()
                                if (paramsJsonStr.isNotEmpty()) {
                                    try {
                                        val inner = org.json.JSONObject(paramsJsonStr)
                                        val t = inner.optString("title", "").trim().ifEmpty {
                                            inner.optString("heading", "").trim().ifEmpty { inner.optString("subject", "").trim() }
                                        }
                                        val b = inner.optString("body", "").trim().ifEmpty {
                                            inner.optString("text", "").trim().ifEmpty {
                                                inner.optString("message", "").trim().ifEmpty { inner.optString("content", "").trim() }
                                            }
                                        }
                                        t to b
                                    } catch (_: Exception) { "" to "" }
                                } else {
                                    val t = params.optString("title", "").trim().ifEmpty {
                                        params.optString("heading", "").trim().ifEmpty { params.optString("subject", "").trim() }
                                    }
                                    val b = params.optString("text", "").trim().ifEmpty {
                                        params.optString("body", "").trim().ifEmpty {
                                            params.optString("message", "").trim().ifEmpty { params.optString("content", "").trim() }
                                        }
                                    }
                                    t to b
                                }
                            }
                            Logger.d(TAG, "nodes notify 解析结果: title=\"$title\" text=\"$text\"")
                            NotificationsHelper.showNotification(ctx, title, text)
                            org.json.JSONObject().put("ok", true)
                        }
                        "contacts.list", "contacts.search" -> kotlin.runCatching {
                            ContactsHelper.getContacts(ctx, params.optInt("limit", 500))
                        }
                        "photos_latest", "photos.latest" -> kotlin.runCatching {
                            PhotosHelper.getLatestPhotos(ctx, params.optInt("limit", 50))
                        }
                        "calendar.list", "calendar.events" -> kotlin.runCatching {
                            CalendarHelper.getEvents(ctx, params.optInt("limit", 100))
                        }
                        "volume.get" -> kotlin.runCatching { VolumeHelper.getVolumeInfo(ctx) }
                        "volume.set" -> kotlin.runCatching {
                            val stream = params.optString("stream", "media")
                            val vol = params.optInt("volume", -1)
                            if (vol < 0) throw IllegalArgumentException("volume required")
                            val ok = if (stream == "ring") VolumeHelper.setRingVolume(ctx, vol) else VolumeHelper.setMediaVolume(ctx, vol)
                            if (ok) "ok" else throw IllegalStateException("set volume failed")
                        }
                        "file.read_text" -> kotlin.runCatching {
                            val path = params.optString("path", "")
                            FileReadHelper.readText(ctx, path) ?: throw IllegalArgumentException("path required or unreadable")
                        }
                        "file.read_base64" -> kotlin.runCatching {
                            val path = params.optString("path", "")
                            FileReadHelper.readBase64(ctx, path) ?: throw IllegalArgumentException("path required or unreadable")
                        }
                        "sensors.steps" -> kotlin.runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { SensorsHelper.getStepCount(ctx) } }
                        "motion.pedometer" -> kotlin.runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { MotionHelper.getPedometer(ctx, params) } }
                        "motion.activity" -> kotlin.runCatching { MotionHelper.getActivity(ctx) }
                        "sensors.light" -> kotlin.runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { SensorsHelper.getLightLevel(ctx) } }
                        "sensors.info" -> kotlin.runCatching { SensorsHelper.getSensorsInfo(ctx) }
                        "bluetooth.list" -> kotlin.runCatching { BluetoothHelper.getBondedDevices(ctx) }
                        "wifi.info" -> kotlin.runCatching { WifiHelper.getWifiInfo(ctx) }
                        "wifi.list" -> kotlin.runCatching { WifiHelper.getWifiScanResults(ctx) }
                        "wifi.enable" -> kotlin.runCatching {
                            val on = params.optBoolean("enabled", true)
                            Logger.d(TAG, "wifi.enable 收到 params: ${params.toString().take(200)}, enabled=$on")
                            val ok = WifiHelper.setWifiEnabled(ctx, on)
                            Logger.d(TAG, "wifi.enable setWifiEnabled($on) 结果: $ok")
                            if (!ok) throw IllegalStateException("setWifiEnabled 返回 false（可能系统限制或权限不足）")
                            "ok"
                        }
                        "sms.list" -> kotlin.runCatching { SmsHelper.getInbox(ctx, params.optInt("limit", 50)) }
                        "sms.send" -> kotlin.runCatching {
                            val address = params.optString("address", "").ifEmpty { params.optString("to", "") }
                            val body = params.optString("body", "").ifEmpty { params.optString("text", "") }
                            SmsHelper.sendSms(ctx, address, body)
                        }
                        "phone.call" -> kotlin.runCatching {
                            val number = params.optString("number", "").ifEmpty { params.optString("phone", "") }
                            if (PhoneHelper.call(ctx, number)) "ok" else throw IllegalArgumentException("number required")
                        }
                        "phone.dial" -> kotlin.runCatching {
                            val number = params.optString("number", "").ifEmpty { params.optString("phone", "") }
                            if (PhoneHelper.dial(ctx, number)) "ok" else throw IllegalArgumentException("number required")
                        }
                        "ringer.get" -> kotlin.runCatching { RingerHelper.getRingerMode(ctx) }
                        "ringer.set" -> kotlin.runCatching {
                            val mode = params.optString("mode", "normal")
                            if (RingerHelper.setRingerMode(ctx, mode)) "ok" else throw IllegalArgumentException("mode required: normal|silent|vibrate")
                        }
                        "dnd.get" -> kotlin.runCatching { RingerHelper.getDndState(ctx) }
                        "dnd.set" -> kotlin.runCatching {
                            val enabled = params.optBoolean("enabled", true)
                            if (RingerHelper.setDnd(ctx, enabled)) "ok" else throw IllegalStateException("need notification policy access")
                        }
                        "camera_snap", "camera.snap" -> kotlin.runCatching {
                            val facing = params.optInt("facing", 0)
                            val intent = Intent(ctx, CameraCaptureService::class.java).putExtra(CameraCaptureService.EXTRA_FACING, facing)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent) else ctx.startService(intent)
                            mapOf("status" to "started", "facing" to facing)
                        }
                        else -> withContext(Dispatchers.Main) {
                            val s = ClawPawAccessibilityService.getInstance()
                            if (s == null) Result.failure(IllegalStateException("无障碍未就绪"))
                            else GatewayProtocol.execute(s, method, params)
                        }
                    }
                }
            },
            context = ctx,
            gatewayToken = nodeAuth.takeIf { it.isNotBlank() },
            displayName = displayName
        )
        val operatorConn = GatewayConnection(
            gatewayUrl = host,
            gatewayPort = port,
            scope = scope,
            requestHandler = { method, _ ->
                kotlin.runCatching { throw IllegalStateException("operator connection does not handle $method") }
            },
            context = ctx,
            gatewayToken = operatorAuth.takeIf { it.isNotBlank() },
            displayName = displayName,
            role = "operator",
            scopes = listOf("operator.read", "operator.write", "operator.talk.secrets")
        )
        _nodeHandshakeDone.value = false
        collectJob?.cancel()
        collectJob = scope.launch {
            launch {
                connection.connectionState.collect { state ->
                    _connectionState.value = state
                    refreshGatewayNotificationFromStates()
                    Logger.i(TAG, "node 连接状态: $state")
                    if (state is GatewayConnection.ConnectionState.Disconnected || state is GatewayConnection.ConnectionState.Error) {
                        _nodeHandshakeDone.value = false
                        val host = currentGatewayHost?.lowercase() ?: ""
                        val isLocalhost = host == "127.0.0.1" || host == "localhost"
                        if (isLocalhost) {
                            // JSch Session.isConnected 在静默断线后可能仍为 true，仅作参考；为 false 时说明 SSH 已断
                            val sshConnected = SshTunnelManager.isConnected()
                            if (!sshConnected) {
                                Logger.i(TAG, "Node 断线且 Gateway 为 localhost，SSH 未连接，可能因 SSH 断线导致")
                                SshPrefs.init(applicationContext)
                                AppPrefs.init(applicationContext)
                                if (AppPrefs.getAutoReconnectSsh() && SshPrefs.getWantedSshConnected()) {
                                    val config = SshPrefs.getConfig()
                                    val mappings = SshPrefs.getPortMappings()
                                    val reverseMappings = SshPrefs.getReversePortMappings()
                                    if (config.host.isNotBlank() && config.username.isNotBlank() && (mappings.isNotEmpty() || reverseMappings.isNotEmpty())) {
                                        withContext(Dispatchers.IO) { SshTunnelManager.connect(config, mappings, reverseMappings) }
                                        delay(2000)
                                    }
                                }
                            }
                        }
                        if (AppPrefs.getAutoReconnectNode()) {
                            reconnectJob?.cancel()
                            reconnectJob = scope.launch {
                                CommandLog.addEntry("Gateway", "5 秒后尝试重连…")
                                delay(5000)
                                Logger.i(TAG, "尝试自动重连 WS…")
                                gatewayConnection?.connect()
                                operatorGatewayConnection?.connect()
                            }
                        }
                    }
                }
            }
            launch {
                connection.handshakeDoneFlow.collect { done ->
                    _nodeHandshakeDone.value = done
                    refreshGatewayNotificationFromStates()
                    Logger.i(TAG, "node 握手完成: $done")
                    if (done) {
                        com.example.clawpaw.data.api.RetrofitClient.init(applicationContext)
                        com.example.clawpaw.data.api.RetrofitClient.reloadFromPrefs()
                        Logger.i(TAG, "node 已握手，启动 operator 连接（使用已下发的 deviceToken）")
                        operatorGatewayConnection?.connect()
                    }
                }
            }
        }
        scope.launch {
            operatorConn.connectionState.collect { s ->
                _operatorConnectionState.value = s
                refreshGatewayNotificationFromStates()
                Logger.i(TAG, "operator 连接状态: $s")
                if (s is GatewayConnection.ConnectionState.Disconnected || s is GatewayConnection.ConnectionState.Error) {
                    if (AppPrefs.getAutoReconnectNode()) {
                        reconnectJob?.cancel()
                        reconnectJob = scope.launch {
                            CommandLog.addEntry("Gateway", "5 秒后尝试重连…")
                            delay(5000)
                            Logger.i(TAG, "尝试自动重连 WS (operator)…")
                            gatewayConnection?.connect()
                            operatorGatewayConnection?.connect()
                        }
                    }
                }
            }
        }
        gatewayConnection = connection
        currentConnection = connection
        operatorGatewayConnection = operatorConn
        operatorConnection = operatorConn
        connection.connect()
        startBackgroundReconnectCheck()
        Logger.i(TAG, "node connect() 已调用，operator 将在 node 握手成功后连接")
        return START_STICKY
    }

    /** 按设置间隔检查：若 Gateway 或 SSH 已断则尝试重连，保证在后台时也能恢复 */
    private fun startBackgroundReconnectCheck() {
        backgroundCheckJob?.cancel()
        val intervalMs = AppPrefs.getReconnectCheckInterval().delayMs
        backgroundCheckJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                if (!isActive) break
                AppPrefs.init(applicationContext)
                val nodeState = _connectionState.value
                val opState = _operatorConnectionState.value
                val gatewayDown = nodeState is GatewayConnection.ConnectionState.Disconnected ||
                    nodeState is GatewayConnection.ConnectionState.Error ||
                    opState is GatewayConnection.ConnectionState.Disconnected ||
                    opState is GatewayConnection.ConnectionState.Error
                if (AppPrefs.getAutoReconnectNode() && gatewayDown && (gatewayConnection != null || operatorGatewayConnection != null)) {
                    Logger.i(TAG, "后台检查: Gateway 已断，尝试重连")
                    CommandLog.addEntry("Gateway", "后台检查触发重连…")
                    gatewayConnection?.connect()
                    operatorGatewayConnection?.connect()
                }
                if (AppPrefs.getAutoReconnectSsh()) {
                    SshPrefs.init(applicationContext)
                    if (SshPrefs.getWantedSshConnected() && !SshTunnelManager.isConnected()) {
                        val config = SshPrefs.getConfig()
                        val mappings = SshPrefs.getPortMappings()
                        val reverseMappings = SshPrefs.getReversePortMappings()
                        if (config.host.isNotBlank() && config.username.isNotBlank() && (mappings.isNotEmpty() || reverseMappings.isNotEmpty())) {
                            Logger.i(TAG, "后台检查: SSH 已断，尝试重连")
                            CommandLog.addEntry("SSH", "后台检查触发重连…")
                            withContext(Dispatchers.IO) {
                                SshTunnelManager.connect(config, mappings, reverseMappings)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        backgroundCheckJob?.cancel()
        backgroundCheckJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        collectJob?.cancel()
        collectJob = null
        currentConnection = null
        operatorConnection = null
        gatewayConnection?.disconnect()
        gatewayConnection = null
        operatorGatewayConnection?.disconnect()
        operatorGatewayConnection = null
        lastStartedPort = -1
        currentGatewayHost = null
        job.cancel()
        _connectionState.value = GatewayConnection.ConnectionState.Disconnected
        _operatorConnectionState.value = GatewayConnection.ConnectionState.Disconnected
        _nodeHandshakeDone.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Gateway WebSocket", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** 前台 Service 必须用 startForeground 刷新内容，仅 notify 在部分机型上不会替换「连接中」文案 */
    private fun refreshGatewayForegroundNotification(contentText: String) {
        val notification = buildNotification(contentText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        AppPrefs.init(applicationContext)
        if (!AppPrefs.getPersistentNotification()) {
            stopForeground(Service.STOP_FOREGROUND_DETACH)
        }
    }

    /** Node + Operator + 握手 综合状态，与主界面连接页一致后再显示「已连接」 */
    private fun refreshGatewayNotificationFromStates() {
        val node = _connectionState.value
        val op = _operatorConnectionState.value
        val handshake = _nodeHandshakeDone.value
        val text = when {
            node is GatewayConnection.ConnectionState.Error -> getString(R.string.notification_gateway_error, node.message)
            op is GatewayConnection.ConnectionState.Error -> getString(R.string.notification_gateway_error, op.message)
            node is GatewayConnection.ConnectionState.Connecting || op is GatewayConnection.ConnectionState.Connecting -> getString(R.string.notification_gateway_connecting)
            node is GatewayConnection.ConnectionState.Connected && handshake && op is GatewayConnection.ConnectionState.Connected -> getString(R.string.notification_gateway_connected)
            node is GatewayConnection.ConnectionState.Connected && handshake -> getString(R.string.notification_gateway_connecting)
            node is GatewayConnection.ConnectionState.Connected && !handshake -> getString(R.string.notification_gateway_connecting)
            node is GatewayConnection.ConnectionState.Disconnected && op is GatewayConnection.ConnectionState.Disconnected -> getString(R.string.notification_gateway_disconnected)
            else -> getString(R.string.notification_gateway_connecting)
        }
        refreshGatewayForegroundNotification(text)
    }

    /**
     * 标准通知样式，参考：
     * - pId=1582 通知小图标适配规范：必设 setSmallIcon，否则“某应用正在运行”提示
     * - pId=1581 MIUI10 通知样式：标准模板（标题/内容/小图标），不把 large icon 设为应用图标（系统会叠加）
     */
    private fun buildNotification(contentText: String): Notification {
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenClaw Gateway")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }
}
