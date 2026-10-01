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
    val author: String? = null,
    val hasMedia: Boolean? = null
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
            return emptyList()
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
            if (rule.hasMedia != null && item.media.isNotEmpty() != rule.hasMedia) {
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
                sortOrder = 1,
                isEnabled = true
            ),
            SavedCollectionEntity(
                name = "📚 長期必讀",
                iconName = "study",
                rulesJson = """{"isFavorite":true,"isRead":false}""",
                sortOrder = 2,
                isEnabled = true
            ),
            SavedCollectionEntity(
                name = "💡 靈感庫",
                iconName = "sparkles",
                rulesJson = """{"isFavorite":true}""",
                sortOrder = 3,
                isEnabled = true
            )
        )
        defaults.forEach { proDao.insertCollection(it) }
    }

    companion object {
        private val describeJson = Json { ignoreUnknownKeys = true }

        /**
         * 把 rulesJson 轉成中文規則說明，例如「讚數 ≥ 100」「已收藏・未讀」。
         * 解析失敗或無條件時回傳「自訂條件」。
         */
        fun describeRules(
            rulesJson: String,
            categoryNameOf: ((Long) -> String?)? = null
        ): String {
            val rule = try {
                describeJson.decodeFromString(CollectionRule.serializer(), rulesJson)
            } catch (e: Exception) {
                return "自訂條件"
            }
            val parts = mutableListOf<String>()
            rule.categoryId?.let { cid ->
                val n = categoryNameOf?.invoke(cid)
                if (n != null) parts.add("分類「$n」") else parts.add("分類 ID $cid")
            }
            rule.isFavorite?.let { parts.add(if (it) "已收藏" else "未收藏") }
            rule.isRead?.let { parts.add(if (it) "已讀" else "未讀") }
            rule.minLikes?.let { parts.add("讚數 ≥ $it") }
            rule.hasMedia?.let { parts.add(if (it) "含媒體" else "無媒體") }
            rule.author?.takeIf { it.isNotBlank() }?.let { parts.add("作者含「$it」") }
            rule.tag?.takeIf { it.isNotBlank() }?.let { parts.add("標籤「$it」") }
            if (parts.isEmpty()) return "全部內容"
            return parts.joinToString("・")
        }
    }
}
