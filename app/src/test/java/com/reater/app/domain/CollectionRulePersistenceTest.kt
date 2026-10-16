package com.reater.app.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CollectionRulePersistenceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun legacyRuleRemainsEnabledWithAllExistingCriteria() {
        val rule = json.decodeFromString(CollectionRule.serializer(), """{"minLikes":100,"categoryId":42,"isFavorite":true}""")
        assertTrue(rule.enabled)
        assertEquals(100, rule.minLikes)
        assertEquals(42L, rule.categoryId)
        assertEquals(true, rule.isFavorite)
    }

    @Test fun disabledRuleSurvivesBackupJsonAndRetainsCriteria() {
        val original = CollectionRule(enabled = false, isRead = false, author = "alice", tag = "旅遊", hasMedia = true)
        val restored = json.decodeFromString(CollectionRule.serializer(), json.encodeToString(CollectionRule.serializer(), original))
        assertEquals(original, restored)
        assertFalse(restored.enabled)
        assertEquals(original.copy(enabled = true), restored.copy(enabled = true))
    }

    @Test fun legacyDisabledCollectionFlagSurvivesBackup() {
        val collection = com.reater.app.data.backup.ExportedCollection(
            name = "舊版停用規則", rulesJson = "{}", isEnabled = false
        )
        val restored = json.decodeFromString(com.reater.app.data.backup.ExportedCollection.serializer(),
            json.encodeToString(com.reater.app.data.backup.ExportedCollection.serializer(), collection))
        assertFalse(restored.isEnabled)
    }
}
