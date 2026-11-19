package com.reater.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.reater.app.R

private const val URL_TAG = "URL"
private val UrlRegex = Regex("""https?://[^\s<>"'）」』】]+""")

private sealed interface LinkPressResult {
    data class Up(val change: PointerInputChange) : LinkPressResult
    data object Cancelled : LinkPressResult
}

/**
 * 內文/留言可點擊連結文字：偵測 http(s) URL，以主色顯示，點擊外部瀏覽器開啟，長按複製網址。
 * 尾端中文標點（。，、！？；：）與 ) ] } 自動剔除，避免誤含。
 */
@Composable
fun LinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    onNeutralClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val linkColor = MaterialTheme.colorScheme.primary
    val currentOnNeutralClick by rememberUpdatedState(onNeutralClick)
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            var last = 0
            UrlRegex.findAll(text).forEach { m ->
                val rawUrl = m.value.trimEnd('.', ',', ')', ']', '}', '?', '!', ';', ':')
                    .trimEnd('。', '，', '、', '！', '？', '；', '：', '「', '」', '『', '』', '【', '】', '…')
                if (rawUrl.isBlank()) return@forEach
                val start = text.indexOf(m.value, last)
                if (start < 0) return@forEach
                if (start > last) append(text.substring(last, start))

                pushStringAnnotation(tag = URL_TAG, annotation = rawUrl)
                withStyle(SpanStyle(color = linkColor)) {
                    append(rawUrl)
                }
                pop()

                last = start + m.value.length
                val strippedTail = m.value.substring(rawUrl.length)
                if (strippedTail.isNotEmpty()) {
                    append(strippedTail)
                }
            }
            if (last < text.length) append(text.substring(last))
        }
    }

    val textStyle = TextStyle(
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = color
    )

    BasicText(
        text = annotated,
        onTextLayout = { layoutResult = it },
        modifier = modifier.pointerInput(annotated, onNeutralClick != null) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                val hitUrl = findUrlAt(layoutResult, annotated, down.position)
                val neutralAction = currentOnNeutralClick
                if (hitUrl == null && neutralAction == null) {
                    return@awaitEachGesture
                }

                down.consume()
                if (hitUrl != null) {
                    val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrSlopCancellation(
                            downId = down.id,
                            downPosition = down.position,
                            touchSlop = viewConfiguration.touchSlop
                        )
                    }
                    when (result) {
                        is LinkPressResult.Up -> {
                            result.change.consume()
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(hitUrl)))
                            }
                        }
                        null -> {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Reater URL", hitUrl))
                            Toast.makeText(
                                context,
                                context.getString(R.string.copied_to_clipboard),
                                Toast.LENGTH_SHORT
                            ).show()
                            consumeUntilAllPointersUp()
                        }
                        LinkPressResult.Cancelled -> Unit
                    }
                } else if (neutralAction != null) {
                    val result = waitForUpOrSlopCancellation(
                        downId = down.id,
                        downPosition = down.position,
                        touchSlop = viewConfiguration.touchSlop
                    )
                    if (result is LinkPressResult.Up) {
                        result.change.consume()
                        neutralAction.invoke()
                    }
                }
            }
        },
        style = textStyle,
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap
    )
}

private fun findUrlAt(
    layout: TextLayoutResult?,
    annotated: AnnotatedString,
    pos: Offset
): String? {
    val result = layout ?: return null
    if (pos.x < 0f || pos.y < 0f || pos.x > result.size.width || pos.y > result.size.height) {
        return null
    }
    val line = result.getLineForVerticalPosition(pos.y)
    if (pos.y < result.getLineTop(line) || pos.y > result.getLineBottom(line)) {
        return null
    }
    val lineLeft = minOf(result.getLineLeft(line), result.getLineRight(line))
    val lineRight = maxOf(result.getLineLeft(line), result.getLineRight(line))
    if (pos.x < lineLeft || pos.x > lineRight) {
        return null
    }

    val charOffset = result.getOffsetForPosition(pos)
    val annotations = annotated.getStringAnnotations(URL_TAG, 0, annotated.length)
    for (ann in annotations) {
        if (charOffset in ann.start until ann.end) {
            return ann.item
        }
        if (charOffset == ann.end && ann.end > ann.start) {
            val lastCharBox = runCatching { result.getBoundingBox(ann.end - 1) }.getOrNull()
            if (lastCharBox != null &&
                pos.y in lastCharBox.top..lastCharBox.bottom &&
                pos.x in lastCharBox.left..lastCharBox.right
            ) {
                return ann.item
            }
        }
    }
    return null
}

private suspend fun AwaitPointerEventScope.waitForUpOrSlopCancellation(
    downId: PointerId,
    downPosition: Offset,
    touchSlop: Float
): LinkPressResult {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.changes.all { it.changedToUp() }) {
            return LinkPressResult.Up(event.changes[0])
        }
        val tracked = event.changes.firstOrNull { it.id == downId } ?: return LinkPressResult.Cancelled
        if (!tracked.pressed) {
            return LinkPressResult.Cancelled
        }
        if (event.changes.any {
                it.isConsumed ||
                    it.isOutOfBounds(size, extendedTouchPadding) ||
                    (it.id == downId && (it.position - downPosition).getDistance() > touchSlop)
            }
        ) {
            return LinkPressResult.Cancelled
        }
        val consumeCheck = awaitPointerEvent(PointerEventPass.Final)
        if (consumeCheck.changes.any { it.isConsumed }) {
            return LinkPressResult.Cancelled
        }
    }
}

private suspend fun AwaitPointerEventScope.consumeUntilAllPointersUp() {
    do {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
}
