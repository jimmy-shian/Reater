package com.reater.app.ui.components

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
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
import com.reater.app.R
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
                            text = stringResource(R.string.smart_collection_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { requestDismiss() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.smart_collection_close))
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
                            text = stringResource(R.string.smart_collection_basic_settings),
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
                                        contentDescription = stringResource(R.string.smart_collection_change_icon_desc),
                                        modifier = Modifier.size(24.dp),
                                        tint = Color.Unspecified
                                    )
                                }
                            }

                            // 規則名稱
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text(stringResource(R.string.smart_collection_rule_name_label)) },
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
                                        text = stringResource(R.string.smart_collection_enabled),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.smart_collection_enabled_desc),
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
                                text = stringResource(R.string.smart_collection_condition_mode),
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
                                        stringResource(R.string.smart_collection_mode_or)
                                    } else {
                                        stringResource(R.string.smart_collection_mode_and)
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
                                        text = { Text(stringResource(R.string.smart_collection_mode_and), style = MaterialTheme.typography.bodyMedium) },
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
                                        text = { Text(stringResource(R.string.smart_collection_mode_or), style = MaterialTheme.typography.bodyMedium) },
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
                                text = stringResource(R.string.smart_collection_conditions_label),
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
                                Text(stringResource(R.string.smart_collection_add_condition), style = MaterialTheme.typography.labelLarge)
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
                                        text = stringResource(R.string.smart_collection_matched_count, matchedItems.size),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = if (matchedItems.isEmpty()) stringResource(R.string.smart_collection_no_matches) else stringResource(R.string.smart_collection_matching_live),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { showPreviewSheet = true },
                                    enabled = matchedItems.isNotEmpty(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.smart_collection_preview_matching), maxLines = 1, softWrap = false)
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
                                    Text(stringResource(R.string.smart_collection_reset_default), style = MaterialTheme.typography.bodyMedium)
                                }
                            } else if (onDelete != null) {
                                TextButton(
                                    onClick = { showDeleteConfirm = true },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(stringResource(R.string.smart_collection_delete_rule), style = MaterialTheme.typography.bodyMedium)
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { requestDismiss() },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.smart_collection_cancel))
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
                                    Text(stringResource(R.string.smart_collection_save))
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
            title = { Text(stringResource(R.string.smart_collection_discard_changes_title)) },
            text = { Text(stringResource(R.string.smart_collection_discard_changes_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    onDismiss()
                }) {
                    Text(stringResource(R.string.smart_collection_discard_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(R.string.smart_collection_continue_editing))
                }
            }
        )
    }

    // 刪除規則確認
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.smart_collection_delete_confirm_title)) },
            text = { Text(stringResource(R.string.smart_collection_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete?.invoke()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.smart_collection_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.smart_collection_cancel)) }
            }
        )
    }

    // 恢復預設規則確認
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.smart_collection_reset_confirm_title)) },
            text = { Text(stringResource(R.string.smart_collection_reset_confirm_body, collection.name)) },
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
                    Text(stringResource(R.string.smart_collection_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(R.string.smart_collection_cancel)) }
            }
        )
    }
}

/**
 * 規則欄位顯示名稱 → 字串資源（顯示用；domain 之 label 僅供內部預設，不再直接顯示）。
 */
@StringRes
private fun fieldLabelRes(field: RuleField): Int = when (field) {
    RuleField.AUTHOR -> R.string.smart_field_author
    RuleField.TAG -> R.string.smart_field_tag
    RuleField.CATEGORY -> R.string.smart_field_category
    RuleField.CONTENT -> R.string.smart_field_content
    RuleField.COMMENT_TEXT -> R.string.smart_field_comment_text
    RuleField.AI_SUMMARY -> R.string.smart_field_ai_summary
    RuleField.MANUAL_NOTE -> R.string.smart_field_manual_note
    RuleField.FAVORITE -> R.string.smart_field_favorite
    RuleField.READ_STATUS -> R.string.smart_field_read_status
    RuleField.HAS_MEDIA -> R.string.smart_field_has_media
    RuleField.HAS_IMAGE -> R.string.smart_field_has_image
    RuleField.HAS_VIDEO -> R.string.smart_field_has_video
    RuleField.LIKES -> R.string.smart_field_likes
    RuleField.REPLIES -> R.string.smart_field_replies
    RuleField.REPOSTS -> R.string.smart_field_reposts
    RuleField.SHARES -> R.string.smart_field_shares
    RuleField.SAVED_DATE -> R.string.smart_field_saved_date
    RuleField.CONTENT_MATCH_CATEGORY_KEYWORDS -> R.string.smart_field_content_match_keywords
    RuleField.COMMENT_MATCH_CATEGORY_KEYWORDS -> R.string.smart_field_comment_match_keywords
}

/**
 * 比較運算子顯示符號/短標籤 → 字串資源（顯示用）。
 */
@StringRes
private fun operatorSymbolRes(op: ComparisonOperator): Int = when (op) {
    ComparisonOperator.EQUALS -> R.string.smart_op_equals
    ComparisonOperator.NOT_EQUALS -> R.string.smart_op_not_equals
    ComparisonOperator.CONTAINS -> R.string.smart_op_contains
    ComparisonOperator.NOT_CONTAINS -> R.string.smart_op_not_contains
    ComparisonOperator.CONTAINS_ANY -> R.string.smart_op_contains_any
    ComparisonOperator.CONTAINS_ALL -> R.string.smart_op_contains_all
    ComparisonOperator.GREATER_EQUAL -> R.string.smart_op_gte
    ComparisonOperator.GREATER_THAN -> R.string.smart_op_gt
    ComparisonOperator.LESS_EQUAL -> R.string.smart_op_lte
    ComparisonOperator.LESS_THAN -> R.string.smart_op_lt
    ComparisonOperator.BETWEEN -> R.string.smart_op_between
    ComparisonOperator.WITHIN_DAYS -> R.string.smart_op_within_days
    ComparisonOperator.OLDER_THAN_DAYS -> R.string.smart_op_older_than_days
    ComparisonOperator.NEWER_THAN_DAYS -> R.string.smart_op_newer_than_days
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
                            text = stringResource(fieldLabelRes(criterion.field)),
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
                            text = stringResource(operatorSymbolRes(criterion.operator)),
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
                                     text = { Text(stringResource(operatorSymbolRes(op)), style = MaterialTheme.typography.bodySmall) },
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
                            contentDescription = stringResource(R.string.smart_collection_delete_condition_desc),
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
            stringResource(R.string.smart_collection_field_group_content) to listOf(
                RuleField.AUTHOR,
                RuleField.TAG,
                RuleField.CATEGORY,
                RuleField.CONTENT,
                RuleField.COMMENT_TEXT,
                RuleField.AI_SUMMARY,
                RuleField.MANUAL_NOTE
            ),
            stringResource(R.string.smart_collection_field_group_status) to listOf(
                RuleField.FAVORITE,
                RuleField.READ_STATUS,
                RuleField.HAS_MEDIA,
                RuleField.HAS_IMAGE,
                RuleField.HAS_VIDEO
            ),
            stringResource(R.string.smart_collection_field_group_interaction) to listOf(
                RuleField.LIKES,
                RuleField.REPLIES,
                RuleField.REPOSTS,
                RuleField.SHARES
            ),
            stringResource(R.string.smart_collection_field_group_time) to listOf(
                RuleField.SAVED_DATE
            ),
            stringResource(R.string.smart_collection_field_group_smart) to listOf(
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
                            text = stringResource(fieldLabelRes(field)),
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
                        label = { Text(stringResource(R.string.smart_collection_hint_min_value)) },
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
                        label = { Text(stringResource(R.string.smart_collection_hint_max_value)) },
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
                    label = { Text(stringResource(fieldLabelRes(criterion.field))) },
                    placeholder = {
                        Text(
                            when (criterion.field) {
                                RuleField.LIKES -> stringResource(R.string.smart_collection_example_likes)
                                RuleField.REPLIES -> stringResource(R.string.smart_collection_example_replies)
                                RuleField.REPOSTS -> stringResource(R.string.smart_collection_example_reposts)
                                RuleField.SHARES -> stringResource(R.string.smart_collection_example_shares)
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
                ComparisonOperator.CONTAINS_ANY -> stringResource(R.string.smart_collection_hint_contains_any)
                ComparisonOperator.CONTAINS_ALL -> stringResource(R.string.smart_collection_hint_contains_all)
                else -> null
            }

            OutlinedTextField(
                value = criterion.value,
                onValueChange = { onUpdate(criterion.copy(value = it)) },
                label = { Text(stringResource(fieldLabelRes(criterion.field))) },
                supportingText = hint?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
                placeholder = {
                    Text(
                        when (criterion.field) {
                            RuleField.AUTHOR -> stringResource(R.string.smart_collection_example_author)
                            RuleField.TAG -> stringResource(R.string.smart_collection_example_tag)
                            RuleField.CONTENT -> stringResource(R.string.smart_collection_example_content)
                            RuleField.COMMENT_TEXT -> stringResource(R.string.smart_collection_example_comment)
                            RuleField.AI_SUMMARY -> stringResource(R.string.smart_collection_example_ai_summary)
                            RuleField.MANUAL_NOTE -> stringResource(R.string.smart_collection_example_manual_note)
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
                    label = { Text(stringResource(R.string.smart_collection_favorite_true_label)) }
                )
                FilterChip(
                    selected = criterion.value.equals("false", ignoreCase = true) || criterion.value == "未收藏",
                    onClick = { onUpdate(criterion.copy(value = "false")) },
                    label = { Text(stringResource(R.string.smart_collection_favorite_false_label)) }
                )
            }
        }
        RuleField.READ_STATUS -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = criterion.value.contains("未讀"),
                    onClick = { onUpdate(criterion.copy(value = "未讀")) },
                    label = { Text(stringResource(R.string.smart_collection_read_status_unread_label)) }
                )
                FilterChip(
                    selected = criterion.value.contains("已讀"),
                    onClick = { onUpdate(criterion.copy(value = "已讀")) },
                    label = { Text(stringResource(R.string.smart_collection_read_status_read_label)) }
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
                    label = { Text(stringResource(R.string.smart_collection_has_media_true_label)) }
                )
                FilterChip(
                    selected = criterion.value.equals("false", ignoreCase = true) || criterion.value == "否",
                    onClick = { onUpdate(criterion.copy(value = "false")) },
                    label = { Text(stringResource(R.string.smart_collection_has_media_false_label)) }
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
                label = { Text(stringResource(R.string.smart_collection_days_label)) },
                suffix = { Text(stringResource(R.string.smart_collection_days_suffix)) },
                placeholder = { Text(stringResource(R.string.smart_collection_days_placeholder)) },
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
                ?: categories.firstOrNull()?.name ?: stringResource(R.string.smart_collection_select_category_hint)

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
                        Text(stringResource(R.string.smart_collection_current_category, currentCatName), style = MaterialTheme.typography.bodyMedium)
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
                        text = stringResource(R.string.smart_collection_match_source_label),
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
                            label = { Text(stringResource(R.string.smart_collection_source_title)) }
                        )
                        FilterChip(
                            selected = config.includeBody,
                            onClick = {
                                onUpdate(criterion.copy(config = config.copy(includeBody = !config.includeBody)))
                            },
                            label = { Text(stringResource(R.string.smart_collection_source_body)) }
                        )
                        FilterChip(
                            selected = config.includeComments,
                            onClick = {
                                onUpdate(criterion.copy(config = config.copy(includeComments = !config.includeComments)))
                            },
                            label = { Text(stringResource(R.string.smart_collection_source_comments)) }
                        )
                        FilterChip(
                            selected = config.includeAiSummary,
                            onClick = {
                                onUpdate(criterion.copy(config = config.copy(includeAiSummary = !config.includeAiSummary)))
                            },
                            label = { Text(stringResource(R.string.smart_field_ai_summary)) }
                        )
                        FilterChip(
                            selected = config.includeManualNote,
                            onClick = {
                                onUpdate(criterion.copy(config = config.copy(includeManualNote = !config.includeManualNote)))
                            },
                            label = { Text(stringResource(R.string.smart_collection_source_manual_note)) }
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
                        text = stringResource(R.string.smart_collection_preview_title, posts.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.smart_collection_close))
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
                            text = stringResource(R.string.smart_collection_preview_empty),
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
                            val context = LocalContext.current
                            val reasons = remember(post, rule, categories, keywords) {
                                SmartCollectionEngine.getMatchReasons(context, rule, post, categories, keywords)
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
                                        text = post.displayBody.ifBlank { stringResource(R.string.smart_collection_preview_no_body) },
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
                                                    text = stringResource(R.string.smart_collection_preview_matched_label),
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
                                        text = stringResource(R.string.smart_collection_preview_more, posts.size - 5),
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
                    Text(stringResource(R.string.smart_collection_preview_close))
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
                    text = stringResource(R.string.smart_collection_icon_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                // 優先展示 5 個專門給規則使用的向量圖示
                Text(
                    text = stringResource(R.string.smart_collection_icon_smart_rule),
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
                                    contentDescription = stringResource(iconItem.name),
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.smart_collection_icon_other),
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
                                    contentDescription = stringResource(iconItem.name),
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.smart_collection_cancel)) }
                }
            }
        }
    }
}
