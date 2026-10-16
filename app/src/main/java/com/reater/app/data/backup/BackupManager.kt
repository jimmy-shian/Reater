package com.reater.app.data.backup

import androidx.room3.withWriteTransaction
import com.reater.app.data.local.AppDatabase
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.data.local.entity.CommentEntity
import com.reater.app.data.local.entity.ItemEntity
import com.reater.app.data.local.entity.ItemTagCrossRef
import com.reater.app.data.local.entity.KeywordEntity
import com.reater.app.data.local.entity.MediaEntity
import com.reater.app.data.local.entity.SavedCollectionEntity
import com.reater.app.data.local.entity.TagEntity
import com.reater.app.data.local.entity.UserEditEntity
import com.reater.app.data.repository.SettingsRepository
import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import com.reater.app.ui.AvatarStorage
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ExportedSettings(
    val themeMode: String = "system",
    val fontScale: Float = 1f,
    val unreadNudgeEnabled: Boolean = true,
    val unreadNudgeDelayMin: Int = 10,
    val reviewDigestEnabled: Boolean = true,
    val reviewDigestHour: Int = 21,
    val selectedModel: String = "gpt-5-nano",
    val customBaseUrl: String = "https://api.openai.com/v1",
    val aiTransmissionConsent: Boolean = false,
    val customAvatarId: String = "life",
    val customAvatarUri: String? = null,
    val isProLicensed: Boolean = false,
    val proLicenseEmail: String? = null,
    val proLicenseCode: String? = null
)

@Serializable
data class ExportedAvatarFile(val name: String, val contentBase64: String)

@Serializable
data class ReaterExportV1(
    val version: String = "v1",
    val fileFormat: String = "reater",
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.2",
    val settings: ExportedSettings? = null,
    val items: List<ExportedItem>,
    val categories: List<ExportedCategory> = emptyList(),
    val collections: List<ExportedCollection> = emptyList(),
    val avatarFiles: List<ExportedAvatarFile> = emptyList(),
    val avatarHistory: List<String> = emptyList(),
    val currentAvatarReference: String? = null
)

@Serializable
data class ExportedCollection(
    val name: String,
    val iconName: String = "life",
    val rulesJson: String,
    val sortOrder: Int = 0,
    val isEnabled: Boolean = true
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
    val aiSummary: String? = null,
    val isRead: Boolean,
    val isFavorite: Boolean,
    val tags: List<String> = emptyList(),
    val comments: List<ExportedComment> = emptyList(),
    val categoryName: String? = null,
    val media: List<ExportedMedia> = emptyList(),
    val topicTag: String = "",
    val isDeleted: Boolean = false,
    val openCount: Int = 0,
    val lastOpenedAt: Long = 0L
)

@Serializable
data class ExportedComment(
    val externalId: String,
    val author: String,
    val text: String,
    val likeCount: Int,
    val parentExternalId: String? = null,
    val depth: Int = 0,
    val sortKey: String = "",
    val mediaJson: String = ""
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
    val avatarIcon: String = "life",
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
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) {

    private val itemDao get() = database.itemDao()
    private val categoryDao get() = database.categoryDao()
    private val proDao get() = database.proDao()

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
        val collections = runCatching { proDao.observeAllCollections().first() }.getOrDefault(emptyList())

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
                aiSummary = detail.userEdit?.aiSummary ?: detail.manualSummary,
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
                        sortKey = it.sortKey,
                        mediaJson = it.mediaJson
                    )
                },
                categoryName = detail.userEdit?.categoryId?.let(categoryNames::get),
                media = detail.media.map {
                    ExportedMedia(it.kind, it.remoteUrl, it.width, it.height, it.position)
                },
                topicTag = detail.item.topicTag,
                isDeleted = detail.item.isDeleted,
                openCount = detail.userEdit?.openCount ?: 0,
                lastOpenedAt = detail.userEdit?.lastOpenedAt ?: 0L
            )
        }

        val exportedCategories = categories.map { category ->
            ExportedCategory(
                name = category.name,
                colorArgb = category.colorArgb,
                sort = category.sort,
                isDefault = category.isDefault,
                avatarIcon = category.avatarIcon,
                keywords = keywordsByCategory[category.id].orEmpty().map {
                    ExportedKeyword(it.term, it.lang, it.weight, it.matchScope)
                }
            )
        }

        val exportedCollections = collections.map { col ->
            ExportedCollection(
                name = col.name,
                iconName = col.iconName,
                rulesJson = col.rulesJson,
                sortOrder = col.sortOrder,
                isEnabled = col.isEnabled
            )
        }

        val currentSettings = runCatching {
            ExportedSettings(
                themeMode = settingsRepository.themeMode.first(),
                fontScale = settingsRepository.fontScale.first(),
                unreadNudgeEnabled = settingsRepository.unreadNudgeEnabled.first(),
                unreadNudgeDelayMin = settingsRepository.unreadNudgeDelayMin.first(),
                reviewDigestEnabled = settingsRepository.reviewDigestEnabled.first(),
                reviewDigestHour = settingsRepository.reviewDigestHour.first(),
                selectedModel = settingsRepository.selectedModel.first(),
                customBaseUrl = settingsRepository.customBaseUrl.first(),
                aiTransmissionConsent = settingsRepository.aiTransmissionConsent.first(),
                customAvatarId = settingsRepository.customAvatarId.first(),
                customAvatarUri = settingsRepository.customAvatarUri.first(),
                isProLicensed = settingsRepository.isProUnlocked.first(),
                proLicenseEmail = settingsRepository.proLicenseEmail.first(),
                proLicenseCode = settingsRepository.proLicenseCode.first()
            )
        }.getOrNull()

        val avatarHistory = AvatarStorage.listHistoryFiles(context)
        val avatarAssets = avatarHistory.flatMap { display ->
            val ts = AvatarStorage.extractTs(display.name)
            listOfNotNull(display, AvatarStorage.pairedOriginalFile(context, display.absolutePath),
                ts?.let { File(AvatarStorage.avatarDir(context), "custom_avatar_crop_$it.txt") }?.takeIf { it.isFile })
        }.distinctBy { it.name }
        require(avatarAssets.sumOf { it.length() } <= MAX_IMPORT_BYTES.toLong() * 3 / 4) {
            "備份含頭像超過 50MB，請先刪除不需要的歷史頭像"
        }
        val exportData = ReaterExportV1(
            items = exportList,
            categories = exportedCategories,
            collections = exportedCollections,
            settings = currentSettings,
            avatarFiles = avatarAssets.map { ExportedAvatarFile(it.name, Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)) },
            avatarHistory = avatarHistory.map { it.name },
            currentAvatarReference = currentSettings?.customAvatarUri?.let { File(it).name }
        )
        val jsonString = json.encodeToString(ReaterExportV1.serializer(), exportData)
        val encoded = jsonString.toByteArray(StandardCharsets.UTF_8)
        require(encoded.size <= MAX_IMPORT_BYTES) { "備份含頭像超過 50MB，請先刪除不需要的歷史頭像" }
        outputStream.write(encoded)
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
                    require(total <= MAX_IMPORT_BYTES) { "僅支援小於 50MB 之 .reater 備份檔" }
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            val content = String(bytes, StandardCharsets.UTF_8)
            val imported = json.decodeFromString(ReaterExportV1.serializer(), content)
            require(imported.fileFormat == "reater") { "非法的 .reater 專屬格式" }
            require(imported.version == "v1") { "不支援的備份版本：${imported.version}" }
            require(imported.items.size <= MAX_IMPORT_ITEMS) { "備份檔項目過多（超過上限）" }
            require(imported.items.all { it.canonicalUrl.isNotBlank() }) { "匯入資料缺少有效 URL" }

            // Validate every asset before changing settings or writing files. Never trust backup paths.
            val assets = imported.avatarFiles.map { asset ->
                require(asset.name == File(asset.name).name && !asset.name.contains('\\') && !asset.name.contains('/')) { "頭像備份檔名無效" }
                require(AvatarStorage.isBackupFileName(asset.name)) { "頭像備份格式無效" }
                val decoded = Base64.decode(asset.contentBase64, Base64.DEFAULT)
                require(decoded.isNotEmpty()) { "頭像備份檔案為空" }
                if (asset.name.endsWith(".txt")) require(AvatarStorage.decodeTransform(decoded.toString(Charsets.UTF_8)) != null) { "頭像裁切資料無效" }
                asset.name to decoded
            }
            require(assets.map { it.first }.distinct().size == assets.size) { "頭像備份檔名重複" }
            val avatarDir = AvatarStorage.avatarDir(context)
            // New ids preserve local history and avoid overwriting an image with an equal timestamp.
            val importedTimes = assets.mapNotNull { AvatarStorage.extractTs(it.first) }.distinct().sorted()
            var nextTs = maxOf(System.currentTimeMillis(),
                avatarDir.listFiles().orEmpty().mapNotNull { AvatarStorage.extractTs(it.name) }.maxOrNull()?.plus(1) ?: 0)
            val timeMap = importedTimes.associateWith { nextTs++ }
            val restoredNames = assets.associate { (name, _) ->
                val ts = AvatarStorage.extractTs(name)
                name to if (ts != null) name.replace(ts.toString(), timeMap.getValue(ts).toString())
                    else "custom_avatar_${nextTs++}.jpg"
            }
            val restoredFiles = mutableListOf<File>()
            try {
                for ((name, bytes) in assets) {
                    val destination = File(avatarDir, restoredNames.getValue(name))
                    check(destination.canonicalFile.parentFile == avatarDir.canonicalFile)
                    destination.writeBytes(bytes)
                    restoredFiles += destination
                }
            } catch (error: Exception) {
                restoredFiles.forEach { it.delete() }
                throw error
            }

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
                        isDefault = category.isDefault,
                        avatarIcon = category.avatarIcon
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

                for (col in imported.collections) {
                    proDao.insertCollection(
                        SavedCollectionEntity(
                            name = col.name,
                            iconName = col.iconName,
                            rulesJson = col.rulesJson,
                            sortOrder = col.sortOrder,
                            isEnabled = col.isEnabled
                        )
                    )
                }

                for (item in imported.items) {
                    require(item.canonicalUrl.isNotBlank()) { "匯入資料缺少有效 URL" }
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
                        topicTag = item.topicTag,
                        commentsText = item.commentsText,
                        mediaJson = existing?.mediaJson ?: "[]",
                        likeCount = existing?.likeCount ?: 0,
                        replyCount = existing?.replyCount ?: 0,
                        repostCount = existing?.repostCount ?: 0,
                        sourceFetchedAt = existing?.sourceFetchedAt ?: System.currentTimeMillis(),
                        sourceVersion = existing?.sourceVersion ?: 1,
                        lastFetchStatus = existing?.lastFetchStatus ?: "NOT_FETCHED",
                        lastFetchAt = existing?.lastFetchAt ?: 0L,
                        rawJsonMin = "",
                        isDeleted = item.isDeleted,
                        deletedAt = if (item.isDeleted) System.currentTimeMillis() else null
                    )
                    val itemId = if (existing == null) itemDao.insertItem(entity) else {
                        itemDao.updateItem(entity)
                        existing.id
                    }
                                        itemDao.insertUserEdit(
                        UserEditEntity(
                            itemId = itemId,
                            userBodyOverride = null,
                            manualNote = item.manualNote,
                            manualSummary = item.manualSummary,
                            aiSummary = item.aiSummary ?: item.manualSummary,
                            categoryId = item.categoryName?.let(importedCategoryIds::get),
                            isRead = item.isRead,
                            isFavorite = item.isFavorite,
                            openCount = item.openCount,
                            lastOpenedAt = item.lastOpenedAt,
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
                            sortKey = comment.sortKey.ifBlank { "0:${index.toString().padStart(8, '0')}" },
                            mediaJson = comment.mediaJson
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
            imported.settings?.let { s ->
                settingsRepository.setThemeMode(s.themeMode)
                settingsRepository.setFontScale(s.fontScale)
                settingsRepository.setUnreadNudgeEnabled(s.unreadNudgeEnabled)
                settingsRepository.setUnreadNudgeDelayMin(s.unreadNudgeDelayMin)
                settingsRepository.setReviewDigestEnabled(s.reviewDigestEnabled)
                settingsRepository.setReviewDigestHour(s.reviewDigestHour)
                settingsRepository.setSelectedModel(s.selectedModel)
                settingsRepository.setCustomBaseUrl(s.customBaseUrl)
                settingsRepository.setAiTransmissionConsent(s.aiTransmissionConsent)
                settingsRepository.setCustomAvatarId(s.customAvatarId)
                val reference = imported.currentAvatarReference ?: s.customAvatarUri?.let { File(it).name }
                val restoredAvatar = reference?.let(restoredNames::get)?.let { File(avatarDir, it) }
                // Old backups carried a device-specific path only; restore it only if it is still readable.
                val legacyAvatar = s.customAvatarUri?.takeIf {
                    val file = File(it)
                    file.canonicalFile.parentFile == avatarDir.canonicalFile && AvatarStorage.isStoredValid(context, it)
                }
                settingsRepository.setCustomAvatarUri(restoredAvatar?.absolutePath ?: legacyAvatar)
                if (s.isProLicensed || !s.proLicenseCode.isNullOrBlank()) {
                    settingsRepository.restoreProLicense(s.proLicenseEmail, s.proLicenseCode)
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
            detail.userEdit?.aiSummary.orEmpty(),
            detail.item.authorHandle,
            detail.item.authorDisplayName,
            detail.tags.joinToString(" ") { it.name },
            detail.comments.joinToString(" ") { it.text }
        ).filter(String::isNotBlank).joinToString(" ")
        itemDao.updateSearchIndex(itemId, searchable)
    }
}
