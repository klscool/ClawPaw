package com.example.clawpaw.gateway

import com.example.clawpaw.service.ClawPawAccessibilityService
import com.example.clawpaw.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * 将 Gateway 的 method/params 映射为无障碍原子操作。
 * 每个操作独立可调用，仅执行并返回结果。
 * 默认：执行成功后等 2 秒并返回当前布局。params 传 return_layout_after: false 时不等待、不返回布局。
 */
object GatewayProtocol {
    private const val TAG = "GatewayProtocol"

    suspend fun execute(service: ClawPawAccessibilityService, method: String, params: JSONObject): Result<Any?> {
        val returnLayoutAfter = params.optBoolean("return_layout_after", true)
        val inner = when (method) {
            "get_layout" -> {
                val layout = service.getLayout()
                Result.success(layout)
            }
            "screenshot" -> {
                suspendCancellableCoroutine { cont ->
                    service.takeScreenshot { path ->
                        cont.resume(Result.success(path ?: "")) {}
                    }
                }
            }
            "click" -> {
                val x = params.optInt("x", 0)
                val y = params.optInt("y", 0)
                suspendCancellableCoroutine { cont ->
                    service.click(x, y) { success ->
                        cont.resume(Result.success(success)) {}
                    }
                }
            }
            "input_text" -> {
                val x = params.optInt("x", 0)
                val y = params.optInt("y", 0)
                val text = params.optString("text", "")
                suspendCancellableCoroutine { cont ->
                    service.click(x, y, text) { success ->
                        cont.resume(Result.success(success)) {}
                    }
                }
            }
            "input_text_direct" -> {
                val x = params.optInt("x", 0)
                val y = params.optInt("y", 0)
                val text = params.optString("text", "")
                suspendCancellableCoroutine { cont ->
                    service.inputTextDirect(x, y, text) { success ->
                        cont.resume(Result.success(success)) {}
                    }
                }
            }
            "swipe" -> {
                val startX = params.optInt("start_x", 0)
                val startY = params.optInt("start_y", 0)
                val endX = params.optInt("end_x", 0)
                val endY = params.optInt("end_y", 0)
                suspendCancellableCoroutine { cont ->
                    service.swipe(startX, startY, endX, endY) { success ->
                        cont.resume(Result.success(success)) {}
                    }
                }
            }
            "long_press" -> {
                val x = params.optInt("x", 0)
                val y = params.optInt("y", 0)
                suspendCancellableCoroutine { cont ->
                    service.longPress(x, y) { success ->
                        cont.resume(Result.success(success)) {}
                    }
                }
            }
            "two_finger_swipe_same" -> {
                val startX = params.optInt("start_x", 0)
                val startY = params.optInt("start_y", 0)
                val endX = params.optInt("end_x", 0)
                val endY = params.optInt("end_y", 0)
                suspendCancellableCoroutine { cont ->
                    service.twoFingerSwipeSame(startX, startY, endX, endY) { success ->
                        cont.resume(Result.success(success)) {}
                    }
                }
            }
            "two_finger_swipe_opposite" -> {
                val startX = params.optInt("start_x", 0)
                val startY = params.optInt("start_y", 0)
                val endX = params.optInt("end_x", 0)
                val endY = params.optInt("end_y", 0)
                suspendCancellableCoroutine { cont ->
                    service.twoFingerSwipeOpposite(startX, startY, endX, endY) { success ->
                        cont.resume(Result.success(success)) {}
                    }
                }
            }
            "back" -> {
                service.back()
                Result.success(true)
            }
            "open_schema" -> {
                val schema = params.optString("schema", params.optString("uri", ""))
                if (schema.isBlank()) Result.failure(IllegalArgumentException("open_schema 需要 schema 或 uri 参数"))
                else Result.success(service.openBySchema(schema))
            }
            else -> {
                Logger.w(TAG, "未知 method: $method")
                Result.failure(IllegalArgumentException("未知 method: $method"))
            }
        }
        if (returnLayoutAfter && inner.isSuccess && method != "get_layout" && method != "screenshot") {
            delay(2000)
            val layout = service.getLayout()
            return Result.success(mapOf("success" to true, "layout" to layout))
        }
        return inner
    }
}
