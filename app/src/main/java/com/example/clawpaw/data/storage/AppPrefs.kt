package com.example.clawpaw.data.storage

import android.content.Context

private const val PREFS = "app_prefs"
private const val KEY_PERSISTENT_NOTIFICATION = "persistent_notification"
private const val KEY_AUTO_RECONNECT_NODE = "auto_reconnect_node"
private const val KEY_AUTO_RECONNECT_SSH = "auto_reconnect_ssh"
private const val KEY_RECONNECT_CHECK_INTERVAL = "reconnect_check_interval"
private const val KEY_CHAT_REFRESH_INTERVAL = "chat_refresh_interval"
private const val KEY_CHAT_FONT_SIZE = "chat_font_size"

/** 重连检查间隔：5s / 10s / 30s */
enum class ReconnectCheckInterval(val key: String, val delayMs: Long) {
    HIGH("high", 5_000L),
    MEDIUM("medium", 10_000L),
    LOW("low", 30_000L);
    companion object {
        fun fromKey(key: String?) = entries.find { it.key == key } ?: MEDIUM
    }
}

/** 对话自动拉取间隔：高=10s 中=20s 低=60s */
enum class ChatRefreshInterval(val key: String, val delayMs: Long) {
    HIGH("high", 10_000L),
    MEDIUM("medium", 20_000L),
    LOW("low", 60_000L);
    companion object {
        fun fromKey(key: String?) = entries.find { it.key == key } ?: MEDIUM
    }
}

/** 对话字号：大=18 中=15 小=12 */
enum class ChatFontSize(val key: String, val sp: Int) {
    LARGE("large", 18),
    MEDIUM("medium", 15),
    SMALL("small", 12);
    companion object {
        fun fromKey(key: String?) = entries.find { it.key == key } ?: MEDIUM
    }
}

object AppPrefs {
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun getPersistentNotification(): Boolean = prefs?.getBoolean(KEY_PERSISTENT_NOTIFICATION, true) ?: true
    fun setPersistentNotification(on: Boolean) {
        prefs?.edit()?.putBoolean(KEY_PERSISTENT_NOTIFICATION, on)?.apply()
    }

    fun getAutoReconnectNode(): Boolean = prefs?.getBoolean(KEY_AUTO_RECONNECT_NODE, true) ?: true
    fun setAutoReconnectNode(on: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTO_RECONNECT_NODE, on)?.apply()
    }

    fun getAutoReconnectSsh(): Boolean = prefs?.getBoolean(KEY_AUTO_RECONNECT_SSH, true) ?: true
    fun setAutoReconnectSsh(on: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTO_RECONNECT_SSH, on)?.apply()
    }

    fun getReconnectCheckInterval(): ReconnectCheckInterval =
        ReconnectCheckInterval.fromKey(prefs?.getString(KEY_RECONNECT_CHECK_INTERVAL, null))
    fun setReconnectCheckInterval(interval: ReconnectCheckInterval) {
        prefs?.edit()?.putString(KEY_RECONNECT_CHECK_INTERVAL, interval.key)?.apply()
    }

    fun getChatRefreshInterval(): ChatRefreshInterval =
        ChatRefreshInterval.fromKey(prefs?.getString(KEY_CHAT_REFRESH_INTERVAL, null))
    fun setChatRefreshInterval(interval: ChatRefreshInterval) {
        prefs?.edit()?.putString(KEY_CHAT_REFRESH_INTERVAL, interval.key)?.apply()
    }

    fun getChatFontSize(): ChatFontSize = ChatFontSize.fromKey(prefs?.getString(KEY_CHAT_FONT_SIZE, null))
    fun setChatFontSize(size: ChatFontSize) {
        prefs?.edit()?.putString(KEY_CHAT_FONT_SIZE, size.key)?.apply()
    }
}
