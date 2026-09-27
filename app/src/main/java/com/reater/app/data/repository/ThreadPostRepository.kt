package com.reater.app.data.repository

import com.reater.app.data.local.dao.CategoryDao
import com.reater.app.data.local.dao.CommentDao
import com.reater.app.data.local.dao.ItemDao
import com.reater.app.data.local.dao.MediaDao
import com.reater.app.data.local.dao.TagDao
import com.reater.app.data.local.entity.CommentEntity
import com.reater.app.data.local.entity.ItemDetail
import com.reater.app.data.local.entity.ItemEntity
import com.reater.app.data.local.entity.MediaEntity
import com.reater.app.data.local.entity.UserEditEntity
import com.reater.app.data.remote.FetchedPostResult
import com.reater.app.data.remote.ThreadsGraphQLClient
import com.reater.app.domain.OnDeviceClassifier
import com.reater.app.domain.UrlParser
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreadPostRepository @Inject constructor(
    private val itemDao: ItemDao,
    private val commentDao: CommentDao,
    private val mediaDao: MediaDao,
    private val tagDao: TagDao,
    private val categoryDao: CategoryDao,
    private val graphQLClient: ThreadsGraphQLClient,
    private val classifier: OnDeviceClassifier,
    private val settingsRepository: SettingsRepository
) {

    fun observeAllPosts(): Flow<List<ItemDetail>> = itemDao.observeAllItemDetails()
    fun observeUnreadPosts(): Flow<List<ItemDetail>> = itemDao.observeUnreadItemDetails()
    fun observeFavoritePosts(): Flow<List<ItemDetail>> = itemDao.observeFavoriteItemDetails()
    fun searchPosts(query: String): Flow<List<ItemDetail>> = itemDao.searchItemDetails(query)
    fun observePostDetail(id: Long): Flow<ItemDetail?> = itemDao.observeItemDetailById(id)

    suspend fun savePost(
        canonicalUrl: String,
        shortcode: String,
        authorHandle: String,
        bodyText: String,
        commentsText: String,
        manualNote: String,
        manualSummary: String,
        categoryId: Long?,
        fetchedResult: FetchedPostResult? = null
    ): Long {
        // Upsert ItemEntity
        val existing = itemDao.getItemByCanonicalUrl(canonicalUrl)
        val itemId = if (existing != null) {
            val updated = existing.copy(
                shortcode = shortcode,
                authorHandle = authorHandle.ifBlank { existing.authorHandle },
                bodyText = bodyText.ifBlank { existing.bodyText },
                commentsText = commentsText.ifBlank { existing.commentsText },
                sourceVersion = existing.sourceVersion + 1,
                lastFetchStatus = fetchedResult?.status ?: existing.lastFetchStatus,
                lastFetchAt = System.currentTimeMillis()
            )
            itemDao.updateItem(updated)
            existing.id
        } else {
            val newItem = ItemEntity(
                canonicalUrl = canonicalUrl,
                shortcode = shortcode,
                authorHandle = authorHandle,
                authorDisplayName = fetchedResult?.authorDisplayName ?: authorHandle,
                authorProfileUrl = fetchedResult?.authorProfileUrl.orEmpty(),
                authorVerified = fetchedResult?.authorVerified ?: false,
                postedAt = fetchedResult?.postedAt ?: System.currentTimeMillis(),
                bodyText = bodyText,
                commentsText = commentsText,
                likeCount = fetchedResult?.likeCount ?: 0,
                replyCount = fetchedResult?.replyCount ?: 0,
                repostCount = fetchedResult?.repostCount ?: 0,
                lastFetchStatus = fetchedResult?.status ?: "COMPLETE",
                rawJsonMin = fetchedResult?.rawJsonMin.orEmpty()
            )
            itemDao.insertItem(newItem)
        }

        // Auto-classification if categoryId is null
        val resolvedCategoryId = categoryId ?: run {
            val classResult = classifier.classify(
                titleScope = authorHandle + " " + bodyText.take(60),
                bodyScope = bodyText + " " + commentsText
            )
            classResult.categoryId
        }

        // Upsert UserEditEntity
        val existingEdit = itemDao.getUserEditByItemId(itemId)
        val userEdit = existingEdit?.copy(
            manualNote = manualNote.ifBlank { existingEdit.manualNote },
            manualSummary = manualSummary.ifBlank { existingEdit.manualSummary },
            categoryId = resolvedCategoryId ?: existingEdit.categoryId,
            editedAt = System.currentTimeMillis()
        ) ?: UserEditEntity(
            itemId = itemId,
            userBodyOverride = null,
            manualNote = manualNote,
            manualSummary = manualSummary,
            categoryId = resolvedCategoryId,
            isRead = false,
            isFavorite = false
        )
        itemDao.insertUserEdit(userEdit)

        // Insert fetched structured comments
        if (fetchedResult != null && fetchedResult.comments.isNotEmpty()) {
            val commentEntities = fetchedResult.comments.mapIndexed { index, c ->
                CommentEntity(
                    itemId = itemId,
                    externalId = c.externalId.ifBlank { "c_$index" },
                    author = c.author,
                    text = c.text,
                    likeCount = c.likeCount,
                    depth = c.depth,
                    sortKey = "0:${System.currentTimeMillis()}:$index"
                )
            }
            commentDao.insertComments(commentEntities)
        }

        // Insert media attachments
        if (fetchedResult != null && fetchedResult.media.isNotEmpty()) {
            val mediaEntities = fetchedResult.media.mapIndexed { index, m ->
                MediaEntity(
                    itemId = itemId,
                    kind = m.kind,
                    remoteUrl = m.remoteUrl,
                    width = m.width,
                    height = m.height,
                    position = index
                )
            }
            mediaDao.insertMediaList(mediaEntities)
        }

        return itemId
    }

    suspend fun toggleReadStatus(itemId: Long, isRead: Boolean) {
        itemDao.updateReadStatus(itemId, isRead)
    }

    suspend fun toggleFavoriteStatus(itemId: Long, isFavorite: Boolean) {
        itemDao.updateFavoriteStatus(itemId, isFavorite)
    }

    suspend fun deletePost(itemId: Long) {
        itemDao.deleteItemById(itemId)
    }
}
