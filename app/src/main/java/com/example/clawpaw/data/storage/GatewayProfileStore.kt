package com.example.clawpaw.data.storage

import android.content.Context
import com.example.clawpaw.data.api.RetrofitClient
import org.json.JSONObject

/** 一套 Gateway 连接配置（与 RetrofitClient 中持久化字段对应，不含设备密钥）。 */
data class GatewayProfile(
    val host: String,
    val port: Int,
    val nodeDisplayName: String,
    val originalToken: String,
    val nodeToken: String,
    val operatorToken: String,
    val gatewayToken: String,
    val password: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("host", host)
        put("port", port)
        put("nodeDisplayName", nodeDisplayName)
        put("originalToken", originalToken)
        put("nodeToken", nodeToken)
        put("operatorToken", operatorToken)
        put("gatewayToken", gatewayToken)
        put("password", password)
    }

    companion object {
        fun fromJson(s: String?): GatewayProfile {
            if (s.isNullOrBlank()) return empty()
            return try {
                val o = JSONObject(s)
                GatewayProfile(
                    host = o.optString("host", "127.0.0.1").ifBlank { "127.0.0.1" },
                    port = o.optInt("port", 18789).coerceIn(1, 65535),
                    nodeDisplayName = o.optString("nodeDisplayName", ""),
                    originalToken = o.optString("originalToken", ""),
                    nodeToken = o.optString("nodeToken", ""),
                    operatorToken = o.optString("operatorToken", ""),
                    gatewayToken = o.optString("gatewayToken", ""),
                    password = o.optString("password", ""),
                )
            } catch (_: Exception) {
                empty()
            }
        }

        fun empty() = GatewayProfile(
            host = "127.0.0.1",
            port = 18789,
            nodeDisplayName = "",
            originalToken = "",
            nodeToken = "",
            operatorToken = "",
            gatewayToken = "",
            password = "",
        )
    }
}

/**
 * 最多 3 套 Gateway 配置槽位 + 当前选用下标。
 * 与 [RetrofitClient] 共用 `gateway_prefs`。
 */
object GatewayProfileStore {
    private const val PREFS_NAME = "gateway_prefs"
    private const val KEY_MIGRATED = "gateway_profiles_migrated"
    private const val KEY_ACTIVE = "gateway_active_profile_index"
    private const val SLOT_COUNT = 3

    private fun keySlot(i: Int) = "gateway_profile_slot_$i"

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * 首次升级：把当前全局 Gateway 配置写入槽位 0，并初始化空槽 1、2。
     */
    fun migrateIfNeeded() {
        val p = prefs ?: return
        if (p.getBoolean(KEY_MIGRATED, false)) return
        val hasAnySlot = (0 until SLOT_COUNT).any { p.contains(keySlot(it)) }
        if (!hasAnySlot) {
            saveSlotInternal(0, RetrofitClient.toGatewayProfile())
            saveSlotInternal(1, GatewayProfile.empty())
            saveSlotInternal(2, GatewayProfile.empty())
        }
        if (!p.contains(KEY_ACTIVE)) {
            p.edit().putInt(KEY_ACTIVE, 0).apply()
        }
        p.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    fun getActiveIndex(): Int = prefs?.getInt(KEY_ACTIVE, 0)?.coerceIn(0, SLOT_COUNT - 1) ?: 0

    fun setActiveIndex(index: Int) {
        prefs?.edit()?.putInt(KEY_ACTIVE, index.coerceIn(0, SLOT_COUNT - 1))?.apply()
    }

    fun loadSlot(index: Int): GatewayProfile = loadSlotInternal(index.coerceIn(0, SLOT_COUNT - 1))

    fun saveSlot(index: Int, profile: GatewayProfile) {
        saveSlotInternal(index.coerceIn(0, SLOT_COUNT - 1), profile)
    }

    private fun loadSlotInternal(index: Int): GatewayProfile {
        val json = prefs?.getString(keySlot(index), null)
        return GatewayProfile.fromJson(json)
    }

    private fun saveSlotInternal(index: Int, profile: GatewayProfile) {
        prefs?.edit()?.putString(keySlot(index), profile.toJson().toString())?.apply()
    }
}
