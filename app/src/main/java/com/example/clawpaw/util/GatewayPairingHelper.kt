package com.example.clawpaw.util

import android.content.Context
import android.net.Uri
import com.example.clawpaw.data.api.RetrofitClient

/**
 * 解析配对码/扫码内容为 (host, port, token?)。
 * 支持 Base64+JSON（含 url、token）、openclaw://host:port?token=、或 host:port。
 * 要求：host 非空且 token 非空才视为成功。
 */
object GatewayPairingHelper {

    /** 解析 Base64+JSON：含 url（含端口）、token。返回 (host, port, token?) 或 null */
    fun parseFromBase64Json(content: String): Triple<String, Int, String?>? {
        val raw = content.trim().ifEmpty { return null }
        val jsonStr = try {
            String(android.util.Base64.decode(raw, android.util.Base64.DEFAULT or android.util.Base64.NO_WRAP), Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        return try {
            val json = org.json.JSONObject(jsonStr)
            val url = json.optString("url", "").trim().ifEmpty { return null }
            val token = json.optString("token", "").trim().takeIf { it.isNotBlank() }
            val withScheme = if (url.contains("://")) url else "openclaw://$url"
            val uri = Uri.parse(withScheme)
            val host = uri.host ?: return null
            val port = if (uri.port != -1) uri.port else 18789
            Triple(host, port, token)
        } catch (_: Exception) {
            null
        }
    }

    /** 解析链接/配对码为 (host, port, token?)。支持 openclaw://、wss://、https:// 或 host:port */
    fun parseFromUrl(content: String): Triple<String, Int, String?>? {
        val s = content.trim().ifEmpty { return null }
        val withScheme = when {
            s.contains("://") -> s
            else -> "openclaw://$s"
        }
        return try {
            val uri = Uri.parse(withScheme)
            val host = uri.host ?: return null
            val port = if (uri.port != -1) uri.port else 18789
            val token = uri.getQueryParameter("token")?.takeIf { it.isNotBlank() }
            Triple(host, port, token)
        } catch (_: Exception) {
            null
        }
    }

    /** 先尝试 Base64+JSON，再尝试 URL。返回 (host, port, token) 或 null；要求 host 与 token 均非空 */
    fun parse(content: String): Triple<String, Int, String?>? {
        val parsed = parseFromBase64Json(content) ?: parseFromUrl(content) ?: return null
        val (host, port, token) = parsed
        if (host.isBlank() || token == null || token.isBlank()) return null
        return parsed
    }

    /** 解析并写入 RetrofitClient（需先 init）。返回 true 表示成功并已写入 */
    fun parseAndSaveToPrefs(context: Context, content: String): Boolean {
        RetrofitClient.init(context)
        val parsed = parse(content) ?: return false
        val (host, port, token) = parsed
        RetrofitClient.setServerHost(host)
        RetrofitClient.setGatewayPort(port)
        RetrofitClient.setGatewayToken(token ?: return false)
        return true
    }
}
