package com.reater.app.data.local.entity

import androidx.room3.Embedded
import androidx.room3.Junction
import androidx.room3.Relation

/**
 * ItemDetail: Comprehensive view model merging Item, UserEdit, Category, Media, Comments, Tags
 */
data class ItemDetail(
    @Embedded
    val item: ItemEntity,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["itemId"]
    )
    val userEdit: UserEditEntity?,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["itemId"]
    )
    val comments: List<CommentEntity> = emptyList(),

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["itemId"]
    )
    val media: List<MediaEntity> = emptyList(),

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["id"],
        associateBy = Junction(
            value = ItemTagCrossRef::class,
            parentColumns = ["itemId"],
            entityColumns = ["tagId"]
        )
    )
    val tags: List<TagEntity> = emptyList()
) {
    val displayBody: String
        get() = userEdit?.userBodyOverride?.takeIf { it.isNotBlank() } ?: item.bodyText

    val isFavorite: Boolean
        get() = userEdit?.isFavorite ?: false

    val isRead: Boolean
        get() = userEdit?.isRead ?: false

    val manualNote: String
        get() = userEdit?.manualNote.orEmpty()

    val manualSummary: String
        get() = userEdit?.manualSummary.orEmpty()
}
