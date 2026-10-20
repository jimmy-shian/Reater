package com.reater.app.domain

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
enum class RuleField(val label: String, val group: String) {
    // 內容
    AUTHOR("作者", "內容"),
    TAG("標籤", "內容"),
    CATEGORY("分類", "內容"),
    CONTENT("內文 / 標題", "內容"),
    COMMENT_TEXT("留言內容", "內容"),
    AI_SUMMARY("AI 摘要", "內容"),
    MANUAL_NOTE("筆記", "內容"),

    // 狀態
    FAVORITE("收藏狀態", "狀態"),
    READ_STATUS("閱讀狀態", "狀態"),
    HAS_MEDIA("包含媒體", "狀態"),
    HAS_IMAGE("包含圖片", "狀態"),
    HAS_VIDEO("包含影片", "狀態"),

    // 互動數據
    LIKES("按讚數", "互動"),
    REPLIES("留言數", "互動"),
    REPOSTS("轉發數", "互動"),
    SHARES("分享數", "互動"),

    // 時間
    SAVED_DATE("收藏時間", "時間"),

    // 智慧分類
    CONTENT_MATCH_CATEGORY_KEYWORDS("內容命中分類關鍵字", "智慧分類"),
    COMMENT_MATCH_CATEGORY_KEYWORDS("留言命中分類關鍵字", "智慧分類")
}

@Serializable
enum class ComparisonOperator(val symbol: String, val label: String) {
    EQUALS("=", "等於"),
    NOT_EQUALS("≠", "不等於"),
    CONTAINS("包含", "包含"),
    NOT_CONTAINS("不包含", "不包含"),
    CONTAINS_ANY("包含任一", "包含任一關鍵字"),
    CONTAINS_ALL("包含所有", "包含所有關鍵字"),
    GREATER_EQUAL("≥", "大於等於"),
    GREATER_THAN(">", "大於"),
    LESS_EQUAL("≤", "小於等於"),
    LESS_THAN("<", "小於"),
    BETWEEN("介於", "介於"),
    WITHIN_DAYS("最近", "最近 N 天"),
    OLDER_THAN_DAYS("早於", "早於 N 天前"),
    NEWER_THAN_DAYS("晚於", "晚於 N 天內")
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
                    val desc = describeMatchedCriterion(criterion, item, categories)
                    if (desc.isNotBlank()) reasons.add(desc)
                }
            }
            return reasons
        }

        private fun describeMatchedCriterion(
            c: RuleCriterion,
            item: ItemDetail,
            categories: List<CategoryEntity>
        ): String {
            return when (c.field) {
                RuleField.LIKES -> "按讚數 ${item.item.likeCount} ${c.operator.symbol} ${c.value}"
                RuleField.REPLIES -> "留言數 ${item.item.replyCount} ${c.operator.symbol} ${c.value}"
                RuleField.REPOSTS -> "轉發數 ${item.item.repostCount} ${c.operator.symbol} ${c.value}"
                RuleField.SHARES -> "分享數符合條件"
                RuleField.AUTHOR -> "作者符合「${c.value}」"
                RuleField.TAG -> "標籤包含「${c.value}」"
                RuleField.CONTENT -> "內文/標題包含「${c.value}」"
                RuleField.COMMENT_TEXT -> "留言包含「${c.value}」"
                RuleField.AI_SUMMARY -> "AI 摘要包含「${c.value}」"
                RuleField.MANUAL_NOTE -> "筆記包含「${c.value}」"
                RuleField.CATEGORY -> {
                    val catName = categories.firstOrNull { it.id.toString() == c.value }?.name ?: c.value
                    "目前分類為「$catName」"
                }
                RuleField.FAVORITE -> if (c.value.equals("true", ignoreCase = true)) "狀態為已收藏" else "狀態為未收藏"
                RuleField.READ_STATUS -> if (c.value.contains("已讀")) "閱讀狀態為已讀" else "閱讀狀態為未讀"
                RuleField.HAS_MEDIA -> "含有多媒體附件"
                RuleField.HAS_IMAGE -> "含有圖片內容"
                RuleField.HAS_VIDEO -> "含有影片內容"
                RuleField.SAVED_DATE -> "收藏時間符合（${c.operator.label} ${c.value} 天）"
                RuleField.CONTENT_MATCH_CATEGORY_KEYWORDS -> {
                    val catName = c.config?.categoryName?.ifBlank { c.value } ?: c.value
                    "內容命中分類關鍵字「$catName」"
                }
                RuleField.COMMENT_MATCH_CATEGORY_KEYWORDS -> {
                    val catName = c.config?.categoryName?.ifBlank { c.value } ?: c.value
                    "留言命中分類關鍵字「$catName」"
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

            // 建立此分類的所有關鍵字（包含分類名稱本身、資料庫關鍵字、預設關鍵字字典）
            val kwSet = mutableSetOf<String>()
            if (targetCategoryName.isNotBlank()) {
                kwSet.add(targetCategoryName.trim())
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

            if (textPool.isEmpty()) return false

            return kwSet.any { kw ->
                kw.isNotBlank() && textPool.any { it.contains(kw, ignoreCase = true) }
            }
        }

        fun generateRuleSummary(
            rule: CollectionRule,
            categories: List<CategoryEntity> = emptyList()
        ): String {
            if (!rule.hasActiveConditions()) return "尚未設定條件"
            val criteria = rule.normalizedCriteria().filter {
                it.value.isNotBlank() || it.config?.categoryId != null || it.config?.categoryName?.isNotBlank() == true
            }
            if (criteria.isEmpty()) return "尚未設定條件"

            val separator = if (rule.matchMode.equals("OR", ignoreCase = true)) " 或 " else " · "
            return criteria.joinToString(separator) { c ->
                when (c.field) {
                    RuleField.LIKES -> "按讚數 ${c.operator.symbol} ${c.value}"
                    RuleField.REPLIES -> "留言數 ${c.operator.symbol} ${c.value}"
                    RuleField.REPOSTS -> "轉發數 ${c.operator.symbol} ${c.value}"
                    RuleField.SHARES -> "分享數 ${c.operator.symbol} ${c.value}"
                    RuleField.AUTHOR -> "作者「${c.value}」"
                    RuleField.TAG -> "標籤「${c.value}」"
                    RuleField.CONTENT -> "內文含「${c.value}」"
                    RuleField.COMMENT_TEXT -> "留言含「${c.value}」"
                    RuleField.AI_SUMMARY -> "AI 摘要含「${c.value}」"
                    RuleField.MANUAL_NOTE -> "筆記含「${c.value}」"
                    RuleField.CATEGORY -> {
                        val catName = categories.firstOrNull { it.id.toString() == c.value }?.name ?: c.value
                        "分類「$catName」"
                    }
                    RuleField.FAVORITE -> if (c.value.equals("true", ignoreCase = true) || c.value == "已收藏") "已收藏" else "未收藏"
                    RuleField.READ_STATUS -> if (c.value.contains("已讀")) "已讀" else "未讀"
                    RuleField.HAS_MEDIA -> if (c.value.equals("true", ignoreCase = true) || c.value == "是") "含媒體" else "無媒體"
                    RuleField.HAS_IMAGE -> if (c.value.equals("true", ignoreCase = true) || c.value == "是") "含圖片" else "無圖片"
                    RuleField.HAS_VIDEO -> if (c.value.equals("true", ignoreCase = true) || c.value == "是") "含影片" else "無影片"
                    RuleField.SAVED_DATE -> "最近 ${c.value} 天"
                    RuleField.CONTENT_MATCH_CATEGORY_KEYWORDS -> {
                        val catName = c.config?.categoryName?.ifBlank { c.value } ?: c.value
                        "內容命中「$catName」"
                    }
                    RuleField.COMMENT_MATCH_CATEGORY_KEYWORDS -> {
                        val catName = c.config?.categoryName?.ifBlank { c.value } ?: c.value
                        "留言命中「$catName」"
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
