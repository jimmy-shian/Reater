package com.reater.app.domain

import android.content.Context
import androidx.annotation.StringRes
import com.reater.app.R
import com.reater.app.data.local.dao.CategoryDao
import com.reater.app.data.local.dao.ItemDao
import com.reater.app.data.local.dao.ProDao
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.data.local.entity.ItemDetail
import com.reater.app.data.local.entity.KeywordEntity
import com.reater.app.data.local.entity.SavedCollectionEntity
import com.reater.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
enum class RuleField(@StringRes val labelRes: Int, @StringRes val groupRes: Int) {
    // 內容
    AUTHOR(R.string.smart_field_author, R.string.smart_collection_field_group_content),
    TAG(R.string.smart_field_tag, R.string.smart_collection_field_group_content),
    CATEGORY(R.string.smart_field_category, R.string.smart_collection_field_group_content),
    CONTENT(R.string.smart_field_content, R.string.smart_collection_field_group_content),
    COMMENT_TEXT(R.string.smart_field_comment_text, R.string.smart_collection_field_group_content),
    AI_SUMMARY(R.string.smart_field_ai_summary, R.string.smart_collection_field_group_content),
    MANUAL_NOTE(R.string.smart_field_manual_note, R.string.smart_collection_field_group_content),

    // 狀態
    FAVORITE(R.string.smart_field_favorite, R.string.smart_collection_field_group_status),
    READ_STATUS(R.string.smart_field_read_status, R.string.smart_collection_field_group_status),
    HAS_MEDIA(R.string.smart_field_has_media, R.string.smart_collection_field_group_status),
    HAS_IMAGE(R.string.smart_field_has_image, R.string.smart_collection_field_group_status),
    HAS_VIDEO(R.string.smart_field_has_video, R.string.smart_collection_field_group_status),

    // 互動數據
    LIKES(R.string.smart_field_likes, R.string.smart_collection_field_group_interaction),
    REPLIES(R.string.smart_field_replies, R.string.smart_collection_field_group_interaction),
    REPOSTS(R.string.smart_field_reposts, R.string.smart_collection_field_group_interaction),
    SHARES(R.string.smart_field_shares, R.string.smart_collection_field_group_interaction),

    // 時間
    SAVED_DATE(R.string.smart_field_saved_date, R.string.smart_collection_field_group_time),

    // 智慧分類
    CONTENT_MATCH_CATEGORY_KEYWORDS(R.string.smart_field_content_match_keywords, R.string.smart_collection_field_group_smart),
    COMMENT_MATCH_CATEGORY_KEYWORDS(R.string.smart_field_comment_match_keywords, R.string.smart_collection_field_group_smart)
}

@Serializable
enum class ComparisonOperator(val symbol: String, @StringRes val labelRes: Int) {
    EQUALS("=", R.string.smart_op_label_equals),
    NOT_EQUALS("≠", R.string.smart_op_label_not_equals),
    CONTAINS("包含", R.string.smart_op_contains),
    NOT_CONTAINS("不包含", R.string.smart_op_not_contains),
    CONTAINS_ANY("包含任一", R.string.smart_op_contains_any),
    CONTAINS_ALL("包含所有", R.string.smart_op_contains_all),
    GREATER_EQUAL("≥", R.string.smart_op_label_gte),
    GREATER_THAN(">", R.string.smart_op_label_gt),
    LESS_EQUAL("≤", R.string.smart_op_label_lte),
    LESS_THAN("<", R.string.smart_op_label_lt),
    BETWEEN("介於", R.string.smart_op_between),
    WITHIN_DAYS("最近", R.string.smart_op_label_within_days),
    OLDER_THAN_DAYS("早於", R.string.smart_op_label_older_than_days),
    NEWER_THAN_DAYS("晚於", R.string.smart_op_label_newer_than_days)
}

@Serializable
data class RuleConditionConfig(
    val categoryId: Long? = null,
    val categoryName: String = "",
    val includeTitle: Boolean = true,
    val includeBody: Boolean = true,
    val includeComments: Boolean = false,
    val includeAiSummary: Boolean = false,
    val includeManualNote: Boolean = false,
    val minValue: Int? = null,
    val maxValue: Int? = null
)

@Serializable
data class RuleCriterion(
    val field: RuleField = RuleField.LIKES,
    val operator: ComparisonOperator = ComparisonOperator.GREATER_EQUAL,
    val value: String = "",
    val config: RuleConditionConfig? = null
)

@Serializable
data class CollectionRule(
    val enabled: Boolean = true,
    val matchMode: String = "AND", // "AND" 或 "OR"
    val criteria: List<RuleCriterion> = emptyList(),
    // 保留舊版欄位以支援舊資料相容
    val categoryId: Long? = null,
    val isRead: Boolean? = null,
    val isFavorite: Boolean? = null,
    val minLikes: Int? = null,
    val tag: String? = null,
    val author: String? = null,
    val hasMedia: Boolean? = null
) {
    /**
     * 檢查此規則是否具備實質生效的過濾條件。
     */
    fun hasActiveConditions(): Boolean {
        if (criteria.any { it.value.isNotBlank() || it.config?.categoryId != null || it.config?.categoryName?.isNotBlank() == true }) return true
        return categoryId != null || isRead != null || isFavorite != null ||
            minLikes != null || !tag.isNullOrBlank() || !author.isNullOrBlank() || hasMedia != null
    }

    /**
     * 將舊版規則欄位轉換為結構化 criteria 條件清單。
     */
    fun normalizedCriteria(): List<RuleCriterion> {
        if (criteria.isNotEmpty()) return criteria
        val list = mutableListOf<RuleCriterion>()
        if (minLikes != null) {
            list.add(RuleCriterion(RuleField.LIKES, ComparisonOperator.GREATER_EQUAL, minLikes.toString()))
        }
        if (author != null && author.isNotBlank()) {
            list.add(RuleCriterion(RuleField.AUTHOR, ComparisonOperator.CONTAINS, author))
        }
        if (tag != null && tag.isNotBlank()) {
            list.add(RuleCriterion(RuleField.TAG, ComparisonOperator.CONTAINS, tag))
        }
        if (isFavorite != null) {
            list.add(RuleCriterion(RuleField.FAVORITE, ComparisonOperator.EQUALS, if (isFavorite) "true" else "false"))
        }
        if (isRead != null) {
            list.add(RuleCriterion(RuleField.READ_STATUS, ComparisonOperator.EQUALS, if (isRead) "已讀" else "未讀"))
        }
        if (hasMedia != null) {
            list.add(RuleCriterion(RuleField.HAS_MEDIA, ComparisonOperator.EQUALS, if (hasMedia) "true" else "false"))
        }
        if (categoryId != null) {
            list.add(RuleCriterion(RuleField.CATEGORY, ComparisonOperator.EQUALS, categoryId.toString()))
        }
        return list
    }
}

@Singleton
class SmartCollectionEngine @Inject constructor(
    private val itemDao: ItemDao,
    private val proDao: ProDao,
    private val categoryDao: CategoryDao,
    private val settingsRepository: SettingsRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun evaluateCollection(rulesJson: String, isEnabled: Boolean = true): List<ItemDetail> {
        if (!isEnabled) return emptyList()
        if (!settingsRepository.isProUnlocked.first()) return emptyList()
        val rule = parseRule(rulesJson) ?: return emptyList()
        if (!rule.enabled || !rule.hasActiveConditions()) return emptyList()

        val all = itemDao.observeAllItemDetails().first()
        val categories = categoryDao.getAllCategories()
        val keywords = categoryDao.getAllKeywords()
        return all.filter { item -> matchesItem(rule, item, categories, keywords) }
    }

    fun parseRule(rulesJson: String): CollectionRule? = runCatching {
        json.decodeFromString(CollectionRule.serializer(), rulesJson)
    }.getOrNull()

    suspend fun updateRule(collection: SavedCollectionEntity, rule: CollectionRule) {
        if (!settingsRepository.isProUnlocked.first()) return
        proDao.insertCollection(
            collection.copy(
                isEnabled = rule.enabled,
                rulesJson = json.encodeToString(CollectionRule.serializer(), rule)
            )
        )
    }

    suspend fun seedDefaultProCollectionsIfEmpty() {
        if (!settingsRepository.isProUnlocked.first()) return
        val existing = proDao.observeAllCollections().first()
        if (existing.isNotEmpty()) {
            existing.forEach { collection ->
                val cleaned = defaultCollectionDisplayName(collection.name, collection.iconName)
                if (cleaned != collection.name) proDao.insertCollection(collection.copy(name = cleaned))
            }
            return
        }

        val defaults = listOf(
            SavedCollectionEntity(
                name = "高讚熱門",
                iconName = "smart_hot",
                rulesJson = """{"enabled":true,"matchMode":"AND","criteria":[{"field":"LIKES","operator":"GREATER_EQUAL","value":"100"}],"minLikes":100}""",
                sortOrder = 1
            ),
            SavedCollectionEntity(
                name = "長期必讀",
                iconName = "smart_study",
                rulesJson = """{"enabled":true,"matchMode":"AND","criteria":[{"field":"FAVORITE","operator":"EQUALS","value":"true"},{"field":"READ_STATUS","operator":"EQUALS","value":"未讀"}],"isFavorite":true,"isRead":false}""",
                sortOrder = 2
            ),
            SavedCollectionEntity(
                name = "靈感庫",
                iconName = "smart_sparkles",
                rulesJson = """{"enabled":true,"matchMode":"AND","criteria":[{"field":"FAVORITE","operator":"EQUALS","value":"true"},{"field":"CONTENT_MATCH_CATEGORY_KEYWORDS","operator":"CONTAINS","value":"靈感庫","config":{"categoryName":"靈感庫","includeTitle":true,"includeBody":true,"includeComments":false,"includeAiSummary":false,"includeManualNote":false}}],"isFavorite":true}""",
                sortOrder = 3
            )
        )
        defaults.forEach { proDao.insertCollection(it) }
    }

    companion object {
        // 常見預設分類關鍵字字典，供分類關鍵字比對補強
        val DEFAULT_CATEGORY_KEYWORDS: Map<String, List<String>> = mapOf(
            "靈感庫" to listOf("靈感", "點子", "想法", "構思", "idea", "brainstorm", "創意"),
            "找工作" to listOf("找工作", "求職", "履歷", "面試", "徵才", "工作機會", "104", "LinkedIn", "職缺", "獵頭"),
            "GPT畫圖" to listOf("GPT畫圖", "AI畫圖", "Prompt", "Midjourney", "Stable Diffusion", "SDXL", "圖像生成", "生圖", "DALL-E", "ComfyUI")
        )

        fun matchesItem(
            rule: CollectionRule,
            item: ItemDetail,
            categories: List<CategoryEntity> = emptyList(),
            keywords: List<KeywordEntity> = emptyList()
        ): Boolean {
            if (!rule.hasActiveConditions()) return false
            val criteria = rule.normalizedCriteria().filter {
                it.value.isNotBlank() || it.config?.categoryId != null || it.config?.categoryName?.isNotBlank() == true
            }
            if (criteria.isEmpty()) return false

            val isOr = rule.matchMode.equals("OR", ignoreCase = true)
            return if (isOr) {
                criteria.any { evaluateCriterion(it, item, categories, keywords) }
            } else {
                criteria.all { evaluateCriterion(it, item, categories, keywords) }
            }
        }

        /**
         * 取得貼文符合此規則的各項具體原因（供預覽展開呈現「為什麼這篇被抓進來」）
         */
        fun getMatchReasons(
            context: Context,
            rule: CollectionRule,
            item: ItemDetail,
            categories: List<CategoryEntity> = emptyList(),
            keywords: List<KeywordEntity> = emptyList()
        ): List<String> {
            val criteria = rule.normalizedCriteria().filter {
                it.value.isNotBlank() || it.config?.categoryId != null || it.config?.categoryName?.isNotBlank() == true
            }
            val reasons = mutableListOf<String>()
            for (criterion in criteria) {
                if (evaluateCriterion(criterion, item, categories, keywords)) {
                    val desc = describeMatchedCriterion(context, criterion, item, categories)
                    if (desc.isNotBlank()) reasons.add(desc)
                }
            }
            return reasons
        }

        private fun describeMatchedCriterion(
            context: Context,
            c: RuleCriterion,
            item: ItemDetail,
            categories: List<CategoryEntity>
        ): String {
            return when (c.field) {
                RuleField.LIKES -> context.getString(R.string.smart_desc_likes, item.item.likeCount, c.operator.symbol, c.value)
                RuleField.REPLIES -> context.getString(R.string.smart_desc_replies, item.item.replyCount, c.operator.symbol, c.value)
                RuleField.REPOSTS -> context.getString(R.string.smart_desc_reposts, item.item.repostCount, c.operator.symbol, c.value)
                RuleField.SHARES -> context.getString(R.string.smart_desc_shares)
                RuleField.AUTHOR -> context.getString(R.string.smart_desc_author, c.value)
                RuleField.TAG -> context.getString(R.string.smart_desc_tag, c.value)
                RuleField.CONTENT -> context.getString(R.string.smart_desc_content, c.value)
                RuleField.COMMENT_TEXT -> context.getString(R.string.smart_desc_comment, c.value)
                RuleField.AI_SUMMARY -> context.getString(R.string.smart_desc_ai_summary, c.value)
                RuleField.MANUAL_NOTE -> context.getString(R.string.smart_desc_manual_note, c.value)
                RuleField.CATEGORY -> {
                    val catName = categories.firstOrNull { it.id.toString() == c.value }?.name ?: c.value
                    context.getString(R.string.smart_desc_category, catName)
                }
                RuleField.FAVORITE -> if (c.value.equals("true", ignoreCase = true)) context.getString(R.string.smart_desc_favorite_true) else context.getString(R.string.smart_desc_favorite_false)
                RuleField.READ_STATUS -> if (c.value.contains("已讀")) context.getString(R.string.smart_desc_read_status_read) else context.getString(R.string.smart_desc_read_status_unread)
                RuleField.HAS_MEDIA -> context.getString(R.string.smart_desc_has_media)
                RuleField.HAS_IMAGE -> context.getString(R.string.smart_desc_has_image)
                RuleField.HAS_VIDEO -> context.getString(R.string.smart_desc_has_video)
                RuleField.SAVED_DATE -> context.getString(R.string.smart_desc_saved_date, context.getString(c.operator.labelRes), c.value)
                RuleField.CONTENT_MATCH_CATEGORY_KEYWORDS -> {
                    val catName = c.config?.categoryName?.ifBlank { c.value } ?: c.value
                    context.getString(R.string.smart_desc_content_keywords, catName)
                }
                RuleField.COMMENT_MATCH_CATEGORY_KEYWORDS -> {
                    val catName = c.config?.categoryName?.ifBlank { c.value } ?: c.value
                    context.getString(R.string.smart_desc_comment_keywords, catName)
                }
            }
        }

        fun evaluateCriterion(
            criterion: RuleCriterion,
            item: ItemDetail,
            categories: List<CategoryEntity> = emptyList(),
            keywords: List<KeywordEntity> = emptyList()
        ): Boolean {
            val v = criterion.value.trim()
            val config = criterion.config

            return when (criterion.field) {
                RuleField.LIKES -> {
                    evaluateNumber(item.item.likeCount, criterion.operator, v, config)
                }
                RuleField.REPLIES -> {
                    val count = maxOf(item.item.replyCount, item.comments.size)
                    evaluateNumber(count, criterion.operator, v, config)
                }
                RuleField.REPOSTS -> {
                    evaluateNumber(item.item.repostCount, criterion.operator, v, config)
                }
                RuleField.SHARES -> {
                    // 重要原則：來源平台若無 shareCount，保存/視為 null / unknown，不視為 0。
                    // 規則遇 null 視為「不符合」。
                    false
                }
                RuleField.AUTHOR -> {
                    val authorName = item.item.authorDisplayName
                    val authorHandle = item.item.authorHandle.removePrefix("@")
                    val target = v.removePrefix("@")
                    evaluateText(listOf(authorName, authorHandle), criterion.operator, target)
                }
                RuleField.TAG -> {
                    val tagNames = item.tags.map { it.name } + listOf(item.item.topicTag)
                    evaluateText(tagNames, criterion.operator, v)
                }
                RuleField.CONTENT -> {
                    val texts = listOf(item.displayBody, item.item.bodyText)
                    evaluateText(texts, criterion.operator, v)
                }
                RuleField.COMMENT_TEXT -> {
                    val commentTexts = item.comments.map { it.text }
                    if (commentTexts.isEmpty()) false
                    else evaluateText(commentTexts, criterion.operator, v)
                }
                RuleField.AI_SUMMARY -> {
                    val summary = item.aiSummary
                    if (summary.isBlank()) false
                    else evaluateText(listOf(summary), criterion.operator, v)
                }
                RuleField.MANUAL_NOTE -> {
                    val note = item.manualNote
                    if (note.isBlank()) false
                    else evaluateText(listOf(note), criterion.operator, v)
                }
                RuleField.CATEGORY -> {
                    // 9.1 分類：只代表貼文目前被分在哪個 Category，判斷 post.userEdit?.categoryId == targetCatId
                    val targetId = v.toLongOrNull() ?: categories.firstOrNull { it.name.equals(v, ignoreCase = true) }?.id
                    targetId != null && item.userEdit?.categoryId == targetId
                }
                RuleField.FAVORITE -> {
                    val target = v.equals("true", ignoreCase = true) || v.equals("已收藏", ignoreCase = true) || v.equals("是", ignoreCase = true)
                    item.isFavorite == target
                }
                RuleField.READ_STATUS -> {
                    val isReadTarget = v.contains("已讀") || v.equals("true", ignoreCase = true)
                    item.isRead == isReadTarget
                }
                RuleField.HAS_MEDIA -> {
                    val target = v.equals("true", ignoreCase = true) || v.equals("是", ignoreCase = true)
                    item.media.isNotEmpty() == target
                }
                RuleField.HAS_IMAGE -> {
                    val hasImage = item.media.any { it.kind.equals("IMAGE", ignoreCase = true) || it.kind.equals("CAROUSEL", ignoreCase = true) }
                    val target = v.equals("true", ignoreCase = true) || v.equals("是", ignoreCase = true)
                    hasImage == target
                }
                RuleField.HAS_VIDEO -> {
                    val hasVideo = item.media.any { it.kind.equals("VIDEO", ignoreCase = true) }
                    val target = v.equals("true", ignoreCase = true) || v.equals("是", ignoreCase = true)
                    hasVideo == target
                }
                RuleField.SAVED_DATE -> {
                    evaluateDays(item, criterion.operator, v)
                }
                RuleField.CONTENT_MATCH_CATEGORY_KEYWORDS -> {
                    evaluateCategoryKeywordsMatch(
                        item = item,
                        config = config,
                        fallbackCategoryName = v,
                        categories = categories,
                        keywords = keywords,
                        isCommentOnly = false
                    )
                }
                RuleField.COMMENT_MATCH_CATEGORY_KEYWORDS -> {
                    evaluateCategoryKeywordsMatch(
                        item = item,
                        config = config,
                        fallbackCategoryName = v,
                        categories = categories,
                        keywords = keywords,
                        isCommentOnly = true
                    )
                }
            }
        }

        private fun evaluateNumber(
            actual: Int,
            operator: ComparisonOperator,
            valueStr: String,
            config: RuleConditionConfig?
        ): Boolean {
            val num = valueStr.toIntOrNull() ?: config?.minValue ?: 0
            return when (operator) {
                ComparisonOperator.EQUALS -> actual == num
                ComparisonOperator.NOT_EQUALS -> actual != num
                ComparisonOperator.GREATER_THAN -> actual > num
                ComparisonOperator.GREATER_EQUAL -> actual >= num
                ComparisonOperator.LESS_THAN -> actual < num
                ComparisonOperator.LESS_EQUAL -> actual <= num
                ComparisonOperator.BETWEEN -> {
                    val parts = valueStr.split("..", "~", "-").mapNotNull { it.trim().toIntOrNull() }
                    val min = if (parts.isNotEmpty()) parts[0] else (config?.minValue ?: 0)
                    val max = if (parts.size > 1) parts[1] else (config?.maxValue ?: Int.MAX_VALUE)
                    actual in min..max
                }
                else -> actual >= num
            }
        }

        private fun evaluateText(
            texts: List<String>,
            operator: ComparisonOperator,
            target: String
        ): Boolean {
            if (target.isBlank()) return true
            val combined = texts.filter { it.isNotBlank() }
            if (combined.isEmpty()) return false

            return when (operator) {
                ComparisonOperator.EQUALS -> combined.any { it.trim().equals(target, ignoreCase = true) }
                ComparisonOperator.NOT_EQUALS -> combined.none { it.trim().equals(target, ignoreCase = true) }
                ComparisonOperator.CONTAINS -> combined.any { it.contains(target, ignoreCase = true) }
                ComparisonOperator.NOT_CONTAINS -> combined.none { it.contains(target, ignoreCase = true) }
                ComparisonOperator.CONTAINS_ANY -> {
                    val keywords = target.split("[\\s,，]+".toRegex()).filter { it.isNotBlank() }
                    if (keywords.isEmpty()) true
                    else keywords.any { kw -> combined.any { it.contains(kw, ignoreCase = true) } }
                }
                ComparisonOperator.CONTAINS_ALL -> {
                    val keywords = target.split("[\\s,，]+".toRegex()).filter { it.isNotBlank() }
                    if (keywords.isEmpty()) true
                    else keywords.all { kw -> combined.any { it.contains(kw, ignoreCase = true) } }
                }
                else -> combined.any { it.contains(target, ignoreCase = true) }
            }
        }

        private fun evaluateDays(item: ItemDetail, operator: ComparisonOperator, valueStr: String): Boolean {
            val days = valueStr.toIntOrNull() ?: 30
            val cutoff = System.currentTimeMillis() - days * 86400000L
            val itemTime = if (item.userEdit?.editedAt != null && item.userEdit.editedAt > 0) {
                item.userEdit.editedAt
            } else if (item.item.sourceFetchedAt > 0) {
                item.item.sourceFetchedAt
            } else {
                item.item.postedAt
            }
            if (itemTime <= 0) return false

            return when (operator) {
                ComparisonOperator.WITHIN_DAYS, ComparisonOperator.LESS_EQUAL, ComparisonOperator.NEWER_THAN_DAYS -> itemTime >= cutoff
                ComparisonOperator.OLDER_THAN_DAYS, ComparisonOperator.GREATER_THAN -> itemTime < cutoff
                else -> itemTime >= cutoff
            }
        }

        private fun evaluateCategoryKeywordsMatch(
            item: ItemDetail,
            config: RuleConditionConfig?,
            fallbackCategoryName: String,
            categories: List<CategoryEntity>,
            keywords: List<KeywordEntity>,
            isCommentOnly: Boolean
        ): Boolean {
            val targetCategoryName = config?.categoryName?.ifBlank { fallbackCategoryName } ?: fallbackCategoryName
            val targetCategoryId = config?.categoryId ?: categories.firstOrNull { it.name.equals(targetCategoryName, ignoreCase = true) }?.id

            // 建立此分類的所有關鍵字（包含分類名稱本身、核心詞彙、資料庫關鍵字、領域關鍵字字典）
            val kwSet = mutableSetOf<String>()
            if (targetCategoryName.isNotBlank()) {
                kwSet.add(targetCategoryName.trim())
                kwSet.addAll(OnDeviceClassifier.extractCategoryCoreTerms(targetCategoryName))
                kwSet.addAll(OnDeviceClassifier.getDomainKeywordsForCategory(targetCategoryName))
            }
            if (targetCategoryId != null) {
                val dbKws = keywords.filter { it.categoryId == targetCategoryId }.map { it.term.trim() }
                kwSet.addAll(dbKws)
            }
            // 查詢常用預設擴充關鍵字
            DEFAULT_CATEGORY_KEYWORDS[targetCategoryName]?.let { kwSet.addAll(it) }

            if (kwSet.isEmpty()) return false

            // 決定比對文字來源
            val textPool = mutableListOf<String>()
            if (isCommentOnly) {
                textPool.addAll(item.comments.map { it.text })
            } else {
                val incTitle = config?.includeTitle ?: true
                val incBody = config?.includeBody ?: true
                val incComments = config?.includeComments ?: false
                val incAiSummary = config?.includeAiSummary ?: false
                val incManualNote = config?.includeManualNote ?: false

                if (incTitle || incBody) {
                    textPool.add(item.displayBody)
                    textPool.add(item.item.bodyText)
                }
                if (incComments) {
                    textPool.addAll(item.comments.map { it.text })
                }
                if (incAiSummary && item.aiSummary.isNotBlank()) {
                    textPool.add(item.aiSummary)
                }
                if (incManualNote && item.manualNote.isNotBlank()) {
                    textPool.add(item.manualNote)
                }
            }

            val cleanTextPool = textPool.map { OnDeviceClassifier.stripNoise(it) }.filter { it.isNotEmpty() }
            if (cleanTextPool.isEmpty()) return false

            return kwSet.any { rawKw ->
                val kwLower = rawKw.trim().lowercase()
                kwLower.isNotEmpty() && cleanTextPool.any { cleanText ->
                    OnDeviceClassifier.containsKeyword(cleanText, kwLower)
                }
            }
        }

        fun generateRuleSummary(
            context: Context,
            rule: CollectionRule,
            categories: List<CategoryEntity> = emptyList()
        ): String {
            if (!rule.hasActiveConditions()) return context.getString(R.string.smart_error_no_conditions)
            val criteria = rule.normalizedCriteria().filter {
                it.value.isNotBlank() || it.config?.categoryId != null || it.config?.categoryName?.isNotBlank() == true
            }
            if (criteria.isEmpty()) return context.getString(R.string.smart_error_no_conditions)

            val separator = if (rule.matchMode.equals("OR", ignoreCase = true)) context.getString(R.string.smart_summary_separator_or) else " · "
            return criteria.joinToString(separator) { c ->
                when (c.field) {
                    RuleField.LIKES -> context.getString(R.string.smart_summary_likes, c.operator.symbol, c.value)
                    RuleField.REPLIES -> context.getString(R.string.smart_summary_replies, c.operator.symbol, c.value)
                    RuleField.REPOSTS -> context.getString(R.string.smart_summary_reposts, c.operator.symbol, c.value)
                    RuleField.SHARES -> context.getString(R.string.smart_summary_shares, c.operator.symbol, c.value)
                    RuleField.AUTHOR -> context.getString(R.string.smart_summary_author, c.value)
                    RuleField.TAG -> context.getString(R.string.smart_summary_tag, c.value)
                    RuleField.CONTENT -> context.getString(R.string.smart_summary_content, c.value)
                    RuleField.COMMENT_TEXT -> context.getString(R.string.smart_summary_comment, c.value)
                    RuleField.AI_SUMMARY -> context.getString(R.string.smart_summary_ai_summary, c.value)
                    RuleField.MANUAL_NOTE -> context.getString(R.string.smart_summary_manual_note, c.value)
                    RuleField.CATEGORY -> {
                        val catName = categories.firstOrNull { it.id.toString() == c.value }?.name ?: c.value
                        context.getString(R.string.smart_summary_category, catName)
                    }
                    RuleField.FAVORITE -> if (c.value.equals("true", ignoreCase = true) || c.value == "已收藏") context.getString(R.string.smart_summary_favorite_true) else context.getString(R.string.smart_summary_favorite_false)
                    RuleField.READ_STATUS -> if (c.value.contains("已讀")) context.getString(R.string.smart_summary_read_status_read) else context.getString(R.string.smart_summary_read_status_unread)
                    RuleField.HAS_MEDIA -> if (c.value.equals("true", ignoreCase = true) || c.value == "是") context.getString(R.string.smart_summary_has_media_true) else context.getString(R.string.smart_summary_has_media_false)
                    RuleField.HAS_IMAGE -> if (c.value.equals("true", ignoreCase = true) || c.value == "是") context.getString(R.string.smart_summary_has_image_true) else context.getString(R.string.smart_summary_has_image_false)
                    RuleField.HAS_VIDEO -> if (c.value.equals("true", ignoreCase = true) || c.value == "是") context.getString(R.string.smart_summary_has_video_true) else context.getString(R.string.smart_summary_has_video_false)
                    RuleField.SAVED_DATE -> context.getString(R.string.smart_summary_saved_date, c.value)
                    RuleField.CONTENT_MATCH_CATEGORY_KEYWORDS -> {
                        val catName = c.config?.categoryName?.ifBlank { c.value } ?: c.value
                        context.getString(R.string.smart_summary_content_keywords, catName)
                    }
                    RuleField.COMMENT_MATCH_CATEGORY_KEYWORDS -> {
                        val catName = c.config?.categoryName?.ifBlank { c.value } ?: c.value
                        context.getString(R.string.smart_summary_comment_keywords, catName)
                    }
                }
            }
        }

        fun isDefaultCollection(name: String, iconName: String): Boolean {
            val clean = defaultCollectionDisplayName(name, iconName)
            return clean in listOf("高讚熱門", "長期必讀", "靈感庫")
        }

        fun getDefaultRule(name: String, iconName: String): CollectionRule {
            val clean = defaultCollectionDisplayName(name, iconName)
            return when (clean) {
                "高讚熱門" -> CollectionRule(
                    enabled = true,
                    matchMode = "AND",
                    criteria = listOf(RuleCriterion(RuleField.LIKES, ComparisonOperator.GREATER_EQUAL, "100")),
                    minLikes = 100
                )
                "長期必讀" -> CollectionRule(
                    enabled = true,
                    matchMode = "AND",
                    criteria = listOf(
                        RuleCriterion(RuleField.FAVORITE, ComparisonOperator.EQUALS, "true"),
                        RuleCriterion(RuleField.READ_STATUS, ComparisonOperator.EQUALS, "未讀")
                    ),
                    isFavorite = true,
                    isRead = false
                )
                "靈感庫" -> CollectionRule(
                    enabled = true,
                    matchMode = "AND",
                    criteria = listOf(
                        RuleCriterion(RuleField.FAVORITE, ComparisonOperator.EQUALS, "true"),
                        RuleCriterion(
                            field = RuleField.CONTENT_MATCH_CATEGORY_KEYWORDS,
                            operator = ComparisonOperator.CONTAINS,
                            value = "靈感庫",
                            config = RuleConditionConfig(categoryName = "靈感庫", includeTitle = true, includeBody = true)
                        )
                    ),
                    isFavorite = true
                )
                else -> CollectionRule(enabled = true)
            }
        }
    }
}

/** 清理預設名稱中的 Emoji，確保使用純淨文字 */
fun defaultCollectionDisplayName(name: String, iconName: String): String {
    val clean = name.replace("[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF]".toRegex(), "").trim()
    return when {
        clean.contains("高讚熱門") -> "高讚熱門"
        clean.contains("長期必讀") -> "長期必讀"
        clean.contains("靈感庫") -> "靈感庫"
        clean.isNotBlank() -> clean
        else -> name
    }
}
