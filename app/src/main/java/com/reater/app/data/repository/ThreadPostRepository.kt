package com.reater.app.data.repository

import androidx.room3.withWriteTransaction
import com.reater.app.data.local.AppDatabase
import com.reater.app.data.local.dao.CategoryDao
import com.reater.app.data.local.dao.AiRunDao
import com.reater.app.data.local.dao.CommentDao
import com.reater.app.data.local.dao.ItemDao
import com.reater.app.data.local.dao.MediaDao
import com.reater.app.data.local.dao.TagDao
import com.reater.app.data.local.entity.CommentEntity
import com.reater.app.data.local.entity.AiRunEntity
import com.reater.app.data.local.entity.ItemDetail
import com.reater.app.data.local.entity.ItemEntity
import com.reater.app.data.local.entity.MediaEntity
import com.reater.app.data.local.entity.ItemTagCrossRef
import com.reater.app.data.local.entity.TagEntity
import com.reater.app.data.local.entity.UserEditEntity
import com.reater.app.data.remote.FetchedPostResult
import com.reater.app.data.remote.AiAnalysisResult
import com.reater.app.data.remote.OpenAiUsage
import com.reater.app.data.remote.ThreadsGraphQLClient
import com.reater.app.data.remote.threads.TopicTags
import com.reater.app.domain.OnDeviceClassifier
import com.reater.app.domain.UrlParser
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreadPostRepository @Inject constructor(
    private val database: AppDatabase,
    private val itemDao: ItemDao,
    private val commentDao: CommentDao,
    private val mediaDao: MediaDao,
    private val tagDao: TagDao,
    private val categoryDao: CategoryDao,
    private val aiRunDao: AiRunDao,
    private val graphQLClient: ThreadsGraphQLClient,
    private val classifier: OnDeviceClassifier,
    private val settingsRepository: SettingsRepository
) {

    fun observeAllPosts(): Flow<List<ItemDetail>> = itemDao.observeAllItemDetails()
    fun observeUnreadPosts(): Flow<List<ItemDetail>> = itemDao.observeUnreadItemDetails()
    fun observeFavoritePosts(): Flow<List<ItemDetail>> = itemDao.observeFavoriteItemDetails()
    fun searchPosts(query: String): Flow<List<ItemDetail>> {
        val clean = query.trim()
        if (clean.codePointCount(0, clean.length) < 3) return itemDao.searchItemDetails(clean)
        val quoted = "\"${clean.replace("\"", "\"\"")}\""
        return itemDao.searchItemDetailsFts(quoted)
    }
    fun observePostDetail(id: Long): Flow<ItemDetail?> = itemDao.observeItemDetailById(id)

    suspend fun savePost(
        canonicalUrl: String,
        shortcode: String,
        authorHandle: String,
        bodyText: String,
        commentsText: String,
        manualNote: String,
        manualSummary: String,
        categoryId: Long?,
        fetchedResult: FetchedPostResult? = null,
        isFavorite: Boolean = false
    ): Long = database.withWriteTransaction {
        // 子串分享（母文存在時）：免 DB 遷移，把母文前綴進 body、母文媒體排前面合併存檔。
        // 例 D-J：【母文 @yw202087】下一位勇者… + 分享 @l.m.sheng_1024 永遠空租吧；媒體 = 母圖 + 子（子無圖則只有母圖）。
        // 「留言的留言」為多層鏈：【母文 @A】a + 分享 @B b + 分享 @主 c（詳情頁逐塊渲染）。
        val combinedBody: String = if (
            fetchedResult != null &&
            (
                (fetchedResult.parentChain.isNotEmpty() &&
                    fetchedResult.parentChain.first().shortcode.isNotBlank() &&
                    fetchedResult.parentChain.first().bodyText.isNotBlank()) ||
                    (fetchedResult.parentShortcode.isNotBlank() &&
                        fetchedResult.parentBodyText.isNotBlank())
                )
        ) {
            val childBody = fetchedResult.bodyText.ifBlank { bodyText }
            // ViewModel 預覽已合併過則不再重複前綴
            if (childBody.startsWith("【母文 @")) childBody
            else if (fetchedResult.parentChain.isNotEmpty()) {
                val sb = StringBuilder()
                val root = fetchedResult.parentChain.first()
                sb.append("【母文 @${root.authorHandle}】${root.bodyText}")
                for (mid in fetchedResult.parentChain.drop(1)) {
                    sb.append("\n\n--- 分享 @${mid.authorHandle} ---\n${mid.bodyText}")
                }
                sb.append("\n\n--- 分享 @${fetchedResult.authorHandle} ---\n$childBody")
                sb.toString()
            }
            else "【母文 @${fetchedResult.parentAuthorHandle}】${fetchedResult.parentBodyText}\n\n--- 分享 @${fetchedResult.authorHandle} ---\n$childBody"
        } else {
            fetchedResult?.bodyText?.takeIf { it.isNotBlank() } ?: bodyText
        }
        val combinedMedia = if (
            fetchedResult != null && (fetchedResult.parentMedia.isNotEmpty() || fetchedResult.parentChain.any { it.media.isNotEmpty() })
        ) {
            // 祖先鏈媒體（root 在前）排前面，免遷移合併存檔；
            // 同一內容不同清晰度 URL 只留首份，避免雙份影片/圖片
            val chainMedia = fetchedResult.parentChain.flatMap { it.media }
            com.reater.app.data.remote.MediaDedup.distinctFetched(
                (chainMedia + fetchedResult.parentMedia) + fetchedResult.media
            )
        } else {
            fetchedResult?.media.orEmpty()
        }
        // 留言鏈各區塊媒體歸屬（免 DB 遷移，編碼進 rawJsonMin；詳情頁逐塊渲染用）。
        // 圖3/圖4 錯亂根因：舊版合併後全部掛在子文底下，母文圖（銀晝戰績/裝備/排行）
        // 全跑到「@1yuunu2 黃色盾牌」留言下。此處記錄 chainMedia/mainMedia 的 remoteUrl，
        // 詳情頁用 remoteUrl 反查 MediaEntity（取 localPath）逐塊顯示。
        val resolvedRawJson: String = if (fetchedResult != null &&
            (fetchedResult.parentChain.isNotEmpty() || fetchedResult.parentMedia.isNotEmpty())
        ) {
            buildChainRawJson(fetchedResult)
        } else {
            fetchedResult?.rawJsonMin.orEmpty()
        }
        // Upsert ItemEntity
        val existing = itemDao.getItemByCanonicalUrl(canonicalUrl)
        // 主題標籤：抓取結果優先，否則退取內文首個 hashtag（抓取失敗時仍可累積同主題紀錄）
        val resolvedTopic = fetchedResult?.topicTag?.trim().orEmpty()
            .ifBlank { TopicTags.firstHashtag(combinedBody) }
        val itemId = if (existing != null) {
            val updated = existing.copy(
                shortcode = shortcode,
                authorHandle = authorHandle.ifBlank { existing.authorHandle },
                topicTag = resolvedTopic.ifBlank { existing.topicTag },
                authorDisplayName = fetchedResult?.authorDisplayName ?: existing.authorDisplayName,
                authorProfileUrl = fetchedResult?.authorProfileUrl ?: existing.authorProfileUrl,
                authorVerified = fetchedResult?.authorVerified ?: existing.authorVerified,
                postedAt = fetchedResult?.postedAt?.takeIf { it > 0 } ?: existing.postedAt,
                bodyText = combinedBody.ifBlank { existing.bodyText },
                commentsText = commentsText.ifBlank { existing.commentsText },
                likeCount = fetchedResult?.likeCount ?: existing.likeCount,
                replyCount = fetchedResult?.replyCount ?: existing.replyCount,
                repostCount = fetchedResult?.repostCount ?: existing.repostCount,
                rawJsonMin = resolvedRawJson.ifBlank { existing.rawJsonMin },
                sourceVersion = existing.sourceVersion + 1,
                lastFetchStatus = fetchedResult?.status ?: existing.lastFetchStatus,
                lastFetchAt = System.currentTimeMillis()
            )
            itemDao.updateItem(updated)
            existing.id
        } else {
            val newItem = ItemEntity(
                canonicalUrl = canonicalUrl,
                shortcode = shortcode,
                authorHandle = authorHandle,
                topicTag = resolvedTopic,
                authorDisplayName = fetchedResult?.authorDisplayName ?: authorHandle,
                authorProfileUrl = fetchedResult?.authorProfileUrl.orEmpty(),
                authorVerified = fetchedResult?.authorVerified ?: false,
                postedAt = fetchedResult?.postedAt ?: System.currentTimeMillis(),
                bodyText = combinedBody.ifBlank { bodyText },
                commentsText = commentsText,
                likeCount = fetchedResult?.likeCount ?: 0,
                replyCount = fetchedResult?.replyCount ?: 0,
                repostCount = fetchedResult?.repostCount ?: 0,
                lastFetchStatus = fetchedResult?.status ?: "COMPLETE",
                rawJsonMin = resolvedRawJson
            )
            itemDao.insertItem(newItem)
        }

        // Auto-classification if categoryId is null
        val resolvedCategoryId = categoryId ?: run {
            val classResult = classifier.classify(
                titleScope = authorHandle + " " + bodyText.take(60),
                bodyScope = bodyText + " " + commentsText
            )
            classResult.categoryId
        }

        // Upsert UserEditEntity
        val existingEdit = itemDao.getUserEditByItemId(itemId)
        val userEdit = existingEdit?.copy(
            userBodyOverride = if (fetchedResult != null && bodyText != combinedBody) bodyText else existingEdit.userBodyOverride,
            manualNote = manualNote.ifBlank { existingEdit.manualNote },
            manualSummary = manualSummary.ifBlank { existingEdit.manualSummary },
            categoryId = resolvedCategoryId ?: existingEdit.categoryId,
            isFavorite = if (isFavorite) true else existingEdit.isFavorite,
            editedAt = System.currentTimeMillis()
        ) ?: UserEditEntity(
            itemId = itemId,
            userBodyOverride = fetchedResult?.let { if (bodyText != combinedBody) bodyText else null },
            manualNote = manualNote,
            manualSummary = manualSummary,
            categoryId = resolvedCategoryId,
            isRead = false,
            isFavorite = isFavorite
        )
        itemDao.insertUserEdit(userEdit)

        // Insert fetched structured comments；若抓取無留言但使用者手貼了留言文字，        // 把手貼文字結構化入庫（支援「作者: 內容」或「@作者 內容」開頭，否則作者記為手動筆記），
        // 否則「貼上留言」存了卻不顯示、計數也不對。
        val manualComments = if ((fetchedResult == null || fetchedResult.comments.isEmpty()) &&
            commentsText.isNotBlank()
        ) {
            parseManualComments(commentsText)
        } else {
            emptyList()
        }
        if (fetchedResult != null && (fetchedResult.comments.isNotEmpty() || manualComments.isNotEmpty() || fetchedResult.status == "COMPLETE")) {
            commentDao.deleteCommentsByItemId(itemId)
            val fetchedEntities = fetchedResult.comments.mapIndexed { index, c ->
                CommentEntity(
                    itemId = itemId,
                    externalId = c.externalId.ifBlank { "c_$index" },
                    author = c.author,
                    text = c.text,
                    likeCount = c.likeCount,
                    parentExternalId = c.parentExternalId,
                    depth = c.depth,
                    sortKey = "0:${System.currentTimeMillis()}:$index",
                    mediaJson = com.reater.app.data.remote.FetchedMediaJson.encode(c.media)
                )
            }
            val manualEntities = manualComments.mapIndexed { index, (author, text) ->
                CommentEntity(
                    itemId = itemId,
                    externalId = "manual_$index",
                    author = author,
                    text = text,
                    likeCount = 0,
                    parentExternalId = null,
                    depth = 0,
                    sortKey = "1:${System.currentTimeMillis()}:$index"
                )
            }
            commentDao.insertComments(fetchedEntities + manualEntities)
        }

        // Insert media attachments（母文媒體排前面，免遷移合併存檔）
        if (fetchedResult != null) {
            mediaDao.deleteMediaByItemId(itemId)
            val mediaEntities = combinedMedia.mapIndexed { index, m ->
                MediaEntity(
                    itemId = itemId,
                    kind = m.kind,
                    remoteUrl = m.remoteUrl,
                    localPath = m.localPath,
                    width = m.width,
                    height = m.height,
                    position = index
                )
            }
            if (mediaEntities.isNotEmpty()) mediaDao.insertMediaList(mediaEntities)
        }

        refreshSearchIndex(itemId)

        itemId
    }

    /**
     * 留言鏈媒體歸屬編碼（免 DB 遷移）：把每層祖先 + 主文各自的 remoteUrl 存進 rawJsonMin。
     * 詳情頁用 remoteUrl 反查 MediaEntity（取 localPath）逐塊渲染，避免母文圖掛到子文下。
     */
    private fun buildChainRawJson(fetched: com.reater.app.data.remote.FetchedPostResult): String {
        return try {
            val root = org.json.JSONObject()
            root.put("code", fetched.shortcode)
            val chainArr = org.json.JSONArray()
            for (pp in fetched.parentChain) {
                val o = org.json.JSONObject()
                o.put("author", pp.authorHandle)
                val urls = org.json.JSONArray()
                for (m in pp.media) urls.put(m.remoteUrl)
                o.put("media", urls)
                chainArr.put(o)
            }
            // 舊單層 parent（無 chain 時）也保留一層，避免遺失
            if (chainArr.length() == 0 && fetched.parentMedia.isNotEmpty()) {
                val o = org.json.JSONObject()
                o.put("author", fetched.parentAuthorHandle)
                val urls = org.json.JSONArray()
                for (m in fetched.parentMedia) urls.put(m.remoteUrl)
                o.put("media", urls)
                chainArr.put(o)
            }
            root.put("chainMedia", chainArr)
            val mainArr = org.json.JSONArray()
            for (m in fetched.media) mainArr.put(m.remoteUrl)
            root.put("mainMedia", mainArr)
            // 保留原始 code 供除錯
            if (fetched.rawJsonMin.isNotBlank() && fetched.rawJsonMin.trimStart().startsWith("{")) {
                root.put("orig", fetched.rawJsonMin.take(200))
            }
            root.toString()
        } catch (_: Exception) {
            fetched.rawJsonMin.orEmpty()
        }
    }

    /**
     * 解析手貼留言文字為 (author, text) 列表。
     * 切分規則：先按「---」分隔線分則，再按行；單行支援「作者: 內容」/「@作者 內容」
     * 開頭，無前綴則作者記為「手動筆記」。上限 50 則，與抓取側一致。
     */
    private fun parseManualComments(commentsText: String): List<Pair<String, String>> {
        val chunks = commentsText.split(Regex("\\n\\s*---\\s*\\n"))
            .flatMap { it.lines() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val out = mutableListOf<Pair<String, String>>()
        for (raw in chunks) {
            if (out.size >= 50) break
            // 「作者: 內容」（半形/全形冒號）
            val colon = Regex("^(@?[\\w.\\u4e00-\\u9fa5_-]{1,30})\\s*[:：]\\s*(.+)$", RegexOption.DOT_MATCHES_ALL)
                .find(raw)
            if (colon != null) {
                out.add(colon.groupValues[1].trimStart('@') to colon.groupValues[2].trim())
                continue
            }
            // 「@作者 內容」
            val at = Regex("^@([\\w.\\u4e00-\\u9fa5_-]{1,30})\\s+(.+)$", RegexOption.DOT_MATCHES_ALL).find(raw)
            if (at != null) {
                out.add(at.groupValues[1] to at.groupValues[2].trim())
                continue
            }
            out.add("手動筆記" to raw)
        }
        return out.filter { it.second.isNotBlank() }
    }

    suspend fun toggleReadStatus(itemId: Long, isRead: Boolean) = database.withWriteTransaction {
        val current = itemDao.getUserEditByItemId(itemId)
        if (current != null) {
            itemDao.updateReadStatus(itemId, isRead)
        } else {
            itemDao.insertUserEdit(UserEditEntity(itemId = itemId, isRead = isRead))
        }
    }

    suspend fun toggleFavoriteStatus(itemId: Long, isFavorite: Boolean) = database.withWriteTransaction {
        val current = itemDao.getUserEditByItemId(itemId)
        if (current != null) {
            itemDao.updateFavoriteStatus(itemId, isFavorite)
        } else {
            itemDao.insertUserEdit(UserEditEntity(itemId = itemId, isFavorite = isFavorite))
        }
    }

    /** 詳情頁改分類：保留既有 UserEdit，僅更新 categoryId */
    suspend fun updateCategory(itemId: Long, categoryId: Long?) = database.withWriteTransaction {
        val current = itemDao.getUserEditByItemId(itemId)
        if (current != null) {
            itemDao.updateCategory(itemId, categoryId)
        } else {
            itemDao.insertUserEdit(UserEditEntity(itemId = itemId, categoryId = categoryId))
        }
        refreshSearchIndex(itemId)
    }

    /** 詳情開啟：開啟次數 +1（分析頁統計用；無列時先建列） */
    suspend fun recordOpen(itemId: Long) = database.withWriteTransaction {
        val current = itemDao.getUserEditByItemId(itemId)
        if (current != null) {
            itemDao.recordOpen(itemId)
        } else {
            itemDao.insertUserEdit(
                UserEditEntity(itemId = itemId, openCount = 1, lastOpenedAt = System.currentTimeMillis())
            )
        }
    }

    data class AnalyticsSnapshot(
        val savedToday: Int,
        val openedToday: Int,
        val totalReviews: Int,
        /** 日期 → 當日儲存數（已按 since 裁切：免費當週、Pro 近30日） */
        val dailySaved: List<Pair<String, Int>>,
        /** 小時 (0..23) → 當小時儲存數 */
        val hourlySaved: List<Pair<Int, Int>> = emptyList()
    )

    suspend fun getAnalytics(since: Long, todayStart: Long): AnalyticsSnapshot {
        val hourly = itemDao.hourlySavedSince(since).mapNotNull {
            val h = it.hour.toIntOrNull() ?: return@mapNotNull null
            h to it.cnt
        }
        return AnalyticsSnapshot(
            savedToday = itemDao.countSavedSince(todayStart),
            openedToday = itemDao.countOpenedSince(todayStart),
            totalReviews = itemDao.countTotalReviews(),
            dailySaved = itemDao.dailySavedSince(since).map { it.day to it.cnt },
            hourlySaved = hourly
        )
    }

    suspend fun countUnreadSince(since: Long): Int = itemDao.countUnreadSince(since)

    fun observeTrashPosts(): Flow<List<ItemDetail>> = itemDao.observeTrashItemDetails()

    suspend fun moveToTrash(itemId: Long) {
        itemDao.moveToTrash(itemId)
    }

    suspend fun restoreFromTrash(itemId: Long) {
        itemDao.restoreFromTrash(itemId)
    }

    suspend fun emptyTrash() {
        itemDao.emptyTrash()
    }

    suspend fun purgeExpiredTrash() {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        itemDao.purgeExpiredTrash(thirtyDaysAgo)
    }

    suspend fun deletePost(itemId: Long) {
        itemDao.deleteItemById(itemId)
        itemDao.deleteSearchIndex(itemId)
    }

    suspend fun persistAiAnalysis(
        itemId: Long,
        analysis: AiAnalysisResult,
        usage: OpenAiUsage,
        model: String,
        inputHash: String,
        promptVersion: String
    ) = database.withWriteTransaction {
        val categoryId = categoryDao.getCategoryByName(analysis.category)?.id
        val current = itemDao.getUserEditByItemId(itemId) ?: UserEditEntity(itemId = itemId)
        itemDao.insertUserEdit(
            current.copy(
                manualSummary = analysis.summary,
                categoryId = categoryId ?: current.categoryId,
                editedAt = System.currentTimeMillis(),
                editSource = "AI"
            )
        )
        tagDao.clearTagsForItem(itemId)
        analysis.tags.map(String::trim).filter(String::isNotEmpty).distinctBy { it.lowercase() }.forEach { name ->
            val tagId = tagDao.getTagByName(name)?.id ?: tagDao.insertTag(TagEntity(name = name)).let { inserted ->
                if (inserted >= 0) inserted else tagDao.getTagByName(name)?.id
            }
            if (tagId != null && tagId > 0) tagDao.insertItemTagCrossRef(ItemTagCrossRef(itemId, tagId))
        }
        val encoded = kotlinx.serialization.json.Json.encodeToString(AiAnalysisResult.serializer(), analysis)
        aiRunDao.insert(
            AiRunEntity(
                itemId = itemId,
                purpose = "CLASSIFY_AND_SUMMARIZE",
                model = model,
                promptVersion = promptVersion,
                inputHash = inputHash,
                outputJson = encoded,
                usageIn = usage.prompt_tokens,
                usageOut = usage.completion_tokens
            )
        )
        refreshSearchIndex(itemId)
    }

    suspend fun refreshSearchIndex(itemId: Long) {
        val detail = itemDao.getItemDetailById(itemId) ?: return
        val tags = detail.tags.joinToString(" ") { it.name }
        val comments = detail.comments.joinToString(" ") { it.text }
        val searchable = listOf(
            detail.item.bodyText,
            detail.item.topicTag,
            detail.item.commentsText,
            detail.userEdit?.userBodyOverride.orEmpty(),
            detail.manualNote,
            detail.manualSummary,
            detail.item.authorHandle,
            detail.item.authorDisplayName,
            tags,
            comments
        ).filter(String::isNotBlank).joinToString(" ")
        itemDao.updateSearchIndex(itemId, searchable)
    }
}
