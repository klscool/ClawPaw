package com.example.clawpaw.http

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Base64
import com.example.clawpaw.data.CalendarHelper
import com.example.clawpaw.data.ContactsHelper
import com.example.clawpaw.data.FileReadHelper
import com.example.clawpaw.data.NotificationsHelper
import com.example.clawpaw.data.PhotosHelper
import com.example.clawpaw.data.SmsHelper
import com.example.clawpaw.data.api.RetrofitClient
import com.example.clawpaw.hardware.BluetoothHelper
import com.example.clawpaw.hardware.CameraCaptureService
import com.example.clawpaw.hardware.HardwareHelper
import com.example.clawpaw.hardware.SensorsHelper
import com.example.clawpaw.hardware.VolumeHelper
import com.example.clawpaw.service.ClawPawAccessibilityService
import com.example.clawpaw.state.PhoneStateHelper
import com.example.clawpaw.hardware.PhoneHelper
import com.example.clawpaw.hardware.RingerHelper
import com.example.clawpaw.state.WifiHelper
import com.example.clawpaw.build.FlavorCommandGate
import com.example.clawpaw.util.Logger
import fi.iki.elonen.NanoHTTPD
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * 手机端轻量 HTTP 服务，供 Tailscale 对端调用。
 * 端口 8765，接口：/api/status, /api/layout, /api/screenshot, /api/execute, /api/task
 */
class NodeHttpServer(
    port: Int = 8765,
    private val appContext: Context
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "NodeHttpServer"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: ""
        val method = session.method
        return try {
            when {
                uri.equals("/api/status", ignoreCase = true) && Method.GET == method ->
                    apiStatus()
                uri.equals("/api/layout", ignoreCase = true) && Method.GET == method ->
                    if (!FlavorCommandGate.allowsHttpLayoutAndScreenshot()) {
                        json(403, JSONObject().put("error", "not_in_build").put("hint", "layout requires full flavor"))
                    } else apiLayout()
                uri.equals("/api/screenshot", ignoreCase = true) && Method.GET == method ->
                    if (!FlavorCommandGate.allowsHttpLayoutAndScreenshot()) {
                        json(403, JSONObject().put("error", "not_in_build").put("hint", "screenshot requires full flavor"))
                    } else apiScreenshot()
                uri.equals("/api/execute", ignoreCase = true) && Method.POST == method ->
                    apiExecute(session)
                uri.equals("/api/task", ignoreCase = true) && Method.POST == method ->
                    apiTaskReserved()
                else ->
                    json(404, JSONObject().put("error", "not_found"))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "HTTP 处理异常", e)
            json(500, JSONObject().put("error", e.message ?: "internal_error"))
        }
    }

    private fun apiStatus(): Response {
        val body = JSONObject()
            .put("status", "ok")
            .put("service", "clawpaw")
            .put("endpoints", org.json.JSONObject().apply {
                put("status", "GET /api/status — 本接口，返回服务状态与命令说明")
                put("layout", "GET /api/layout — 获取当前界面布局（Base64），需无障碍")
                put("screenshot", "GET /api/screenshot — 截屏（Base64），需无障碍")
                put("execute", "POST /api/execute — 执行命令，body 为 JSON，见下方 commands")
            })
            .put("状态说明", org.json.JSONObject().apply {
                put("无障碍_界面", org.json.JSONArray().apply {
                    put("get_layout"); put("screenshot"); put("click"); put("input_text"); put("input_text_direct")
                    put("swipe"); put("long_press"); put("two_finger_swipe_same"); put("two_finger_swipe_opposite")
                    put("back"); put("open_schema")
                })
                put("状态", org.json.JSONArray().apply {
                    put("location_get"); put("get_wifi_name"); put("wifi.list"); put("get_screen_state"); put("get_state"); put("device_status"); put("device_info")
                })
                put("硬件", org.json.JSONArray().apply {
                    put("vibrate"); put("camera_rear"); put("camera_front"); put("screen_on")
                })
            })
            .put("commands", org.json.JSONArray().apply {
                // 无障碍 / 界面
                put(entry("get_layout", "获取当前界面布局（XML Base64）", """{"action":"get_layout"}"""))
                put(entry("screenshot", "截屏，返回 Base64", """{"action":"screenshot"}"""))
                put(entry("click", "点击坐标", """{"action":"click","x":500,"y":1000}"""))
                put(entry("input_text", "点击并输入（需本应用输入法）", """{"action":"input_text","x":300,"y":800,"text":"你好"}"""))
                put(entry("input_text_direct", "无障碍直接输入", """{"action":"input_text_direct","x":300,"y":800,"text":"测试"}"""))
                put(entry("swipe", "滑动", """{"action":"swipe","start_x":500,"start_y":1500,"end_x":500,"end_y":500}"""))
                put(entry("long_press", "长按 700ms", """{"action":"long_press","x":500,"y":1000}"""))
                put(entry("two_finger_swipe_same", "两指同向张开放大（部分机型可能不生效）", """{"action":"two_finger_swipe_same","start_x":500,"start_y":1200,"end_x":500,"end_y":500}"""))
                put(entry("two_finger_swipe_opposite", "两指反向（像缩小/放大照片）：并拢=缩小、张开=放大", """{"action":"two_finger_swipe_opposite","start_x":350,"start_y":900,"end_x":650,"end_y":900}""", """{"action":"two_finger_swipe_opposite","start_x":480,"start_y":900,"end_x":520,"end_y":900}"""))
                put(entry("back", "返回键", """{"action":"back"}"""))
                put(entry("open_schema", "按 schema/包名打开应用", """{"action":"open_schema","schema":"com.android.chrome"}"""))
                // 状态（不依赖无障碍）
                put(entry("location_get", "最后已知定位（需定位权限）", """{"action":"location_get"}"""))
                put(entry("device_status", "设备状态（同 get_state，带 ok:true）", """{"action":"device_status"}"""))
                put(entry("device_info", "设备信息", """{"action":"device_info"}"""))
                put(entry("get_wifi_name", "当前 WiFi 名称", """{"action":"get_wifi_name"}"""))
                put(entry("wifi.list", "附近 WiFi 列表（SSID、信号等），会先触发扫描；需定位权限", """{"action":"wifi.list"}"""))
                put(entry("get_screen_state", "屏幕亮/灭 on|off", """{"action":"get_screen_state"}"""))
                put(entry("get_state", "一次返回：定位+WiFi+屏幕+电量", """{"action":"get_state"}"""))
                // 硬件（不依赖无障碍）
                put(entry("vibrate", "震动，可选 duration_ms", """{"action":"vibrate"}""", """{"action":"vibrate","duration_ms":500}"""))
                put(entry("camera_rear", "后置拍照（异步）", """{"action":"camera_rear"}"""))
                put(entry("camera_front", "前置拍照（异步）", """{"action":"camera_front"}"""))
                put(entry("camera_snap", "拍照 facing:0 后置 1 前置", """{"action":"camera_snap","facing":0}"""))
                put(entry("screen_on", "点亮/唤醒屏幕", """{"action":"screen_on"}"""))
                put(entry("notifications_list", "通知列表", """{"action":"notifications_list"}"""))
                put(entry("photos_latest", "最近照片，可选 limit", """{"action":"photos_latest","limit":50}"""))
            })
        return json(200, body)
    }

    private fun entry(action: String, desc: String, example: String, example2: String? = null): JSONObject {
        val o = JSONObject().put("action", action).put("description", desc).put("example", example)
        if (example2 != null) o.put("example_optional", example2)
        return o
    }

    private fun apiLayout(): Response {
        val service = ClawPawAccessibilityService.getInstance()
            ?: return json(503, JSONObject().put("error", "accessibility_not_ready"))
        val layout = runBlocking {
            withContext(Dispatchers.Main) { service.getLayout() }
        }
        val base64 = Base64.encodeToString(layout.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return json(200, JSONObject().put("layout", base64))
    }

    private fun apiScreenshot(): Response {
        val service = ClawPawAccessibilityService.getInstance()
            ?: return json(503, JSONObject().put("error", "accessibility_not_ready"))
        var path: String? = null
        runBlocking {
            withContext(Dispatchers.Main) {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                    service.takeScreenshot { path = it; cont.resume(Unit) {} }
                }
            }
        }
        if (path.isNullOrBlank()) return json(500, JSONObject().put("error", "screenshot_failed"))
        if (path == "系统相册") return json(200, JSONObject().put("screenshot", "").put("note", "saved_to_gallery"))
        val file = File(path)
        if (!file.exists()) return json(500, JSONObject().put("error", "file_not_found"))
        val bytes = file.readBytes()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return json(200, JSONObject().put("screenshot", base64))
    }

    private fun apiExecute(session: IHTTPSession): Response {
        val body = parseBody(session) ?: return json(400, JSONObject().put("error", "invalid_body"))
        val action = body.optString("action", "")
        if (action.isEmpty()) return json(400, JSONObject().put("error", "action_required"))
        com.example.clawpaw.util.CommandLog.addEntry("HTTP", action)
        if (!FlavorCommandGate.isHttpExecuteAllowed(action)) {
            return json(403, JSONObject().put("error", "not_in_build").put("action", action))
        }

        // 状态 / 硬件类：不依赖无障碍，直接处理
        when (action) {
            "location_get" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val result = PhoneStateHelper.getLocation(appContext)
                return json(200, JSONObject().put("success", true).put("result", JSONObject(result)))
            }
            "get_wifi_name" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val name = PhoneStateHelper.getWifiName(appContext)
                return json(200, JSONObject().put("success", true).put("result", name))
            }
            "get_screen_state" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val on = PhoneStateHelper.getScreenOn(appContext)
                return json(200, JSONObject().put("success", true).put("result", if (on) "on" else "off"))
            }
            "get_state" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val snapshot = PhoneStateHelper.getStateSnapshot(appContext)
                return json(200, JSONObject().put("success", true).put("result", JSONObject(snapshot)))
            }
            "vibrate" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val durationMs = body.optLong("duration_ms", 200)
                HardwareHelper.vibrate(appContext, durationMs)
                return json(200, JSONObject().put("success", true).put("result", "ok"))
            }
            "camera_rear" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val intent = Intent(appContext, CameraCaptureService::class.java).putExtra(CameraCaptureService.EXTRA_FACING, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appContext.startForegroundService(intent) else appContext.startService(intent)
                return json(200, JSONObject().put("success", true).put("result", JSONObject().put("status", "started")))
            }
            "camera_front" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val intent = Intent(appContext, CameraCaptureService::class.java).putExtra(CameraCaptureService.EXTRA_FACING, 1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appContext.startForegroundService(intent) else appContext.startService(intent)
                return json(200, JSONObject().put("success", true).put("result", JSONObject().put("status", "started")))
            }
            "screen_on" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val ok = HardwareHelper.wakeScreen(appContext)
                return json(200, JSONObject().put("success", ok).put("result", if (ok) "ok" else "failed"))
            }
            "device_status" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val snapshot = PhoneStateHelper.getStateSnapshot(appContext)
                return json(200, JSONObject().put("success", true).put("result", JSONObject(snapshot).put("ok", true)))
            }
            "device_info" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val name = RetrofitClient.getNodeDisplayName().trim().ifEmpty { Build.MODEL }
                val info = JSONObject().apply {
                    put("model", Build.MODEL)
                    put("manufacturer", Build.MANUFACTURER)
                    put("androidVersion", Build.VERSION.RELEASE)
                    put("sdkInt", Build.VERSION.SDK_INT)
                    put("displayName", name)
                }
                return json(200, JSONObject().put("success", true).put("result", info))
            }
            "notifications_list" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val arr = NotificationsHelper.getNotifications(appContext)
                return json(200, JSONObject().put("success", true).put("result", arr))
            }
            "notification.show", "notifications.push", "system.notify" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val title = body.optString("title", "").trim().ifEmpty {
                    body.optString("heading", "").trim().ifEmpty { body.optString("subject", "").trim() }
                }
                val text = body.optString("text", "").trim().ifEmpty {
                    body.optString("body", "").trim().ifEmpty {
                        body.optString("message", "").trim().ifEmpty { body.optString("content", "").trim() }
                    }
                }
                NotificationsHelper.showNotification(appContext, title, text)
                return json(200, JSONObject().put("success", true).put("result", JSONObject().put("ok", true)))
            }
            "contacts.list", "contacts.search" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val limit = body.optInt("limit", 500)
                val arr = ContactsHelper.getContacts(appContext, limit)
                return json(200, JSONObject().put("success", true).put("result", arr))
            }
            "photos_latest" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val limit = body.optInt("limit", 50)
                val arr = PhotosHelper.getLatestPhotos(appContext, limit)
                return json(200, JSONObject().put("success", true).put("result", arr))
            }
            "calendar.list", "calendar.events" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val limit = body.optInt("limit", 100)
                val arr = CalendarHelper.getEvents(appContext, limit)
                return json(200, JSONObject().put("success", true).put("result", arr))
            }
            "volume.get" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val info = VolumeHelper.getVolumeInfo(appContext)
                return json(200, JSONObject().put("success", true).put("result", info))
            }
            "volume.set" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val stream = body.optString("stream", "media")
                val vol = body.optInt("volume", -1)
                if (vol < 0) return json(400, JSONObject().put("error", "volume required"))
                val ok = if (stream == "ring") VolumeHelper.setRingVolume(appContext, vol) else VolumeHelper.setMediaVolume(appContext, vol)
                return json(200, JSONObject().put("success", ok).put("result", if (ok) "ok" else "failed"))
            }
            "file.read_text" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val path = body.optString("path", "")
                val content = FileReadHelper.readText(appContext, path)
                if (content == null) return json(400, JSONObject().put("error", "path required or unreadable"))
                return json(200, JSONObject().put("success", true).put("result", content))
            }
            "file.read_base64" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val path = body.optString("path", "")
                val content = FileReadHelper.readBase64(appContext, path)
                if (content == null) return json(400, JSONObject().put("error", "path required or unreadable"))
                return json(200, JSONObject().put("success", true).put("result", content))
            }
            "sensors.steps" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val info = SensorsHelper.getStepCount(appContext)
                return json(200, JSONObject().put("success", true).put("result", info))
            }
            "sensors.light" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val info = SensorsHelper.getLightLevel(appContext)
                return json(200, JSONObject().put("success", true).put("result", info))
            }
            "sensors.info" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val info = SensorsHelper.getSensorsInfo(appContext)
                return json(200, JSONObject().put("success", true).put("result", info))
            }
            "bluetooth.list" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val arr = BluetoothHelper.getBondedDevices(appContext)
                return json(200, JSONObject().put("success", true).put("result", arr))
            }
            "wifi.info" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val info = WifiHelper.getWifiInfo(appContext)
                return json(200, JSONObject().put("success", true).put("result", info))
            }
            "wifi.list" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val arr = WifiHelper.getWifiScanResults(appContext)
                return json(200, JSONObject().put("success", true).put("result", arr))
            }
            "wifi.enable" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val enabled = body.optBoolean("enabled", true)
                WifiHelper.setWifiEnabled(appContext, enabled)
                return json(200, JSONObject().put("success", true).put("result", "ok"))
            }
            "sms.list" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val limit = body.optInt("limit", 50)
                val arr = SmsHelper.getInbox(appContext, limit)
                return json(200, JSONObject().put("success", true).put("result", arr))
            }
            "sms.send" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val address = body.optString("address", "").ifEmpty { body.optString("to", "") }
                val bodyText = body.optString("body", "").ifEmpty { body.optString("text", "") }
                SmsHelper.sendSms(appContext, address, bodyText)
                return json(200, JSONObject().put("success", true).put("result", "ok"))
            }
            "phone.call" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val number = body.optString("number", "").ifEmpty { body.optString("phone", "") }
                PhoneHelper.call(appContext, number)
                return json(200, JSONObject().put("success", true).put("result", "ok"))
            }
            "phone.dial" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val number = body.optString("number", "").ifEmpty { body.optString("phone", "") }
                PhoneHelper.dial(appContext, number)
                return json(200, JSONObject().put("success", true).put("result", "ok"))
            }
            "ringer.get" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val info = RingerHelper.getRingerMode(appContext)
                return json(200, JSONObject().put("success", true).put("result", info))
            }
            "ringer.set" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val mode = body.optString("mode", "normal")
                val ok = RingerHelper.setRingerMode(appContext, mode)
                return json(200, JSONObject().put("success", ok).put("result", if (ok) "ok" else "failed"))
            }
            "dnd.get" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val info = RingerHelper.getDndState(appContext)
                return json(200, JSONObject().put("success", true).put("result", info))
            }
            "dnd.set" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val enabled = body.optBoolean("enabled", true)
                val ok = RingerHelper.setDnd(appContext, enabled)
                return json(200, JSONObject().put("success", ok).put("result", if (ok) "ok" else "failed"))
            }
            "camera_snap" -> {
                com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(appContext, action)
                val facing = body.optInt("facing", 0)
                val intent = Intent(appContext, CameraCaptureService::class.java).putExtra(CameraCaptureService.EXTRA_FACING, facing)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appContext.startForegroundService(intent) else appContext.startService(intent)
                return json(200, JSONObject().put("success", true).put("result", JSONObject().put("status", "started").put("facing", facing)))
            }
            // 以下命令仅 Node(WebSocket) 实现，HTTP 未实现
            "device.health", "device.permissions", "motion.pedometer", "motion.activity", "notifications.actions" ->
                return json(501, JSONObject().put("success", false).put("error", "该命令仅支持 Node(WebSocket)，HTTP 未实现"))
        }

        val service = ClawPawAccessibilityService.getInstance()
            ?: return json(503, JSONObject().put("error", "accessibility_not_ready"))
        val params = JSONObject().apply {
            if (body.has("x")) put("x", body.optInt("x", 0))
            if (body.has("y")) put("y", body.optInt("y", 0))
            body.optString("text", "").takeIf { it.isNotEmpty() }?.let { put("text", it) }
            if (body.has("start_x")) put("start_x", body.optInt("start_x", 0))
            if (body.has("start_y")) put("start_y", body.optInt("start_y", 0))
            if (body.has("end_x")) put("end_x", body.optInt("end_x", 0))
            if (body.has("end_y")) put("end_y", body.optInt("end_y", 0))
            if (body.has("return_layout_after")) put("return_layout_after", body.optBoolean("return_layout_after", true))
            body.optString("schema", "").takeIf { it.isNotEmpty() }?.let { put("schema", it) }
            body.optString("uri", "").takeIf { it.isNotEmpty() }?.let { put("uri", it) }
        }
        val method = when (action) {
            "click" -> "click"
            "input_text", "input" -> "input_text"
            "input_text_direct" -> "input_text_direct"
            "swipe" -> "swipe"
            "long_press" -> "long_press"
            "two_finger_swipe_same" -> "two_finger_swipe_same"
            "two_finger_swipe_opposite" -> "two_finger_swipe_opposite"
            "back" -> "back"
            "open_schema", "open_app" -> "open_schema"
            else -> action
        }
        com.example.clawpaw.data.storage.DebugPrefs.showCommandToastIfEnabled(service.applicationContext, method)
        val result = runBlocking {
            withContext(Dispatchers.Main) {
                com.example.clawpaw.gateway.GatewayProtocol.execute(service, method, params)
            }
        }
        return result.fold(
            onSuccess = { data ->
                val out = JSONObject().put("success", true)
                when (data) {
                    is Map<*, *> -> {
                        val layout = data["layout"] as? String
                        if (!layout.isNullOrEmpty()) out.put("layout", Base64.encodeToString(layout.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                    }
                    else -> out.put("result", data.toString())
                }
                json(200, out)
            },
            onFailure = { json(200, JSONObject().put("success", false).put("error", it.message ?: "unknown")) }
        )
    }

    private fun apiTaskReserved(): Response {
        return json(200, JSONObject().put("message", "reserved"))
    }

    private fun parseBody(session: IHTTPSession): JSONObject? {
        return try {
            val len = session.headers["content-length"]?.toIntOrNull() ?: 0
            if (len !in 1..512 * 1024) return null
            val buf = ByteArray(len)
            var read = 0
            val ins = session.inputStream
            while (read < len) {
                val n = ins.read(buf, read, len - read)
                if (n <= 0) break
                read += n
            }
            val bodyStr = String(buf, 0, read, Charsets.UTF_8).trim()
            if (bodyStr.isEmpty()) return null
            JSONObject(bodyStr)
        } catch (e: Exception) {
            null
        }
    }

    private fun json(code: Int, obj: JSONObject): Response {
        return newFixedLengthResponse(
            Response.Status.lookup(code),
            "application/json; charset=utf-8",
            obj.toString()
        )
    }
}
