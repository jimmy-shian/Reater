package com.reater.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.reater.app.data.local.entity.CategoryEntity

/** Editable combobox: the editor retains its identity and focus as results change. */
@Composable
fun CategoryDropdown(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    onSelect: (Long?) -> Unit,
    onRequestCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
    categoryQuery: String? = null,
    onQueryChange: ((String) -> Unit)? = null,
    dropdownExpanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onFieldFocused: ((Boolean) -> Unit)? = null
) {
    var localQuery by rememberSaveable { mutableStateOf("") }
    var localExpanded by rememberSaveable { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val expanded = dropdownExpanded ?: localExpanded
    val queryText = categoryQuery ?: localQuery
    val selected = categories.firstOrNull { it.id == selectedCategoryId }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun expand(value: Boolean) { localExpanded = value; onExpandedChange?.invoke(value) }
    fun query(value: String) { localQuery = value; onQueryChange?.invoke(value) }
    fun select(id: Long?) {
        query(categories.firstOrNull { it.id == id }?.name.orEmpty())
        onSelect(id)
        expand(false)
        focusManager.clearFocus()
        keyboard?.hide()
    }
    val normalized = queryText.trim()
    val filtered = categories.filter { normalized.isBlank() || it.name.contains(normalized, true) }
    val canCreate = normalized.isNotBlank() && normalized != "未分類" &&
        normalized.length <= 80 && normalized.none { it.isISOControl() } &&
        categories.none { it.name.trim().equals(normalized, true) }
    BackHandler(enabled = expanded) { expand(false); focusManager.clearFocus(); keyboard?.hide() }
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (focused || expanded) queryText else selected?.name.orEmpty(),
            onValueChange = { query(it); expand(true) },
            label = { Text(if (focused) "搜尋或輸入新分類…" else "選擇分類") },
            placeholder = { Text("未分類") },
            singleLine = true,
            trailingIcon = {
                Row {
                    if (queryText.isNotEmpty() || selectedCategoryId != null) {
                        IconButton(onClick = { query(""); expand(true) }) {
                            Icon(Icons.Default.Clear, "清除分類")
                        }
                    }
                    IconButton(onClick = { expand(!expanded) }) { ReaterDropdownArrow(expanded) }
                }
            },
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                focused = it.isFocused
                onFieldFocused?.invoke(it.isFocused)
                if (it.isFocused) expand(true) else expand(false)
            }
        )
        ReaterDropdownPanel(expanded) {
            Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                ReaterDropdownOption("未分類", selectedCategoryId == null, { select(null) })
                if (filtered.isEmpty()) Text("找不到相符分類", Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (normalized.isNotBlank()) Text("符合分類", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                filtered.forEach { category ->
                    ReaterDropdownOption(category.name, selectedCategoryId == category.id, { select(category.id) })
                }
                if (canCreate) {
                    HorizontalDivider()
                    ReaterDropdownOption("＋ 新增「$normalized」", false, {
                        onRequestCreate(normalized)
                        expand(false)
                        focusManager.clearFocus()
                        keyboard?.hide()
                    })
                }
            }
        }
    }
}
