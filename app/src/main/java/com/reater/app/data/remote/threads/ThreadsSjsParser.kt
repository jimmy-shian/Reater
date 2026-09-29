package com.reater.app.data.remote.threads

import com.reater.app.data.remote.FetchedComment
import com.reater.app.data.remote.FetchedMedia
import org.json.JSONArray
import org.json.JSONObject

/**
 * Threads SJS 內嵌 JSON 解析器（主力解析器）。
 *
 * 方法來源：cobalt PR #1558（threads extractor）+ discordbot threads.py 交叉驗證。
 * Threads 把貼文（含母串、回覆）以 data-sjs JSON 塊嵌在 HTML 裡：
 *   <script type="application/json" ... data-sjs ...>{"require":... "thread_items":[{"post":{...}}]}}
 * 以短碼精確匹配 post.code，而非猜第一個 caption。
 *
 * post 物件關鍵欄位（Instagram 系 shape）：
 *  code / caption{text} / user{username, profile_pic_url, is_verified} /
 *  taken_at / like_count / image_versions2{candidates[{url,width,height}](大→小)} /
 *  video_versions[{url}](同檔多版，取首個有效) / carousel_media[] /
 *  giphy_media_info{images{fixed_height{url}}} /
 *  text_post_app_info{text_fragments{fragments[{plaintext}]},
 *    share_info{quoted_attachment_post}, linked_inline_media}
 */
object ThreadsSjsParser {

    data class SjsResult(
        val bodyText: String,
        val authorHandle: String,
        val authorDisplayName: String,
        val authorProfileUrl: String,
        val authorVerified: Boolean,
        val postedAtMs: Long,
        val likeCount: Int,
        val media: List<FetchedMedia>,
        /** 非主貼文的其他 thread_items（含回覆與母串），上限 50 */
        val comments: List<FetchedComment>
    )

    const val MAX_COMMENTS = 50
    private const val MAX_DEPTH = 25

    private val sjsRegex =
        Regex("""<script type="application/json"[^>]*\bdata-sjs\b[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    fun parse(html: String, shortcode: String): SjsResult? {
        if (html.isBlank() || shortcode.isBlank()) return null
        // /share/ 未解析時 shortcode 是 share_TOKEN 佔位符：SJS 內絕無此 code，
        // 直接跳過，避免誤匹配到別篇貼文的 caption 當留言。
        if (shortcode.startsWith("share_")) return null
        val allBlocks = sjsRegex.findAll(html)
            .map { it.groupValues[1] }
            .filter { it.contains("thread_items") }
            .take(60)
            .toList()
        // 實測（2026-05，dev.to/Apify 交叉驗證）：正文 payload 位於同時含
        // thread_items + BarcelonaPostPage 的 data-sjs 塊；其餘為側欄/相關貼文/廣告。
        // 必須優先解析該塊，否則留言會混入別篇貼文。
        val ranked = (allBlocks.filter { it.contains("BarcelonaPostPage") } +
            allBlocks.filter { !it.contains("BarcelonaPostPage") }).take(40)
        return parseBlocks(ranked, shortcode)
    }

    /**
     * 直接解析 SJS JSON 文本列表（供 WebView 渲染路徑使用：
     * JS 端已用 querySelectorAll 抽好 script 內容，不必再跑 HTML 正則）。
     */
    fun parseBlocks(blocks: List<String>, shortcode: String): SjsResult? {
        if (blocks.isEmpty() || shortcode.isBlank() || shortcode.startsWith("share_")) return null
        // BarcelonaPostPage 優先（同 parse(html) 邏輯，供 WebView 路徑使用）
        val ranked = (blocks.filter { it.contains("BarcelonaPostPage") } +
            blocks.filter { !it.contains("BarcelonaPostPage") })
        val posts = mutableListOf<JSONObject>()
        // 記錄每個 post 來自哪個 block 是否為正文塊，供留言過濾用
        val mainBlockIdx = mutableMapOf<String, Int>()
        for ((bi, block) in ranked.withIndex()) {
            if (!block.contains("thread_items")) continue
            try {
                val before = posts.size
                collectPosts(JSONObject(block), posts, 0)
                for (i in before until posts.size) {
                    val code = posts[i].optString("code")
                    if (code.isNotBlank() && !mainBlockIdx.containsKey(code + "@" + i)) {
                        mainBlockIdx[code + "@" + i] = bi
                    }
                }
            } catch (_: Exception) {
                continue
            }
            if (posts.size > 200) break
        }
        if (posts.isEmpty()) return null

        val main = posts.firstOrNull { it.optString("code") == shortcode }
            ?: return null

        val user = main.optJSONObject("user")
        val authorHandle = user?.optString("username").orEmpty()
        val authorVerified = user?.optBoolean("is_verified", false) == true
        val authorProfileUrl = user?.optString("profile_pic_url").orEmpty()
        val takenAtSec = main.optLong("taken_at", 0L)
        val likeCount = main.optInt("like_count", 0)

        val body = postText(main)
        val media = postMedia(main)

        val comments = mutableListOf<FetchedComment>()
        val seen = HashSet<String>()
        var idx = 0
        // 主貼文的 pk / 作者 / 時間，供回覆歸屬判斷（避免母串/相關貼文混入）
        val mainPk = main.optString("pk").ifBlank { main.optString("id").substringBefore("_") }
        val mainAuthor = authorHandle
        val mainTakenAt = main.optLong("taken_at", 0L)
        for (p in posts) {
            if (comments.size >= MAX_COMMENTS) break
            if (p === main) continue
            val pCode = p.optString("code")
            if (pCode.isNotBlank() && pCode == shortcode) continue
            val text = postText(p)
            if (text.isBlank()) continue
            if (text == body) continue
            // 必須是真正的回覆：text_post_app_info.is_reply==true 或 reply_to_author 非空。
            // 頂層母串（is_reply==false 且無 reply_to）一律排除，否則會把同串前文當留言。
            if (!isTrueReply(p)) continue
            // 回覆歸屬：reply_to / root 指向主串作者或主串 pk 才收；皆無時放行（舊 shape 相容）
            // 但若明確指向他人且與主串無關則排除，避免相關貼文污染。
            if (!belongsToThread(p, mainAuthor, mainPk, mainTakenAt)) continue
            // 作者必須可解析，否則跳過（不用 threads_reply 佔位，避免「抓不到真正留言人名稱」）
            val author = resolveReplyAuthor(p)
            if (author.isBlank()) continue
            val key = author + "||" + text.length + "||" + text
            if (!seen.add(key)) continue
            comments.add(
                FetchedComment(
                    externalId = "sjs_c_$idx",
                    author = author,
                    text = text,
                    likeCount = p.optInt("like_count", 0)
                )
            )
            idx++
        }

        // 寬鬆 fallback：嚴格過濾零結果時（免登入缺 tpa 常見），僅排除主串/同文/無作者再收一次
        if (comments.isEmpty()) {
            for (p in posts) {
                if (comments.size >= MAX_COMMENTS) break
                if (p === main) continue
                val pCode = p.optString("code")
                if (pCode.isNotBlank() && pCode == shortcode) continue
                val text = postText(p)
                if (text.isBlank() || text == body) continue
                val author = resolveReplyAuthor(p)
                if (author.isBlank()) continue
                val key = author + "||" + text.length + "||" + text
                if (!seen.add(key)) continue
                comments.add(
                    FetchedComment(
                        externalId = "sjs_c_fb_$idx",
                        author = author,
                        text = text,
                        likeCount = p.optInt("like_count", 0)
                    )
                )
                idx++
            }
        }

        return SjsResult(
            bodyText = body,
            authorHandle = authorHandle,
            authorDisplayName = authorHandle,
            authorProfileUrl = authorProfileUrl,
            authorVerified = authorVerified,
            postedAtMs = if (takenAtSec > 0) takenAtSec * 1000 else 0L,
            likeCount = likeCount,
            media = media,
            comments = comments
        )
    }

    // ---------- 遞迴收集 ----------

    private fun collectPosts(node: Any?, out: MutableList<JSONObject>, depth: Int) {
        if (node == null || depth > MAX_DEPTH || out.size > 300) return
        when (node) {
            is JSONObject -> {
                val items = node.optJSONArray("thread_items")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val post = items.optJSONObject(i)?.optJSONObject("post")
                        if (post != null && post.optString("code").isNotBlank()) {
                            out.add(post)
                        }
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    collectPosts(node.opt(keys.next()), out, depth + 1)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectPosts(node.opt(i), out, depth + 1)
                }
            }
        }
    }

    // ---------- 貼文文字 ----------

    /** 是否為真正的回覆（排除母串/頂層貼文混入留言） */
    private fun isTrueReply(post: JSONObject): Boolean {
        val tpa = post.optJSONObject("text_post_app_info")
        if (tpa == null) {
            // 免登入 SSR 常缺 tpa：有作者有內文即視為候選，交給 belongsToThread + 時間過濾
            return true
        }
        // 新 shape：is_reply 明確標記
        if (tpa.has("is_reply")) {
            // is_reply==false 可能是母串；但若有 reply_to/root 仍視為回覆（巢狀相容）
            if (tpa.optBoolean("is_reply", false)) return true
            val replyTo = tpa.optJSONObject("reply_to_author")?.optString("username").orEmpty()
            if (replyTo.isNotBlank()) return true
            if (tpa.optJSONObject("root_post_author") != null) return true
            return false
        }
        // 舊 shape 相容：有 reply_to_author 即視為回覆
        val replyTo = tpa.optJSONObject("reply_to_author")?.optString("username").orEmpty()
        if (replyTo.isNotBlank()) return true
        if (tpa.optJSONObject("root_post_author") != null) return true
        // 無任何標記：放行候選，靠 taken_at + 歸屬過濾（避免零留言）
        return true
    }

    /** 回覆是否歸屬本串（避免相關貼文/側欄污染） */
    private fun belongsToThread(post: JSONObject, mainAuthor: String, mainPk: String, mainTakenAt: Long = 0L): Boolean {
        // 時間過濾：明顯早於主串的頂層貼文視為母串排除（容差 5 秒）
        if (mainTakenAt > 0) {
            val pt = post.optLong("taken_at", 0L)
            val tpaTmp = post.optJSONObject("text_post_app_info")
            val isReplyFlag = tpaTmp?.optBoolean("is_reply", false) == true ||
                tpaTmp?.optJSONObject("reply_to_author") != null
            if (!isReplyFlag && pt > 0 && pt + 5 < mainTakenAt) return false
        }
        val tpa = post.optJSONObject("text_post_app_info") ?: return true // 無資訊時放行（舊 shape）
        val replyTo = tpa.optJSONObject("reply_to_author")?.optString("username").orEmpty()
        val replyToId = tpa.optJSONObject("reply_to_author")?.optString("id").orEmpty()
            .ifBlank { tpa.optJSONObject("reply_to_author")?.optString("pk").orEmpty() }
        val rootPk = tpa.optJSONObject("root_post_author")?.optString("pk").orEmpty()
            .ifBlank { tpa.optJSONObject("root_post_author")?.optString("id").orEmpty() }
        // 直接回主串：reply_to == 主作者
        if (replyTo.isNotBlank() && mainAuthor.isNotBlank() &&
            replyTo.equals(mainAuthor, ignoreCase = true)
        ) return true
        // 巢狀回覆：root 指向主串 pk
        if (rootPk.isNotBlank() && mainPk.isNotBlank() && rootPk == mainPk) return true
        // reply_to id 與主串 pk 一致（數字 id 比對）
        if (replyToId.isNotBlank() && mainPk.isNotBlank() && replyToId == mainPk) return true
        // 若三者皆空（舊 shape 無歸屬資訊）：放行，避免誤殺
        if (replyTo.isBlank() && rootPk.isBlank() && replyToId.isBlank()) return true
        // 明確指向他人且與主串無關：可能是相關貼文，排除
        // 但巢狀回覆（回覆別則留言）root 仍應指向主串；若 root 缺失則放行以免誤殺
        return rootPk.isBlank()
    }

    /** 回覆作者多路徑解析：user.username → 直屬 username → author.username */
    private fun resolveReplyAuthor(post: JSONObject): String {
        fun clean(s: String?): String {
            val v = s.orEmpty().trim().trimStart('@')
            if (v.isBlank() || v.equals("null", ignoreCase = true)) return ""
            // 過濾明顯非帳號字串
            if (v.contains(" ") || v.contains("\n") || v.length > 30) return ""
            return v
        }
        clean(post.optJSONObject("user")?.optString("username")).takeIf { it.isNotBlank() }?.let { return it }
        clean(post.optString("username")).takeIf { it.isNotBlank() }?.let { return it }
        clean(post.optJSONObject("author")?.optString("username")).takeIf { it.isNotBlank() }?.let { return it }
        clean(
            post.optJSONObject("text_post_app_info")?.optJSONObject("owner")
                ?.optString("username")
        ).takeIf { it.isNotBlank() }?.let { return it }
        // text_post_app_info.reply_to 的對端不是作者，不取；最後嘗試 pk 對應（無帳號則空）
        return ""
    }

    /** caption.text 優先；無則拼 text_fragments plaintext（官方欄位，見 EasyDown 文件） */
    fun postText(post: JSONObject): String {
        val rawCap = post.optJSONObject("caption")?.optString("text").orEmpty()
        val caption = if (rawCap == "null") "" else rawCap
        if (caption.isNotBlank()) return caption.trim()
        val fragments = post.optJSONObject("text_post_app_info")
            ?.optJSONObject("text_fragments")
            ?.optJSONArray("fragments") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until fragments.length()) {
            val part = fragments.optJSONObject(i)?.optString("plaintext").orEmpty()
            if (part == "null") continue
            sb.append(part)
        }
        return sb.toString().trim()
    }

    // ---------- 媒體 ----------

    fun postMedia(post: JSONObject): List<FetchedMedia> {
        // GIF：giphy 直連 .gif
        val gif = post.optJSONObject("giphy_media_info")
            ?.optJSONObject("images")
            ?.optJSONObject("fixed_height")
            ?.optString("url").orEmpty()
        if (gif.isNotBlank()) {
            return listOf(FetchedMedia(kind = "IMAGE", remoteUrl = gif))
        }

        // 輪播：逐項取（影片優先，否則最大圖）
        val carousel = post.optJSONArray("carousel_media")
        if (carousel != null && carousel.length() > 0) {
            val out = mutableListOf<FetchedMedia>()
            for (i in 0 until minOf(carousel.length(), 10)) {
                val item = carousel.optJSONObject(i) ?: continue
                mediaFromItem(item)?.let { out.add(it) }
            }
            if (out.isNotEmpty()) return out.take(6)
        }

        // 單一媒體
        mediaFromItem(post)?.let { return listOf(it) }

        // 轉發包裝：quoted_attachment_post / linked_inline_media（遞迴一層）
        val tpa = post.optJSONObject("text_post_app_info")
        val wrappers = listOf(
            tpa?.optJSONObject("share_info")?.optJSONObject("quoted_attachment_post"),
            tpa?.optJSONObject("linked_inline_media")
        )
        for (w in wrappers) {
            if (w == null) continue
            val inner = postMedia(w)
            if (inner.isNotEmpty()) return inner
        }
        return emptyList()
    }

    private fun mediaFromItem(item: JSONObject): FetchedMedia? {
        // video_versions：同檔多版，取首個有效 url（cobalt 同策略）
        val videos = item.optJSONArray("video_versions")
        if (videos != null) {
            for (i in 0 until videos.length()) {
                val url = videos.optJSONObject(i)?.optString("url").orEmpty()
                if (url.startsWith("http")) {
                    return FetchedMedia(kind = "VIDEO", remoteUrl = url)
                }
            }
        }
        // image_versions2.candidates：大→小排序，取首個（最大）
        val candidates = item.optJSONObject("image_versions2")?.optJSONArray("candidates")
        if (candidates != null) {
            for (i in 0 until candidates.length()) {
                val c = candidates.optJSONObject(i) ?: continue
                val url = c.optString("url")
                if (url.startsWith("http")) {
                    return FetchedMedia(
                        kind = "IMAGE",
                        remoteUrl = url,
                        width = c.optInt("width", 0),
                        height = c.optInt("height", 0)
                    )
                }
            }
        }
        return null
    }
}
