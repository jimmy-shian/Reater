package com.reater.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.ui.AvatarIcons

/**
 * 分類徽章：圖示 + 名稱，統一樣式供 PostCard / DetailDialog / 篩選列共用。
 * 傳入 onClick 即變為可點擊（顯示小箭頭），點擊直接展開儲存同款 CategoryDropdown。
 */
@Composable
fun CategoryBadge(
    category: CategoryEntity?,
    modifier: Modifier = Modifier,
    fallbackName: String = "未分類",
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .then(clickableModifier)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            painter = painterResource(id = AvatarIcons.getDrawableRes(category?.avatarIcon)),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = category?.name ?: fallbackName,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "更改分類",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
