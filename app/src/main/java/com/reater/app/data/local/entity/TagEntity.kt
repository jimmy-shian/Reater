package com.reater.app.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "item_tags",
    primaryKeys = ["itemId", "tagId"],
    foreignKeys = [
        androidx.room3.ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = androidx.room3.ForeignKey.CASCADE
        ),
        androidx.room3.ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = androidx.room3.ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tagId"])
    ]
)
data class ItemTagCrossRef(
    val itemId: Long,
    val tagId: Long
)
