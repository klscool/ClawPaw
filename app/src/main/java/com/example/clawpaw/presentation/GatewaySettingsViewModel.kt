package com.example.clawpaw.presentation

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.clawpaw.data.api.RetrofitClient
import com.example.clawpaw.data.storage.DebugPrefs
import com.example.clawpaw.data.storage.GatewayProfile
import com.example.clawpaw.data.storage.GatewayProfileStore
import com.example.clawpaw.gateway.GatewayConnection
import com.example.clawpaw.gateway.GatewayConnection.ConnectionState
import com.example.clawpaw.service.ClawPawAccessibilityService
import com.example.clawpaw.service.GatewayConnectionService
import com.example.clawpaw.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class GatewaySettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _activeProfileIndex = MutableStateFlow(GatewayProfileStore.getActiveIndex())
    val activeProfileIndex = _activeProfileIndex.asStateFlow()

    init {
        _activeProfileIndex.value = GatewayProfileStore.getActiveIndex()
    }

    val gatewayHost = RetrofitClient.serverHost.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ""
    )

    val gatewayToken = RetrofitClient.gatewayToken.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ""
    )

    val originalToken = RetrofitClient.originalToken.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ""
    )

    val nodeToken = RetrofitClient.nodeToken.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ""
    )

    val operatorToken = RetrofitClient.operatorToken.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ""
    )

    val gatewayPassword = RetrofitClient.gatewayPassword.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ""
    )

    val nodeDisplayName = RetrofitClient.nodeDisplayName.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ""
    )

    val gatewayPort = RetrofitClient.gatewayPortFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        18789
    )

    val maskedGatewayToken = gatewayToken.map { t ->
        if (t.isEmpty()) "" else "••••${t.takeLast(4)}"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val maskedGatewayPassword = gatewayPassword.map { p ->
        if (p.isEmpty()) "" else "••••${p.takeLast(2)}"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val connectionState = GatewayConnectionService.connectionState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ConnectionState.Disconnected
    )

    val nodeHandshakeDone = GatewayConnectionService.nodeHandshakeDone.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        false
    )

    val hasNodeDeviceToken = RetrofitClient.hasNodeDeviceToken.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )
    val hasOperatorDeviceToken = RetrofitClient.hasOperatorDeviceToken.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    fun updateHost(host: String) {
        RetrofitClient.setServerHost(host.trim())
    }

    fun updateToken(token: String) {
        RetrofitClient.setGatewayToken(token.trim())
    }

    fun updateOriginalToken(token: String) {
        RetrofitClient.setOriginalToken(token)
    }
    fun updateNodeToken(token: String) {
        RetrofitClient.setNodeToken(token)
    }
    fun updateOperatorToken(token: String) {
        RetrofitClient.setOperatorToken(token)
    }

    fun updatePassword(password: String) {
        RetrofitClient.setGatewayPassword(password.trim())
    }

    fun updateNodeDisplayName(name: String) {
        RetrofitClient.setNodeDisplayName(name)
    }

    fun updateGatewayPort(port: Int) {
        RetrofitClient.setGatewayPort(port)
    }

    /**
     * 切换配置槽：先把当前表单（含未点保存的编辑）写入旧槽，再加载新槽到全局。
     */
    fun switchGatewayProfile(newIndex: Int, currentForm: GatewayProfile) {
        if (newIndex !in 0..2 || newIndex == _activeProfileIndex.value) return
        val old = _activeProfileIndex.value
        GatewayProfileStore.saveSlot(old, currentForm)
        val loaded = GatewayProfileStore.loadSlot(newIndex)
        RetrofitClient.applyGatewayProfile(loaded)
        GatewayProfileStore.setActiveIndex(newIndex)
        _activeProfileIndex.value = newIndex
    }

    fun connect() {
        val host = RetrofitClient.getServerHost().trim()
        Logger.i("GatewaySettingsVM", "connect() 调用, host=\"$host\"")
        if (host.isBlank()) {
            Logger.w("GatewaySettingsVM", "连接取消: Gateway 地址为空")
            return
        }
        val intent = Intent(getApplication(), GatewayConnectionService::class.java).apply {
            putExtra(GatewayConnectionService.EXTRA_GATEWAY_HOST, host)
        }
        Logger.i("GatewaySettingsVM", "启动 GatewayConnectionService, host=$host")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    fun disconnect() {
        getApplication<Application>().stopService(
            Intent(getApplication(), GatewayConnectionService::class.java)
        )
    }

    fun clearDeviceIdentity() {
        GatewayConnection.clearStoredDeviceIdentity(getApplication())
    }

    /** 断开 Gateway，并清空地址/端口/令牌/密码及三套配置槽，回到默认。 */
    fun resetGatewayConfigurationToDefaults() {
        disconnect()
        RetrofitClient.resetGatewayToDefaults()
        _activeProfileIndex.value = GatewayProfileStore.getActiveIndex()
    }

    fun getDebugToast(): Boolean = DebugPrefs.getDebugToast()

    fun setDebugToast(enabled: Boolean) {
        DebugPrefs.setDebugToast(enabled)
        if (!enabled) DebugPrefs.cancelDebugNotification(getApplication())
    }
}
