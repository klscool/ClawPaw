package com.example.clawpaw.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.clawpaw.R
import com.example.clawpaw.http.NodeHttpServer
import com.example.clawpaw.presentation.MainActivity
import com.example.clawpaw.util.Logger

/**
 * 前台 Service 保活 HTTP 服务（端口 8765），供 Tailscale 对端调用。
 * 应用启动时由 Application 启动，退出时随进程结束。
 */
class NodeHttpService : Service() {

    companion object {
        private const val TAG = "NodeHttpService"
        private const val CHANNEL_ID = "node_http"
        private const val NOTIFICATION_ID = 2
        const val PORT = 8765
    }

    private var server: NodeHttpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 先拉成前台，避免 Android 12+ 未在规定时间内 startForeground 被系统杀进程
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        if (server == null) {
            try {
                server = NodeHttpServer(PORT, applicationContext).apply { start() }
                Logger.i(TAG, "HTTP 服务已启动 0.0.0.0:$PORT")
            } catch (e: Exception) {
                Logger.error(TAG, "HTTP 服务启动失败", e)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            server?.stop()
            server = null
            Logger.i(TAG, "HTTP 服务已停止")
        } catch (e: Exception) {
            Logger.error(TAG, "停止 HTTP 服务异常", e)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_http_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeIcon = android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_http_title))
            .setContentText(getString(R.string.notification_http_text, PORT))
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }
}
