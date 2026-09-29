package com.reater.app.ui

import androidx.annotation.DrawableRes
import com.reater.app.R

data class AvatarIconItem(
    val id: String,
    val name: String,
    @DrawableRes val resId: Int
)

object AvatarIcons {
    /**
     * open-design 精確版：8 個核心分類圖示，對齊內建種子分類。
     * 2dp 圓角描邊 + 12% 淡底，去除可愛動物系，確保小尺寸清晰可辨。
     */
    val ALL: List<AvatarIconItem> = listOf(
        AvatarIconItem("tech", "科技開發", R.drawable.ic_avatar_tech),
        AvatarIconItem("life", "生活日常", R.drawable.ic_avatar_life),
        AvatarIconItem("finance", "財經投資", R.drawable.ic_avatar_finance),
        AvatarIconItem("media", "影視動漫", R.drawable.ic_avatar_media),
        AvatarIconItem("career", "職場職涯", R.drawable.ic_avatar_career),
        AvatarIconItem("study", "讀書學習", R.drawable.ic_avatar_study),
        AvatarIconItem("travel", "美食旅遊", R.drawable.ic_avatar_travel),
        AvatarIconItem("news", "時事觀點", R.drawable.ic_avatar_news)
    )

    /** 舊版 id 相容對映（已存資料 avatarIcon=camel 等仍可正確顯示） */
    private val LEGACY_MAP: Map<String, Int> = mapOf(
        "camel" to R.drawable.ic_avatar_life,
        "fire" to R.drawable.ic_avatar_news,
        "rocket" to R.drawable.ic_avatar_tech,
        "code" to R.drawable.ic_avatar_tech,
        "palette" to R.drawable.ic_avatar_media,
        "book" to R.drawable.ic_avatar_study,
        "coffee" to R.drawable.ic_avatar_travel,
        "music" to R.drawable.ic_avatar_media,
        "camera" to R.drawable.ic_avatar_media,
        "star" to R.drawable.ic_avatar_career,
        "heart" to R.drawable.ic_avatar_life
    )

    fun getDrawableRes(id: String?): Int {
        if (id.isNullOrBlank()) return R.drawable.ic_avatar_life
        ALL.firstOrNull { it.id.equals(id, ignoreCase = true) }?.let { return it.resId }
        LEGACY_MAP[id.lowercase()]?.let { return it }
        return R.drawable.ic_avatar_life
    }

    /** 自訂分類圖示 id 一覽（tech / life / finance / media / career / study / travel / news） */
    fun iconForSeedCategory(name: String): String = when (name) {
        "科技與開發" -> "tech"
        "生活與日常" -> "life"
        "財經與投資" -> "finance"
        "動漫與影視" -> "media"
        "職場與職涯" -> "career"
        "讀書與學習" -> "study"
        "美食與旅遊" -> "travel"
        "時事與觀點" -> "news"
        else -> "life"
    }
}
