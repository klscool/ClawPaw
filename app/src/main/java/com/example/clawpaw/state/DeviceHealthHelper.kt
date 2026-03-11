package com.example.clawpaw.state

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import org.json.JSONObject

/**
 * 设备健康状态（device.health）：内存、电池详情、电源、系统信息。
 * 与官方 node 返回格式对齐。
 */
object DeviceHealthHelper {

    fun getHealth(context: Context): JSONObject {
        val memory = getMemoryInfo(context)
        val battery = getBatteryInfo(context)
        val power = getPowerInfo(context)
        val system = getSystemInfo()
        return JSONObject().apply {
            put("memory", memory)
            put("battery", battery)
            put("power", power)
            put("system", system)
        }
    }

    private fun getMemoryInfo(context: Context): JSONObject {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return JSONObject()
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalBytes = info.totalMem
        val availBytes = info.availMem
        val usedBytes = totalBytes - availBytes
        val thresholdBytes = info.threshold
        return JSONObject().apply {
            put("pressure", if (info.lowMemory) "high" else "normal")
            put("totalRamBytes", totalBytes)
            put("availableRamBytes", availBytes)
            put("usedRamBytes", usedBytes)
            put("thresholdBytes", thresholdBytes)
            put("lowMemory", info.lowMemory)
        }
    }

    private fun getBatteryInfo(context: Context): JSONObject {
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(null, filter)
        } ?: return JSONObject()
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return JSONObject()
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val stateStr = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "unplugged"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "unplugged"
            else -> "unknown"
        }
        val plugType = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val chargingTypeStr = when (plugType) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "none"
        }
        val temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        val currentMa = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000
        } else null
        return JSONObject().apply {
            put("state", stateStr)
            put("chargingType", chargingTypeStr)
            put("temperatureC", temp)
            if (currentMa != null) put("currentMa", currentMa)
        }
    }

    private fun getPowerInfo(context: Context): JSONObject {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return JSONObject()
        val lowPower = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            pm.isPowerSaveMode
        } else false
        val dozeMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isDeviceIdleMode
        } else false
        return JSONObject().apply {
            put("dozeModeEnabled", dozeMode)
            put("lowPowerModeEnabled", lowPower)
        }
    }

    private fun getSystemInfo(): JSONObject {
        val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH
        } else ""
        return JSONObject().apply {
            put("securityPatchLevel", securityPatch)
        }
    }
}
