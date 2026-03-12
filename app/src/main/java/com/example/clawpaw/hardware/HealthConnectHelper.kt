package com.example.clawpaw.hardware

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.clawpaw.util.Logger
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 通过系统 Health Connect 读取当日步数（自然日 0 点至今）。
 * 需用户已在 Health Connect 中授权本应用读取步数；不可用时退回传感器+基线方案。
 */
object HealthConnectHelper {

    private const val TAG = "HealthConnectHelper"

    /**
     * 尝试从 Health Connect 获取「今日 0 点至今」的步数。
     * @return 步数，不可用或未授权时返回 null
     */
    fun getTodayStepsFromHealthConnect(context: Context): Long? {
        return runBlocking {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                val granted = client.permissionController.getGrantedPermissions()
                val readSteps = androidx.health.connect.client.permission.HealthPermission.getReadPermission(StepsRecord::class)
                if (!granted.contains(readSteps)) {
                    Logger.d(TAG, "未授权读取步数")
                    return@runBlocking null
                }
                val zone = ZoneId.systemDefault()
                val startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant()
                val end = Instant.now()
                val request = AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfToday, end)
                )
                val response = client.aggregate(request)
                val total = response[StepsRecord.COUNT_TOTAL]
                Logger.d(TAG, "Health Connect 当日步数: $total")
                total
            } catch (e: Exception) {
                Logger.d(TAG, "Health Connect 读取失败: ${e.message}")
                null
            }
        }
    }
}
