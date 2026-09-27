package com.reater.app.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * ItemFts: Full text search table
 * Using Fts4 compatible with default SQLite in minSdk 26+ Android
 */
@Entity(tableName = "items_fts")
@Fts4(contentEntity = ItemEntity::class)
data class ItemFtsEntity(
    @PrimaryKey
    val rowid: Long,
    val bodyText: String,
    val commentsText: String,
    val authorHandle: String,
    val authorDisplayName: String
)
