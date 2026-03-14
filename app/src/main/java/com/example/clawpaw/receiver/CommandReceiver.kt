package com.example.clawpaw.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.clawpaw.data.CalendarHelper
import com.example.clawpaw.data.ContactsHelper
import com.example.clawpaw.data.FileReadHelper
import com.example.clawpaw.data.NotificationsHelper
import com.example.clawpaw.data.PhotosHelper
import com.example.clawpaw.data.SmsHelper
import com.example.clawpaw.data.api.RetrofitClient
import com.example.clawpaw.data.storage.DebugPrefs
import com.example.clawpaw.hardware.BluetoothHelper
import com.example.clawpaw.hardware.CameraCaptureService
import com.example.clawpaw.hardware.HardwareHelper
import com.example.clawpaw.hardware.SensorsHelper
import com.example.clawpaw.hardware.PhoneHelper
import com.example.clawpaw.hardware.RingerHelper
import com.example.clawpaw.hardware.VolumeHelper
import com.example.clawpaw.service.ClawPawAccessibilityService
import com.example.clawpaw.state.PhoneStateHelper
import com.example.clawpaw.state.WifiHelper
import com.example.clawpaw.util.CommandLog
import com.example.clawpaw.util.Logger
import org.json.JSONException
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommandReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CommandReceiver"
        const val ACTION_EXECUTE_COMMAND = "com.example.clawpaw.EXECUTE_COMMAND"
        const val EXTRA_COMMAND = "command"
        private const val RESULT_OK = Activity.RESULT_OK
        private const val RESULT_CANCELED = Activity.RESULT_CANCELED
        
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        Logger.i(TAG, "收到广播: ${intent.action}")
        Logger.i(TAG, "Intent extras: ${intent.extras?.keySet()?.joinToString()}")
        
        if (intent.action == ACTION_EXECUTE_COMMAND) {
            val commandJson = intent.getStringExtra(EXTRA_COMMAND)
            Logger.cmd(TAG, "收到命令: $commandJson")
            
            if (commandJson != null) {
                try {
                    val command: JSONObject
                    var action: String
                    val trimmed = commandJson.trim()
                    if (trimmed.startsWith("{")) {
                        command = JSONObject(trimmed)
                        action = command.optString("action", "").trim()
                        if (action.isEmpty()) {
                            showToast(context, "命令缺少 action 字段")
                            setResultCode(RESULT_CANCELED)
                            setResultData("missing_action")
                            return
                        }
                    } else {
                        // 兼容：仅传 action 名称，如 "vibrate"
                        action = trimmed
                        command = JSONObject().put("action", action)
                    }
                    // 兼容 adb/shell 导致 extras 变成 "action:long_press" 等形式
                    if (action.contains(":")) {
                        action = action.substringAfterLast(":").trim()
                    }
                    Logger.cmd(TAG, "执行命令: $action")
                    CommandLog.addEntry("ADB", action)
                    DebugPrefs.showCommandToastIfEnabled(context, action)

                    // 状态类、硬件类：不依赖无障碍，优先处理
                    when (action) {
                        "location_get" -> {
                            val result = PhoneStateHelper.getLocation(context)
                            Logger.op(TAG, "定位: $result")
                            showToast(context, result)
                            setResultCode(RESULT_OK)
                            setResultData(result)
                            return
                        }
                        "get_wifi_name" -> {
                            val name = PhoneStateHelper.getWifiName(context)
                            Logger.op(TAG, "WiFi: $name")
                            showToast(context, "WiFi: $name")
                            setResultCode(RESULT_OK)
                            setResultData(name)
                            return
                        }
                        "get_screen_state" -> {
                            val on = PhoneStateHelper.getScreenOn(context)
                            val result = if (on) "on" else "off"
                            Logger.op(TAG, "屏幕: $result")
                            showToast(context, "屏幕: $result")
                            setResultCode(RESULT_OK)
                            setResultData(result)
                            return
                        }
                        "get_state" -> {
                            val result = PhoneStateHelper.getStateSnapshot(context)
                            Logger.op(TAG, "状态: $result")
                            showToast(context, "已获取状态，见日志")
                            setResultCode(RESULT_OK)
                            setResultData(result)
                            return
                        }
                        "vibrate" -> {
                            val durationMs = command.optLong("duration_ms", 200)
                            HardwareHelper.vibrate(context, durationMs)
                            showToast(context, "震动 ${durationMs}ms")
                            setResultCode(RESULT_OK)
                            setResultData("ok")
                            return
                        }
                        "camera_rear" -> {
                            val intent = Intent(context, CameraCaptureService::class.java).putExtra(CameraCaptureService.EXTRA_FACING, 0)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            showToast(context, "后置拍照中…")
                            setResultCode(RESULT_OK)
                            setResultData("started")
                            return
                        }
                        "camera_front" -> {
                            val intent = Intent(context, CameraCaptureService::class.java).putExtra(CameraCaptureService.EXTRA_FACING, 1)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            showToast(context, "前置拍照中…")
                            setResultCode(RESULT_OK)
                            setResultData("started")
                            return
                        }
                        "screen_on" -> {
                            val ok = HardwareHelper.wakeScreen(context)
                            showToast(context, if (ok) "已点亮屏幕" else "点亮屏幕失败")
                            setResultCode(if (ok) RESULT_OK else RESULT_CANCELED)
                            setResultData(if (ok) "ok" else "failed")
                            return
                        }
                        "device_status" -> {
                            val result = PhoneStateHelper.getStateSnapshot(context)
                            val json = org.json.JSONObject(result).put("ok", true)
                            setResultCode(RESULT_OK)
                            setResultData(json.toString())
                            return
                        }
                        "device_info" -> {
                            val name = RetrofitClient.getNodeDisplayName().trim().ifEmpty { android.os.Build.MODEL }
                            val json = org.json.JSONObject().apply {
                                put("model", android.os.Build.MODEL)
                                put("manufacturer", android.os.Build.MANUFACTURER)
                                put("androidVersion", android.os.Build.VERSION.RELEASE)
                                put("sdkInt", android.os.Build.VERSION.SDK_INT)
                                put("displayName", name)
                            }
                            setResultCode(RESULT_OK)
                            setResultData(json.toString())
                            return
                        }
                        // device.health, device.permissions, motion.pedometer, motion.activity, notifications.actions 仅 Node 实现，ADB 未实现
                        "device.health", "device.permissions", "motion.pedometer", "motion.activity", "notifications.actions" -> {
                            setResultCode(RESULT_CANCELED)
                            setResultData("""{"ok":false,"error":"该命令仅支持 Node(WebSocket)，ADB 未实现"}""")
                            return
                        }
                        "notifications_list" -> {
                            val arr = NotificationsHelper.getNotifications(context)
                            setResultCode(RESULT_OK)
                            setResultData(arr.toString())
                            return
                        }
                        "notification.show", "notifications.push", "system.notify" -> {
                            val title = command.optString("title", "").trim().ifEmpty {
                                command.optString("heading", "").trim().ifEmpty { command.optString("subject", "").trim() }
                            }
                            val text = command.optString("text", "").trim().ifEmpty {
                                command.optString("body", "").trim().ifEmpty {
                                    command.optString("message", "").trim().ifEmpty { command.optString("content", "").trim() }
                                }
                            }
                            NotificationsHelper.showNotification(context, title, text)
                            setResultCode(RESULT_OK)
                            setResultData("""{"ok":true}""")
                            return
                        }
                        "contacts.list", "contacts.search" -> {
                            val limit = command.optInt("limit", 500)
                            val arr = ContactsHelper.getContacts(context, limit)
                            setResultCode(RESULT_OK)
                            setResultData(arr.toString())
                            return
                        }
                        "photos_latest" -> {
                            val limit = command.optInt("limit", 50)
                            val arr = PhotosHelper.getLatestPhotos(context, limit)
                            setResultCode(RESULT_OK)
                            setResultData(arr.toString())
                            return
                        }
                        "calendar.list", "calendar.events" -> {
                            val limit = command.optInt("limit", 100)
                            val arr = CalendarHelper.getEvents(context, limit)
                            setResultCode(RESULT_OK)
                            setResultData(arr.toString())
                            return
                        }
                        "volume.get" -> {
                            val info = VolumeHelper.getVolumeInfo(context)
                            setResultCode(RESULT_OK)
                            setResultData(info.toString())
                            return
                        }
                        "volume.set" -> {
                            val stream = command.optString("stream", "media")
                            val vol = command.optInt("volume", -1)
                            if (vol < 0) {
                                setResultCode(RESULT_CANCELED)
                                setResultData("volume required")
                                return
                            }
                            val ok = if (stream == "ring") VolumeHelper.setRingVolume(context, vol) else VolumeHelper.setMediaVolume(context, vol)
                            setResultCode(if (ok) RESULT_OK else RESULT_CANCELED)
                            setResultData(if (ok) "ok" else "failed")
                            return
                        }
                        "file.read_text" -> {
                            val path = command.optString("path", "")
                            val content = FileReadHelper.readText(context, path)
                            if (content == null) {
                                setResultCode(RESULT_CANCELED)
                                setResultData("path required or unreadable")
                                return
                            }
                            setResultCode(RESULT_OK)
                            setResultData(content)
                            return
                        }
                        "file.read_base64" -> {
                            val path = command.optString("path", "")
                            val content = FileReadHelper.readBase64(context, path)
                            if (content == null) {
                                setResultCode(RESULT_CANCELED)
                                setResultData("path required or unreadable")
                                return
                            }
                            setResultCode(RESULT_OK)
                            setResultData(content)
                            return
                        }
                        "sensors.steps" -> {
                            val info = SensorsHelper.getStepCount(context)
                            setResultCode(RESULT_OK)
                            setResultData(info.toString())
                            return
                        }
                        "sensors.light" -> {
                            val info = SensorsHelper.getLightLevel(context)
                            setResultCode(RESULT_OK)
                            setResultData(info.toString())
                            return
                        }
                        "sensors.info" -> {
                            val info = SensorsHelper.getSensorsInfo(context)
                            setResultCode(RESULT_OK)
                            setResultData(info.toString())
                            return
                        }
                        "bluetooth.list" -> {
                            val arr = BluetoothHelper.getBondedDevices(context)
                            setResultCode(RESULT_OK)
                            setResultData(arr.toString())
                            return
                        }
                        "wifi.info" -> {
                            val info = WifiHelper.getWifiInfo(context)
                            setResultCode(RESULT_OK)
                            setResultData(info.toString())
                            return
                        }
                        "wifi.list" -> {
                            val arr = WifiHelper.getWifiScanResults(context)
                            setResultCode(RESULT_OK)
                            setResultData(arr.toString())
                            return
                        }
                        "wifi.enable" -> {
                            val enabled = command.optBoolean("enabled", true)
                            WifiHelper.setWifiEnabled(context, enabled)
                            setResultCode(RESULT_OK)
                            setResultData("ok")
                            return
                        }
                        "sms.list" -> {
                            val limit = command.optInt("limit", 50)
                            val arr = SmsHelper.getInbox(context, limit)
                            setResultCode(RESULT_OK)
                            setResultData(arr.toString())
                            return
                        }
                        "sms.send" -> {
                            val address = command.optString("address", "").ifEmpty { command.optString("to", "") }
                            val body = command.optString("body", "").ifEmpty { command.optString("text", "") }
                            SmsHelper.sendSms(context, address, body)
                            setResultCode(RESULT_OK)
                            setResultData("ok")
                            return
                        }
                        "phone.call" -> {
                            val number = command.optString("number", "").ifEmpty { command.optString("phone", "") }
                            PhoneHelper.call(context, number)
                            setResultCode(RESULT_OK)
                            setResultData("ok")
                            return
                        }
                        "phone.dial" -> {
                            val number = command.optString("number", "").ifEmpty { command.optString("phone", "") }
                            PhoneHelper.dial(context, number)
                            setResultCode(RESULT_OK)
                            setResultData("ok")
                            return
                        }
                        "ringer.get" -> {
                            val info = RingerHelper.getRingerMode(context)
                            setResultCode(RESULT_OK)
                            setResultData(info.toString())
                            return
                        }
                        "ringer.set" -> {
                            val mode = command.optString("mode", "normal")
                            val ok = RingerHelper.setRingerMode(context, mode)
                            setResultCode(if (ok) RESULT_OK else RESULT_CANCELED)
                            setResultData(if (ok) "ok" else "failed")
                            return
                        }
                        "dnd.get" -> {
                            val info = RingerHelper.getDndState(context)
                            setResultCode(RESULT_OK)
                            setResultData(info.toString())
                            return
                        }
                        "dnd.set" -> {
                            val enabled = command.optBoolean("enabled", true)
                            val ok = RingerHelper.setDnd(context, enabled)
                            setResultCode(if (ok) RESULT_OK else RESULT_CANCELED)
                            setResultData(if (ok) "ok" else "failed")
                            return
                        }
                        "camera_snap" -> {
                            val facing = command.optInt("facing", 0)
                            val intent = Intent(context, CameraCaptureService::class.java).putExtra(CameraCaptureService.EXTRA_FACING, facing)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                            showToast(context, "拍照中…")
                            setResultCode(RESULT_OK)
                            setResultData("""{"status":"started","facing":$facing}""")
                            return
                        }
                    }

                    val accessibilityService = ClawPawAccessibilityService.getInstance()
                    Logger.service(TAG, "无障碍服务状态: ${if (accessibilityService != null) "已启动" else "未启动"}")
                    if (accessibilityService == null) {
                        val errorMsg = "无障碍服务未启动，请先开启无障碍服务"
                        Logger.error(TAG, errorMsg)
                        showToast(context, errorMsg)
                        setResultCode(RESULT_CANCELED)
                        setResultData("无障碍服务未启动")
                        return
                    }

                    when (action) {
                        "click" -> {
                            val x = command.getInt("x")
                            val y = command.getInt("y")
                            Logger.op(TAG, "点击坐标: ($x, $y)")
                            accessibilityService.click(x, y) { success ->
                                val msg = if (success) "点击成功" else "点击失败"
                                Logger.op(TAG, msg)
                                showToast(context, msg)
                            }
                        }
                        "input_text" -> {
                            val x = command.getInt("x")
                            val y = command.getInt("y")
                            val text = command.getString("text")
                            Logger.op(TAG, "输入文本: $text 在坐标: ($x, $y)")
                            accessibilityService.click(x, y, text) { success ->
                                val msg = if (success) "点击并输入文本成功" else "操作失败"
                                Logger.op(TAG, msg)
                                showToast(context, msg)
                            }
                        }
                        "swipe" -> {
                            val startX = command.optInt("start_x", 0)
                            val startY = command.optInt("start_y", 0)
                            val endX = command.optInt("end_x", 0)
                            val endY = command.optInt("end_y", 0)
                            Logger.op(TAG, "滑动: 从 ($startX, $startY) 到 ($endX, $endY)")
                            accessibilityService.swipe(startX, startY, endX, endY) { success ->
                                val msg = if (success) "滑动成功" else "滑动失败"
                                Logger.op(TAG, msg)
                                showToast(context, msg)
                            }
                        }
                        "long_press" -> {
                            val x = command.optInt("x", 0)
                            val y = command.optInt("y", 0)
                            Logger.op(TAG, "长按: ($x, $y)")
                            accessibilityService.longPress(x, y) { success ->
                                val msg = if (success) "长按成功" else "长按失败"
                                Logger.op(TAG, msg)
                                showToast(context, msg)
                            }
                        }
                        "two_finger_swipe_same" -> {
                            val startX = command.optInt("start_x", 0)
                            val startY = command.optInt("start_y", 0)
                            val endX = command.optInt("end_x", 0)
                            val endY = command.optInt("end_y", 0)
                            Logger.op(TAG, "两指同向滑动: ($startX,$startY)→($endX,$endY)")
                            accessibilityService.twoFingerSwipeSame(startX, startY, endX, endY) { success ->
                                val msg = if (success) "两指同向滑动成功" else "失败"
                                Logger.op(TAG, msg)
                                showToast(context, msg)
                            }
                        }
                        "two_finger_swipe_opposite" -> {
                            val startX = command.optInt("start_x", 0)
                            val startY = command.optInt("start_y", 0)
                            val endX = command.optInt("end_x", 0)
                            val endY = command.optInt("end_y", 0)
                            Logger.op(TAG, "两指反向滑动: ($startX,$startY)↔($endX,$endY)")
                            accessibilityService.twoFingerSwipeOpposite(startX, startY, endX, endY) { success ->
                                val msg = if (success) "两指反向滑动成功" else "失败"
                                Logger.op(TAG, msg)
                                showToast(context, msg)
                            }
                        }
                        "back" -> {
                            Logger.op(TAG, "执行返回操作")
                            accessibilityService.back()
                            showToast(context, "已执行返回操作")
                        }
                        "screenshot" -> {
                            Logger.op(TAG, "开始截图")
                            accessibilityService.takeScreenshot { path ->
                                if (path != null) {
                                    val msg = if (path == "系统相册") {
                                        "截图已保存到系统相册"
                                    } else {
                                        "截图已保存: $path"
                                    }
                                    Logger.op(TAG, msg)
                                    showToast(context, msg)
                                } else {
                                    val msg = "截图失败"
                                    Logger.error(TAG, msg)
                                    showToast(context, msg)
                                }
                            }
                        }
                        "open_schema" -> {
                            val schema = command.optString("schema", command.optString("uri", ""))
                            Logger.op(TAG, "open_schema: $schema")
                            val ok = accessibilityService.openBySchema(schema)
                            showToast(context, if (ok) "已打开: $schema" else "打开失败: $schema")
                        }
                        "input_text_direct" -> {
                            val x = command.optInt("x", 0)
                            val y = command.optInt("y", 0)
                            val text = command.optString("text", "")
                            Logger.op(TAG, "无障碍直接输入: $text at ($x,$y)")
                            accessibilityService.inputTextDirect(x, y, text) { success ->
                                val msg = if (success) "直接输入成功" else "直接输入失败(可能不支持)"
                                showToast(context, msg)
                            }
                        }
                        "get_layout" -> {
                            Logger.op(TAG, "获取布局信息")
                            val layout = accessibilityService.getLayout()
                            Logger.layout(TAG, "当前布局: $layout")
                            showToast(context, "已获取布局信息，查看日志")
                        }
                        "screen_on" -> {
                            val ok = HardwareHelper.wakeScreen(context)
                            showToast(context, if (ok) "已点亮屏幕" else "点亮屏幕失败")
                            setResultCode(if (ok) RESULT_OK else RESULT_CANCELED)
                            setResultData(if (ok) "ok" else "failed")
                            return
                        }
                        else -> {
                            val msg = "未知命令: $action"
                            Logger.error(TAG, msg)
                            showToast(context, msg)
                            setResultCode(RESULT_CANCELED)
                            setResultData(msg)
                            return
                        }
                    }
                    setResultCode(RESULT_OK)
                    setResultData("命令执行成功")
                } catch (e: JSONException) {
                    Logger.error(TAG, "命令 JSON 解析失败, 原始内容: $commandJson", e)
                    val msg = "命令格式错误: ${e.message}"
                    showToast(context, msg)
                    setResultCode(RESULT_CANCELED)
                    setResultData("json_error: ${e.message}")
                } catch (e: Exception) {
                    val msg = "执行命令失败: ${e.message}"
                    Logger.error(TAG, msg, e)
                    showToast(context, msg)
                    setResultCode(RESULT_CANCELED)
                    setResultData(msg ?: "")
                }
            } else {
                val msg = "命令为空"
                Logger.error(TAG, msg)
                showToast(context, msg)
                setResultCode(RESULT_CANCELED)
                setResultData(msg)
            }
        } else {
            Logger.error(TAG, "未知的广播 action: ${intent.action}")
        }
    }

    private fun showToast(context: Context, message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
} 