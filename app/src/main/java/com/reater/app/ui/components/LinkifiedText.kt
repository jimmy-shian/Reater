package com.reater.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
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
                val rawUrl = m.value.trimEnd('.', ',', ')', ']', '}', '?', '!', ';', ':')
                    .trimEnd('。', '，', '、', '！', '？', '；', '：', '「', '」', '『', '』', '【', '】', '…')
                if (rawUrl.isBlank()) return@forEach
                val start = text.indexOf(m.value, last)
                if (start < 0) return@forEach
                if (start > last) append(text.substring(last, start))

                val link = androidx.compose.ui.text.LinkAnnotation.Url(
                    url = rawUrl,
                    styles = androidx.compose.ui.text.TextLinkStyles(
                        style = SpanStyle(color = linkColor)
                    )
                ) { annotation ->
                    val urlStr = (annotation as? androidx.compose.ui.text.LinkAnnotation.Url)?.url ?: rawUrl
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlStr)))
                    }
                }
                withLink(link) {
                    append(rawUrl)
                }
                last = start + m.value.length
                val strippedTail = m.value.substring(rawUrl.length)
                if (strippedTail.isNotEmpty()) {
                    append(strippedTail)
                }
            }
            if (last < text.length) append(text.substring(last))
        }
    }

    val textStyle = androidx.compose.ui.text.TextStyle(
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = color
    )

    if (onNeutralClick != null) {
        androidx.compose.foundation.text.BasicText(
            text = annotated,
            modifier = modifier.clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = onNeutralClick
            ),
            style = textStyle,
            maxLines = maxLines,
            overflow = overflow,
            softWrap = softWrap
        )
    } else {
        androidx.compose.foundation.text.BasicText(
            text = annotated,
            modifier = modifier,
            style = textStyle,
            maxLines = maxLines,
            overflow = overflow,
            softWrap = softWrap
        )
    }
}
