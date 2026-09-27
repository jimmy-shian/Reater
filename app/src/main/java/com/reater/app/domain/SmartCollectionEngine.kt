package com.reater.app.domain

import com.reater.app.data.local.dao.ItemDao
import com.reater.app.data.local.dao.ProDao
import com.reater.app.data.local.entity.ItemDetail
import com.reater.app.data.local.entity.SavedCollectionEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CollectionRule(
    val categoryId: Long? = null,
    val isRead: Boolean? = null,
    val isFavorite: Boolean? = null,
    val minLikes: Int? = null,
    val tag: String? = null,
    val author: String? = null
)

@Singleton
class SmartCollectionEngine @Inject constructor(
    private val itemDao: ItemDao,
    private val proDao: ProDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun evaluateCollection(rulesJson: String): List<ItemDetail> {
        val all = itemDao.observeAllItemDetails().first()
        val rule = try {
            json.decodeFromString(CollectionRule.serializer(), rulesJson)
        } catch (e: Exception) {
            return all
        }

        return all.filter { item ->
            var pass = true
            if (rule.categoryId != null && item.userEdit?.categoryId != rule.categoryId) {
                pass = false
            }
            if (rule.isRead != null && item.isRead != rule.isRead) {
                pass = false
            }
            if (rule.isFavorite != null && item.isFavorite != rule.isFavorite) {
                pass = false
            }
            if (rule.minLikes != null && item.item.likeCount < rule.minLikes) {
                pass = false
            }
            if (rule.author != null && !item.item.authorHandle.contains(rule.author, ignoreCase = true)) {
                pass = false
            }
            if (rule.tag != null && item.tags.none { it.name.equals(rule.tag, ignoreCase = true) }) {
                pass = false
            }
            pass
        }
    }

    suspend fun seedDefaultProCollectionsIfEmpty() {
        val existing = proDao.observeAllCollections().first()
        if (existing.isNotEmpty()) return

        val defaults = listOf(
            SavedCollectionEntity(
                name = "🔥 高讚熱門",
                iconName = "fire",
                rulesJson = """{"minLikes":100}""",
                sortOrder = 1
            ),
            SavedCollectionEntity(
                name = "📚 長期必讀",
                iconName = "book",
                rulesJson = """{"isFavorite":true,"isRead":false}""",
                sortOrder = 2
            ),
            SavedCollectionEntity(
                name = "💡 靈感庫",
                iconName = "lightbulb",
                rulesJson = """{"isFavorite":true}""",
                sortOrder = 3
            )
        )
        defaults.forEach { proDao.insertCollection(it) }
    }
}
