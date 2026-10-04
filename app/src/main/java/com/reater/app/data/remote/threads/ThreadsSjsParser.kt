package com.reater.app.data.remote.threads

import com.reater.app.data.remote.FetchedComment
import com.reater.app.data.remote.FetchedMedia
import org.json.JSONArray
import org.json.JSONObject

/**
 * Threads SJS 內嵌 JSON 解析器（主力解析器）。
 *
 * 方法來源：cobalt PR #1558（threads extractor）+ discordbot threads.py 交叉驗證，
 * 並含 2026-09 Googlebot UA SSR 實測的新 shape。
 *
 * 舊 shape（thread_items，仍保留相容）：
 *   <script type="application/json" ... data-sjs ...>{"require":... "thread_items":[{"post":{...}}]}}
 *
 * 新 shape（2026-09 起，Googlebot UA 才拿得到）：
 *   主貼："data":{"media":{...post 物件含 code/caption...}}
 *   留言：data.media.text_post_app_info.direct_replies.edges[].node.posts.edges[].node
 *   每則留言是完整 post 物件（caption/like_count/taken_at/自己的 image/video/carousel）。
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

    data class ParentPost(
        val shortcode: String,
        val authorHandle: String,
        val bodyText: String,
        val likeCount: Int,
        val postedAtMs: Long,
        val media: List<FetchedMedia>
    )

    data class SjsResult(
        val bodyText: String,
        val authorHandle: String,
        val authorDisplayName: String,
        val authorProfileUrl: String,
        val authorVerified: Boolean,
        val postedAtMs: Long,
        val likeCount: Int,
        val media: List<FetchedMedia>,
        /** 留言（含每則留言自帶的圖/影媒體），上限 50 */
        val comments: List<FetchedComment>,
        /** /share/ 留言鏈的母文（主本身 is_reply==true 時回溯；頂層串文為 null） */
        val parent: ParentPost? = null,
        /**
         * 完整祖先鏈（root → … → 直接父層）。
         * 「留言的留言」（主回覆 B、B 回覆 A）會回溯出 [A, B]；
         * 單層回覆時只含 [A]，與 parent 相同；頂層串文為空。
         */
        val parentChain: List<ParentPost> = emptyList()
    )

    const val MAX_COMMENTS = 50
    private const val MAX_DEPTH = 40

    private val sjsRegex =
        Regex("""<script type="application/json"[^>]*\bdata-sjs\b[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    /** 是否為含貼文 payload 的 SJS 塊（新舊 shape 通吃） */
    private fun isPayloadBlock(block: String): Boolean =
        block.contains("thread_items") ||
            block.contains("\"direct_replies\"") ||
            block.contains("\"data\":{\"media\"")

    fun parse(html: String, shortcode: String): SjsResult? {
        if (html.isBlank() || shortcode.isBlank()) return null
        // /share/ 未解析時 shortcode 是 share_TOKEN 佔位符：SJS 內絕無此 code，
        // 直接跳過，避免誤匹配到別篇貼文的 caption 當留言。
        if (shortcode.startsWith("share_")) return null
        val allBlocks = sjsRegex.findAll(html)
            .map { it.groupValues[1] }
            .filter { isPayloadBlock(it) }
            .take(80)
            .toList()
        return parseBlocks(allBlocks, shortcode)
    }

    /**
     * 直接解析 SJS JSON 文本列表（供 WebView 渲染路徑使用：
     * JS 端已用 querySelectorAll 抽好 script 內容，不必再跑 HTML 正則）。
     */
    fun parseBlocks(blocks: List<String>, shortcode: String): SjsResult? {
        if (blocks.isEmpty() || shortcode.isBlank() || shortcode.startsWith("share_")) return null
        val relevant = blocks.filter { isPayloadBlock(it) }
        // 含本篇短碼的塊最先（避免別篇 payload 干擾）；其餘依 BarcelonaPostPage / 其他排序
        val hasCode = "\"code\":\"$shortcode\""
        val ranked = (relevant.filter { it.contains(hasCode) } +
            relevant.filter { !it.contains(hasCode) && it.contains("BarcelonaPostPage") } +
            relevant.filter { !it.contains(hasCode) && !it.contains("BarcelonaPostPage") })

        val legacyPosts = mutableListOf<JSONObject>()
        val replyPosts = mutableListOf<JSONObject>()
        val mainCandidates = mutableListOf<JSONObject>()
        val allPosts = mutableListOf<JSONObject>()
        val seenReplyPk = HashSet<String>()
        for (block in ranked) {
            try {
                val root = JSONObject(block)
                collectNewShape(root, shortcode, mainCandidates, replyPosts, seenReplyPk, 0)
                collectPosts(root, legacyPosts, 0)
                collectAllPosts(root, allPosts, 0)
            } catch (_: Exception) {
                continue
            }
            if (replyPosts.size > 200 && mainCandidates.isNotEmpty()) break
        }

        val main = mainCandidates.maxByOrNull { mainScore(it) }
            ?: legacyPosts.firstOrNull { it.optString("code") == shortcode }
            ?: return null

        val user = main.optJSONObject("user")
        val authorHandle = user?.optString("username").orEmpty()
        val authorVerified = user?.optBoolean("is_verified", false) == true
        val authorProfileUrl = user?.optString("profile_pic_url").orEmpty()
        val takenAtSec = main.optLong("taken_at", 0L)
        val likeCount = main.optInt("like_count", 0)

        val body = postText(main)
        val media = postMedia(main)

        // 主貼文的 pk / 作者 / 時間，供回覆歸屬判斷（避免母串/相關貼文/其他串混入）
        val mainPk = main.optString("pk").ifBlank { main.optString("id").substringBefore("_") }
        val mainAuthor = authorHandle
        val mainTakenAt = main.optLong("taken_at", 0L)
        val mainAuthorPk = user?.optString("pk").orEmpty()
            .ifBlank { user?.optString("id").orEmpty() }
        val mainTpa = main.optJSONObject("text_post_app_info")
        val mainIsReply = mainTpa?.optBoolean("is_reply", false) == true

        // /share/ 可能解析到留言而非主串（實測 BAV_nQgC4O → n.i.c.k.0.3.2.3 的留言，
        // 但使用者要的是 octopus5201314 的主串「新角拿了MVP」）。
        // 當主貼本身是留言時，自動回溯根貼文（root post）當主貼，原留言則降為留言。
        val effectiveMain: JSONObject
        if (mainIsReply) {
            val chain = findParentChain(main, allPosts, shortcode)
            val rootPost = chain.firstOrNull()?.let { root ->
                allPosts.find { it.optString("code") == root.shortcode }
            }
            // 12 self-thread 不回溯：主本身 is_reply 時，若 root 作者等於作者
            // 或 reply_to 等於作者，判為自串，不回溯 root。
            val rootAuthor = chain.firstOrNull()?.authorHandle.orEmpty()
            val replyToUser = mainTpa?.optJSONObject("reply_to_author")?.optString("username").orEmpty()
            val isSelfThread = (rootAuthor.isNotBlank() && rootAuthor.equals(authorHandle, ignoreCase = true)) ||
                (replyToUser.isNotBlank() && replyToUser.equals(authorHandle, ignoreCase = true))
            effectiveMain = if (isSelfThread) main else (rootPost ?: main)
        } else {
            effectiveMain = main
        }

        // 用 effectiveMain 重新取作者/時間/內文/媒體（根貼文可能與原留言不同）
        val effUser = effectiveMain.optJSONObject("user")
        val effAuthorHandle = effUser?.optString("username").orEmpty().ifBlank { authorHandle }
        val effAuthorVerified = effUser?.optBoolean("is_verified", false) == true || authorVerified
        val effAuthorProfileUrl = effUser?.optString("profile_pic_url").orEmpty().ifBlank { authorProfileUrl }
        val effTakenAtSec = effectiveMain.optLong("taken_at", 0L).let { if (it > 0) it else takenAtSec }
        val effLikeCount = effectiveMain.optInt("like_count", 0).let { if (it > 0) it else likeCount }
        val effBody = postText(effectiveMain).ifBlank { body }
        val effMedia = postMedia(effectiveMain).ifEmpty { media }

        // direct_replies 歸屬過濾：頂層串文（is_reply==false）沿用嚴格 root 檢查；
        // 子串分享（主本身 is_reply==true，如 D-JnnrknO/DzVwcgDJw 實測）root 指向最終祖先而非主，
        // 若仍要求 root==主會把 5+3 則子留言全滅 → 此時信任伺服器已限定的 direct_replies 全收，
        // 頂層串才額外用 reply_to==主當巢狀相容放行。
        val attributedReplies = replyPosts.filter { p ->
            if (mainAuthorPk.isBlank() && mainAuthor.isBlank()) return@filter true
            val tpa = p.optJSONObject("text_post_app_info")
            val root = tpa?.optJSONObject("root_post_author")
                ?: return@filter true
            // 子串：伺服器已限定範圍，直接放行（後續 resolveReplyAuthor/去重仍會擋空作者）
            if (effectiveMain !== main) return@filter true
            val rootPk = root.optString("pk").ifBlank { root.optString("id") }
            val rootUser = root.optString("username")
            val pkOk = rootPk.isNotBlank() && rootPk == mainAuthorPk
            val userOk = rootUser.isNotBlank() && rootUser.equals(mainAuthor, ignoreCase = true)
            if (pkOk || userOk) return@filter true
            // 巢狀相容：reply_to 直接指主也算屬於本串（root 可能指祖先）
            val replyTo = tpa.optJSONObject("reply_to_author")
            if (replyTo != null) {
                val rtUser = replyTo.optString("username")
                if (rtUser.isNotBlank() && rtUser.equals(mainAuthor, ignoreCase = true)) return@filter true
                val rtId = replyTo.optString("pk").ifBlank { replyTo.optString("id") }
                if (rtId.isNotBlank() && (rtId == mainAuthorPk || rtId == mainPk)) return@filter true
            }
            false
        }

        val comments = mutableListOf<FetchedComment>()
        val seen = HashSet<String>()
        var idx = 0

        // 12 延續置頂：頂層串文自回覆（同作者回自己、主文後 1 小時內、有文或圖）
        // 按 taken_at 升序置頂，其餘保原邊序。
        val orderedReplies: List<JSONObject> = if (!mainIsReply && mainTakenAt > 0 && effAuthorHandle.isNotBlank()) {
            val pinnedPks = HashSet<String>()
            val pinned = attributedReplies.filter { p ->
                val pauthor = resolveReplyAuthor(p)
                if (!pauthor.equals(effAuthorHandle, ignoreCase = true)) return@filter false
                val ptpa = p.optJSONObject("text_post_app_info") ?: return@filter false
                val replyTo = ptpa.optJSONObject("reply_to_author")?.optString("username").orEmpty()
                if (!replyTo.equals(effAuthorHandle, ignoreCase = true)) return@filter false
                val ptaken = p.optLong("taken_at", 0L)
                if (ptaken <= mainTakenAt || ptaken - mainTakenAt > 3600) return@filter false
                val ptext = postText(p)
                val hasMedia = try {
                    postMedia(p).isNotEmpty()
                } catch (_: Exception) {
                    false
                }
                if (ptext.isBlank() && !hasMedia) return@filter false
                val pk = p.optString("pk").ifBlank { p.optString("id") }
                if (pk.isNotBlank()) pinnedPks.add(pk)
                true
            }.sortedBy { it.optLong("taken_at", 0L) }
            if (pinned.isEmpty()) attributedReplies
            else pinned + attributedReplies.filter { p ->
                val pk = p.optString("pk").ifBlank { p.optString("id") }
                pk.isBlank() || !pinnedPks.contains(pk)
            }
        } else {
            attributedReplies
        }

        // 新 shape：direct_replies 留言（結構上已保證屬於本串，直接收，含留言媒體）
        for (p in orderedReplies) {
            if (comments.size >= MAX_COMMENTS) break
            val text = postText(p)
            val pMedia = postMedia(p)
            if (text.isBlank() && pMedia.isEmpty()) continue
            if (text == effBody && pMedia.isEmpty()) continue
            val author = resolveReplyAuthor(p)
            if (author.isBlank()) continue
            val key = author + "||" + text + "||" + (pMedia.firstOrNull()?.remoteUrl ?: "")
            if (!seen.add(key)) continue
            comments.add(
                FetchedComment(
                    externalId = "sjs_r_$idx",
                    author = author,
                    text = text,
                    likeCount = p.optInt("like_count", 0),
                    media = pMedia
                )
            )
            idx++
        }

        // 舊 shape thread_items 回覆（新 shape 已由 direct_replies 收齊，此處僅補舊頁）
        for (p in legacyPosts) {
            if (comments.size >= MAX_COMMENTS) break
            if (p === effectiveMain) continue
            val pCode = p.optString("code")
            if (pCode.isNotBlank() && pCode == shortcode) continue
            val text = postText(p)
            if (text.isBlank()) continue
            if (text == effBody) continue
            // 必須是真正的回覆：text_post_app_info.is_reply==true 或 reply_to_author 非空。
            // 頂層母串（is_reply==false 且無 reply_to）一律排除，否則會把同串前文當留言。
            if (!isTrueReply(p)) continue
            // 回覆歸屬：reply_to / root 指向主串作者或主串 pk 才收；皆無時放行（舊 shape 相容）
            // 但若明確指向他人且與主串無關則排除，避免相關貼文污染。
            if (!belongsToThread(p, mainAuthor, mainPk, mainTakenAt, mainAuthorPk)) continue
            // 作者必須可解析，否則跳過（不用 threads_reply 佔位，避免「抓不到真正留言人名稱」）
            val author = resolveReplyAuthor(p)
            if (author.isBlank()) continue
            val pMedia = postMedia(p)
            val key = author + "||" + text.length + "||" + text
            if (!seen.add(key)) continue
            comments.add(
                FetchedComment(
                    externalId = "sjs_c_$idx",
                    author = author,
                    text = text,
                    likeCount = p.optInt("like_count", 0),
                    media = pMedia
                )
            )
            idx++
        }

        // 寬鬆 fallback：嚴格過濾零結果時（免登入缺 tpa 常見），僅排除主串/同文/無作者再收一次
        if (comments.isEmpty()) {
            for (p in legacyPosts) {
                if (comments.size >= MAX_COMMENTS) break
                if (p === effectiveMain) continue
                val pCode = p.optString("code")
                if (pCode.isNotBlank() && pCode == shortcode) continue
                val text = postText(p)
                if (text.isBlank() || text == effBody) continue
                val author = resolveReplyAuthor(p)
                if (author.isBlank()) continue
                val key = author + "||" + text.length + "||" + text
                if (!seen.add(key)) continue
                comments.add(
                    FetchedComment(
                        externalId = "sjs_c_fb_$idx",
                        author = author,
                        text = text,
                        likeCount = p.optInt("like_count", 0),
                        media = postMedia(p)
                    )
                )
                idx++
            }
        }

        // /share/ 留言鏈母文回溯：主本身 is_reply==true 時（D-JnnrknO/DzVwcgDJw 實測），
        // 同頁 JSON 內含母文（is_reply==false、作者==主的 reply_to/root、時間早於主）。
        // 例：DdwK5xuk6Uk 的母為 Ddvrtejj15a（下一位勇者+圖片）；
        // Dd1Ry_jlKfT 的母為 Dd0sfd4CCLT（他故意输给...+影片）。
        // 「留言的留言」（主回覆 B、B 回覆 A）則沿 reply_to 逐層上溯，得到完整鏈 [A, B]，
        // 存檔時編碼成多段 --- 分享 @... ---，詳情頁逐塊渲染，不再擠成 3 層亂文。
        // 注意：若 effectiveMain 已回溯到根貼文，parentChain 應從 effectiveMain 重新計算，
        // 否則會把根貼文的母文（不存在）或原留言的母文（根貼文自己）錯誤納入。
        val parentChain = if (effectiveMain !== main) {
            emptyList()  // 已回溯到根貼文，無母文鏈
        } else {
            findParentChain(main, allPosts, shortcode)
        }
        val parent = parentChain.firstOrNull()

        return SjsResult(
            bodyText = effBody,
            authorHandle = effAuthorHandle,
            authorDisplayName = effAuthorHandle,
            authorProfileUrl = effAuthorProfileUrl,
            authorVerified = effAuthorVerified,
            postedAtMs = if (effTakenAtSec > 0) effTakenAtSec * 1000 else 0L,
            likeCount = effLikeCount,
            media = effMedia,
            comments = comments,
            parent = parent,
            parentChain = parentChain
        )
    }

    /**
     * 母文鏈回溯：從主貼沿 reply_to 逐層上溯（root 先、直接父層後）。
     * 條件：code!=當前、作者命中 reply_to（直接父層，本身可為回覆）或 root（頂層）、
     * taken_at 早於當前、有內文或媒體；同分取時間最接近當前者（直接父層）。
     * 上限 5 層，避免髒資料循環拖慢。
     */
    private fun findParentChain(
        main: JSONObject,
        allPosts: List<JSONObject>,
        shortcode: String
    ): List<ParentPost> {
        val chain = mutableListOf<ParentPost>()
        var current = main
        var currentCode = shortcode
        val visited = HashSet<String>()
        visited.add(shortcode)
        for (depth in 0 until 5) {
            val parentJson = findDirectParentJson(current, allPosts, currentCode) ?: break
            val code = parentJson.optString("code")
            if (code.isBlank() || !visited.add(code)) break
            chain.add(0, toParentPost(parentJson))
            current = parentJson
            currentCode = code
        }
        return chain
    }

    /**
     * 找當前貼文的直接父層（reply_to 命中優先，含本身是回覆的中間層；
     * 找不到才退回 root 頂層，維持舊版單層行為）。
     */
    private fun findDirectParentJson(
        current: JSONObject,
        allPosts: List<JSONObject>,
        currentCode: String
    ): JSONObject? {
        val tpa = current.optJSONObject("text_post_app_info") ?: return null
        val replyToUser = tpa.optJSONObject("reply_to_author")?.optString("username").orEmpty()
        val rootUser = tpa.optJSONObject("root_post_author")?.optString("username").orEmpty()
        var rootPk = tpa.optJSONObject("root_post_author")?.optString("pk").orEmpty()
        if (rootPk.isBlank()) rootPk = tpa.optJSONObject("root_post_author")?.optString("id").orEmpty()
        val isReply = tpa.optBoolean("is_reply", false)
        if (!isReply && replyToUser.isBlank() && rootUser.isBlank() && rootPk.isBlank()) return null
        val currentTaken = current.optLong("taken_at", 0L)
        var best: JSONObject? = null
        var bestScore = -1
        var bestTaken = -1L
        val seenCode = HashSet<String>()
        for (p in allPosts) {
            if (p === current) continue
            val code = p.optString("code")
            if (code.isBlank() || code == currentCode || !seenCode.add(code)) continue
            val pu = p.optJSONObject("user") ?: continue
            val pauthor = pu.optString("username").orEmpty()
            if (pauthor.isBlank()) continue
            val ppk = pu.optString("pk").orEmpty().ifBlank { pu.optString("id").orEmpty() }
            val ptpa = p.optJSONObject("text_post_app_info")
            val pIsTop = ptpa == null || !ptpa.optBoolean("is_reply", false)
            // 歸屬：直接回覆對象（中間層）優先，其次 root 頂層
            val directHit = replyToUser.isNotBlank() && pauthor.equals(replyToUser, ignoreCase = true)
            val rootHit = (rootUser.isNotBlank() && pauthor.equals(rootUser, ignoreCase = true)) ||
                (rootPk.isNotBlank() && ppk.isNotBlank() && ppk == rootPk)
            if (!directHit && !rootHit) continue
            val ptaken = p.optLong("taken_at", 0L)
            // 時間須早於當前（容差 5 秒）
            if (currentTaken > 0 && ptaken > 0 && ptaken + 5 >= currentTaken) continue
            val ptext = postText(p)
            val hasMedia = try {
                postMedia(p).isNotEmpty()
            } catch (_: Exception) {
                false
            }
            if (ptext.isBlank() && !hasMedia) continue
            var score = if (directHit) 2 else 0
            if (pIsTop) score += 1
            if (score > bestScore || (score == bestScore && ptaken > bestTaken)) {
                best = p
                bestScore = score
                bestTaken = ptaken
            }
        }
        return best
    }

    private fun toParentPost(p: JSONObject): ParentPost {
        val u = p.optJSONObject("user")
        return ParentPost(
            shortcode = p.optString("code"),
            authorHandle = u?.optString("username").orEmpty(),
            bodyText = postText(p),
            likeCount = p.optInt("like_count", 0),
            postedAtMs = p.optLong("taken_at", 0L).let { if (it > 0) it * 1000 else 0L },
            media = try {
                postMedia(p)
            } catch (_: Exception) {
                emptyList()
            }
        )
    }

    /** 收集同頁所有含 code 的 post 物件（供母文回溯；上限 500 避免側欄污染拖慢）。 */
    private fun collectAllPosts(node: Any?, out: MutableList<JSONObject>, depth: Int) {
        if (node == null || depth > MAX_DEPTH || out.size > 500) return
        when (node) {
            is JSONObject -> {
                val code = node.optString("code")
                if (code.isNotBlank() && (node.optJSONObject("caption") != null || node.optJSONObject("user") != null)) {
                    if (out.none { it === node }) out.add(node)
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    collectAllPosts(node.opt(keys.next()), out, depth + 1)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectAllPosts(node.opt(i), out, depth + 1)
                }
            }
        }
    }

    // ---------- 遞迴收集 ----------

    /**
     * 新 shape 收集：
     *  - 主貼候選：任何含 code==shortcode 的 post 物件（data.media 下的主貼）
     *  - 留言：direct_replies.edges[].node.posts.edges[].node（完整 post 物件）
     */
    private fun collectNewShape(
        node: Any?,
        shortcode: String,
        mains: MutableList<JSONObject>,
        replies: MutableList<JSONObject>,
        seenReplyPk: HashSet<String>,
        depth: Int
    ) {
        if (node == null || depth > MAX_DEPTH) return
        when (node) {
            is JSONObject -> {
                val dr = node.optJSONObject("direct_replies")
                if (dr != null) collectReplyEdges(dr, replies, seenReplyPk, depth)
                if (node.optString("code") == shortcode && mains.none { it === node }) {
                    mains.add(node)
                }
                // 已是留言節點：其子層多為 caption/media 細節，不必再深挖別的 direct_replies
                val keys = node.keys()
                while (keys.hasNext()) {
                    collectNewShape(node.opt(keys.next()), shortcode, mains, replies, seenReplyPk, depth + 1)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectNewShape(node.opt(i), shortcode, mains, replies, seenReplyPk, depth + 1)
                }
            }
        }
    }

    /** direct_replies.edges[].node.posts.edges[].node → 每則留言的完整 post 物件 */
    private fun collectReplyEdges(
        directReplies: JSONObject,
        replies: MutableList<JSONObject>,
        seenPk: HashSet<String>,
        depth: Int
    ) {
        if (depth > MAX_DEPTH) return
        val edges = directReplies.optJSONArray("edges") ?: return
        for (i in 0 until edges.length()) {
            val node = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
            val posts = node.optJSONObject("posts")?.optJSONArray("edges") ?: continue
            for (j in 0 until posts.length()) {
                val post = posts.optJSONObject(j)?.optJSONObject("node") ?: continue
                // 依 pk/id 去重（同留言可能在多塊出現）
                val key = post.optString("pk").ifBlank { post.optString("id") }
                if (key.isNotBlank() && !seenPk.add(key)) continue
                if (post.optString("code").isBlank() && post.optJSONObject("caption") == null &&
                    post.optJSONObject("user") == null
                ) continue
                replies.add(post)
            }
        }
    }

    /** 主貼候選完整度評分（同一 post 可能出現多個 stub 塊，取資訊最完整者） */
    private fun mainScore(p: JSONObject): Int {
        var s = 0
        if (p.optJSONObject("user") != null) s += 8
        if (p.optJSONObject("caption") != null) s += 4
        if (p.has("taken_at")) s += 2
        if (p.has("image_versions2") || p.has("video_versions") || p.has("carousel_media")) s += 2
        if (p.optJSONObject("text_post_app_info")?.has("direct_reply_count") == true) s += 2
        s += postText(p).length / 100
        return s
    }

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
    private fun belongsToThread(
        post: JSONObject,
        mainAuthor: String,
        mainPk: String,
        mainTakenAt: Long = 0L,
        mainAuthorPk: String = ""
    ): Boolean {
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
        // 巢狀回覆：root 指向主串 post pk
        if (rootPk.isNotBlank() && mainPk.isNotBlank() && rootPk == mainPk) return true
        // 新 shape：root_post_author 存的是主串「作者」的 user pk → 比對主作者 pk
        if (rootPk.isNotBlank() && mainAuthorPk.isNotBlank() && rootPk == mainAuthorPk) return true
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
