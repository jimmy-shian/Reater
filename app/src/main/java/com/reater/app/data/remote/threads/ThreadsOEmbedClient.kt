package com.reater.app.data.remote.threads

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * Threads oEmbed 客戶端（模組化）。
 *
 * 官方文件（developers.facebook.com/docs/threads/tools-and-resources/embed-a-threads-post）：
 *  只接受兩種 URL：
 *   - https://www.threads.com/@{username}/post/{shortcode}/
 *   - https://www.threads.com/t/{shortcode}/
 *  注意：/share/CODE 不被 oEmbed 接受 → 呼叫前必須先轉成上述兩種（由 Facade 保證）。
 *
 *  舊程式寫死 graph.threads.net 單一 host，現改為雙 host 容錯：
 *   graph.threads.com → graph.threads.net。
 */
object ThreadsOEmbedClient {

    data class OEmbedResult(
        val authorDisplayName: String,
        val authorHandle: String,
        val bodySnippet: String
    )

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun fetch(client: OkHttpClient, canonicalOrResolvedUrl: String): OEmbedResult? {
        val encoded = try {
            URLEncoder.encode(canonicalOrResolvedUrl, "UTF-8")
        } catch (_: Exception) {
            return null
        }
        val hosts = listOf(
            "https://graph.threads.com/oembed?url=$encoded",
            "https://graph.threads.net/oembed?url=$encoded"
        )
        for (endpoint in hosts) {
            val result = tryOnce(client, endpoint)
            if (result != null) return result
        }
        return null
    }

    private fun tryOnce(client: OkHttpClient, oembedUrl: String): OEmbedResult? {
        return try {
            val req = Request.Builder()
                .url(oembedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = response.body?.string().orEmpty()
                if (json.isBlank()) return null
                val parsed = jsonParser.parseToJsonElement(json).jsonObject
                val authorName = parsed["author_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val authorUrl = parsed["author_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                var handle = ""
                if (authorUrl.contains("@")) {
                    handle = authorUrl.substringAfter("@").trim().trimEnd('/')
                        .split("?", "/", "#").firstOrNull().orEmpty()
                }
                val html = parsed["html"]?.jsonPrimitive?.contentOrNull.orEmpty()
                var snippet = ""
                if (html.isNotBlank()) {
                    val m = Regex("""<div style="[^"]*font-size:\s*15px[^"]*">(.*?)</div>""")
                        .find(html)
                    if (m != null) {
                        val t = ThreadsHtmlParser.decodeHtml(m.groupValues[1].trim())
                        if (t.isNotBlank() && t != "View on Threads") snippet = t
                    }
                }
                if (authorName.isBlank() && handle.isBlank() && snippet.isBlank()) return null
                OEmbedResult(
                    authorDisplayName = authorName,
                    authorHandle = handle,
                    bodySnippet = snippet
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
