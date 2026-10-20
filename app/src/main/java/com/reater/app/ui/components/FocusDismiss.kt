package com.reater.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 點擊空白處收合鍵盤：套在輸入框所在容器最外層。
 *
 * 設計重點：
 * 1. 使用 pointerInput + detectTapGestures（只處理觸控輕點）：點在 TextField / 按鈕上由子元件先消費，
 *    父層不會誤清焦點；拖曳/捲動時手勢自動取消，不會誤觸。
 * 2. 刻意不用 clickable：Compose foundation 1.7.x 的 clickable 會在收到 Enter「KeyUp」時直接觸發 onClick，
 *    三星等鍵盤以 KeyEvent 送出 Enter → 多行輸入框換行後 KeyUp 冒泡到此 → clearFocus → 鍵盤被收起。
 * 3. 外部「滑動」關閉鍵盤請搭配 [DismissFocusOnScroll]（監聽外層 ScrollState）。
 */
@Composable
fun Modifier.dismissFocusOnTap(focusManager: FocusManager): Modifier {
    return this.pointerInput(focusManager) {
        detectTapGestures(onTap = { runCatching { focusManager.clearFocus() } })
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
