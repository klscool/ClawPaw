package com.example.clawpaw.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * 照片：从 MediaStore 读取最近图片（需 READ_MEDIA_IMAGES 或 READ_EXTERNAL_STORAGE）。
 */
object PhotosHelper {

    fun getLatestPhotos(context: Context, limit: Int = 50): JSONArray {
        val arr = JSONArray()
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )
        context.contentResolver.query(uri, projection, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { cursor ->
            val idIdx = cursor.getColumnIndex(MediaStore.Images.Media._ID)
            val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val dateIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
            val sizeIdx = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                arr.put(JSONObject().apply {
                    put("id", cursor.getLong(idIdx))
                    put("displayName", cursor.getString(nameIdx) ?: "")
                    put("dateAdded", cursor.getLong(dateIdx))
                    put("size", cursor.getLong(sizeIdx))
                })
                count++
            }
        }
        return arr
    }
}
