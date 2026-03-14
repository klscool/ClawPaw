package com.example.clawpaw.util

import android.content.Context
import android.net.Uri
import com.example.clawpaw.data.api.RetrofitClient

/**
 * 解析配对码/扫码内容为 (host, port, token?)。
 * 支持两种格式：
 * 1. Base64(JSON)：JSON 需含 "url"（如 host:port 或 ws://host:port）、"token" 或 "bootstrapToken"。
 * 2. 链接：openclaw://host:port?token=xxx 或 wss://host:port?token=xxx 或 host:port?token=xxx。
 * 要求：host 与 token 均非空才视为成功。
 */
object GatewayPairingHelper {

    /** 解析结果：成功时为 (Triple, null)，失败时为 (null, 具体原因) */
    fun parseWithReason(content: String): Pair<Triple<String, Int, String>?, String?> {
        val raw = content.trim()
        if (raw.isEmpty()) return null to "内容为空"

        // 支持直接粘贴的 JSON（含 url、token 或 bootstrapToken）
        if (raw.startsWith("{")) {
            val fromJson = parseFromRawJsonWithReason(raw)
            if (fromJson.first != null) return fromJson
        }
        val fromBase64 = parseFromBase64JsonWithReason(raw)
        if (fromBase64.first != null) return fromBase64
        val fromUrl = parseFromUrlWithReason(raw)
        if (fromUrl.first != null) return fromUrl
        val reason = fromBase64.second?.takeIf { it != "Base64 解码失败" } ?: fromUrl.second
            ?: "无法识别格式：需为 Base64(JSON 含 url+token) 或链接(含 ?token=)"
        return null to reason
    }

    /** 解析纯 JSON 字符串（非 Base64）：需 url、token 或 bootstrapToken */
    private fun parseFromRawJsonWithReason(raw: String): Pair<Triple<String, Int, String>?, String?> {
        return try {
            val json = org.json.JSONObject(raw)
            val url = json.optString("url", "").trim()
            if (url.isEmpty()) return null to "JSON 中缺少 url 字段"
            val token = json.optString("token", "").trim().takeIf { it.isNotBlank() }
                ?: json.optString("bootstrapToken", "").trim().takeIf { it.isNotBlank() }
            if (token.isNullOrEmpty()) return null to "JSON 中缺少 token 或 bootstrapToken 字段"
            val withScheme = if (url.contains("://")) url else "openclaw://$url"
            val uri = Uri.parse(withScheme)
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return null to "url 中缺少主机地址"
            val port = if (uri.port != -1) uri.port else 18789
            Triple(host, port, token) to null
        } catch (e: Exception) {
            null to "JSON 格式错误：${e.message?.take(50) ?: "解析异常"}"
        }
    }

    /** 解析 Base64+JSON：需字段 url、token 或 bootstrapToken。返回 (Triple, null) 或 (null, 原因) */
    private fun parseFromBase64JsonWithReason(raw: String): Pair<Triple<String, Int, String>?, String?> {
        val jsonStr = try {
            String(android.util.Base64.decode(raw, android.util.Base64.DEFAULT or android.util.Base64.NO_WRAP), Charsets.UTF_8)
        } catch (_: Exception) {
            return null to "Base64 解码失败"
        }
        return parseFromRawJsonWithReason(jsonStr)
    }

    /** 解析 URL：需 query 含 token 或 bootstrapToken。返回 (Triple, null) 或 (null, 原因) */
    private fun parseFromUrlWithReason(s: String): Pair<Triple<String, Int, String>?, String?> {
        val withScheme = when {
            s.contains("://") -> s
            else -> "openclaw://$s"
        }
        return try {
            val uri = Uri.parse(withScheme)
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return null to "链接中缺少主机地址"
            val port = if (uri.port != -1) uri.port else 18789
            val token = uri.getQueryParameter("token")?.takeIf { it.isNotBlank() }
                ?: uri.getQueryParameter("bootstrapToken")?.takeIf { it.isNotBlank() }
                ?: return null to "链接中缺少 token 或 bootstrapToken（需 ?token=xxx 或 ?bootstrapToken=xxx）"
            Triple(host, port, token) to null
        } catch (e: Exception) {
            null to "链接格式错误：${e.message?.take(50) ?: "解析异常"}"
        }
    }

    /** 解析 Base64+JSON：含 url（含端口）、token。返回 (host, port, token?) 或 null */
    fun parseFromBase64Json(content: String): Triple<String, Int, String?>? {
        val (triple, _) = parseFromBase64JsonWithReason(content.trim())
        return triple
    }

    /** 解析链接/配对码为 (host, port, token?)。支持 openclaw://、wss://、https:// 或 host:port */
    fun parseFromUrl(content: String): Triple<String, Int, String?>? {
        val (triple, _) = parseFromUrlWithReason(content.trim())
        return triple
    }

    /** 先尝试 Base64+JSON，再尝试 URL。返回 (host, port, token) 或 null；要求 host 与 token 均非空 */
    fun parse(content: String): Triple<String, Int, String?>? {
        val (parsed, _) = parseWithReason(content)
        return parsed
    }

    /** 解析并写入 RetrofitClient（需先 init）。成功返回 null，失败返回具体原因字符串。写入的 token 类型由调用方通过 asNode 指定。 */
    fun parseAndSaveToPrefsWithReason(context: Context, content: String, asNode: Boolean): String? {
        RetrofitClient.init(context)
        val (parsed, reason) = parseWithReason(content)
        if (parsed == null) return reason
        val (host, port, token) = parsed
        if (asNode) saveParsedAsNode(context, host, port, token) else saveParsedAsOperator(context, host, port, token)
        return null
    }

    /** 将扫码/配对码结果保存为 Node 用 bootstrapToken（host、port、Node Token） */
    fun saveParsedAsNode(context: Context, host: String, port: Int, token: String) {
        RetrofitClient.init(context)
        RetrofitClient.setServerHost(host)
        RetrofitClient.setGatewayPort(port)
        RetrofitClient.setNodeToken(token)
        RetrofitClient.setHasNodeDeviceToken(false)
    }

    /** 将扫码/配对码结果保存为 Operator 用 bootstrapToken（host、port、Operator Token） */
    fun saveParsedAsOperator(context: Context, host: String, port: Int, token: String) {
        RetrofitClient.init(context)
        RetrofitClient.setServerHost(host)
        RetrofitClient.setGatewayPort(port)
        RetrofitClient.setOperatorToken(token)
        RetrofitClient.setHasOperatorDeviceToken(false)
    }
}
