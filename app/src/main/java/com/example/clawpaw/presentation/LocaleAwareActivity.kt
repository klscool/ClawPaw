package com.example.clawpaw.presentation

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import com.example.clawpaw.data.storage.MainPrefs
import java.util.Locale

/**
 * 在 attachBaseContext 中根据 MainPrefs 的 app_language 应用对应 Locale，
 * 使 stringResource() 等使用 values-zh / values-en。
 */
abstract class LocaleAwareActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        MainPrefs.init(newBase.applicationContext)
        val tag = MainPrefs.getAppLanguage()
        if (tag == "system") {
            super.attachBaseContext(newBase)
            return
        }
        val locale = when (tag) {
            "zh" -> Locale("zh")
            "en" -> Locale.ENGLISH
            else -> {
                super.attachBaseContext(newBase)
                return
            }
        }
        val config = Configuration(newBase.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.setLocale(locale)
        }
        val wrapped = newBase.createConfigurationContext(config)
        super.attachBaseContext(wrapped)
    }
}
