package com.example.clawpaw.hardware

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 拨号：通过 Intent 调起系统拨号界面或直接拨打电话（需 CALL_PHONE 权限）。
 */
object PhoneHelper {

    /**
     * 打开拨号界面并预填号码，不直接拨出。
     */
    fun dial(context: Context, number: String): Boolean {
        if (number.isBlank()) return false
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${Uri.encode(number)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    /**
     * 直接拨打电话，需 CALL_PHONE 权限。
     */
    fun call(context: Context, number: String): Boolean {
        if (number.isBlank()) return false
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${Uri.encode(number)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }
}
