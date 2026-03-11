package com.example.clawpaw.service

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 通知监听服务：用户授权后可用于获取当前通知列表（notifications.list）。
 * 需在「设置 → 无障碍 / 已安装服务」或「通知使用权」中手动开启。
 */
class NotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
    }

    companion object {
        private var instance: NotificationListener? = null

        fun getInstance(): NotificationListener? = instance

        fun isEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val cn = ComponentName(context, NotificationListener::class.java)
            val ourFlatten = cn.flattenToString()
            if (flat.contains(ourFlatten)) return true
            val pkg = context.packageName
            return flat.split(":").any { ComponentName.unflattenFromString(it.trim())?.packageName == pkg }
        }

        fun getActiveNotifications(context: Context): List<StatusBarNotification>? {
            return instance?.activeNotifications?.toList()
        }

        /** 根据 key 取消一条通知（notifications.actions dismiss）。key 来自 notifications.list 返回。API 20+ */
        fun cancelNotificationByKey(key: String?): Boolean {
            if (key.isNullOrBlank()) return false
            val inst = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    inst.cancelNotification(key)
                    true
                } catch (_: Throwable) { false }
            } else false
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
