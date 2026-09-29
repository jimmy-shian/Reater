package com.reater.app.ui.share

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.reater.app.R
import com.reater.app.data.remote.threads.ThreadsWebResolver
import com.reater.app.ui.AvatarIcons
import com.reater.app.ui.components.dismissFocusOnTap
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

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
                // /share/ 連結：OkHttp 跟不到 JS 跳轉，改用隱藏 WebView 跑完 JS 再解。
                // 條件：是 /share/、SSR 直抓已結束、還沒試過 WebView。
                LaunchedEffect(
                    webState.fetchUrl,
                    webState.webResolveAttempted,
                    webState.webResolving,
                    webState.isFetching,
                    webState.fetchedResult
                ) {
                    val needShareResolve = !webState.webResolveAttempted && !webState.webResolving &&
                        !webState.isFetching && webState.fetchUrl.contains("/share/")
                    // 免登入留言補強：canonical 已抓完但零留言，且有內文/媒體時，用真瀏覽器指紋再補一次
                    val fetched = webState.fetchedResult
                    val needCommentEnrich = !webState.webResolveAttempted && !webState.webResolving &&
                        !webState.isFetching && !webState.fetchUrl.contains("/share/") &&
                        fetched != null && fetched.comments.isEmpty() &&
                        (fetched.bodyText.isNotBlank() || fetched.media.isNotEmpty())
                    if (needShareResolve || needCommentEnrich) {
                        viewModel.markWebResolveStarted()
                        ThreadsWebResolver.resolve(this@ShareSaveActivity, webState.fetchUrl) { page ->
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
                ShareSaveScreen(
                    viewModel = viewModel,
                    onDismiss = { finish() },
                    onSaved = {
                        Toast.makeText(this, "已儲存至 Reater！", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "分享文字已超過 200 KB，上限外內容已截除。", Toast.LENGTH_LONG).show()
        }
        return result.toString()
    }

    private companion object {
        const val MAX_SHARED_TEXT_BYTES = 200 * 1024
    }
}

@Composable
fun ShareSaveScreen(
    viewModel: ShareSaveViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isPro by viewModel.isProUnlocked.collectAsState()
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onSaved()
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
                        title = com.reater.app.ui.components.ProCopy.SHARE_LIMIT_TITLE,
                        onClose = { viewModel.setShowProLimitNotice(false) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = com.reater.app.ui.components.ProCopy.SHARE_LIMIT_DESC,
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
                            Text("我知道了")
                        }
                    }
                }
            }
        }
    }

    // Create Category Dialog（支援下拉選單帶入篩選關鍵字預填）
    var createPrefill by remember { mutableStateOf("") }
    // 下拉選單要求新增時帶入關鍵字：透過 key 重建對話框初始值
    if (state.showCreateCategoryDialog) {
        var catName by remember(createPrefill) { mutableStateOf(createPrefill) }
        var selectedAvatar by remember { mutableStateOf("life") }

        Dialog(onDismissRequest = {
            createPrefill = ""
            viewModel.setShowCreateCategoryDialog(false)
        }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    com.reater.app.ui.components.DialogHeader(
                        title = "新建分類",
                        onClose = {
                            createPrefill = ""
                            viewModel.setShowCreateCategoryDialog(false)
                        },
                        modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
                    )
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .dismissFocusOnTap(focusManager)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                    // 配額提示：免費版 3 個自訂分類上限（內建 8 分類不計）
                    run {
                        val customCount = categories.count { !it.isDefault }
                        val remaining = com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT - customCount
                        if (!isPro) {
                            Text(
                                text = if (remaining > 0) "免費版還可新增 $remaining 個自訂分類（已用 $customCount/3）"
                                else "免費版自訂分類已滿（3/3），升級 Pro 可無限新增",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = catName,
                        onValueChange = { catName = it },
                        placeholder = { Text("分類名稱 (如：技術、生活、閱讀)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "選擇頭像標籤圖示",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AvatarIcons.ALL.forEach { item ->
                            val isSelected = selectedAvatar == item.id
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
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedAvatar = item.id },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = item.resId),
                                    contentDescription = item.name,
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = {
                            createPrefill = ""
                            viewModel.setShowCreateCategoryDialog(false)
                        }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.createCategory(catName, selectedAvatar)
                                createPrefill = ""
                            },
                            enabled = catName.isNotBlank()
                        ) {
                            Text("建立")
                        }
                    }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .dismissFocusOnTap(focusManager),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 固定頂欄：標題 + 外部開啟 + X，不隨捲動離開
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.share_save_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.targetUrl.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.targetUrl))
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "外部開啟",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "關閉",
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
                        .verticalScroll(rememberScrollState())
                        .imePadding()
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
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )
                }

                // Author Info + 分享種類徽章（串文 / 留言/分享）
                if (state.authorHandle.isNotBlank() || state.shareKindLabel.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
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

                // Status info banner
                if (state.isFetching || state.webResolving) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.webResolving) "正在以內建瀏覽器解析分享連結（含留言與媒體）..."
                            else "正在自動分析 Threads 內容與下載離線媒體...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (state.isFetchFailed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
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
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Preview downloaded media（多圖橫滑 + 影片/張數徽章，點擊全螢幕檢視）
                val previewMedia = state.fetchedResult?.media.orEmpty()
                var viewerIndex by remember { mutableStateOf<Int?>(null) }
                if (viewerIndex != null) {
                    com.reater.app.ui.MediaViewerDialog(
                        media = previewMedia.map {
                            com.reater.app.ui.ViewerMedia(kind = it.kind, remoteUrl = it.remoteUrl, localPath = it.localPath)
                        },
                        startIndex = viewerIndex ?: 0,
                        onDismiss = { viewerIndex = null }
                    )
                }
                if (previewMedia.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        previewMedia.take(6).forEachIndexed { index, m ->
                            val imageSource = if (m.localPath.isNotBlank()) {
                                File(m.localPath)
                            } else {
                                m.remoteUrl
                            }
                            Box(
                                modifier = Modifier
                                    .size(width = 160.dp, height = 120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { viewerIndex = index }
                            ) {
                                AsyncImage(
                                    model = imageSource,
                                    contentDescription = "Post Media ${index + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (m.kind.equals("VIDEO", ignoreCase = true)) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("▶ 影片", fontSize = 10.sp, color = Color.White, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                    if (previewMedia.size > 1) {
                        Text(
                            text = "共 ${previewMedia.size} 個媒體",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                // 媒體提示：原連結是 /media（確定有圖/影）但自動下載掛零時，
                // 明確告訴使用者去 Threads 看，避免以為存檔壞掉
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
                            text = "此貼文含圖片/影片，自動下載失敗，請點右上開啟 Threads 查看。",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Category Selection：下拉選單 + 輸入篩選（自訂再多也不爆版）
                Text(
                    text = "選擇分類",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                com.reater.app.ui.components.CategoryDropdown(
                    categories = categories,
                    selectedCategoryId = state.selectedCategoryId,
                    onSelect = { viewModel.onCategorySelected(it) },
                    onRequestCreate = { prefill ->
                        createPrefill = prefill
                        viewModel.setShowCreateCategoryDialog(true)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Body text field（標題讓位給按鈕，避免窄螢幕互擠）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "貼文內文",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = clipMgr?.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clip.isNullOrBlank()) {
                                viewModel.onBodyTextChanged(clip)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("貼上內文", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.bodyText,
                    onValueChange = { viewModel.onBodyTextChanged(it) },
                    placeholder = { Text(stringResource(R.string.body_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 220.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Comments text field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "精華留言",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
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
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("貼上留言", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.commentsText,
                    onValueChange = { viewModel.onCommentsTextChanged(it) },
                    placeholder = { Text(stringResource(R.string.comments_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 180.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Notes text field
                Text(
                    text = "個人筆記",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.manualNote,
                    onValueChange = { viewModel.onNoteChanged(it) },
                    placeholder = { Text(stringResource(R.string.note_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp, max = 140.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { viewModel.savePost() },
                        enabled = !state.isSaving
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
                }
            }
        }
    }
}
