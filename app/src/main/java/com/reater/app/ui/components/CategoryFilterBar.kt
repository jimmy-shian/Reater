package com.reater.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.reater.app.data.local.entity.CategoryEntity

/**
 * 分類篩選列：緊湊 Dropdown、套用全域字體 Theme Typography、寬度對齊 Header、
 * 限制最大高度並支援內部捲動。
 */
@Composable
fun CategoryFilterBar(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    counts: Map<Long?, Int> = emptyMap(),
    totalCount: Int = 0,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(active) { if (!active) expanded = false }

    val label = when (selectedId) {
        null -> "全部分類"
        -1L -> "未分類"
        else -> categories.firstOrNull { it.id == selectedId }?.name ?: "全部分類"
    }

    val configuration = LocalConfiguration.current
    val maxDropdownListHeight = (configuration.screenHeightDp * 0.52f).dp.coerceIn(180.dp, 360.dp)

    Box(modifier = modifier.fillMaxWidth()) {
        ReaterDropdownTrigger(
            expanded = expanded,
            onClick = { expanded = !expanded },
            title = label,
            value = "${if (selectedId == null) totalCount else counts[selectedId] ?: 0} 筆",
            leading = {
                if (selectedId != null && selectedId != -1L) {
                    CategoryIcon(categories.firstOrNull { it.id == selectedId }, modifier = Modifier.size(20.dp))
                }
            }
        )

        AnimatedDropdownPopup(
            expanded = expanded,
            onDismiss = { expanded = false; query = "" },
            matchAnchorWidth = true
        ) {
            // 緊湊搜尋欄 (48dp，含 Search 圖示、Placeholder、清除鈕)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text("搜尋分類", style = MaterialTheme.typography.bodyMedium)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { query = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清除",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .height(48.dp)
            )

            // 分類列表（超過可用高度內部捲動）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDropdownListHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                fun select(id: Long?) {
                    onSelect(id)
                    expanded = false
                    query = ""
                }

                // 系統項目：全部分類與未分類
                ReaterDropdownOption(
                    text = "全部分類",
                    selected = selectedId == null,
                    subLabel = "$totalCount 筆",
                    onClick = { select(null) }
                )
                if (query.isBlank() || "未分類".contains(query.trim(), ignoreCase = true)) {
                    ReaterDropdownOption(
                        text = "未分類",
                        selected = selectedId == -1L,
                        subLabel = "${counts[-1L] ?: 0} 筆",
                        onClick = { select(-1L) }
                    )
                }

                // 分隔線：將系統篩選與自訂實體分類分開
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // 實體自訂分類
                val filtered = categories.filter { it.name.contains(query.trim(), ignoreCase = true) }
                filtered.forEach { cat ->
                    ReaterDropdownOption(
                        text = cat.name,
                        selected = selectedId == cat.id,
                        subLabel = "${counts[cat.id] ?: 0} 筆",
                        leading = { CategoryIcon(cat, modifier = Modifier.size(20.dp)) },
                        onClick = { select(cat.id) }
                    )
                }

                if (filtered.isEmpty() && query.isNotBlank() && !"未分類".contains(query.trim(), ignoreCase = true)) {
                    Text(
                        text = "找不到相符分類",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}
