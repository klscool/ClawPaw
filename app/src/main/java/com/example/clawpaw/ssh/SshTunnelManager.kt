package com.example.clawpaw.ssh

import com.example.clawpaw.util.Logger
import com.jcraft.jsch.JSch
import com.jcraft.jsch.ProxySOCKS5
import com.jcraft.jsch.Session

private const val TAG = "SshTunnel"

/**
 * 在后台线程建立 SSH 会话并做本地端口转发。Session 需保持连接隧道才有效。
 */
object SshTunnelManager {
    private var session: Session? = null
    private val lock = Any()

    @Volatile
    var lastError: String? = null
        private set

    fun isConnected(): Boolean = synchronized(lock) { session?.isConnected == true }

    /**
     * 连接 SSH 并建立正向 + 反向端口映射。在 IO 线程调用。
     * @return 成功返回 null，失败返回错误信息
     */
    fun connect(
        config: SshTunnelConfig,
        mappings: List<PortMapping>,
        reverseMappings: List<ReversePortMapping>
    ): String? {
        synchronized(lock) {
            if (session?.isConnected == true) {
                Logger.w(TAG, "已连接，请先断开")
                return "已连接，请先断开"
            }
        }
        if (mappings.isEmpty() && reverseMappings.isEmpty()) {
            Logger.w(TAG, "请至少添加一条正向或反向映射")
            return "请至少添加一条正向或反向映射"
        }
        lastError = null
        Logger.i(TAG, "开始连接 ${config.host}:${config.port}")
        return try {
            val jsch = JSch()
            val s = jsch.getSession(config.username, config.host, config.port).apply {
                setPassword(config.password)
                setConfig("StrictHostKeyChecking", "no")
                setConfig("ServerAliveInterval", "30") // 保活：每 30 秒发送 keepalive，避免中间设备断连
                if (config.proxyPort > 0) {
                    val proxyHost = config.proxyHost.ifEmpty { "127.0.0.1" }
                    setProxy(ProxySOCKS5(proxyHost, config.proxyPort))
                    Logger.i(TAG, "使用 SOCKS5 代理 $proxyHost:${config.proxyPort}")
                }
            }
            s.connect(15000)
            if (!s.isConnected) {
                Logger.e(TAG, "connect() 返回但 isConnected=false")
                return "连接未就绪"
            }
            synchronized(lock) { session = s }
            mappings.forEach { m ->
                s.setPortForwardingL(m.localPort, m.remoteHost, m.remotePort)
            }
            reverseMappings.forEach { m ->
                s.setPortForwardingR(m.remotePort, m.localHost, m.localPort)
            }
            Logger.i(TAG, "SSH 连接成功")
            null
        } catch (e: Exception) {
            val detail = buildString {
                append(e.message ?: e.javaClass.simpleName)
                e.cause?.message?.let { append(" ($it)") }
            }.ifEmpty { "连接失败" }
            lastError = detail
            Logger.e(TAG, "SSH 连接失败: $detail", e)
            disconnect()
            lastError
        }
    }

    fun disconnect() {
        synchronized(lock) {
            try {
                session?.disconnect()
            } catch (_: Exception) { }
            session = null
        }
        lastError = null
    }
}
