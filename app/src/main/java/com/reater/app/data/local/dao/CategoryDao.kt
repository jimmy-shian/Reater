package com.reater.app.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.data.local.entity.KeywordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sort ASC, id ASC")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sort ASC, id ASC")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getCategoryByName(name: String): CategoryEntity?

    @Query("DELETE FROM keywords WHERE categoryId = :categoryId")
    suspend fun clearKeywordsForCategory(categoryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("UPDATE categories SET name = :name, avatarIcon = :avatarIcon, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCategoryMeta(id: Long, name: String, avatarIcon: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    /** 產品方向：分類為付費主打，清除全部內建預設分類 */
    @Query("DELETE FROM categories WHERE isDefault = 1")
    suspend fun deleteDefaultCategories(): Int

    /** 清除已無所屬分類的孤兒關鍵字 */
    @Query("DELETE FROM keywords WHERE categoryId NOT IN (SELECT id FROM categories)")
    suspend fun deleteOrphanKeywords(): Int

    /** 被刪分類的貼文引用置空，改顯示為未分類 */
    @Query("UPDATE user_edits SET categoryId = NULL WHERE categoryId IS NOT NULL AND categoryId NOT IN (SELECT id FROM categories)")
    suspend fun nullOutDanglingCategoryRefs(): Int

    @Query("SELECT * FROM keywords")
    suspend fun getAllKeywords(): List<KeywordEntity>

    @Query("SELECT * FROM keywords WHERE categoryId = :categoryId")
    suspend fun getKeywordsByCategory(categoryId: Long): List<KeywordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKeywords(keywords: List<KeywordEntity>)
}
