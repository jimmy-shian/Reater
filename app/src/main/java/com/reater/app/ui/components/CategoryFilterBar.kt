package com.reater.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.ui.AvatarIcons

/**
 * 第二列分類下拉選單：供 全部 / 未讀 / 收藏 三頁共用。
 * selectedId == null 全部；== -1L 未分類；其餘為分類 id。
 * 展開/收合帶 expand + fade 過度動畫，箭頭同步旋轉。
 */
@Composable
fun CategoryFilterBar(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    counts: Map<Long?, Int> = emptyMap(),
    totalCount: Int = 0,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "filterArrow"
    )

    val selectedCat = categories.firstOrNull { it.id == selectedId }
    val selectedLabel = when (selectedId) {
        null -> "全部"
        -1L -> "未分類"
        else -> selectedCat?.name ?: "全部"
    }
    val selectedCount = when (selectedId) {
        null -> totalCount
        else -> counts[selectedId] ?: 0
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 收合列：當前選擇 + 筆數 + 箭頭
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (selectedCat != null) {
                Icon(
                    painter = painterResource(id = AvatarIcons.getDrawableRes(selectedCat.avatarIcon)),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = "分類：$selectedLabel",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$selectedCount 筆",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = if (expanded) "收合分類選單" else "展開分類選單",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(arrowRotation)
            )
        }

        // 下拉選單：過度動畫展開
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    FilterOption(
                        label = "全部",
                        count = totalCount,
                        selected = selectedId == null,
                        onClick = {
                            onSelect(null)
                            expanded = false
                        }
                    )
                    FilterOption(
                        label = "未分類",
                        count = counts[-1L] ?: 0,
                        selected = selectedId == -1L,
                        onClick = {
                            onSelect(-1L)
                            expanded = false
                        }
                    )
                    categories.forEach { cat ->
                        FilterOption(
                            label = cat.name,
                            count = counts[cat.id] ?: 0,
                            selected = selectedId == cat.id,
                            avatarIcon = cat.avatarIcon,
                            onClick = {
                                onSelect(cat.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterOption(
    label: String,
    count: Int,
    selected: Boolean,
    avatarIcon: String? = null,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        if (avatarIcon != null) {
            Icon(
                painter = painterResource(id = AvatarIcons.getDrawableRes(avatarIcon)),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1
        )
        if (selected) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
