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

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
