package com.reater.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * ItemDetail: Comprehensive view model merging Item, UserEdit, Category, Media, Comments, Tags
 */
data class ItemDetail(
    @Embedded
    val item: ItemEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "itemId"
    )
    val userEdit: UserEditEntity?,

    @Relation(
        parentColumn = "id",
        entityColumn = "itemId"
    )
    val comments: List<CommentEntity> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "itemId"
    )
    val media: List<MediaEntity> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ItemTagCrossRef::class,
            parentColumn = "itemId",
            entityColumn = "tagId"
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
