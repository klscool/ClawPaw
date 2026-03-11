package com.example.clawpaw.state

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.clawpaw.service.NotificationListener
import org.json.JSONObject

/**
 * 设备权限状态（device.permissions）：各权限 granted/denied 及是否可再次请求（promptable）。
 * 与官方 node 返回格式对齐。
 */
object DevicePermissionsHelper {

    fun getPermissions(context: Context): JSONObject {
        val perms = JSONObject()
        perms.put("camera", permissionEntry(context, android.Manifest.permission.CAMERA))
        perms.put("microphone", permissionEntry(context, android.Manifest.permission.RECORD_AUDIO))
        perms.put("location", permissionEntry(context, android.Manifest.permission.ACCESS_FINE_LOCATION))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms.put("backgroundLocation", permissionEntry(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }
        perms.put("notificationListener", notificationListenerEntry(context))
        perms.put("notifications", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionEntry(context, android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            JSONObject().put("status", "granted").put("promptable", false)
        })
        perms.put("photos", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionEntry(context, android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            @Suppress("DEPRECATION")
            permissionEntry(context, android.Manifest.permission.READ_EXTERNAL_STORAGE)
        })
        perms.put("contacts", permissionEntry(context, android.Manifest.permission.READ_CONTACTS))
        perms.put("calendar", permissionEntry(context, android.Manifest.permission.READ_CALENDAR))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms.put("motion", permissionEntry(context, android.Manifest.permission.ACTIVITY_RECOGNITION))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.put("bluetooth", permissionEntry(context, android.Manifest.permission.BLUETOOTH_CONNECT))
        }
        val readSms = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val sendSms = ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        perms.put("sms", JSONObject().apply {
            put("status", if (readSms && sendSms) "granted" else "denied")
            put("promptable", !readSms || !sendSms)
        })
        perms.put("phone", permissionEntry(context, android.Manifest.permission.CALL_PHONE))
        perms.put("screenCapture", JSONObject().put("status", "denied").put("promptable", true))
        return JSONObject().put("permissions", perms)
    }

    private fun permissionEntry(context: Context, permission: String): JSONObject {
        val granted = ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val promptable = !granted
        return JSONObject().apply {
            put("status", if (granted) "granted" else "denied")
            put("promptable", promptable)
        }
    }

    private fun notificationListenerEntry(context: Context): JSONObject {
        val enabled = NotificationListener.isEnabled(context)
        return JSONObject().apply {
            put("status", if (enabled) "granted" else "denied")
            put("promptable", !enabled)
        }
    }
}
