package com.reater.app.data.local.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * SavedCollection: Smart Collection entity for Pro users
 * Rule json contains matching fields: category, tag, author, read, favorite, hasMedia
 */
@Entity(tableName = "saved_collections")
data class SavedCollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val iconName: String = "life",
    val rulesJson: String, // e.g. {"isRead":false,"minLikes":50}
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * SavedQuery: Advanced Search queries saved for Pro users
 */
@Entity(tableName = "saved_queries")
data class SavedQueryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val filterJson: String,
    val createdAt: Long = System.currentTimeMillis()
)
