package com.reater.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * 點擊空白處收合鍵盤：套在輸入框所在容器最外層。
 *
 * 設計重點：
 * 1. 使用 pointerInput + detectTapGestures（只處理觸控輕點）：點在 TextField / 按鈕上由子元件先消費，
 *    父層不會誤清焦點；拖曳/捲動時手勢自動取消，不會誤觸。
 * 2. 刻意不用 clickable：Compose foundation 1.7.x 的 clickable 會在收到 Enter「KeyUp」時直接觸發 onClick，
 *    三星等鍵盤以 KeyEvent 送出 Enter → 多行輸入框換行後 KeyUp 冒泡到此 → clearFocus → 鍵盤被收起。
 * 3. 點擊同時清除焦點並收起軟鍵盤，並搭配 [DismissFocusOnScroll]（監聽外層 ScrollState 實質滾動）。
 */
@Composable
fun Modifier.dismissFocusOnTap(focusManager: FocusManager, onDismiss: (() -> Unit)? = null): Modifier {
    val keyboard = LocalSoftwareKeyboardController.current
    return this.pointerInput(focusManager, keyboard, onDismiss) {
        detectTapGestures(onTap = {
            runCatching {
                focusManager.clearFocus()
                keyboard?.hide()
            }
            onDismiss?.invoke()
        })
    }
}

/**
 * 外層垂直滑動即收合鍵盤（不影響輸入框內水平拖曳與鼠標選取長文字）。
 *
 * 在對話框內使用方式：
 * ```
 * val scrollState = rememberScrollState()
 * DismissFocusOnScroll(scrollState, focusManager)
 * Column(Modifier.verticalScroll(scrollState).dismissFocusOnTap(focusManager)) { ... }
 * ```
 * 監聽外層垂直實質滾動（超過 6px），輸入框內左右拖曳游標、選取文字或微小滑鼠抖動不會中斷焦點。
 */
@Composable
fun DismissFocusOnScroll(scrollState: ScrollState, focusManager: FocusManager) {
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            val startVal = scrollState.value
            snapshotFlow { scrollState.value }.collect { currentVal ->
                if (kotlin.math.abs(currentVal - startVal) > 6) {
                    runCatching {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    }
                }
            }
        }
    }
}
