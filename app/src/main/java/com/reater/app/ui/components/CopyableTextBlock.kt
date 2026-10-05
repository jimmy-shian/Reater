package com.reater.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 可一鍵複製的內文區塊（Prompt / 長文專用）。
 *
 * - 外框：圓角邊框容器，右上固定一顆複製 SVG 鍵，點一下即複製整段文字。
 * - 點擊展開：超過閾值的長文預設收合（[collapsedMaxLines] 行），點內文或
 *   「展開全文」可獨立展開／收起，不影響其他區塊。
 */
@Composable
fun CopyableTextBlock(
    text: String,
    modifier: Modifier = Modifier,
    /** 左上角小標籤，如「母文」「分享」「留言」；null 則只顯示複製鍵 */
    label: String? = null,
    collapsedMaxLines: Int = 6,
    /** 超過此字數或行數才啟用收合 */
    collapseThresholdChars: Int = 200,
    collapseThresholdLines: Int = 7,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 18.sp,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    if (text.isBlank()) return
    val context = LocalContext.current
    var expanded by remember(text) { mutableStateOf(false) }
    val collapsible = remember(text) {
        text.length > collapseThresholdChars || text.lines().size > collapseThresholdLines
    }
    val shownMaxLines = if (!collapsible || expanded) Int.MAX_VALUE else collapsedMaxLines

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                RoundedCornerShape(8.dp)
            )
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!label.isNullOrBlank()) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Reater", text))
                        Toast.makeText(context, "已複製內文", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "複製內文",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            // 點內文空白處即展開／收起（URL 點擊仍由 LinkifiedText 開啟瀏覽器）；
            // 注意：不可再包 Modifier.clickable，否則與 onNeutralClick 雙重觸發互相抵消。
            LinkifiedText(
                text = text,
                fontSize = fontSize,
                lineHeight = lineHeight,
                color = color,
                maxLines = shownMaxLines,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                onNeutralClick = { if (collapsible) expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            )
            if (collapsible) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (expanded) "收起" else "展開全文",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展開全文",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
