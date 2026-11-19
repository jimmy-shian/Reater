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
import com.reater.app.R
import java.io.File
import java.io.BufferedInputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.CancellationException
import com.reater.app.data.remote.FetchedMediaJson
import com.reater.app.data.local.entity.SavedQueryEntity
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
    val autoFetchEnabled: Boolean = true,
    val lastCategoryName: String? = null,
    val isProLicensed: Boolean = false,
    val proLicenseEmail: String? = null,
    val proLicenseCode: String? = null,
    val aiAnalyzeOnSave: Boolean = false
)

@Serializable
data class ExportedAvatarFile(val name: String, val contentBase64: String)

@Serializable
data class ExportedAsset(val path: String, val size: Long, val sha256: String)

@Serializable
data class ExportedQuery(val name: String, val filterJson: String, val createdAt: Long)

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
    val currentAvatarReference: String? = null,
    val assets: List<ExportedAsset> = emptyList(),
    val queries: List<ExportedQuery> = emptyList()
)

@Serializable
data class ExportedCollection(
    val name: String,
    val iconName: String = "life",
    val rulesJson: String,
    val sortOrder: Int = 0,
    val isEnabled: Boolean = true,
    val createdAt: Long? = null
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
    val lastOpenedAt: Long = 0L,
    val authorProfileUrl: String = "",
    val authorVerified: Boolean = false,
    val postedAtRaw: String = "",
    val mediaJson: String = "[]",
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val repostCount: Int = 0,
    val sourceFetchedAt: Long? = null,
    val sourceVersion: Int = 1,
    val lastFetchStatus: String = "NOT_FETCHED",
    val lastFetchAt: Long = 0,
    val rawJsonMin: String = "",
    /** THREADS（預設，舊備份）/ INSTAGRAM */
    val platform: String = com.reater.app.domain.UrlParser.PLATFORM_THREADS,
    val deletedAt: Long? = null,
    val userBodyOverride: String? = null,
    val editedAt: Long? = null,
    val editSource: String = "IMPORT",
    val dirtyFlag: Boolean = false,
    val aiRuns: List<com.reater.app.data.local.entity.AiRunEntity> = emptyList(),
    val fetchAttempts: List<com.reater.app.data.local.entity.FetchAttemptEntity> = emptyList()
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
    val mediaJson: String = "",
    val authorProfileUrl: String = "",
    val authorProfileLocalPath: String = "",
    val fetchedAt: Long = 0L
)

@Serializable
data class ExportedMedia(
    val kind: String,
    val remoteUrl: String,
    val width: Int = 0,
    val height: Int = 0,
    val position: Int = 0,
    val archivePath: String = ""
)

@Serializable
data class ExportedCategory(
    val name: String,
    val colorArgb: Int,
    val sort: Int,
    val isDefault: Boolean,
    val avatarIcon: String = "life",
    val originalId: Long? = null,
    val updatedAt: Long? = null,
    val keywords: List<ExportedKeyword> = emptyList()
)

@Serializable
data class ExportedKeyword(
    val term: String,
    val lang: String,
    val weight: Int,
    val matchScope: String
)

data class BackupProgress(
    val current: Int,
    val total: Int,
    val stage: String,
    val detail: String = "",
    val estimatedRemainingSeconds: Long? = null
) {
    val fraction: Float
        get() = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    /** 供 UI 顯示的 ETA 文字（需 Context 解析字串資源） */
    fun etaText(context: Context): String? = estimatedRemainingSeconds?.let { sec ->
        val (res, args) = etaFormatSelection(sec)
        context.getString(res, *args.toTypedArray())
    }
}

/** ETA 分支選擇（純邏輯：字串資源 ID + 格式參數），供 BackupProgress.etaText 與單元測試使用 */
internal fun etaFormatSelection(seconds: Long): Pair<Int, LongArray> = if (seconds <= 0L) {
    R.string.backup_progress_almost_done to LongArray(0)
} else if (seconds < 60L) {
    R.string.backup_eta_seconds to longArrayOf(seconds)
} else {
    val mins = seconds / 60
    val remSec = seconds % 60
    if (remSec == 0L) R.string.backup_eta_minutes to longArrayOf(mins)
    else R.string.backup_eta_min_sec to longArrayOf(mins, remSec)
}

class EtaEstimator(val total: Int) {
    private val startTimeNs = System.nanoTime()

    fun estimateRemainingSeconds(current: Int): Long? {
        if (current <= 0 || total <= 0) return null
        if (current >= total) return 0L
        val elapsedSec = (System.nanoTime() - startTimeNs) / 1_000_000_000.0
        if (elapsedSec < 0.2) return null
        val rate = current / elapsedSec
        if (rate <= 0.0) return null
        val remaining = (total - current) / rate
        return remaining.toLong().coerceAtLeast(1L)
    }
}

@Singleton
class BackupManager @Inject constructor(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val graphQLClient: com.reater.app.data.remote.ThreadsGraphQLClient,
    private val mediaDownloader: com.reater.app.data.remote.MediaDownloader,
    @ApplicationContext private val context: Context
) {

    private val itemDao get() = database.itemDao()
    private val categoryDao get() = database.categoryDao()
    private val proDao get() = database.proDao()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Exports a v2 ZIP container with a JSON manifest and binary assets. API keys remain device-local.
     */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun exportToJson(
        outputStream: OutputStream,
        onProgress: (BackupProgress) -> Unit = {}
    ) {
        val staging = File(context.cacheDir, "backup_export_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val archiveFiles = linkedMapOf<String, File>()
            val mediaBySource = mutableMapOf<String, String>()
            val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()
            fun packMedia(remote: String, local: String, kind: String): String {
                val key = local.ifBlank { remote }
                if (key.isBlank()) return ""
                mediaBySource[key]?.takeIf { it.isNotBlank() }?.let { return it }

                // Check local candidates
                val localFileCandidates = listOfNotNull(
                    local.takeIf { it.isNotBlank() }?.let(::File),
                    local.takeIf { it.isNotBlank() }?.let { File(context.filesDir, it) },
                    local.takeIf { it.isNotBlank() }?.let { File(context.cacheDir, it) }
                )
                val source = localFileCandidates.firstOrNull { it.isFile && it.length() > 0 }
                    ?: if (remote.isNotBlank()) {
                        runCatching {
                            coil.Coil.imageLoader(context).diskCache?.openSnapshot(remote)?.use { snapshot ->
                                snapshot.data.toFile().takeIf { it.isFile && it.length() > 0 }
                            }
                        }.getOrNull()
                    } else null

                val knownExtensions = setOf("mp4", "mov", "webm", "m4v", "jpg", "jpeg", "png", "webp", "gif", "avif")
                val extension = File(local.ifBlank { remote.substringBefore('?') }).extension.lowercase()
                val ext = extension.takeIf { it in knownExtensions }
                    ?: if (kind.equals("VIDEO", true) || kind.equals("AUDIO", true)) "mp4" else "jpg"
                val name = "media/${archiveFiles.size}.$ext"
                val destination = File(staging, name).apply { parentFile?.mkdirs() }
                val remaining = ReaterArchive.MAX_ARCHIVE_BYTES - archiveFiles.values.sumOf { it.length() }

                if (source != null) {
                    try {
                        source.inputStream().use { input ->
                            destination.outputStream().use { ReaterArchive.copyBounded(input, it, remaining) }
                        }
                    } catch (_: Exception) {
                        destination.delete()
                    }
                } else if (remote.startsWith("https://") || remote.startsWith("http://")) {
                    try {
                        val req = Request.Builder()
                            .url(remote)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                            .header("Referer", "https://www.threads.com/")
                            .header("Accept", "image/avif,image/webp,image/*,video/*,*/*;q=0.8")
                            .build()
                        client.newCall(req).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body
                                if (body != null) {
                                    val type = body.contentType()?.toString().orEmpty()
                                    if (!type.contains("text/html") && !type.contains("application/json")) {
                                        body.byteStream().use { input ->
                                            destination.outputStream().use { ReaterArchive.copyBounded(input, it, remaining) }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        destination.delete()
                    }
                }

                if (destination.isFile && destination.length() > 0) {
                    archiveFiles[name] = destination
                    mediaBySource[key] = name
                    return name
                } else {
                    destination.delete()
                    mediaBySource[key] = ""
                    return ""
                }
            }
            val details = itemDao.getBackupItemDetails()
            val totalItems = details.size.coerceAtLeast(1)
            val exportEta = EtaEstimator(totalItems)
            onProgress(BackupProgress(0, totalItems, context.getString(R.string.backup_progress_preparing), context.getString(R.string.backup_progress_items_detail, details.size)))
            val categories = categoryDao.getAllCategories()
            val keywordsByCategory = categoryDao.getAllKeywords().groupBy { it.categoryId }
            val categoryNames = categories.associate { it.id to it.name }
            val collections = proDao.observeAllCollections().first()

            val exportList = details.mapIndexed { idx, detail ->
                val currentIdx = idx + 1
                val handleLabel = "@${detail.item.authorHandle.ifBlank { detail.item.shortcode }}"
                val etaSec = exportEta.estimateRemainingSeconds(currentIdx)
                onProgress(BackupProgress(currentIdx, totalItems, context.getString(R.string.backup_progress_packing, currentIdx, details.size), handleLabel, etaSec))

                var packedMediaList = detail.media.map {
                    ExportedMedia(it.kind, it.remoteUrl, it.width, it.height, it.position, packMedia(it.remoteUrl, it.localPath, it.kind))
                }
                var packedAuthorProfile = detail.item.authorProfileUrl.let { url ->
                    if (url.isBlank()) ""
                    else {
                        val packed = packMedia(if (url.startsWith("http")) url else "", if (url.startsWith("http")) "" else url, "IMAGE")
                        if (packed.isNotBlank()) packed else url
                    }
                }
                // 若匯出時發現媒體缺檔（例如舊 CDN URL 已過期且無本地檔）或未抓取，自動向 Threads 重新抓取補齊
                val needsRefetch = (packedMediaList.isNotEmpty() && packedMediaList.any { it.archivePath.isBlank() }) ||
                    (packedMediaList.isEmpty() && detail.item.lastFetchStatus == "NOT_FETCHED")
                if (needsRefetch && (detail.item.canonicalUrl.contains("threads.") || detail.item.shortcode.isNotBlank())) {
                    onProgress(BackupProgress(currentIdx, totalItems, context.getString(R.string.backup_progress_refetching_media, currentIdx, details.size), handleLabel, etaSec))
                    val fresh = graphQLClient.fetchPostByPostIdOrShortcode(
                        detail.item.canonicalUrl, detail.item.shortcode
                    ).getOrNull()
                    if (fresh != null) {
                        val chainMedia = fresh.parentChain.flatMap { it.media }
                        val freshCombined = com.reater.app.data.remote.MediaDedup.distinctFetched(
                            (chainMedia + fresh.parentMedia) + fresh.media
                        )
                        if (freshCombined.isNotEmpty()) {
                            packedMediaList = freshCombined.mapIndexed { mIdx, fm ->
                                ExportedMedia(
                                    kind = fm.kind,
                                    remoteUrl = fm.remoteUrl,
                                    width = fm.width,
                                    height = fm.height,
                                    position = mIdx,
                                    archivePath = packMedia(fm.remoteUrl, fm.localPath, fm.kind)
                                )
                            }
                        }
                        if ((packedAuthorProfile.isBlank() || packedAuthorProfile.startsWith("http")) && fresh.authorProfileUrl.isNotBlank()) {
                            val p = packMedia(fresh.authorProfileUrl, "", "IMAGE")
                            if (p.isNotBlank()) packedAuthorProfile = p
                        }
                    }
                }

                ExportedItem(
                    canonicalUrl = detail.item.canonicalUrl,
                    shortcode = detail.item.shortcode,
                    authorHandle = detail.item.authorHandle,
                    authorDisplayName = detail.item.authorDisplayName,
                    postedAt = detail.item.postedAt,
                    bodyText = detail.item.bodyText,
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
                            authorProfileUrl = it.authorProfileUrl,
                            authorProfileLocalPath = packMedia(it.authorProfileUrl, it.authorProfileLocalPath, "IMAGE"),
                            mediaJson = FetchedMediaJson.encode(FetchedMediaJson.decode(it.mediaJson).map { media ->
                                media.copy(localPath = packMedia(media.remoteUrl, media.localPath, media.kind))
                            }),
                            fetchedAt = it.fetchedAt
                        )
                    },
                    categoryName = detail.userEdit?.categoryId?.let(categoryNames::get),
                    media = packedMediaList,
                    topicTag = detail.item.topicTag,
                    isDeleted = detail.item.isDeleted,
                    openCount = detail.userEdit?.openCount ?: 0,
                    lastOpenedAt = detail.userEdit?.lastOpenedAt ?: 0L,
                    authorProfileUrl = packedAuthorProfile,
                    authorVerified = detail.item.authorVerified,
                    postedAtRaw = detail.item.postedAtRaw,
                    mediaJson = detail.item.mediaJson,
                    likeCount = detail.item.likeCount,
                    replyCount = detail.item.replyCount,
                    repostCount = detail.item.repostCount,
                    sourceFetchedAt = detail.item.sourceFetchedAt,
                    sourceVersion = detail.item.sourceVersion,
                    lastFetchStatus = detail.item.lastFetchStatus,
                    lastFetchAt = detail.item.lastFetchAt,
                    rawJsonMin = detail.item.rawJsonMin,
                    platform = detail.item.platform,
                    deletedAt = detail.item.deletedAt,
                    userBodyOverride = detail.userEdit?.userBodyOverride,
                    editedAt = detail.userEdit?.editedAt,
                    editSource = detail.userEdit?.editSource ?: "IMPORT",
                    dirtyFlag = detail.userEdit?.dirtyFlag ?: false,
                    aiRuns = database.aiRunDao().getBackupRuns(detail.item.id),
                    fetchAttempts = database.aiRunDao().getBackupAttempts(detail.item.id)
                )
            }

            val exportedCategories = categories.map { category ->
                ExportedCategory(
                    name = category.name,
                    colorArgb = category.colorArgb,
                    sort = category.sort,
                    isDefault = category.isDefault,
                    avatarIcon = category.avatarIcon,
                    originalId = category.id,
                    updatedAt = category.updatedAt,
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
                    isEnabled = col.isEnabled,
                    createdAt = col.createdAt
                )
            }

            val currentSettings = ExportedSettings(
                    themeMode = settingsRepository.themeMode.first(),
                    fontScale = settingsRepository.fontScale.first(),
                    unreadNudgeEnabled = settingsRepository.unreadNudgeEnabled.first(),
                    unreadNudgeDelayMin = settingsRepository.unreadNudgeDelayMin.first(),
                    reviewDigestEnabled = settingsRepository.reviewDigestEnabled.first(),
                    reviewDigestHour = settingsRepository.reviewDigestHour.first(),
                    selectedModel = settingsRepository.selectedModel.first(),
                    customBaseUrl = settingsRepository.customBaseUrl.first(),
                    aiTransmissionConsent = settingsRepository.aiTransmissionConsent.first(),
                    aiAnalyzeOnSave = settingsRepository.aiAnalyzeOnSave.first(),
                    customAvatarId = settingsRepository.customAvatarId.first(),
                    customAvatarUri = settingsRepository.customAvatarUri.first(),
                    isProLicensed = settingsRepository.isProUnlocked.first(),
                    proLicenseEmail = settingsRepository.proLicenseEmail.first(),
                    proLicenseCode = settingsRepository.proLicenseCode.first(),
                    autoFetchEnabled = settingsRepository.autoFetchEnabled.first(),
                    lastCategoryName = settingsRepository.lastSelectedCategoryId.first()?.let(categoryNames::get)
                )

            val avatarHistory = AvatarStorage.listHistoryFiles(context)
            val currentAvatarFile = currentSettings.customAvatarUri?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile && it.length() > 0 }
            val avatarAssets = (avatarHistory + listOfNotNull(currentAvatarFile)).flatMap { display ->
                val ts = AvatarStorage.extractTs(display.name)
                listOfNotNull(display, AvatarStorage.pairedOriginalFile(context, display.absolutePath),
                    ts?.let { File(AvatarStorage.avatarDir(context), "custom_avatar_crop_$it.txt") }?.takeIf { it.isFile })
            }.filter { it.isFile && it.length() > 0 }.distinctBy { it.name }
            avatarAssets.forEach { source ->
                val name = "avatars/${source.name}"
                val copy = File(staging, name).apply { parentFile?.mkdirs() }
                source.copyTo(copy, overwrite = true)
                archiveFiles[name] = copy
            }
            val currentAvatarReference = currentSettings.customAvatarUri?.let { File(it).name }?.takeIf { ref ->
                avatarAssets.any { it.name == ref }
            }
            val exportData = ReaterExportV1(
                version = "v2",
                items = exportList,
                categories = exportedCategories,
                collections = exportedCollections,
                settings = if (currentAvatarReference == null && currentSettings.customAvatarUri != null) currentSettings.copy(customAvatarUri = null) else currentSettings,
                assets = archiveFiles.map { (path, file) -> ExportedAsset(path, file.length(), ReaterArchive.sha256(file)) },
                queries = proDao.observeAllSavedQueries().first().map { ExportedQuery(it.name, it.filterJson, it.createdAt) },
                avatarHistory = avatarHistory.map { it.name },
                currentAvatarReference = currentAvatarReference
            )
            onProgress(BackupProgress(totalItems, totalItems, context.getString(R.string.backup_progress_packaging), context.getString(R.string.backup_progress_packaging_detail, archiveFiles.size), 0L))
            val jsonString = json.encodeToString(ReaterExportV1.serializer(), exportData)
            val encoded = jsonString.toByteArray(StandardCharsets.UTF_8)
            val tempArchive = File(staging, "archive.reater")
            tempArchive.outputStream().buffered(64 * 1024).use { fileOut ->
                ReaterArchive.write(fileOut, encoded, archiveFiles)
            }
            check(tempArchive.exists() && tempArchive.length() > 0) { context.getString(R.string.backup_error_empty_file) }
            tempArchive.inputStream().buffered(64 * 1024).use { fileIn ->
                fileIn.copyTo(outputStream, bufferSize = 64 * 1024)
            }
            outputStream.flush()
        } finally {
            staging.deleteRecursively()
        }
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
     * Imports verified v2 containers and legacy v1 JSON backups.
     */
    suspend fun importFromJson(
        inputStream: InputStream,
        onProgress: (BackupProgress) -> Unit = {}
    ): Result<Int> {
        val staging = File(context.cacheDir, "backup_import_${UUID.randomUUID()}").apply { mkdirs() }
        val restoredFiles = mutableListOf<File>()
        var databaseCommitted = false
        return try {
            onProgress(BackupProgress(0, 1, context.getString(R.string.backup_progress_reading)))
            val tempSourceFile = File(staging, "import_source.tmp")
            tempSourceFile.outputStream().buffered(64 * 1024).use { out ->
                inputStream.copyTo(out, bufferSize = 64 * 1024)
            }
            require(tempSourceFile.exists() && tempSourceFile.length() > 0) {
                context.getString(R.string.backup_error_file_empty)
            }

            val isZip = tempSourceFile.inputStream().buffered().use { stream ->
                stream.read() == 0x50 && stream.read() == 0x4b
            }
            if (isZip) {
                onProgress(BackupProgress(0, 1, context.getString(R.string.backup_progress_extracting)))
            }
            val packed = if (isZip) {
                tempSourceFile.inputStream().buffered(64 * 1024).use { ReaterArchive.read(it, staging) }
            } else emptyMap()

            val content = if (isZip) {
                val manifestFile = packed[ReaterArchive.MANIFEST] ?: error(context.getString(R.string.backup_error_missing_manifest_file))
                manifestFile.readText(StandardCharsets.UTF_8)
            } else {
                tempSourceFile.readText(StandardCharsets.UTF_8)
            }
            require(content.isNotBlank()) { context.getString(R.string.backup_error_content_empty) }
            val imported = try {
                json.decodeFromString(ReaterExportV1.serializer(), content)
            } catch (e: Exception) {
                throw IllegalArgumentException(context.getString(R.string.backup_error_parse_failed, e.localizedMessage), e)
            }
            require((isZip && imported.version == "v2") || (!isZip && imported.version == "v1")) { context.getString(R.string.backup_error_unsupported_format) }
            require(imported.assets.map { it.path }.distinct().size == imported.assets.size) { context.getString(R.string.backup_error_duplicate_assets) }
            if (isZip) {
                val missingAssets = imported.assets.filter { it.path !in packed }
                if (missingAssets.isNotEmpty()) {
                    android.util.Log.w("BackupManager", "備份中有 ${missingAssets.size} 個多媒體資源缺失，將安全降級為遠端連結還原")
                }
            }

            fun isMediaAvailable(path: String): Boolean {
                if (path.isBlank() || path.startsWith("http://") || path.startsWith("https://")) return true
                return path.startsWith("media/") && path in packed
            }

            val mediaDir = File(context.filesDir, "restored_media/${UUID.randomUUID()}")
            fun restoreMedia(path: String): String {
                if (path.isBlank() || !isZip) return path
                if (path.startsWith("http://") || path.startsWith("https://")) return path
                if (!isMediaAvailable(path)) return ""
                val sourceFile = packed[path] ?: return ""
                val destination = File(mediaDir, File(path).name)
                if (!destination.exists()) {
                    destination.parentFile?.mkdirs()
                    restoredFiles += destination
                    sourceFile.copyTo(destination, overwrite = true)
                }
                return destination.absolutePath
            }
            require(imported.fileFormat == "reater") { context.getString(R.string.backup_error_illegal_format) }
            require(imported.items.size <= MAX_IMPORT_ITEMS) { context.getString(R.string.backup_error_too_many_items) }
            require(imported.items.all { it.canonicalUrl.isNotBlank() }) { context.getString(R.string.backup_error_missing_url) }

            // Validate every asset before changing settings or writing files. Never trust backup paths.
            val assets: List<Pair<String, File>> = if (isZip) {
                packed.filterKeys { it.startsWith("avatars/") }.map { (name, file) -> File(name).name to file }
            } else imported.avatarFiles.map { asset ->
                require(AvatarStorage.isBackupFileName(asset.name) && ReaterArchive.validName("avatars/${asset.name}")) { context.getString(R.string.backup_error_invalid_avatar_name) }
                val file = File(staging, "avatars/${asset.name}").apply { parentFile?.mkdirs() }
                file.writeBytes(Base64.decode(asset.contentBase64, Base64.DEFAULT))
                asset.name to file
            }
            assets.forEach { (name, file) ->
                require(AvatarStorage.isBackupFileName(name) && file.length() > 0) { context.getString(R.string.backup_error_invalid_avatar_format) }
                if (name.endsWith(".txt")) {
                    require(file.length() < 4096 && AvatarStorage.decodeTransform(file.readText()) != null) { context.getString(R.string.backup_error_invalid_avatar_crop) }
                }
            }
            val validCurrentAvatarReference = imported.currentAvatarReference?.takeIf { isZip }?.takeIf { reference ->
                assets.any { it.first == reference }
            }
            require(assets.map { it.first }.distinct().size == assets.size) { context.getString(R.string.backup_error_duplicate_avatar_names) }
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
            try {
                for ((name, source) in assets) {
                    val destination = File(avatarDir, restoredNames.getValue(name))
                    check(destination.canonicalFile.parentFile == avatarDir.canonicalFile)
                    restoredFiles += destination
                    source.copyTo(destination)
                }
            } catch (error: Exception) {
                restoredFiles.forEach { it.delete() }
                throw error
            }

            val totalItems = imported.items.size.coerceAtLeast(1)
            val restoreEta = EtaEstimator(totalItems)

            // 解壓縮還原本機媒體與留言（純本機解壓與檔案搬移，不進行網路抓取）
            data class PreparedImportItem(
                val item: ExportedItem,
                val resolvedAuthorProfile: String,
                val resolvedMedia: List<ExportedMedia>,
                val resolvedComments: List<ExportedComment>
            )

            val preparedItems = imported.items.mapIndexed { idx, rawItem ->
                val currentIdx = idx + 1
                val handleLabel = "@${rawItem.authorHandle.ifBlank { rawItem.shortcode }}"
                val etaSec = restoreEta.estimateRemainingSeconds(currentIdx)
                onProgress(
                    BackupProgress(
                        current = currentIdx,
                        total = totalItems,
                        stage = context.getString(R.string.backup_progress_restoring_articles, currentIdx, imported.items.size),
                        detail = handleLabel,
                        estimatedRemainingSeconds = etaSec
                    )
                )

                // 優先自備份封裝資源還原本機檔案；無備份本地檔時直接保留遠端 URL
                val prelimMedia = rawItem.media.map { m ->
                    val local = if (isZip) restoreMedia(m.archivePath) else m.archivePath
                    m.copy(archivePath = local)
                }

                val prelimAuthorProfile = if (isZip) restoreMedia(rawItem.authorProfileUrl) else rawItem.authorProfileUrl

                val prelimComments = rawItem.comments.map { comment ->
                    val restoredAvatar = restoreMedia(comment.authorProfileLocalPath)
                    val restoredMediaJson = if (isZip) {
                        FetchedMediaJson.encode(FetchedMediaJson.decode(comment.mediaJson).map { cm ->
                            val cmLocal = restoreMedia(cm.localPath)
                            cm.copy(localPath = cmLocal)
                        })
                    } else comment.mediaJson
                    comment.copy(authorProfileLocalPath = restoredAvatar, mediaJson = restoredMediaJson)
                }

                PreparedImportItem(
                    item = rawItem,
                    resolvedAuthorProfile = prelimAuthorProfile,
                    resolvedMedia = prelimMedia,
                    resolvedComments = prelimComments
                )
            }

            onProgress(BackupProgress(0, totalItems, context.getString(R.string.backup_progress_writing_db), context.getString(R.string.backup_progress_items_detail, preparedItems.size)))

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
                        avatarIcon = category.avatarIcon,
                        updatedAt = category.updatedAt ?: System.currentTimeMillis()
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

                val categoryIdMap = imported.categories.mapNotNull { cat ->
                    cat.originalId?.let { old -> importedCategoryIds[cat.name]?.let { old to it } }
                }.toMap()
                fun remapFilter(raw: String): String {
                    val rule = runCatching { json.decodeFromString(com.reater.app.domain.CollectionRule.serializer(), raw) }.getOrNull()
                    if (rule != null) {
                        val newTopCat = rule.categoryId?.let { categoryIdMap[it] ?: it }
                        val remappedCriteria = rule.criteria.map { criterion ->
                            var updated = criterion
                            if (criterion.field == com.reater.app.domain.RuleField.CATEGORY) {
                                val oldId = criterion.value.toLongOrNull()
                                val newId = oldId?.let { categoryIdMap[it] }
                                if (newId != null) {
                                    updated = updated.copy(value = newId.toString())
                                }
                            }
                            criterion.config?.categoryId?.let { oldConfigId ->
                                val newConfigId = categoryIdMap[oldConfigId]
                                if (newConfigId != null) {
                                    updated = updated.copy(config = updated.config?.copy(categoryId = newConfigId))
                                }
                            }
                            updated
                        }
                        return runCatching {
                            json.encodeToString(
                                com.reater.app.domain.CollectionRule.serializer(),
                                rule.copy(categoryId = newTopCat, criteria = remappedCriteria)
                            )
                        }.getOrDefault(raw)
                    }

                    val parsed = runCatching { json.parseToJsonElement(raw) }.getOrNull()
                    if (parsed !is kotlinx.serialization.json.JsonObject) return raw
                    val old = (parsed["categoryId"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()
                    if (old == null || old !in categoryIdMap) return raw
                    return kotlinx.serialization.json.JsonObject(parsed.toMutableMap().apply {
                        put("categoryId", kotlinx.serialization.json.JsonPrimitive(categoryIdMap.getValue(old)))
                    }).toString()
                }
                val existingCollections = proDao.observeAllCollections().first()
                for (col in imported.collections) {
                    proDao.insertCollection(
                        SavedCollectionEntity(
                            id = existingCollections.firstOrNull { it.name == col.name }?.id ?: 0,
                            name = col.name,
                            iconName = col.iconName,
                            rulesJson = remapFilter(col.rulesJson),
                            sortOrder = col.sortOrder,
                            isEnabled = col.isEnabled,
                            createdAt = col.createdAt ?: System.currentTimeMillis()
                        )
                    )
                }

                val existingQueries = proDao.observeAllSavedQueries().first()
                for (query in imported.queries) {
                    proDao.insertSavedQuery(SavedQueryEntity(
                        id = existingQueries.firstOrNull { it.name == query.name }?.id ?: 0,
                        name = query.name, filterJson = remapFilter(query.filterJson), createdAt = query.createdAt
                    ))
                }

                val dbEta = EtaEstimator(preparedItems.size)
                for ((idx, prep) in preparedItems.withIndex()) {
                    val currentIdx = idx + 1
                    if (currentIdx % 5 == 0 || currentIdx == preparedItems.size) {
                        val etaSec = dbEta.estimateRemainingSeconds(currentIdx)
                        val handleLabel = "@${prep.item.authorHandle.ifBlank { prep.item.shortcode }}"
                        onProgress(
                            BackupProgress(
                                current = currentIdx,
                                total = totalItems,
                                stage = context.getString(R.string.backup_progress_writing_db_detail, currentIdx, preparedItems.size),
                                detail = handleLabel,
                                estimatedRemainingSeconds = etaSec
                            )
                        )
                    }
                    val item = prep.item
                    require(item.canonicalUrl.isNotBlank()) { context.getString(R.string.backup_error_missing_url) }
                    val existing = item.sourceFetchedAt?.let { itemDao.getBackupMatch(item.canonicalUrl, it) }
                        ?: itemDao.getItemByCanonicalUrl(item.canonicalUrl)
                    val entity = ItemEntity(
                        id = existing?.id ?: 0,
                        canonicalUrl = item.canonicalUrl,
                        shortcode = item.shortcode,
                        authorHandle = item.authorHandle,
                        authorDisplayName = item.authorDisplayName,
                        authorProfileUrl = prep.resolvedAuthorProfile,
                        authorVerified = item.authorVerified,
                        postedAt = item.postedAt,
                        postedAtRaw = item.postedAtRaw,
                        bodyText = item.bodyText,
                        topicTag = item.topicTag,
                        commentsText = item.commentsText,
                        mediaJson = item.mediaJson,
                        likeCount = item.likeCount,
                        replyCount = item.replyCount,
                        repostCount = item.repostCount,
                        sourceFetchedAt = item.sourceFetchedAt?.takeIf { it > 0 } ?: existing?.sourceFetchedAt ?: System.currentTimeMillis(),
                        sourceVersion = item.sourceVersion,
                        lastFetchStatus = item.lastFetchStatus,
                        lastFetchAt = item.lastFetchAt,
                        rawJsonMin = item.rawJsonMin,
                        platform = item.platform,
                        isDeleted = item.isDeleted,
                        deletedAt = item.deletedAt
                    )
                    val itemId = if (existing == null) itemDao.insertItem(entity) else {
                        itemDao.updateItem(entity)
                        existing.id
                    }
                    itemDao.insertUserEdit(
                        UserEditEntity(
                            itemId = itemId,
                            userBodyOverride = item.userBodyOverride,
                            manualNote = item.manualNote,
                            manualSummary = item.manualSummary,
                            aiSummary = item.aiSummary ?: item.manualSummary,
                            categoryId = item.categoryName?.let(importedCategoryIds::get),
                            isRead = item.isRead,
                            isFavorite = item.isFavorite,
                            openCount = item.openCount,
                            lastOpenedAt = item.lastOpenedAt,
                            editedAt = item.editedAt ?: System.currentTimeMillis(),
                            editSource = item.editSource,
                            dirtyFlag = item.dirtyFlag
                        )
                    )
                    database.commentDao().deleteCommentsByItemId(itemId)
                    database.commentDao().insertComments(prep.resolvedComments.mapIndexed { index, comment ->
                        CommentEntity(
                            itemId = itemId,
                            externalId = comment.externalId.ifBlank { "import_$index" },
                            author = comment.author,
                            text = comment.text,
                            likeCount = comment.likeCount,
                            parentExternalId = comment.parentExternalId,
                            depth = comment.depth,
                            authorProfileUrl = comment.authorProfileUrl,
                            authorProfileLocalPath = comment.authorProfileLocalPath,
                            sortKey = comment.sortKey.ifBlank { "0:${index.toString().padStart(8, '0')}" },
                            mediaJson = comment.mediaJson,
                            fetchedAt = if (comment.fetchedAt > 0) comment.fetchedAt else (item.sourceFetchedAt ?: System.currentTimeMillis())
                        )
                    })
                    database.mediaDao().deleteMediaByItemId(itemId)
                    val restoredMedia = prep.resolvedMedia.map {
                        MediaEntity(
                            itemId = itemId,
                            kind = it.kind,
                            remoteUrl = it.remoteUrl,
                            localPath = it.archivePath,
                            width = it.width,
                            height = it.height,
                            position = it.position
                        )
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
                    if (isZip || item.aiRuns.isNotEmpty() || item.fetchAttempts.isNotEmpty()) {
                        database.aiRunDao().deleteRuns(itemId)
                        database.aiRunDao().deleteAttempts(itemId)
                        item.aiRuns.forEach { database.aiRunDao().insert(it.copy(id = 0, itemId = itemId)) }
                        item.fetchAttempts.forEach { database.aiRunDao().insertAttempt(it.copy(id = 0, itemId = itemId)) }
                    }
                    refreshSearchIndex(itemId)
                    count++
                }
            }
            databaseCommitted = true
            imported.settings?.let { s ->
                settingsRepository.setAutoFetchEnabled(s.autoFetchEnabled)
                settingsRepository.setLastSelectedCategoryId(s.lastCategoryName?.let { categoryDao.getCategoryByName(it)?.id })
                settingsRepository.setThemeMode(s.themeMode)
                settingsRepository.setFontScale(s.fontScale)
                settingsRepository.setUnreadNudgeEnabled(s.unreadNudgeEnabled)
                settingsRepository.setUnreadNudgeDelayMin(s.unreadNudgeDelayMin)
                settingsRepository.setReviewDigestEnabled(s.reviewDigestEnabled)
                settingsRepository.setReviewDigestHour(s.reviewDigestHour)
                settingsRepository.setSelectedModel(s.selectedModel)
                settingsRepository.setCustomBaseUrl(s.customBaseUrl)
                settingsRepository.setAiTransmissionConsent(s.aiTransmissionConsent)
                settingsRepository.setAiAnalyzeOnSave(s.aiAnalyzeOnSave)
                settingsRepository.setCustomAvatarId(s.customAvatarId)
                val reference = validCurrentAvatarReference ?: imported.currentAvatarReference ?: s.customAvatarUri?.let { File(it).name }
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

            onProgress(BackupProgress(totalItems, totalItems, context.getString(R.string.backup_progress_restore_done, count), "", 0L))
            Result.success(count)
        } catch (e: Exception) {
            if (!databaseCommitted) restoredFiles.forEach { it.delete() }
            if (e is CancellationException) throw e
            Result.failure(e)
        } finally {
            staging.deleteRecursively()
        }
    }

    private companion object {
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
