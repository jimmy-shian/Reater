package com.reater.app.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "ai_runs",
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
data class AiRunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemId: Long,
    val purpose: String, // CLASSIFY, SUMMARIZE
    val model: String,
    val promptVersion: String,
    val inputHash: String, // SHA-256
    val outputJson: String,
    val usageIn: Int = 0,
    val usageOut: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "fetch_attempts",
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
data class FetchAttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemId: Long,
    val startedAt: Long = System.currentTimeMillis(),
    val status: String, // SUCCESS, FAILED, RUNNING
    val httpCode: Int = 0,
    val errorKind: String = "",
    val pageCursor: String = ""
)
