package com.reater.app.ui

import androidx.annotation.DrawableRes
import com.reater.app.R

data class AvatarIconItem(
    val id: String,
    val name: String,
    val desc: String,
    @DrawableRes val resId: Int,
    val isPro: Boolean = false
)

object AvatarIcons {
    /**
     * 免費基礎 5 款
     */
    val FREE: List<AvatarIconItem> = listOf(
        AvatarIconItem("life", "生活日常", "生活隨筆、日常雜記與生活日常", R.drawable.ic_avatar_life, isPro = false),
        AvatarIconItem("tech", "科技開發", "技術架構、程式開發與前沿資訊", R.drawable.ic_avatar_tech, isPro = false),
        AvatarIconItem("study", "讀書學習", "好書推薦、深度閱讀與知識筆記", R.drawable.ic_avatar_study, isPro = false),
        AvatarIconItem("finance", "財經投資", "市場動態、理財觀念與經濟商業", R.drawable.ic_avatar_finance, isPro = false),
        AvatarIconItem("travel", "休閒旅行", "美食探索、旅行足跡與在地生活", R.drawable.ic_avatar_travel, isPro = false)
    )

    /**
     * Pro 尊爵專屬福利 10 款
     */
    val PRO: List<AvatarIconItem> = listOf(
        AvatarIconItem("crown", "至尊皇冠", "王者決策、頂級智慧與精選策展", R.drawable.ic_avatar_crown, isPro = true),
        AvatarIconItem("diamond", "璀璨鑽石", "永恆珍藏、純粹高價值知識結晶", R.drawable.ic_avatar_diamond, isPro = true),
        AvatarIconItem("rocket", "極速推進", "高效成長、生產力爆發與開拓實踐", R.drawable.ic_avatar_rocket, isPro = true),
        AvatarIconItem("sparkles", "智慧星芒", "AI 賦能、創意靈感與思維碰撞", R.drawable.ic_avatar_sparkles, isPro = true),
        AvatarIconItem("shield", "隱私金庫", "安全護航、個人金庫與核心機密", R.drawable.ic_avatar_shield, isPro = true),
        AvatarIconItem("compass", "探索羅盤", "視野引航、長遠眼界與策略指南", R.drawable.ic_avatar_compass, isPro = true),
        AvatarIconItem("fire", "熱門火種", "爆紅話題、社群熱議與流行脈動", R.drawable.ic_avatar_fire, isPro = true),
        AvatarIconItem("palette", "創意思潮", "設計美學、文化藝術與創意展演", R.drawable.ic_avatar_palette, isPro = true),
        AvatarIconItem("globe", "世界格局", "全球洞察、宏觀視野與國際脈動", R.drawable.ic_avatar_globe, isPro = true),
        AvatarIconItem("trophy", "卓越榮耀", "成就里程碑、菁英典藏與榮譽徽記", R.drawable.ic_avatar_trophy, isPro = true)
    )

    val ALL: List<AvatarIconItem> = FREE + PRO

    fun getDrawableRes(id: String?): Int {
        if (id.isNullOrBlank()) return FREE.first().resId
        ALL.firstOrNull { it.id.equals(id, ignoreCase = true) }?.let { return it.resId }
        return FREE.first().resId
    }

    fun getIconItem(id: String?): AvatarIconItem {
        return ALL.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: FREE[0]
    }
}
