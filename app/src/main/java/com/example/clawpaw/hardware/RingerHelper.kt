package com.example.clawpaw.hardware

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import org.json.JSONObject

/**
 * 铃声/勿扰：获取与设置铃声模式（正常/静音/震动）、勿扰状态。
 * 修改铃声模式需 ACCESS_NOTIFICATION_POLICY（通知策略访问），部分机型需用户到设置中授权。
 */
object RingerHelper {

    private fun getAudioManager(context: Context): AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private fun getNotificationManager(context: Context): NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private fun ringerModeToString(mode: Int): String = when (mode) {
        AudioManager.RINGER_MODE_NORMAL -> "normal"
        AudioManager.RINGER_MODE_SILENT -> "silent"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        else -> "unknown"
    }

    fun getRingerMode(context: Context): JSONObject {
        val am = getAudioManager(context) ?: return JSONObject().put("error", "无 AudioManager")
        return JSONObject().apply {
            put("mode", ringerModeToString(am.ringerMode))
            put("ringerMode", am.ringerMode)
        }
    }

    /**
     * 设置铃声模式。mode: "normal" | "silent" | "vibrate"。
     * 需通知策略访问权限（ACCESS_NOTIFICATION_POLICY），否则可能无效。
     */
    fun setRingerMode(context: Context, mode: String): Boolean {
        val am = getAudioManager(context) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val nm = getNotificationManager(context)
            if (nm != null && !nm.isNotificationPolicyAccessGranted) return false
        }
        val ringerMode = when (mode.lowercase()) {
            "normal" -> AudioManager.RINGER_MODE_NORMAL
            "silent" -> AudioManager.RINGER_MODE_SILENT
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            else -> return false
        }
        am.ringerMode = ringerMode
        return true
    }

    /**
     * 获取勿扰（中断过滤）状态。API 23+。
     */
    fun getDndState(context: Context): JSONObject {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return JSONObject().put("enabled", false).put("filter", "unknown")
        }
        val nm = getNotificationManager(context) ?: return JSONObject().put("enabled", false)
        val filter = nm.currentInterruptionFilter
        val enabled = filter != NotificationManager.INTERRUPTION_FILTER_ALL
        val filterStr = when (filter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "all"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "none"
            else -> "unknown"
        }
        return JSONObject().apply {
            put("enabled", enabled)
            put("filter", filterStr)
        }
    }

    /**
     * 设置勿扰。enabled=true 设为仅闹钟（alarms），false 为关闭勿扰（all）。
     * 需通知策略访问权限。
     */
    fun setDnd(context: Context, enabled: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val nm = getNotificationManager(context) ?: return false
        if (!nm.isNotificationPolicyAccessGranted) return false
        nm.setInterruptionFilter(
            if (enabled) NotificationManager.INTERRUPTION_FILTER_ALARMS
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return true
    }
}
