package com.example.clawpaw.state

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.clawpaw.util.Logger
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch

private const val TAG = "PhoneStateHelper"

/** WGS84 转 GCJ-02（火星坐标），用于国内地图展示。中国境外偏移量很小可忽略。 */
private fun wgs84ToGcj02(wgs84Lat: Double, wgs84Lon: Double): Pair<Double, Double> {
    val a = 6378245.0
    val ee = 0.00669342162296594323
    fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }
    fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
    val dLat = transformLat(wgs84Lon - 105.0, wgs84Lat - 35.0)
    val dLon = transformLon(wgs84Lon - 105.0, wgs84Lat - 35.0)
    val radLat = wgs84Lat / 180.0 * PI
    val magic = sin(radLat).let { 1 - ee * it * it }
    val sqrtMagic = sqrt(magic)
    val dLat2 = dLat * 180.0 / (a * (1 - ee) / (magic * sqrtMagic) * PI)
    val dLon2 = dLon * 180.0 / (a * sqrtMagic * cos(radLat) / PI)
    return Pair(wgs84Lat + dLat2, wgs84Lon + dLon2)
}

/** 将 Location 转为含 WGS84 与 GCJ-02 的 JSON 字符串。 */
private fun locationToJson(loc: Location): String {
    val (latGcj, lonGcj) = wgs84ToGcj02(loc.latitude, loc.longitude)
    return JSONObject().apply {
        put("lat", loc.latitude)
        put("lon", loc.longitude)
        put("lat_gcj", latGcj)
        put("lon_gcj", lonGcj)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && loc.hasAltitude()) put("altitude", loc.altitude)
        if (loc.hasAccuracy()) put("accuracy", loc.accuracy.toDouble())
    }.toString()
}

/**
 * 手机状态相关：定位、WiFi、屏幕、电量。
 * 与无障碍（软件操作）分离，仅做状态读取。
 */
object PhoneStateHelper {

    /** 后台请求一次最新定位时的最长等待时间（毫秒） */
    private const val FRESH_LOCATION_TIMEOUT_MS = 15_000L

    /**
     * 获取定位。需要权限 ACCESS_FINE_LOCATION 或 ACCESS_COARSE_LOCATION。
     * 在非主线程（如 Gateway/HTTP 后台）会先尝试请求一次最新定位（最多等 15 秒），失败或超时则退回「最后已知位置」。
     * 主线程调用仅返回最后已知位置，避免阻塞。Android 10+ 后台拿到最新定位需授予「始终允许」。
     * 同时返回 WGS84（lat/lon）与 GCJ-02（lat_gcj/lon_gcj）。
     * @param tryFresh 是否在后台尝试请求最新定位；主线程下会被忽略
     * @return JSON 字符串 {"lat", "lon", "lat_gcj", "lon_gcj", ...} 或 {"error": "原因"}
     */
    fun getLocation(context: Context, tryFresh: Boolean = true): String {
        val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return JSONObject().put("error", "无定位服务").toString()
        val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            return JSONObject().put("error", "需要定位权限").toString()
        }
        return try {
            val onMainThread = Looper.myLooper() == Looper.getMainLooper()
            var loc: Location? = null
            if (tryFresh && !onMainThread) {
                loc = requestFreshLocation(context)
            }
            if (loc == null) {
                loc = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: locManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            }
            if (loc != null) locationToJson(loc)
            else JSONObject().put("error", "无最后已知位置").toString()
        } catch (e: SecurityException) {
            JSONObject().put("error", "定位权限不足").toString()
        } catch (e: Exception) {
            Logger.e(TAG, "getLocation", e)
            JSONObject().put("error", e.message ?: "未知错误").toString()
        }
    }

    /**
     * 在后台通过 FusedLocationProvider 请求一次最新定位，最多等待 FRESH_LOCATION_TIMEOUT_MS。
     * 须在非主线程调用；无 Play 服务或超时则返回 null。
     */
    private fun requestFreshLocation(context: Context): Location? {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
            val tokenSource = CancellationTokenSource()
            val resultRef = AtomicReference<Location?>(null)
            val latch = CountDownLatch(1)
            Handler(Looper.getMainLooper()).postDelayed({ tokenSource.cancel() }, FRESH_LOCATION_TIMEOUT_MS)
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                .addOnCompleteListener {
                    resultRef.set(it.result)
                    latch.countDown()
                }
            latch.await(FRESH_LOCATION_TIMEOUT_MS + 1000, TimeUnit.MILLISECONDS)
            resultRef.get()
        } catch (e: Exception) {
            Logger.e(TAG, "requestFreshLocation failed", e)
            null
        }
    }

    /**
     * 当前连接的 WiFi SSID（名称）。需要 ACCESS_WIFI_STATE。
     */
    fun getWifiName(context: Context): String {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifi == null) return "无 WiFi 服务"
            val info = wifi.connectionInfo ?: return "未连接"
            var ssid = info.ssid ?: ""
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) ssid = ssid.drop(1).dropLast(1)
            if (ssid.isEmpty()) "未连接" else ssid
        } catch (e: Exception) {
            Logger.e(TAG, "getWifiName", e)
            "错误: ${e.message}"
        }
    }

    /**
     * 屏幕是否亮着（交互状态）。
     */
    fun getScreenOn(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            pm.isInteractive
        } else {
            @Suppress("DEPRECATION")
            pm.isScreenOn
        }
    }

    /**
     * 电量百分比 0–100。若无法获取返回 -1。
     */
    fun getBatteryPercent(context: Context): Int {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(null, filter)
            }
                ?: return -1
            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (scale <= 0) -1 else (level * 100 / scale).coerceIn(0, 100)
        } catch (e: Exception) {
            Logger.e(TAG, "getBatteryPercent", e)
            -1
        }
    }

    /**
     * 一次性获取常用状态，便于一次广播返回多字段。
     */
    fun getStateSnapshot(context: Context): String {
        val loc = try {
            val j = org.json.JSONObject(getLocation(context))
            if (j.has("error")) JSONObject().put("location_error", j.optString("error"))
            else j
        } catch (_: Exception) {
            JSONObject().put("location_error", "解析失败")
        }
        val dm = context.resources.displayMetrics
        return JSONObject().apply {
            put("wifi_name", getWifiName(context))
            put("screen_on", getScreenOn(context))
            put("battery_percent", getBatteryPercent(context))
            put("location", loc)
            put("screen_width", dm.widthPixels)
            put("screen_height", dm.heightPixels)
        }.toString()
    }
}
