package com.example.clawpaw.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * 短信：读取收件箱、发送短信（需 READ_SMS、SEND_SMS 权限）。
 */
object SmsHelper {

    /**
     * 读取收件箱最近短信，返回 JSONArray，每项含 address, body, date, type。
     */
    fun getInbox(context: Context, limit: Int = 50): JSONArray {
        val arr = JSONArray()
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Telephony.Sms.CONTENT_URI
        } else {
            @Suppress("DEPRECATION")
            Uri.parse("content://sms/inbox")
        }
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
            val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                arr.put(JSONObject().apply {
                    put("address", cursor.getString(addrIdx).orEmpty())
                    put("body", cursor.getString(bodyIdx).orEmpty())
                    put("date", cursor.getLong(dateIdx))
                    put("type", cursor.getInt(typeIdx))
                })
                count++
            }
        }
        return arr
    }

    /**
     * 发送短信。address 为号码，body 为内容。返回 "ok" 或抛异常。
     */
    fun sendSms(context: Context, address: String, body: String): String {
        if (address.isBlank()) throw IllegalArgumentException("address required")
        return try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(address, null, body, null, null)
            "ok"
        } catch (e: SecurityException) {
            throw SecurityException("需要 SEND_SMS 权限", e)
        }
    }
}
