package com.reater.app.data.remote.threads

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Threads 頁面抓取器（模組化）。
 *
 * 2026-09 實測：一般 Chrome UA 只回 284KB 的 JS 殼（零 thread_items/零媒體）；
 * Googlebot UA 回 690KB 完整預渲染 payload——主貼在 "data":{"media":{...}}，
 * 留言在 data.media.text_post_app_info.direct_replies.edges[].node.posts.edges[].node
 * （含 caption/愛心/每則留言自己的 image/video）。故 Googlebot UA 為第一主力。
 *
 * 請求策略（依序，拿到足夠 payload 即止）：
 *  1. Googlebot UA：SSR 完整 payload（主貼 + direct_replies 留言 + 留言媒體）。
 *  2. desktop Chrome UA + 完整瀏覽器 headers：/share/ 跳轉與登入牆情境。
 *  3. crawler UA（facebookexternalhit）：Meta 對爬蟲的預渲染備援。
 *  4. threads.net 換域再試（對爬蟲更友好的舊驗證）。
 *
 *  /share/CODE 會 302 轉到正文（Apify 文件已證實可 resolve），OkHttp 跟隨 redirect
 *  後的 resolvedUrl 必須回傳給 Facade 做 shortcode/handle 重抽。
 */
object ThreadsPageFetcher {

    data class FetchResult(
        val resolvedUrl: String,
        val html: String,
        /** 是否曾拿到任一成功 HTML（即使內容是登入牆也算 true，解析層再判斷） */
        val gotAnyHtml: Boolean
    )

    private val googlebotUa =
        "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
    private val crawlerUa =
        "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    /**
     * 是否含真正的貼文 payload（新舊 shape 通吃）：
     *  - thread_items：舊 SSR shape
     *  - direct_replies：2026-09 新 shape 的留言樹
     *  - "data":{"media"：新 shape 的主貼物件（殼頁沒有）
     */
    fun hasPostPayload(body: String): Boolean {
        if (body.isBlank()) return false
        return body.contains("thread_items") ||
            body.contains("\"direct_replies\"") ||
            body.contains("\"data\":{\"media\"")
    }

    /**
     * 完整瀏覽器導航 headers（cobalt threads extractor 配方）。
     * Threads 對缺指紋的請求只回最小登入殼；送齊這組 headers 才會回嵌有
     * data-sjs 貼文 JSON 的頁面（與 instagram embeds 同行為）。
     */
    private fun browserHeaders(): Map<String, String> = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        "Accept-Language" to "en-GB,en;q=0.9",
        "Cache-Control" to "max-age=0",
        "Dnt" to "1",
        "Priority" to "u=0, i",
        "Sec-Ch-Ua" to "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"",
        "Sec-Ch-Ua-Mobile" to "?0",
        "Sec-Ch-Ua-Platform" to "\"Windows\"",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-User" to "?1",
        "Upgrade-Insecure-Requests" to "1",
        "User-Agent" to desktopUa
    )

    fun fetch(client: OkHttpClient, targetUrl: String): FetchResult {
        var resolved = targetUrl
        var html = ""
        var got = false

        fun betterThanCurrent(body: String): Boolean {
            if (body.length < 500) return false
            if (html.isBlank()) return true
            val hasPayload = hasPostPayload(body)
            val curHasPayload = hasPostPayload(html)
            // 含貼文 payload（留言/主貼）優先，即使較短；否則取較長者
            if (hasPayload && !curHasPayload) return true
            if (!hasPayload && curHasPayload) return false
            return body.length > html.length
        }

        fun saveIfBetter(body: String, respUrl: String) {
            if (respUrl.isNotBlank()) resolved = respUrl
            if (betterThanCurrent(body)) {
                html = body
                got = body.length > 500
            }
        }

        // Pass 1：Googlebot UA——2026-09 實測唯一能拿全 SSR payload（主貼+留言+留言媒體）的 UA
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", googlebotUa)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            client.newCall(req).execute().use { resp ->
                val rUrl = resp.request.url.toString()
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    saveIfBetter(body, rUrl)
                } else if (rUrl.isNotBlank()) {
                    resolved = rUrl
                }
            }
        } catch (_: Exception) {
        }

        // Pass 2：完整瀏覽器 headers（/share/ 跳轉、登入牆情境；payload 較少仍保留為備援）
        if (!hasPostPayload(html)) {
            try {
                val builder = Request.Builder()
                    .url(resolved.ifBlank { targetUrl })
                browserHeaders().forEach { (k, v) -> builder.header(k, v) }
                client.newCall(builder.build()).execute().use { resp ->
                    val rUrl = resp.request.url.toString()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        saveIfBetter(body, rUrl)
                    } else if (rUrl.isNotBlank()) {
                        resolved = rUrl
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Pass 3：內容太少 / 疑似登入殼 / 無貼文 payload → crawler UA 再試（Meta 對爬蟲預渲染 SSR）
        if (!hasPostPayload(html) || html.length < 8_000 || isLoginWall(html)) {
            try {
                val req = Request.Builder()
                    .url(resolved.ifBlank { targetUrl })
                    .header("User-Agent", crawlerUa)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val r2 = resp.request.url.toString()
                    if (r2.isNotBlank()) resolved = r2
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (betterThanCurrent(body)) {
                            html = body
                            got = body.length > 500
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Pass 4：免登入留言常需 threads.net SSR（2026 多源驗證：threads.net 對爬蟲更友好）
        // 若仍無貼文 payload，把 threads.com 換成 threads.net 再試一次
        if (!hasPostPayload(html)) {
            try {
                val altUrl = resolved.ifBlank { targetUrl }
                    .replace("www.threads.com", "www.threads.net")
                    .replace("threads.com", "threads.net")
                if (altUrl != resolved) {
                    val req = Request.Builder()
                        .url(altUrl)
                        .header("User-Agent", googlebotUa)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            if (betterThanCurrent(body)) {
                                html = body
                                got = body.length > 500
                                // resolved 保持原 com 連結（可開啟性），僅 html 用 net 版解析
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        return FetchResult(resolvedUrl = resolved, html = html, gotAnyHtml = got)
    }

    fun isLoginWall(html: String): Boolean {
        if (html.isBlank()) return true
        val l = html.lowercase()
        // 有內嵌貼文 JSON 就不算牆（即使同時有 login 字樣）
        if (hasPostPayload(html)) return false
        if (html.contains("\"caption\"") && html.contains("\"text\"")) return false
        if (html.contains("og:description") && !l.contains("log in to")) return false
        return l.contains("log in to threads") || l.contains("log in • threads") ||
            (l.contains("login") && html.length < 30_000 && !html.contains("\"thread_items\""))
    }

    /** 從 resolved URL 重抽 shortcode/handle（含 /share/）。 */
    data class ExtractedIds(val shortcode: String, val handle: String)

    fun extractIds(resolvedUrl: String, fallbackShortcode: String): ExtractedIds {
        var code = fallbackShortcode
        var handle = ""
        val post = Regex("""/@?([A-Za-z0-9_.]+)/post/([A-Za-z0-9_-]+)""").find(resolvedUrl)
        if (post != null) {
            handle = post.groupValues[1]
            code = post.groupValues[2]
        } else {
            val share = Regex("""/share/([A-Za-z0-9_-]+)""").find(resolvedUrl)
            if (share != null) {
                code = share.groupValues[1]
            } else {
                val t = Regex("""/t/([A-Za-z0-9_-]+)""").find(resolvedUrl)
                if (t != null) code = t.groupValues[1]
            }
        }
        return ExtractedIds(shortcode = code, handle = handle)
    }

    fun newClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}
