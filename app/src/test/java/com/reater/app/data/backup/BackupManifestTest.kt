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

}
