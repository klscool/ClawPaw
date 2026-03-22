package com.example.clawpaw.data.api

import com.example.clawpaw.data.storage.GatewayProfile
import com.example.clawpaw.data.storage.GatewayProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RetrofitClient {
    private const val DEFAULT_HOST = "127.0.0.1"
    private const val PREFS_GATEWAY = "gateway_prefs"
    private const val KEY_HOST = "host"
    private const val KEY_GATEWAY_TOKEN = "gateway_token"
    private const val KEY_ORIGINAL_TOKEN = "gateway_original_token"
    private const val KEY_NODE_TOKEN = "gateway_node_token"
    private const val KEY_OPERATOR_TOKEN = "gateway_operator_token"
    private const val KEY_GATEWAY_PASSWORD = "gateway_password"
    private const val KEY_NODE_DISPLAY_NAME = "node_display_name"
    private const val KEY_GATEWAY_PORT = "gateway_port"
    private const val KEY_HAS_NODE_DEVICE_TOKEN = "has_node_device_token"
    private const val KEY_HAS_OPERATOR_DEVICE_TOKEN = "has_operator_device_token"
    private const val DEFAULT_GATEWAY_PORT = 18789
    /** 开源版本不内置默认 token，由用户在 App 内配置 */
    private const val DEFAULT_GATEWAY_TOKEN = ""

    private var prefs: android.content.SharedPreferences? = null

    private val _serverHost = MutableStateFlow(DEFAULT_HOST)
    val serverHost: StateFlow<String> = _serverHost.asStateFlow()

    private val _gatewayToken = MutableStateFlow(DEFAULT_GATEWAY_TOKEN)
    val gatewayToken: StateFlow<String> = _gatewayToken.asStateFlow()

    private val _originalToken = MutableStateFlow("")
    val originalToken: StateFlow<String> = _originalToken.asStateFlow()

    private val _nodeToken = MutableStateFlow("")
    val nodeToken: StateFlow<String> = _nodeToken.asStateFlow()

    private val _operatorToken = MutableStateFlow("")
    val operatorToken: StateFlow<String> = _operatorToken.asStateFlow()

    private val _gatewayPassword = MutableStateFlow("")
    val gatewayPassword: StateFlow<String> = _gatewayPassword.asStateFlow()

    private val _nodeDisplayName = MutableStateFlow("")
    val nodeDisplayName: StateFlow<String> = _nodeDisplayName.asStateFlow()

    private val _gatewayPort = MutableStateFlow(DEFAULT_GATEWAY_PORT)
    val gatewayPortFlow: StateFlow<Int> = _gatewayPort.asStateFlow()

    private val _hasNodeDeviceToken = MutableStateFlow(false)
    val hasNodeDeviceToken: StateFlow<Boolean> = _hasNodeDeviceToken.asStateFlow()
    private val _hasOperatorDeviceToken = MutableStateFlow(false)
    val hasOperatorDeviceToken: StateFlow<Boolean> = _hasOperatorDeviceToken.asStateFlow()

    /** 批量写入配置（如切换配置槽）时不把中间状态写回当前槽 */
    private var suppressProfilePush = false

    fun init(context: android.content.Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_GATEWAY, android.content.Context.MODE_PRIVATE)
        }
        GatewayProfileStore.init(context.applicationContext)
        reloadFromPrefs()
        suppressProfilePush = true
        try {
            GatewayProfileStore.migrateIfNeeded()
        } finally {
            suppressProfilePush = false
        }
    }

    /** 当前内存中的 Gateway 配置快照（用于多槽保存）。 */
    fun toGatewayProfile(): GatewayProfile = GatewayProfile(
        host = _serverHost.value,
        port = _gatewayPort.value,
        nodeDisplayName = _nodeDisplayName.value,
        originalToken = _originalToken.value,
        nodeToken = _nodeToken.value,
        operatorToken = _operatorToken.value,
        gatewayToken = _gatewayToken.value,
        password = _gatewayPassword.value,
    )

    /** 应用某套配置到全局（连接、配对等均读此状态）。 */
    fun applyGatewayProfile(p: GatewayProfile) {
        suppressProfilePush = true
        try {
            setServerHost(p.host)
            setGatewayPort(p.port)
            setNodeDisplayName(p.nodeDisplayName)
            setOriginalToken(p.originalToken)
            setNodeToken(p.nodeToken)
            setOperatorToken(p.operatorToken)
            setGatewayToken(p.gatewayToken)
            setGatewayPassword(p.password)
            reloadFromPrefs()
        } finally {
            suppressProfilePush = false
        }
    }

    private fun pushActiveProfileSnapshot() {
        if (suppressProfilePush) return
        GatewayProfileStore.saveSlot(GatewayProfileStore.getActiveIndex(), toGatewayProfile())
    }

    /** 从 prefs 重新加载，确保读到其他连接刚写入的 deviceToken。
     * 兼容旧版：若未配置过 原/Node/Operator 任一 token 但存有 legacy gateway_token，则视为原 token 用于直连。 */
    fun reloadFromPrefs() {
        val p = prefs ?: return
        p.getString(KEY_HOST, DEFAULT_HOST)?.takeIf { it.isNotBlank() }?.let { _serverHost.value = it }
        _gatewayToken.value = p.getString(KEY_GATEWAY_TOKEN, "") ?: ""
        _originalToken.value = p.getString(KEY_ORIGINAL_TOKEN, "")?.takeIf { it.isNotBlank() } ?: ""
        _nodeToken.value = p.getString(KEY_NODE_TOKEN, "") ?: ""
        _operatorToken.value = p.getString(KEY_OPERATOR_TOKEN, "") ?: ""
        // 兼容旧版：未配置过原 token 但存有 legacy gateway_token 时视为原 token，优先直连且不传 bootstrapToken
        if (_originalToken.value.isEmpty() && _gatewayToken.value.isNotBlank()) {
            _originalToken.value = _gatewayToken.value
        }
        _gatewayPassword.value = p.getString(KEY_GATEWAY_PASSWORD, "") ?: ""
        _nodeDisplayName.value = p.getString(KEY_NODE_DISPLAY_NAME, "")?.takeIf { it.isNotBlank() } ?: ""
        _gatewayPort.value = p.getInt(KEY_GATEWAY_PORT, DEFAULT_GATEWAY_PORT)
        _hasNodeDeviceToken.value = p.getBoolean(KEY_HAS_NODE_DEVICE_TOKEN, false)
        _hasOperatorDeviceToken.value = p.getBoolean(KEY_HAS_OPERATOR_DEVICE_TOKEN, false)
    }

    fun getGatewayPort(): Int = _gatewayPort.value

    fun setGatewayPort(port: Int) {
        val p = port.coerceIn(1, 65535)
        _gatewayPort.value = p
        prefs?.edit()?.putInt(KEY_GATEWAY_PORT, p)?.apply()
        pushActiveProfileSnapshot()
    }

    fun setGatewayToken(token: String) {
        val t = token.trim()
        _gatewayToken.value = t
        prefs?.edit()?.putString(KEY_GATEWAY_TOKEN, t)?.commit()
        pushActiveProfileSnapshot()
    }

    fun setOriginalToken(token: String) {
        val t = token.trim()
        _originalToken.value = t
        prefs?.edit()?.putString(KEY_ORIGINAL_TOKEN, t)?.commit()
        pushActiveProfileSnapshot()
    }
    fun setNodeToken(token: String) {
        val t = token.trim()
        _nodeToken.value = t
        prefs?.edit()?.putString(KEY_NODE_TOKEN, t)?.commit()
        pushActiveProfileSnapshot()
    }
    fun setOperatorToken(token: String) {
        val t = token.trim()
        _operatorToken.value = t
        prefs?.edit()?.putString(KEY_OPERATOR_TOKEN, t)?.commit()
        pushActiveProfileSnapshot()
    }
    fun setHasNodeDeviceToken(has: Boolean) {
        _hasNodeDeviceToken.value = has
        prefs?.edit()?.putBoolean(KEY_HAS_NODE_DEVICE_TOKEN, has)?.commit()
    }
    fun setHasOperatorDeviceToken(has: Boolean) {
        _hasOperatorDeviceToken.value = has
        prefs?.edit()?.putBoolean(KEY_HAS_OPERATOR_DEVICE_TOKEN, has)?.commit()
    }
    fun getHasNodeDeviceToken(): Boolean = _hasNodeDeviceToken.value
    fun getHasOperatorDeviceToken(): Boolean = _hasOperatorDeviceToken.value
    fun getGatewayToken(): String = _gatewayToken.value

    /** Gateway 密码（与 token 二选一，部分网关支持密码认证）。 */
    fun getGatewayPassword(): String = _gatewayPassword.value

    fun setGatewayPassword(password: String) {
        val p = password.trim()
        _gatewayPassword.value = p
        prefs?.edit()?.putString(KEY_GATEWAY_PASSWORD, p)?.apply()
        pushActiveProfileSnapshot()
    }

    /** 用于 connect 的认证（Node 与 Operator 共用此顺序）：
     * 1. 原 Token（直连优先，有则 Node/Operator 都用它）
     * 2. 无原 Token 时按 role 用 Node Token 或 Operator Token
     * 3. 再无则用密码
     * 旧版单 token 在 reload 时已并入原 token，故原 token 直连方式仍有效。 */
    fun getAuthForConnect(role: String): String? {
        val orig = _originalToken.value.trim()
        if (orig.isNotBlank()) return orig
        val roleToken = if (role == "operator") _operatorToken.value.trim() else _nodeToken.value.trim()
        if (roleToken.isNotBlank()) return roleToken
        if (_gatewayPassword.value.trim().isNotBlank()) return _gatewayPassword.value.trim()
        return null
    }

    fun getOriginalToken(): String = _originalToken.value
    fun getNodeToken(): String = _nodeToken.value
    fun getOperatorToken(): String = _operatorToken.value

    /** 当前 getAuthForConnect 返回的是否为持久化（原）token。为 true 时 connect 只传 token 不传 bootstrapToken，避免服务端按 bootstrap 校验。 */
    fun isAuthFromPersistentToken(): Boolean = _originalToken.value.trim().isNotBlank()

    /** 一次 reload 内同时返回 connect 用的 token 与是否来自持久化，保证一致；second 为 true 时不应传 bootstrapToken。 */
    fun getAuthForConnectWithSource(role: String): Pair<String?, Boolean> {
        val orig = _originalToken.value.trim()
        if (orig.isNotBlank()) return Pair(orig, true)
        val roleToken = if (role == "operator") _operatorToken.value.trim() else _nodeToken.value.trim()
        if (roleToken.isNotBlank()) return Pair(roleToken, false)
        val pwd = _gatewayPassword.value.trim()
        if (pwd.isNotBlank()) return Pair(pwd, false)
        return Pair(null, false)
    }

    fun getServerHost(): String = _serverHost.value

    fun setServerHost(host: String) {
        val trimmed = host.trim()
        _serverHost.value = trimmed
        prefs?.edit()?.putString(KEY_HOST, trimmed)?.apply()
        pushActiveProfileSnapshot()
    }

    /** 节点显示名称，Gateway 端展示用；未配置时返回设备型号（与官方一致）。 */
    fun getNodeDisplayName(): String =
        _nodeDisplayName.value.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL

    fun setNodeDisplayName(name: String) {
        val trimmed = name.trim()
        _nodeDisplayName.value = trimmed
        prefs?.edit()?.putString(KEY_NODE_DISPLAY_NAME, trimmed)?.apply()
        pushActiveProfileSnapshot()
    }
}
