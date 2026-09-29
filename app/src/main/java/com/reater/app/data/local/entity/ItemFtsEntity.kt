package com.reater.app.data.local.entity

import androidx.room3.Entity
import androidx.room3.Fts5
import androidx.room3.FtsOptions
import androidx.room3.PrimaryKey

/**
 * Manual FTS5 trigram index supports substring search for CJK text.
 */
@Entity(tableName = "items_fts")
@Fts5(tokenizer = FtsOptions.TOKENIZER_TRIGRAM)
data class ItemFtsEntity(
    @PrimaryKey
    val rowid: Long,
    val searchText: String
)
