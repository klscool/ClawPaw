package com.example.clawpaw.data.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RetrofitClient {
    private const val DEFAULT_HOST = "127.0.0.1"
    private const val PREFS_GATEWAY = "gateway_prefs"
    private const val KEY_HOST = "host"
    private const val KEY_GATEWAY_TOKEN = "gateway_token"
    private const val KEY_GATEWAY_PASSWORD = "gateway_password"
    private const val KEY_NODE_DISPLAY_NAME = "node_display_name"
    private const val KEY_GATEWAY_PORT = "gateway_port"
    private const val DEFAULT_GATEWAY_PORT = 18789
    /** 开源版本不内置默认 token，由用户在 App 内配置 */
    private const val DEFAULT_GATEWAY_TOKEN = ""

    private var prefs: android.content.SharedPreferences? = null

    private val _serverHost = MutableStateFlow(DEFAULT_HOST)
    val serverHost: StateFlow<String> = _serverHost.asStateFlow()

    private val _gatewayToken = MutableStateFlow(DEFAULT_GATEWAY_TOKEN)
    val gatewayToken: StateFlow<String> = _gatewayToken.asStateFlow()

    private val _gatewayPassword = MutableStateFlow("")
    val gatewayPassword: StateFlow<String> = _gatewayPassword.asStateFlow()

    private val _nodeDisplayName = MutableStateFlow("")
    val nodeDisplayName: StateFlow<String> = _nodeDisplayName.asStateFlow()

    private val _gatewayPort = MutableStateFlow(DEFAULT_GATEWAY_PORT)
    val gatewayPortFlow: StateFlow<Int> = _gatewayPort.asStateFlow()

    fun init(context: android.content.Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS_GATEWAY, android.content.Context.MODE_PRIVATE)
        prefs?.getString(KEY_HOST, DEFAULT_HOST)?.takeIf { it.isNotBlank() }?.let {
            _serverHost.value = it
        }
        _gatewayToken.value = prefs?.getString(KEY_GATEWAY_TOKEN, "") ?: ""
        _gatewayPassword.value = prefs?.getString(KEY_GATEWAY_PASSWORD, "") ?: ""
        _nodeDisplayName.value = prefs?.getString(KEY_NODE_DISPLAY_NAME, "")?.takeIf { it.isNotBlank() } ?: ""
        _gatewayPort.value = prefs?.getInt(KEY_GATEWAY_PORT, DEFAULT_GATEWAY_PORT) ?: DEFAULT_GATEWAY_PORT
    }

    fun getGatewayPort(): Int = _gatewayPort.value

    fun setGatewayPort(port: Int) {
        val p = port.coerceIn(1, 65535)
        _gatewayPort.value = p
        prefs?.edit()?.putInt(KEY_GATEWAY_PORT, p)?.apply()
    }

    fun setGatewayToken(token: String) {
        val t = token.trim()
        _gatewayToken.value = t
        prefs?.edit()?.putString(KEY_GATEWAY_TOKEN, t)?.apply()
    }

    /** 当前保存的 Gateway 认证 token，connect 时带上可避免 AUTH_TOKEN_MISSING。 */
    fun getGatewayToken(): String = _gatewayToken.value

    /** Gateway 密码（与 token 二选一，部分网关支持密码认证）。 */
    fun getGatewayPassword(): String = _gatewayPassword.value

    fun setGatewayPassword(password: String) {
        val p = password.trim()
        _gatewayPassword.value = p
        prefs?.edit()?.putString(KEY_GATEWAY_PASSWORD, p)?.apply()
    }

    /** 用于 connect 的认证字符串：优先 token，否则 password。 */
    fun getAuthForConnect(): String? {
        val t = _gatewayToken.value.trim()
        if (t.isNotBlank()) return t
        val p = _gatewayPassword.value.trim()
        if (p.isNotBlank()) return p
        return null
    }

    fun getServerHost(): String = _serverHost.value

    fun setServerHost(host: String) {
        val trimmed = host.trim()
        _serverHost.value = trimmed
        prefs?.edit()?.putString(KEY_HOST, trimmed)?.apply()
    }

    /** 节点显示名称，Gateway 端展示用；未配置时返回设备型号（与官方一致）。 */
    fun getNodeDisplayName(): String =
        _nodeDisplayName.value.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL

    fun setNodeDisplayName(name: String) {
        val trimmed = name.trim()
        _nodeDisplayName.value = trimmed
        prefs?.edit()?.putString(KEY_NODE_DISPLAY_NAME, trimmed)?.apply()
    }
}
