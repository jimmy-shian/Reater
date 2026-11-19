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
import org.junit.Assert.assertNull
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

    /**
     * 迴歸測試：歷史文章數量懸殊時，少量分類的文章若有明確關鍵字命中，
     * 不應被大量分類的歷史記錄搶走。
     */
    @Test
    fun largestCategoryDoesNotStealFromSmallerOneWithBetterKeywordMatch() = runBlocking {
        val catAi = CategoryEntity(id = 1, name = "AI科技", colorArgb = 0)
        val catFood = CategoryEntity(id = 2, name = "美食日記", colorArgb = 0)

        val categoryDao = FakeCategoryDao(listOf(catAi, catFood))
        // AI 有 50 篇歷史、美食只有 2 篇
        val history = (1..50).map {
            CategorizedItemSummary(
                categoryId = 1,
                topicTag = "AI",
                bodyText = "這篇文章討論了人工智慧的最新發展，包含模型分析。"
            )
        } + listOf(
            CategorizedItemSummary(
                categoryId = 2,
                topicTag = "美食",
                bodyText = "今天去了一間超棒的拉麵店，湯頭濃郁好吃。"
            ),
            CategorizedItemSummary(
                categoryId = 2,
                topicTag = "美食",
                bodyText = "推薦台北東區的火鍋店，食材新鮮必吃。"
            )
        )
        val itemDao = createMockItemDao(history)

        val classifier = OnDeviceClassifier(categoryDao, itemDao)

        // 新文章明確談美食，不應被 AI 分類搶走
        val result = classifier.classify(
            titleScope = "台北必吃拉麵推薦",
            bodyScope = "這間拉麵店的豚骨湯頭超級濃郁，菜單選擇多，推薦大家去吃看看。"
        )

        assertEquals(2L, result.categoryId)
        assertEquals("美食日記", result.categoryName)
    }

    @Test
    fun compositeCategoryNamesAndExpandedDomainsClassifyAccurately() = runBlocking {
        val categories = listOf(
            CategoryEntity(id = 1, name = "美麗的文案", colorArgb = 0),
            CategoryEntity(id = 2, name = "UIUX", colorArgb = 0),
            CategoryEntity(id = 3, name = "GPT指令", colorArgb = 0),
            CategoryEntity(id = 4, name = "日本", colorArgb = 0),
            CategoryEntity(id = 5, name = "自然", colorArgb = 0),
            CategoryEntity(id = 6, name = "音樂", colorArgb = 0),
            CategoryEntity(id = 7, name = "寫小說", colorArgb = 0),
            CategoryEntity(id = 8, name = "蘋果Mac", colorArgb = 0)
        )
        val classifier = OnDeviceClassifier(
            categoryDao = FakeCategoryDao(categories),
            itemDao = createMockItemDao(emptyList())
        )

        assertEquals(1L, classifier.classify("深夜文案金句", "整理幾段寫進心裡的散文句子與語錄").categoryId)
        assertEquals(2L, classifier.classify("Figma 設計系統", "調整介面排版字體與 UI/UX 互動動效").categoryId)
        assertEquals(3L, classifier.classify("ChatGPT 角色提示詞", "這組系統提示詞可以大幅提升回答品質").categoryId)
        assertEquals(4L, classifier.classify("東京自由行", "搭乘新幹線看富士山，晚上去居酒屋吃壽司").categoryId)
        assertEquals(5L, classifier.classify("清晨森林步道", "登山看到雲海與銀河星空的大自然風景").categoryId)
        assertEquals(6L, classifier.classify("深夜吉他彈唱", "這首單曲的副歌旋律與編曲太抓耳，樂團主唱現場超強").categoryId)
        assertEquals(7L, classifier.classify("長篇奇幻故事大綱", "主角人設與世界觀設定完成，準備開始寫第一章伏筆").categoryId)
        assertEquals(8L, classifier.classify("MacBook Pro 使用技巧", "用 macOS 內建捷徑與 Homebrew 整理開發環境").categoryId)
    }

    @Test
    fun asciiWordBoundariesAndUrlNoiseDoNotMisclassify() = runBlocking {
        val categories = listOf(
            CategoryEntity(id = 1, name = "UIUX", colorArgb = 0),
            CategoryEntity(id = 2, name = "AI科技", colorArgb = 0),
            CategoryEntity(id = 3, name = "音樂", colorArgb = 0)
        )
        val classifier = OnDeviceClassifier(
            categoryDao = FakeCategoryDao(categories),
            itemDao = createMockItemDao(emptyList())
        )

        // "guitar" 含 "ui"、"Taiwan"/"wait" 含 "ai"、URL 與 @ui_user 都不應誤判成 UIUX 或 AI科技
        val result = classifier.classify(
            titleScope = "Acoustic guitar practice in Taiwan",
            bodyScope = "Can't wait for the new album! @ui_lover https://example.com/ai/ui/test 木吉他編曲跟副歌旋律真好聽"
        )

        assertEquals(3L, result.categoryId)
        assertEquals("音樂", result.categoryName)
    }

    @Test
    fun largeCategoryWithFillerWordsDoesNotStealFillerOnlyPosts() = runBlocking {
        val categories = listOf(
            CategoryEntity(id = 1, name = "大分類", colorArgb = 0),
            CategoryEntity(id = 2, name = "小分類", colorArgb = 0)
        )
        val history = (1..80).map { idx ->
            CategorizedItemSummary(
                id = idx.toLong(),
                categoryId = 1L,
                topicTag = "",
                bodyText = "今天真的覺得這個超級好用，大家有空可以看看，哈哈謝謝分享。"
            )
        } + listOf(
            CategorizedItemSummary(
                id = 200L,
                categoryId = 2L,
                topicTag = "",
                bodyText = "昨天在公園散步，看到橘貓睡在長椅上。"
            )
        )
        val classifier = OnDeviceClassifier(
            categoryDao = FakeCategoryDao(categories),
            itemDao = createMockItemDao(history)
        )

        val result = classifier.classify(
            titleScope = "今天心情",
            bodyScope = "今天真的覺得大家這樣做很可以，哈哈非常感謝分享。"
        )

        assertNull(result.categoryId)
    }

    @Test
    fun classifyExcludesSelfItemIdDuringReclassification() = runBlocking {
        val categories = listOf(
            CategoryEntity(id = 1, name = "生活雜記", colorArgb = 0),
            CategoryEntity(id = 2, name = "音樂", colorArgb = 0)
        )
        // 該貼文原本錯放在「生活雜記」，重跑分類時應排除自身歷史紀錄並依內文分到「音樂」
        val history = listOf(
            CategorizedItemSummary(
                id = 99L,
                categoryId = 1L,
                topicTag = "",
                bodyText = "獨立樂團單曲副歌旋律太洗腦，巡迴演唱會必聽。"
            )
        )
        val classifier = OnDeviceClassifier(
            categoryDao = FakeCategoryDao(categories),
            itemDao = createMockItemDao(history)
        )

        val result = classifier.classify(
            titleScope = "巡迴演唱會歌單",
            bodyScope = "獨立樂團單曲副歌旋律太洗腦，巡迴演唱會必聽。",
            excludeItemId = 99L
        )

        assertEquals(2L, result.categoryId)
        assertEquals("音樂", result.categoryName)
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
