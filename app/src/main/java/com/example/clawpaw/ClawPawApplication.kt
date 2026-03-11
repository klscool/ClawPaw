package com.example.clawpaw

import android.app.Application
import android.content.Context
import com.example.clawpaw.data.storage.AppPrefs
import com.example.clawpaw.data.storage.DebugPrefs
import com.example.clawpaw.data.storage.MainPrefs
import com.example.clawpaw.util.Logger
import com.example.clawpaw.worker.StepsMidnightWorker

class ClawPawApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.i("Application", "应用启动")
        com.example.clawpaw.data.api.RetrofitClient.init(this)
        com.example.clawpaw.data.storage.OnboardingPrefs.init(this)
        MainPrefs.init(this)
        AppPrefs.init(this)
        DebugPrefs.init(this)
        StepsMidnightWorker.scheduleNextMidnight(this)
        if (MainPrefs.getHttpServiceEnabled()) startNodeHttpService()
    }

    private fun startNodeHttpService() {
        try {
            val intent = android.content.Intent(this, com.example.clawpaw.service.NodeHttpService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Logger.error("Application", "启动 HTTP 服务失败", e)
        }
    }
} 