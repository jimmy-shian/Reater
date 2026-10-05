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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalFoundationApi::class)
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

    // 主內容捲動狀態：注意「捲動即收鍵盤」在此頁停用——鍵盤彈出時的 ime 佈局變化
    // 會觸發內容捲動，若此時 clearFocus 就會形成「彈鍵盤→被捲動→被收焦點→收鍵盤」
    // 的閃爍迴圈，導致精華留言/個人筆記點不出鍵盤。只保留「點空白收鍵盤」。
    val shareContentScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    // 輸入框焦點鏈：內文 Next → 留言 Next → 筆記 Done，鍵盤不蓋住輸入框
    val bodyFocusRequester = remember { FocusRequester() }
    val commentsFocusRequester = remember { FocusRequester() }
    val noteFocusRequester = remember { FocusRequester() }
    val bodyBiv = remember { BringIntoViewRequester() }
    val commentsBiv = remember { BringIntoViewRequester() }
    val noteBiv = remember { BringIntoViewRequester() }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onSaved()
        }
    }

    // 免費版配額提示（文案統一至 ProCopy，不使用第三方比喻）
    if (state.showProLimitNotice) {
        com.reater.app.ui.theme.AppDialog(onDismissRequest = { viewModel.setShowProLimitNotice(false) }) {
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
                        lineHeight = 18.sp,
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

    // 同一篇已存在：覆蓋並更新內容（重新抓取）/ 另存一篇新的（方形按鈕、左右均分）
    if (state.showDuplicateDialog) {
        com.reater.app.ui.theme.AppDialog(onDismissRequest = { viewModel.dismissDuplicateDialogs() }) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    com.reater.app.ui.components.DialogHeader(
                        title = "已經儲存過這篇文章",
                        onClose = { viewModel.dismissDuplicateDialogs() }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "要覆蓋並更新內容（重新抓取），還是另存一篇新的？",
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.confirmSaveAsNew() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("另存一篇新的", maxLines = 2, softWrap = true, textAlign = TextAlign.Center)
                        }
                        Button(
                            onClick = { viewModel.confirmOverwrite() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("覆蓋並更新內容", maxLines = 2, softWrap = true, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    // 同一篇在垃圾桶：復原並更新內容（重新抓取）/ 僅復原（方形按鈕、左右均分）
    if (state.showTrashDialog) {
        com.reater.app.ui.theme.AppDialog(onDismissRequest = { viewModel.dismissDuplicateDialogs() }) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    com.reater.app.ui.components.DialogHeader(
                        title = "這篇文章在垃圾桶",
                        onClose = { viewModel.dismissDuplicateDialogs() }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "要復原並更新內容（重新抓取），還是僅復原？",
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.confirmRestoreOnly() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("僅復原", maxLines = 2, softWrap = true, textAlign = TextAlign.Center)
                        }
                        Button(
                            onClick = { viewModel.confirmRestoreAndUpdate() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("復原並更新內容", maxLines = 2, softWrap = true, textAlign = TextAlign.Center)
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
            if (remaining > 0) "免費版還可新增 $remaining 個自訂分類（已用 $customCount/3）"
            else "免費版自訂分類已滿（3/3），升級 Pro 可無限新增"
        } else null
        androidx.compose.runtime.key(createPrefill) {
            com.reater.app.ui.components.CategoryCreateDialog(
                initialName = createPrefill,
                quotaText = quotaText,
                isPro = isPro,
                onProIconLocked = {
                    Toast.makeText(context, "此為 Pro 專屬圖示，請先升級解鎖", Toast.LENGTH_SHORT).show()
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
            // ime inset 在最外層一次處理：鍵盤彈出時整個卡片縮小，不在捲動區內再縮一次
            .imePadding()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 20.dp)
            // 只留外層點空白收鍵盤：子元件（TextField/按鈕）會先消費點擊，不會誤清焦點
            .dismissFocusOnTap(focusManager),
        contentAlignment = Alignment.TopCenter
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
                                contentDescription = if (state.isFavorite) "已珍藏" else "加入珍藏",
                                tint = if (state.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.outline
                            )
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
                        .verticalScroll(shareContentScrollState)
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
                                    fontSize = 12.sp,
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
                    text = "選擇分類",
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
                    }
                )
                // 歷史預選提示：同主題/同作者過往最常用分類（手動更改後消失）
                AnimatedVisibility(visible = state.suggestedBasis.isNotBlank()) {
                    Text(
                        text = if (state.suggestedBasis == "topic") "已依過往同主題紀錄預選分類" else "已依過往同作者紀錄預選分類",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Status info banner
                if (state.isFetching || state.webResolving) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
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
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
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
                            text = "貼文內文",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        if (state.isFetching || state.webResolving) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "載入中…",
                                fontSize = 12.sp,
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
                        Text("貼上內文", fontSize = 12.sp, maxLines = 1, softWrap = false)
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
                            if (state.isFetching || state.webResolving) "正在自動擷取 Threads 內容中…"
                            else stringResource(R.string.body_placeholder)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 220.dp)
                        .focusRequester(bodyFocusRequester)
                        .bringIntoViewRequester(bodyBiv)
                        .onFocusChanged { if (it.isFocused) scope.launch { bodyBiv.bringIntoView() } },
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { commentsFocusRequester.requestFocus() })
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
                            text = "精華留言",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        if (state.isFetching || state.webResolving) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "載入中…",
                                fontSize = 12.sp,
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
                        Text("貼上留言", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.commentsText,
                    onValueChange = { viewModel.onCommentsTextChanged(it) },
                    placeholder = {
                        Text(
                            if (state.isFetching || state.webResolving) "正在自動擷取精華留言中…"
                            else stringResource(R.string.comments_placeholder)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp, max = 160.dp)
                        .focusRequester(commentsFocusRequester)
                        .bringIntoViewRequester(commentsBiv)
                        .onFocusChanged { if (it.isFocused) scope.launch { commentsBiv.bringIntoView() } },
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { noteFocusRequester.requestFocus() })
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
                            text = "你存的是留言鏈：上方【母文】為原貼（含原圖），下方為該則留言。要存主串請分享主貼文連結。",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
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
                    Text(
                        text = "已下載媒體 (${previewMedia.size})",
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
                            val imageSource = if (m.localPath.isNotBlank()) {
                                File(m.localPath)
                            } else {
                                m.remoteUrl
                            }
                            Box(
                                modifier = Modifier
                                    .size(width = 140.dp, height = 100.dp)
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
                                        Text("▶ 影片", fontSize = 12.sp, color = Color.White, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // 媒體提示：原連結是 /media（確定有圖/影）但自動下載掛零時提示
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
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Notes text field
                Text(
                    text = "個人筆記",
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
                        .focusRequester(noteFocusRequester)
                        .bringIntoViewRequester(noteBiv)
                        .onFocusChanged { if (it.isFocused) scope.launch { noteBiv.bringIntoView() } },
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { viewModel.savePost() },
                        enabled = !state.isSaving,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
                }
            }
        }
    }
}
