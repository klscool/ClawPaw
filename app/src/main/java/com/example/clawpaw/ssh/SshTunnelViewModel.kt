package com.example.clawpaw.ssh

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.clawpaw.util.CommandLog
import com.example.clawpaw.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SshTunnelViewModel(application: Application) : AndroidViewModel(application) {

    init {
        SshPrefs.init(application)
    }

    private val _config: MutableStateFlow<SshTunnelConfig> = MutableStateFlow(SshPrefs.getConfig())
    val config: StateFlow<SshTunnelConfig> = _config.asStateFlow()

    private val _mappings: MutableStateFlow<List<PortMapping>> = MutableStateFlow(SshPrefs.getPortMappings())
    val mappings: StateFlow<List<PortMapping>> = _mappings.asStateFlow()

    private val _reverseMappings: MutableStateFlow<List<ReversePortMapping>> = MutableStateFlow(SshPrefs.getReversePortMappings())
    val reverseMappings: StateFlow<List<ReversePortMapping>> = _reverseMappings.asStateFlow()

    private val _isConnecting: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _isConnected: MutableStateFlow<Boolean> = MutableStateFlow(SshTunnelManager.isConnected())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionError: MutableStateFlow<String?> = MutableStateFlow(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    fun updateConfig(c: SshTunnelConfig) {
        _config.value = c
        SshPrefs.saveConfig(c)
    }

    fun updateMappings(list: List<PortMapping>) {
        _mappings.value = list
        SshPrefs.savePortMappings(list)
    }

    fun addMapping(m: PortMapping) {
        val list = _mappings.value.toMutableList()
        list.add(m)
        _mappings.value = list
        SshPrefs.savePortMappings(list)
    }

    fun removeMappingAt(index: Int) {
        val list = _mappings.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _mappings.value = list
            SshPrefs.savePortMappings(list)
        }
    }

    fun addReverseMapping(m: ReversePortMapping) {
        val list = _reverseMappings.value.toMutableList()
        list.add(m)
        _reverseMappings.value = list
        SshPrefs.saveReversePortMappings(list)
    }

    fun removeReverseMappingAt(index: Int) {
        val list = _reverseMappings.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _reverseMappings.value = list
            SshPrefs.saveReversePortMappings(list)
        }
    }

    fun connect() {
        val c = _config.value
        if (c.host.isBlank() || c.username.isBlank()) {
            val msg = "请填写地址和用户名"
            _connectionError.value = msg
            CommandLog.addEntry("SSH", "连接失败: $msg")
            return
        }
        val list = _mappings.value
        val reverseList = _reverseMappings.value
        if (list.isEmpty() && reverseList.isEmpty()) {
            val msg = "请至少添加一条正向或反向端口映射"
            _connectionError.value = msg
            CommandLog.addEntry("SSH", "连接失败: $msg")
            return
        }
        _connectionError.value = null
        _isConnecting.value = true
        CommandLog.addEntry("SSH", "连接中…")
        viewModelScope.launch {
            var err: String? = null
            try {
                err = withContext(Dispatchers.IO) {
                    SshTunnelManager.connect(c, list, reverseList)
                }
            } catch (e: Throwable) {
                err = (e.message ?: e.javaClass.simpleName).toString()
                Logger.e("SshTunnel", "connect 异常", e)
            }
            _isConnecting.value = false
            val connected = SshTunnelManager.isConnected()
            _isConnected.value = connected
            if (err != null) {
                _connectionError.value = err
                CommandLog.addEntry("SSH", "连接失败: $err")
                Logger.w("SshTunnel", "连接失败: $err")
            } else if (!connected) {
                val msg = SshTunnelManager.lastError ?: "连接未就绪或已断开"
                _connectionError.value = msg
                CommandLog.addEntry("SSH", "连接失败: $msg")
                Logger.w("SshTunnel", "connect 返回成功但未连接: $msg")
            } else {
                CommandLog.addEntry("SSH", "连接成功，隧道已建立")
                Logger.i("SshTunnel", "连接成功")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SshTunnelManager.disconnect() }
            _isConnected.value = false
            _connectionError.value = null
            CommandLog.addEntry("SSH", "已断开（主动断开）")
        }
    }

    fun clearError() {
        _connectionError.value = null
    }

    fun refreshConnectedState() {
        _isConnected.value = SshTunnelManager.isConnected()
    }
}
