package com.example.clawpaw.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import android.content.ClipboardManager
import android.content.ClipData
import android.os.Bundle
import com.example.clawpaw.util.Logger
import android.content.Context
import android.view.WindowManager
import android.hardware.display.DisplayManager

class ClawPawAccessibilityService : AccessibilityService() {
    
    companion object {
        private var instance: ClawPawAccessibilityService? = null
        private const val TAG = "ClawPawAccessibilityService"
        /** 滑动路径中点垂直于方向的最大偏移（像素），模拟真人轻微不直 */
        private const val HUMAN_SWIPE_OFFSET = 25f
        /** 长按时长（毫秒） */
        private const val LONG_PRESS_DURATION_MS = 700L
        /** 单指滑动时长（毫秒），越小越快 */
        private const val SWIPE_DURATION_MS = 150L
        /** 两指同向滑动时第二指与第一指的垂直间距（像素） */
        private const val TWO_FINGER_OFFSET = 48f

        fun getInstance(): ClawPawAccessibilityService? = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Logger.service(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    private fun getLayoutInfo(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        
        val stringBuilder = StringBuilder()

        fun appendNodeInfo(node: AccessibilityNodeInfo?, path: List<Int> = emptyList()) {
            if (node == null) return

            // 检查节点是否包含必要的信息
            val hasContent = node.text != null || 
                           node.contentDescription != null || 
                           node.viewIdResourceName != null
            
            if (!hasContent) {
                // 如果节点没有内容，递归处理子节点
                if (node.childCount > 0) {
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i)
                        try {
                            appendNodeInfo(child, path + i)
                        } finally {
                            child?.recycle()
                        }
                    }
                }
                return
            }
            
            stringBuilder.append("<")
            node.text?.let { stringBuilder.append(" text=\"$it\"") }
            node.contentDescription?.let { stringBuilder.append(" content-desc=\"$it\"") }
            node.viewIdResourceName?.let { stringBuilder.append(" resource-id=\"$it\"") }
            
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            stringBuilder.append(" bounds=\"${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}\"")
            
            // if (node.isClickable) stringBuilder.append(" clickable=\"true\"")
            if (node.isEditable) stringBuilder.append(" editable=\"true\"")
            // if (node.isEnabled) stringBuilder.append(" enabled=\"true\"")
            if (node.isFocused) stringBuilder.append(" focused=\"true\"")
            if (node.isLongClickable) stringBuilder.append(" long-clickable=\"true\"")
            if (node.isPassword) stringBuilder.append(" password=\"true\"")
            if (node.isScrollable) stringBuilder.append(" scrollable=\"true\"")
            if (node.isSelected) stringBuilder.append(" selected=\"true\"")
            
            if (node.childCount > 0) {
                stringBuilder.append(">")
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    try {
                        appendNodeInfo(child, path + i)
                    } finally {
                        child?.recycle()
                    }
                }
                stringBuilder.append("</>")
            } else {
                stringBuilder.append("/>")
            }
        }
        
        appendNodeInfo(node)

        return stringBuilder.toString()
    }

    // 获取当前布局信息
    fun getLayout(): String {
        val rootInActiveWindow = rootInActiveWindow
        if (rootInActiveWindow == null) {
            Logger.error(TAG, "无法获取根节点")
            return ""
        }

        try {
            val layout = getLayoutInfo(rootInActiveWindow)
            Logger.service(TAG, "获取布局，长度: ${layout.length}")
            return layout
        } finally {
            rootInActiveWindow.recycle()
        }
    }

    fun click(x: Int, y: Int, text: String? = null, callback: ((Boolean) -> Unit)? = null) {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        
        val gestureBuilder = GestureDescription.Builder()
        val gesture = gestureBuilder
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (text != null) {
                    // 点击成功后延迟500ms再输入文本，确保焦点已经设置
                    Handler(Looper.getMainLooper()).postDelayed({
                        inputText(text)
                        callback?.invoke(true)
                    }, 500)
                } else {
                    callback?.invoke(true)
                }
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * 长按 (x, y)，700ms。
     * Path 需包含 lineTo 才被视为有效笔画，故在同一点 lineTo。
     */
    fun longPress(x: Int, y: Int, callback: ((Boolean) -> Unit)? = null) {
        val fx = x.toFloat()
        val fy = y.toFloat()
        val path = Path()
        path.moveTo(fx, fy)
        path.lineTo(fx, fy) // 同一点，形成有效 path，系统才会派发完整 down→hold→up
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, LONG_PRESS_DURATION_MS))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }

    /** 两指滑动手势时长（ms），过短可能不被识别 */
    private val TWO_FINGER_DURATION_MS = 400L

    /**
     * 两指同向滑动：两根手指沿同一方向从 (startX,startY) 滑到 (endX,endY)，路径平行。
     * 第二指略错开 10ms 开始，部分设备对双 stroke 同时 startTime=0 支持不好。
     */
    fun twoFingerSwipeSame(startX: Int, startY: Int, endX: Int, endY: Int, callback: ((Boolean) -> Unit)? = null) {
        val sx = startX.toFloat()
        val sy = startY.toFloat()
        val ex = endX.toFloat()
        val ey = endY.toFloat()
        val dx = ex - sx
        val dy = ey - sy
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val perpX = -dy / len * TWO_FINGER_OFFSET
        val perpY = dx / len * TWO_FINGER_OFFSET
        val path1 = Path().apply { moveTo(sx, sy); lineTo(ex, ey) }
        val path2 = Path().apply {
            moveTo(sx + perpX, sy + perpY)
            lineTo(ex + perpX, ey + perpY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path1, 0, TWO_FINGER_DURATION_MS))
            .addStroke(GestureDescription.StrokeDescription(path2, 10, TWO_FINGER_DURATION_MS))
            .build()
        val resultCallback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Logger.d(TAG, "两指同向滑动 onCompleted")
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Logger.w(TAG, "两指同向滑动 onCancelled")
                callback?.invoke(false)
            }
        }
        dispatchGesture(gesture, resultCallback, Handler(Looper.getMainLooper()))
    }

    /**
     * 两指反向滑动：一指 (startX,startY)→(endX,endY)，另一指略偏移后 (endX,endY)→(startX,startY)，避免两路径完全重叠。
     */
    fun twoFingerSwipeOpposite(startX: Int, startY: Int, endX: Int, endY: Int, callback: ((Boolean) -> Unit)? = null) {
        val sx = startX.toFloat()
        val sy = startY.toFloat()
        val ex = endX.toFloat()
        val ey = endY.toFloat()
        val dx = ex - sx
        val dy = ey - sy
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val perpX = -dy / len * TWO_FINGER_OFFSET
        val perpY = dx / len * TWO_FINGER_OFFSET
        val path1 = Path().apply { moveTo(sx, sy); lineTo(ex, ey) }
        val path2 = Path().apply {
            moveTo(ex + perpX, ey + perpY)
            lineTo(sx + perpX, sy + perpY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path1, 0, TWO_FINGER_DURATION_MS))
            .addStroke(GestureDescription.StrokeDescription(path2, 10, TWO_FINGER_DURATION_MS))
            .build()
        val resultCallback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Logger.d(TAG, "两指反向滑动 onCompleted")
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Logger.w(TAG, "两指反向滑动 onCancelled")
                callback?.invoke(false)
            }
        }
        dispatchGesture(gesture, resultCallback, Handler(Looper.getMainLooper()))
    }

    /**
     * 从 (startX,startY) 滑到 (endX,endY)。路径带轻微随机弯曲，模拟真人滑动。
     */
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, callback: ((Boolean) -> Unit)? = null) {
        val path = Path()
        val sx = startX.toFloat()
        val sy = startY.toFloat()
        val ex = endX.toFloat()
        val ey = endY.toFloat()
        path.moveTo(sx, sy)
        val dx = ex - sx
        val dy = ey - sy
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len > 10f) {
            val midX = (sx + ex) / 2f
            val midY = (sy + ey) / 2f
            val perpX = -dy / len
            val perpY = dx / len
            val offset = (java.util.Random().nextDouble() - 0.5) * 2 * HUMAN_SWIPE_OFFSET
            val ctrlX = midX + perpX * offset.toFloat()
            val ctrlY = midY + perpY * offset.toFloat()
            path.quadTo(ctrlX, ctrlY, ex, ey)
        } else {
            path.lineTo(ex, ey)
        }
        val gestureBuilder = GestureDescription.Builder()
        val gesture = gestureBuilder
            .addStroke(GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }

    fun back() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * 根据 schema/URI 或包名打开任意应用。例如：amapuri://、baidumap://、com.android.chrome、https://...
     */
    fun openBySchema(schemaOrPackage: String): Boolean {
        return try {
            val uri = schemaOrPackage.trim()
            val intent = when {
                uri.contains("://") -> Intent(Intent.ACTION_VIEW).apply { data = android.net.Uri.parse(uri) }
                uri.startsWith("/") -> Intent(Intent.ACTION_VIEW).apply { data = android.net.Uri.parse("file://$uri") }
                else -> packageManager.getLaunchIntentForPackage(uri)
                    ?: Intent(Intent.ACTION_VIEW).apply { data = android.net.Uri.parse(uri) }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.d(TAG, "openBySchema: $schemaOrPackage")
            true
        } catch (e: Exception) {
            Log.e(TAG, "openBySchema 失败: $schemaOrPackage", e)
            false
        }
    }

    fun takeScreenshot(callback: (String?) -> Unit) {
        try {
            Logger.service(TAG, "开始截图")
            
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                // Android 10 及以下使用旧方法
                Logger.i(TAG, "Android 10 使用模拟按键方式截图")
                val result = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (result) {
                        Logger.service(TAG, "截图已保存到系统相册")
                        callback("系统相册")  // 返回一个提示信息而不是 null
                    } else {
                        Logger.error(TAG, "截图失败")
                        callback(null)
                    }
                }, 2000)
                return
            }

            // Android 11+ 使用新API
            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val defaultDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            } else {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            }
            
            takeScreenshot(
                defaultDisplay.displayId,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            // 创建保存截图的目录
                            val screenshotDir = File(getExternalFilesDir(null), "screenshots")
                            if (!screenshotDir.exists()) {
                                screenshotDir.mkdirs()
                            }

                            // 生成文件名
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            val fileName = "screenshot_$timestamp.png"
                            val file = File(screenshotDir, fileName)

                            // 保存截图
                            FileOutputStream(file).use { out ->
                                val bitmap = Bitmap.wrapHardwareBuffer(
                                    screenshot.hardwareBuffer,
                                    screenshot.colorSpace
                                )
                                if (bitmap != null) {
                                    try {
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                        Logger.service(TAG, "截图保存成功: ${file.absolutePath}")
                                        mainExecutor.execute {
                                            callback(file.absolutePath)
                                        }
                                    } catch (e: Exception) {
                                        Logger.error(TAG, "保存截图失败", e)
                                        mainExecutor.execute {
                                            callback(null)
                                        }
                                    } finally {
                                        bitmap.recycle()
                                    }
                                } else {
                                    Logger.error(TAG, "无法创建位图")
                                    mainExecutor.execute {
                                        callback(null)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Logger.error(TAG, "处理截图失败", e)
                            mainExecutor.execute {
                                callback(null)
                            }
                        } finally {
                            screenshot.hardwareBuffer.close()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Logger.error(TAG, "截图失败，错误码: $errorCode")
                        mainExecutor.execute {
                            callback(null)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Logger.error(TAG, "截图过程发生异常", e)
            callback(null)
        }
    }

    fun inputText(text: String) {
        val root = rootInActiveWindow ?: return
        
        // 查找可编辑的节点
        fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            
            // 检查当前节点是否可编辑
            if (node.isEditable || node.className?.contains("EditText") == true) {
                return node
            }
            
            // 递归查找子节点
            for (i in 0 until node.childCount) {
                val editableNode = findEditableNode(node.getChild(i))
                if (editableNode != null) {
                    return editableNode
                }
            }
            
            return null
        }
        
        try {
            // 查找可编辑节点
            val editableNode = findEditableNode(root)
            
            if (editableNode != null) {
                // 聚焦到输入框  多余操作！！！！
//                editableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                
                // 直接尝试输入文本
                Handler(Looper.getMainLooper()).postDelayed({
                    val inputMethodService = ClawPawInputMethodService.getInstance()
                    if (inputMethodService != null) {
                        inputMethodService.inputText(text)
                    } else {
                        Log.e(TAG, "输入法服务未启动")
                    }
                }, 500)
            } else {
                Log.e(TAG, "未找到可编辑的节点")
            }
        } finally {
            root.recycle()
        }
    }

    /** 设完文字后延迟发回车的间隔（毫秒），留足时间给输入框/IME 更新后再发回车。 */
    private val inputTextDirectEnterDelayMs = 500L

    /** Android 11+ 对当前输入焦点节点执行 IME 回车。 */
    private fun sendImeEnterOnFocusedInput() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val root = rootInActiveWindow ?: return
        try {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
            try {
                val actionId = getActionImeEnterId() ?: run {
                    Log.w(TAG, "inputTextDirect: 无法获取 ACTION_IME_ENTER id，跳过回车")
                    return
                }
                val enterOk = focused.performAction(actionId)
                Log.d(TAG, "inputTextDirect ACTION_IME_ENTER: $enterOk")
            } finally {
                focused.recycle()
            }
        } finally {
            root.recycle()
        }
    }

    /** 获取 IME 回车 action 的 id（API 30+）：先尝试系统资源 id，再反射 AccessibilityNodeInfo。 */
    private fun getActionImeEnterId(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val ctx = applicationContext ?: return null
        val idFromRes = ctx.resources.getIdentifier("accessibilityActionImeEnter", "id", "android")
        if (idFromRes != 0) {
            Log.d(TAG, "getActionImeEnterId: 使用系统资源 id=$idFromRes")
            return idFromRes
        }
        return try {
            val innerClass = Class.forName("android.view.accessibility.AccessibilityNodeInfo\$AccessibilityAction")
            val field = innerClass.getDeclaredField("ACTION_IME_ENTER")
            field.isAccessible = true
            val action = field.get(null) ?: return null
            val id = action.javaClass.getMethod("getId").invoke(action) as? Int
            Log.d(TAG, "getActionImeEnterId: 反射得到 id=$id")
            id
        } catch (e: Exception) {
            try {
                val clazz = Class.forName("android.view.accessibility.AccessibilityNodeInfo")
                val field = clazz.getDeclaredField("ACTION_IME_ENTER")
                field.isAccessible = true
                val action = field.get(null) ?: return null
                val id = action.javaClass.getMethod("getId").invoke(action) as? Int
                Log.d(TAG, "getActionImeEnterId: 反射(NodeInfo) 得到 id=$id")
                return id
            } catch (e2: Exception) {
                Log.e(TAG, "getActionImeEnterId", e2)
                null
            }
        }
    }

    /**
     * 尝试用无障碍直接设置输入框文字（不依赖自定义输入法）。
     * 设完后延迟一段时间发送 IME 回车（Android 11+），与 input_text 行为一致。
     * 以当前焦点为准：若 (x,y) 非 (0,0) 先点击 (x,y) 再取焦点，否则直接取当前焦点节点并 ACTION_SET_TEXT。
     * 部分应用/控件可能不支持。
     */
    fun inputTextDirect(x: Int, y: Int, text: String, callback: (Boolean) -> Unit) {
        fun setTextOnFocused(root: AccessibilityNodeInfo?) {
            if (root == null) {
                callback(false)
                return
            }
            try {
                val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused == null) {
                    Log.e(TAG, "inputTextDirect: 无当前输入焦点")
                    callback(false)
                    return
                }
                try {
                    val bundle = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    }
                    val ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                    Log.d(TAG, "inputTextDirect ACTION_SET_TEXT: $ok")
                    callback(ok)
                    if (ok && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            sendImeEnterOnFocusedInput()
                        }, inputTextDirectEnterDelayMs)
                    }
                } finally {
                    focused.recycle()
                }
            } finally {
                root.recycle()
            }
        }

        val root = rootInActiveWindow ?: run { callback(false); return }
        if (x != 0 || y != 0) {
            val path = Path()
            path.moveTo(x.toFloat(), y.toFloat())
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        setTextOnFocused(rootInActiveWindow)
                    }, 300)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    callback(false)
                }
            }, null)
            root.recycle()
        } else {
            setTextOnFocused(root)
        }
    }
} 