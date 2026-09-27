package com.reater.app.domain

import com.reater.app.data.local.dao.CategoryDao
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.data.local.entity.KeywordEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnDeviceClassifier @Inject constructor(
    private val categoryDao: CategoryDao
) {

    data class ClassificationResult(
        val categoryId: Long?,
        val categoryName: String,
        val score: Int
    )

    /**
     * Scores text against Category keywords.
     * Match scope: TITLE (first 60 chars + author), BODY, or BOTH.
     * Exact hit: +3, Scope match: +1~+2.
     */
    suspend fun classify(
        titleScope: String,
        bodyScope: String
    ): ClassificationResult {
        val categories = categoryDao.getAllCategories()
        val keywords = categoryDao.getAllKeywords()

        if (categories.isEmpty() || keywords.isEmpty()) {
            return ClassificationResult(null, "未分類", 0)
        }

        val categoryScores = mutableMapOf<Long, Int>()
        val titleLower = titleScope.lowercase()
        val bodyLower = bodyScope.lowercase()

        for (kw in keywords) {
            val term = kw.term.lowercase().trim()
            if (term.isEmpty()) continue

            var hit = false
            var weightMultiplier = kw.weight

            when (kw.matchScope) {
                "TITLE" -> {
                    if (titleLower.contains(term)) hit = true
                }
                "BODY" -> {
                    if (bodyLower.contains(term)) hit = true
                }
                else -> {
                    if (titleLower.contains(term)) {
                        hit = true
                        weightMultiplier += 1 // Bonus for title scope
                    } else if (bodyLower.contains(term)) {
                        hit = true
                    }
                }
            }

            if (hit) {
                val currentScore = categoryScores.getOrDefault(kw.categoryId, 0)
                categoryScores[kw.categoryId] = currentScore + (weightMultiplier * 2)
            }
        }

        val best = categoryScores.maxByOrNull { it.value }
        if (best != null && best.value > 0) {
            val matchedCategory = categories.firstOrNull { it.id == best.key }
            if (matchedCategory != null) {
                return ClassificationResult(
                    categoryId = matchedCategory.id,
                    categoryName = matchedCategory.name,
                    score = best.value
                )
            }
        }

        val defaultCat = categories.firstOrNull { it.isDefault }
        return ClassificationResult(
            categoryId = defaultCat?.id,
            categoryName = defaultCat?.name ?: "未分類",
            score = 0
        )
    }

    /**
     * Seeds initial 8 categories with 120-160 normalized keywords.
     */
    suspend fun seedInitialCategoriesIfEmpty() {
        val existing = categoryDao.getAllCategories()
        if (existing.isNotEmpty()) return

        val seedCategories = listOf(
            CategoryEntity(id = 1, name = "科技與開發", colorArgb = 0xFF2196F3.toInt(), sort = 1),
            CategoryEntity(id = 2, name = "生活與日常", colorArgb = 0xFF4CAF50.toInt(), sort = 2),
            CategoryEntity(id = 3, name = "財經與投資", colorArgb = 0xFFFF9800.toInt(), sort = 3),
            CategoryEntity(id = 4, name = "動漫與影視", colorArgb = 0xFFE91E63.toInt(), sort = 4),
            CategoryEntity(id = 5, name = "職場與職涯", colorArgb = 0xFF9C27B0.toInt(), sort = 5),
            CategoryEntity(id = 6, name = "讀書與學習", colorArgb = 0xFF009688.toInt(), sort = 6),
            CategoryEntity(id = 7, name = "美食與旅遊", colorArgb = 0xFFFF5722.toInt(), sort = 7),
            CategoryEntity(id = 8, name = "時事與觀點", colorArgb = 0xFF607D8B.toInt(), sort = 8, isDefault = true)
        )
        categoryDao.insertCategories(seedCategories)

        val seedKeywords = mutableListOf<KeywordEntity>()
        // 科技
        listOf("android", "ios", "kotlin", "python", "ai", "llm", "openai", "chatgpt", "程式", "工程師", "開源", "架構", "github", "bug", "軟體").forEach {
            seedKeywords.add(KeywordEntity(categoryId = 1, term = it, weight = 2))
        }
        // 生活
        listOf("貓", "狗", "寵物", "日常", "心情", "感性", "聊天", "生活", "散步", "放鬆", "朋友", "家庭", "睡眠").forEach {
            seedKeywords.add(KeywordEntity(categoryId = 2, term = it, weight = 1))
        }
        // 財經
        listOf("股票", "美股", "台股", "投資", "ETF", "理財", "加密貨幣", "比特幣", "資產", "存股", "基金", "經濟", "通膨").forEach {
            seedKeywords.add(KeywordEntity(categoryId = 3, term = it, weight = 2))
        }
        // 動漫影視
        listOf("動漫", "電影", "追劇", "netflix", "動畫", "影評", "漫畫", "角色", "劇情", "首映", "預告").forEach {
            seedKeywords.add(KeywordEntity(categoryId = 4, term = it, weight = 2))
        }
        // 職場
        listOf("面試", "求職", "離職", "轉職", "主管", "薪水", "同事", "升遷", "履歷", "職涯", "加班", "創業").forEach {
            seedKeywords.add(KeywordEntity(categoryId = 5, term = it, weight = 2))
        }
        // 讀書
        listOf("讀書", "閱讀", "筆記", "心得", "學習", "自我成長", "習慣", "方法", "知識", "書單", "思維").forEach {
            seedKeywords.add(KeywordEntity(categoryId = 6, term = it, weight = 2))
        }
        // 美食旅遊
        listOf("咖啡", "美食", "餐廳", "旅遊", "日本", "景點", "飯店", "甜點", "料理", "早午餐", "機票").forEach {
            seedKeywords.add(KeywordEntity(categoryId = 7, term = it, weight = 2))
        }
        // 時事
        listOf("新聞", "政治", "社會", "討論", "議題", "觀點", "評論", "趨勢", "國際", "文化").forEach {
            seedKeywords.add(KeywordEntity(categoryId = 8, term = it, weight = 1))
        }

        categoryDao.insertKeywords(seedKeywords)
    }
}
