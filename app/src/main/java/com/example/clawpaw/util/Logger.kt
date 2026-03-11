package com.example.clawpaw.util

import android.util.Log

object Logger {
    private const val APP_TAG = "ClawPaw"
    private var isDebug = true

    fun setDebug(debug: Boolean) {
        isDebug = debug
    }

    fun d(tag: String, message: String) {
        if (isDebug) {
            Log.d("$APP_TAG/$tag", message)
        }
    }

    fun i(tag: String, message: String) {
        Log.i("$APP_TAG/$tag", message)
    }

    fun w(tag: String, message: String) {
        Log.w("$APP_TAG/$tag", message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$APP_TAG/$tag", message, throwable)
        } else {
            Log.e("$APP_TAG/$tag", message)
        }
    }

    fun v(tag: String, message: String) {
        if (isDebug) {
            Log.v("$APP_TAG/$tag", message)
        }
    }

    fun network(tag: String, message: String) {
        if (isDebug) {
            Log.d("$APP_TAG/Network/$tag", message)
        }
    }

    fun operation(tag: String, message: String) {
        if (isDebug) {
            Log.d("$APP_TAG/Operation/$tag", message)
        }
    }

    fun command(tag: String, message: String) {
        if (isDebug) {
            Log.d("$APP_TAG/Command/$tag", message)
        }
    }

    fun service(tag: String, message: String) {
        if (isDebug) {
            Log.d("$APP_TAG/Service/$tag", message)
        }
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$APP_TAG/Error/$tag", message, throwable)
        } else {
            Log.e("$APP_TAG/Error/$tag", message)
        }
    }

    // 用于记录操作执行
    fun op(tag: String, operation: String) {
        if (isDebug) {
            Log.d("$APP_TAG/Operation/$tag", operation)
        }
    }

    // 用于记录命令执行
    fun cmd(tag: String, command: String) {
        if (isDebug) {
            Log.d("$APP_TAG/Command/$tag", command)
        }
    }

    // 用于记录布局信息
    fun layout(tag: String, info: String) {
        if (isDebug) {
            Log.d("$APP_TAG/Layout/$tag", info)
        }
    }

    // 用于记录生命周期事件
    fun lifecycle(tag: String, event: String) {
        if (isDebug) {
            Log.d("$APP_TAG/Lifecycle/$tag", event)
        }
    }

    // 用于记录性能相关信息
    fun performance(tag: String, info: String) {
        if (isDebug) {
            Log.d("$APP_TAG/Performance/$tag", info)
        }
    }

    // 用于记录用户交互
    fun interaction(tag: String, action: String) {
        if (isDebug) {
            Log.d("$APP_TAG/Interaction/$tag", action)
        }
    }

    // 用于记录系统事件
    fun system(tag: String, event: String) {
        if (isDebug) {
            Log.d("$APP_TAG/System/$tag", event)
        }
    }
} 