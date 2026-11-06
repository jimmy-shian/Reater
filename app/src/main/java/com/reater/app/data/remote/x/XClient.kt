package com.reater.app.data.remote.x

import com.reater.app.data.remote.FetchedMedia
import com.reater.app.data.remote.FetchedPostResult
import com.reater.app.data.remote.MediaDedup
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

/**
 * X (Twitter) 抓取 Facade（模組化入口，Hilt 只需注入此類）。
 *
 * X 官方 API 需登入 token，App 內不可用；改用兩個免認證公開端點（2026-11 實測）：
 *
 * Pass 1（主）：fxtwitter 公開 API
 *   https://api.fxtwitter.com/{handle}/status/{id}
 *   → {"code":200,"tweet":{text, author{screen_name,name,avatar_url,verification},
 *     created_timestamp(秒), replies, retweets, likes, views,
 *     media{all:[{type:photo|video|gif, url, width, height, formats:[{url,bitrate,container}]}]}}}
 *   text 已去除 t.co 媒體短連結，直接是乾淨主文。
 *
 * Pass 2（備援）：X 官方 syndication 端點（網頁嵌入用，免 token）
 *   https://cdn.syndication.twimg.com/tweet-result?id={id}&token=a
 *   → {id_str, text(尾端帶 t.co 短連結需清除), user{screen_name,name,profile_image_url_https},
 *     created_at(ISO), favorite_count, conversation_count,
 *     mediaDetails[{type, media_url_https, video_info.variants[{bitrate,content_type,url}]}]}
 *
 * 留言：兩個端點皆不回覆留言內容 → comments 為空（主文/媒體/計數完整，符合「至少主文存下來」）。
 */
@Singleton
class XClient @Inject constructor(
    private val mediaDownloader: MediaDownloader
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    suspend fun fetchPost(url: String, fallbackStatusId: String): Result<FetchedPostResult> {
        return withContext(Dispatchers.IO) {
            try {
                val parse = UrlParser.parseXUrl(url)
                val statusId = parse?.statusId?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
                    ?: fallbackStatusId.trim().takeIf { it.isNotBlank() && it.all(Char::isDigit) }
                if (statusId.isNullOrBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("無效的 X 貼文連結"))
                }
                val handleHint = parse?.handle.orEmpty()

                // Pass 1：fxtwitter（主）
                var parsed = fetchFxtwitter(handleHint, statusId)
                // Pass 2：syndication（備援；token 任意字串即可）
                if (parsed == null) {
                    parsed = fetchSyndication(statusId)
                }
                if (parsed == null) {
                    return@withContext Result.failure(IllegalStateException("X 貼文抓取失敗（兩個來源皆無資料）"))
                }

                // 媒體離線下載（X CDN 直連，無需特殊 headers）
                val media = MediaDedup.distinctFetched(parsed.media).mapIndexed { index, m ->
                    m.copy(localPath = mediaDownloader.downloadMedia(m.remoteUrl, statusId, index))
                }
                val body = parsed.bodyText
                val status = when {
                    body.isNotBlank() && media.isNotEmpty() -> "COMPLETE"
                    body.isNotBlank() || media.isNotEmpty() -> "PARTIAL"
                    else -> "FAILED"
                }
                val finalHandle = parsed.handle.ifBlank { handleHint.ifBlank { "x_user" } }
                Result.success(
                    FetchedPostResult(
                        shortcode = statusId,
                        authorHandle = finalHandle,
                        authorDisplayName = parsed.displayName.ifBlank { finalHandle },
                        authorProfileUrl = parsed.avatarUrl,
                        authorVerified = parsed.verified,
                        postedAt = if (parsed.postedAtMs > 0) parsed.postedAtMs else System.currentTimeMillis(),
                        bodyText = body,
                        likeCount = parsed.likeCount,
                        replyCount = parsed.replyCount,
                        repostCount = parsed.repostCount,
                        comments = emptyList(),
                        media = media,
                        rawJsonMin = """{"code":"$statusId"}""",
                        resolvedUrl = parsed.url.ifBlank { "https://x.com/$finalHandle/status/$statusId" },
                        status = status,
                        topicTag = TopicTags.firstHashtag(body)
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun fetchFxtwitter(handle: String, statusId: String): ParsedXPost? {
        val apiPath = if (handle.isNotBlank()) "/$handle/status/$statusId" else "/status/$statusId"
        val body = httpGet("https://api.fxtwitter.com$apiPath") ?: return null
        return try {
            val root = JSONObject(body)
            if (root.optInt("code", 0) !in 200..299) return null
            val tweet = root.optJSONObject("tweet") ?: return null
            XJsonParser.fromFxtwitter(tweet, statusId)
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchSyndication(statusId: String): ParsedXPost? {
        val body = httpGet("https://cdn.syndication.twimg.com/tweet-result?id=$statusId&token=a") ?: return null
        return try {
            XJsonParser.fromSyndication(JSONObject(body), statusId)
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", desktopUa)
                .header("Accept", "application/json,*/*;q=0.8")
                .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** 解析後的 X 貼文（未下載媒體；localPath 由 XClient 統一下載） */
internal data class ParsedXPost(
    val url: String,
    val handle: String,
    val displayName: String,
    val avatarUrl: String,
    val verified: Boolean,
    val postedAtMs: Long,
    val bodyText: String,
    val likeCount: Int,
    val replyCount: Int,
    val repostCount: Int,
    val media: List<FetchedMedia>
)

/** X 兩種端點的 JSON → ParsedXPost 純解析（無網路，可單元測試） */
internal object XJsonParser {

    /** fxtwitter：tweet 物件（root 內層） */
    fun fromFxtwitter(tweet: JSONObject, fallbackStatusId: String): ParsedXPost? {
        val id = tweet.optString("id").orEmpty()
            .takeIf { it.isNotBlank() && it != "null" } ?: fallbackStatusId
        val body = tweet.stringOrEmpty("text")
        val author = tweet.optJSONObject("author")
        val media = mutableListOf<FetchedMedia>()
        val all = tweet.optJSONObject("media")?.optJSONArray("all")
        if (all != null) {
            for (i in 0 until all.length()) {
                val m = all.optJSONObject(i) ?: continue
                addMediaByType(
                    out = media,
                    type = m.stringOrEmpty("type"),
                    url = m.stringOrEmpty("url"),
                    thumbnailUrl = m.stringOrEmpty("thumbnail_url"),
                    width = m.optInt("width", 0),
                    height = m.optInt("height", 0),
                    variants = m.optJSONArray("formats")
                )
            }
        }
        val createdSec = tweet.optLong("created_timestamp", 0L)
        if (body.isBlank() && media.isEmpty()) return null
        return ParsedXPost(
            url = tweet.stringOrEmpty("url").ifBlank { "https://x.com/i/web/status/$id" },
            handle = author?.stringOrEmpty("screen_name").orEmpty(),
            displayName = author?.stringOrEmpty("name").orEmpty(),
            avatarUrl = author?.stringOrEmpty("avatar_url").orEmpty(),
            verified = author?.optJSONObject("verification")?.optBoolean("verified", false) == true,
            postedAtMs = if (createdSec > 0) createdSec * 1000L else 0L,
            bodyText = body,
            likeCount = tweet.optInt("likes", 0),
            replyCount = tweet.optInt("replies", 0),
            repostCount = tweet.optInt("retweets", 0),
            media = media
        )
    }

    /** syndication tweet-result：根物件即貼文 */
    fun fromSyndication(root: JSONObject, fallbackStatusId: String): ParsedXPost? {
        val id = root.stringOrEmpty("id_str").ifBlank { fallbackStatusId }
        // text 尾端常帶媒體 t.co 短連結，清除（fxtwitter 版已清除）
        val body = root.stringOrEmpty("text")
            .replace(Regex("\\s*https?://t\\.co/\\w+\\s*$"), "")
            .trim()
        val user = root.optJSONObject("user")
        val media = mutableListOf<FetchedMedia>()
        val details = root.optJSONArray("mediaDetails")
        if (details != null) {
            for (i in 0 until details.length()) {
                val m = details.optJSONObject(i) ?: continue
                addMediaByType(
                    out = media,
                    type = m.stringOrEmpty("type"),
                    url = m.stringOrEmpty("media_url_https"),
                    thumbnailUrl = m.stringOrEmpty("media_url_https"),
                    width = m.optInt("original_info_width", 0).takeIf { it > 0 }
                        ?: m.optJSONObject("original_info")?.optInt("width", 0) ?: 0,
                    height = m.optInt("original_info_height", 0).takeIf { it > 0 }
                        ?: m.optJSONObject("original_info")?.optInt("height", 0) ?: 0,
                    variants = m.optJSONObject("video_info")?.optJSONArray("variants")
                        ?: root.optJSONObject("video")?.optJSONArray("variants")
                )
            }
        }
        if (body.isBlank() && media.isEmpty()) return null
        return ParsedXPost(
            url = "https://x.com/${user?.stringOrEmpty("screen_name").orEmpty().ifBlank { "i/web" }}/status/$id",
            handle = user?.stringOrEmpty("screen_name").orEmpty(),
            displayName = user?.stringOrEmpty("name").orEmpty(),
            avatarUrl = user?.stringOrEmpty("profile_image_url_https").orEmpty(),
            verified = user?.optBoolean("verified", false) == true,
            postedAtMs = parseIsoDate(root.stringOrEmpty("created_at")),
            bodyText = body,
            likeCount = root.optInt("favorite_count", 0),
            replyCount = root.optInt("conversation_count", 0),
            repostCount = 0,
            media = media
        )
    }

    /**
     * 依媒體類型加入清單：
     *  - photo → IMAGE（pbs.twimg.com 直連）
     *  - video/gif → VIDEO：mp4 候選擇最高碼率（m3u8 為串流清單，下載層不支援，跳過）
     *    候選來源：variants（formats/video_info.variants）；主 url 為 mp4 時直接採用。
     */
    private fun addMediaByType(
        out: MutableList<FetchedMedia>,
        type: String,
        url: String,
        thumbnailUrl: String,
        width: Int,
        height: Int,
        variants: org.json.JSONArray?
    ) {
        when {
            type.equals("photo", ignoreCase = true) -> {
                if (url.startsWith("http")) {
                    out.add(FetchedMedia(kind = "IMAGE", remoteUrl = url, width = width, height = height))
                }
            }
            type.equals("video", ignoreCase = true) || type.equals("gif", ignoreCase = true) -> {
                val mp4 = pickBestMp4(url, variants)
                val chosen = mp4 ?: thumbnailUrl.takeIf { it.startsWith("http") && !it.contains(".m3u8") }
                if (chosen != null) {
                    // 影片無 mp4 候選時退化為縮圖（至少保留視覺內容）
                    out.add(
                        FetchedMedia(
                            kind = if (mp4 != null) "VIDEO" else "IMAGE",
                            remoteUrl = chosen,
                            width = width,
                            height = height
                        )
                    )
                }
            }
        }
    }

    /** 最高碼率 mp4 候選；主 url 本身為 mp4 時優先（fxtwitter url 即最高畫質 mp4） */
    private fun pickBestMp4(primaryUrl: String, variants: org.json.JSONArray?): String? {
        if (primaryUrl.startsWith("http") && primaryUrl.contains(".mp4") && !primaryUrl.contains(".m3u8")) {
            return primaryUrl
        }
        var best: String? = null
        var bestBitrate = -1
        if (variants != null) {
            for (i in 0 until variants.length()) {
                val v = variants.optJSONObject(i) ?: continue
                val u = v.stringOrEmpty("url")
                if (!u.startsWith("http") || u.contains(".m3u8") || !u.contains(".mp4")) continue
                val bitrate = v.optInt("bitrate", 0)
                if (bitrate > bestBitrate) {
                    best = u
                    bestBitrate = bitrate
                }
            }
        }
        return best
    }

    private fun JSONObject.stringOrEmpty(key: String): String =
        when (val v = opt(key)) {
            null, JSONObject.NULL -> ""
            is String -> v.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }.orEmpty()
            else -> ""
        }

    /** ISO 8601（2026-11-03T09:19:08.000Z）→ epoch ms；失敗回 0 */
    private fun parseIsoDate(raw: String): Long {
        if (raw.isBlank()) return 0L
        return try {
            java.time.Instant.parse(raw).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}
