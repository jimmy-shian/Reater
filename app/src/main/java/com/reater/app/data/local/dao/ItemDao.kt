package com.reater.app.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.reater.app.data.local.entity.ItemDetail
import com.reater.app.data.local.entity.ItemEntity
import com.reater.app.data.local.entity.UserEditEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity): Long

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE canonicalUrl = :canonicalUrl LIMIT 1")
    suspend fun getItemByCanonicalUrl(canonicalUrl: String): ItemEntity?

    @Query("SELECT * FROM items WHERE shortcode = :shortcode LIMIT 1")
    suspend fun getItemByShortcode(shortcode: String): ItemEntity?

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    fun observeItemDetailById(id: Long): Flow<ItemDetail?>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getItemDetailById(id: Long): ItemDetail?

    @Transaction
    @Query("""
        SELECT items.* FROM items
        LEFT JOIN user_edits ON items.id = user_edits.itemId
        WHERE items.isDeleted = 0
        ORDER BY items.id DESC
    """)
    fun observeAllItemDetails(): Flow<List<ItemDetail>>

    @Transaction
    @Query("""
        SELECT items.* FROM items
        LEFT JOIN user_edits ON items.id = user_edits.itemId
        WHERE items.isDeleted = 0 AND (user_edits.isRead = 0 OR user_edits.isRead IS NULL)
        ORDER BY items.id DESC
    """)
    fun observeUnreadItemDetails(): Flow<List<ItemDetail>>

    @Transaction
    @Query("""
        SELECT items.* FROM items
        LEFT JOIN user_edits ON items.id = user_edits.itemId
        WHERE items.isDeleted = 0 AND user_edits.isFavorite = 1
        ORDER BY items.id DESC
    """)
    fun observeFavoriteItemDetails(): Flow<List<ItemDetail>>

    @Transaction
    @Query("""
        SELECT items.* FROM items
        LEFT JOIN user_edits ON items.id = user_edits.itemId
        WHERE items.isDeleted = 0 AND user_edits.categoryId = :categoryId
        ORDER BY items.id DESC
    """)
    fun observeItemDetailsByCategory(categoryId: Long): Flow<List<ItemDetail>>

    @Transaction
    @Query("""
        SELECT items.* FROM items
        LEFT JOIN user_edits ON items.id = user_edits.itemId
        WHERE items.isDeleted = 1
        ORDER BY items.deletedAt DESC
    """)
    fun observeTrashItemDetails(): Flow<List<ItemDetail>>

    @Transaction
    @Query("""
        SELECT items.* FROM items
        LEFT JOIN user_edits ON items.id = user_edits.itemId
        WHERE items.isDeleted = 0 AND (
            items.bodyText LIKE '%' || :query || '%'
            OR items.commentsText LIKE '%' || :query || '%'
            OR items.authorHandle LIKE '%' || :query || '%'
            OR items.authorDisplayName LIKE '%' || :query || '%'
            OR user_edits.manualNote LIKE '%' || :query || '%'
            OR user_edits.manualSummary LIKE '%' || :query || '%'
            OR EXISTS (
                 SELECT 1 FROM comments
                 WHERE comments.itemId = items.id
                   AND comments.text LIKE '%' || :query || '%'
            )
            OR EXISTS (
                 SELECT 1 FROM item_tags
                 INNER JOIN tags ON tags.id = item_tags.tagId
                 WHERE item_tags.itemId = items.id
                   AND tags.name LIKE '%' || :query || '%'
            )
        )
        ORDER BY items.id DESC
    """)
    fun searchItemDetails(query: String): Flow<List<ItemDetail>>

    @Transaction
    @Query("""
        SELECT items.* FROM items
        INNER JOIN items_fts ON items_fts.rowid = items.id
        WHERE items.isDeleted = 0 AND items_fts MATCH :query
        ORDER BY items.id DESC
    """)
    fun searchItemDetailsFts(query: String): Flow<List<ItemDetail>>

    @Query("INSERT OR REPLACE INTO items_fts(rowid, searchText) VALUES (:rowid, :searchText)")
    suspend fun updateSearchIndex(rowid: Long, searchText: String)

    @Query("DELETE FROM items_fts WHERE rowid = :rowid")
    suspend fun deleteSearchIndex(rowid: Long)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("UPDATE items SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun moveToTrash(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE items SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    @Query("DELETE FROM items WHERE isDeleted = 1 AND deletedAt IS NOT NULL AND deletedAt < :purgeThreshold")
    suspend fun purgeExpiredTrash(purgeThreshold: Long)

    @Query("DELETE FROM items WHERE isDeleted = 1")
    suspend fun emptyTrash()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserEdit(userEdit: UserEditEntity)

    @Query("SELECT * FROM user_edits WHERE itemId = :itemId LIMIT 1")
    suspend fun getUserEditByItemId(itemId: Long): UserEditEntity?

    @Query("UPDATE user_edits SET isRead = :isRead WHERE itemId = :itemId")
    suspend fun updateReadStatus(itemId: Long, isRead: Boolean)

    @Query("UPDATE user_edits SET isFavorite = :isFavorite WHERE itemId = :itemId")
    suspend fun updateFavoriteStatus(itemId: Long, isFavorite: Boolean)

    @Query("UPDATE user_edits SET categoryId = :categoryId WHERE itemId = :itemId")
    suspend fun updateCategory(itemId: Long, categoryId: Long?)

    /** 詳情開啟：次數 +1、更新最近開啟時間 */
    @Query("UPDATE user_edits SET openCount = openCount + 1, lastOpenedAt = :now WHERE itemId = :itemId")
    suspend fun recordOpen(itemId: Long, now: Long = System.currentTimeMillis())

    // ---------- 分析頁統計 ----------

    /** 期間內儲存文章數（以首次儲存時間計） */
    @Query("SELECT COUNT(*) FROM items WHERE isDeleted = 0 AND sourceFetchedAt >= :since")
    suspend fun countSavedSince(since: Long): Int

    /** 期間內打開過的文章數（去重） */
    @Query("SELECT COUNT(*) FROM user_edits WHERE lastOpenedAt >= :since")
    suspend fun countOpenedSince(since: Long): Int

    /** 累計回顧次數（每篇超出首次的開啟次數加總） */
    @Query("SELECT COALESCE(SUM(CASE WHEN openCount > 1 THEN openCount - 1 ELSE 0 END), 0) FROM user_edits")
    suspend fun countTotalReviews(): Int

    /** 每日儲存數（localtime 日期字串 yyyy-MM-dd → 筆數），供趨勢圖用 */
    @Query("""
        SELECT date(sourceFetchedAt / 1000, 'unixepoch', 'localtime') AS day,
               COUNT(*) AS cnt
        FROM items
        WHERE isDeleted = 0 AND sourceFetchedAt >= :since
        GROUP BY day
        ORDER BY day ASC
    """)
    suspend fun dailySavedSince(since: Long): List<DayCount>

    /** 期間內未讀數（通知文案用） */
    @Query("""
        SELECT COUNT(*) FROM items
        LEFT JOIN user_edits ON items.id = user_edits.itemId
        WHERE items.isDeleted = 0 AND (user_edits.isRead = 0 OR user_edits.isRead IS NULL)
          AND items.sourceFetchedAt >= :since
    """)
    suspend fun countUnreadSince(since: Long): Int
}

data class DayCount(
    val day: String,
    val cnt: Int
)
