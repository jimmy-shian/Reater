package com.reater.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.verticalScroll
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.ui.AvatarIcons

/**
 * 新建分類對話框（鍵盤友善版）。
 *
 * 修復重點：
 * 1. 按鈕列固定在鍵盤上方：內容捲動 + 底部按鈕 outside scroll + imePadding，
 *    鍵盤彈出時「建立」不會被蓋住。
 * 2. 失焦：點空白 / 捲動 / Done 都會收鍵盤（不只點外部）。
 * 3. 返回鍵不再靜默丟稿：有輸入時先彈「捨棄確認」，避免整串標籤不見。
 * 4. rememberSaveable：旋轉螢幕不丟草稿；Done 直接建立（不用先收鍵盤再找按鈕）。
 */
@Composable
fun CategoryCreateDialog(
    initialName: String = "",
    quotaText: String? = null,
    isPro: Boolean = false,
    onProIconLocked: () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (name: String, avatarId: String) -> Unit,
) {
    var catName by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var selectedAvatar by rememberSaveable { mutableStateOf("life") }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    DismissFocusOnScroll(scrollState, focusManager)

    fun requestDismiss() {
        if (catName.isBlank()) {
            onDismiss()
        } else {
            showDiscardConfirm = true
        }
    }

    // 系統返回 / 手勢返回：有草稿先確認，不直接丟失
    BackHandler(enabled = true) { requestDismiss() }

    // 自動聚焦彈鍵盤，減少一次點擊
    LaunchedEffect(Unit) {
        runCatching {
            kotlinx.coroutines.delay(120)
            focusRequester.requestFocus()
        }
    }

    fun submitIfValid() {
        val trimmed = catName.trim()
        if (trimmed.isNotBlank()) {
            focusManager.clearFocus()
            onConfirm(trimmed, selectedAvatar)
        } else {
            focusManager.clearFocus()
        }
    }

    Dialog(
        onDismissRequest = { requestDismiss() },
        properties = DialogProperties(
            decorFitsSystemWindows = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DialogHeader(
                        title = "新建分類",
                        onClose = { requestDismiss() },
                        modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
                    )
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 中間可捲動區：滑動即收鍵盤，輸入框 Done 直接建立
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(scrollState)
                            .dismissFocusOnTap(focusManager)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        if (quotaText != null) {
                            Text(
                                text = quotaText,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = catName,
                            onValueChange = { catName = it },
                            placeholder = { Text("分類名稱（如：技術、生活、閱讀）") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitIfValid() })
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(text = "選擇圖示", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AvatarIcons.ALL.forEach { item ->
                                val isSelected = selectedAvatar == item.id
                                val isLocked = item.isPro && !isPro
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else if (item.isPro) Color(0xFFFFB300).copy(alpha = 0.5f)
                                            else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            focusManager.clearFocus()
                                            if (isLocked) {
                                                onProIconLocked()
                                            } else {
                                                selectedAvatar = item.id
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = item.resId),
                                        contentDescription = item.name,
                                        modifier = Modifier.size(24.dp),
                                        tint = Color.Unspecified
                                    )
                                    if (isLocked) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Pro 專屬",
                                                tint = Color(0xFFFFB300),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 底部按鈕固定在鍵盤上方，不隨內容捲走
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { requestDismiss() }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { submitIfValid() },
                            enabled = catName.isNotBlank()
                        ) {
                            Text("建立")
                        }
                    }
                }
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("捨棄尚未建立的分類？", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "已輸入「${catName.trim()}」尚未建立，返回會捨棄草稿。",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    focusManager.clearFocus()
                    onDismiss()
                }) { Text("捨棄") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("繼續編輯") }
            }
        )
    }
}

/**
 * 編輯分類對話框：重新命名 + 換圖示 + 刪除（內建分類不可刪除）。
 * 入口：主畫面「PRO 分類」頁 → 自訂分類列 → ✎。
 */
@Composable
fun CategoryEditDialog(
    category: CategoryEntity,
    isPro: Boolean = false,
    onProIconLocked: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (name: String, avatarId: String) -> Unit,
    onDelete: () -> Unit,
) {
    var catName by rememberSaveable(category.id) { mutableStateOf(category.name) }
    var selectedAvatar by rememberSaveable(category.id) { mutableStateOf(category.avatarIcon) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    DismissFocusOnScroll(scrollState, focusManager)

    fun submitIfValid() {
        val trimmed = catName.trim()
        if (trimmed.isNotBlank()) {
            focusManager.clearFocus()
            onSave(trimmed, selectedAvatar)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            decorFitsSystemWindows = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DialogHeader(
                        title = "編輯分類",
                        onClose = onDismiss,
                        modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
                    )
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(scrollState)
                            .dismissFocusOnTap(focusManager)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        if (category.isDefault) {
                            Text(
                                text = "內建分類不可刪除，可重新命名與更換圖示",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        OutlinedTextField(
                            value = catName,
                            onValueChange = { catName = it },
                            placeholder = { Text("分類名稱") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitIfValid() })
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(text = "選擇圖示", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AvatarIcons.ALL.forEach { item ->
                                val isSelected = selectedAvatar == item.id
                                val isLocked = item.isPro && !isPro
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else if (item.isPro) Color(0xFFFFB300).copy(alpha = 0.5f)
                                            else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            focusManager.clearFocus()
                                            if (isLocked) onProIconLocked()
                                            else selectedAvatar = item.id
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = item.resId),
                                        contentDescription = item.name,
                                        modifier = Modifier.size(24.dp),
                                        tint = Color.Unspecified
                                    )
                                    if (isLocked) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Pro 專屬",
                                                tint = Color(0xFFFFB300),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!category.isDefault) {
                            TextButton(onClick = { showDeleteConfirm = true }) {
                                Text("刪除", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Row(horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = onDismiss) { Text("取消") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { submitIfValid() },
                                enabled = catName.isNotBlank() &&
                                    (catName.trim() != category.name || selectedAvatar != category.avatarIcon)
                            ) { Text("儲存") }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("刪除「${category.name}」？", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "該分類底下的貼文會改為「未分類」，不會刪除貼文本身。",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}
