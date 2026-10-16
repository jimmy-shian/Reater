package com.reater.app.data.local

import com.reater.app.data.local.dao.CategoryDao
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.data.local.entity.KeywordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CategoryNormalizationTest {
    @Test fun normalizedNameReusesExistingCategoryInsteadOfCreatingDuplicate() = runBlocking {
        val dao = MemoryCategoryDao(mutableListOf(CategoryEntity(id = 7, name = "　GPT 旅遊　", colorArgb = 0)))
        assertEquals(7L, dao.getOrCreateCategory(CategoryEntity(name = " gpt 旅遊 ", colorArgb = 0)))
        assertEquals(1, dao.getAllCategories().size)
    }

    @Test fun uncategorizedNeverCreatesEntityAndBlankNamesAreRejected() = runBlocking {
        val dao = MemoryCategoryDao()
        for (name in listOf("未分類", "　 ", "旅遊\n日本")) {
            val error = runCatching { dao.getOrCreateCategory(CategoryEntity(name = name, colorArgb = 0)) }.exceptionOrNull()
            assertTrue(name, error is IllegalArgumentException)
        }
        assertTrue(dao.getAllCategories().isEmpty())
    }

    private class MemoryCategoryDao(private val rows: MutableList<CategoryEntity> = mutableListOf()) : CategoryDao {
        override fun observeAllCategories(): Flow<List<CategoryEntity>> = flowOf(rows)
        override suspend fun getAllCategories(): List<CategoryEntity> = rows.toList()
        override suspend fun getCategoryById(id: Long): CategoryEntity? = rows.firstOrNull { it.id == id }
        override suspend fun clearKeywordsForCategory(categoryId: Long) = Unit
        override suspend fun insertCategory(category: CategoryEntity): Long {
            val id = (rows.maxOfOrNull { it.id } ?: 0) + 1
            rows += category.copy(id = id)
            return id
        }
        override suspend fun insertCategories(categories: List<CategoryEntity>) { rows += categories }
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
