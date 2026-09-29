package com.reater.app.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

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
    /** 詳情開啟次數（分析頁：打開/回顧統計用） */
    val openCount: Int = 0,
    /** 最近一次開啟時間（UTC millis，分析頁今日打開用） */
    val lastOpenedAt: Long = 0L,
    val editedAt: Long = System.currentTimeMillis(),
    val editSource: String = "MANUAL_EDIT", // GRAPHQL, SHARE_TEXT, CLIPBOARD, MANUAL_EDIT, AI, SYSTEM
    val dirtyFlag: Boolean = false
)
