package com.reater.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reater.app.R

/**
 * Threads 風格數字格式化：1200 → 1.2K，2500000 → 2.5M。
 * <= 0 回空字串，呼叫端以此判斷是否顯示數字。
 */
fun formatThreadsCount(count: Int): String {
    if (count <= 0) return ""
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0).replace(".0M", "M")
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0).replace(".0K", "K")
        else -> count.toString()
    }
}

/**
 * 模擬 Threads 原生貼文的互動數值列：
 * [愛心 8,099] [留言 115] [轉發 1,142] [分享]
 *
 * - 圖示使用 Threads 風格 SVG（res/drawable/ic_threads_*），單行不換行；
 * - 數字只有 >0 才顯示（與 Threads 一致），溢出省略；
 * - 祖先區塊 / 無數據時直接傳 0，即只顯示四個圖示。
 *
 * @param likeCount 愛心數，<=0 不顯示數字
 * @param replyCount 留言數，<=0 不顯示數字
 * @param repostCount 轉發數，<=0 不顯示數字
 * @param shareCount 分享數，null 或 <=0 不顯示數字（目前後端無此欄位，預設僅顯示圖示）
 * @param iconSize 圖示大小，預設 16.dp（留言列可用 15.dp）
 * @param tint 圖示與數字顏色，預設 outline（詳情頁/卡片共用）
 */
@Composable
fun ThreadsStatsRow(
    likeCount: Int = 0,
    replyCount: Int = 0,
    repostCount: Int = 0,
    shareCount: Int? = null,
    modifier: Modifier = Modifier,
    iconSize: Dp = 16.dp,
    tint: Color = MaterialTheme.colorScheme.outline
) {
    val likeStr = formatThreadsCount(likeCount)
    val replyStr = formatThreadsCount(replyCount)
    val repostStr = formatThreadsCount(repostCount)
    val shareStr = if (shareCount != null) formatThreadsCount(shareCount) else ""

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
    ) {
        ThreadsStatItem(
            drawableRes = R.drawable.ic_threads_heart,
            contentDescription = "讚",
            countText = likeStr,
            iconSize = iconSize,
            tint = tint
        )
        ThreadsStatItem(
            drawableRes = R.drawable.ic_threads_comment,
            contentDescription = "回覆",
            countText = replyStr,
            iconSize = iconSize,
            tint = tint
        )
        ThreadsStatItem(
            drawableRes = R.drawable.ic_threads_repost,
            contentDescription = "轉發",
            countText = repostStr,
            iconSize = iconSize,
            tint = tint
        )
        ThreadsStatItem(
            drawableRes = R.drawable.ic_threads_share,
            contentDescription = "分享",
            countText = shareStr,
            iconSize = iconSize,
            tint = tint
        )
    }
}

@Composable
private fun ThreadsStatItem(
    drawableRes: Int,
    contentDescription: String,
    countText: String,
    iconSize: Dp,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id = drawableRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
        if (countText.isNotBlank()) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = countText,
                fontSize = 12.sp,
                color = tint,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
