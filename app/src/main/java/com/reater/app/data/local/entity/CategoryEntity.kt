package com.reater.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Category: Category entity
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val sort: Int = 0,
    val isDefault: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
