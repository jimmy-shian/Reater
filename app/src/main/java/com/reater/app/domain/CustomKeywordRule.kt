package com.reater.app.domain

import kotlinx.serialization.Serializable

/**
 * 客製化特定關鍵字智慧分類規則：
 * 優先度高於一般關鍵字自動分類，當貼文命中特定關鍵字時，直接歸類至指定分類或標籤。
 */
@Serializable
data class CustomKeywordRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val keyword: String,
    val targetType: String = "CATEGORY", // "CATEGORY" 或 "TAG"
    val targetCategoryId: Long? = null,
    val targetCategoryName: String = "",
    val targetTagName: String = "",
    val isEnabled: Boolean = true
)
