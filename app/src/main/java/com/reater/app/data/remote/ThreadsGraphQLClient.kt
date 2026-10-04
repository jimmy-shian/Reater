package com.reater.app.data.remote

import com.reater.app.data.remote.threads.ThreadsHtmlParser
import com.reater.app.data.remote.threads.ThreadsOEmbedClient
import com.reater.app.data.remote.threads.ThreadsPageFetcher
import com.reater.app.data.remote.threads.ThreadsSjsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class FetchedComment(
    val externalId: String,
    val author: String,
    val text: String,
    val likeCount: Int,
    val parentExternalId: String? = null,
    val depth: Int = 0,
    /** 留言自帶的圖/影（新 shape direct_replies 每則留言可有 image/video/carousel） */
    val media: List<FetchedMedia> = emptyList()
)

@Serializable
data class FetchedMedia(
    val kind: String,
    val remoteUrl: String,
    val localPath: String = "",
    val width: Int = 0,
    val height: Int = 0
)

/** FetchedMedia 列表的 JSON 編解碼（留言 mediaJson 欄位用） */
object FetchedMediaJson {
    private val codec = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val listSerializer = kotlinx.serialization.builtins.ListSerializer(FetchedMedia.serializer())

    fun encode(media: List<FetchedMedia>): String {
        if (media.isEmpty()) return ""
        return runCatching { codec.encodeToString(listSerializer, media) }.getOrDefault("")
    }

    fun decode(raw: String?): List<FetchedMedia> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { codec.decodeFromString(listSerializer, raw) }.getOrElse { emptyList() }
    }
}

data class FetchedPostResult(
    val shortcode: String,
    val authorHandle: String,
    val authorDisplayName: String,
    val authorProfileUrl: String,
    val authorVerified: Boolean,
    val postedAt: Long,
    val bodyText: String,
    val likeCount: Int,
    val replyCount: Int,
    val repostCount: Int,
    val comments: List<FetchedComment>,
    val media: List<FetchedMedia>,
    val rawJsonMin: String,
    val resolvedUrl: String = "",
    val status: String, // COMPLETE, PARTIAL, FAILED
    /** /share/ 留言鏈母文（主本身為留言時；頂層串文為 null） */
    val parentShortcode: String = "",
    val parentAuthorHandle: String = "",
    val parentBodyText: String = "",
    val parentLikeCount: Int = 0,
    val parentPostedAt: Long = 0L,
    val parentMedia: List<FetchedMedia> = emptyList(),
    /**
     * 完整祖先鏈（root → … → 直接父層；「留言的留言」為多層）。
     * parent* 單欄位永遠指向鏈首（母文），維持舊語義；多層存檔/預覽請用此鏈組裝。
     */
    val parentChain: List<ThreadsSjsParser.ParentPost> = emptyList()
)

/**
 * Threads 抓取 Facade（模組化入口，Hilt 只需注入此類）。
 *
 * 流程：
 *  1. ThreadsPageFetcher.fetch(targetUrl)：crawler UA + desktop UA 雙 pass，
 *     自動跟隨 /share/ → 正文 302。
 *  2. ThreadsPageFetcher.extractIds(resolvedUrl)：重抽 shortcode/handle（含 /share/）。
 *  3. ThreadsHtmlParser.parse(html, shortcode)：內嵌 JSON → og → 兜底。
 *  4. 缺字/缺作者 → ThreadsOEmbedClient.fetch（graph.threads.com → .net）。
 *     oEmbed 只接受 /@user/post/ 與 /t/，/share/ 需先轉 canonical（此處處理）。
 *  5. MediaDownloader 下載離線媒體。
 */
@Singleton
class ThreadsGraphQLClient @Inject constructor(
    private val mediaDownloader: MediaDownloader
) {
    private val client: OkHttpClient = ThreadsPageFetcher.newClient()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun fetchPostByPostIdOrShortcode(urlOrPostID: String, fallbackShortcode: String): Result<FetchedPostResult> {        return withContext(Dispatchers.IO) {
            val targetUrl = if (urlOrPostID.startsWith("http://") || urlOrPostID.startsWith("https://")) {
                urlOrPostID
            } else {
                "https://www.threads.com/t/$fallbackShortcode"
            }

            // 1. 抓頁（含 redirect）
            val fetched = ThreadsPageFetcher.fetch(client, targetUrl)
            var resolvedUrl = fetched.resolvedUrl.ifBlank { targetUrl }
            val htmlContent = fetched.html

            // 2. 重抽 ID（含 /share/）
            val ids = ThreadsPageFetcher.extractIds(resolvedUrl, fallbackShortcode)
            var extractedShortcode = ids.shortcode
            var extractedHandle = ids.handle

            // 3. HTML 解析（SJS 精確解析優先，內含作者/時間/讚數/留言鏈）
            val parsed = ThreadsHtmlParser.parse(htmlContent, extractedShortcode)
            var bodyText = parsed.bodyText
            var authorDisplayName = parsed.authorDisplayName
            var authorProfileUrl = parsed.authorProfileUrl
            var authorVerified = parsed.authorVerified
            var postedAtMs = parsed.postedAtMs
            var likeCount = parsed.likeCount
            if (parsed.authorHandleFromTitle.isNotBlank() && extractedHandle.isBlank()) {
                extractedHandle = parsed.authorHandleFromTitle
            }
            val mediaList = parsed.media.toMutableList()
            val commentsList = parsed.comments.toMutableList()

            // 4. oEmbed 補齊（只接受 /@user/post/ 與 /t/；實測回傳僅有按鈕無內文，
            //    故只當作者/存在性補充。若仍是 /share/ 未解析則跳過——token 不是短碼）。
            //    注意：oEmbed 對真實短碼回 200、對假短碼回 OAuthException，失敗無害。
            if ((bodyText.isBlank() || extractedHandle.isBlank()) && !resolvedUrl.contains("/share/")) {
                // 若 resolved 仍是 /share/，oEmbed 不接受 → 上層已跳過，此處不再嘗試
                val oembed = ThreadsOEmbedClient.fetch(client, resolvedUrl)
                if (oembed != null) {
                    if (authorDisplayName.isBlank()) authorDisplayName = oembed.authorDisplayName
                    if (extractedHandle.isBlank()) extractedHandle = oembed.authorHandle
                    if (bodyText.isBlank()) bodyText = oembed.bodySnippet
                }
            }

            // 5. 離線媒體下載（主貼 + 母文鏈 + 留言媒體，留言檔名用 500+ 偏移避開主貼索引，母文鏈用 900+）
            // 下載前先正規化去重，避免同內容多變體下載多份造成雙份顯示
            val dedupedMediaList = MediaDedup.distinctFetched(mediaList)
            val downloadedMediaList = dedupedMediaList.mapIndexed { index, m ->
                val localFilePath = mediaDownloader.downloadMedia(m.remoteUrl, extractedShortcode, index)
                m.copy(localPath = localFilePath)
            }
            val parent = parsed.parent
            // 祖先鏈媒體：root 在前依序下載（900 + 層*8 + 序），合併時保持鏈序
            val downloadedChain = downloadParentChainMedia(
                parsed.parentChain.ifEmpty { listOfNotNull(parent) },
                extractedShortcode
            )
            val downloadedParentMedia = MediaDedup.distinctFetched(downloadedChain.flatMap { it.media })
            val commentsWithMedia = downloadCommentMedia(commentsList, extractedShortcode)

            val finalAuthorHandle = extractedHandle.ifBlank { "threads_user" }
            val finalDisplayName = authorDisplayName.ifBlank { finalAuthorHandle }

            if (bodyText.isNotBlank() || downloadedMediaList.isNotEmpty() || commentsWithMedia.isNotEmpty()) {
                Result.success(
                    FetchedPostResult(
                        shortcode = extractedShortcode,
                        authorHandle = finalAuthorHandle,
                        authorDisplayName = finalDisplayName,
                        authorProfileUrl = authorProfileUrl,
                        authorVerified = authorVerified,
                        postedAt = if (postedAtMs > 0) postedAtMs else System.currentTimeMillis(),
                        bodyText = bodyText,
                        likeCount = likeCount,
                        replyCount = commentsWithMedia.size,
                        repostCount = 0,
                        comments = commentsWithMedia,
                        media = downloadedMediaList,
                        rawJsonMin = """{"code":"$extractedShortcode"}""",
                        resolvedUrl = resolvedUrl,
                        status = "COMPLETE",
                        parentShortcode = parent?.shortcode.orEmpty(),
                        parentAuthorHandle = parent?.authorHandle.orEmpty(),
                        parentBodyText = parent?.bodyText.orEmpty(),
                        parentLikeCount = parent?.likeCount ?: 0,
                        parentPostedAt = parent?.postedAtMs ?: 0L,
                        parentMedia = downloadedParentMedia,
                        parentChain = downloadedChain
                    )
                )
            } else {
                Result.success(
                    FetchedPostResult(
                        shortcode = extractedShortcode,
                        authorHandle = finalAuthorHandle,
                        authorDisplayName = finalDisplayName,
                        authorProfileUrl = "",
                        authorVerified = false,
                        postedAt = System.currentTimeMillis(),
                        bodyText = "",
                        likeCount = 0,
                        replyCount = 0,
                        repostCount = 0,
                        comments = emptyList(),
                        media = downloadedMediaList,
                        rawJsonMin = "",
                        resolvedUrl = resolvedUrl,
                        status = "PARTIAL"
                    )
                )
            }
        }
    }

    /** 兩份留言清單合併：key = author + 空白正規化後的文字；帶 media 或讚數更高者勝出 */
    private fun mergeCommentsLists(
        base: List<FetchedComment>,
        extra: List<FetchedComment>
    ): List<FetchedComment> {
        val map = LinkedHashMap<String, FetchedComment>()
        fun norm(t: String) = t.replace(Regex("\\s+"), " ").trim()
        for (c in (base + extra)) {
            val key = c.author.trim().lowercase() + "\u0000" + norm(c.text).lowercase()
            if (key.isBlank() || key.endsWith("\u0000")) continue
            val prev = map[key]
            if (prev == null ||
                (prev.media.isEmpty() && c.media.isNotEmpty()) ||
                (c.likeCount > prev.likeCount)
            ) {
                // 保留較多資訊者：media 併集 + 較高讚數（同內容多變體只留一份）
                val merged = if (prev != null && prev.media.isNotEmpty() && c.media.isNotEmpty()) {
                    c.copy(
                        media = MediaDedup.distinctFetched(prev.media + c.media),
                        likeCount = maxOf(prev.likeCount, c.likeCount)
                    )
                } else if (prev != null && prev.media.isNotEmpty() && c.media.isEmpty()) {
                    c.copy(media = prev.media, likeCount = maxOf(prev.likeCount, c.likeCount))
                } else {
                    c
                }
                map[key] = merged
            }
        }
        return map.values.toList()
    }

    /**
     * 祖先鏈媒體離線下載（root 在前依序）。
     * 檔名索引用 900 + 層*8 + 序，避免與主貼（0..N）/留言（500+）索引衝突。
     */
    private suspend fun downloadParentChainMedia(
        chain: List<ThreadsSjsParser.ParentPost>,
        fallbackShortcode: String
    ): List<ThreadsSjsParser.ParentPost> {
        return chain.mapIndexed { ci, pp ->
            if (pp.media.isEmpty()) return@mapIndexed pp
            val updated = pp.media.mapIndexed { j, m ->
                val local = mediaDownloader.downloadMedia(
                    m.remoteUrl,
                    pp.shortcode.ifBlank { fallbackShortcode },
                    900 + ci * 8 + j
                )
                m.copy(localPath = local)
            }
            pp.copy(media = updated)
        }
    }

    /**
     * 留言媒體離線下載（留言中的圖片/影片）。
     * 檔名索引用 500+ 偏移，避免與主貼 media 的 0..N 索引衝突；
     * 整篇上限 budget 檔，防止大量留言拖垮抓取。
     */
    private suspend fun downloadCommentMedia(
        comments: List<FetchedComment>,
        shortcode: String,
        startIndex: Int = 500,
        budget: Int = 12
    ): List<FetchedComment> {
        var remaining = budget
        var ci = 0
        return comments.map { c ->
            if (c.media.isEmpty() || remaining <= 0) return@map c
            val take = c.media.take(remaining)
            val updated = take.mapIndexed { j, m ->
                val local = mediaDownloader.downloadMedia(m.remoteUrl, shortcode, startIndex + ci * 8 + j)
                m.copy(localPath = local)
            }
            remaining -= updated.size
            ci++
            c.copy(media = updated)
        }
    }

    /**
     * WebView 渲染路徑入口：/share/ 已在內建瀏覽器跑完 JS 跳轉，
     * 這裡拿最終 URL + SJS JSON 塊直接解析並下載媒體（不再發網路請求）。
     *
     * @param sjsBlocks JS 端 querySelectorAll 抽出的 data-sjs 文本
     * @param resolvedUrl WebView 最終 URL（應為 /@user/post/CODE 形）
     */
    suspend fun resolveFromRenderedBlocks(
        sjsBlocks: List<String>,
        resolvedUrl: String,
        renderedText: String = "",
        renderedHtml: String = "",
        domComments: List<com.reater.app.data.remote.threads.ThreadsWebResolver.DomComment> = emptyList()
    ): Result<FetchedPostResult> {
        return withContext(Dispatchers.IO) {
            val ids = ThreadsPageFetcher.extractIds(resolvedUrl, "")
            val shortcode = ids.shortcode
            if (shortcode.isBlank() || shortcode.startsWith("share_")) {
                return@withContext Result.failure(IllegalStateException("share link not resolved"))
            }
            val sjs = try {
                if (sjsBlocks.isNotEmpty()) ThreadsSjsParser.parseBlocks(sjsBlocks, shortcode) else null
            } catch (_: Exception) {
                null
            }
            // DOM 直抽留言轉換（免登入 WebView 渲染兜底，第一篇為主貼時排除）
            // 注意：必須保留 d.media，否則留言圖會在 DOM 路徑全丟（舊版只取 author/text）。
            val domFetched = domComments.mapIndexedNotNull { i, d ->
                if (d.text.isBlank() || d.author.isBlank()) null
                else FetchedComment(
                    externalId = "dom_c_$i",
                    author = d.author,
                    text = d.text,
                    likeCount = d.likeCount,
                    media = d.media.take(6)
                )
            }
            if (sjs != null && (sjs.bodyText.isNotBlank() || sjs.media.isNotEmpty() || sjs.comments.isNotEmpty())) {
                val domClean = domFetched.filter { it.text != sjs.bodyText }.take(50)
                // SSR 留言 + DOM 留言合併：同一則（author+文字）優先取帶 media 的版本，
                // DOM 補 SSR 沒帶圖的留言，SSR 補 DOM 沒渲染出來的
                val mergedComments = if (sjs.comments.isNotEmpty()) {
                    mergeCommentsLists(sjs.comments, domClean)
                } else {
                    domClean
                }
            val downloadedMedia = MediaDedup.distinctFetched(sjs.media).mapIndexed { index, m ->
                val local = mediaDownloader.downloadMedia(m.remoteUrl, shortcode, index)
                m.copy(localPath = local)
            }
            val downloadedParentMediaWeb = downloadParentChainMedia(
                sjs.parentChain.ifEmpty { listOfNotNull(sjs.parent) },
                shortcode
            )
            val commentsWithMedia = downloadCommentMedia(mergedComments, shortcode)
            val handle = sjs.authorHandle.ifBlank { ids.handle.ifBlank { "threads_user" } }

            return@withContext Result.success(
                FetchedPostResult(
                    shortcode = shortcode,
                    authorHandle = handle,
                    authorDisplayName = sjs.authorDisplayName.ifBlank { handle },
                    authorProfileUrl = sjs.authorProfileUrl,
                    authorVerified = sjs.authorVerified,
                    postedAt = if (sjs.postedAtMs > 0) sjs.postedAtMs else System.currentTimeMillis(),
                    bodyText = sjs.bodyText,
                    likeCount = sjs.likeCount,
                    replyCount = commentsWithMedia.size,
                    repostCount = 0,
                    comments = commentsWithMedia,
                    media = downloadedMedia,
                    rawJsonMin = "{\"code\":\"$shortcode\"}",
                    resolvedUrl = resolvedUrl,
                    status = "COMPLETE",
                    parentShortcode = sjs.parent?.shortcode.orEmpty(),
                    parentAuthorHandle = sjs.parent?.authorHandle.orEmpty(),
                    parentBodyText = sjs.parent?.bodyText.orEmpty(),
                    parentLikeCount = sjs.parent?.likeCount ?: 0,
                    parentPostedAt = sjs.parent?.postedAtMs ?: 0L,
                    parentMedia = MediaDedup.distinctFetched(downloadedParentMediaWeb.flatMap { it.media }),
                    parentChain = downloadedParentMediaWeb
                )
            )
            }
            // SJS 為空或無正文：用真短碼走 OkHttp 標準管線重抓
            val refetch = try {
                fetchPostByPostIdOrShortcode(resolvedUrl, shortcode).getOrNull()
            } catch (_: Exception) {
                null
            }
            if (refetch != null && (refetch.bodyText.isNotBlank() || refetch.media.isNotEmpty() || refetch.comments.isNotEmpty())) {
                return@withContext Result.success(refetch)
            }
            // 渲染 HTML 內若含貼文 payload（thread_items 舊 shape / direct_replies 新 shape），再試一次 SJS
            if (renderedHtml.contains("thread_items") || renderedHtml.contains("direct_replies")) {
                val fromHtml = try {
                    ThreadsHtmlParser.parse(renderedHtml, shortcode)
                } catch (_: Exception) {
                    null
                }
                if (fromHtml != null && (fromHtml.bodyText.isNotBlank() || fromHtml.media.isNotEmpty())) {
                    val downloadedMedia2 = MediaDedup.distinctFetched(fromHtml.media).mapIndexed { index, m ->
                        val local = mediaDownloader.downloadMedia(m.remoteUrl, shortcode, index)
                        m.copy(localPath = local)
                    }
                    val downloadedParentMedia2 = downloadParentChainMedia(
                        fromHtml.parentChain.ifEmpty { listOfNotNull(fromHtml.parent) },
                        shortcode
                    )
                    val handle2 = fromHtml.authorHandleFromTitle.ifBlank {
                        ids.handle.ifBlank { "threads_user" }
                    }
                    return@withContext Result.success(
                        FetchedPostResult(
                            shortcode = shortcode,
                            authorHandle = handle2,
                            authorDisplayName = fromHtml.authorDisplayName.ifBlank { handle2 },
                            authorProfileUrl = fromHtml.authorProfileUrl,
                            authorVerified = fromHtml.authorVerified,
                            postedAt = if (fromHtml.postedAtMs > 0) fromHtml.postedAtMs else System.currentTimeMillis(),
                            bodyText = fromHtml.bodyText,
                            likeCount = fromHtml.likeCount,
                            replyCount = fromHtml.comments.size,
                            repostCount = 0,
                            comments = fromHtml.comments,
                            media = downloadedMedia2,
                            rawJsonMin = "{\"code\":\"$shortcode\"}",
                            resolvedUrl = resolvedUrl,
                            status = "COMPLETE",
                            parentShortcode = fromHtml.parent?.shortcode.orEmpty(),
                            parentAuthorHandle = fromHtml.parent?.authorHandle.orEmpty(),
                            parentBodyText = fromHtml.parent?.bodyText.orEmpty(),
                            parentLikeCount = fromHtml.parent?.likeCount ?: 0,
                            parentPostedAt = fromHtml.parent?.postedAtMs ?: 0L,
                            parentMedia = MediaDedup.distinctFetched(downloadedParentMedia2.flatMap { it.media }),
                            parentChain = downloadedParentMedia2
                        )
                    )
                }
            }
            if (domFetched.isNotEmpty()) {
                val bodyGuess = (refetch?.bodyText?.takeIf { it.isNotBlank() }
                    ?: sjs?.bodyText?.takeIf { it.isNotBlank() }
                    ?: cleanDomBody(renderedText))
                val handleGuess = ids.handle.ifBlank {
                    refetch?.authorHandle?.takeIf { it != "threads_user" }
                        ?: sjs?.authorHandle?.takeIf { it.isNotBlank() }
                        ?: domFetched.firstOrNull()?.author ?: "threads_user"
                }
                val filtered = domFetched.filter { it.text != bodyGuess }.take(50)
                if (bodyGuess.isNotBlank() || filtered.isNotEmpty()) {
                    return@withContext Result.success(
                        FetchedPostResult(
                            shortcode = shortcode,
                            authorHandle = handleGuess,
                            authorDisplayName = handleGuess,
                            authorProfileUrl = sjs?.authorProfileUrl ?: "",
                            authorVerified = sjs?.authorVerified ?: false,
                            postedAt = System.currentTimeMillis(),
                            bodyText = bodyGuess,
                            likeCount = sjs?.likeCount ?: 0,
                            replyCount = filtered.size,
                            repostCount = 0,
                            comments = downloadCommentMedia(filtered, shortcode),
                            media = sjs?.media?.mapIndexed { index, m ->
                                val local = mediaDownloader.downloadMedia(m.remoteUrl, shortcode, index)
                                m.copy(localPath = local)
                            } ?: emptyList(),
                            rawJsonMin = "{\"code\":\"$shortcode\"}",
                            resolvedUrl = resolvedUrl,
                            status = "COMPLETE"
                        )
                    )
                }
            }
            // 最後兜底：渲染可見文字，留言不偽造
            val cleanText = renderedText.lines()
                .map { it.trim() }
                .filter { it.length > 2 && !it.startsWith("http") }
                .filterNot {
                    it.startsWith("Threads") || it.startsWith("Log in") || it.startsWith("Sign up") ||
                        it.startsWith("登入") || it == "Like" || it == "Reply" || it == "Repost" || it == "Share"
                }
                .joinToString("\n").trim().take(2000)
            if (cleanText.isNotBlank()) {
                val handle3 = ids.handle.ifBlank { "threads_user" }
                return@withContext Result.success(
                    FetchedPostResult(
                        shortcode = shortcode,
                        authorHandle = handle3,
                        authorDisplayName = handle3,
                        authorProfileUrl = "",
                        authorVerified = false,
                        postedAt = System.currentTimeMillis(),
                        bodyText = cleanText,
                        likeCount = 0,
                        replyCount = 0,
                        repostCount = 0,
                        comments = emptyList(),
                        media = emptyList(),
                        rawJsonMin = "{\"code\":\"$shortcode\"}",
                        resolvedUrl = resolvedUrl,
                        status = "COMPLETE"
                    )
                )
            }
            return@withContext Result.failure(IllegalStateException("no post data in page"))
        }
    }
    private fun cleanDomBody(renderedText: String): String {
        // 圖1 文字渲染修正：同步 ThreadsWebResolver 的清洗規則——
        // 動作詞（中英）、時間行、純數字列一律剔除，避免「8小時」「3K」「23」混入正文造成跑版
        val countLike = Regex("""^[\d,，\s]+(\.\d+)?\s*[KkMm萬千]?$""")
        val timeLike = Regex(
            """^(\d+\s*[秒分鐘小时時天週周月年]+|\d+\s*(s|sec|secs|m|min|mins|h|hr|hrs|d|day|days|w|week|weeks|mo|yr)\.?|昨天|前天|Yesterday|\d{4}[./-]\d{1,2}[./-]\d{1,2}|\d{1,2}[月/\-]\d{1,2}日?)$""",
            RegexOption.IGNORE_CASE
        )
        val actions = setOf(
            "like", "likes", "reply", "replies", "repost", "reposts",
            "share", "shares", "send",
            "讚", "喜歡", "愛心", "回覆", "回應", "留言",
            "轉發", "轉po", "轉帖", "分享", "傳送"
        )
        return renderedText.lines()
            .map { it.trim() }
            .filter { it.length > 2 && !it.startsWith("http") }
            .filterNot {
                it.startsWith("Threads") || it.startsWith("Log in") || it.startsWith("Sign up") ||
                    it.startsWith("登入") || actions.contains(it.lowercase()) ||
                    countLike.matches(it) || timeLike.matches(it)
            }
            .joinToString("\n").trim().take(2000)
    }
}
