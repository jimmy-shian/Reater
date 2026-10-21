package com.reater.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.domain.CustomKeywordRule
import com.reater.app.ui.AvatarIcons

/**
 * 編輯/新增「特定關鍵字智慧分類比對規則」對話框：
 * 支援設定特定關鍵字 -> 歸入指定分類（帶圖示）或指定標籤。
 * 避開系統導覽列與軟體鍵盤，防止按鈕被截斷。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCustomKeywordRuleDialog(
    initialRule: CustomKeywordRule? = null,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (CustomKeywordRule) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    var keyword by rememberSaveable(initialRule?.id) { mutableStateOf(initialRule?.keyword ?: "") }
    var targetType by rememberSaveable(initialRule?.id) { mutableStateOf(initialRule?.targetType ?: "CATEGORY") }
    var selectedCategoryId by rememberSaveable(initialRule?.id) {
        mutableStateOf(initialRule?.targetCategoryId ?: categories.firstOrNull()?.id)
    }
    var targetTagName by rememberSaveable(initialRule?.id) { mutableStateOf(initialRule?.targetTagName ?: "") }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val selectedCategory = remember(selectedCategoryId, categories) {
        categories.firstOrNull { it.id == selectedCategoryId } ?: categories.firstOrNull()
    }

    val canSave = keyword.trim().isNotBlank() && (
        (targetType == "CATEGORY" && selectedCategory != null) ||
        (targetType == "TAG" && targetTagName.trim().isNotBlank())
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { focusManager.clearFocus() }
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable(enabled = false) {}
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (initialRule == null) "新增關鍵字比對規則" else "編輯關鍵字比對規則",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "關閉")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // Content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 提示說明
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "比對內文含有此特定關鍵字時，享有最高優先度，強制自動分類到指定的分類或標籤。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                lineHeight = 18.sp
                            )
                        }

                        // 特定關鍵字輸入
                        OutlinedTextField(
                            value = keyword,
                            onValueChange = { keyword = it },
                            label = { Text("特定關鍵字") },
                            placeholder = { Text("例如：人工智慧、股票、美食、Threads...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 歸類目標選擇：分類 / 標籤
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "自動歸類至",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilterChip(
                                    selected = targetType == "CATEGORY",
                                    onClick = { targetType = "CATEGORY" },
                                    label = { Text("目標分類") },
                                    leadingIcon = if (targetType == "CATEGORY") {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = targetType == "TAG",
                                    onClick = { targetType = "TAG" },
                                    label = { Text("目標標籤") },
                                    leadingIcon = if (targetType == "TAG") {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // 依選擇呈現對應設定
                        if (targetType == "CATEGORY") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "選擇指定分類",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { categoryDropdownExpanded = true },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        if (selectedCategory != null) {
                                            Icon(
                                                painter = painterResource(AvatarIcons.getDrawableRes(selectedCategory.avatarIcon)),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = Color.Unspecified
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = selectedCategory.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        } else {
                                            Text("尚未選擇分類")
                                        }
                                        Spacer(Modifier.weight(1f))
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                    }

                                    DropdownMenu(
                                        expanded = categoryDropdownExpanded,
                                        onDismissRequest = { categoryDropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    ) {
                                        categories.forEach { cat ->
                                            DropdownMenuItem(
                                                leadingIcon = {
                                                    Icon(
                                                        painter = painterResource(AvatarIcons.getDrawableRes(cat.avatarIcon)),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp),
                                                        tint = Color.Unspecified
                                                    )
                                                },
                                                text = { Text(cat.name) },
                                                trailingIcon = if (cat.id == selectedCategory?.id) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                                } else null,
                                                onClick = {
                                                    selectedCategoryId = cat.id
                                                    categoryDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = targetTagName,
                                onValueChange = { targetTagName = it },
                                label = { Text("目標標籤名稱") },
                                placeholder = { Text("例如：精選推薦、AI日報...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // Footer Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (initialRule != null && onDelete != null) {
                            TextButton(
                                onClick = {
                                    onDelete(initialRule.id)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("刪除規則")
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("取消")
                            }

                            Button(
                                onClick = {
                                    val trimmedKw = keyword.trim()
                                    val rule = CustomKeywordRule(
                                        id = initialRule?.id ?: java.util.UUID.randomUUID().toString(),
                                        keyword = trimmedKw,
                                        targetType = targetType,
                                        targetCategoryId = if (targetType == "CATEGORY") selectedCategory?.id else null,
                                        targetCategoryName = if (targetType == "CATEGORY") (selectedCategory?.name ?: "") else "",
                                        targetTagName = if (targetType == "TAG") targetTagName.trim() else "",
                                        isEnabled = initialRule?.isEnabled ?: true
                                    )
                                    onSave(rule)
                                    onDismiss()
                                },
                                enabled = canSave,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("儲存")
                            }
                        }
                    }
                }
            }
        }
    }
}
