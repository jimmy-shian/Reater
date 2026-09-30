package com.reater.app.data.remote.threads

import android.text.Html
import com.reater.app.data.remote.FetchedComment
import com.reater.app.data.remote.FetchedMedia
import java.util.regex.Pattern

/**
 * Threads SSR HTML 解析器（純函數模組，無 Android 網路依賴）。
 *
 * 為何之前抓不到內容/圖片/影片：
 *  - 舊程式只看 og:title / og:description，Threads 對未登入爬蟲常回傳
 *    「Threads • ...」通用文案或登入牆，導致 無內文。
 *  - 真正的內文藏在 SSR 內嵌 JSON：thread_items → caption{text}、
 *    image_versions / video_versions / carousel_media。
 *  - 舊程式只抓單張 og:image，輪播（多圖）、影片 variants 全漏掉。
 *
 * 解析順序：內嵌 JSON（最準）→ meta title → og/twitter → 去標籤文字兜底。
 */
object ThreadsHtmlParser {

    data class ParsedPage(
        val bodyText: String,
        val authorDisplayName: String,
        val authorHandleFromTitle: String,
        val authorProfileUrl: String,
        val authorVerified: Boolean,
        val postedAtMs: Long,
        val likeCount: Int,
        val media: List<FetchedMedia>,
        val comments: List<FetchedComment>,
        /** 是否命中 SJS 精確解析（短碼匹配成功） */
        val fromSjs: Boolean,
        /** /share/ 留言鏈母文（SJS 回溯；頂層串文為 null） */
        val parent: ThreadsSjsParser.ParentPost? = null
    )

    fun parse(html: String, shortcode: String): ParsedPage {
        if (html.isBlank()) return ParsedPage("", "", "", "", false, 0L, 0, emptyList(), emptyList(), false)
        // /share/ 未解析時（shortcode 為 share_TOKEN 佔位符）：HTML 裡沒有本篇資料，
        // 所有 caption/og 猜測都會抓到別篇或殼內容 → 直接回空，交給分享文字草稿兜底。
        // 否則會出現「7 則留言顯示 12 則、@threads_reply 配不相關內文」的污染。
        if (shortcode.startsWith("share_")) {
            return ParsedPage("", "", "", "", false, 0L, 0, emptyList(), emptyList(), false)
        }

        // 第一順位：SJS 內嵌 JSON（短碼精確匹配，最準；含留言鏈、輪播、GIF、計數）
        val sjs = try {
            ThreadsSjsParser.parse(html, shortcode)
        } catch (_: Exception) {
            null
        }
        if (sjs != null && (sjs.bodyText.isNotBlank() || sjs.media.isNotEmpty() || sjs.comments.isNotEmpty())) {
            return ParsedPage(
                bodyText = sjs.bodyText.trim(),
                authorDisplayName = sjs.authorDisplayName.trim(),
                authorHandleFromTitle = sjs.authorHandle.trim(),
                authorProfileUrl = sjs.authorProfileUrl,
                authorVerified = sjs.authorVerified,
                postedAtMs = sjs.postedAtMs,
                likeCount = sjs.likeCount,
                media = sjs.media,
                comments = sjs.comments,
                fromSjs = true,
                parent = sjs.parent
            )
        }
        // SJS 有命中但主貼文無內文無媒體（如純轉發）：仍沿用其留言/作者
        val sjsFallbackComments = sjs?.comments.orEmpty()
        val sjsFallbackAuthor = sjs?.authorHandle.orEmpty()

        val (ogTitleHandle, ogTitleName) = parseOgTitle(html)
        val embedded = parseEmbeddedPostJson(html, shortcode)
        val metaTitleBody = parseMetaTitleJson(html)

        var body = embedded.body.ifBlank { metaTitleBody }
        if (body.isBlank()) body = parseOgDescription(html)
        if (body.isBlank()) body = parseStrippedTextFallback(html)
        // og:title 有時長這樣：Name (@handle) on Threads: "內文..." —— 最後兜底拆出來
        if (body.isBlank()) body = parseOgTitleInlineText(html)

        val displayName = embedded.authorDisplayName.ifBlank { ogTitleName }
        val handleFromTitle =
            embedded.authorHandle.ifBlank { ogTitleHandle }.ifBlank { sjsFallbackAuthor }

        val media = mergeMedia(
            ogMedia = parseOgMedia(html),
            embeddedMedia = embedded.media
        ).toMutableList()
        // 前兩路都空才掃全頁 CDN（避免 shell 靜態資源污染已抓到的結果）
        if (media.isEmpty()) {
            media += scanCdnMedia(html)
        }

        val comments = if (embedded.comments.isNotEmpty()) {
            embedded.comments
        } else if (sjsFallbackComments.isNotEmpty()) {
            sjsFallbackComments
        } else {
            parseCaptionCommentsFallback(html, body)
        }

        return ParsedPage(
            bodyText = body.trim(),
            authorDisplayName = displayName.trim(),
            authorHandleFromTitle = handleFromTitle.trim(),
            authorProfileUrl = "",
            authorVerified = false,
            postedAtMs = 0L,
            likeCount = 0,
            media = media,
            comments = comments,
            fromSjs = false
        )
    }

    // ---------- og:title ----------

    private fun parseOgTitle(html: String): Pair<String, String> {
        val ogTitle = extractMetaContent(html, "og:title")
        if (ogTitle.isBlank()) return "" to ""
        val m = Regex("""^(.*?)\s*\(@([\w.]+)\)""").find(ogTitle)
        return if (m != null) {
            m.groupValues[2].trim() to m.groupValues[1].trim()
        } else {
            "" to ogTitle.substringBefore(" on Threads").trim()
        }
    }

    private fun parseOgTitleInlineText(html: String): String {
        val ogTitle = extractMetaContent(html, "og:title")
        if (ogTitle.isBlank()) return ""
        // 例：王小明 (@ming) on Threads: "今天天氣真好..." → 取引號內
        val quoted = Regex("""["「『](.+?)["」』]\s*$""", RegexOption.DOT_MATCHES_ALL).find(ogTitle)
        if (quoted != null) {
            val t = quoted.groupValues[1].trim()
            if (t.length > 2 && !t.startsWith("Threads")) return t
        }
        val afterColon = ogTitle.substringAfterLast(":").trim().trim('"', '“', '”', '「', '」')
        if (afterColon.length > 2 && afterColon != ogTitle && !afterColon.startsWith("Threads")) {
            return afterColon
        }
        return ""
    }

    // ---------- 內嵌 JSON（主力） ----------

    private data class EmbeddedResult(
        val body: String,
        val authorDisplayName: String,
        val authorHandle: String,
        val media: List<FetchedMedia>,
        val comments: List<FetchedComment>
    )

    private fun parseEmbeddedPostJson(html: String, shortcode: String): EmbeddedResult {
        // caption.text 全部候選（含轉義引號處理）
        val captionRegex = Regex(""""caption"\s*:\s*\{\s*"text"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val captions = captionRegex.findAll(html).map {
            unescapeJsonString(it.groupValues[1])
        }.filter { it.isNotBlank() }.toList()
        if (captions.isEmpty()) return EmbeddedResult("", "", "", emptyList(), emptyList())

        // 盡量把屬於 shortcode 那一篇的 caption 排第一：
        // 策略：在 "code":"SHORTCODE" 附近 ±8KB 找最近的 caption。
        var body = captions.firstOrNull().orEmpty()
        var bodyIdx = 0
        if (shortcode.isNotBlank()) {
            val codePos = html.indexOf("\"code\":\"$shortcode\"").takeIf { it >= 0 }
                ?: html.indexOf("\"code\": \"$shortcode\"").takeIf { it >= 0 }
            if (codePos != null) {
                var bestDist = Int.MAX_VALUE
                captionRegex.findAll(html).forEachIndexed { idx, m ->
                    val d = kotlin.math.abs(m.range.first - codePos)
                    if (d < bestDist) {
                        bestDist = d
                        bodyIdx = idx
                    }
                }
                if (bestDist < 8192) body = captions.getOrNull(bodyIdx).orEmpty()
            }
        }

        val comments = captions
            .filterIndexed { idx, t -> idx != bodyIdx && t != body && t.length > 1 }
            .take(30)
            .mapIndexedNotNull { idx, t ->
                // 兜底路徑仍須避免「抓不到真正留言人名稱」：無明確作者則捨棄，不用佔位符污染
                val author = findAuthorNearCaption(html, t)?.trim()?.trimStart('@')
                    .orEmpty().takeIf { it.isNotBlank() && !it.contains(" ") && it.length <= 30 }
                    ?: return@mapIndexedNotNull null
                FetchedComment(
                    externalId = "ssr_c_$idx",
                    author = author,
                    text = t,
                    likeCount = 0
                )
            }.take(20)

        // 作者：caption 附近的 username
        val author = if (body.isNotBlank()) {
            findAuthorNearCaption(html, body) ?: ""
        } else ""

        val media = parseEmbeddedMedia(html, shortcode)

        return EmbeddedResult(
            body = body,
            authorDisplayName = "",
            authorHandle = author,
            media = media,
            comments = comments
        )
    }

    private fun findAuthorNearCaption(html: String, captionText: String): String? {
        // caption 在 JSON 轉義後的形式可能與原文差異大，只取前 24 字定位
        val needle = captionText.take(24)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .takeIf { it.length >= 4 } ?: return null
        val pos = html.indexOf(needle.take(12))
        if (pos < 0) return null
        // 前後雙向取最近的 username（回覆物件 user 可能在 caption 前或後）
        val windowStart = maxOf(0, pos - 6000)
        val windowEnd = minOf(html.length, pos + 6000)
        val window = html.substring(windowStart, windowEnd)
        val userRegex = Regex(""""username"\s*:\s*"([A-Za-z0-9_.]{2,})"""")
        var best: String? = null
        var bestDist = Int.MAX_VALUE
        userRegex.findAll(window).forEach { m ->
            val absPos = windowStart + m.range.first
            val d = kotlin.math.abs(absPos - pos)
            if (d < bestDist) {
                bestDist = d
                best = m.groupValues[1]
            }
        }
        return best
    }

    private fun parseEmbeddedMedia(html: String, shortcode: String): List<FetchedMedia> {
        val out = linkedMapOf<String, FetchedMedia>()

        fun add(url: String, kind: String) {
            val clean = unescapeJsonString(url).trim()
            if (clean.isBlank() || !clean.startsWith("http")) return
            if (isJunkMediaUrl(clean)) return
            if (out.containsKey(clean)) return
            out[clean] = FetchedMedia(kind = kind, remoteUrl = clean)
        }

        // 限縮範圍：shortcode 附近 ±40KB（避免抓到全頁頭像/廣告圖）
        var scope = html
        if (shortcode.isNotBlank()) {
            val pos = html.indexOf("\"code\":\"$shortcode\"")
            if (pos >= 0) {
                scope = html.substring(
                    maxOf(0, pos - 40_000),
                    minOf(html.length, pos + 40_000)
                )
            }
        }

        // image_versions2 candidates[].url
        Regex(""""url"\s*:\s*"(https://[^"]*?(?:cdninstagram|fbcdn|scontent)[^"]*)"""")
            .findAll(scope).forEach { m ->
                val url = m.groupValues[1]
                if (url.contains(".mp4", ignoreCase = true)) add(url, "VIDEO") else add(url, "IMAGE")
            }
        // video_versions[].url / video_url / playable_url
        Regex(""""(?:video_url|playable_url)"\s*:\s*"((?:\\.|[^"\\])*)"""")
            .findAll(scope).forEach { m -> add(m.groupValues[1], "VIDEO") }
        Regex(""""video_versions"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            .findAll(scope).forEach { block ->
                Regex(""""url"\s*:\s*"((?:\\.|[^"\\])*)"""").findAll(block.groupValues[1])
                    .forEach { m -> add(m.groupValues[1], "VIDEO") }
            }

        return out.values.take(6)
    }

    private fun isJunkMediaUrl(url: String): Boolean {
        val l = url.lowercase()
        return l.contains("profile_pic") || l.contains("/avatar") ||
            l.contains("emoji") || l.contains("sprite") ||
            l.contains("s150x150") || l.contains("s72x72") ||
            // shell 靜態資源（實測每頁固定出現）：一律排除
            l.contains("static.cdninstagram") || l.contains("rsrc.php") ||
            l.endsWith(".svg") || l.endsWith(".ico") || l.endsWith(".js") || l.endsWith(".css")
    }

    // ---------- meta / og / 兜底 ----------

    private fun parseMetaTitleJson(html: String): String {
        val m = Regex(""""meta"\s*:\s*\{\s*"title"\s*:\s*"((?:\\.|[^"\\])*)"""").find(html)
            ?: return ""
        val candidate = unescapeJsonString(m.groupValues[1]).trim()
        if (candidate.isBlank() || candidate.startsWith("Threads") || candidate.length <= 2) return ""
        return candidate
    }

    private fun parseOgDescription(html: String): String {
        val ogDesc = extractMetaContent(html, "og:description")
        if (ogDesc.isNotBlank() && !ogDesc.startsWith("Threads")) return ogDesc
        val twDesc = extractMetaContent(html, "twitter:description")
        if (twDesc.isNotBlank() && !twDesc.startsWith("Threads")) return twDesc
        return ""
    }

    private fun parseOgMedia(html: String): List<FetchedMedia> {
        val out = linkedMapOf<String, FetchedMedia>()
        fun add(url: String, kind: String) {
            val u = url.trim()
            if (u.isBlank() || !u.startsWith("http") || isJunkMediaUrl(u)) return
            out.putIfAbsent(u, FetchedMedia(kind = kind, remoteUrl = u))
        }
        extractAllMetaContents(html, "og:image").forEach { add(it, "IMAGE") }
        extractAllMetaContents(html, "og:image:secure_url").forEach { add(it, "IMAGE") }
        extractAllMetaContents(html, "twitter:image").forEach { add(it, "IMAGE") }
        extractAllMetaContents(html, "og:video").forEach { add(it, "VIDEO") }
        extractAllMetaContents(html, "og:video:secure_url").forEach { add(it, "VIDEO") }
        extractAllMetaContents(html, "twitter:player:stream").forEach { add(it, "VIDEO") }
        return out.values.toList()
    }

    private fun mergeMedia(
        ogMedia: List<FetchedMedia>,
        embeddedMedia: List<FetchedMedia>
    ): List<FetchedMedia> {
        // 內嵌 JSON 優先（多圖/影片最準），og 當補充；去重後最多 6 個
        val merged = linkedMapOf<String, FetchedMedia>()
        (embeddedMedia + ogMedia).forEach { m ->
            if (m.remoteUrl.isNotBlank() && !merged.containsKey(m.remoteUrl)) {
                merged[m.remoteUrl] = m
            }
        }
        return merged.values.take(6)
    }

    /**
     * 全頁 CDN 直連兜底：掃描 HTML 內所有 scontent / 非靜態 cdninstagram 直連。
     * 實測 shell 內只有 static.cdninstagram 靜態資源（9 個固定 URL），必須排除；
     * 真正的貼文媒體 host 為 scontent（含地區前綴如 scontent-tpe1）。
     */
    fun scanCdnMedia(html: String): List<FetchedMedia> {
        val out = linkedMapOf<String, FetchedMedia>()
        // 帶副檔名的直連（最可信）
        Regex("""https://[A-Za-z0-9.-]*scontent[A-Za-z0-9.-]*[^"'\\\s]*?\.(?:jpg|jpeg|png|webp|gif|mp4)(?:\?[^"'\\\s]*)?""",
            RegexOption.IGNORE_CASE)
            .findAll(html).forEach { m ->
                val url = m.value
                if (isJunkMediaUrl(url) || out.containsKey(url)) return@forEach
                val kind = if (url.contains(".mp4", ignoreCase = true)) "VIDEO" else "IMAGE"
                out[url] = FetchedMedia(kind = kind, remoteUrl = url)
            }
        // 無副檔名的 CDN 連結（Meta CDN 常不帶副檔名，用參數區分）
        if (out.isEmpty()) {
            Regex("""https://[A-Za-z0-9.-]*scontent[A-Za-z0-9.-]*/[^"'\\\s]{16,}""")
                .findAll(html).forEach { m ->
                    var url = m.value.trimEnd('.', ',', ';', ')', '\\')
                    if (url.length < 40 || isJunkMediaUrl(url) || out.containsKey(url)) return@forEach
                    // 靜態資源一律排除
                    if (url.contains("static.cdninstagram", ignoreCase = true)) return@forEach
                    if (url.contains("rsrc.php", ignoreCase = true)) return@forEach
                    val kind = when {
                        url.contains(".mp4", ignoreCase = true) -> "VIDEO"
                        url.contains("video", ignoreCase = true) -> "VIDEO"
                        else -> "IMAGE"
                    }
                    out[url] = FetchedMedia(kind = kind, remoteUrl = url)
                }
        }
        return out.values.take(6)
    }

    private fun parseCaptionCommentsFallback(html: String, body: String): List<FetchedComment> {
        val regex = Regex(""""caption"\s*:\s*\{\s*"text"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return regex.findAll(html).mapIndexedNotNull { idx, m ->
            val text = unescapeJsonString(m.groupValues[1])
            if (text.isBlank() || text == body) null
            else {
                val author = findAuthorNearCaption(html, text)?.trim()?.trimStart('@')
                    .orEmpty().takeIf { it.isNotBlank() && !it.contains(" ") && it.length <= 30 }
                    ?: return@mapIndexedNotNull null
                FetchedComment(
                    externalId = "ssr_c_$idx",
                    author = author,
                    text = text,
                    likeCount = 0
                )
            }
        }.take(20).toList()
    }

    private fun parseStrippedTextFallback(html: String): String {
        val stripped = html
            .replace(Regex("""<script.*?</script>""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""<style.*?</style>""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""<[^>]+>"""), "\n")
        val blocklist = listOf(
            "Threads", "Log in", "Sign up", "登入", "註冊", "Download", "下載",
            "Privacy", "Terms", "隱私", "條款", "Meta", "©"
        )
        val lines = stripped.split("\n")
            .map { decodeHtml(it.trim()) }
            .filter { line ->
                line.length > 5 && blocklist.none { line.startsWith(it) } && !line.startsWith("http")
            }
        return lines.firstOrNull().orEmpty()
    }

    // ---------- 小工具 ----------

    fun extractMetaContent(html: String, metaNameOrProperty: String): String {
        return extractAllMetaContents(html, metaNameOrProperty).firstOrNull().orEmpty()
    }

    fun extractAllMetaContents(html: String, metaNameOrProperty: String): List<String> {
        val out = mutableListOf<String>()
        val q = Pattern.quote(metaNameOrProperty)
        val p1 = Pattern.compile(
            """<meta[^>]*?(?:property|name)=["']$q["'][^>]*?content=["']([^"']*)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val m1 = p1.matcher(html)
        while (m1.find()) {
            val v = decodeHtml(m1.group(1).orEmpty())
            if (v.isNotBlank()) out.add(v)
        }
        val p2 = Pattern.compile(
            """<meta[^>]*?content=["']([^"']*)["'][^>]*?(?:property|name)=["']$q["']""",
            Pattern.CASE_INSENSITIVE
        )
        val m2 = p2.matcher(html)
        while (m2.find()) {
            val v = decodeHtml(m2.group(1).orEmpty())
            if (v.isNotBlank()) out.add(v)
        }
        return out.distinct()
    }

    fun decodeHtml(input: String): String {
        return try {
            Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } catch (_: Exception) {
            input.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#064;", "@")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
        }
    }

    fun unescapeJsonString(input: String): String {
        return input.replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "")
            .replace("\\t", "\t")
            .replace("\\/", "/")
            .replace(Regex("""\\u([0-9a-fA-F]{4})""")) {
                it.groupValues[1].toInt(16).toChar().toString()
            }
    }
}
