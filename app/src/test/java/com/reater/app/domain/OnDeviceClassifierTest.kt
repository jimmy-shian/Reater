package com.reater.app.domain

import com.reater.app.data.local.dao.CategoryDao
import com.reater.app.data.local.dao.CategorizedItemSummary
import com.reater.app.data.local.dao.ItemDao
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.data.local.entity.KeywordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Proxy

class OnDeviceClassifierTest {

    @Test
    fun historicalKeywordMatchingClassifiesNewPostToSameCategory() = runBlocking {
        val catA = CategoryEntity(id = 10, name = "A分類", colorArgb = 0)
        val catB = CategoryEntity(id = 20, name = "B分類", colorArgb = 0)

        val categoryDao = FakeCategoryDao(listOf(catA, catB))
        // 過去使用者曾把包含「提示詞」的文章分類到 Category 10 (A分類)
        val history = listOf(
            CategorizedItemSummary(
                categoryId = 10,
                topicTag = "AI",
                bodyText = "這是一篇關於提示詞技巧的心得分享，如何寫好提示詞。"
            )
        )
        val itemDao = createMockItemDao(history)

        val classifier = OnDeviceClassifier(categoryDao, itemDao)

        // 新文章同樣出現「提示詞」
        val result = classifier.classify(
            titleScope = "最新提示詞整理",
            bodyScope = "今天來分享超實用的提示詞模板，讓你效率加倍！"
        )

        assertNotNull(result.categoryId)
        assertEquals(10L, result.categoryId)
        assertEquals("A分類", result.categoryName)
    }

    @Test
    fun baseDomainKeywordsClassifyWithoutCustomRules() = runBlocking {
        val catAi = CategoryEntity(id = 1, name = "AI科技", colorArgb = 0)
        val catFood = CategoryEntity(id = 2, name = "美食日記", colorArgb = 0)

        val categoryDao = FakeCategoryDao(listOf(catAi, catFood))
        val itemDao = createMockItemDao(emptyList())

        val classifier = OnDeviceClassifier(categoryDao, itemDao)

        val result = classifier.classify(
            titleScope = "新版 Claude 3.5 釋出",
            bodyScope = "ChatGPT 與 Claude 在生成式 AI 程式碼表現非常強大。"
        )

        assertEquals(1L, result.categoryId)
        assertEquals("AI科技", result.categoryName)
    }

    private fun createMockItemDao(summaries: List<CategorizedItemSummary>): ItemDao {
        return Proxy.newProxyInstance(
            ItemDao::class.java.classLoader,
            arrayOf(ItemDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getRecentCategorizedSummaries" -> summaries
                else -> null
            }
        } as ItemDao
    }

    private class FakeCategoryDao(private val categories: List<CategoryEntity>) : CategoryDao {
        override fun observeAllCategories(): Flow<List<CategoryEntity>> = flowOf(categories)
        override suspend fun getAllCategories(): List<CategoryEntity> = categories
        override suspend fun getCategoryById(id: Long): CategoryEntity? = categories.firstOrNull { it.id == id }
        override suspend fun clearKeywordsForCategory(categoryId: Long) = Unit
        override suspend fun insertCategory(category: CategoryEntity): Long = category.id
        override suspend fun insertCategories(categories: List<CategoryEntity>) = Unit
        override suspend fun updateCategoryMeta(id: Long, name: String, avatarIcon: String, updatedAt: Long) = Unit
        override suspend fun updateSort(id: Long, sort: Int) = Unit
        override suspend fun deleteCategory(id: Long) = Unit
        override suspend fun deleteDefaultCategories(): Int = 0
        override suspend fun deleteOrphanKeywords(): Int = 0
        override suspend fun nullOutDanglingCategoryRefs(): Int = 0
        override suspend fun getAllKeywords(): List<KeywordEntity> = emptyList()
        override suspend fun getKeywordsByCategory(categoryId: Long): List<KeywordEntity> = emptyList()
        override suspend fun insertKeywords(keywords: List<KeywordEntity>) = Unit
    }
}
