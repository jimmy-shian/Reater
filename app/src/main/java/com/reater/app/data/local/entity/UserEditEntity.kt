package com.reater.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * UserEdit: User edits separated from source data
 */
@Entity(
    tableName = "user_edits",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["itemId"], unique = true)
    ]
)
data class UserEditEntity(
    @PrimaryKey
    val itemId: Long,
    val userBodyOverride: String? = null,
    val manualNote: String = "",
    val manualSummary: String = "",
    val categoryId: Long? = null,
    val isRead: Boolean = false,
    val isFavorite: Boolean = false,
    val editedAt: Long = System.currentTimeMillis(),
    val editSource: String = "MANUAL_EDIT", // GRAPHQL, SHARE_TEXT, CLIPBOARD, MANUAL_EDIT, AI, SYSTEM
    val dirtyFlag: Boolean = false
)
