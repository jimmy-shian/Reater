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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.reater.app.R
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
            label = { Text(if (focused) stringResource(R.string.category_dropdown_search_or_new) else stringResource(R.string.label_select_category)) },
            placeholder = { Text(stringResource(R.string.category_uncategorized)) },
            singleLine = true,
            leadingIcon = { CategoryIcon(selected) },
            trailingIcon = {
                Row {
                    if (queryText.isNotEmpty() || selectedCategoryId != null) {
                        IconButton(onClick = { query(""); expand(true) }) {
                            Icon(Icons.Default.Clear, stringResource(R.string.category_dropdown_clear_desc))
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
                ReaterDropdownOption(stringResource(R.string.category_uncategorized), selectedCategoryId == null, { select(null) })
                if (filtered.isEmpty()) Text(stringResource(R.string.category_dropdown_no_match), Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (normalized.isNotBlank()) Text(stringResource(R.string.category_dropdown_has_match), Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                filtered.forEach { category ->
                    ReaterDropdownOption(category.name, selectedCategoryId == category.id, { select(category.id) },
                        leading = { CategoryIcon(category) })
                }
                if (canCreate) {
                    HorizontalDivider()
                    ReaterDropdownOption(stringResource(R.string.category_dropdown_add_new, normalized), false, {
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
