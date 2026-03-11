package com.example.clawpaw.data

import android.content.Context
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * 日历：读取事件（需 READ_CALENDAR）、添加/更新事件（需 WRITE_CALENDAR）。
 */
object CalendarHelper {

    fun getEvents(context: Context, limit: Int = 100): JSONArray {
        val arr = JSONArray()
        val uri = CalendarContract.Events.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION
        )
        context.contentResolver.query(uri, projection, null, null, CalendarContract.Events.DTSTART + " DESC")?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
            val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
            val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
            val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)
            val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
            val descIdx = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                arr.put(JSONObject().apply {
                    put("id", cursor.getLong(idIdx))
                    put("title", cursor.getString(titleIdx) ?: "")
                    put("dtstart", cursor.getLong(startIdx))
                    put("dtend", cursor.getLong(endIdx))
                    put("location", cursor.getString(locIdx) ?: "")
                    put("description", cursor.getString(descIdx) ?: "")
                })
                count++
            }
        }
        return arr
    }
}
