package com.reater.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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
        ORDER BY items.id DESC
    """)
    fun observeAllItemDetails(): Flow<List<ItemDetail>>

    @Transaction
    @Query("""
        SELECT items.* FROM items
        LEFT JOIN user_edits ON items.id = user_edits.itemId
        WHERE user_edits.isRead = 0 OR user_edits.isRead IS NULL
        ORDER BY items.id DESC
    """)
    fun observeUnreadItemDetails(): Flow<List<ItemDetail>>

    @Transaction
    @Query("""
        SELECT items.* FROM items
        LEFT JOIN user_edits ON items.id = user_edits.itemId
        WHERE user_edits.isFavorite = 1
        ORDER BY items.id DESC
    """)
    fun observeFavoriteItemDetails(): Flow<List<ItemDetail>>

    @Transaction
    @Query("""
        SELECT items.* FROM items
        LEFT JOIN user_edits ON items.id = user_edits.itemId
        WHERE user_edits.categoryId = :categoryId
        ORDER BY items.id DESC
    """)
    fun observeItemDetailsByCategory(categoryId: Long): Flow<List<ItemDetail>>

    @Transaction
    @Query("""
        SELECT items.* FROM items
        LEFT JOIN user_edits ON items.id = user_edits.itemId
        WHERE items.bodyText LIKE '%' || :query || '%'
           OR items.commentsText LIKE '%' || :query || '%'
           OR items.authorHandle LIKE '%' || :query || '%'
           OR items.authorDisplayName LIKE '%' || :query || '%'
           OR user_edits.manualNote LIKE '%' || :query || '%'
           OR user_edits.manualSummary LIKE '%' || :query || '%'
        ORDER BY items.id DESC
    """)
    fun searchItemDetails(query: String): Flow<List<ItemDetail>>

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

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
}
