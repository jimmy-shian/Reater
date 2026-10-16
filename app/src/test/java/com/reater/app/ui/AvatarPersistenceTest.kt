package com.reater.app.ui

import org.junit.Assert.*
import org.junit.Test
import com.reater.app.data.backup.ExportedAvatarFile
import com.reater.app.data.backup.ExportedSettings
import com.reater.app.data.backup.ReaterExportV1
import kotlinx.serialization.json.Json

class AvatarPersistenceTest {
    @Test fun transformRetainsRotationViewportAndOffset() {
        val transform = AvatarStorage.CropTransform(2.5f, -0.21f, 0.18f, 270f, 1f)
        assertEquals(transform, AvatarStorage.decodeTransform(AvatarStorage.encodeTransform(transform)))
    }

    @Test fun oldMetadataGetsDefaultRotationAndViewport() {
        assertEquals(AvatarStorage.CropTransform(1.5f, 0.2f, -0.3f), AvatarStorage.decodeTransform("1.5,0.2,-0.3"))
        assertNull(AvatarStorage.decodeTransform("NaN,0,0"))
        assertNull(AvatarStorage.decodeTransform("2,Infinity,0"))
    }

    @Test fun backupRejectsTraversalAndArbitraryFileTypes() {
        listOf("custom_avatar_123.png", "custom_avatar_orig_123.jpg", "custom_avatar_crop_123.txt", "custom_avatar.jpg").forEach {
            assertTrue(it, AvatarStorage.isBackupFileName(it))
        }
        listOf("../custom_avatar_123.png", "custom_avatar_orig_1.exe", "custom_avatar_crop_../test", "custom_avatar_123.png/other").forEach {
            assertFalse(it, AvatarStorage.isBackupFileName(it))
        }
        assertEquals(123L, AvatarStorage.extractTs("custom_avatar_crop_123.txt"))
    }

    @Test fun backupRoundTripIncludesOriginalMetadataHistoryAndCurrentReference() {
        val backup = ReaterExportV1(
            exportedAt = 1_700_000_000_000L,
            items = emptyList(), settings = ExportedSettings(customAvatarUri = "/old/device/custom_avatar_123.png"),
            avatarFiles = listOf(
                ExportedAvatarFile("custom_avatar_orig_123.jpg", "b3JpZ2luYWw="),
                ExportedAvatarFile("custom_avatar_123.png", "Y3JvcA=="),
                ExportedAvatarFile("custom_avatar_crop_123.txt", "MiwwLDAsOTAsMQ==")
            ), avatarHistory = listOf("custom_avatar_123.png"), currentAvatarReference = "custom_avatar_123.png"
        )
        val encoded = Json.encodeToString(ReaterExportV1.serializer(), backup)
        assertEquals(backup, Json.decodeFromString(ReaterExportV1.serializer(), encoded))
        assertEquals(3, backup.avatarFiles.size)
    }

    @Test fun oldBackupRemainsReadableWithoutAvatarAssets() {
        val backup = Json.decodeFromString(ReaterExportV1.serializer(), """{"version":"v1","fileFormat":"reater","items":[]}""")
        assertTrue(backup.avatarFiles.isEmpty())
        assertTrue(backup.avatarHistory.isEmpty())
        assertNull(backup.currentAvatarReference)
    }

    @Test fun deletingCurrentHistoryIsBlocked() {
        assertFalse(AvatarStorage.canDeleteHistory("/avatar/current.png", "/avatar/current.png"))
        assertTrue(AvatarStorage.canDeleteHistory("/avatar/old.png", "/avatar/current.png"))
    }
}
