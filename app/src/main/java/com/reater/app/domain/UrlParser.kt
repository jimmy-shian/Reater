package com.reater.app.domain

import java.net.URI
import java.security.MessageDigest

/**
 * Threads 連結解析器（模組化）。
 *
 * Threads App 分享會產生兩種連結（經網路查詢 + Apify/curl-x 文件交叉驗證）：
 *  1. 串文本身（canonical post）: /@handle/post/CODE （App 複製連結會再帶 ?xmt=TOKEN）
 *  2. 單則留言 / 手機分享短鏈：/share/CODE （手機分享按鈕輸出，需經 302 解析回正文）
 *  3. 短鏈：/t/CODE（不帶使用者名稱，精簡分享 / embed 用）
 *
 * 本模組只負責「辨識種類 + 抽 shortcode + 保留原始 URL 供抓取層做 redirect」，
 * 不做網路請求（網路解析交給 data.remote.threads.*）。
 */
object UrlParser {
    private val VALID_HOSTS = setOf(
        "threads.net",
        "www.threads.net",
        "threads.com",
        "www.threads.com"
    )

    // 注意：用 find（非 matchEntire），容忍尾端 /media、尾端斜線、額外 path。
    private val POST_PATH_REGEX = Regex("""/@?([A-Za-z0-9_.]+)/post/([A-Za-z0-9_-]+)""")
    private val T_PATH_REGEX = Regex("""/t/([A-Za-z0-9_-]+)""")
    private val SHARE_PATH_REGEX = Regex("""/share/([A-Za-z0-9_-]+)""")

    enum class ShareKind {
        /** /@handle/post/CODE：串文本身（最完整，含作者） */
        POST,
        /** /t/CODE：短鏈（無作者，需靠抓取補齊） */
        SHORT_T,
        /** /share/CODE：手機分享按鈕輸出；可能是某則留言或串文，需 redirect 解析 */
        SHARE_LINK,
        UNKNOWN
    }

    data class ParsedThreadsUrl(
        val handle: String,
        /**
         * 貼文短碼。注意：SHARE_LINK 時為空字串——/share/ 後面是分享 token，
         * 不是短碼（實測：HtGadxz38 → Ddz53L2krrE），伺服器端無法解析，
         * 真正短碼只能等抓取層跟 redirect 成功後才知道。
         */
        val shortcode: String,
        /**
         * 存檔用 canonical。POST/SHORT_T 為正規形；
         * SHARE_LINK 直接保留 /share/ 原鏈（瀏覽器可開啟，會自動展開到正文）。
         */
        val canonicalUrl: String,
        val urlHash: String,
        /** 實際拿去抓取的 URL（/share/ 保留原樣，不可用 canonical 蓋掉） */
        val originalUrl: String,
        val shareKind: ShareKind,
        /** /share/ token（非短碼），僅 SHARE_LINK 有值 */
        val shareToken: String = "",
        /** 原始連結是否帶 /media 尾綴（代表該貼文含圖片/影片） */
        val hadMediaSuffix: Boolean = false
    )

    val ParsedThreadsUrl.shareKindLabel: String
        get() = when (shareKind) {
            ShareKind.POST -> "串文"
            ShareKind.SHORT_T -> "短鏈"
            ShareKind.SHARE_LINK -> "留言 / 分享"
            ShareKind.UNKNOWN -> ""
        }

    /**
     * 去除分享追蹤參數（?xmt= 跨 App 歸因 token、?slof=、?utm_* 等）。
     * 乾淨形 https://www.threads.com/@handle/post/CODE 照常開啟，不影響內容。
     * 顯示／複製／外部開啟一律走此 helper；舊資料若已存入髒網址，讀取側也會被清掉。
     */
    fun stripTrackingParams(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        return rawUrl.split("?")[0].trim().trimEnd('/').trim()
    }

    /**
     * Extracts first Threads URL from raw text (such as Intent.EXTRA_TEXT).
     * 容忍中文標點結尾（。，、！？；：「」『』【】）與 App 自動截斷的 "..."。
     */
    fun extractFirstThreadsUrl(text: String): String? {
        val urlRegex = Regex("""https?://[^\s<>"']+""")
        val matches = urlRegex.findAll(text)
        for (match in matches) {
            var url = match.value
                // App 有時會把長連結截斷成 "..." —— 去掉它再判斷
                .trimEnd('.', ',', ')', ']', '}', '?', '!', ';', ':')
                .trimEnd('。', '，', '、', '！', '？', '；', '：', '「', '」', '『', '』', '【', '】', '…')
            if (url.endsWith("...")) continue
            // 有些分享文字會把 URL 包在括號裡
            url = url.trimStart('(', '[', '{', '「', '『', '【', '<')
            if (isThreadsHost(url)) {
                return url
            }
        }
        return null
    }

    fun isShareLink(url: String): Boolean {
        return try {
            val uri = URI(url.split("?")[0].trim())
            if (!VALID_HOSTS.contains(uri.host?.lowercase())) return false
            val path = uri.path.orEmpty()
            SHARE_PATH_REGEX.containsMatchIn(path)
        } catch (_: Exception) {
            false
        }
    }

    private fun isThreadsHost(rawUrl: String): Boolean {
        return try {
            val uri = URI(rawUrl)
            val host = uri.host?.lowercase() ?: return false
            VALID_HOSTS.contains(host)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 從分享文字中扣除 URL，取出內文草稿。
     * Threads App 的 ACTION_SEND 常把貼文/留言全文一起送來（URL + 內文），
     * 這是伺服器端抓不到時最可靠的內文來源。
     */
    fun extractBodyDraft(sharedText: String, threadsUrl: String?): String {
        var draft = sharedText
        if (!threadsUrl.isNullOrBlank()) {
            draft = draft.replace(threadsUrl, "")
            // URL 可能帶或不帶 query，去 query 版也清掉
            val noQuery = threadsUrl.split("?")[0]
            draft = draft.replace(noQuery, "")
        }
        // 清掉常見的包覆符號與多餘空行
        draft = draft.trim().trim('"', '"', '"', '\'')
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        return draft
    }

    /**
     * Canonicalizes Threads URL:
     * https://www.threads.com/@{lower(handle)}/post/{shortcode}
     * 無 handle 時退回 https://www.threads.com/t/{shortcode}。
     * 同時保留 originalUrl + shareKind 供抓取層使用。
     */
    fun parseAndCanonicalize(rawUrl: String): ParsedThreadsUrl? {
        return try {
            val trimmed = rawUrl.trim()
            if (trimmed.isBlank()) return null
            // strip query (?xmt=TOKEN、?utm_* 等追蹤參數不影響 shortcode，但 original 要保留乾淨版供 redirect)
            var withoutQuery = trimmed.split("?")[0].trim().trimEnd('/')
            // 正規化媒體/內嵌尾綴：/@user/post/CODE/media → /@user/post/CODE
            // （複製影片連結會拿到 /media 結尾；內文頁與媒體頁是同一篇）
            var pathForMatch = try {
                URI(withoutQuery).path?.trimEnd('/') ?: withoutQuery
            } catch (_: Exception) {
                withoutQuery
            }
            var hadMediaSuffix = false
            for (suffix in listOf("/media", "/embed")) {
                if (pathForMatch.endsWith(suffix, ignoreCase = true)) {
                    pathForMatch = pathForMatch.dropLast(suffix.length).trimEnd('/')
                    hadMediaSuffix = true
                    break
                }
            }
            if (hadMediaSuffix) {
                withoutQuery = try {
                    val uri = URI(withoutQuery)
                    URI(uri.scheme, uri.authority, pathForMatch, null, null).toString().trimEnd('/')
                } catch (_: Exception) {
                    withoutQuery
                }
            }
            val uri = URI(withoutQuery)
            val host = uri.host?.lowercase() ?: return null
            if (!VALID_HOSTS.contains(host)) return null

            val path = uri.path?.trimEnd('/') ?: return null

            var handle = "threads_user"
            var shortcode = ""
            var kind = ShareKind.UNKNOWN
            var shareToken = ""

            val postMatch = POST_PATH_REGEX.find(path)
            if (postMatch != null) {
                handle = postMatch.groupValues[1].lowercase()
                shortcode = postMatch.groupValues[2]
                kind = ShareKind.POST
            } else {
                val shareMatch = SHARE_PATH_REGEX.find(path)
                if (shareMatch != null) {
                    // /share/ 後面是分享 token，不是短碼：短碼未知，canonical 保留原鏈
                    shareToken = shareMatch.groupValues[1]
                    kind = ShareKind.SHARE_LINK
                } else {
                    val tMatch = T_PATH_REGEX.find(path)
                    if (tMatch != null) {
                        shortcode = tMatch.groupValues[1]
                        kind = ShareKind.SHORT_T
                    } else {
                        return null
                    }
                }
            }
            if (kind != ShareKind.SHARE_LINK && shortcode.isBlank()) return null
            if (kind == ShareKind.SHARE_LINK && shareToken.isBlank()) return null

            val canonicalUrl = when {
                handle != "threads_user" ->
                    "https://www.threads.com/@$handle/post/$shortcode"
                kind == ShareKind.SHARE_LINK ->
                    withoutQuery // 保留 /share/ 原鏈，瀏覽器開啟會自動展開
                else ->
                    "https://www.threads.com/t/$shortcode"
            }
            val hash = sha256(canonicalUrl)

            ParsedThreadsUrl(
                handle = handle,
                shortcode = shortcode,
                canonicalUrl = canonicalUrl,
                urlHash = hash,
                originalUrl = withoutQuery,
                shareKind = kind,
                shareToken = shareToken,
                hadMediaSuffix = hadMediaSuffix
            )
        } catch (_: Exception) {
            null
        }
    }

    // ───────────────────────── Instagram ─────────────────────────
    //
    // IG 分享連結形態（實測 Instagram App 分享鈕輸出）：
    //  1. 貼文  https://www.instagram.com/p/{code}/（可帶 ?vrfl=… / ?igsh=… 等追蹤參數）
    //  2. Reels https://www.instagram.com/reel/{code}/ 或 /reels/{code}/
    //  3. IGTV  https://www.instagram.com/tv/{code}/
    //  4. 短鏈  https://instagram.com/share/{token}（需 redirect，抓取層處理）
    private val IG_VALID_HOSTS = setOf(
        "instagram.com",
        "www.instagram.com",
        "instagr.am",
        "www.instagr.am"
    )
    private val IG_POST_PATH_REGEX = Regex("""/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""")
    private val IG_SHARE_PATH_REGEX = Regex("""/share/([A-Za-z0-9_-]+)""")

    /** 平台常數（items.platform 欄位值） */
    const val PLATFORM_THREADS = "THREADS"
    const val PLATFORM_INSTAGRAM = "INSTAGRAM"

    data class ParsedInstagramUrl(
        val shortcode: String,
        /** 正規形 https://www.instagram.com/p/{code}/ */
        val canonicalUrl: String,
        /** 實際拿去抓取的 URL（去 query 但保留 path 原樣） */
        val fetchUrl: String,
        val kindLabel: String,
        val isShareLink: Boolean,
        val shareToken: String = ""
    )

    /** 是否為 Instagram 連結（host 為 instagram 相關網域） */
    fun isInstagramHost(rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return false
        return try {
            val host = URI(rawUrl.split("?")[0].trim()).host?.lowercase() ?: return false
            IG_VALID_HOSTS.contains(host)
        } catch (_: Exception) {
            rawUrl.contains("instagram.com")
        }
    }

    /** 從分享文字中抽出第一個 Instagram 連結（容錯規則同 Threads 版）。 */
    fun extractFirstInstagramUrl(text: String): String? {
        val urlRegex = Regex("""https?://[^\s<>"']+""")
        for (match in urlRegex.findAll(text)) {
            var url = match.value
                .trimEnd('.', ',', ')', ']', '}', '?', '!', ';', ':')
                .trimEnd('。', '，', '、', '！', '？', '；', '：', '「', '」', '『', '』', '【', '】', '…')
            if (url.endsWith("...")) continue
            url = url.trimStart('(', '[', '{', '「', '『', '【', '<')
            if (isInstagramHost(url)) return url
        }
        return null
    }

    /**
     * 解析 Instagram 連結（貼文/reel/tv/share 短鏈）。
     * 貼文 canonical 為 https://www.instagram.com/p/{code}/；
     * /share/ 直接保留原鏈（token 不是短碼，需抓取層 redirect 展開）。
     */
    fun parseInstagramUrl(rawUrl: String): ParsedInstagramUrl? {
        return try {
            val trimmed = rawUrl.trim().trimEnd('/', '\\', '"', '\'', '。', '，', '！', '？')
            if (trimmed.isBlank() || !isInstagramHost(trimmed)) return null
            val withoutQuery = trimmed.split("?")[0].trim().trimEnd('/')
            val uri = URI(withoutQuery)
            val path = uri.path.orEmpty().trimEnd('/')
            val host = uri.host?.lowercase() ?: return null
            if (host !in IG_VALID_HOSTS) return null

            val shareMatch = IG_SHARE_PATH_REGEX.find(path)
            if (shareMatch != null) {
                val token = shareMatch.groupValues[1]
                return ParsedInstagramUrl(
                    shortcode = "igshare_$token",
                    canonicalUrl = withoutQuery,
                    fetchUrl = withoutQuery,
                    kindLabel = "IG 分享",
                    isShareLink = true,
                    shareToken = token
                )
            }
            val postMatch = IG_POST_PATH_REGEX.find(path) ?: return null
            val code = postMatch.groupValues[1]
            if (code.isBlank()) return null
            val type = Regex("""/(p|reel|reels|tv)/""").find(path)?.groupValues?.get(1) ?: "p"
            val canonicalType = if (type == "p" || type == "tv") "p" else "reel"
            ParsedInstagramUrl(
                shortcode = code,
                canonicalUrl = "https://www.instagram.com/$canonicalType/$code/",
                fetchUrl = "https://www.instagram.com/$canonicalType/$code/",
                kindLabel = if (canonicalType == "reel") "Reels" else "貼文",
                isShareLink = false
            )
        } catch (_: Exception) {
            null
        }
    }

    /** 去除 IG 追蹤參數（?igsh=?vrfl=?utm_* 等），顯示／複製／外部開啟一律走此 helper。 */
    fun stripInstagramParams(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        return rawUrl.split("?")[0].trim().trimEnd('/').trim()
    }

    // ───────────────────────── X (Twitter) ─────────────────────────
    //
    // X 貼文連結形態（官方 App 分享鈕輸出 + web 常見變體）：
    //  1. https://x.com/{handle}/status/{id}（可帶 ?s=20 / ?t=… 等追蹤參數）
    //  2. https://twitter.com/{handle}/status/{id}（舊網域）
    //  3. https://mobile.twitter.com/{handle}/status/{id}、https://www.x.com/...
    //  4. https://x.com/i/web/status/{id}（web app 內部路徑，無 handle）
    //  尾端 /photo/1、/video/1、/analytics 為媒體/統計頁，屬同一則貼文，正規化時去除。
    private val X_VALID_HOSTS = setOf(
        "x.com",
        "www.x.com",
        "twitter.com",
        "www.twitter.com",
        "mobile.twitter.com",
        "m.twitter.com"
    )
    private val X_STATUS_PATH_REGEX =
        Regex("""/i/web/status/(\d+)|/([A-Za-z0-9_]{1,20})/status(?:es)?/(\d+)""")
    private val X_MEDIA_SUFFIX_REGEX = Regex("""/(?:photo|video)/\d+$|/analytics$""")

    /** 平台常數（items.platform 欄位值）——X (Twitter) */
    const val PLATFORM_X = "X"

    // ───────────────────────── Facebook ─────────────────────────
    private val FB_VALID_HOSTS = setOf(
        "facebook.com",
        "www.facebook.com",
        "m.facebook.com",
        "web.facebook.com",
        "fb.com",
        "fb.watch"
    )
    /** 平台常數（items.platform 欄位值）——Facebook */
    const val PLATFORM_FACEBOOK = "FACEBOOK"

    // ───────────────────────── YouTube ─────────────────────────
    private val YT_VALID_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "youtu.be"
    )
    /** 平台常數（items.platform 欄位值）——YouTube */
    const val PLATFORM_YOUTUBE = "YOUTUBE"

    data class ParsedXUrl(
        /** 可能為空（/i/web/status/ 形無 handle；抓取後由 API 補齊） */
        val handle: String,
        val statusId: String,
        /** 正規形 https://x.com/{handle|'i/web'}/status/{id} */
        val canonicalUrl: String,
        /** 實際拿去抓取的 URL（去 query；fxtwitter API 同一路徑格式） */
        val fetchUrl: String,
        /** UI 顯示用 */
        val kindLabel: String = "X 貼文",
        /** 是否帶 /photo|video/N 或 /analytics 尾綴（同一則） */
        val hadMediaSuffix: Boolean = false
    )

    /** 是否為 X/Twitter 連結（host 為 x.com / twitter 相關網域） */
    fun isXHost(rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return false
        return try {
            val host = URI(rawUrl.split("?")[0].trim()).host?.lowercase() ?: return false
            X_VALID_HOSTS.contains(host)
        } catch (_: Exception) {
            rawUrl.contains("x.com/") || rawUrl.contains("twitter.com/")
        }
    }

    /** 從分享文字中抽出第一個 X 連結（容錯規則同 Threads 版）。 */
    fun extractFirstXUrl(text: String): String? {
        val urlRegex = Regex("""https?://[^\s<>"']+""")
        for (match in urlRegex.findAll(text)) {
            var url = match.value
                .trimEnd('.', ',', ')', ']', '}', '?', '!', ';', ':')
                .trimEnd('。', '，', '、', '！', '？', '；', '：', '「', '」', '『', '』', '【', '】', '…')
            if (url.endsWith("...")) continue
            url = url.trimStart('(', '[', '{', '「', '『', '【', '<')
            if (isXHost(url)) return url
        }
        return null
    }

    /** 去除 X 追蹤參數（?s=09 / ?t=… / ?utm_* 等），顯示／複製／外部開啟一律走此 helper。 */
    fun stripXParams(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        return rawUrl.split("?")[0].trim().trimEnd('/').trim()
    }

    /**
     * 解析 X 貼文連結。canonical 取 handle（無 handle 退 /i/web/status/），
     * 抓取層（fxtwitter）會補齊真實 handle 與作者資訊。
     */
    fun parseXUrl(rawUrl: String): ParsedXUrl? {
        return try {
            val trimmed = rawUrl.trim().trimEnd('/', '\\', '"', '\'', '。', '，', '！', '？')
            if (trimmed.isBlank() || !isXHost(trimmed)) return null
            val withoutQuery = trimmed.split("?")[0].trim().trimEnd('/')
            val uri = try { URI(withoutQuery) } catch (_: Exception) { return null }
            val host = uri.host?.lowercase() ?: return null
            if (host !in X_VALID_HOSTS) return null

            val path = uri.path.orEmpty().trimEnd('/')
            val hadSuffix = X_MEDIA_SUFFIX_REGEX.containsMatchIn(path)
            val cleanPath = X_MEDIA_SUFFIX_REGEX.replace(path, "")
            val m = X_STATUS_PATH_REGEX.find(cleanPath) ?: return null
            val handle: String
            val statusId: String
            if (m.groupValues[1].isNotBlank()) {
                // /i/web/status/{id}：無 handle
                handle = ""
                statusId = m.groupValues[1]
            } else {
                handle = m.groupValues[2]
                statusId = m.groupValues[3]
            }
            if (statusId.isBlank()) return null

            val canonicalPath = if (handle.isNotBlank()) "/$handle/status/$statusId"
            else "/i/web/status/$statusId"
            ParsedXUrl(
                handle = handle,
                statusId = statusId,
                canonicalUrl = "https://x.com$canonicalPath",
                fetchUrl = "https://x.com$canonicalPath",
                hadMediaSuffix = hadSuffix
            )
        } catch (_: Exception) {
            null
        }
    }

    data class ParsedFacebookUrl(
        val postId: String,
        val canonicalUrl: String,
        val fetchUrl: String,
        val kindLabel: String = "FB 貼文",
        val isShareLink: Boolean = false
    )

    /** 是否為 Facebook 連結 */
    fun isFacebookHost(rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return false
        return try {
            val host = URI(rawUrl.split("?")[0].trim()).host?.lowercase() ?: return false
            FB_VALID_HOSTS.contains(host) || host.endsWith(".facebook.com")
        } catch (_: Exception) {
            rawUrl.contains("facebook.com/") || rawUrl.contains("fb.watch/")
        }
    }

    /** 從分享文字中抽出第一個 Facebook 連結 */
    fun extractFirstFacebookUrl(text: String): String? {
        val urlRegex = Regex("""https?://[^\s<>"']+""")
        for (match in urlRegex.findAll(text)) {
            var url = match.value
                .trimEnd('.', ',', ')', ']', '}', '?', '!', ';', ':')
                .trimEnd('。', '，', '、', '！', '？', '；', '：', '「', '」', '『', '』', '【', '】', '…')
            if (url.endsWith("...")) continue
            url = url.trimStart('(', '[', '{', '「', '『', '【', '<')
            if (isFacebookHost(url)) return url
        }
        return null
    }

    /** 去除 Facebook 追蹤參數（?mibextid=... / ?rdid=... / ?share_id=... 等） */
    fun stripFacebookParams(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        return rawUrl.split("?")[0].trim().trimEnd('/').trim()
    }

    /** 解析 Facebook 連結（貼文、Reels、影片或 share 短鏈） */
    fun parseFacebookUrl(rawUrl: String): ParsedFacebookUrl? {
        return try {
            val trimmed = rawUrl.trim().trimEnd('/', '\\', '"', '\'', '。', '，', '！', '？')
            if (trimmed.isBlank() || !isFacebookHost(trimmed)) return null
            val withoutQuery = trimmed.split("?")[0].trim().trimEnd('/')
            val uri = try { URI(withoutQuery) } catch (_: Exception) { return null }
            val host = uri.host?.lowercase() ?: return null
            if (!FB_VALID_HOSTS.contains(host) && !host.endsWith(".facebook.com")) return null

            val path = uri.path.orEmpty().trimEnd('/')
            val shareMatch = Regex("""/share/(?:p|r|v)/([A-Za-z0-9_-]+)""").find(path)
            val isShare = shareMatch != null
            val id = shareMatch?.groupValues?.get(1)
                ?: Regex("""/(?:posts|videos|reel)/([A-Za-z0-9_-]+)""").find(path)?.groupValues?.get(1)
                ?: "fb_${sha256(trimmed).take(12)}"

            val label = when {
                path.contains("/share/r/") || path.contains("/reel/") -> "FB Reels"
                path.contains("/share/v/") || path.contains("/videos/") || host == "fb.watch" -> "FB 影片"
                else -> "FB 貼文"
            }
            ParsedFacebookUrl(
                postId = id,
                canonicalUrl = withoutQuery,
                fetchUrl = trimmed,
                kindLabel = label,
                isShareLink = isShare
            )
        } catch (_: Exception) {
            null
        }
    }

    data class ParsedYouTubeUrl(
        val videoId: String,
        val canonicalUrl: String,
        val fetchUrl: String,
        val kindLabel: String = "YouTube 影片",
        val isShorts: Boolean = false
    )

    /** 是否為 YouTube 連結 */
    fun isYouTubeHost(rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return false
        return try {
            val host = URI(rawUrl.split("?")[0].trim()).host?.lowercase() ?: return false
            YT_VALID_HOSTS.contains(host) || host.endsWith(".youtube.com")
        } catch (_: Exception) {
            rawUrl.contains("youtube.com/") || rawUrl.contains("youtu.be/")
        }
    }

    /** 從分享文字中抽出第一個 YouTube 連結 */
    fun extractFirstYouTubeUrl(text: String): String? {
        val urlRegex = Regex("""https?://[^\s<>"']+""")
        for (match in urlRegex.findAll(text)) {
            var url = match.value
                .trimEnd('.', ',', ')', ']', '}', '?', '!', ';', ':')
                .trimEnd('。', '，', '、', '！', '？', '；', '：', '「', '」', '『', '』', '【', '】', '…')
            if (url.endsWith("...")) continue
            url = url.trimStart('(', '[', '{', '「', '『', '【', '<')
            if (isYouTubeHost(url)) return url
        }
        return null
    }

    /** 去除 YouTube 追蹤與播放清單多餘參數（保留 watch?v=... 或 shorts/...） */
    fun stripYouTubeParams(rawUrl: String): String {
        return parseYouTubeUrl(rawUrl)?.canonicalUrl ?: rawUrl.trim()
    }

    /** 解析 YouTube 連結（標準影片、Shorts、youtu.be 短鏈） */
    fun parseYouTubeUrl(rawUrl: String): ParsedYouTubeUrl? {
        return try {
            val trimmed = rawUrl.trim().trimEnd('/', '\\', '"', '\'', '。', '，', '！', '？')
            if (trimmed.isBlank() || !isYouTubeHost(trimmed)) return null

            val uri = try { URI(trimmed) } catch (_: Exception) { return null }
            val host = uri.host?.lowercase() ?: return null
            if (!YT_VALID_HOSTS.contains(host) && !host.endsWith(".youtube.com")) return null

            val path = uri.path.orEmpty()
            val videoId = when {
                host == "youtu.be" -> path.trim('/').split("/").firstOrNull()?.takeIf { it.isNotBlank() }
                path.contains("/shorts/") -> Regex("""/shorts/([A-Za-z0-9_-]+)""").find(path)?.groupValues?.get(1)
                path.contains("/watch") -> {
                    val query = uri.query.orEmpty()
                    query.split("&").mapNotNull {
                        val p = it.split("=")
                        if (p.size == 2 && p[0] == "v") p[1] else null
                    }.firstOrNull()
                }
                path.contains("/embed/") -> Regex("""/embed/([A-Za-z0-9_-]+)""").find(path)?.groupValues?.get(1)
                else -> null
            }
            if (videoId.isNullOrBlank()) return null

            val isShorts = path.contains("/shorts/")
            val canonical = if (isShorts) "https://www.youtube.com/shorts/$videoId"
            else "https://www.youtube.com/watch?v=$videoId"

            ParsedYouTubeUrl(
                videoId = videoId,
                canonicalUrl = canonical,
                fetchUrl = canonical,
                kindLabel = if (isShorts) "YouTube Shorts" else "YouTube 影片",
                isShorts = isShorts
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
