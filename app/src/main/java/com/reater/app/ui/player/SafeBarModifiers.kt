package com.reater.app.ui.player

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 直向 / 橫向全螢幕頂部列樣式（拆開撰寫）。
 * 供影片全螢幕播放器 (FullscreenVideoViewer) 與圖片檢視器 (MediaViewerDialog) 共用。
 *
 * 取 max(實際 safeDrawing inset + 呼吸, 保底) 手算 padding，取代 windowInsetsPadding：
 * inset 正常時貼齊系統列；部分機型（requestedOrientation 切換後首幀、三鍵列機型）
 * inset 會回 0，此時保底仍保證控制列不被導覽列/瀏海蓋掉。
 *
 * 直向全螢幕：左右均衡，頂部保證在狀態列之下。
 * - top = max(實際 top inset + 8dp, 40dp)
 * - start/end = max(實際左右 inset + 14dp, 14dp)
 *
 * 橫向全螢幕：左側可貼邊、右側充分讓出導覽列返回鍵。
 * - top = max(實際 top inset + 8dp, 28dp)
 * - start = max(實際 left inset + 8dp, 12dp)（稍留呼吸）
 * - end = max(實際 right inset + 20dp, 44dp)（充分空出返回鍵，避免膠囊被裁）
 */
@Composable
internal fun fullscreenTopBarModifier(isLandscape: Boolean): Modifier {
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val topInset = safe.calculateTopPadding()
    val leftInset = safe.calculateLeftPadding(layoutDirection)
    val rightInset = safe.calculateRightPadding(layoutDirection)
    return if (isLandscape) {
        val top = maxOf(topInset + 8.dp, 28.dp)
        val left = maxOf(leftInset + 8.dp, 12.dp)
        val right = maxOf(rightInset + 20.dp, 44.dp)
        Modifier.padding(top = top, bottom = 8.dp, start = left, end = right)
    } else {
        val top = maxOf(topInset + 8.dp, 40.dp)
        val left = maxOf(leftInset + 14.dp, 14.dp)
        val right = maxOf(rightInset + 14.dp, 14.dp)
        Modifier.padding(top = top, bottom = 8.dp, start = left, end = right)
    }
}

/**
 * 直向 / 橫向全螢幕底部控制列樣式（拆開撰寫）。
 * 同時供圖片檢視器底列共用：「下載圖片」按鈕先前貼底被導覽列蓋掉，
 * 改走同一套保底 inset 邏輯。
 *
 * 直向全螢幕：底部曾被放到手機導覽列返回區塊而無法使用。
 * - bottom = max(實際 bottom inset + 16dp 呼吸, 58dp 保底)
 *   （三鍵列約 48dp，手勢列約 16~24dp；58dp 保底確保兩者之上都不被蓋，含呼吸空間）
 * - start/end 均衡 14dp 起跳。
 *
 * 橫向全螢幕：底部純粹過低＋右側有導覽列返回鍵。
 * - bottom = max(實際 bottom inset + 20dp, 40dp)（上抬避免貼圓角/手勢區）
 * - start = max(實際 left inset + 8dp, 12dp)（左稍留呼吸）
 * - end = max(實際 right inset + 20dp, 44dp)（右充分空出返回鍵，避免進度條右端被裁）
 */
@Composable
internal fun fullscreenBottomBarModifier(isLandscape: Boolean): Modifier {
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val bottomInset = safe.calculateBottomPadding()
    val leftInset = safe.calculateLeftPadding(layoutDirection)
    val rightInset = safe.calculateRightPadding(layoutDirection)
    return if (isLandscape) {
        val bottom = maxOf(bottomInset + 20.dp, 40.dp)
        val left = maxOf(leftInset + 8.dp, 12.dp)
        val right = maxOf(rightInset + 20.dp, 44.dp)
        Modifier.padding(start = left, end = right, top = 4.dp, bottom = bottom)
    } else {
        val bottom = maxOf(bottomInset + 16.dp, 58.dp)
        val left = maxOf(leftInset + 14.dp, 14.dp)
        val right = maxOf(rightInset + 14.dp, 14.dp)
        Modifier.padding(start = left, end = right, top = 4.dp, bottom = bottom)
    }
}
