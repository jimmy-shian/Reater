package com.reater.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
 * 編輯/新增「特定文字智慧分類規則」對話框：
 * 支援輸入多個比對文字 -> 自動歸入 APP 目標分類，並可選填自訂純文字標籤（無 # 符號）。
 * 採用固定底部操作列與彈性可滾動內容區，防止軟體鍵盤或小螢幕下按鈕跑版。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditCustomKeywordRuleDialog(
    initialRule: CustomKeywordRule? = null,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (CustomKeywordRule) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    var keywordInput by rememberSaveable(initialRule?.id) { mutableStateOf(initialRule?.keyword ?: "") }
    var targetTagName by rememberSaveable(initialRule?.id) { mutableStateOf(initialRule?.targetTagName ?: "") }
    var selectedCategoryId by rememberSaveable(initialRule?.id) {
        mutableStateOf<Long?>(initialRule?.targetCategoryId ?: categories.firstOrNull()?.id)
    }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val selectedCategory = remember(selectedCategoryId, categories) {
        categories.firstOrNull { it.id == selectedCategoryId } ?: categories.firstOrNull()
    }

    // 解析多個比對文字（以逗號、頓號、分號或空白分隔）
    val parsedKeywords = remember(keywordInput) {
        keywordInput.split(Regex("[,，、;；\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    val canSave = parsedKeywords.isNotEmpty() && (selectedCategory != null || targetTagName.trim().isNotBlank())

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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clickable(enabled = false) {}
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (initialRule == null) "新增關鍵字規則" else "編輯關鍵字規則",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "關閉")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // 可滾動內容區（加 weight 確保不會擠壓底部按鈕）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 簡明提示
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "當貼文內文或留言包含以下文字時，儲存時將自動歸入指定的 APP 分類。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                lineHeight = 18.sp
                            )
                        }

                        // 1. 比對文字輸入
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "比對文字（可多個關鍵字）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = keywordInput,
                                onValueChange = { keywordInput = it },
                                placeholder = { Text("輸入文字，例如：AI, 科技, ChatGPT") },
                                singleLine = false,
                                minLines = 2,
                                maxLines = 3,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (parsedKeywords.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                ) {
                                    parsedKeywords.forEach { kw ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                        ) {
                                            Text(
                                                text = "「$kw」",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. 目標分類（Reater APP 分類）
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "目標分類（APP 分類）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { categoryDropdownExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
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
                                        Text(
                                            text = "選擇目標分類",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
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

                        // 3. 自訂附加標籤（選填純文字，不含 #）
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "自訂標籤（選填）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = targetTagName,
                                onValueChange = { targetTagName = it },
                                placeholder = { Text("選填純文字，例如：筆記、重要") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // 固定底部操作列（永遠可見）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                enabled = canSave,
                                shape = RoundedCornerShape(8.dp),
                                onClick = {
                                    val cleanedTag = targetTagName.trim().removePrefix("#").trim()
                                    val targetTypeResolved = if (cleanedTag.isNotBlank() && selectedCategory != null) "BOTH"
                                        else if (cleanedTag.isNotBlank()) "TAG"
                                        else "CATEGORY"
                                    onSave(
                                        CustomKeywordRule(
                                            id = initialRule?.id ?: java.util.UUID.randomUUID().toString(),
                                            keyword = keywordInput.trim(),
                                            targetType = targetTypeResolved,
                                            targetCategoryId = selectedCategory?.id,
                                            targetCategoryName = selectedCategory?.name.orEmpty(),
                                            targetTagName = cleanedTag,
                                            isEnabled = initialRule?.isEnabled ?: true
                                        )
                                    )
                                    onDismiss()
                                }
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
