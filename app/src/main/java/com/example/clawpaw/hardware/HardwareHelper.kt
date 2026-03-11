package com.example.clawpaw.hardware

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.clawpaw.util.Logger

private const val TAG = "HardwareHelper"
private const val WAKE_LOCK_TAG = "clawpaw:screen_on"
private const val WAKE_HOLD_MS = 3000L

/**
 * 硬件交互：震动、摄像头等。
 * 与无障碍（软件 UI 操作）分离。
 */
object HardwareHelper {

    /**
     * 震动指定毫秒。需要 VIBRATE 权限。
     * @param durationMs 震动时长，建议 50–500
     */
    fun vibrate(context: Context, durationMs: Long = 200) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: run {
                Logger.w(TAG, "无震动器")
                return
            }
            val ms = durationMs.coerceIn(1, 5000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "vibrate", e)
        }
    }

    /**
     * 点亮/唤醒屏幕。通过短暂持有 SCREEN_BRIGHT_WAKE_LOCK 唤醒设备。
     * 需要 WAKE_LOCK 权限。
     */
    fun wakeScreen(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: run {
                    Logger.w(TAG, "无 PowerManager")
                    return false
                }
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                WAKE_LOCK_TAG
            )
            wakeLock.acquire(WAKE_HOLD_MS) // 持有一小段时间后自动 release，避免立刻灭屏
            true
        } catch (e: Exception) {
            Logger.e(TAG, "wakeScreen", e)
            false
        }
    }
}
