package com.reater.app.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.reater.app.data.local.entity.SavedCollectionEntity
import com.reater.app.data.local.entity.SavedQueryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProDao {
    @Query("SELECT * FROM saved_collections ORDER BY sortOrder ASC, id ASC")
    fun observeAllCollections(): Flow<List<SavedCollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: SavedCollectionEntity): Long

    @Query("DELETE FROM saved_collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)

    @Query("SELECT * FROM saved_queries ORDER BY createdAt DESC")
    fun observeAllSavedQueries(): Flow<List<SavedQueryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedQuery(query: SavedQueryEntity): Long

    @Query("DELETE FROM saved_queries WHERE id = :id")
    suspend fun deleteSavedQuery(id: Long)
}
