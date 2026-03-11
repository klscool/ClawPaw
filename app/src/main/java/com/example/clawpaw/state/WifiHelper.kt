package com.example.clawpaw.state

import android.content.Context
import android.net.wifi.WifiManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * WiFi 管理：获取当前连接信息、扫描结果（需 CHANGE_WIFI_STATE 以触发扫描）。
 */
object WifiHelper {

    fun getWifiInfo(context: Context): JSONObject {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return JSONObject().put("error", "无 WiFi 服务")
        val info = wifi.connectionInfo
        return JSONObject().apply {
            put("enabled", wifi.isWifiEnabled)
            put("ssid", info?.ssid?.toString()?.trim('"') ?: "")
            put("bssid", info?.bssid ?: "")
            put("rssi", info?.rssi ?: 0)
            put("ipAddress", wifi.connectionInfo?.ipAddress?.let { intToIp(it) } ?: "")
        }
    }

    fun setWifiEnabled(context: Context, enabled: Boolean): Boolean {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        return wifi.setWifiEnabled(enabled)
    }

    private fun intToIp(ip: Int): String = "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
}
