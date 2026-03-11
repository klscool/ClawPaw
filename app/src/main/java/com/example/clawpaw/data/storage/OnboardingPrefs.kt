package com.example.clawpaw.data.storage

import android.content.Context

private const val PREFS = "onboarding_prefs"
private const val KEY_COMPLETED = "completed"

object OnboardingPrefs {
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun isCompleted(): Boolean = prefs?.getBoolean(KEY_COMPLETED, false) ?: false

    fun setCompleted(completed: Boolean) {
        prefs?.edit()?.putBoolean(KEY_COMPLETED, completed)?.apply()
    }
}
