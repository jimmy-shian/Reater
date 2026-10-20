package com.reater.app.data.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BackupManifestTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun commentAvatarAndFullTextAttachmentSurviveBackupRoundTrip() {
        val comment = ExportedComment(
            externalId = "DeUgbQCmWZv", author = "eliot.chunchen",
            text = com.reater.app.domain.TextAttachmentStore.appendToText("Prompt如下", "  first\n\nsecond\n  "),
            likeCount = 4, authorProfileUrl = "https://example.com/profile.jpg",
            authorProfileLocalPath = "media/1.jpg"
        )
        val restored = json.decodeFromString(ExportedComment.serializer(),
            json.encodeToString(ExportedComment.serializer(), comment))
        assertEquals(comment, restored)
        val legacy = json.decodeFromString(ExportedComment.serializer(),
            """{"externalId":"old","author":"user","text":"body","likeCount":0}""")
        assertEquals("", legacy.authorProfileUrl)
        assertEquals("", legacy.authorProfileLocalPath)
    }

    private fun item() = ExportedItem(
        canonicalUrl = "https://www.threads.com/@author/post/abc", shortcode = "abc",
        authorHandle = "author", authorDisplayName = "作者", postedAt = 123,
        bodyText = "原始文章", commentsText = "留言", manualNote = "我的筆記",
        manualSummary = "人工摘要", isRead = true, isFavorite = true,
        categoryName = "GPT畫圖", sourceFetchedAt = 456, editedAt = 789,
        userBodyOverride = "修改後文章", deletedAt = 999, isDeleted = true,
        rawJsonMin = """{"chainMedia":["https://example.com/image.jpg"]}""",
        media = listOf(ExportedMedia("VIDEO", "https://example.com/video.mp4", archivePath = "media/0.mp4"))
    )

    @Test fun legacyJsonWithoutAssetsStillDecodes() {
        val legacy = """{"fileFormat":"reater","version":"v1","items":[],"categories":[{"name":"舊分類","colorArgb":123,"sort":0,"isDefault":false,"avatarIcon":"palette"}]}"""
        val decoded = json.decodeFromString(ReaterExportV1.serializer(), legacy)
        assertEquals("v1", decoded.version)
        assertTrue(decoded.assets.isEmpty())
        assertEquals("palette", decoded.categories.single().avatarIcon)
    }

    @Test fun migrationManifestPreservesEditsTimestampsSvgAndPro() {
        val manifest = ReaterExportV1(
            version = "v2", items = listOf(item(), item().copy(sourceFetchedAt = 457)),
            categories = listOf(ExportedCategory("GPT畫圖", 123, 2, false, "palette", originalId = 10)),
            settings = ExportedSettings(isProLicensed = true, customAvatarId = "shield", proLicenseCode = "fixture"),
            avatarHistory = listOf("custom_avatar_100.png"), currentAvatarReference = "custom_avatar_100.png",
            assets = listOf(ExportedAsset("media/0.mp4", 1024, "hash"))
        )
        val restored = json.decodeFromString(ReaterExportV1.serializer(), json.encodeToString(ReaterExportV1.serializer(), manifest))
        assertEquals(manifest, restored)
        assertEquals(2, restored.items.size)
        assertEquals(456L, restored.items.first().sourceFetchedAt)
        assertEquals("palette", restored.categories.single().avatarIcon)
        assertTrue(restored.settings!!.isProLicensed)
    }

    @Test fun manifestWithBlankArchivePathAndCompleteFieldsSerializes() {
        val completeItem = item().copy(
            tags = listOf("測試", "科技"),
            authorProfileUrl = "https://example.com/avatar.jpg",
            media = listOf(
                ExportedMedia("IMAGE", "https://example.com/expired_403.jpg", archivePath = ""),
                ExportedMedia("VIDEO", "https://example.com/video.mp4", archivePath = "media/0.mp4")
            )
        )
        val manifest = ReaterExportV1(
            version = "v2",
            items = listOf(completeItem),
            categories = listOf(
                ExportedCategory("分類A", 0xFF123456.toInt(), 1, true, "sparkles", 1, System.currentTimeMillis())
            ),
            collections = listOf(
                ExportedCollection("精選", "star", """{"categoryId":1}""", 0, true, 1000L)
            ),
            settings = ExportedSettings(
                isProLicensed = true,
                proLicenseCode = "TEST-CODE-1234",
                proLicenseEmail = "user@test.com",
                customAvatarId = "heart"
            )
        )
        val jsonStr = json.encodeToString(ReaterExportV1.serializer(), manifest)
        val decoded = json.decodeFromString(ReaterExportV1.serializer(), jsonStr)
        assertEquals(manifest, decoded)
        assertEquals("", decoded.items.first().media.first().archivePath)
        assertEquals("https://example.com/expired_403.jpg", decoded.items.first().media.first().remoteUrl)
        assertEquals("sparkles", decoded.categories.first().avatarIcon)
        assertEquals("star", decoded.collections.first().iconName)
        assertEquals("TEST-CODE-1234", decoded.settings?.proLicenseCode)
        assertTrue(decoded.settings?.isProLicensed == true)
        assertEquals(456L, decoded.items.first().sourceFetchedAt)
    }
}
