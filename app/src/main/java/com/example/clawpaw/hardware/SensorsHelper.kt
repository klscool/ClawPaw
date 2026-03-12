package com.example.clawpaw.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.atomic.AtomicReference

/**
 * 传感器：步数（TYPE_STEP_COUNTER）、光照（TYPE_LIGHT）等。
 * 步数返回「当天」真实步数：若已有 0 点基线（由 StepsMidnightWorker 写入），则 当前累计 - 0点基线；否则退化为「当日首次读取」基线。
 */
object SensorsHelper {

    private const val PREFS_STEPS = "sensors_steps_baseline"
    private const val KEY_BASELINE_DATE = "baseline_date"
    private const val KEY_BASELINE_STEPS = "baseline_steps"
    private const val KEY_MIDNIGHT_DATE = "midnight_date"
    private const val KEY_MIDNIGHT_STEPS = "midnight_steps"

    /** 仅读传感器当前累计值，供 0 点 Worker 或内部使用。无传感器返回 0。 */
    fun readStepCountRaw(context: Context): Int {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return 0
        val stepCounter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return 0
        val result = AtomicReference<Float>(0f)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.values?.getOrNull(0)?.let { result.set(it) }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
        Thread.sleep(300)
        sm.unregisterListener(listener)
        return result.get().toInt()
    }

    fun getStepCount(context: Context): JSONObject {
        // 优先用系统 Health Connect 取当日自然日步数
        val hcSteps = HealthConnectHelper.getTodayStepsFromHealthConnect(context)
        if (hcSteps != null) {
            val totalNow = run {
                val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return@run 0
                val stepCounter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return@run 0
                readStepCountRaw(context)
            }
            return JSONObject()
                .put("steps", hcSteps.toInt())
                .put("available", true)
                .put("period", "today")
                .put("totalSinceBoot", totalNow)
                .put("dailyFromMidnight", true)
                .put("source", "health_connect")
        }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return JSONObject().put("error", "无传感器服务")
        val stepCounter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounter == null) return JSONObject().put("steps", 0).put("available", false)
        val totalNow = readStepCountRaw(context)
        val prefs = context.getSharedPreferences(PREFS_STEPS, Context.MODE_PRIVATE)
        val today = todayDateString()
        val midnightDate = prefs.getString(KEY_MIDNIGHT_DATE, null)
        val midnightSteps = prefs.getInt(KEY_MIDNIGHT_STEPS, 0)
        val stepsToday: Int
        val usedMidnightBaseline: Boolean
        if (midnightDate == today && totalNow >= midnightSteps) {
            stepsToday = totalNow - midnightSteps
            usedMidnightBaseline = true
        } else {
            // 无 0 点基线时：以「今日首次读取」为当日起点，当日步数 = 当前累计 - 该基线（不把全部累计当作每日）
            val savedDate = prefs.getString(KEY_BASELINE_DATE, null)
            val savedBaseline = prefs.getInt(KEY_BASELINE_STEPS, 0)
            if (savedDate != today) {
                stepsToday = 0
                prefs.edit().putString(KEY_BASELINE_DATE, today).putInt(KEY_BASELINE_STEPS, totalNow).apply()
            } else if (totalNow < savedBaseline) {
                stepsToday = 0
                prefs.edit().putInt(KEY_BASELINE_STEPS, totalNow).apply()
            } else {
                stepsToday = totalNow - savedBaseline
            }
            usedMidnightBaseline = false
        }
        val out = JSONObject()
            .put("steps", stepsToday)
            .put("available", true)
            .put("period", "today")
            .put("totalSinceBoot", totalNow)
            .put("dailyFromMidnight", usedMidnightBaseline)
        if (usedMidnightBaseline) out.put("source", "midnight_baseline")
        return out
    }

    /** 由 StepsMidnightWorker 在 0 点附近调用：写入当日 0 点基线，用于计算当天真实步数。 */
    fun saveMidnightBaseline(context: Context, date: String, steps: Int) {
        context.getSharedPreferences(PREFS_STEPS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MIDNIGHT_DATE, date)
            .putInt(KEY_MIDNIGHT_STEPS, steps)
            .apply()
    }

    fun todayDateString(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun getLightLevel(context: Context): JSONObject {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return JSONObject().put("error", "无传感器服务")
        val light = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (light == null) return JSONObject().put("lux", -1f).put("available", false)
        val result = AtomicReference<Float>(-1f)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.values?.getOrNull(0)?.let { result.set(it) }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, light, SensorManager.SENSOR_DELAY_NORMAL)
        Thread.sleep(200)
        sm.unregisterListener(listener)
        return JSONObject().put("lux", result.get()).put("available", true)
    }

    fun getSensorsInfo(context: Context): JSONObject {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return JSONObject().put("error", "无传感器服务")
        val list = sm.getSensorList(Sensor.TYPE_ALL)
        val arr = org.json.JSONArray()
        for (s in list.take(50)) {
            arr.put(org.json.JSONObject().apply {
                put("name", s.name)
                put("type", s.stringType)
                put("vendor", s.vendor)
            })
        }
        return JSONObject().put("sensors", arr).put("count", list.size)
    }
}
