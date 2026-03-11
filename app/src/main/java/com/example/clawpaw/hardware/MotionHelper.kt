package com.example.clawpaw.hardware

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 运动相关（motion.pedometer、motion.activity），与官方 node 返回格式对齐。
 */
object MotionHelper {

    /**
     * motion.pedometer：返回指定时间范围内的步数。
     * 若未传 start/end，默认最近 24 小时。步数来自 TYPE_STEP_COUNTER（自开机累计），
     * 无法按真实时间范围统计时返回当前传感器步数供参考。
     */
    fun getPedometer(context: Context, params: org.json.JSONObject): JSONObject {
        val now = Instant.now()
        val endIso = try {
            params.optString("endISO", "").takeIf { it.isNotBlank() }?.let { Instant.parse(it) } ?: now
        } catch (_: Exception) { now }
        val startIso = try {
            params.optString("startISO", "").takeIf { it.isNotBlank() }?.let { Instant.parse(it) }
                ?: endIso.minus(24, ChronoUnit.HOURS)
        } catch (_: Exception) { endIso.minus(24, ChronoUnit.HOURS) }
        val stepObj = SensorsHelper.getStepCount(context)
        val steps = if (stepObj.optBoolean("available", false)) stepObj.optInt("steps", 0) else 0
        return JSONObject().apply {
            put("startISO", startIso.toString())
            put("endISO", endIso.toString())
            put("steps", steps)
        }
    }

    /**
     * motion.activity：当前活动识别（静止/行走/跑步等）。
     * 完整实现需 Google Play Services Activity Recognition；此处返回与官方一致的结构，
     * 默认 isStationary=true、confidence=medium。可后续接入 Play Services 后替换。
     */
    fun getActivity(context: Context): JSONObject {
        val now = Instant.now()
        val startIso = now.minus(2, ChronoUnit.SECONDS).toString()
        val endIso = now.toString()
        val activity = JSONObject().apply {
            put("startISO", startIso)
            put("endISO", endIso)
            put("confidence", "medium")
            put("isWalking", false)
            put("isRunning", false)
            put("isCycling", false)
            put("isAutomotive", false)
            put("isStationary", true)
            put("isUnknown", false)
        }
        val arr = JSONArray().put(activity)
        return JSONObject().put("activities", arr)
    }
}
