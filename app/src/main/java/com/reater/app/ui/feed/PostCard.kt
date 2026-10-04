package com.reater.app.ui.feed

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.data.local.entity.ItemDetail
import com.reater.app.ui.AvatarIcons
 import com.reater.app.ui.components.CategoryBadge
 import com.reater.app.ui.components.CategoryDropdown
import com.reater.app.ui.components.LinkifiedText
import com.reater.app.ui.components.formatSavedTime
import com.reater.app.ui.player.InlineVideoPlayer
import com.reater.app.ui.player.VideoPlaybackManager
import java.io.File

/**
 * 格式化單一貼文分享文字：
 * "摘要\n乾淨網址"（摘要有值＝ AI摘要 or 筆記；無摘要時只輸出網址）
 * 網址一律去除 ?xmt= / ?slof= 等追蹤參數後再輸出。
 */
fun formatPostShareText(itemDetail: ItemDetail): String {
    val url = com.reater.app.domain.UrlParser.stripTrackingParams(itemDetail.item.canonicalUrl)
    val summary = itemDetail.manualSummary.ifBlank { itemDetail.manualNote }.trim()
    return if (summary.isNotBlank()) "$summary\n$url" else url
}

@Composable
fun PostCard(
    itemDetail: ItemDetail,
    categories: List<CategoryEntity>,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onUpdateCategory: (Long?) -> Unit = {},
    onRequestCreateCategory: (prefill: String, itemId: Long) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val category = categories.firstOrNull { it.id == itemDetail.userEdit?.categoryId }
    val firstMedia = itemDetail.media.firstOrNull()
    var showMenu by remember { mutableStateOf(false) }
    // 列表直改分類：點中間徽章即展開儲存同款 CategoryDropdown，不必進詳情再點 ...
    var showCategoryEditor by remember(itemDetail.item.id, itemDetail.userEdit?.categoryId) {
        mutableStateOf(false)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                VideoPlaybackManager.pauseAll()
                onClick()
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (itemDetail.isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (itemDetail.isRead) 1.dp else 2.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: 左側讀取狀態選單 + 作者/分類，右側操作按鈕
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                ) {
                    // 左上方已/未讀狀態選單（不再是單擊切換，點擊展開選單供手動變更或標為未讀）
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (!itemDetail.isRead) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                                .clickable { showMenu = true }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (!itemDetail.isRead) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (!itemDetail.isRead) "未讀" else "已讀",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (!itemDetail.isRead) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.outline
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (itemDetail.isRead) "標示為未讀" else "標示為已讀")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (itemDetail.isRead) Icons.Default.MarkEmailUnread else Icons.Default.MarkEmailRead,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleRead()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("複製分享網址與摘要") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    val shareText = formatPostShareText(itemDetail)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Reater Share", shareText))
                                    Toast.makeText(context, "已複製網址與摘要", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "@${itemDetail.item.authorHandle.ifBlank { "threads_user" }}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (itemDetail.item.authorVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFF1DA1F2),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    // 分類徽章（含未分類）一律顯示，點即改分類（儲存同款選單）
                    Spacer(modifier = Modifier.width(8.dp))
                    CategoryBadge(
                        category = category,
                        onClick = { showCategoryEditor = !showCategoryEditor }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 一鍵複製分享單一網址 ("網址: 摘要")
                    IconButton(
                        onClick = {
                            val shareText = formatPostShareText(itemDetail)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Reater Share", shareText))
                            Toast.makeText(context, "已複製貼文網址與摘要", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "複製分享",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Open in external browser（去除追蹤參數後再開啟）
                    IconButton(
                        onClick = {
                            runCatching {
                                val url = com.reater.app.domain.UrlParser.stripTrackingParams(itemDetail.item.canonicalUrl)
                                if (url.isNotBlank()) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "外部開啟",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Favorite Toggle
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (itemDetail.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "收藏",
                            tint = if (itemDetail.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Move to Trash
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "移至垃圾桶",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 行內改分類（儲存同款 CategoryDropdown）：點徽章展開，選即生效
            if (showCategoryEditor) {
                Spacer(modifier = Modifier.height(6.dp))
                CategoryDropdown(
                    categories = categories,
                    selectedCategoryId = itemDetail.userEdit?.categoryId,
                    onSelect = {
                        onUpdateCategory(it)
                        showCategoryEditor = false
                        Toast.makeText(
                            context,
                            if (it == null) "已改為未分類" else "分類已更新",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onRequestCreate = { query ->
                        showCategoryEditor = false
                        onRequestCreateCategory(query, itemDetail.item.id)
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body Text — 點內文即展開詳情（像 Threads）；URL 仍可點擊外部跳轉
            LinkifiedText(
                text = remember(itemDetail.displayBody) {
                    com.reater.app.data.remote.threads.ThreadsSjsParser.stripSnippetMarkers(itemDetail.displayBody).trim()
                        .replace(Regex("\n{3,}"), "\n\n")
                        .ifBlank { "無內文" }
                },
                fontSize = 14.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                onNeutralClick = onClick
            )

            // 未展開卡片：圖片縮圖 + 影片直接顯示播放器；點圖片/影片/內文皆展開詳情
            if (firstMedia != null) {
                Spacer(modifier = Modifier.height(10.dp))
                if (firstMedia.kind.equals("IMAGE", ignoreCase = true)) {
                    val imageSource = if (firstMedia.localPath.isNotBlank() && File(firstMedia.localPath).exists()) {
                        File(firstMedia.localPath)
                    } else {
                        firstMedia.remoteUrl
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onClick)
                    ) {
                        AsyncImage(
                            model = imageSource,
                            contentDescription = "Post Media",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    ) {
                        InlineVideoPlayer(
                            remoteUrl = firstMedia.remoteUrl,
                            localPath = firstMedia.localPath,
                            autoPlay = false,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable {
                                    VideoPlaybackManager.pauseAll()
                                    onClick()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("詳情", fontSize = 11.sp, color = Color.White, maxLines = 1)
                        }
                    }
                }
            }

            if (itemDetail.manualSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "✨ AI 摘要: ${itemDetail.manualSummary}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (itemDetail.manualNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "筆記: ${itemDetail.manualNote}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (itemDetail.comments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "包含 ${itemDetail.comments.size} 則精華留言",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 儲存時間記錄
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatSavedTime(itemDetail.item.sourceFetchedAt),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
