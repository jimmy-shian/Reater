package com.reater.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager

/**
 * 點擊空白處收合鍵盤：套在輸入框所在容器最外層。
 *
 * 設計重點（修復舊版 pointerInput 搶事件問題）：
 * 1. 使用 clickable（不消費拖曳事件）：只有「輕點空白處」才觸發 clearFocus，
 *    點在 TextField / 按鈕上由子元件消費，父層不會誤清焦點。
 * 2. 絕不攔截/消費滑動與輸入框內水平拖曳：長字串游標拖到最左/最右時，
 *    TextField 內部水平捲動可正常自動跟隨，不會被父層中斷卡住。
 * 3. 外部「滑動」關閉鍵盤請搭配 [DismissFocusOnScroll]（監聽外層 ScrollState），
 *    因為 clickable 在拖曳時會自動取消，不會誤觸。
 */
@Composable
fun Modifier.dismissFocusOnTap(focusManager: FocusManager): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        indication = null,
        interactionSource = interactionSource
    ) {
        runCatching { focusManager.clearFocus() }
    }
}

/**
 * 外層垂直滑動即收合鍵盤（不影響輸入框內水平拖曳）。
 *
 * 在對話框內使用方式：
 * ```
 * val scrollState = rememberScrollState()
 * DismissFocusOnScroll(scrollState, focusManager)
 * Column(Modifier.verticalScroll(scrollState).dismissFocusOnTap(focusManager)) { ... }
 * ```
 * 只監聽外層垂直 ScrollState，輸入框內左右拖曳游標不會觸發，長文編輯不卡住。
 */
@Composable
fun DismissFocusOnScroll(scrollState: ScrollState, focusManager: FocusManager) {
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            runCatching { focusManager.clearFocus() }
        }
    }
}
