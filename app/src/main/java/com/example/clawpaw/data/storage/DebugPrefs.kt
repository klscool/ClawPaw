package com.example.clawpaw.data.storage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.clawpaw.R
import com.example.clawpaw.presentation.MainActivity

/**
 * 调试开关：打开时每次收到执行命令会显示命令内容。
 * 前台用 Toast，后台用通知（Notification），保证切到其他应用时也能看到。
 */
object DebugPrefs {
    private const val PREFS = "clawpaw_debug"
    private const val KEY_TOAST = "debug_toast"
    private const val CHANNEL_ID = "debug_command"
    private const val NOTIFICATION_ID = 99
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** 调试 Toast 功能已移除，始终返回 false */
    fun getDebugToast(): Boolean = false
    fun setDebugToast(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_TOAST, enabled)?.apply()
    }

    /**
     * 若开启了调试，显示命令描述：前台弹 Toast，同时更新一条通知（后台也能看到）。
     * context 可为 Application/Service/Activity。
     */
    fun showCommandToastIfEnabled(context: Context?, commandDesc: String) {
        if (context == null || !getDebugToast()) return
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, "命令: $commandDesc", Toast.LENGTH_SHORT).show()
            ensureChannel(app)
            val pending = PendingIntent.getActivity(
                app, 0,
                Intent(app, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val largeIcon = android.graphics.BitmapFactory.decodeResource(app.resources, R.mipmap.ic_launcher)
            val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(largeIcon)
                .setContentTitle("命令")
                .setContentText(commandDesc)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "调试命令", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    /** 关闭调试时调用，清除「命令」通知。 */
    fun cancelDebugNotification(context: Context?) {
        context ?: return
        (context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIFICATION_ID)
    }
}
