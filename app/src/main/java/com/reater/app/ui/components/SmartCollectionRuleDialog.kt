package com.reater.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.data.local.entity.ItemDetail
import com.reater.app.data.local.entity.KeywordEntity
import com.reater.app.data.local.entity.SavedCollectionEntity
import com.reater.app.domain.*
import com.reater.app.ui.AvatarIcons

/**
 * 編輯智慧分類規則對話框：
 * 完整實作「固定 Header + 垂直滾動內容 + 固定 Insets Footer + 結構化條件 (AND/OR) + 專用 Editor + 即時符合預覽 (含命中原因)」。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmartCollectionRuleDialog(
    collection: SavedCollectionEntity,
    initialRule: CollectionRule,
    categories: List<CategoryEntity>,
    allItems: List<ItemDetail>,
    keywords: List<KeywordEntity> = emptyList(),
    isPro: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, iconName: String, rule: CollectionRule) -> Unit,
    onDelete: (() -> Unit)? = null,
    onResetToDefault: (() -> Unit)? = null
) {
    var name by rememberSaveable(collection.id) {
        mutableStateOf(defaultCollectionDisplayName(collection.name, collection.iconName))
    }
    var iconName by rememberSaveable(collection.id) { mutableStateOf(collection.iconName) }
    var enabled by rememberSaveable(collection.id) { mutableStateOf(initialRule.enabled) }
    var matchMode by rememberSaveable(collection.id) { mutableStateOf(initialRule.matchMode.ifBlank { "AND" }) }

    // 條件清單狀態
    val criteria = remember(collection.id) {
        val initialCriteria = initialRule.normalizedCriteria()
        mutableStateListOf<RuleCriterion>().apply {
            if (initialCriteria.isNotEmpty()) {
                addAll(initialCriteria)
            } else {
                add(RuleCriterion(RuleField.LIKES, ComparisonOperator.GREATER_EQUAL, "100"))
            }
        }
    }

    var showIconPicker by remember { mutableStateOf(false) }
    var showPreviewSheet by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var matchModeMenuOpen by remember { mutableStateOf(false) }

    val isDefault = remember(collection.id) {
        SmartCollectionEngine.isDefaultCollection(collection.name, collection.iconName)
    }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // 當前規則物件（用於即時預覽與 dirty 判定）
    val currentRule = remember(enabled, matchMode, criteria.toList()) {
        CollectionRule(
            enabled = enabled,
            matchMode = matchMode,
            criteria = criteria.toList()
        )
    }

    // 即時比對貼文（響應式即時更新）
    val matchedItems = remember(currentRule, allItems, categories, keywords) {
        allItems.filter { SmartCollectionEngine.matchesItem(currentRule, it, categories, keywords) }
    }

    val isDirty = remember(name, iconName, enabled, matchMode, criteria.toList()) {
        name != defaultCollectionDisplayName(collection.name, collection.iconName) ||
            iconName != collection.iconName ||
            enabled != initialRule.enabled ||
            matchMode != initialRule.matchMode ||
            criteria.toList() != initialRule.normalizedCriteria()
    }

    fun requestDismiss() {
        if (isDirty) showDiscardConfirm = true
        else onDismiss()
    }

    Dialog(
        onDismissRequest = { requestDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .dismissFocusOnTap(focusManager)
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
                    .fillMaxHeight()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ==========================================
                    // 一、固定 Header（不跟隨內容滾動）
                    // ==========================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 10.dp, top = 14.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "編輯智慧分類規則",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { requestDismiss() }) {
                            Icon(Icons.Default.Close, contentDescription = "關閉")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // ==========================================
                    // 二、Scrollable Content（中間垂直捲動區域）
                    // ==========================================
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. 【基本設定】
                        Text(
                            text = "基本設定",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // SVG 分類圖示按鈕
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { showIconPicker = true }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(AvatarIcons.getDrawableRes(iconName)),
                                        contentDescription = "更換圖示",
                                        modifier = Modifier.size(24.dp),
                                        tint = Color.Unspecified
                                    )
                                }
                            }

                            // 規則名稱
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("規則名稱") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 啟用此智慧規則 Toggle
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "啟用此智慧規則",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "符合條件時，自動出現在此智慧分類檢視中",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = enabled, onCheckedChange = { enabled = it })
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        // 2. 【條件模式】
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "條件模式",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            Box {
                                OutlinedButton(
                                    onClick = { matchModeMenuOpen = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    val modeLabel = if (matchMode.equals("OR", ignoreCase = true)) {
                                        "任一條件符合（OR）"
                                    } else {
                                        "所有條件皆符合（AND）"
                                    }
                                    Text(
                                        text = modeLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                }

                                DropdownMenu(
                                    expanded = matchModeMenuOpen,
                                    onDismissRequest = { matchModeMenuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("所有條件皆符合（AND）", style = MaterialTheme.typography.bodyMedium) },
                                        trailingIcon = {
                                            if (!matchMode.equals("OR", ignoreCase = true)) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        onClick = {
                                            matchMode = "AND"
                                            matchModeMenuOpen = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("任一條件符合（OR）", style = MaterialTheme.typography.bodyMedium) },
                                        trailingIcon = {
                                            if (matchMode.equals("OR", ignoreCase = true)) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        onClick = {
                                            matchMode = "OR"
                                            matchModeMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        // 3. 【符合條件】
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "符合條件",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            // 條件卡片清單
                            criteria.forEachIndexed { index, criterion ->
                                ConditionCard(
                                    criterion = criterion,
                                    categories = categories,
                                    onUpdate = { updated -> criteria[index] = updated },
                                    onDelete = { criteria.removeAt(index) },
                                    canDelete = criteria.size > 1
                                )
                            }

                            // ＋ 新增條件按鈕
                            OutlinedButton(
                                onClick = {
                                    criteria.add(
                                        RuleCriterion(
                                            field = RuleField.CONTENT,
                                            operator = ComparisonOperator.CONTAINS,
                                            value = ""
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("＋ 新增條件", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        // 4. 【預覽】
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp)
                                ) {
                                    Text(
                                        text = "符合內容：${matchedItems.size} 篇",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = if (matchedItems.isEmpty()) "目前無符合條件內容" else "即時比對中",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { showPreviewSheet = true },
                                    enabled = matchedItems.isNotEmpty(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("預覽符合內容", maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // ==========================================
                    // 三、固定 Footer（不跟隨內容滾動，避開導覽列）
                    // ==========================================
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isDefault) {
                                TextButton(
                                    onClick = { showResetConfirm = true },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Text("恢復預設規則", style = MaterialTheme.typography.bodyMedium)
                                }
                            } else if (onDelete != null) {
                                TextButton(
                                    onClick = { showDeleteConfirm = true },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("刪除規則", style = MaterialTheme.typography.bodyMedium)
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { requestDismiss() },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("取消")
                                }
                                Button(
                                    onClick = {
                                        val trimmed = name.trim().ifBlank { collection.name }
                                        onSave(trimmed, iconName, currentRule)
                                        onDismiss()
                                    },
                                    enabled = isPro,
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

    // 圖示選擇器 Dialog
    if (showIconPicker) {
        IconPickerDialog(
            currentIcon = iconName,
            onSelect = {
                iconName = it
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }

    // 預覽貼文 Modal（顯示命中原因）
    if (showPreviewSheet) {
        MatchedPostsPreviewDialog(
            posts = matchedItems,
            rule = currentRule,
            categories = categories,
            keywords = keywords,
            onDismiss = { showPreviewSheet = false }
        )
    }

    // 放棄未儲存修改確認
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("放棄未儲存的修改？") },
            text = { Text("你有尚未儲存的修改，離開後這些修改將會遺失。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    onDismiss()
                }) {
                    Text("放棄修改", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("繼續編輯")
                }
            }
        )
    }

    // 刪除規則確認
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("確定刪除規則？") },
            text = { Text("刪除此規則將移除該智慧分類，但原始貼文仍會完整保留。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete?.invoke()
                    onDismiss()
                }) {
                    Text("確定刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    // 恢復預設規則確認
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("恢復預設規則？") },
            text = { Text("將會把條件還原為「${collection.name}」的系統初始條件。") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    val def = SmartCollectionEngine.getDefaultRule(collection.name, collection.iconName)
                    name = defaultCollectionDisplayName(collection.name, collection.iconName)
                    enabled = def.enabled
                    matchMode = def.matchMode
                    criteria.clear()
                    criteria.addAll(def.normalizedCriteria())
                    onResetToDefault?.invoke()
                }) {
                    Text("恢復預設")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            }
        )
    }
}

/**
 * Condition Card 重新設計：
 * [欄位 ▼] [運算子 ▼] [刪除 SVG]
 * 下方自適應特定 Editor
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionCard(
    criterion: RuleCriterion,
    categories: List<CategoryEntity>,
    onUpdate: (RuleCriterion) -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    var fieldMenuOpen by remember { mutableStateOf(false) }
    var opMenuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 第一列：[欄位 ▼] [運算子 ▼] [刪除]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. 欄位 Dropdown（具分組、合理寬度與高度、左對齊）
                Box(modifier = Modifier.weight(1.15f)) {
                    OutlinedButton(
                        onClick = { fieldMenuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = criterion.field.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                    }

                    FieldDropdownMenu(
                        expanded = fieldMenuOpen,
                        onDismissRequest = { fieldMenuOpen = false },
                        onSelectField = { newField ->
                            fieldMenuOpen = false
                            if (newField != criterion.field) {
                                // 重要原則：修改欄位時清除不相容資料，自動重置為適配預設
                                val newCriterion = createDefaultCriterionForField(newField, categories)
                                onUpdate(newCriterion)
                            }
                        }
                    )
                }

                // 2. 運算子 Dropdown
                val availableOps = getAvailableOperatorsForField(criterion.field)
                val showOpSelector = availableOps.size > 1

                Box(modifier = Modifier.weight(0.95f)) {
                    OutlinedButton(
                        onClick = { if (showOpSelector) opMenuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = showOpSelector
                    ) {
                        Text(
                            text = criterion.operator.symbol,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (showOpSelector) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (showOpSelector) {
                        DropdownMenu(
                            expanded = opMenuOpen,
                            onDismissRequest = { opMenuOpen = false }
                        ) {
                            availableOps.forEach { op ->
                                DropdownMenuItem(
                                     text = { Text(op.symbol, style = MaterialTheme.typography.bodySmall) },
                                    trailingIcon = {
                                        if (criterion.operator == op) {
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                    onClick = {
                                        opMenuOpen = false
                                        onUpdate(criterion.copy(operator = op))
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. 刪除按鈕靠右
                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "刪除條件",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // 第二列：依不同欄位提供不同 Editor（文字型、數值型、狀態型、布林型、時間型、分類關鍵字型）
            ConditionValueEditor(
                criterion = criterion,
                categories = categories,
                onUpdate = onUpdate
            )
        }
    }
}

/**
 * 欄位下拉清單：依類型分組（內容、狀態、互動、時間、智慧分類）
 */
@Composable
private fun FieldDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onSelectField: (RuleField) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .widthIn(min = 220.dp, max = 280.dp)
            .heightIn(max = 360.dp)
    ) {
        val groups = listOf(
            "內容" to listOf(
                RuleField.AUTHOR,
                RuleField.TAG,
                RuleField.CATEGORY,
                RuleField.CONTENT,
                RuleField.COMMENT_TEXT,
                RuleField.AI_SUMMARY,
                RuleField.MANUAL_NOTE
            ),
            "狀態" to listOf(
                RuleField.FAVORITE,
                RuleField.READ_STATUS,
                RuleField.HAS_MEDIA,
                RuleField.HAS_IMAGE,
                RuleField.HAS_VIDEO
            ),
            "互動數據" to listOf(
                RuleField.LIKES,
                RuleField.REPLIES,
                RuleField.REPOSTS,
                RuleField.SHARES
            ),
            "時間" to listOf(
                RuleField.SAVED_DATE
            ),
            "智慧分類" to listOf(
                RuleField.CONTENT_MATCH_CATEGORY_KEYWORDS,
                RuleField.COMMENT_MATCH_CATEGORY_KEYWORDS
            )
        )

        groups.forEachIndexed { groupIndex, (groupName, fields) ->
            if (groupIndex > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }
            fields.forEach { field ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = field.label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = { onSelectField(field) },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * 依欄位產生預設條件設定（清除不相容舊資料）
 */
private fun createDefaultCriterionForField(
    field: RuleField,
    categories: List<CategoryEntity>
): RuleCriterion {
    val defaultCategoryName = categories.firstOrNull()?.name ?: "GPT畫圖"
    val defaultCategoryId = categories.firstOrNull()?.id

    return when (field) {
        RuleField.LIKES -> RuleCriterion(field, ComparisonOperator.GREATER_EQUAL, "100")
        RuleField.REPLIES -> RuleCriterion(field, ComparisonOperator.GREATER_EQUAL, "20")
        RuleField.REPOSTS -> RuleCriterion(field, ComparisonOperator.GREATER_EQUAL, "10")
        RuleField.SHARES -> RuleCriterion(field, ComparisonOperator.GREATER_EQUAL, "5")
        RuleField.AUTHOR, RuleField.TAG, RuleField.CONTENT, RuleField.COMMENT_TEXT,
        RuleField.AI_SUMMARY, RuleField.MANUAL_NOTE -> {
            RuleCriterion(field, ComparisonOperator.CONTAINS, "")
        }
        RuleField.CATEGORY -> {
            RuleCriterion(field, ComparisonOperator.EQUALS, defaultCategoryId?.toString() ?: "")
        }
        RuleField.FAVORITE -> RuleCriterion(field, ComparisonOperator.EQUALS, "true")
        RuleField.READ_STATUS -> RuleCriterion(field, ComparisonOperator.EQUALS, "未讀")
        RuleField.HAS_MEDIA, RuleField.HAS_IMAGE, RuleField.HAS_VIDEO -> {
            RuleCriterion(field, ComparisonOperator.EQUALS, "true")
        }
        RuleField.SAVED_DATE -> RuleCriterion(field, ComparisonOperator.WITHIN_DAYS, "30")
        RuleField.CONTENT_MATCH_CATEGORY_KEYWORDS -> {
            RuleCriterion(
                field = field,
                operator = ComparisonOperator.CONTAINS,
                value = defaultCategoryName,
                config = RuleConditionConfig(
                    categoryId = defaultCategoryId,
                    categoryName = defaultCategoryName,
                    includeTitle = true,
                    includeBody = true,
                    includeComments = false,
                    includeAiSummary = false,
                    includeManualNote = false
                )
            )
        }
        RuleField.COMMENT_MATCH_CATEGORY_KEYWORDS -> {
            RuleCriterion(
                field = field,
                operator = ComparisonOperator.CONTAINS,
                value = defaultCategoryName,
                config = RuleConditionConfig(
                    categoryId = defaultCategoryId,
                    categoryName = defaultCategoryName
                )
            )
        }
    }
}

/**
 * 取得特定欄位支援的運算子清單
 */
private fun getAvailableOperatorsForField(field: RuleField): List<ComparisonOperator> {
    return when (field) {
        RuleField.LIKES, RuleField.REPLIES, RuleField.REPOSTS, RuleField.SHARES -> listOf(
            ComparisonOperator.GREATER_EQUAL,
            ComparisonOperator.GREATER_THAN,
            ComparisonOperator.EQUALS,
            ComparisonOperator.LESS_EQUAL,
            ComparisonOperator.LESS_THAN,
            ComparisonOperator.BETWEEN
        )
        RuleField.AUTHOR, RuleField.TAG, RuleField.CONTENT, RuleField.COMMENT_TEXT,
        RuleField.AI_SUMMARY, RuleField.MANUAL_NOTE -> listOf(
            ComparisonOperator.CONTAINS,
            ComparisonOperator.NOT_CONTAINS,
            ComparisonOperator.EQUALS,
            ComparisonOperator.NOT_EQUALS,
            ComparisonOperator.CONTAINS_ANY,
            ComparisonOperator.CONTAINS_ALL
        )
        RuleField.SAVED_DATE -> listOf(
            ComparisonOperator.WITHIN_DAYS,
            ComparisonOperator.OLDER_THAN_DAYS,
            ComparisonOperator.NEWER_THAN_DAYS
        )
        RuleField.CATEGORY, RuleField.FAVORITE, RuleField.READ_STATUS,
        RuleField.HAS_MEDIA, RuleField.HAS_IMAGE, RuleField.HAS_VIDEO,
        RuleField.CONTENT_MATCH_CATEGORY_KEYWORDS, RuleField.COMMENT_MATCH_CATEGORY_KEYWORDS -> listOf(
            ComparisonOperator.EQUALS
        )
    }
}

/**
 * 依欄位呈現不同的 Value Editor
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConditionValueEditor(
    criterion: RuleCriterion,
    categories: List<CategoryEntity>,
    onUpdate: (RuleCriterion) -> Unit
) {
    var catDropdownOpen by remember { mutableStateOf(false) }

    when (criterion.field) {
        // ----------------------------------------------------
        // 1. 數值型 Editor（按讚數、留言數、轉發數、分享數）
        // ----------------------------------------------------
        RuleField.LIKES, RuleField.REPLIES, RuleField.REPOSTS, RuleField.SHARES -> {
            if (criterion.operator == ComparisonOperator.BETWEEN) {
                val parts = criterion.value.split("..", "~", "-")
                var minStr by remember(criterion.value) { mutableStateOf(parts.getOrNull(0)?.trim().orEmpty()) }
                var maxStr by remember(criterion.value) { mutableStateOf(parts.getOrNull(1)?.trim().orEmpty()) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = minStr,
                        onValueChange = { newVal ->
                            if (newVal.all { it.isDigit() }) {
                                minStr = newVal
                                onUpdate(criterion.copy(value = "$minStr..$maxStr"))
                            }
                        },
                        label = { Text("最小值") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Text("～", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = maxStr,
                        onValueChange = { newVal ->
                            if (newVal.all { it.isDigit() }) {
                                maxStr = newVal
                                onUpdate(criterion.copy(value = "$minStr..$maxStr"))
                            }
                        },
                        label = { Text("最大值") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                OutlinedTextField(
                    value = criterion.value,
                    onValueChange = { newVal ->
                        if (newVal.all { it.isDigit() }) {
                            onUpdate(criterion.copy(value = newVal))
                        }
                    },
                    label = { Text("${criterion.field.label}數值") },
                    placeholder = {
                        Text(
                            when (criterion.field) {
                                RuleField.LIKES -> "例如 100"
                                RuleField.REPLIES -> "例如 20"
                                RuleField.REPOSTS -> "例如 10"
                                RuleField.SHARES -> "例如 5"
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ----------------------------------------------------
        // 2. 文字型 Editor（作者、標籤、內文/標題、留言內容、AI摘要、筆記）
        // ----------------------------------------------------
        RuleField.AUTHOR, RuleField.TAG, RuleField.CONTENT, RuleField.COMMENT_TEXT,
        RuleField.AI_SUMMARY, RuleField.MANUAL_NOTE -> {
            val hint = when (criterion.operator) {
                ComparisonOperator.CONTAINS_ANY -> "多個關鍵字以空白分開，命中任一即可"
                ComparisonOperator.CONTAINS_ALL -> "多個關鍵字以空白分開，需全數命中"
                else -> null
            }

            OutlinedTextField(
                value = criterion.value,
                onValueChange = { onUpdate(criterion.copy(value = it)) },
                label = { Text(criterion.field.label) },
                supportingText = hint?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
                placeholder = {
                    Text(
                        when (criterion.field) {
                            RuleField.AUTHOR -> "例如 @user 或 暱稱"
                            RuleField.TAG -> "例如 AI"
                            RuleField.CONTENT -> "例如 Midjourney 或 Prompt"
                            RuleField.COMMENT_TEXT -> "例如 教學 資源"
                            RuleField.AI_SUMMARY -> "例如 精華 重點"
                            RuleField.MANUAL_NOTE -> "例如 必看"
                        }
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ----------------------------------------------------
        // 3. 狀態型 Editor（收藏狀態、閱讀狀態）
        // ----------------------------------------------------
        RuleField.FAVORITE -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = criterion.value.equals("true", ignoreCase = true) || criterion.value == "已收藏",
                    onClick = { onUpdate(criterion.copy(value = "true")) },
                    label = { Text("已收藏") }
                )
                FilterChip(
                    selected = criterion.value.equals("false", ignoreCase = true) || criterion.value == "未收藏",
                    onClick = { onUpdate(criterion.copy(value = "false")) },
                    label = { Text("未收藏") }
                )
            }
        }
        RuleField.READ_STATUS -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = criterion.value.contains("未讀"),
                    onClick = { onUpdate(criterion.copy(value = "未讀")) },
                    label = { Text("未讀") }
                )
                FilterChip(
                    selected = criterion.value.contains("已讀"),
                    onClick = { onUpdate(criterion.copy(value = "已讀")) },
                    label = { Text("已讀") }
                )
            }
        }

        // ----------------------------------------------------
        // 4. 布林型 Editor（包含媒體、包含圖片、包含影片）
        // ----------------------------------------------------
        RuleField.HAS_MEDIA, RuleField.HAS_IMAGE, RuleField.HAS_VIDEO -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = criterion.value.equals("true", ignoreCase = true) || criterion.value == "是",
                    onClick = { onUpdate(criterion.copy(value = "true")) },
                    label = { Text("是") }
                )
                FilterChip(
                    selected = criterion.value.equals("false", ignoreCase = true) || criterion.value == "否",
                    onClick = { onUpdate(criterion.copy(value = "false")) },
                    label = { Text("否") }
                )
            }
        }

        // ----------------------------------------------------
        // 5. 時間型 Editor（收藏時間）
        // ----------------------------------------------------
        RuleField.SAVED_DATE -> {
            OutlinedTextField(
                value = criterion.value,
                onValueChange = { newVal ->
                    if (newVal.all { it.isDigit() }) onUpdate(criterion.copy(value = newVal))
                },
                label = { Text("天數") },
                suffix = { Text("天") },
                placeholder = { Text("例如 30") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ----------------------------------------------------
        // 6. 分類（9.1 分類：貼文目前所屬 Category）
        // ----------------------------------------------------
        RuleField.CATEGORY -> {
            val selectedCatName = categories.firstOrNull { it.id.toString() == criterion.value }?.name
                ?: categories.firstOrNull()?.name ?: "選擇分類"

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { catDropdownOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(selectedCatName, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.KeyboardArrowDown, null)
                }

                DropdownMenu(
                    expanded = catDropdownOpen,
                    onDismissRequest = { catDropdownOpen = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name, style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { CategoryIcon(cat, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (criterion.value == cat.id.toString()) {
                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                catDropdownOpen = false
                                onUpdate(criterion.copy(value = cat.id.toString()))
                            }
                        )
                    }
                }
            }
        }

        // ----------------------------------------------------
        // 7. 內容命中分類關鍵字 / 留言命中分類關鍵字
        // ----------------------------------------------------
        RuleField.CONTENT_MATCH_CATEGORY_KEYWORDS, RuleField.COMMENT_MATCH_CATEGORY_KEYWORDS -> {
            val config = criterion.config ?: RuleConditionConfig(
                categoryName = criterion.value.ifBlank { categories.firstOrNull()?.name ?: "" }
            )
            val currentCatName = config.categoryName.ifBlank { criterion.value }.ifBlank {
                categories.firstOrNull()?.name ?: "GPT畫圖"
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 分類選擇按鈕
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { catDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("指定分類：$currentCatName", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.KeyboardArrowDown, null)
                    }

                    DropdownMenu(
                        expanded = catDropdownOpen,
                        onDismissRequest = { catDropdownOpen = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name, style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = { CategoryIcon(cat, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (currentCatName == cat.name) {
                                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    catDropdownOpen = false
                                    onUpdate(
                                        criterion.copy(
                                            value = cat.name,
                                            config = config.copy(
                                                categoryId = cat.id,
                                                categoryName = cat.name
                                            )
                                        )
                                    )
                                }
                            )
                        }
                    }
                }

                // 針對「內容命中分類關鍵字」，提供來源選擇
                if (criterion.field == RuleField.CONTENT_MATCH_CATEGORY_KEYWORDS) {
                    Text(
                        text = "比對來源：",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = config.includeTitle,
                            onClick = {
                                onUpdate(criterion.copy(config = config.copy(includeTitle = !config.includeTitle)))
                            },
                            label = { Text("標題") }
                        )
                        FilterChip(
                            selected = config.includeBody,
                            onClick = {
                                onUpdate(criterion.copy(config = config.copy(includeBody = !config.includeBody)))
                            },
                            label = { Text("內文") }
                        )
                        FilterChip(
                            selected = config.includeComments,
                            onClick = {
                                onUpdate(criterion.copy(config = config.copy(includeComments = !config.includeComments)))
                            },
                            label = { Text("留言") }
                        )
                        FilterChip(
                            selected = config.includeAiSummary,
                            onClick = {
                                onUpdate(criterion.copy(config = config.copy(includeAiSummary = !config.includeAiSummary)))
                            },
                            label = { Text("AI 摘要") }
                        )
                        FilterChip(
                            selected = config.includeManualNote,
                            onClick = {
                                onUpdate(criterion.copy(config = config.copy(includeManualNote = !config.includeManualNote)))
                            },
                            label = { Text("筆記") }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 符合貼文清單預覽 Dialog（含命中原因呈現）
 */
@Composable
private fun MatchedPostsPreviewDialog(
    posts: List<ItemDetail>,
    rule: CollectionRule,
    categories: List<CategoryEntity>,
    keywords: List<KeywordEntity>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                // 預覽標題列
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "符合條件內容預覽（共 ${posts.size} 篇）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "關閉")
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                if (posts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "目前無符合條件的貼文",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    // Preview 最多先顯示 3～5 筆，讓使用者清楚知道為什麼這篇被抓進來
                    val previewList = remember(posts) { posts.take(5) }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(previewList, key = { it.item.id }) { post ->
                            val reasons = remember(post, rule, categories, keywords) {
                                SmartCollectionEngine.getMatchReasons(rule, post, categories, keywords)
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = post.item.authorDisplayName.ifBlank { "@${post.item.authorHandle}" },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "♥ ${post.item.likeCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = post.displayBody.ifBlank { "(無內文)" },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (reasons.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                                Text(
                                                    text = "命中條件：",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                reasons.forEach { reason ->
                                                    Text(
                                                        text = "• $reason",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (posts.size > 5) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "… 另有 ${posts.size - 5} 篇符合",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("關閉預覽")
                }
            }
        }
    }
}

/**
 * 分類 SVG 圖示選擇對話框（包含規則專屬 5 個圖示）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPickerDialog(
    currentIcon: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = "選擇規則專屬圖示",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                // 優先展示 5 個專門給規則使用的向量圖示
                Text(
                    text = "智慧規則專用圖示",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AvatarIcons.RULE_ICONS.forEach { iconItem ->
                        val isSelected = iconItem.id.equals(currentIcon, ignoreCase = true)
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { onSelect(iconItem.id) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(iconItem.resId),
                                    contentDescription = iconItem.name,
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "其他可用圖示",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    (AvatarIcons.FREE + AvatarIcons.PRO).forEach { iconItem ->
                        val isSelected = iconItem.id.equals(currentIcon, ignoreCase = true)
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { onSelect(iconItem.id) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(iconItem.resId),
                                    contentDescription = iconItem.name,
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
            }
        }
    }
}
