package com.example.clawpaw.state

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * WiFi 管理：获取当前连接信息、扫描结果（需 CHANGE_WIFI_STATE 以触发扫描，Android 6+ 需定位权限才能拿到扫描列表）。
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

    /**
     * 获取 WiFi 扫描列表（附近热点）。会先触发一次扫描再返回当前结果。返回 JSONArray，每项含 ssid、bssid、level（信号强度）、frequency、capabilities。
     * Android 6+ 需要定位权限且定位开启，否则列表可能为空。
     */
    fun getWifiScanResults(context: Context): JSONArray {
        val appContext = context.applicationContext
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return JSONArray()
        if (!wifi.isWifiEnabled) return JSONArray()
        try {
            @Suppress("DEPRECATION")
            wifi.startScan()
        } catch (_: Exception) { }
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wifi.scanResults
        } else {
            @Suppress("DEPRECATION")
            wifi.scanResults
        }
        val arr = JSONArray()
        for (result in list) {
            arr.put(JSONObject().apply {
                put("ssid", result.SSID?.toString()?.trim('"') ?: "")
                put("bssid", result.BSSID ?: "")
                put("level", result.level)
                put("frequency", result.frequency)
                put("capabilities", result.capabilities ?: "")
            })
        }
        return arr
    }

    fun setWifiEnabled(context: Context, enabled: Boolean): Boolean {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        return wifi.setWifiEnabled(enabled)
    }

    private fun intToIp(ip: Int): String = "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
}
