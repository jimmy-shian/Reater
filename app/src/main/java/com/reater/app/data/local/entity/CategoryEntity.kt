package com.reater.app.data.local.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

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
    val avatarIcon: String = "life",
    val updatedAt: Long = System.currentTimeMillis()
)
