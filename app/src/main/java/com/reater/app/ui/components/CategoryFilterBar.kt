package com.reater.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reater.app.data.local.entity.CategoryEntity

/** Shared searchable, animated filter. null = all; -1 = uncategorized. */
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
    Box(modifier.fillMaxWidth()) {
        ReaterDropdownTrigger(expanded, { expanded = !expanded }, label,
            "${if (selectedId == null) totalCount else counts[selectedId] ?: 0} 筆")
        AnimatedDropdownPopup(expanded, { expanded = false }) {
            OutlinedTextField(query, { query = it }, label = { Text("搜尋分類") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(8.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                fun select(id: Long?) { onSelect(id); expanded = false; query = "" }
                ReaterDropdownOption("全部分類", selectedId == null, { select(null) })
                if (query.isBlank() || "未分類".contains(query.trim(), true))
                    ReaterDropdownOption("未分類", selectedId == -1L, { select(-1L) })
                val filtered = categories.filter { it.name.contains(query.trim(), true) }
                filtered.forEach { cat ->
                    ReaterDropdownOption(cat.name, selectedId == cat.id, { select(cat.id) }, subLabel = "${counts[cat.id] ?: 0} 筆")
                }
                if (filtered.isEmpty() && query.isNotBlank()) Text("找不到相符分類", Modifier.padding(14.dp))
            }
        }
    }
}
