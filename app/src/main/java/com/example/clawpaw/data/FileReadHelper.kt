package com.example.clawpaw.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

/**
 * 文件读取：读取应用可访问路径或 ContentUri 的文本/Base64（需存储权限或 URI 权限）。
 */
object FileReadHelper {

    /**
     * 读取 path 对应文件为 UTF-8 文本。path 可为应用私有目录或已授权路径。
     */
    fun readText(context: Context, path: String): String? {
        return try {
            val file = File(path)
            if (!file.canRead()) return null
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 读取 path 对应文件为 Base64。
     */
    fun readBase64(context: Context, path: String): String? {
        return try {
            val file = File(path)
            if (!file.canRead()) return null
            FileInputStream(file).use { it.readBytes() }.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 通过 ContentUri 读取（如 SAF 返回的 uri）。返回 { content, fileName } 或 { error }。
     */
    fun readFromUri(context: Context, uriString: String): JSONObject {
        return try {
            val uri = Uri.parse(uriString)
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIdx >= 0) cursor.getString(nameIdx) else null
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return JSONObject().put("error", "无法打开流")
            val content = String(bytes, Charsets.UTF_8)
            JSONObject().put("content", content).put("fileName", name ?: "")
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "读取失败")
        }
    }
}
