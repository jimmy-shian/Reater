package com.reater.app.domain

import com.reater.app.data.local.dao.CategoryDao
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

    companion object {
        /** 免費版自訂分類上限 */
        const val FREE_CUSTOM_CATEGORY_LIMIT = 3
    }

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
        // 產品方向：分類為付費主打功能，清除 8 個內建預設分類，
        // 新舊機一律只剩自訂分類（免費 3 個、Pro 無上限）。
        // 被刪分類的貼文引用置空顯示為未分類；此後不再種子任何預設。
        runCatching {
            categoryDao.deleteDefaultCategories()
            categoryDao.deleteOrphanKeywords()
            categoryDao.nullOutDanglingCategoryRefs()
        }
    }
}
