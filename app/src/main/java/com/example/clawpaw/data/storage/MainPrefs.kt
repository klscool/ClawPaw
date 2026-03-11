package com.example.clawpaw.data.storage

import android.content.Context

private const val PREFS = "main_prefs"
private const val KEY_HTTP_SERVICE_ENABLED = "http_service_enabled"
private const val KEY_AUTH_GUIDE_SHOWN_AFTER_CONNECT = "auth_guide_shown_after_connect"

object MainPrefs {
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun getHttpServiceEnabled(): Boolean = prefs?.getBoolean(KEY_HTTP_SERVICE_ENABLED, true) ?: true

    fun setHttpServiceEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_HTTP_SERVICE_ENABLED, enabled)?.apply()
    }

    fun getAuthGuideShownAfterConnect(): Boolean = prefs?.getBoolean(KEY_AUTH_GUIDE_SHOWN_AFTER_CONNECT, false) ?: false

    fun setAuthGuideShownAfterConnect(shown: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTH_GUIDE_SHOWN_AFTER_CONNECT, shown)?.apply()
    }
}
