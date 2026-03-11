package com.example.clawpaw.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.clawpaw.hardware.SensorsHelper
import com.example.clawpaw.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 每天 0 点附近执行一次：读取当前步数累计并写入「当日 0 点基线」，
 * 供 sensors.steps 计算「当天真实步数」= 当前累计 - 0点基线。
 * 执行后自动调度下一次「下一个 0 点」。
 */
class StepsMidnightWorker(
    private val context: Context,
    params: androidx.work.WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val steps = SensorsHelper.readStepCountRaw(context)
            val today = SensorsHelper.todayDateString()
            SensorsHelper.saveMidnightBaseline(context, today, steps)
            Logger.i(TAG, "0 点步数基线已记录: date=$today steps=$steps")
            scheduleNextMidnight(context)
            Result.success()
        } catch (e: Exception) {
            Logger.error(TAG, "记录 0 点步数基线失败", e)
            scheduleNextMidnight(context)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "StepsMidnightWorker"
        private const val WORK_NAME = "steps_midnight_baseline"

        /** 计算从当前时刻到「下一个 0 点」的毫秒数。 */
        fun delayUntilNextMidnightMs(): Long {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            cal.add(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            var next = cal.timeInMillis
            if (next <= now) {
                cal.add(Calendar.DAY_OF_MONTH, 1)
                next = cal.timeInMillis
            }
            return next - now
        }

        /** 调度下一次在「下一个 0 点」执行。 */
        fun scheduleNextMidnight(context: Context) {
            val delayMs = delayUntilNextMidnightMs()
            val request = OneTimeWorkRequestBuilder<StepsMidnightWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Logger.i(TAG, "已调度下次 0 点执行，${delayMs / 1000 / 60} 分钟后")
        }
    }
}
