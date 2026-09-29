package com.reater.app.data.remote

import com.reater.app.data.remote.threads.ThreadsHtmlParser
import com.reater.app.data.remote.threads.ThreadsOEmbedClient
import com.reater.app.data.remote.threads.ThreadsPageFetcher
import com.reater.app.data.remote.threads.ThreadsSjsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val depth: Int = 0
)

data class FetchedMedia(
    val kind: String,
    val remoteUrl: String,
    val localPath: String = "",
    val width: Int = 0,
    val height: Int = 0
)

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
    val status: String // COMPLETE, PARTIAL, FAILED
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

            // 5. 離線媒體下載
            val downloadedMediaList = mediaList.mapIndexed { index, m ->
                val localFilePath = mediaDownloader.downloadMedia(m.remoteUrl, extractedShortcode, index)
                m.copy(localPath = localFilePath)
            }

            val finalAuthorHandle = extractedHandle.ifBlank { "threads_user" }
            val finalDisplayName = authorDisplayName.ifBlank { finalAuthorHandle }

            if (bodyText.isNotBlank() || downloadedMediaList.isNotEmpty()) {
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
                        replyCount = commentsList.size,
                        repostCount = 0,
                        comments = commentsList,
                        media = downloadedMediaList,
                        rawJsonMin = """{"code":"$extractedShortcode"}""",
                        resolvedUrl = resolvedUrl,
                        status = "COMPLETE"
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
            val domFetched = domComments.mapIndexedNotNull { i, d ->
                if (d.text.isBlank() || d.author.isBlank()) null
                else FetchedComment(externalId = "dom_c_$i", author = d.author, text = d.text, likeCount = 0)
            }
            if (sjs != null && (sjs.bodyText.isNotBlank() || sjs.media.isNotEmpty())) {
                val mergedComments = if (sjs.comments.isNotEmpty()) sjs.comments
                else domFetched.filter { it.text != sjs.bodyText }.take(50)
            val downloadedMedia = sjs.media.mapIndexed { index, m ->
                val local = mediaDownloader.downloadMedia(m.remoteUrl, shortcode, index)
                m.copy(localPath = local)
            }
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
                    replyCount = mergedComments.size,
                    repostCount = 0,
                    comments = mergedComments,
                    media = downloadedMedia,
                    rawJsonMin = "{\"code\":\"$shortcode\"}",
                    resolvedUrl = resolvedUrl,
                    status = "COMPLETE"
                )
            )
            }
            // SJS 為空或無正文：用真短碼走 OkHttp 標準管線重抓
            val refetch = try {
                fetchPostByPostIdOrShortcode(resolvedUrl, shortcode).getOrNull()
            } catch (_: Exception) {
                null
            }
            if (refetch != null && (refetch.bodyText.isNotBlank() || refetch.media.isNotEmpty())) {
                return@withContext Result.success(refetch)
            }
            // 渲染 HTML 內若含 thread_items，再試一次 SJS
            if (renderedHtml.contains("thread_items")) {
                val fromHtml = try {
                    ThreadsHtmlParser.parse(renderedHtml, shortcode)
                } catch (_: Exception) {
                    null
                }
                if (fromHtml != null && (fromHtml.bodyText.isNotBlank() || fromHtml.media.isNotEmpty())) {
                    val downloadedMedia2 = fromHtml.media.mapIndexed { index, m ->
                        val local = mediaDownloader.downloadMedia(m.remoteUrl, shortcode, index)
                        m.copy(localPath = local)
                    }
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
                            status = "COMPLETE"
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
                            comments = filtered,
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
        return renderedText.lines()
            .map { it.trim() }
            .filter { it.length > 2 && !it.startsWith("http") }
            .filterNot {
                it.startsWith("Threads") || it.startsWith("Log in") || it.startsWith("Sign up") ||
                    it.startsWith("登入") || it == "Like" || it == "Reply" || it == "Repost" || it == "Share"
            }
            .joinToString("\n").trim().take(2000)
    }
}
