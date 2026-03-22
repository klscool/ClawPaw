package com.example.clawpaw.build

import com.example.clawpaw.BuildConfig
import org.json.JSONArray

/**
 * 按构建变体（basic / sensitive / full）限制 Node 与 HTTP 可调用的命令。
 * HTTP 与 Gateway `node.invoke` 共用同一套判定。
 */
object FlavorCommandGate {

    enum class Tier {
        BASIC,
        SENSITIVE,
        FULL,
    }

    fun currentTier(): Tier = when (BuildConfig.BUILD_TIER) {
        "BASIC" -> Tier.BASIC
        "SENSITIVE" -> Tier.SENSITIVE
        else -> Tier.FULL
    }

    /** 统一成下划线形式，便于与 HTTP action、Gateway method 对照 */
    fun canonical(methodOrAction: String): String =
        methodOrAction.trim().lowercase().replace(".", "_")

    private val BASIC_CANONICAL: Set<String> = setOf(
        "location_get",
        "get_wifi_name",
        "wifi_list",
        "wifi_info",
        "get_screen_state",
        "get_state",
        "device_status",
        "device_info",
        "device_health",
        "device_permissions",
        "vibrate",
        "screen_on",
        "sensors_steps",
        "sensors_light",
        "sensors_info",
        "motion_pedometer",
        "motion_activity",
    )

    private val SENSITIVE_EXTRA: Set<String> = setOf(
        "wifi_enable",
        "notifications_list",
        "notifications_actions",
        "notification_show",
        "notifications_push",
        "system_notify",
        "contacts_list",
        "contacts_search",
        "photos_latest",
        "calendar_list",
        "calendar_events",
        "file_read_text",
        "file_read_base64",
        "sms_list",
        "sms_send",
        "phone_call",
        "phone_dial",
        "camera_rear",
        "camera_front",
        "camera_snap",
        "bluetooth_list",
        "volume_get",
        "volume_set",
        "ringer_get",
        "ringer_set",
        "dnd_get",
        "dnd_set",
    )

    private val ACCESSIBILITY_CANONICAL: Set<String> = setOf(
        "get_layout",
        "screenshot",
        "click",
        "input_text",
        "input_text_direct",
        "input", // HTTP 别名
        "swipe",
        "long_press",
        "two_finger_swipe_same",
        "two_finger_swipe_opposite",
        "back",
        "open_schema",
        "open_app",
    )

    private fun sensitiveAllowedCanon(): Set<String> = BASIC_CANONICAL + SENSITIVE_EXTRA

    fun isNodeInvokeAllowed(method: String): Boolean {
        val c = canonical(method)
        return when (currentTier()) {
            Tier.FULL -> true
            Tier.SENSITIVE -> c in sensitiveAllowedCanon()
            Tier.BASIC -> c in BASIC_CANONICAL
        }
    }

    /** HTTP POST /api/execute 的 action；与 Node 共用规则（含 input→input_text） */
    fun isHttpExecuteAllowed(action: String): Boolean {
        val a = if (canonical(action) == "input") "input_text" else action
        return isNodeInvokeAllowed(a)
    }

    fun allowsHttpLayoutAndScreenshot(): Boolean = currentTier() == Tier.FULL

    /** 是否包含无障碍远程操作能力（用于 UI 等） */
    fun hasAccessibilityFlavor(): Boolean = currentTier() == Tier.FULL

    fun hasSshFlavor(): Boolean = currentTier() == Tier.FULL

    /** Node connect 里声明的 caps（与 Gateway 协商） */
    fun nodeCapsJsonArray(): JSONArray = JSONArray().apply {
        when (currentTier()) {
            Tier.FULL -> {
                listOf("screen", "device", "notifications", "camera", "location", "photos", "contacts", "calendar", "motion", "sms")
                    .forEach { put(it) }
            }
            Tier.SENSITIVE -> {
                listOf("device", "location", "motion", "notifications", "camera", "photos", "contacts", "calendar", "sms")
                    .forEach { put(it) }
            }
            Tier.BASIC -> {
                listOf("device", "location", "motion").forEach { put(it) }
            }
        }
    }

    /** Node connect 里声明的 commands 列表（与现有 Gateway 字符串完全一致，按 tier 过滤） */
    fun nodeCommandsJsonArray(): JSONArray {
        val all = listOf(
            "device_status", "device.status",
            "device_info", "device.info",
            "device.health", "device.permissions",
            "system.notify", "notification.show", "notifications.push",
            "notifications_list", "notifications.list", "notifications.actions",
            "camera_snap", "camera.snap",
            "location_get", "location.get",
            "photos_latest", "photos.latest",
            "contacts.list", "contacts.search",
            "calendar.list", "calendar.events",
            "get_state", "get_wifi_name", "get_screen_state",
            "camera_rear", "camera_front", "vibrate", "screen_on",
            "volume.get", "volume.set",
            "file.read_text", "file.read_base64",
            "sensors.steps", "sensors.light",
            "motion.pedometer", "motion.activity", "sensors.info",
            "bluetooth.list",
            "wifi.info", "wifi.list", "wifi.enable",
            "sms.list", "sms.send",
            "phone.call", "phone.dial",
            "ringer.get", "ringer.set", "dnd.get", "dnd.set",
            "get_layout", "screenshot",
            "click", "input_text", "input_text_direct",
            "swipe", "long_press",
            "two_finger_swipe_same", "two_finger_swipe_opposite",
            "back", "open_schema",
        )
        val ja = JSONArray()
        for (cmd in all) {
            if (isNodeInvokeAllowed(cmd)) ja.put(cmd)
        }
        return ja
    }
}
