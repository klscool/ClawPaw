package com.example.clawpaw.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.clawpaw.R
import com.example.clawpaw.service.NotificationListener
import org.json.JSONArray
import org.json.JSONObject

/**
 * 通知：获取当前通知列表（依赖 NotificationListener 已授权）、推送/显示通知。
 */
object NotificationsHelper {

    const val PUSH_CHANNEL_ID = "clawpaw_push"
    const val PUSH_NOTIFICATION_ID = 8000

    /**
     * 获取当前通知列表。需用户已在系统设置中授权「通知使用权」。
     * 来源为系统 NotificationListenerService.activeNotifications，即当前「未移除」的所有通知。
     * 部分机型（如 MIUI）会折叠/收纳通知，状态栏可见条数少于系统实际条数，故列表可能多于你在通知栏看到的。
     * @return JSONArray 每项 { packageName, title, text, postTime, key? }
     */
    fun getNotifications(context: Context): JSONArray {
        val list = NotificationListener.getActiveNotifications(context) ?: return JSONArray()
        val arr = JSONArray()
        for (sbn in list) {
            val n = sbn.notification ?: continue
            val ext = n.extras ?: continue
            arr.put(JSONObject().apply {
                put("packageName", sbn.packageName)
                put("title", ext.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: "")
                put("text", ext.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: "")
                put("postTime", sbn.postTime)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) put("key", sbn.key)
            })
        }
        return arr
    }

    /**
     * 是否已授权通知监听（可获取通知列表）。
     */
    fun isNotificationAccessEnabled(context: Context): Boolean =
        NotificationListener.isEnabled(context)

    /**
     * 推送/显示一条本地通知（需 POST_NOTIFICATIONS）。title、text 可为空；空标题时用内容首行或「通知」避免只显示应用名。
     */
    fun showNotification(context: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PUSH_CHANNEL_ID,
                "ClawPaw 推送",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val displayTitle = title.trim().ifEmpty { "通知" }
        val displayText = text.trim()
        val largeIcon = android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        val notification = NotificationCompat.Builder(context, PUSH_CHANNEL_ID)
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(PUSH_NOTIFICATION_ID, notification)
    }

    /**
     * notifications.actions：对通知执行操作（dismiss 等）。需 NotificationListener 已授权。
     * 参数：action = "dismiss"，key = 通知的 key（来自 notifications.list 返回的 key）。
     * open/reply 等未实现。
     */
    fun performActions(context: Context, action: String, key: String?): JSONObject {
        val result = JSONObject()
        when (action) {
            "dismiss", "cancel" -> {
                val ok = NotificationListener.cancelNotificationByKey(key)
                result.put("ok", ok)
                if (!ok) result.put("error", "需要通知监听权限或 key 无效")
            }
            else -> {
                result.put("ok", false)
                result.put("error", "仅支持 action=dismiss，open/reply 未实现")
            }
        }
        return result
    }
}
