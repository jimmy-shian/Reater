package com.reater.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.reater.app.R

data class AvatarIconItem(
    val id: String,
    @StringRes val name: Int,
    @StringRes val desc: Int,
    @DrawableRes val resId: Int,
    val isPro: Boolean = false
) {
    val category: Int @StringRes get() = when (id) {
        "tech", "rocket", "shield" -> R.string.avatar_category_tech
        "study", "sparkles" -> R.string.avatar_category_reading
        "finance", "diamond", "crown" -> R.string.avatar_category_finance
        "travel", "compass", "globe" -> R.string.avatar_category_travel
        "palette" -> R.string.avatar_category_creative
        else -> R.string.avatar_category_life
    }
}

object AvatarIcons {
    /**
     * 免費基礎 5 款
     */
    val FREE: List<AvatarIconItem> = listOf(
        AvatarIconItem("life", R.string.avatar_item_life_name, R.string.avatar_item_life_desc, R.drawable.ic_avatar_life, isPro = false),
        AvatarIconItem("tech", R.string.avatar_item_tech_name, R.string.avatar_item_tech_desc, R.drawable.ic_avatar_tech, isPro = false),
        AvatarIconItem("study", R.string.avatar_item_study_name, R.string.avatar_item_study_desc, R.drawable.ic_avatar_study, isPro = false),
        AvatarIconItem("finance", R.string.avatar_item_finance_name, R.string.avatar_item_finance_desc, R.drawable.ic_avatar_finance, isPro = false),
        AvatarIconItem("travel", R.string.avatar_item_travel_name, R.string.avatar_item_travel_desc, R.drawable.ic_avatar_travel, isPro = false)
    )

    /**
     * Pro 尊爵專屬福利 10 款
     */
    val PRO: List<AvatarIconItem> = listOf(
        AvatarIconItem("crown", R.string.avatar_item_crown_name, R.string.avatar_item_crown_desc, R.drawable.ic_avatar_crown, isPro = true),
        AvatarIconItem("diamond", R.string.avatar_item_diamond_name, R.string.avatar_item_diamond_desc, R.drawable.ic_avatar_diamond, isPro = true),
        AvatarIconItem("rocket", R.string.avatar_item_rocket_name, R.string.avatar_item_rocket_desc, R.drawable.ic_avatar_rocket, isPro = true),
        AvatarIconItem("sparkles", R.string.avatar_item_sparkles_name, R.string.avatar_item_sparkles_desc, R.drawable.ic_avatar_sparkles, isPro = true),
        AvatarIconItem("shield", R.string.avatar_item_shield_name, R.string.avatar_item_shield_desc, R.drawable.ic_avatar_shield, isPro = true),
        AvatarIconItem("compass", R.string.avatar_item_compass_name, R.string.avatar_item_compass_desc, R.drawable.ic_avatar_compass, isPro = true),
        AvatarIconItem("fire", R.string.avatar_item_fire_name, R.string.avatar_item_fire_desc, R.drawable.ic_avatar_fire, isPro = true),
        AvatarIconItem("palette", R.string.avatar_item_palette_name, R.string.avatar_item_palette_desc, R.drawable.ic_avatar_palette, isPro = true),
        AvatarIconItem("globe", R.string.avatar_item_globe_name, R.string.avatar_item_globe_desc, R.drawable.ic_avatar_globe, isPro = true),
        AvatarIconItem("trophy", R.string.avatar_item_trophy_name, R.string.avatar_item_trophy_desc, R.drawable.ic_avatar_trophy, isPro = true)
    )

    /**
     * 專門提供智慧規則的 5 款專屬向量圖示
     */
    val RULE_ICONS: List<AvatarIconItem> = listOf(
        AvatarIconItem("smart_hot", R.string.avatar_item_smart_hot_name, R.string.avatar_item_smart_hot_desc, R.drawable.ic_rule_hot, isPro = true),
        AvatarIconItem("smart_study", R.string.avatar_item_smart_study_name, R.string.avatar_item_smart_study_desc, R.drawable.ic_rule_bookmark, isPro = true),
        AvatarIconItem("smart_sparkles", R.string.avatar_item_smart_sparkles_name, R.string.avatar_item_smart_sparkles_desc, R.drawable.ic_rule_sparkles, isPro = true),
        AvatarIconItem("smart_filter", R.string.avatar_item_smart_filter_name, R.string.avatar_item_smart_filter_desc, R.drawable.ic_rule_filter, isPro = true),
        AvatarIconItem("smart_trending", R.string.avatar_item_smart_trending_name, R.string.avatar_item_smart_trending_desc, R.drawable.ic_rule_trending, isPro = true)
    )

    val ALL: List<AvatarIconItem> = FREE + PRO + RULE_ICONS

    fun getDrawableRes(id: String?): Int {
        if (id.isNullOrBlank()) return FREE.first().resId
        when (id.lowercase()) {
            "smart_hot", "fire" -> return R.drawable.ic_rule_hot
            "smart_study", "study" -> return R.drawable.ic_rule_bookmark
            "smart_sparkles", "sparkles" -> return R.drawable.ic_rule_sparkles
            "smart_filter" -> return R.drawable.ic_rule_filter
            "smart_trending" -> return R.drawable.ic_rule_trending
        }
        ALL.firstOrNull { it.id.equals(id, ignoreCase = true) }?.let { return it.resId }
        return FREE.first().resId
    }

    fun getIconItem(id: String?): AvatarIconItem {
        return ALL.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: FREE[0]
    }
}
