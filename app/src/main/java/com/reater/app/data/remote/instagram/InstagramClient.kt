package com.reater.app.data.remote.instagram

import com.reater.app.data.remote.FetchedComment
import com.reater.app.data.remote.FetchedMedia
import com.reater.app.data.remote.FetchedPostResult
import com.reater.app.data.remote.MediaDedup
import com.reater.app.data.remote.MediaDownloader
import com.reater.app.data.remote.threads.TopicTags
import com.reater.app.domain.UrlParser
import com.reater.app.data.remote.FetchProgressInfo
import com.reater.app.data.remote.FetchStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Instagram 抓取 Facade（模組化入口，Hilt 只需注入此類）。
 *
 * 實測（2026-10，貼文 De_mAFQH-sr 驗證）：
 *  - 桌面 Chrome UA → IG 回傳 512KB JS 空殼（無 og meta、無 JSON）→ 一律失敗。
 *  - Googlebot UA → 回傳完整 HTML，其中 <script type="application/json"> 區塊
 *    內含：caption（shortcode 節點 caption.text）、image_versions2.candidates
 *    （每格 carousel 候選清單）、video_versions、like_count / comment_count /
 *    taken_at、留言節點（text + user.username + parent_comment_id）。
 *    → 這是主要資料 pass。
 *  - iPhone Safari UA → 無 JSON，但 og:title / og:description 帶完整 caption
 *    （含 "17K likes, 62 comments - author on October 26, 2026:" 前綴）。
 *    → caption/作者/計數兜底 pass。
 *  - embed/captioned → 有時 302 登入牆，僅作最後兜底。
 */
@Singleton
class InstagramClient @Inject constructor(
    private val mediaDownloader: MediaDownloader
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val googlebotUa = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
    private val iphoneUa =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

    private data class PageResponse(val html: String, val finalUrl: String)

    private fun resolveShortcode(url: String): String? {
        return UrlParser.parseInstagramUrl(url)?.shortcode?.takeIf { !it.startsWith("igshare_") }
    }

    suspend fun fetchPost(url: String, fallbackShortcode: String, onProgress: ((FetchProgressInfo, FetchedPostResult) -> Unit)? = null): Result<FetchedPostResult> {
        return withContext(Dispatchers.IO) {
            try {
                val parse = UrlParser.parseInstagramUrl(url)
                val initialUrl = parse?.fetchUrl ?: url

                // Pass 1：Googlebot（主要資料來源：caption + 媒體 + 留言 + 計數）
                val pass1 = fetchPage(initialUrl, googlebotUa)
                val agg = Aggregator(
                    shortcode = resolveShortcode(pass1.finalUrl)
                        ?: parse?.shortcode?.takeIf { !it.startsWith("igshare_") }
                        ?: fallbackShortcode
                )
                agg.walkHtml(pass1.html)
                agg.applyOg(pass1.html)

                // Pass 2：iPhone UA（og:description 兜底：作者/計數/日期/全文 caption）
                if (agg.body.isBlank()) {
                    val pass2 = fetchPage(initialUrl, iphoneUa)
                    resolveShortcode(pass2.finalUrl)?.let {
                        if (agg.shortcode.startsWith("igshare_") || agg.shortcode.isBlank()) agg.shortcode = it
                    }
                    agg.walkHtml(pass2.html)
                    if (agg.body.isBlank()) agg.applyOg(pass2.html)
                }

                // Pass 3：embed 兜底（有時登入牆，失敗無害）
                if (agg.body.isBlank()) {
                    val pass3 = fetchPage(initialUrl.trimEnd('/') + "/embed/captioned/", iphoneUa)
                    if (agg.body.isBlank()) agg.applyOg(pass3.html)
                    if (agg.body.isBlank()) agg.applyEmbed(pass3.html)
                }

                val comments = agg.comments.take(50)
                val finalHandle = agg.authorHandle.ifBlank { "instagram_user" }
                val finalShortcode = agg.shortcode.ifBlank { fallbackShortcode }

                var currentResult = FetchedPostResult(
                    shortcode = finalShortcode,
                    authorHandle = finalHandle,
                    authorDisplayName = agg.authorDisplayName.ifBlank { agg.authorHandle },
                    authorProfileUrl = agg.mainAuthorProfile,
                    authorVerified = false,
                    postedAt = agg.postedAt,
                    bodyText = agg.body,
                    likeCount = agg.likeCount,
                    replyCount = agg.commentCount,
                    repostCount = 0,
                    comments = comments,
                    media = emptyList(),
                    rawJsonMin = """{"code":"$finalShortcode"}""",
                    resolvedUrl = pass1.finalUrl.ifBlank { "https://www.instagram.com/p/$finalShortcode/" },
                    status = "PARTIAL",
                    topicTag = TopicTags.firstHashtag(agg.body)
                )

                // 階段 2：已取得貼文內文
                onProgress?.invoke(FetchProgressInfo(stage = FetchStage.BODY_PARSED, message = "已取得貼文內文"), currentResult)

                // 階段 3：若有留言則通知已取得精華留言
                if (comments.isNotEmpty()) {
                    onProgress?.invoke(FetchProgressInfo(stage = FetchStage.FETCHING_COMMENTS, message = "已擷取精華留言 (${comments.size} 則)"), currentResult)
                }

                // 階段 4：分批非同步下載媒體
                val distinctMedia = MediaDedup.distinctFetched(agg.mediaList)
                val totalMedia = distinctMedia.size
                var failedMediaCount = 0
                var succeededMediaCount = 0

                if (totalMedia > 0) {
                    // 先把完整媒體清單（localPath 尚空）放進結果：預覽可先用遠端圖顯示，
                    // 中途按「背景下載」或抓取被取消時，未下載的媒體不會消失，
                    // 存檔後 MediaDownloadWorker 才有東西可以接續下載。
                    currentResult = currentResult.copy(media = distinctMedia)
                    onProgress?.invoke(
                        FetchProgressInfo(stage = FetchStage.DOWNLOADING_MEDIA, message = "正在下載媒體 (0/$totalMedia)", mediaIndex = 0, mediaTotal = totalMedia),
                        currentResult
                    )
                    distinctMedia.forEachIndexed { index, m ->
                        var lastReported = 0L
                        val localPath = mediaDownloader.downloadMedia(m.remoteUrl, finalShortcode, index) { p ->
                            val now = System.currentTimeMillis()
                            if (now - lastReported >= 250) {
                                lastReported = now
                                onProgress?.invoke(
                                    FetchProgressInfo(
                                        stage = FetchStage.DOWNLOADING_MEDIA,
                                        message = "正在下載媒體 (${succeededMediaCount + 1}/$totalMedia)",
                                        mediaIndex = succeededMediaCount + 1,
                                        mediaTotal = totalMedia,
                                        bytesRead = p.bytesRead,
                                        totalBytes = p.totalBytes,
                                        speedBps = p.speedBytesPerSec,
                                        etaSeconds = p.etaSeconds
                                    ),
                                    currentResult
                                )
                            }
                        }
                        // 下載失敗／尚未下載也保留同一筆（localPath 為空），交由背景 Worker 續傳
                        currentResult = currentResult.copy(
                            media = currentResult.media.mapIndexed { i, existing ->
                                if (i == index) existing.copy(localPath = localPath) else existing
                            }
                        )
                        if (localPath.isNotBlank()) succeededMediaCount++ else failedMediaCount++
                        // 每完成一個媒體，即時更新 UI
                        onProgress?.invoke(
                            FetchProgressInfo(
                                stage = FetchStage.DOWNLOADING_MEDIA,
                                message = "正在下載媒體 ($succeededMediaCount/$totalMedia)",
                                mediaIndex = succeededMediaCount,
                                mediaTotal = totalMedia
                            ),
                            currentResult
                        )
                    }
                }

                // 階段 5：完成或部分成功
                val downloadedCount = currentResult.media.count { it.localPath.isNotBlank() }
                val status = when {
                    agg.body.isNotBlank() && failedMediaCount == 0 && (comments.isNotEmpty() || currentResult.media.isNotEmpty()) -> "COMPLETE"
                    agg.body.isNotBlank() || currentResult.media.isNotEmpty() || comments.isNotEmpty() -> "PARTIAL"
                    else -> "FAILED"
                }
                val finalStage = if (failedMediaCount == 0) FetchStage.COMPLETED else FetchStage.PARTIAL_SUCCESS
                val finalMsg = if (failedMediaCount == 0) "已完成下載離線媒體" else "已完成部分下載（成功 $downloadedCount 個，失敗 $failedMediaCount 個，其餘交由背景續傳）"
                currentResult = currentResult.copy(status = status)
                onProgress?.invoke(
                    FetchProgressInfo(stage = finalStage, message = finalMsg, mediaIndex = downloadedCount, mediaTotal = totalMedia),
                    currentResult
                )
                Result.success(currentResult)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ─────────────────────── 抓取聚合器 ───────────────────────

    private inner class Aggregator(
        /** 主貼短碼：統計/作者/內文只採計此節點，避免推薦貼文污染 */
        var shortcode: String,
        var body: String = "",
        var authorHandle: String = "",
        var authorDisplayName: String = "",
        var mainAuthorProfile: String = "",
        var likeCount: Int = 0,
        var commentCount: Int = 0,
        var postedAt: Long = 0L
    ) {
        val mediaList = mutableListOf<FetchedMedia>()
        val comments = mutableListOf<FetchedComment>()
        private var nodesWalked = 0

        /** 加入媒體（URL 去重；先到先得） */
        private fun addMedia(m: FetchedMedia) {
            if (m.remoteUrl.isBlank() || !m.remoteUrl.startsWith("http")) return
            if (mediaList.none { it.remoteUrl == m.remoteUrl }) mediaList.add(m)
        }

        /**
         * 從主貼節點收集內文/作者/計數，並只在其子樹內收集媒體
         * （image_versions2 / video_versions / video_url）。
         * 可被多個命中節點呼叫：欄位以「先填為主、數值取最大」合併，媒體靠 URL 去重。
         */
        private fun collectMain(node: JSONObject, depth: Int) {
            // 內文：caption 物件（xdt 新版）或字串（舊版）。
            // optString() 遇到 JSON null 會回傳字面 "null"，會使 og:description 兜底被跳過。
            if (body.isBlank()) {
                val captionText = when (val caption = node.opt("caption")) {
                    is JSONObject -> caption.stringOrNull("text")
                    is String -> caption.takeIf(::isUsableText)
                    else -> null
                } ?: node.optJSONObject("edge_media_to_caption")
                    ?.optJSONArray("edges")
                    ?.optJSONObject(0)
                    ?.optJSONObject("node")
                    ?.stringOrNull("text")
                if (captionText != null) body = captionText
            }
            val likes = node.optInt("like_count", 0)
            if (likes > likeCount) likeCount = likes
            val cc = node.optInt("comment_count", 0)
            if (cc > commentCount) commentCount = cc
            val ts = node.optLong("taken_at", 0L).takeIf { it > 0 }
                ?: node.optLong("taken_at_timestamp", 0L)
            if (ts > 0) postedAt = normalizeTs(ts)
            val mainUser = node.optJSONObject("user")
            val mainHandle = mainUser?.stringOrNull("username")
                ?: node.optJSONObject("owner")?.stringOrNull("username")
            if (!mainHandle.isNullOrBlank() && authorHandle.isBlank()) {
                authorHandle = mainHandle
                authorDisplayName = mainHandle
            }
            if (mainAuthorProfile.isBlank()) {
                mainAuthorProfile = mainUser?.stringOrNull("profile_pic_url").orEmpty()
            }
            collectMediaWithin(node, depth)
        }

        /** 媒體子樹收集：image_versions2 候選[0]（最大圖）、video_versions[0]（最高畫質） */
        private fun collectMediaWithin(node: Any?, depth: Int) {
            if (depth > 15 || nodesWalked > 200_000) return
            when (node) {
                is JSONObject -> {
                    nodesWalked++
                    val iv2 = node.optJSONObject("image_versions2")
                    val candidates = iv2?.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val c = candidates.optJSONObject(0)
                        if (c != null) {
                            addMedia(
                                FetchedMedia(
                                    kind = "image",
                                    remoteUrl = c.optString("url"),
                                    width = node.optInt("original_width", c.optInt("width", 0)),
                                    height = node.optInt("original_height", c.optInt("height", 0))
                                )
                            )
                        }
                    }
                    val vids = node.optJSONArray("video_versions")
                    if (vids != null && vids.length() > 0) {
                        val first = vids.optJSONObject(0)
                        if (first != null) {
                            addMedia(
                                FetchedMedia(
                                    kind = "video",
                                    remoteUrl = first.optString("url"),
                                    width = first.optInt("width", 0),
                                    height = first.optInt("height", 0)
                                )
                            )
                        }
                    }
                    for (vk in listOf("video_url", "playable_url", "playable_url_quality_hd")) {
                        val v = node.optString(vk, "")
                        if (v.startsWith("http")) addMedia(FetchedMedia(kind = "video", remoteUrl = v))
                    }
                    val keys = node.keys().asSequence().toList()
                    for (key in keys) collectMediaWithin(node.opt(key), depth + 1)
                }
                is JSONArray -> {
                    nodesWalked++
                    for (i in 0 until node.length()) collectMediaWithin(node.opt(i), depth + 1)
                }
            }
        }

        fun walkHtml(html: String) {
            if (html.isBlank()) return
            nodesWalked = 0
            for (js in extractScriptJsonBlocks(html)) {
                try {
                    val root = if (js.trimStart().startsWith("[")) JSONArray(js) else JSONObject(js)
                    walkJson(root, depth = 0)
                } catch (_: Exception) { /* 非 JSON 區塊略過 */ }
            }
        }

        private fun extractScriptJsonBlocks(html: String): List<String> {
            val blocks = mutableListOf<String>()
            Regex("""<script type="application/json"[^>]*>([\s\S]*?)</script>""").findAll(html).forEach {
                val t = it.groupValues[1].trim()
                if (t.startsWith("{") || t.startsWith("[")) blocks.add(t)
            }
            Regex("""window\._sharedData\s*=\s*(\{[\s\S]*?\});\s*</script>""").find(html)?.let {
                blocks.add(it.groupValues[1])
            }
            Regex("""window\.__additionalDataLoaded\s*\(\s*'[^']*'\s*,\s*(\{[\s\S]*\})\s*\)\s*;""").find(html)?.let {
                blocks.add(it.groupValues[1])
            }
            return blocks
        }

        private fun walkJson(node: Any?, depth: Int, inCommentTree: Boolean = false) {
            if (depth > 25 || nodesWalked > 200_000) return
            when (node) {
                is JSONObject -> {
                    nodesWalked++
                    // 主貼節點：內文/作者/計數/媒體（每次命中都合併，去重保證安全；
                    // 避免只帶 code 的連結預覽小節點先命中就蓋住完整資料節點）
                    val nodeCode = node.stringOrNull("shortcode") ?: node.stringOrNull("code").orEmpty()
                    if (nodeCode.isNotBlank() && nodeCode == shortcode) {
                        collectMain(node, 0)
                    }

                    // 限定在 comments/replies 子樹，並相容 owner/user 兩種作者欄位。
                    val text = node.stringOrNull("text").orEmpty()
                    if (inCommentTree && text.isNotBlank()) {
                        val user = node.optJSONObject("user") ?: node.optJSONObject("owner")
                        val username = user?.stringOrNull("username").orEmpty()
                        if (username.isNotBlank()) {
                            val parentId = node.stringOrNull("parent_comment_id")
                                ?: node.stringOrNull("parent_id").orEmpty()
                            val likes = node.optInt("comment_like_count", 0)
                                .takeIf { it > 0 }
                                ?: node.optInt("like_count", 0).takeIf { it > 0 } ?: 0
                            val existing = comments.none {
                                it.author == username && it.text == text
                            }
                            if (existing) {
                                comments.add(
                                    FetchedComment(
                                        externalId = node.stringOrNull("pk")
                                            ?: node.stringOrNull("id")
                                            ?: "ig_${text.hashCode()}",
                                        author = username,
                                        text = text,
                                        likeCount = likes,
                                        parentExternalId = parentId.takeIf { it.isNotBlank() },
                                        depth = if (parentId.isBlank()) 0 else 1,
                                        authorProfileUrl = user?.stringOrNull("profile_pic_url").orEmpty()
                                    )
                                )
                            }
                        }
                    }

                    val keys = node.keys().asSequence().toList()
                    for (key in keys) {
                        val childIsCommentTree = inCommentTree ||
                            key.contains("comment", ignoreCase = true) ||
                            key.contains("repl", ignoreCase = true)
                        walkJson(node.opt(key), depth + 1, childIsCommentTree)
                    }
                }
                is JSONArray -> {
                    nodesWalked++
                    for (i in 0 until node.length()) walkJson(node.opt(i), depth + 1, inCommentTree)
                }
            }
        }

        fun applyOg(html: String) {
            if (html.isBlank()) return
            for (meta in extractOgMetas(html)) {
                when (meta.first) {
                    "og:description", "twitter:description" -> {
                        if (body.isBlank()) parseOgDescription(meta.second)
                    }
                    "og:title", "twitter:title" -> {
                        val at = Regex("""\(@([A-Za-z0-9_.]+)\)""").find(meta.second)
                        if (at != null && authorHandle.isBlank()) {
                            authorHandle = at.groupValues[1]
                            authorDisplayName = meta.second.substringBefore("(").trim().ifBlank { authorHandle }
                        }
                    }
                    "og:image", "twitter:image" -> {
                        if (mediaList.isEmpty() && meta.second.startsWith("http")) {
                            addMedia(FetchedMedia(kind = "image", remoteUrl = meta.second))
                        }
                    }
                    "og:video", "twitter:player:stream" -> {
                        if (meta.second.startsWith("http")) {
                            addMedia(FetchedMedia(kind = "video", remoteUrl = meta.second))
                        }
                    }
                }
            }
        }

        /**
         * og:description 形如：
         * "17K likes, 62 comments - yoo_may_ on October 26, 2026: "#광고\n..."
         * 解析出 讚數 / 留言數 / 作者 / 日期 / 完整 caption。
         */
        private fun parseOgDescription(raw: String) {
            val decoded = decodeHtmlEntities(raw)
            if (!isUsableText(decoded)) return
            val m = Regex(
                """^(?:(\d[\d.,]*\s*[KkMm]?)\s*likes?(?:,\s*(\d[\d.,]*\s*[KkMm]?)\s*comments?)?\s*-\s*)?([^\s:]+)\s+on\s+([A-Za-z]+\s+\d{1,2},\s+\d{4}):\s*"?(.*?)"?\s*$""",
                RegexOption.DOT_MATCHES_ALL
            ).find(decoded)
            if (m != null) {
                val likes = parseCount(m.groupValues[1])
                val cmts = parseCount(m.groupValues[2])
                if (likes > likeCount) likeCount = likes
                if (cmts > commentCount) commentCount = cmts
                val handle = m.groupValues[3].removePrefix("@")
                if (authorHandle.isBlank() && handle.matches(Regex("""[A-Za-z0-9_.]+"""))) {
                    authorHandle = handle
                    authorDisplayName = handle
                }
                val ts = parsePostDate(m.groupValues[4])
                if (ts > 0 && postedAt == 0L) postedAt = ts
                body = m.groupValues[5].trim().takeIf(::isUsableText).orEmpty()
            } else if (body.isBlank()) {
                body = decoded.trim().takeIf(::isUsableText).orEmpty()
            }
        }

        fun applyEmbed(html: String) {
            if (html.isBlank()) return
            if (authorHandle.isBlank()) {
                Regex("""class="UsernameText"[^>]*>([^<]+)<""").find(html)?.let {
                    authorHandle = it.groupValues[1].trim()
                    authorDisplayName = authorHandle
                }
            }
            Regex("""class="EmbeddedMediaImage"[^>]*src="([^"]+)"""").find(html)?.let {
                addMedia(FetchedMedia(kind = "image", remoteUrl = decodeHtmlEntities(it.groupValues[1])))
            }
            if (body.isBlank()) {
                val cap = Regex("""class="Caption"[^>]*>([\s\S]*?)</div>""").find(html) ?: return
                val plain = cap.groupValues[1]
                    .replace(Regex("<[^>]+>"), "\n")
                    .replace(Regex("""\n{2,}"""), "\n")
                val cleaned = decodeHtmlEntities(plain)
                    .replace(Regex("""^\s*@?[A-Za-z0-9_.]{2,30}\s*"""), "")
                    .replace(Regex("""View this post on Instagram[\s\S]*$"""), "")
                    .trim()
                if (cleaned.isNotBlank()) body = cleaned
            }
        }

        private fun parsePostDate(text: String): Long {
            return try {
                LocalDate.parse(text.trim(), DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    LocalDate.parse(text.trim(), DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))
                        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    0L
                }
            }
        }
    }

    private fun parseCount(raw: String): Int {
        val t = raw.trim().replace(",", "").uppercase()
        if (t.isBlank()) return 0
        return try {
            when {
                t.endsWith("K") -> (t.dropLast(1).trim().toDouble() * 1000).toInt()
                t.endsWith("M") -> (t.dropLast(1).trim().toDouble() * 1_000_000).toInt()
                else -> t.replace(Regex("[^0-9]"), "").ifBlank { "0" }.toInt()
            }
        } catch (_: Exception) {
            0
        }
    }

    /** IG taken_at 為秒級 epoch；避免存成 1970 年（毫秒判定） */
    private fun normalizeTs(sec: Long): Long {
        return if (sec < 10_000_000_000L) sec * 1000L else sec
    }

    /** 讀取 JSON 字串欄位時略過缺值、JSON null 與 API 回傳的 null/undefined 字串。 */
    private fun JSONObject.stringOrNull(key: String): String? = when (val value = opt(key)) {
        null, JSONObject.NULL -> null
        is String -> value.takeIf(::isUsableText)
        else -> null
    }

    private fun isUsableText(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.isNotEmpty() &&
            !trimmed.equals("null", ignoreCase = true) &&
            !trimmed.equals("undefined", ignoreCase = true)
    }

    // ─────────────────────── 基礎工具 ───────────────────────

    private fun fetchPage(url: String, ua: String): PageResponse {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", ua)
                .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val html = if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                PageResponse(html, resp.request.url.toString())
            }
        } catch (_: Exception) {
            PageResponse("", "")
        }
    }

    /** og/twitter meta（content 在前在後皆可） */
    private fun extractOgMetas(html: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val regexes = listOf(
            Regex("""<meta[^>]+(?:property|name)="(og:[^"]+|twitter:[^"]+)"[^>]*content="([^"]*)"[\s\S]{0,10}?>"""),
            Regex("""<meta[^>]+content="([^"]*)"[^>]*(?:property|name)="(og:[^"]+|twitter:[^"]+)"""")
        )
        for (regex in regexes) {
            for (m in regex.findAll(html)) {
                val (prop, content) = if (regex === regexes[0]) {
                    m.groupValues[1] to m.groupValues[2]
                } else {
                    m.groupValues[2] to m.groupValues[1]
                }
                out.add(prop to decodeHtmlEntities(content))
            }
        }
        return out
    }

    /** HTML entity 解碼（"、&#xhhhh;、&#ddd; 等，og:description 常見） */
    private fun decodeHtmlEntities(s: String): String {
        if (!s.contains('&')) return s
        var out = s
        val numeric = Regex("""&#x([0-9A-Fa-f]+);|&#(\d+);""")
        out = numeric.replace(out) { m ->
            val hex = m.groupValues[1]
            val dec = m.groupValues[2]
            val cp = if (hex.isNotEmpty()) hex.toInt(16) else dec.toInt()
            String(Character.toChars(cp))
        }
        return out
            .replace("\u0026quot;", "\"")
            .replace("\u0026apos;", "'")
            .replace("&#039;", "'")
            .replace("\u0026nbsp;", " ")
            .replace("\u0026lt;", "<")
            .replace("\u0026gt;", ">")
            .replace("\u0026amp;", "&")
    }
}
