package com.reater.app.ui.share

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.shape.CircleShape
import com.reater.app.data.remote.FetchStage
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.reater.app.R
import com.reater.app.data.remote.threads.ThreadsWebResolver
import com.reater.app.ui.AvatarIcons
import com.reater.app.ui.components.DismissFocusOnScroll
import com.reater.app.ui.components.dismissFocusOnTap
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

private fun formatMediaBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024f * 1024f)
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}

private fun formatMediaEta(seconds: Long): String {
    if (seconds <= 0) return "0:00"
    val m = seconds / 60
    val s = seconds % 60
    return String.format(java.util.Locale.US, "%d:%02d", m, s)
}

private fun formatMediaSpeed(speedBps: Long): String {
    if (speedBps <= 0) return ""
    val kb = speedBps / 1024f
    return if (kb >= 1024f) {
        String.format(java.util.Locale.US, "%.1f MB/s", kb / 1024f)
    } else {
        String.format(java.util.Locale.US, "%.0f KB/s", kb)
    }
}

@AndroidEntryPoint
class ShareSaveActivity : ComponentActivity() {

    private val viewModel: ShareSaveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") {
            finish()
            return
        }
        val sharedText = sanitizeSharedText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
        viewModel.processIncomingText(sharedText)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val fontScale by viewModel.fontScale.collectAsState()
            com.reater.app.ui.theme.ReaterTheme(themeMode = themeMode, fontScale = fontScale) {
                val webState by viewModel.uiState.collectAsState()
                // 隱藏 WebView 一律同時跑：背景模擬「打開這則貼文」，
                // /share/ 要靠它跟 JS 跳轉；一般連結用 Googlebot 指紋直接拿
                // 預渲染 payload（留言/留言媒體只有這條路拿得到），與 HTTP 抓取
                // 平行、結果於 ViewModel merge，兩路任一成功即視為成功。
                LaunchedEffect(
                    webState.fetchUrl,
                    webState.webResolveAttempted,
                    webState.webResolving
                ) {
                    val url = webState.fetchUrl
                    val isThreads = url.startsWith("http") &&
                        (url.contains("threads.com") || url.contains("threads.net"))
                    if (isThreads && !webState.webResolveAttempted && !webState.webResolving) {
                        viewModel.markWebResolveStarted()
                        ThreadsWebResolver.resolve(this@ShareSaveActivity, url) { page ->
                            if (page != null) {
                                viewModel.onWebResolved(
                                    page.finalUrl,
                                    page.sjsBlocks,
                                    page.renderedText,
                                    page.renderedHtml,
                                    page.domComments
                                )
                            } else {
                                viewModel.onWebResolveFailed()
                            }
                        }
                    }
                }
                val requestPermissionLauncher = rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { granted ->
                    // 沒拿到通知權限時，背景下載仍會繼續，只是通知欄看不到進度：
                    // 明確告知使用者，避免誤以為「沒有在下載」。
                    if (!granted && !com.reater.app.notify.NotifyCenter.canNotify(this@ShareSaveActivity)) {
                        Toast.makeText(
                            this,
                            getString(R.string.notification_permission_missing),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                LaunchedEffect(Unit) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        if (!com.reater.app.notify.NotifyCenter.canNotify(this@ShareSaveActivity)) {
                            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                ShareSaveScreen(
                    viewModel = viewModel,
                    onDismiss = { finish() },
                    onSaved = { hasPending ->
                        val msg = if (hasPending) {
                            getString(R.string.saved_downloading_media)
                        } else {
                            getString(R.string.saved_success)
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }

    private fun sanitizeSharedText(value: String): String {
        val result = StringBuilder()
        var offset = 0
        var bytes = 0
        while (offset < value.length) {
            val codePoint = value.codePointAt(offset)
            offset += Character.charCount(codePoint)
            if (codePoint in 0x200B..0x200F || codePoint in 0x202A..0x202E ||
                codePoint in 0x2066..0x2069 || codePoint in 0x007F..0x009F ||
                codePoint < 0x20 && codePoint != '\n'.code && codePoint != '\t'.code
            ) continue
            val charCount = when {
                codePoint <= 0x7F -> 1
                codePoint <= 0x7FF -> 2
                codePoint <= 0xFFFF -> 3
                else -> 4
            }
            if (bytes + charCount > MAX_SHARED_TEXT_BYTES) break
            result.appendCodePoint(codePoint)
            bytes += charCount
        }
        if (value.toByteArray(Charsets.UTF_8).size > MAX_SHARED_TEXT_BYTES) {
            Toast.makeText(this, getString(R.string.share_text_too_long), Toast.LENGTH_LONG).show()
        }
        return result.toString()
    }

    private companion object {
        const val MAX_SHARED_TEXT_BYTES = 200 * 1024
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShareSaveScreen(
    viewModel: ShareSaveViewModel,
    onDismiss: () -> Unit,
    onSaved: (hasPendingBackgroundDownload: Boolean) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isPro by viewModel.isProUnlocked.collectAsState()
    val context = LocalContext.current
    val newCategoryName = state.categoryQuery.trim()
    val isNewCategory = newCategoryName.isNotBlank() &&
        !newCategoryName.equals("未分類", ignoreCase = true) &&
        state.selectedCategoryId == null &&
        categories.none { it.name.trim().equals(newCategoryName, ignoreCase = true) }
    if (state.showSaveConflict) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissSaveConflict,
            title = { Text(if (state.conflictIsInTrash) stringResource(R.string.conflict_title_in_trash) else stringResource(R.string.conflict_title_exists)) },
            text = { Text(if (state.conflictIsInTrash)
                stringResource(R.string.conflict_message_in_trash)
                else stringResource(R.string.conflict_message_exists)) },
            confirmButton = { TextButton(onClick = { viewModel.savePost(false) }) {
                Text(if (state.conflictIsInTrash) stringResource(R.string.conflict_action_restore) else stringResource(R.string.conflict_action_overwrite))
            } },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::dismissSaveConflict) { Text(stringResource(R.string.conflict_action_cancel)) }
                    TextButton(onClick = { viewModel.savePost(true) }) {
                        Text(if (state.conflictIsInTrash) stringResource(R.string.conflict_action_new) else stringResource(R.string.conflict_action_save_as))
                    }
                }
            }
        )
    }
    LaunchedEffect(state.saveError) {
        state.saveError?.let { Toast.makeText(context, context.getString(R.string.save_error_with_reason, it), Toast.LENGTH_LONG).show() }
    }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // 主內容捲動狀態提升：內容區垂直滑動即收鍵盤（多行框內拖曳不受影響）
    val shareContentScrollState = rememberScrollState()
    val keyboard = LocalSoftwareKeyboardController.current
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val noteIntoView = remember { BringIntoViewRequester() }
    var noteFocused by remember { mutableStateOf(false) }
    LaunchedEffect(noteFocused, imeOpen) {
        if (noteFocused && imeOpen) { delay(100); noteIntoView.bringIntoView() }
    }
    BackHandler {
        if (imeOpen) { focusManager.clearFocus(); keyboard?.hide() }
        else onDismiss()
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onSaved(state.hasPendingMediaBackgroundDownload)
        }
    }

    // 免費版配額提示（文案統一至 ProCopy，不使用第三方比喻）
    if (state.showProLimitNotice) {
        Dialog(onDismissRequest = { viewModel.setShowProLimitNotice(false) }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    com.reater.app.ui.components.DialogHeader(
                        title = stringResource(R.string.pro_share_limit_title),
                        onClose = { viewModel.setShowProLimitNotice(false) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.pro_share_limit_body, com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = { viewModel.setShowProLimitNotice(false) }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
            }
        }
    }

    // Create Category Dialog（鍵盤友善版：按鈕不被鍵盤蓋、返回不丟稿、Done 直接建立）
    var createPrefill by remember { mutableStateOf("") }
    if (state.showCreateCategoryDialog) {
        val customCount = categories.count { !it.isDefault }
        val quotaText = if (!isPro) {
            val remaining = com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT - customCount
            if (remaining > 0) stringResource(R.string.free_category_limit_remaining, remaining, customCount, com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT)
            else stringResource(R.string.free_category_limit_full, com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT, com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT)
        } else null
        androidx.compose.runtime.key(createPrefill) {
            com.reater.app.ui.components.CategoryCreateDialog(
                initialName = createPrefill,
                quotaText = quotaText,
                isPro = isPro,
                onProIconLocked = {
                    Toast.makeText(context, context.getString(R.string.pro_icon_locked), Toast.LENGTH_SHORT).show()
                },
                onDismiss = {
                    createPrefill = ""
                    viewModel.setShowCreateCategoryDialog(false)
                },
                onConfirm = { name, avatar ->
                    viewModel.createCategory(name, avatar)
                    createPrefill = ""
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .dismissFocusOnTap(focusManager),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 固定頂欄：標題 + 珍藏 + X，不隨捲動離開
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.share_save_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.toggleFavorite() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (state.isFavorite) stringResource(R.string.accessibility_favorited) else stringResource(R.string.accessibility_add_to_favorites),
                                tint = if (state.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.outline
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.accessibility_close),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(shareContentScrollState)
                        .dismissFocusOnTap(focusManager)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                if (state.targetUrl.isNotBlank()) {
                    Text(
                        text = state.targetUrl,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                }

                // Author Info + 分享種類徽章（串文 / 留言/分享）
                if (state.authorHandle.isNotBlank() || state.shareKindLabel.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        if (state.authorHandle.isNotBlank()) {
                            Text(
                                text = "@${state.authorHandle}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        if (state.shareKindLabel.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = state.shareKindLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // 固定置頂：選擇分類（位置恆定，抓取載入/媒體插入時絕不跳動！）
                Text(
                    text = stringResource(R.string.label_select_category),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                com.reater.app.ui.components.CategoryDropdown(
                    categories = categories,
                    selectedCategoryId = state.selectedCategoryId,
                    onSelect = { viewModel.onCategorySelected(it) },
                    onRequestCreate = { prefill ->
                        createPrefill = prefill
                        viewModel.setShowCreateCategoryDialog(true)
                    },
                    categoryQuery = state.categoryQuery,
                    onQueryChange = viewModel::onCategoryQueryChanged,
                    dropdownExpanded = state.isCategoryDropdownExpanded,
                    onExpandedChange = viewModel::setCategoryExpanded,
                    onFieldFocused = viewModel::setCategoryFocused
                )
                // 歷史預選提示：同主題/同作者過往最常用分類（手動更改後消失）
                AnimatedVisibility(visible = state.suggestedBasis.isNotBlank()) {
                    Text(
                        text = when (state.suggestedBasis) {
                            "smart" -> stringResource(R.string.smart_source_smart)
                            "topic" -> stringResource(R.string.smart_source_topic)
                            else -> stringResource(R.string.smart_source_author)
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Status info banner
                val showProgressBanner = state.isFetching || state.webResolving || state.fetchStage != FetchStage.IDLE
                if (showProgressBanner && !state.isFetchFailed) {
                    val stage = state.fetchStage
                    val isDownloading = stage == FetchStage.DOWNLOADING_MEDIA
                    val isComplete = stage == FetchStage.COMPLETED || stage == FetchStage.PARTIAL_SUCCESS

                    val bannerText = when {
                        state.webResolving -> stringResource(R.string.status_web_resolving)
                        state.fetchStatusMessage.isNotBlank() -> {
                            if (isDownloading && state.mediaBytesTotal > 0) {
                                val etaText = if (state.mediaEtaSeconds > 0) stringResource(R.string.share_eta_prefix, formatMediaEta(state.mediaEtaSeconds)) else ""
                                val speedText = if (state.mediaSpeedBps > 0) " (${formatMediaSpeed(state.mediaSpeedBps)})" else ""
                                stringResource(R.string.status_downloading_media, state.mediaDownloadedCount, state.mediaTotalCount, formatMediaBytes(state.mediaBytesDownloaded), formatMediaBytes(state.mediaBytesTotal), etaText, speedText)
                            } else {
                                state.fetchStatusMessage
                            }
                        }
                        state.platform == com.reater.app.domain.UrlParser.PLATFORM_INSTAGRAM -> stringResource(R.string.status_analyzing_instagram)
                        state.platform == com.reater.app.domain.UrlParser.PLATFORM_X -> stringResource(R.string.status_analyzing_x)
                        state.platform == com.reater.app.domain.UrlParser.PLATFORM_FACEBOOK -> stringResource(R.string.status_analyzing_facebook)
                        state.platform == com.reater.app.domain.UrlParser.PLATFORM_YOUTUBE -> stringResource(R.string.status_analyzing_youtube)
                        else -> stringResource(R.string.status_analyzing_threads)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isComplete) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isComplete) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else if (isDownloading && state.mediaBytesTotal > 0) {
                                val percent = ((state.mediaBytesDownloaded.toFloat() / state.mediaBytesTotal) * 100).toInt().coerceIn(0, 100)
                                Text(
                                    text = "$percent%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = bannerText,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            if (isDownloading) {
                                Spacer(modifier = Modifier.width(6.dp))
                                TextButton(
                                    onClick = { viewModel.savePost() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.status_background_download),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (isDownloading) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val progressFrac = if (state.mediaBytesTotal > 0) {
                                (state.mediaBytesDownloaded.toFloat() / state.mediaBytesTotal).coerceIn(0f, 1f)
                            } else if (state.mediaTotalCount > 0) {
                                (state.mediaDownloadedCount.toFloat() / state.mediaTotalCount).coerceIn(0f, 1f)
                            } else null

                            if (progressFrac != null) {
                                LinearProgressIndicator(
                                    progress = { progressFrac },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (state.isFetchFailed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.fetch_failed_tip),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Body text field（標題讓位給按鈕，且載入時具備載入進度視覺效果）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.label_post_body),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        if (state.isFetching || state.webResolving) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.label_loading),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = clipMgr?.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clip.isNullOrBlank()) {
                                viewModel.onBodyTextChanged(clip)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.button_paste_body), fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (state.isFetching || state.webResolving) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                OutlinedTextField(
                    value = state.bodyText,
                    onValueChange = { viewModel.onBodyTextChanged(it) },
                    placeholder = {
                        Text(
                            if (state.isFetching || state.webResolving) {
                                when (state.platform) {
                                    com.reater.app.domain.UrlParser.PLATFORM_INSTAGRAM -> stringResource(R.string.fetching_instagram)
                                    com.reater.app.domain.UrlParser.PLATFORM_X -> stringResource(R.string.fetching_x)
                                    com.reater.app.domain.UrlParser.PLATFORM_FACEBOOK -> stringResource(R.string.fetching_facebook)
                                    com.reater.app.domain.UrlParser.PLATFORM_YOUTUBE -> stringResource(R.string.fetching_youtube)
                                    else -> stringResource(R.string.fetching_threads)
                                }
                            }
                            else stringResource(R.string.body_placeholder)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 220.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Comments text field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.label_comments),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        if (state.isFetching || state.webResolving) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.label_loading),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = clipMgr?.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clip.isNullOrBlank()) {
                                viewModel.onCommentsTextChanged(
                                    if (state.commentsText.isNotBlank()) state.commentsText + "\n" + clip else clip
                                )
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.button_paste_comments), fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.commentsText,
                    onValueChange = { viewModel.onCommentsTextChanged(it) },
                    placeholder = {
                        Text(
                            if (state.isFetching || state.webResolving) stringResource(R.string.share_fetching_comments_auto)
                            else stringResource(R.string.comments_placeholder)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp, max = 160.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Preview downloaded media（多圖橫滑 + 影片/張數徽章，點擊全螢幕檢視）
                // 留言鏈存檔：母文圖 + 子文圖合併預覽（與 ThreadPostRepository.combinedMedia 同序：祖先在前），
                // 否則只看子文 media 會誤以為「留言無圖」或「圖對不上內文」（圖3/圖4 教訓）。
                val previewMedia = remember(state.fetchedResult) {
                    val fr = state.fetchedResult
                    if (fr == null) emptyList()
                    else com.reater.app.data.remote.MediaDedup.distinctFetched(
                        (fr.parentChain.flatMap { it.media } + fr.parentMedia) + fr.media
                    )
                }
                // 尚未落到本機的媒體：這些會在儲存後由背景下載 Worker 接續（通知欄看得到進度）
                val pendingPreviewCount = remember(previewMedia) {
                    previewMedia.count { it.localPath.isBlank() }
                }
                // 存到留言鏈（母文存在）時明確提示，避免誤以為存的是主串
                val isReplyChain = (state.fetchedResult?.parentChain?.isNotEmpty() == true) ||
                    state.fetchedResult?.parentShortcode?.isNotBlank() == true
                if (isReplyChain) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.notice_comment_chain),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                var viewerIndex by remember { mutableStateOf<Int?>(null) }
                // AUDIO 媒體 metadata（解析時附在 FetchedMedia 上）→ normalizeKey 映射，交給全螢幕匯出
                val audioMetaJsonByUrl = remember(previewMedia) {
                    previewMedia.filter { it.audioMetaJson.isNotBlank() }
                        .associate { com.reater.app.data.remote.MediaDedup.normalizeKey(it.remoteUrl) to it.audioMetaJson }
                }
                if (viewerIndex != null) {
                    com.reater.app.ui.MediaViewerDialog(
                        media = previewMedia.map {
                            com.reater.app.ui.ViewerMedia(kind = it.kind, remoteUrl = it.remoteUrl, localPath = it.localPath)
                        },
                        startIndex = viewerIndex ?: 0,
                        audioMetaJsonByUrl = audioMetaJsonByUrl,
                        onDismiss = { viewerIndex = null }
                    )
                }
                if (previewMedia.isNotEmpty()) {
                    Text(
                        text = if (pendingPreviewCount > 0) {
                            stringResource(R.string.media_progress, previewMedia.size - pendingPreviewCount, previewMedia.size)
                        } else {
                            stringResource(R.string.media_downloaded_count, previewMedia.size)
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        previewMedia.take(6).forEachIndexed { index, m ->
                            MediaThumbnailItem(
                                media = m,
                                previewMedia = previewMedia,
                                index = index,
                                onClick = { viewerIndex = index }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // 媒體提示：原連結是 /media（確定有圖/影）但一個 media 都沒解析到時提示
                if (previewMedia.isEmpty() && !state.isFetching &&
                    (state.hadMediaSuffix || state.fetchUrl.contains("/media"))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.media_no_urls),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Notes text field
                Text(
                    text = stringResource(R.string.label_personal_note),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.manualNote,
                    onValueChange = { viewModel.onNoteChanged(it) },
                    placeholder = { Text(stringResource(R.string.note_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp, max = 140.dp)
                        .bringIntoViewRequester(noteIntoView)
                        .onFocusChanged { noteFocused = it.isFocused },
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                } // Scrollable body; actions remain above IME and navigation insets.
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    val saveButtonLabel = when {
                        isNewCategory -> stringResource(R.string.button_create_category)
                        state.aiAnalyzeOnSave && state.hasApiKey -> stringResource(R.string.button_analyze_and_save)
                        else -> stringResource(R.string.save)
                    }
                    Button(
                        onClick = {
                            if (isNewCategory) {
                                // 先收起分類輸入框的鍵盤，再開啟既有新建分類視窗，
                                // 避免 IME 把底部選項頂上來遮住。
                                focusManager.clearFocus()
                                keyboard?.hide()
                                createPrefill = newCategoryName
                                viewModel.setShowCreateCategoryDialog(true)
                            } else {
                                viewModel.savePost()
                            }
                        },
                        enabled = !state.isSaving,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (state.aiAnalyzeOnSave && state.hasApiKey && !isNewCategory) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = saveButtonLabel,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaThumbnailItem(
    media: com.reater.app.data.remote.FetchedMedia,
    previewMedia: List<com.reater.app.data.remote.FetchedMedia>,
    index: Int,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isVideo = media.kind.equals("VIDEO", ignoreCase = true)
    val isAudio = media.kind.equals("AUDIO", ignoreCase = true)

    // 影片縮圖：非同步從影片檔案擷取影格並快取至 cache/video_thumbs
    val videoThumbFile by androidx.compose.runtime.produceState<File?>(initialValue = null, media.localPath) {
        if (isVideo && media.localPath.isNotBlank()) {
            value = com.reater.app.ui.player.VideoThumbnailHelper.getThumbnail(context, media.localPath)
        }
    }

    Box(
        modifier = Modifier
            .size(width = 140.dp, height = 100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        when {
            isAudio -> {
                val coverSource = previewMedia.firstOrNull {
                    it.kind.equals("IMAGE", ignoreCase = true)
                }?.let { if (it.localPath.isNotBlank()) File(it.localPath) else it.remoteUrl }
                if (coverSource != null) {
                    AsyncImage(
                        model = coverSource,
                        contentDescription = "Audio ${index + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("♪", fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            isVideo -> {
                if (videoThumbFile != null) {
                    AsyncImage(
                        model = videoThumbFile,
                        contentDescription = "Video ${index + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // 縮圖產生中或備用畫面：顯示統一的影片圖示，絕不留空白
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                // 縮圖中央播放按鈕（明確區分圖片與影片）
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            else -> {
                val imageSource = if (media.localPath.isNotBlank()) File(media.localPath) else media.remoteUrl
                AsyncImage(
                    model = imageSource,
                    contentDescription = "Post Media ${index + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // 左下角標籤
        if (isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("▶ 影片", fontSize = 10.sp, color = Color.White, maxLines = 1)
            }
        } else if (isAudio) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("♪ 音訊", fontSize = 10.sp, color = Color.White, maxLines = 1)
            }
        }
    }
}
