package com.reater.app.data.backup

import androidx.room3.withWriteTransaction
import com.reater.app.data.local.AppDatabase
import com.reater.app.data.local.entity.CommentEntity
import com.reater.app.data.local.entity.ItemEntity
import com.reater.app.data.local.entity.ItemTagCrossRef
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.data.local.entity.KeywordEntity
import com.reater.app.data.local.entity.MediaEntity
import com.reater.app.data.local.entity.TagEntity
import com.reater.app.data.local.entity.UserEditEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ReaterExportV1(
    val version: String = "v1",
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0",
    val items: List<ExportedItem>,
    val categories: List<ExportedCategory> = emptyList()
)

@Serializable
data class ExportedItem(
    val canonicalUrl: String,
    val shortcode: String,
    val authorHandle: String,
    val authorDisplayName: String,
    val postedAt: Long,
    val bodyText: String,
    val commentsText: String,
    val manualNote: String,
    val manualSummary: String,
    val isRead: Boolean,
    val isFavorite: Boolean,
    val tags: List<String> = emptyList(),
    val comments: List<ExportedComment> = emptyList(),
    val categoryName: String? = null,
    val media: List<ExportedMedia> = emptyList()
)

@Serializable
data class ExportedComment(
    val externalId: String,
    val author: String,
    val text: String,
    val likeCount: Int,
    val parentExternalId: String? = null,
    val depth: Int = 0,
    val sortKey: String = ""
)

@Serializable
data class ExportedMedia(
    val kind: String,
    val remoteUrl: String,
    val width: Int = 0,
    val height: Int = 0,
    val position: Int = 0
)

@Serializable
data class ExportedCategory(
    val name: String,
    val colorArgb: Int,
    val sort: Int,
    val isDefault: Boolean,
    val keywords: List<ExportedKeyword> = emptyList()
)

@Serializable
data class ExportedKeyword(
    val term: String,
    val lang: String,
    val weight: Int,
    val matchScope: String
)

@Singleton
class BackupManager @Inject constructor(
    private val database: AppDatabase
) {

    private val itemDao get() = database.itemDao()
    private val categoryDao get() = database.categoryDao()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Exports full dataset into JSON schema v1 without sensitive Keys or rawJson.
     */
    suspend fun exportToJson(outputStream: OutputStream) {
        val details = itemDao.observeAllItemDetails().first()
        val categories = categoryDao.getAllCategories()
        val keywordsByCategory = categoryDao.getAllKeywords().groupBy { it.categoryId }
        val categoryNames = categories.associate { it.id to it.name }
        val exportList = details.map { detail ->
            ExportedItem(
                canonicalUrl = detail.item.canonicalUrl,
                shortcode = detail.item.shortcode,
                authorHandle = detail.item.authorHandle,
                authorDisplayName = detail.item.authorDisplayName,
                postedAt = detail.item.postedAt,
                bodyText = detail.displayBody,
                commentsText = detail.item.commentsText,
                manualNote = detail.manualNote,
                manualSummary = detail.manualSummary,
                isRead = detail.isRead,
                isFavorite = detail.isFavorite,
                tags = detail.tags.map { it.name },
                comments = detail.comments.map {
                    ExportedComment(
                        externalId = it.externalId,
                        author = it.author,
                        text = it.text,
                        likeCount = it.likeCount,
                        parentExternalId = it.parentExternalId,
                        depth = it.depth,
                        sortKey = it.sortKey
                    )
                },
                categoryName = detail.userEdit?.categoryId?.let(categoryNames::get),
                media = detail.media.map {
                    ExportedMedia(it.kind, it.remoteUrl, it.width, it.height, it.position)
                }
            )
        }

        val exportedCategories = categories.map { category ->
            ExportedCategory(
                name = category.name,
                colorArgb = category.colorArgb,
                sort = category.sort,
                isDefault = category.isDefault,
                keywords = keywordsByCategory[category.id].orEmpty().map {
                    ExportedKeyword(it.term, it.lang, it.weight, it.matchScope)
                }
            )
        }
        val exportData = ReaterExportV1(items = exportList, categories = exportedCategories)
        val jsonString = json.encodeToString(ReaterExportV1.serializer(), exportData)
        outputStream.write(jsonString.toByteArray(StandardCharsets.UTF_8))
        outputStream.flush()
    }

    /**
     * Lossy CSV export for spreadsheets.
     */
    suspend fun exportToCsv(outputStream: OutputStream) {
        val details = itemDao.observeAllItemDetails().first()
        val writer = outputStream.bufferedWriter(StandardCharsets.UTF_8)

        // CSV Header
        writer.write("CanonicalUrl,Author,PostedAt,Body,Comments,Note,Summary,Tags,IsRead,IsFavorite\n")

        details.forEach { d ->
            val fields = listOf(
                escapeCsv(d.item.canonicalUrl),
                escapeCsv(d.item.authorHandle),
                escapeCsv(d.item.postedAt.toString()),
                escapeCsv(d.displayBody),
                escapeCsv(d.item.commentsText),
                escapeCsv(d.manualNote),
                escapeCsv(d.manualSummary),
                escapeCsv(d.tags.joinToString(";") { it.name }),
                escapeCsv(d.isRead.toString()),
                escapeCsv(d.isFavorite.toString())
            )
            writer.write(fields.joinToString(",") + "\n")
        }
        writer.flush()
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    /**
     * Imports JSON schema v1 file safely with verification.
     */
    suspend fun importFromJson(inputStream: InputStream): Result<Int> {
        return try {
            val bytes = inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_IMPORT_BYTES) { "Backup exceeds the 50 MiB import limit" }
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            val content = String(bytes, StandardCharsets.UTF_8)
            val imported = json.decodeFromString(ReaterExportV1.serializer(), content)
            require(imported.version == "v1") { "Unsupported backup version: ${imported.version}" }
            require(imported.items.size <= MAX_IMPORT_ITEMS) { "Backup contains too many items" }
            var count = 0
            database.withWriteTransaction {
                val itemDao = database.itemDao()
                val importedCategoryIds = mutableMapOf<String, Long>()
                for (category in imported.categories) {
                    val existingCategory = categoryDao.getCategoryByName(category.name)
                    val saved = CategoryEntity(
                        id = existingCategory?.id ?: 0,
                        name = category.name,
                        colorArgb = category.colorArgb,
                        sort = category.sort,
                        isDefault = category.isDefault
                    )
                    val categoryId = if (existingCategory == null) categoryDao.insertCategory(saved) else {
                        categoryDao.insertCategory(saved)
                        existingCategory.id
                    }
                    categoryDao.clearKeywordsForCategory(categoryId)
                    categoryDao.insertKeywords(category.keywords.map {
                        KeywordEntity(categoryId = categoryId, term = it.term, lang = it.lang, weight = it.weight, matchScope = it.matchScope)
                    })
                    importedCategoryIds[category.name] = categoryId
                }
                for (item in imported.items) {
                    require(item.canonicalUrl.isNotBlank()) { "Backup contains an item without a URL" }
                    val existing = itemDao.getItemByCanonicalUrl(item.canonicalUrl)
                    val entity = ItemEntity(
                        id = existing?.id ?: 0,
                        canonicalUrl = item.canonicalUrl,
                        shortcode = item.shortcode,
                        authorHandle = item.authorHandle,
                        authorDisplayName = item.authorDisplayName,
                        authorProfileUrl = existing?.authorProfileUrl.orEmpty(),
                        authorVerified = existing?.authorVerified ?: false,
                        postedAt = item.postedAt,
                        postedAtRaw = existing?.postedAtRaw.orEmpty(),
                        bodyText = item.bodyText,
                        commentsText = item.commentsText,
                        mediaJson = existing?.mediaJson ?: "[]",
                        likeCount = existing?.likeCount ?: 0,
                        replyCount = existing?.replyCount ?: 0,
                        repostCount = existing?.repostCount ?: 0,
                        sourceFetchedAt = existing?.sourceFetchedAt ?: System.currentTimeMillis(),
                        sourceVersion = existing?.sourceVersion ?: 1,
                        lastFetchStatus = existing?.lastFetchStatus ?: "NOT_FETCHED",
                        lastFetchAt = existing?.lastFetchAt ?: 0L,
                        rawJsonMin = ""
                    )
                    val itemId = if (existing == null) itemDao.insertItem(entity) else {
                        itemDao.updateItem(entity)
                        existing.id
                    }
                    val oldEdit = itemDao.getUserEditByItemId(itemId)
                    itemDao.insertUserEdit(
                        UserEditEntity(
                            itemId = itemId,
                            userBodyOverride = null,
                            manualNote = item.manualNote,
                            manualSummary = item.manualSummary,
                            categoryId = item.categoryName?.let(importedCategoryIds::get) ?: oldEdit?.categoryId,
                            isRead = item.isRead,
                            isFavorite = item.isFavorite,
                            editedAt = System.currentTimeMillis(),
                            editSource = "IMPORT",
                            dirtyFlag = false
                        )
                    )
                    database.commentDao().deleteCommentsByItemId(itemId)
                    database.commentDao().insertComments(item.comments.mapIndexed { index, comment ->
                        CommentEntity(
                            itemId = itemId,
                            externalId = comment.externalId.ifBlank { "import_$index" },
                            author = comment.author,
                            text = comment.text,
                            likeCount = comment.likeCount,
                            parentExternalId = comment.parentExternalId,
                            depth = comment.depth,
                            sortKey = comment.sortKey.ifBlank { "0:${index.toString().padStart(8, '0')}" }
                        )
                    })
                    database.mediaDao().deleteMediaByItemId(itemId)
                    val restoredMedia = item.media.map {
                        MediaEntity(itemId = itemId, kind = it.kind, remoteUrl = it.remoteUrl, width = it.width, height = it.height, position = it.position)
                    }
                    if (restoredMedia.isNotEmpty()) database.mediaDao().insertMediaList(restoredMedia)
                    val tagDao = database.tagDao()
                    tagDao.clearTagsForItem(itemId)
                    for (name in item.tags.map(String::trim).filter(String::isNotEmpty).distinctBy { it.lowercase() }) {
                        val tagId = tagDao.getTagByName(name)?.id ?: tagDao.insertTag(TagEntity(name = name)).let { inserted ->
                            if (inserted >= 0) inserted else tagDao.getTagByName(name)?.id
                        }
                        if (tagId != null && tagId > 0) tagDao.insertItemTagCrossRef(ItemTagCrossRef(itemId, tagId))
                    }
                    refreshSearchIndex(itemId)
                    count++
                }
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 50 * 1024 * 1024
        const val MAX_IMPORT_ITEMS = 100_000
    }

    private suspend fun refreshSearchIndex(itemId: Long) {
        val detail = itemDao.getItemDetailById(itemId) ?: return
        val searchable = listOf(
            detail.item.bodyText,
            detail.item.commentsText,
            detail.userEdit?.userBodyOverride.orEmpty(),
            detail.manualNote,
            detail.manualSummary,
            detail.item.authorHandle,
            detail.item.authorDisplayName,
            detail.tags.joinToString(" ") { it.name },
            detail.comments.joinToString(" ") { it.text }
        ).filter(String::isNotBlank).joinToString(" ")
        itemDao.updateSearchIndex(itemId, searchable)
    }
}
