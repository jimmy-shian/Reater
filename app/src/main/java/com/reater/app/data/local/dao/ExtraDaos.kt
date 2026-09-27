package com.reater.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reater.app.data.local.entity.CommentEntity
import com.reater.app.data.local.entity.ItemTagCrossRef
import com.reater.app.data.local.entity.MediaEntity
import com.reater.app.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments WHERE itemId = :itemId ORDER BY sortKey ASC, id ASC")
    fun observeCommentsByItemId(itemId: Long): Flow<List<CommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<CommentEntity>)

    @Query("DELETE FROM comments WHERE itemId = :itemId")
    suspend fun deleteCommentsByItemId(itemId: Long)
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE itemId = :itemId ORDER BY position ASC")
    suspend fun getMediaByItemId(itemId: Long): List<MediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaList(mediaList: List<MediaEntity>)

    @Query("DELETE FROM media WHERE itemId = :itemId")
    suspend fun deleteMediaByItemId(itemId: Long)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAllTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemTagCrossRef(crossRef: ItemTagCrossRef)

    @Query("DELETE FROM item_tags WHERE itemId = :itemId")
    suspend fun clearTagsForItem(itemId: Long)
}
