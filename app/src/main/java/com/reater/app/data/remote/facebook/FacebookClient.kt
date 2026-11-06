package com.reater.app.data.remote.facebook

import com.reater.app.data.remote.DownloadProgress
import com.reater.app.data.remote.FetchedMedia
import com.reater.app.data.remote.FetchedPostResult
import com.reater.app.data.remote.MediaDownloader
import com.reater.app.data.remote.threads.TopicTags
import com.reater.app.domain.UrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FacebookClient @Inject constructor(
    private val mediaDownloader: MediaDownloader
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Meta crawler UA: Facebook 會對爬蟲直接吐出完整 Open Graph meta 標籤與媒體預覽，免登入牆
    private val crawlerUa = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"

    suspend fun fetchPost(
        url: String,
        fallbackPostId: String,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): Result<FetchedPostResult> {
        return withContext(Dispatchers.IO) {
            try {
                val parsed = UrlParser.parseFacebookUrl(url)
                val postId = parsed?.postId?.ifBlank { fallbackPostId } ?: fallbackPostId
                val targetUrl = parsed?.fetchUrl ?: url

                val request = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", crawlerUa)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                    .build()

                val (html, finalUrl) = client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    val actualUrl = resp.request.url.toString()
                    Pair(bodyStr, actualUrl)
                }

                // 擷取 Open Graph 與 Meta 標籤
                val ogTitle = extractOgTag(html, "og:title")
                val ogDesc = extractOgTag(html, "og:description")
                val ogImage = extractOgTag(html, "og:image")
                val ogVideo = extractOgTag(html, "og:video")

                val title = unescapeHtml(ogTitle)
                val description = unescapeHtml(ogDesc)
                val body = when {
                    description.isNotBlank() && title.isNotBlank() && !description.contains(title) -> "$title\n\n$description"
                    description.isNotBlank() -> description
                    title.isNotBlank() -> title
                    else -> ""
                }

                val mediaList = mutableListOf<FetchedMedia>()
                val remoteMediaUrl = ogVideo.ifBlank { ogImage }
                val isVideo = ogVideo.isNotBlank() || targetUrl.contains("/share/v/") || targetUrl.contains("/videos/") || targetUrl.contains("/reel/")

                if (remoteMediaUrl.isNotBlank()) {
                    val localPath = mediaDownloader.downloadMedia(
                        remoteUrl = remoteMediaUrl,
                        shortcode = postId,
                        index = 0,
                        referer = "https://www.facebook.com/",
                        onProgress = onProgress
                    )
                    mediaList.add(
                        FetchedMedia(
                            kind = if (isVideo) "video" else "image",
                            remoteUrl = remoteMediaUrl,
                            localPath = localPath
                        )
                    )
                }

                val author = if (title.contains(" - ")) title.substringBefore(" - ").trim() else "Facebook"

                val status = when {
                    body.isNotBlank() && mediaList.isNotEmpty() -> "COMPLETE"
                    body.isNotBlank() || mediaList.isNotEmpty() -> "PARTIAL"
                    else -> "FAILED"
                }

                Result.success(
                    FetchedPostResult(
                        shortcode = postId,
                        authorHandle = author,
                        authorDisplayName = author,
                        authorProfileUrl = "",
                        authorVerified = false,
                        postedAt = System.currentTimeMillis(),
                        bodyText = body,
                        likeCount = 0,
                        replyCount = 0,
                        repostCount = 0,
                        comments = emptyList(),
                        media = mediaList,
                        rawJsonMin = """{"code":"$postId"}""",
                        resolvedUrl = finalUrl.ifBlank { targetUrl },
                        status = status,
                        topicTag = TopicTags.firstHashtag(body)
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun extractOgTag(html: String, property: String): String {
        val pattern1 = Regex("""<meta\s+property=["']$property["']\s+content=["'](.*?)["']""", RegexOption.IGNORE_CASE)
        val pattern2 = Regex("""<meta\s+content=["'](.*?)["']\s+property=["']$property["']""", RegexOption.IGNORE_CASE)
        val patternName1 = Regex("""<meta\s+name=["']$property["']\s+content=["'](.*?)["']""", RegexOption.IGNORE_CASE)
        val patternName2 = Regex("""<meta\s+content=["'](.*?)["']\s+name=["']$property["']""", RegexOption.IGNORE_CASE)
        return pattern1.find(html)?.groupValues?.get(1)
            ?: pattern2.find(html)?.groupValues?.get(1)
            ?: patternName1.find(html)?.groupValues?.get(1)
            ?: patternName2.find(html)?.groupValues?.get(1)
            ?: ""
    }

    private fun unescapeHtml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }
}
