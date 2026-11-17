package com.reater.app.ui.components

import android.content.Context
import com.reater.app.R

/**
 * Pro / 分類共用文案模組。
 * 統一移除與 App 無關的第三方比喻（如 WinRAR），改以功能價值說明。
 * 文案集中在 strings.xml（pro_* 鍵），非 Compose 端透過 Context 取字；
 * Compose 端顯示請直接使用 stringResource(R.string.pro_*)。
 */
object ProCopy {
    /** Pro 鎖定頁主標 */
    fun lockTitle(context: Context): String = context.getString(R.string.pro_smart_curation_badge)

    /** Pro 鎖定頁說明（無上限 + 完整保存） */
    fun lockDescUnlockedFeatures(context: Context): String =
        context.getString(R.string.pro_lock_desc_unlocked_features)

    fun lockCta(context: Context): String = context.getString(R.string.pro_lock_cta)

    /** 分享頁免費配額提示 */
    fun shareLimitTitle(context: Context): String = context.getString(R.string.pro_share_limit_title)

    fun shareLimitDesc(context: Context): String =
        context.getString(R.string.pro_share_limit_body, com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT)

    /** 解鎖 Dialog 說明 */
    fun unlockDesc(context: Context): String = context.getString(R.string.pro_upsell_body)
}
