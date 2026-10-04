package com.reater.app.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reater.app.notify.NotifyCenter
import com.reater.app.ui.MainViewModel
import com.reater.app.ui.components.DialogHeader
import com.reater.app.ui.components.ReaterDropdownNumberInput
import com.reater.app.ui.components.ReaterDropdownOption
import com.reater.app.ui.components.ReaterDropdownPanel
import com.reater.app.ui.components.ReaterDropdownTrigger
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.reater.app.ui.components.DismissFocusOnScroll
import com.reater.app.ui.components.dismissFocusOnTap

import androidx.compose.material.icons.filled.Lock
import com.reater.app.ui.AvatarIcons
import com.reater.app.ui.AvatarStorage
import com.reater.app.ui.components.AvatarCropDialog
import com.reater.app.ui.components.UserAvatarView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onOpenIconGallery: () -> Unit = {},
    onOpenUnlock: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isPro by viewModel.isProUnlocked.collectAsState()
    val customAvatarId by viewModel.customAvatarId.collectAsState()
    val customAvatarUri by viewModel.customAvatarUri.collectAsState()
    val currentBaseUrl by viewModel.customBaseUrl.collectAsState()
    val currentModel by viewModel.selectedModel.collectAsState()
    val aiConsent by viewModel.aiTransmissionConsent.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val fontScale by viewModel.fontScale.collectAsState()
    val nudgeEnabled by viewModel.unreadNudgeEnabled.collectAsState()
    val nudgeDelay by viewModel.unreadNudgeDelayMin.collectAsState()
    val digestEnabled by viewModel.reviewDigestEnabled.collectAsState()
    val digestHour by viewModel.reviewDigestHour.collectAsState()

    val settingsScope = androidx.compose.runtime.rememberCoroutineScope()
    var settingsCropSource by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var settingsCropSourceUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            // 原圖保留＋裁切配對：先解碼預覽，確認後原始檔與成品同 ts 存放
            settingsScope.launch(Dispatchers.IO) {
                val bmp = AvatarStorage.decodeForEdit(context, uri)
                withContext(Dispatchers.Main) {
                    if (bmp != null) {
                        settingsCropSource = bmp
                        settingsCropSourceUri = uri
                    } else viewModel.importCustomAvatar(uri) { success ->
                        Toast.makeText(
                            context,
                            if (success) "已成功套用自訂頭像照片" else "讀取照片失敗，請重試",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    var apiKeyInput by remember { mutableStateOf("") }
    var baseUrlInput by remember { mutableStateOf(currentBaseUrl) }
    var modelInput by remember { mutableStateOf(currentModel) }
    var previewScale by remember(fontScale) { mutableStateOf(fontScale) }

    fun dismissWithApply() {
        if (previewScale != fontScale) viewModel.setFontScale(previewScale)
        onDismiss()
    }

    var permNonce by remember { mutableStateOf(0) }
    val notifGranted = remember(permNonce) {
        NotifyCenter.canNotify(context)
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permNonce++ }

    // 外層捲動狀態提升：垂直滑動即收鍵盤（輸入框內水平拖曳游標不受影響）
    val settingsScrollState = rememberScrollState()
    DismissFocusOnScroll(settingsScrollState, focusManager)

    Dialog(onDismissRequest = { dismissWithApply() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 固定頂欄
                DialogHeader(
                    title = "系統設定",
                    onClose = { dismissWithApply() },
                    modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(settingsScrollState)
                        .dismissFocusOnTap(focusManager)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {

                    // ---------- 外觀 ----------
                    SettingsSectionTitle("外觀與閱讀")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "亮暗模式", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("system" to "跟隨系統", "light" to "淺色", "dark" to "深色").forEach { (id, label) ->
                            FilterChip(
                                selected = themeMode == id,
                                onClick = { viewModel.setThemeMode(id) },
                                label = { Text(label, maxLines = 1, fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "字體大小", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "${(previewScale * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = previewScale,
                        onValueChange = { previewScale = it },
                        valueRange = 0.85f..1.3f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Reater 閱讀器預覽：探索與典藏優質貼文",
                            fontSize = (14 * previewScale).sp,
                            lineHeight = (20 * previewScale).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ---------- 個人大頭像與圖示庫 ----------
                    SettingsSectionTitle("個人大頭像與圖示庫 (PRO 專屬)")
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isPro) {
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatarView(
                                        avatarId = customAvatarId,
                                        avatarUri = customAvatarUri,
                                        size = 52.dp
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        val iconItem = AvatarIcons.getIconItem(customAvatarId)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (customAvatarUri != null) "自訂相片頭貼" else iconItem.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFFFB300), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text("PRO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (customAvatarUri != null) "已複製到 App 內部儲存・刪原圖不影響" else iconItem.desc,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onOpenIconGallery,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            "圖示總覽大廳",
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = { photoPickerLauncher.launch("image/*") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            "相簿自訂上傳",
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (customAvatarUri != null) {
                                        OutlinedButton(
                                            onClick = { viewModel.setCustomAvatarUri(null) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text("重設", fontSize = 12.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFFFFB300).copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "升級 Pro 解鎖自訂大頭像",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "解鎖 10 款尊爵圖示、圖示總覽大廳與自訂相片",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = onOpenUnlock,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("解鎖", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ---------- 通知與排程 ----------
                    SettingsSectionTitle("通知與推播提醒")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (notifGranted) "推播權限：已授權"
                            else "推播權限：未授權",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(1f)
                        )
                        if (!notifGranted) {
                            OutlinedButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("授權", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. 未讀提醒區塊（重新設計專屬時間操作元件）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(text = "未讀提醒", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "儲存後若仍未讀，延遲自動發送推播提醒",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = nudgeEnabled,
                            onCheckedChange = { viewModel.setUnreadNudgeEnabled(context, it) }
                        )
                    }

                    AnimatedVisibility(
                        visible = nudgeEnabled,
                        enter = expandVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeIn(),
                        exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeOut()
                    ) {
                        var delayExpanded by remember { mutableStateOf(false) }
                        var customDelay by remember(nudgeEnabled) { mutableStateOf("") }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReaterDropdownTrigger(
                                expanded = delayExpanded,
                                onClick = { delayExpanded = !delayExpanded },
                                title = "延遲時間",
                                value = "$nudgeDelay 分鐘",
                                leading = {
                                    Icon(
                                        Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            ReaterDropdownPanel(expanded = delayExpanded) {
                                listOf(10, 15, 30, 45, 60, 120).forEach { mins ->
                                    ReaterDropdownOption(
                                        text = "$mins 分鐘",
                                        selected = nudgeDelay == mins,
                                        onClick = {
                                            viewModel.setUnreadNudgeDelayMin(mins)
                                            customDelay = ""
                                            delayExpanded = false
                                        }
                                    )
                                }
                                ReaterDropdownNumberInput(
                                    value = customDelay,
                                    onValueChange = { customDelay = it },
                                    label = "自訂分鐘數",
                                    suffix = "分鐘",
                                    hint = "可輸入 1～120",
                                    onApply = {
                                        customDelay.toIntOrNull()?.let {
                                            viewModel.setUnreadNudgeDelayMin(it)
                                            delayExpanded = false
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. 每日回顧區塊（重新設計專屬時間操作元件）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(text = "每日回顧", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "每天固定時間點推播彙整未讀數",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = digestEnabled,
                            onCheckedChange = { viewModel.setReviewDigestEnabled(context, it) }
                        )
                    }

                    AnimatedVisibility(
                        visible = digestEnabled,
                        enter = expandVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeIn(),
                        exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeOut()
                    ) {
                        val periodLabel = when (digestHour) {
                            in 5..10 -> "晨間"
                            in 11..13 -> "午間"
                            in 14..17 -> "下午"
                            in 18..22 -> "晚間"
                            else -> "深夜"
                        }
                        var timeExpanded by remember { mutableStateOf(false) }
                        var customHour by remember(digestEnabled) { mutableStateOf("") }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReaterDropdownTrigger(
                                expanded = timeExpanded,
                                onClick = { timeExpanded = !timeExpanded },
                                title = "推送時間",
                                value = String.format("%02d:00", digestHour),
                                leading = {
                                    Icon(
                                        Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                badge = {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                                    ) {
                                        Text(
                                            text = periodLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            )
                            ReaterDropdownPanel(expanded = timeExpanded) {
                                listOf(
                                    8 to "08:00 晨讀",
                                    12 to "12:00 午間",
                                    18 to "18:00 下班",
                                    21 to "21:00 晚間",
                                    23 to "23:00 睡前"
                                ).forEach { (hr, label) ->
                                    ReaterDropdownOption(
                                        text = label,
                                        selected = digestHour == hr,
                                        onClick = {
                                            viewModel.setReviewDigestHour(context, hr)
                                            customHour = ""
                                            timeExpanded = false
                                        }
                                    )
                                }
                                ReaterDropdownNumberInput(
                                    value = customHour,
                                    onValueChange = { customHour = it },
                                    label = "自訂小時",
                                    suffix = "時",
                                    hint = "可輸入 0～23",
                                    onApply = {
                                        customHour.toIntOrNull()?.let {
                                            viewModel.setReviewDigestHour(context, it)
                                            timeExpanded = false
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ---------- AI 服務 (BYOK) ----------
                    SettingsSectionTitle("AI 服務 (BYOK)")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "API Key（支援 OpenAI / 相容端點）", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = { Text("sk-...", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        // 單行長字串由 TextField 內部水平捲動處理；父層不再搶事件，
                        // 游標拖到最左/最右可自動跟隨捲動，不會卡住
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "API Base URL", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = baseUrlInput,
                        onValueChange = { baseUrlInput = it },
                        placeholder = {
                            Text(
                                "https://api.openai.com/v1 或自訂代理",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Model ID", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        placeholder = {
                            Text(
                                "gpt-5-nano, deepseek-chat 等",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = aiConsent,
                            onCheckedChange = viewModel::setAiTransmissionConsent
                        )
                        Text(
                            text = "允許將貼文與留言傳送至設定的 AI 服務",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ---------- 備份與資料管理 ----------
                    SettingsSectionTitle("資料備份與還原")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "僅支援 .reater 專屬備份檔。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onExportJson,
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "匯出 .reater",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        OutlinedButton(
                            onClick = onImportJson,
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "匯入 .reater",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 底部儲存 AI 設定
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveAiConfig(apiKeyInput, baseUrlInput, modelInput)
                                Toast.makeText(context, "AI 設定已儲存", Toast.LENGTH_SHORT).show()
                                dismissWithApply()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("儲存 AI 設定")
                        }
                    }
                }
            }
        }
    }

    // 設定頁上傳同樣先裁切再存檔（原圖保留＋成品配對，設定/總覽/主畫面同步刷新）
    if (settingsCropSource != null) {
        AvatarCropDialog(
            source = settingsCropSource!!,
            onConfirm = { cropped ->
                val srcUri = settingsCropSourceUri
                settingsCropSource = null
                settingsCropSourceUri = null
                if (srcUri != null) {
                    viewModel.importOriginalThenCropped(srcUri, cropped) { success ->
                        Toast.makeText(
                            context,
                            if (success) "已成功套用自訂頭像照片（原圖已保留）" else "儲存裁切圖片失敗，請重試",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    viewModel.saveCroppedAvatar(cropped) { success ->
                        Toast.makeText(
                            context,
                            if (success) "已成功套用自訂頭像照片" else "儲存裁切圖片失敗，請重試",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDismiss = {
                settingsCropSource = null
                settingsCropSourceUri = null
            }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}
