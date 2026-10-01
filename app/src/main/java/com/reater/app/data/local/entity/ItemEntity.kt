package com.reater.app.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Item: Source snapshot layer (never directly overwritten by user edits)
 */
@Entity(
    tableName = "items",
    indices = [
        Index(value = ["canonicalUrl"], unique = true),
        Index(value = ["shortcode"])
    ]
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val canonicalUrl: String,
    val shortcode: String,
    val authorHandle: String,
    val authorDisplayName: String,
    val authorProfileUrl: String,
    val authorVerified: Boolean = false,
    val postedAt: Long = 0L, // UTC epoch millis
    val postedAtRaw: String = "",
    val bodyText: String = "",
    /** Threads 主題標籤（topic pill；抓不到時為內文首個 hashtag；供同主題預選分類用） */
    val topicTag: String = "",
    val commentsText: String = "",
    val mediaJson: String = "[]",
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val repostCount: Int = 0,
    val sourceFetchedAt: Long = System.currentTimeMillis(),
    val sourceVersion: Int = 1,
    val lastFetchStatus: String = "NOT_FETCHED", // COMPLETE, PARTIAL, FAILED, NOT_FETCHED
    val lastFetchAt: Long = System.currentTimeMillis(),
    val rawJsonMin: String = "",
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)
