package com.reater.app.data.remote.youtube

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
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeClient @Inject constructor(
    private val mediaDownloader: MediaDownloader
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val desktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    suspend fun fetchPost(
        url: String,
        fallbackVideoId: String,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): Result<FetchedPostResult> {
        return withContext(Dispatchers.IO) {
            try {
                val parsed = UrlParser.parseYouTubeUrl(url)
                val videoId = parsed?.videoId?.ifBlank { fallbackVideoId } ?: fallbackVideoId
                if (videoId.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("無效的 YouTube 網址"))
                }

                // 1. YouTube 官方免 Token oEmbed API 取得公開資訊
                val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
                val oembedReq = Request.Builder()
                    .url(oembedUrl)
                    .header("User-Agent", desktopUa)
                    .build()

                var title = ""
                var authorName = "YouTube"
                var authorUrl = ""
                var defaultThumbnail = ""

                try {
                    client.newCall(oembedReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string().orEmpty())
                            title = json.optString("title", "")
                            authorName = json.optString("author_name", "YouTube")
                            authorUrl = json.optString("author_url", "")
                            defaultThumbnail = json.optString("thumbnail_url", "")
                        }
                    }
                } catch (_: Exception) {}

                // 2. 獲取最高解析度封面圖片（maxresdefault -> hqdefault -> defaultThumbnail）
                val candidateThumbnails = listOf(
                    "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
                    "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    defaultThumbnail
                ).filter { it.isNotBlank() }

                val mediaList = mutableListOf<FetchedMedia>()
                var downloadedPath = ""
                for (thumbUrl in candidateThumbnails) {
                    downloadedPath = mediaDownloader.downloadMedia(
                        remoteUrl = thumbUrl,
                        shortcode = videoId,
                        index = 0,
                        referer = "https://www.youtube.com/",
                        onProgress = onProgress
                    )
                    if (downloadedPath.isNotBlank()) {
                        mediaList.add(
                            FetchedMedia(
                                kind = "image",
                                remoteUrl = thumbUrl,
                                localPath = downloadedPath
                            )
                        )
                        break
                    }
                }

                val body = title.ifBlank { parsed?.kindLabel ?: "YouTube 影片" }
                val canonical = parsed?.canonicalUrl ?: "https://www.youtube.com/watch?v=$videoId"

                Result.success(
                    FetchedPostResult(
                        shortcode = videoId,
                        authorHandle = authorName,
                        authorDisplayName = authorName,
                        authorProfileUrl = authorUrl,
                        authorVerified = false,
                        postedAt = System.currentTimeMillis(),
                        bodyText = body,
                        likeCount = 0,
                        replyCount = 0,
                        repostCount = 0,
                        comments = emptyList(),
                        media = mediaList,
                        rawJsonMin = """{"code":"$videoId"}""",
                        resolvedUrl = canonical,
                        status = if (mediaList.isNotEmpty()) "COMPLETE" else "PARTIAL",
                        topicTag = TopicTags.firstHashtag(body)
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
