package com.reater.app.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.data.local.entity.ItemDetail
import com.reater.app.data.remote.FetchedMediaJson
import com.reater.app.ui.MainViewModel
import com.reater.app.ui.MediaViewerDialog
import com.reater.app.ui.ViewerMedia
import com.reater.app.ui.components.CategoryBadge
import com.reater.app.ui.components.CategoryDropdown
import com.reater.app.ui.components.LinkifiedText
import com.reater.app.ui.components.formatSavedTime
import com.reater.app.ui.feed.formatPostShareText
import com.reater.app.ui.player.InlineVideoPlayer
import com.reater.app.ui.player.VideoPlaybackManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DetailDialog(
    item: ItemDetail,
    categories: List<CategoryEntity>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onMoveToTrash: () -> Unit
) {
    val context = LocalContext.current

    // 進入詳細檢視時，強制暫停背景 Feed 的所有影片播放；關閉詳細檢視時亦確保詳細檢視內的影片全部暫停銷毀
    DisposableEffect(Unit) {
        VideoPlaybackManager.pauseAll()
        onDispose {
            VideoPlaybackManager.pauseAll()
        }
    }
    var isSummarizing by remember { mutableStateOf(false) }
    var currentSummary by remember { mutableStateOf(item.manualSummary) }
    var showAiConsent by remember { mutableStateOf(false) }
    val aiConsent by viewModel.aiTransmissionConsent.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val category = categories.firstOrNull { it.id == item.userEdit?.categoryId }
    // 全螢幕媒體檢視器下標（null = 關閉）
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    // 左上 ... 溢位選單 + 改分類編輯器
    var showOverflow by remember { mutableStateOf(false) }
    var showCategoryEditor by remember { mutableStateOf(false) }

    if (viewerIndex != null) {
        MediaViewerDialog(
            media = item.media.map { ViewerMedia(kind = it.kind, remoteUrl = it.remoteUrl, localPath = it.localPath) },
            startIndex = viewerIndex ?: 0,
            onDismiss = { viewerIndex = null }
        )
    }

    // 留言內建圖片/影片的全螢幕檢視器（點留言圖片放大）
    var commentViewerMedia by remember { mutableStateOf<List<ViewerMedia>?>(null) }
    var commentViewerIndex by remember { mutableStateOf(0) }
    if (commentViewerMedia != null) {
        MediaViewerDialog(
            media = commentViewerMedia ?: emptyList(),
            startIndex = commentViewerIndex,
            onDismiss = { commentViewerMedia = null }
        )
    }

    fun runAiSummary() {
        isSummarizing = true
        coroutineScope.launch {
            val result = viewModel.summarizeItemWithAi(item)
            isSummarizing = false
            result.onSuccess {
                currentSummary = it
                Toast.makeText(context, "AI 分析完成！", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, err.message ?: "AI 分析失敗", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showAiConsent) {
        Dialog(onDismissRequest = { showAiConsent = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("允許 AI 分析？", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        IconButton(onClick = { showAiConsent = false }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "關閉", modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("按下同意後，這篇貼文內文與已保存的留言會傳送到你設定的 AI 服務端點，用於分類與摘要。Reater 不代收 API Key 或代付 AI 費用。你可以在設定中撤回同意。")
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showAiConsent = false }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            viewModel.setAiTransmissionConsent(true)
                            showAiConsent = false
                            runAiSummary()
                        }) { Text("同意並分析") }
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 固定頂欄：左上 ... 溢位選單 + 分類徽章（左）+ 分享 + X 關閉（最右）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(
                            onClick = { showOverflow = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "更多操作",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("複製分享網址與摘要") },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                },
                                onClick = {
                                    showOverflow = false
                                    val shareText = formatPostShareText(item)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Reater Share", shareText))
                                    Toast.makeText(context, "已複製貼文網址與摘要", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("改分類標籤") },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                },
                                onClick = {
                                    showOverflow = false
                                    showCategoryEditor = !showCategoryEditor
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("AI 智慧摘要")
                                        Text(
                                            text = "產生摘要・分類建議・標籤",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                },
                                onClick = {
                                    showOverflow = false
                                    if (aiConsent) runAiSummary() else showAiConsent = true
                                }
                            )
                        }
                    }
                    CategoryBadge(
                        category = category,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val shareText = formatPostShareText(item)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Reater Share", shareText))
                                Toast.makeText(context, "已複製貼文網址與摘要", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "複製分享",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "關閉",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                // 改分類編輯器（由 ... 選單展開）：下拉篩選 + 即時生效
                if (showCategoryEditor) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CategoryDropdown(
                        categories = categories,
                        selectedCategoryId = item.userEdit?.categoryId,
                        onSelect = {
                            viewModel.updateCategory(item.item.id, it)
                            Toast.makeText(
                                context,
                                if (it == null) "已改為未分類" else "分類已更新",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onRequestCreate = {
                            Toast.makeText(context, "請至 PRO 分類以 + 新增分類", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 作者列 + 儲存時間
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "@${item.item.authorHandle}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.item.authorVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFF1DA1F2),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatSavedTime(item.item.sourceFetchedAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(10.dp))

                // External Link Button（去除追蹤參數後再開啟）
                if (item.item.canonicalUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(com.reater.app.domain.UrlParser.stripTrackingParams(item.item.canonicalUrl))
                                )
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "在 Threads / 瀏覽器開啟",
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Media Preview
                item.media.forEachIndexed { mediaIndex, m ->
                    val imageSource = if (m.localPath.isNotBlank() && File(m.localPath).exists()) {
                        File(m.localPath)
                    } else {
                        m.remoteUrl
                    }
                    val isVideo = m.kind.equals("VIDEO", ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .then(if (isVideo) Modifier else Modifier.clickable { viewerIndex = mediaIndex })
                    ) {
                        if (isVideo) {
                            InlineVideoPlayer(
                                remoteUrl = m.remoteUrl,
                                localPath = m.localPath,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AsyncImage(
                                model = imageSource,
                                contentDescription = "Media",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { viewerIndex = mediaIndex },
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (item.media.isEmpty() && item.item.lastFetchStatus == "PARTIAL") {
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
                            text = "圖片/影片未能自動下載，可點上方按鈕在 Threads 查看原貼文。",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Post Body
                run {
                    val rawBody = item.displayBody.trim()
                    val isUrlOnly = rawBody.startsWith("http", ignoreCase = true) &&
                        rawBody.lines().size == 1 && rawBody.length < 500 &&
                        (rawBody.contains("threads.com") || rawBody.contains("threads.net"))
                    if (isUrlOnly) {
                        Text(
                            text = "無內文",
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinkifiedText(
                            text = rawBody,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            softWrap = true,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinkifiedText(
                            text = rawBody.ifBlank { "無內文" },
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            softWrap = true,
                            color = if (rawBody.isBlank()) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (currentSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "✨ AI 智能摘要",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentSummary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (item.manualNote.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "個人筆記", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = item.manualNote, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                }

                if (item.comments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "精華留言 (${item.comments.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    item.comments.forEach { c ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(text = "@${c.author}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            LinkifiedText(
                                text = c.text,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val commentMedia = FetchedMediaJson.decode(c.mediaJson)
                            if (commentMedia.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                commentMedia.forEachIndexed { ci, cm ->
                                    val cmIsVideo = cm.kind.equals("VIDEO", ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(170.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        if (cmIsVideo) {
                                            InlineVideoPlayer(
                                                remoteUrl = cm.remoteUrl,
                                                localPath = cm.localPath,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            val cmSource = if (cm.localPath.isNotBlank() && File(cm.localPath).exists()) {
                                                File(cm.localPath)
                                            } else {
                                                cm.remoteUrl
                                            }
                                            AsyncImage(
                                                model = cmSource,
                                                contentDescription = "留言圖片",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clickable {
                                                        commentViewerMedia = commentMedia.map { m ->
                                                            ViewerMedia(kind = m.kind, remoteUrl = m.remoteUrl, localPath = m.localPath)
                                                        }
                                                        commentViewerIndex = ci
                                                    },
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 底部操作列：方形按鈕、左右均分不再擠壓換行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onMoveToTrash,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("移至垃圾桶", fontSize = 13.sp, maxLines = 1, softWrap = false)
                    }

                    Button(
                        onClick = {
                            if (aiConsent) runAiSummary() else showAiConsent = true
                        },
                        enabled = !isSummarizing,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        if (isSummarizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI 摘要", fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
                }
            }
        }
    }
}
