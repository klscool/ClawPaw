package com.example.clawpaw.state

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.clawpaw.util.Logger
import org.json.JSONObject

private const val TAG = "PhoneStateHelper"

/**
 * 手机状态相关：定位、WiFi、屏幕、电量。
 * 与无障碍（软件操作）分离，仅做状态读取。
 */
object PhoneStateHelper {

    /**
     * 获取最后已知定位。需要权限 ACCESS_FINE_LOCATION 或 ACCESS_COARSE_LOCATION。
     * @return JSON 字符串 {"lat": double, "lon": double} 或 {"error": "原因"}
     */
    fun getLocation(context: Context): String {
        val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return JSONObject().put("error", "无定位服务").toString()
        val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            return JSONObject().put("error", "需要定位权限").toString()
        }
        return try {
            val loc = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            if (loc != null) {
                JSONObject().apply {
                    put("lat", loc.latitude)
                    put("lon", loc.longitude)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && loc.hasAltitude()) put("altitude", loc.altitude)
                    if (loc.hasAccuracy()) put("accuracy", loc.accuracy.toDouble())
                }.toString()
            } else {
                JSONObject().put("error", "无最后已知位置").toString()
            }
        } catch (e: SecurityException) {
            JSONObject().put("error", "定位权限不足").toString()
        } catch (e: Exception) {
            Logger.e(TAG, "getLocation", e)
            JSONObject().put("error", e.message ?: "未知错误").toString()
        }
    }

    /**
     * 当前连接的 WiFi SSID（名称）。需要 ACCESS_WIFI_STATE。
     */
    fun getWifiName(context: Context): String {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifi == null) return "无 WiFi 服务"
            val info = wifi.connectionInfo ?: return "未连接"
            var ssid = info.ssid ?: ""
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) ssid = ssid.drop(1).dropLast(1)
            if (ssid.isEmpty()) "未连接" else ssid
        } catch (e: Exception) {
            Logger.e(TAG, "getWifiName", e)
            "错误: ${e.message}"
        }
    }

    /**
     * 屏幕是否亮着（交互状态）。
     */
    fun getScreenOn(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            pm.isInteractive
        } else {
            @Suppress("DEPRECATION")
            pm.isScreenOn
        }
    }

    /**
     * 电量百分比 0–100。若无法获取返回 -1。
     */
    fun getBatteryPercent(context: Context): Int {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(null, filter)
            }
                ?: return -1
            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (scale <= 0) -1 else (level * 100 / scale).coerceIn(0, 100)
        } catch (e: Exception) {
            Logger.e(TAG, "getBatteryPercent", e)
            -1
        }
    }

    /**
     * 一次性获取常用状态，便于一次广播返回多字段。
     */
    fun getStateSnapshot(context: Context): String {
        val loc = try {
            val j = org.json.JSONObject(getLocation(context))
            if (j.has("error")) JSONObject().put("location_error", j.optString("error"))
            else JSONObject().put("lat", j.optDouble("lat")).put("lon", j.optDouble("lon"))
        } catch (_: Exception) {
            JSONObject().put("location_error", "解析失败")
        }
        val dm = context.resources.displayMetrics
        return JSONObject().apply {
            put("wifi_name", getWifiName(context))
            put("screen_on", getScreenOn(context))
            put("battery_percent", getBatteryPercent(context))
            put("location", loc)
            put("screen_width", dm.widthPixels)
            put("screen_height", dm.heightPixels)
        }.toString()
    }
}
