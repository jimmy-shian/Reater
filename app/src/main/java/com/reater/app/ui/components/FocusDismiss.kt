package com.reater.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 點擊空白處 + 滑動即收合鍵盤：套在輸入框所在容器最外層。
 * 舊版僅 clickable（一定要「點一下」外部才生效，滑動無效）。
 * 新版用 pointerInput 在按下瞬間即清焦點（不消費事件，不擋捲動/按鈕），
 * 滑動、拖曳、點擊皆可失焦關閉鍵盤。
 */
@Composable
fun Modifier.dismissFocusOnTap(focusManager: FocusManager): Modifier {
    return this.pointerInput(focusManager) {
        awaitEachGesture {
            // 任何觸碰按下即失焦（含滑動起始），不消費事件讓捲動/點擊繼續傳遞
            val down = awaitFirstDown(requireUnconsumed = false)
            // 僅在有焦點時清，避免多餘重組；失敗忽略
            runCatching { focusManager.clearFocus() }
            // 等待手勢結束（抬起或取消），期間不攔截
            do {
                val event = awaitPointerEvent()
                if (event.changes.all { !it.pressed }) break
            } while (true)
            down.consume()
        }
    }
}
