package com.example.clawpaw.ssh

/**
 * SSH 连接配置：地址、端口、用户名、密码；可选 SOCKS5 代理（经本地端口转发）。
 */
data class SshTunnelConfig(
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val password: String = "",
    /** SOCKS5 代理主机，空则用 127.0.0.1 */
    val proxyHost: String = "127.0.0.1",
    /** SOCKS5 代理端口，0 或负数表示不使用代理 */
    val proxyPort: Int = 0
)

/**
 * 正向端口映射：本地端口 → 远程主机:端口（经 SSH 隧道转发）。
 */
data class PortMapping(
    val localPort: Int,
    val remoteHost: String,
    val remotePort: Int
) {
    fun displayText(): String = "本地 $localPort → $remoteHost:$remotePort"
}

/**
 * 反向端口映射：SSH 服务器上的远程端口 → 本机 host:port。
 * 连接后，访问服务器上的 remotePort 即转发到本机 localHost:localPort。
 */
data class ReversePortMapping(
    val remotePort: Int,
    val localHost: String,
    val localPort: Int
) {
    fun displayText(): String = "服务器 $remotePort → 本机 $localHost:$localPort"
}
