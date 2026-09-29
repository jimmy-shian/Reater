package com.reater.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.ui.AvatarIcons

/**
 * PRO Tab 分類篩選列：全部 / 未分類 / 各分類橫滑。
 * selectedId == null 代表全部；selectedId == -1 代表未分類。
 */
@Composable
fun ProCategoryFilterRow(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    showUncategorized: Boolean = true,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedId == null,
            onClick = { onSelect(null) },
            label = { Text("全部", maxLines = 1) }
        )
        if (showUncategorized) {
            FilterChip(
                selected = selectedId == -1L,
                onClick = { onSelect(-1L) },
                label = { Text("未分類", maxLines = 1) }
            )
        }
        categories.forEach { cat ->
            FilterChip(
                selected = selectedId == cat.id,
                onClick = { onSelect(cat.id) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = AvatarIcons.getDrawableRes(cat.avatarIcon)),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Unspecified
                    )
                },
                label = { Text(cat.name, maxLines = 1, softWrap = false) }
            )
        }
    }
}
