package com.reater.app.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Keyword: Normalized keywords replacing csv
 */
@Entity(
    tableName = "keywords",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["term"])
    ]
)
data class KeywordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val term: String,
    val lang: String = "ANY", // zh-TW, zh-CN, en, ANY
    val weight: Int = 1, // 1 - 3
    val matchScope: String = "BOTH" // TITLE, BODY, BOTH
)
