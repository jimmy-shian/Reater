package com.reater.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.ui.AvatarIcons

/**
 * 分享頁分類下拉選單：輸入即篩選 + 一鍵新增。
 * - 自訂分類再多也不會橫向爆版
 * - 輸入關鍵字即時過濾；無命中時提供「新增「xxx」」入口
 * - 已選分類顯示圖示 + 名稱，右側 X 可清空回未分類
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    onSelect: (Long?) -> Unit,
    onRequestCreate: (prefill: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var filterText by remember { mutableStateOf("") }

    val selected = categories.firstOrNull { it.id == selectedCategoryId }
    val query = filterText.trim()
    val filtered = remember(categories, query) {
        if (query.isBlank()) categories
        else categories.filter { it.name.contains(query, ignoreCase = true) }
    }
    val exactMatch = query.isNotBlank() &&
        categories.any { it.name.equals(query, ignoreCase = true) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = if (expanded) filterText else (selected?.name ?: "未分類"),
            onValueChange = {
                filterText = it
                expanded = true
            },
            readOnly = false,
            label = { Text("選擇分類（可輸入篩選）") },
            placeholder = { Text("輸入關鍵字篩選，或新增分類") },
            leadingIcon = {
                Icon(
                    painter = painterResource(
                        id = AvatarIcons.getDrawableRes(selected?.avatarIcon ?: "life")
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedCategoryId != null && !expanded) {
                        IconButton(
                            onClick = { onSelect(null) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "清除分類",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    type = androidx.compose.material3.MenuAnchorType.PrimaryEditable,
                    enabled = true
                )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                filterText = ""
            }
        ) {
            DropdownMenuItem(
                text = { Text("未分類") },
                trailingIcon = {
                    if (selectedCategoryId == null) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                onClick = {
                    onSelect(null)
                    expanded = false
                    filterText = ""
                }
            )
            filtered.forEach { cat ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(
                                    id = AvatarIcons.getDrawableRes(cat.avatarIcon)
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.Unspecified
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                cat.name,
                                maxLines = 1,
                                fontSize = 14.sp
                            )
                            if (cat.isDefault) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "內建",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        if (selectedCategoryId == cat.id) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = {
                        onSelect(cat.id)
                        expanded = false
                        filterText = ""
                    }
                )
            }
            // 無命中或想新增：提供建立入口（把輸入文字帶入新建對話框）
            if (query.isNotBlank() && !exactMatch) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("新增「$query」", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = {
                        expanded = false
                        filterText = ""
                        onRequestCreate(query)
                    }
                )
            } else {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("新增分類…", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = {
                        expanded = false
                        filterText = ""
                        onRequestCreate(query)
                    }
                )
            }
            if (categories.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        "共 ${categories.size} 個分類（內建 ${categories.count { it.isDefault }}）",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
