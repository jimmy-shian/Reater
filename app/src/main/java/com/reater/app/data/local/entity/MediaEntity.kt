package com.reater.app.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Media: Attachment items (single image, carousel, video, link card, etc.)
 */
@Entity(
    tableName = "media",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["itemId"])
    ]
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemId: Long,
    val kind: String, // TEXT, IMAGE, VIDEO, CAROUSEL, QUOTE, REPOST, LINK
    val remoteUrl: String,
    val localPath: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val position: Int = 0
)
