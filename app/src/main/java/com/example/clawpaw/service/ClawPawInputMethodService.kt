package com.example.clawpaw.service

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import com.example.clawpaw.util.Logger

class ClawPawInputMethodService : InputMethodService() {
    companion object {
        private const val TAG = "ClawPawIME"
        private var instance: ClawPawInputMethodService? = null
        fun getInstance(): ClawPawInputMethodService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Logger.i(TAG, "输入法服务已创建")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Logger.i(TAG, "输入法服务已销毁")
    }

    override fun onCreateInputView(): View {
        Logger.i(TAG, "创建输入法视图")
        return LinearLayout(this)
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Logger.i(TAG, "开始输入，restarting: $restarting")
    }

    override fun onBindInput() {
        super.onBindInput()
        Logger.i(TAG, "输入法服务已绑定")
    }

    fun inputText(text: String) {
        Logger.i(TAG, "准备输入文本: $text")
        val ic = currentInputConnection
        if (ic == null) {
            Logger.e(TAG, "输入连接为空")
            return
        }

        try {
            // 先清空当前文本。可以模拟退格
            ic.deleteSurroundingText(Int.MAX_VALUE, Int.MAX_VALUE)
            Logger.i(TAG, "已清空当前文本")
            
            // 输入新文本
            val commitResult = ic.commitText(text, 1)
            Logger.i(TAG, "文本输入结果: $commitResult")
            
            // 模拟回车键
            val enterDown = ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            val enterUp = ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            Logger.i(TAG, "回车键事件结果: down=$enterDown, up=$enterUp")
        } catch (e: Exception) {
            Logger.e(TAG, "输入文本失败", e)
        }
    }
} 