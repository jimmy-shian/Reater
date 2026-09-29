package com.reater.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit

private val UrlRegex = Regex("""https?://[^\s<>"'）」』】]+""")

/**
 * 內文/留言可點擊連結文字：偵測 http(s) URL，以主色底線顯示，點擊外部瀏覽器開啟。
 * 尾端中文標點（。，、！？；：）與 ) ] } 自動剔除，避免誤含。
 */
@Composable
fun LinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    onNeutralClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            var last = 0
            UrlRegex.findAll(text).forEach { m ->
                var url = m.value.trimEnd('.', ',', ')', ']', '}', '?', '!', ';', ':')
                    .trimEnd('。', '，', '、', '！', '？', '；', '：', '「', '」', '『', '』', '【', '】', '…')
                if (url.isBlank()) return@forEach
                // 在原文中的實際起點
                val start = text.indexOf(m.value, last)
                if (start < 0) return@forEach
                if (start > last) append(text.substring(last, start))
                pushStringAnnotation(tag = "URL", annotation = url)
                withStyle(SpanStyle(color = linkColor)) {
                    append(url)
                }
                pop()
                last = start + m.value.length
                // 若剔除了尾端標點，把標點補回為普通文字
                val strippedTail = m.value.substring(url.length)
                if (strippedTail.isNotEmpty()) {
                    append(strippedTail)
                }
            }
            if (last < text.length) append(text.substring(last))
        }
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = androidx.compose.ui.text.TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = color
        ),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
        onClick = { offset ->
            val hit = annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
            if (hit != null) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(hit.item)))
                }
            } else {
                onNeutralClick?.invoke()
            }
        }
    )
}
