package com.reater.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.DialogProperties
import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Lock
import com.reater.app.ui.AvatarIcons
import com.reater.app.ui.components.UserAvatarView
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onTriggerUninstallBackup: () -> Unit = {},
    onOpenIconGallery: () -> Unit = {},
    onOpenUnlock: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val isPro by viewModel.isProUnlocked.collectAsState()
    val customAvatarId by viewModel.customAvatarId.collectAsState()
    val customAvatarUri by viewModel.customAvatarUri.collectAsState()
    val currentBaseUrl by viewModel.customBaseUrl.collectAsState()
    val currentModel by viewModel.selectedModel.collectAsState()
    val aiConsent by viewModel.aiTransmissionConsent.collectAsState()
    val aiAnalyzeOnSave by viewModel.aiAnalyzeOnSave.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val fontScale by viewModel.fontScale.collectAsState()
    val nudgeEnabled by viewModel.unreadNudgeEnabled.collectAsState()
    val nudgeDelay by viewModel.unreadNudgeDelayMin.collectAsState()
    val digestEnabled by viewModel.reviewDigestEnabled.collectAsState()
    val digestHour by viewModel.reviewDigestHour.collectAsState()

    var apiKeyInput by remember { mutableStateOf("") }
    var baseUrlInput by remember { mutableStateOf(currentBaseUrl) }
    var modelInput by remember { mutableStateOf(currentModel) }
    var previewScale by remember(fontScale) { mutableStateOf(fontScale) }
    var hasSavedApiKey by remember { mutableStateOf(false) }
    var aiExpanded by rememberSaveable { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelFetchInProgress by remember { mutableStateOf(false) }
    var modelFetchError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        hasSavedApiKey = !viewModel.getSavedApiKey().isNullOrBlank()
    }

    LaunchedEffect(baseUrlInput, apiKeyInput, hasSavedApiKey) {
        availableModels = emptyList()
        showModelDropdown = false
        modelFetchError = null
        if (baseUrlInput.isBlank()) return@LaunchedEffect
        delay(600)
        modelFetchInProgress = true
        val key = apiKeyInput.ifBlank { viewModel.getSavedApiKey().orEmpty() }
        viewModel.fetchAvailableModels(key, baseUrlInput)
            .onSuccess { models -> availableModels = models }
            .onFailure { modelFetchError = it.message }
        modelFetchInProgress = false
    }

    fun dismissWithApply() {
        if (previewScale != fontScale) viewModel.setFontScale(previewScale)
        if (apiKeyInput.isNotBlank() || baseUrlInput != currentBaseUrl || modelInput != currentModel) {
            viewModel.saveAiConfig(apiKeyInput, baseUrlInput, modelInput)
        }
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

    // 自動儲存 AI 設定變更，免按儲存按鈕
    LaunchedEffect(baseUrlInput, modelInput) {
        if (baseUrlInput != currentBaseUrl || modelInput != currentModel) {
            viewModel.saveAiConfig(apiKeyInput, baseUrlInput, modelInput)
        }
    }
    LaunchedEffect(apiKeyInput) {
        if (apiKeyInput.isNotBlank()) {
            delay(500)
            viewModel.saveAiConfig(apiKeyInput, baseUrlInput, modelInput)
            hasSavedApiKey = true
        }
    }

    Dialog(
        onDismissRequest = { dismissWithApply() },
        properties = DialogProperties(
            dismissOnBackPress = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val imeVisible = WindowInsets.isImeVisible
        BackHandler {
            if (imeVisible) {
                focusManager.clearFocus()
                keyboard?.hide()
            } else dismissWithApply()
        }

        val handleTapOutside = {
            focusManager.clearFocus()
            keyboard?.hide()
            if (!imeVisible && aiExpanded) {
                aiExpanded = false
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .dismissFocusOnTap(focusManager)
            ) {
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
                    Text(text = "亮暗模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("system" to "跟隨系統", "light" to "淺色", "dark" to "深色").forEach { (id, label) ->
                            FilterChip(
                                selected = themeMode == id,
                                onClick = { viewModel.setThemeMode(id) },
                                label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "字體大小", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = "${(previewScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
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
                                            text = if (customAvatarUri != null) "已從本機相簿匯入自訂個人照片" else iconItem.desc,
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
                                            "圖示展覽室",
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = onOpenIconGallery,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            "自訂頭像",
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
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "解鎖 10 款尊爵圖示、圖示展覽室與自訂相片",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = onOpenUnlock,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("解鎖", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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
                            style = MaterialTheme.typography.bodyMedium,
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
                                Text("授權", style = MaterialTheme.typography.labelMedium)
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
                            Text(text = "未讀提醒", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "儲存後若仍未讀，延遲自動發送推播提醒",
                                style = MaterialTheme.typography.bodySmall,
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
                            Text(text = "每日回顧", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "每天固定時間點推播彙整未讀數",
                                style = MaterialTheme.typography.bodySmall,
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // ---------- AI 服務 (BYOK) - 收合式設計 ----------
                    val keyConfigured = apiKeyInput.isNotBlank() || hasSavedApiKey
                    val providerLabel = if (baseUrlInput.contains("openai.com")) "OpenAI" else "自訂端點"
                    val modelLabel = if (modelInput.isNotBlank()) modelInput else "尚未指定模型"
                    val keyStatusText = if (keyConfigured) "API Key 已設定" else "尚未設定"

                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .dismissFocusOnTap(focusManager),
                        shape = RoundedCornerShape(10.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (aiExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .dismissFocusOnTap(focusManager)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (imeVisible) {
                                            focusManager.clearFocus()
                                            keyboard?.hide()
                                        } else {
                                            aiExpanded = !aiExpanded
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    SettingsSectionTitle("AI 服務 (BYOK)")
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$providerLabel · $modelLabel ($keyStatusText)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val arrowRotation by animateFloatAsState(
                                    targetValue = if (aiExpanded) 180f else 0f,
                                    animationSpec = tween(250),
                                    label = "aiArrow"
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (aiExpanded) "收合 AI 設定" else "展開 AI 設定",
                                    modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }

                            AnimatedVisibility(
                                visible = aiExpanded,
                                enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                                exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .dismissFocusOnTap(focusManager)
                                        .padding(top = 10.dp)
                                ) {
                                    Text(
                                        text = "API Key",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = apiKeyInput,
                                        onValueChange = { apiKeyInput = it },
                                        placeholder = {
                                            Text(
                                                text = if (hasSavedApiKey) "•••••••••••••••• (已保存)" else "sk-...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                         modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = "API Base URL",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = baseUrlInput,
                                        onValueChange = { baseUrlInput = it },
                                        placeholder = {
                                            Text(
                                                "https://api.openai.com/v1",
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                         modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Model ID",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Box(modifier = Modifier.padding(start = 8.dp)) {
                                            Text(
                                                text = when {
                                                    modelFetchInProgress -> "讀取模型…"
                                                    availableModels.isNotEmpty() -> "選取可用模型 ▾"
                                                    else -> "輸入 URL 後取得"
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .clickable(enabled = availableModels.isNotEmpty()) { showModelDropdown = true }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                            DropdownMenu(
                                                expanded = showModelDropdown && availableModels.isNotEmpty(),
                                                onDismissRequest = { showModelDropdown = false }
                                            ) {
                                                Text(
                                                    text = "從目前 API Base URL 取得",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                                )
                                                HorizontalDivider()
                                                availableModels.forEach { modelId ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(modelId, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                        },
                                                        onClick = {
                                                            modelInput = modelId
                                                            showModelDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = modelInput,
                                        onValueChange = { modelInput = it },
                                        placeholder = {
                                            Text(
                                                "從 API 取得的 Model ID",
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        trailingIcon = {
                                            IconButton(
                                                enabled = availableModels.isNotEmpty(),
                                                onClick = { showModelDropdown = !showModelDropdown }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = "展開常用模型清單",
                                                    tint = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        },
                                         modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (availableModels.isNotEmpty()) {
                                        Text(
                                            text = "可用模型：",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        availableModels.forEach { chipModel ->
                                            val isSelected = modelInput == chipModel
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                                ),
                                                modifier = Modifier.clickable {
                                                    modelInput = chipModel
                                                }
                                            ) {
                                                Text(
                                                    text = chipModel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { viewModel.setAiTransmissionConsent(!aiConsent) }
                                    ) {
                                        Checkbox(
                                            checked = aiConsent,
                                            onCheckedChange = viewModel::setAiTransmissionConsent
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "允許將貼文與留言內容傳送至設定的 AI 服務",
                                            style = MaterialTheme.typography.bodySmall,
                                            lineHeight = 16.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { viewModel.setAiAnalyzeOnSave(!aiAnalyzeOnSave) }
                                    ) {
                                        Checkbox(
                                            checked = aiAnalyzeOnSave,
                                            onCheckedChange = viewModel::setAiAnalyzeOnSave
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "儲存後自動 AI 分析",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "儲存貼文時在背景產生摘要並發送通知",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ---------- 備份與資料管理 ----------
                    SettingsSectionTitle("資料備份與還原")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ".reater 專屬備份格式",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onExportJson,
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "匯出備份",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                        OutlinedButton(
                            onClick = onImportJson,
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "匯入備份",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ---------- 解除安裝與資料保留 ----------
                    SettingsSectionTitle("解除安裝與資料保留")
                    Spacer(modifier = Modifier.height(6.dp))
                    val keepDataAfterUninstall by viewModel.keepDataAfterUninstall.collectAsState()
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val target = !keepDataAfterUninstall
                                viewModel.setKeepDataAfterUninstall(target)
                                if (target) {
                                    onTriggerUninstallBackup()
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            1.dp,
                            if (keepDataAfterUninstall) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = keepDataAfterUninstall,
                                onCheckedChange = { checked ->
                                    viewModel.setKeepDataAfterUninstall(checked)
                                    if (checked) {
                                        onTriggerUninstallBackup()
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "保留可還原的 Reater 資料",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "重新安裝後可再次匯入。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                } // End of scrollable Column
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}
