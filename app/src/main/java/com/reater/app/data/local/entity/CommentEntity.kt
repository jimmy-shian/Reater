package com.reater.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Comment: Structure for threaded comments
 */
@Entity(
    tableName = "comments",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["itemId", "externalId"], unique = true),
        Index(value = ["itemId", "sortKey"])
    ]
)
data class CommentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemId: Long,
    val externalId: String,
    val author: String,
    val text: String,
    val likeCount: Int = 0,
    val parentExternalId: String? = null,
    val depth: Int = 0,
    val sortKey: String = "", // e.g. "0:timestamp:001"
    val fetchedAt: Long = System.currentTimeMillis()
)
