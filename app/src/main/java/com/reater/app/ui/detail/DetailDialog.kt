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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import com.reater.app.ui.components.ThreadsStatsRow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.data.local.entity.CommentEntity
import com.reater.app.data.local.entity.ItemDetail
import com.reater.app.data.remote.FetchedMedia
import com.reater.app.data.remote.FetchedMediaJson
import com.reater.app.ui.MainViewModel
import com.reater.app.ui.MediaViewerDialog
import com.reater.app.ui.ViewerMedia
import com.reater.app.ui.components.CategoryBadge
import com.reater.app.ui.components.CategoryDropdown
import com.reater.app.ui.components.CopyableTextBlock
import com.reater.app.ui.components.LinkifiedText
import com.reater.app.ui.components.formatSavedTime
import com.reater.app.ui.feed.formatPostShareText
import com.reater.app.ui.player.InlineVideoPlayer
import com.reater.app.ui.player.VideoPlaybackManager
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

private data class ThreadPostBlock(
    val author: String,
    val isParent: Boolean,
    val body: String,
    /** 區塊標籤：「母文」/「分享」，主文為 null */
    val label: String? = null,
    val isVerified: Boolean = false
)

/** 母文前綴：【母文 @author】 */
private val THREAD_ROOT_PREFIX = Regex("""^【母文\s*@([^】]+)】""")

/**
 * 分享分隔線（必須自成一行）：--- 分享 @handle ---
 * handle 允許點/底線/連字號；行尾僅允許空白，內文裡的零散 --- 不會誤判。
 */
private val THREAD_SHARE_SEP =
    Regex("""(?m)^[ \t]*---[ \t]*分享[ \t]*@([^\r\n]+?)[ \t]*---[ \t]*\r?$""")

/**
 * 解析螺紋鏈為有序區塊（母文 → … → 主文）。
 *
 * 支援 N 層：「留言的留言」會存成
 * 【母文 @A】a\n\n--- 分享 @B ---\nb\n\n--- 分享 @C ---\nc。
 * 舊版只拆首尾兩層，中間層會殘留成 --- 分享 @B --- 原文（看起來像 3 層亂文）；
 * 此處把每一層都拆成獨立區塊，由 UI 逐塊渲染。
 */
private fun parseThreadChain(rawBody: String, defaultAuthor: String): List<ThreadPostBlock> {
    val trimmed = rawBody.trim()
    if (trimmed.isEmpty()) return listOf(ThreadPostBlock(defaultAuthor, false, ""))
    val rootMatch = THREAD_ROOT_PREFIX.find(trimmed)
        ?: return listOf(ThreadPostBlock(defaultAuthor, false, trimmed))
    val rootAuthor = rootMatch.groupValues[1].trim().trimStart('@').trim()
    if (rootAuthor.isBlank()) return listOf(ThreadPostBlock(defaultAuthor, false, trimmed))
    val rest = trimmed.substring(rootMatch.range.last + 1)
    val seps = THREAD_SHARE_SEP.findAll(rest).toList()
    if (seps.isEmpty()) {
        // 只有母文前綴、無分享分隔：單一母文塊（由 UI 當主文渲染，保留媒體/數據列）
        return listOf(ThreadPostBlock(rootAuthor, true, rest.trim(), label = "母文"))
    }
    val blocks = ArrayList<ThreadPostBlock>(seps.size + 1)
    blocks.add(
        ThreadPostBlock(
            author = rootAuthor,
            isParent = true,
            body = rest.substring(0, seps[0].range.first).trim(),
            label = "母文"
        )
    )
    for (i in seps.indices) {
        var handle = seps[i].groupValues[1].trim().trimStart('@').trim()
        // 防呆：分隔行 @ 後若夾雜空白，一律取第一段當帳號
        handle = handle.split(Regex("\\s+")).firstOrNull()?.trim().orEmpty()
        if (handle.isBlank()) handle = defaultAuthor
        val start = seps[i].range.last + 1
        val end = if (i + 1 < seps.size) seps[i + 1].range.first else rest.length
        val body = rest.substring(start, end).trim()
        val isLast = (i == seps.lastIndex)
        blocks.add(
            ThreadPostBlock(
                author = handle,
                isParent = !isLast,
                body = body,
                label = if (isLast) null else "分享"
            )
        )
    }
    // 避免病態堆疊拖慢渲染
    return blocks.take(6)
}

/** 普通訊息只可展開；只有 Threads snippet 結構才顯示複製卡。 */
private const val PLAIN_COLLAPSE_MIN_CHARS = 60
private const val PLAIN_COLLAPSE_MIN_LINES = 4

/**
 * 拆分「一般訊息 + 文字區塊」：
 * - 有結構標記：精確拆成普通訊息與 snippet，只有 snippet 可複製。
 * - 沒有結構標記：全文都是普通訊息，不因長度而顯示複製鍵。
 */
private fun splitLeadAndSnippet(text: String): Pair<String, String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return "" to ""
    if (com.reater.app.data.remote.threads.ThreadsSjsParser.hasSnippetBlock(trimmed)) {
        return com.reater.app.data.remote.threads.ThreadsSjsParser.splitSnippetBlock(trimmed)
    }
    // 不用字數/換行數猜測，避免普通留言被誤顯示成可複製卡片。
    return trimmed to ""
}

/**
 * 可收合的純文字（一般訊息用：無灰框、無複製鍵；過長才出現展開/收起）。
 */
@Composable
private fun CollapsiblePlainText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 4,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 18.sp,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    if (text.isBlank()) return
    var expanded by remember(text) { mutableStateOf(false) }
    val collapsible = text.length > PLAIN_COLLAPSE_MIN_CHARS || text.lines().size >= PLAIN_COLLAPSE_MIN_LINES
    val maxLines = if (!collapsible || expanded) Int.MAX_VALUE else collapsedMaxLines
    Column(modifier = modifier.fillMaxWidth()) {
        LinkifiedText(
            text = text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = color,
            softWrap = true,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onNeutralClick = { if (collapsible) expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        )
        if (collapsible) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (expanded) "收起" else "展開全文",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展開全文",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 選擇性可複製文字：
 * - 一般訊息（短或長）→ 純文字顯示 (無灰框、無複製鍵；長文可展開收起)。
 * - 文字區塊 (snippet) → CopyableTextBlock (灰框 + 複製鍵 + 展開收起)。
 * - 混合 (一般訊息 + 文字區塊) → 上方純文字 + 下方灰框 (只複製文字區塊)。
 */
@Composable
private fun SelectiveTextBlock(
    text: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    collapsedMaxLines: Int = 6,
    collapseThresholdChars: Int = 200,
    collapseThresholdLines: Int = 7,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 18.sp,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    if (text.isBlank()) return
    val (lead, snippet) = remember(text) { splitLeadAndSnippet(text) }
    if (lead.isNotBlank() && snippet.isNotBlank()) {
        Column(modifier = modifier.fillMaxWidth()) {
            CollapsiblePlainText(
                text = lead,
                collapsedMaxLines = 4,
                fontSize = fontSize,
                lineHeight = lineHeight,
                color = color,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            CopyableTextBlock(
                text = snippet,
                label = label,
                collapsedMaxLines = collapsedMaxLines,
                collapseThresholdChars = collapseThresholdChars,
                collapseThresholdLines = collapseThresholdLines,
                fontSize = fontSize,
                lineHeight = lineHeight,
                color = color,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else if (snippet.isNotBlank()) {
        CopyableTextBlock(
            text = snippet,
            label = label,
            collapsedMaxLines = collapsedMaxLines,
            collapseThresholdChars = collapseThresholdChars,
            collapseThresholdLines = collapseThresholdLines,
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = color,
            modifier = modifier.fillMaxWidth()
        )
    } else {
        CollapsiblePlainText(
            text = lead.ifBlank { text.trim() },
            collapsedMaxLines = collapsedMaxLines,
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = color,
            modifier = modifier.fillMaxWidth()
        )
    }
}

/**
 * 留言鏈各區塊媒體歸屬（對應圖3/圖4 錯亂修正）。
 *
 * 新存檔：ThreadPostRepository.buildChainRawJson 已把每層祖先 + 主文各自的
 * remoteUrl 寫進 ItemEntity.rawJsonMin（chainMedia/mainMedia），此處用
 * remoteUrl 反查 MediaEntity（取 localPath 離線檔）逐塊還原。
 * 例：母文 pito 銀晝戰績/裝備/排行圖 → 母文塊；子文 1yuunu2 提問（無圖）→ 空。
 *
 * 舊存檔（rawJsonMin 無 chainMedia，只有合併後的 item.media）：
 * 合併順序恆為「祖先在前、子文在後」，且子留言通常無圖（如黃色盾牌提問），
 * 若仍全掛在最後一塊子文下就會出現「留言配主文圖」。因此舊資料一律把
 * 全部媒體歸還給第一塊母文，主文顯示空並由 UI 標註「舊資料已自動歸位」。
 */
private fun resolveThreadBlockMedia(
    item: ItemDetail,
    blocks: List<ThreadPostBlock>
): List<List<com.reater.app.data.local.entity.MediaEntity>> {
    if (blocks.isEmpty()) return emptyList()
    // 單塊：已存髒資料可能含同內容多變體（多解析度/多碼率存成多列），顯示前先正規化去重
    if (blocks.size == 1) return listOf(
        com.reater.app.data.remote.MediaDedup.distinctEntities(item.media.sortedBy { it.position })
    )
    // 正規化查表：同一內容不同清晰度 URL 視為同一媒體（byUrl 同時支援精確與正規化命中）
    val byUrlExact = item.media.associateBy { it.remoteUrl }
    val byUrlNorm = LinkedHashMap<String, com.reater.app.data.local.entity.MediaEntity>()
    for (m in item.media.sortedBy { it.position }) {
        byUrlNorm.putIfAbsent(
            com.reater.app.data.remote.MediaDedup.normalizeKey(m.remoteUrl),
            m
        )
    }
    fun lookup(url: String): com.reater.app.data.local.entity.MediaEntity? {
        byUrlExact[url]?.let { return it }
        return byUrlNorm[com.reater.app.data.remote.MediaDedup.normalizeKey(url)]
    }
    try {
        val raw = item.item.rawJsonMin
        if (raw.isNotBlank() && raw.contains("chainMedia")) {
            val root = org.json.JSONObject(raw)
            val chainArr = root.optJSONArray("chainMedia")
            val mainArr = root.optJSONArray("mainMedia")
            if (chainArr != null) {
                val out = ArrayList<List<com.reater.app.data.local.entity.MediaEntity>>(blocks.size)
                // 祖先塊：blocks[0..n-2] ← chainMedia[0..]
                for (i in 0 until blocks.size - 1) {
                    val urls = chainArr.optJSONObject(i)?.optJSONArray("media")
                    val list = ArrayList<com.reater.app.data.local.entity.MediaEntity>()
                    if (urls != null) {
                        for (j in 0 until urls.length()) {
                            val u = urls.optString(j).trim()
                            if (u.isBlank()) continue
                            lookup(u)?.let { list.add(it) }
                                ?: run {
                                    // 離線檔遺失仍用遠端顯示，避免整塊消失
                                    list.add(
                                        com.reater.app.data.local.entity.MediaEntity(
                                            itemId = item.item.id,
                                            kind = "IMAGE",
                                            remoteUrl = u,
                                            localPath = "",
                                            position = -1
                                        )
                                    )
                                }
                        }
                    }
                    out.add(list)
                }
                // 主文塊 ← mainMedia
                val mainList = ArrayList<com.reater.app.data.local.entity.MediaEntity>()
                if (mainArr != null) {
                    for (j in 0 until mainArr.length()) {
                        val u = mainArr.optString(j).trim()
                        if (u.isBlank()) continue
                        lookup(u)?.let { mainList.add(it) }
                            ?: run {
                                mainList.add(
                                    com.reater.app.data.local.entity.MediaEntity(
                                        itemId = item.item.id,
                                        kind = "IMAGE",
                                        remoteUrl = u,
                                        localPath = "",
                                        position = -1
                                    )
                                )
                            }
                    }
                }
                out.add(mainList)
                // 跨塊去重：同一內容同時出現在母文與主文映射時只留首次，避免雙份顯示；
                // 塊內同內容多變體也只留首個（含已存髒資料）
                val deduped = ArrayList<List<com.reater.app.data.local.entity.MediaEntity>>(out.size)
                val seen = LinkedHashSet<String>()
                for (block in out) {
                    val kept = ArrayList<com.reater.app.data.local.entity.MediaEntity>(block.size)
                    for (m in com.reater.app.data.remote.MediaDedup.distinctEntities(block)) {
                        val key = com.reater.app.data.remote.MediaDedup.normalizeKey(m.remoteUrl)
                            .ifBlank { m.remoteUrl.trim() }
                        if (key.isBlank() || !seen.add(key)) continue
                        kept.add(m)
                    }
                    deduped.add(kept)
                }
                // 容錯：解析成功但全空、而 item.media 非空（去重/寫入異常）→ 回退舊邏輯
                if (deduped.flatten().isEmpty() && item.media.isNotEmpty()) {
                    return listOf(
                        com.reater.app.data.remote.MediaDedup.distinctEntities(
                            item.media.sortedBy { it.position }
                        )
                    ) +
                        List(blocks.size - 1) { emptyList<com.reater.app.data.local.entity.MediaEntity>() }
                }
                return deduped
            }
        }
    } catch (_: Exception) {
        // 落到舊資料相容
    }
    // 舊資料相容：全部歸還母文（blocks[0]），子文清空（顯示前先去重，避免已存雙份）
    val sorted = com.reater.app.data.remote.MediaDedup.distinctEntities(item.media.sortedBy { it.position })
    return listOf(sorted) + List(blocks.size - 1) { emptyList<com.reater.app.data.local.entity.MediaEntity>() }
}

/** 是否為舊合併資料（無 chainMedia 映射、卻有多塊 + 有媒體）：需顯示歸位提示 */
private fun isLegacyMergedMedia(item: ItemDetail, blocks: List<ThreadPostBlock>): Boolean {
    if (blocks.size <= 1 || item.media.isEmpty()) return false
    return !item.item.rawJsonMin.contains("chainMedia")
}

private val AVATAR_PALETTE = listOf(
    Color(0xFF6750A4),
    Color(0xFF386A20),
    Color(0xFF006874),
    Color(0xFF984061),
    Color(0xFF7D5260),
    Color(0xFF1E88E5),
    Color(0xFFD81B60),
    Color(0xFF8E24AA),
    Color(0xFF00897B),
    Color(0xFFF4511E)
)

private fun getAvatarColor(name: String): Color {
    val hash = abs(name.hashCode())
    return AVATAR_PALETTE[hash % AVATAR_PALETTE.size]
}

/** Threads 會保留輪播原圖比例；固定正方形裁切會讓儲存畫面和原文不同。 */
private fun mediaAspectRatio(width: Int, height: Int, fallback: Float = 1f): Float {
    if (width <= 0 || height <= 0) return fallback
    return (width.toFloat() / height.toFloat()).coerceIn(0.55f, 1.8f)
}

@Composable
private fun ThreadAvatar(
    name: String,
    profileUrl: String = "",
    modifier: Modifier = Modifier.size(36.dp)
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(getAvatarColor(name)),
        contentAlignment = Alignment.Center
    ) {
        if (profileUrl.isNotBlank()) {
            AsyncImage(
                model = profileUrl,
                contentDescription = "$name 頭像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

/**
 * 圖2 文章圖片簡易左右顯示：單張維持大圖；多張改橫滑縮圖列（省垂直空間）。
 * 縮圖 132dp 正方裁切（Crop 無灰邊），點任一開全螢幕檢視器；右上顯示「n 張・左右滑」提示。
 */
@Composable
private fun MediaGalleryRow(
    media: List<com.reater.app.data.local.entity.MediaEntity>,
    onOpenAt: (com.reater.app.data.local.entity.MediaEntity) -> Unit
) {
    if (media.isEmpty()) return
    if (media.size == 1) {
        val m = media[0]
        val source: Any = if (m.localPath.isNotBlank() && File(m.localPath).exists()) {
            File(m.localPath)
        } else {
            m.remoteUrl
        }
        val isVideo = m.kind.equals("VIDEO", ignoreCase = true)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(mediaAspectRatio(m.width, m.height, 1.15f))
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (isVideo) Modifier else Modifier.clickable { onOpenAt(m) })
        ) {
            if (isVideo) {
                InlineVideoPlayer(
                    remoteUrl = m.remoteUrl,
                    localPath = m.localPath,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = source,
                    contentDescription = "貼文圖片",
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onOpenAt(m) },
                    contentScale = ContentScale.Crop
                )
            }
        }
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${media.size} 張・左右滑動查看",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(media, key = { _, m -> m.remoteUrl + m.localPath }) { _, m ->
                val source: Any = if (m.localPath.isNotBlank() && File(m.localPath).exists()) {
                    File(m.localPath)
                } else {
                    m.remoteUrl
                }
                val isVideo = m.kind.equals("VIDEO", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .width(132.dp)
                        .aspectRatio(mediaAspectRatio(m.width, m.height))
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(if (isVideo) Modifier else Modifier.clickable { onOpenAt(m) })
                ) {
                    if (isVideo) {
                        InlineVideoPlayer(
                            remoteUrl = m.remoteUrl,
                            localPath = m.localPath,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = source,
                            contentDescription = "貼文圖片",
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { onOpenAt(m) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

/**
 * 留言圖片同樣橫滑簡易顯示（FetchedMedia 版，複用同一視覺語言）。
 */
@Composable
private fun CommentMediaGalleryRow(
    media: List<FetchedMedia>,
    onOpenAt: (Int) -> Unit
) {
    if (media.isEmpty()) return
    if (media.size == 1) {
        val cm = media[0]
        val cmIsVideo = cm.kind.equals("VIDEO", ignoreCase = true)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(mediaAspectRatio(cm.width, cm.height, 1.15f))
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (cmIsVideo) {
                InlineVideoPlayer(
                    remoteUrl = cm.remoteUrl,
                    localPath = cm.localPath,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val cmSource: Any = if (cm.localPath.isNotBlank() && File(cm.localPath).exists()) {
                    File(cm.localPath)
                } else {
                    cm.remoteUrl
                }
                AsyncImage(
                    model = cmSource,
                    contentDescription = "留言圖片",
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onOpenAt(0) },
                    contentScale = ContentScale.Crop
                )
            }
        }
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${media.size} 張・左右滑動查看",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(media, key = { i, m -> m.remoteUrl + m.localPath + i }) { ci, cm ->
                val cmIsVideo = cm.kind.equals("VIDEO", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(mediaAspectRatio(cm.width, cm.height))
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (cmIsVideo) {
                        InlineVideoPlayer(
                            remoteUrl = cm.remoteUrl,
                            localPath = cm.localPath,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val cmSource: Any = if (cm.localPath.isNotBlank() && File(cm.localPath).exists()) {
                            File(cm.localPath)
                        } else {
                            cm.remoteUrl
                        }
                        AsyncImage(
                            model = cmSource,
                            contentDescription = "留言圖片",
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { onOpenAt(ci) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun DetailDialog(
    item: ItemDetail,
    categories: List<CategoryEntity>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRequestCreateCategory: (prefill: String) -> Unit = {}
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
    var currentSummary by remember(item.item.id) { mutableStateOf(item.manualSummary) }
    // Flow 更新（AI 完成後）即時同步摘要顯示；備註草稿只在初次/外部變更且無未存修改時同步，避免蓋掉輸入
    LaunchedEffect(item.manualSummary) { currentSummary = item.manualSummary }
    var noteDraft by remember(item.item.id) { mutableStateOf(item.manualNote) }
    var lastSyncedNote by remember(item.item.id) { mutableStateOf(item.manualNote) }
    // 外部 Flow 更新時：只有本地無未存修改（draft == 上次同步值）才跟進，否則保留使用者原始輸入
    LaunchedEffect(item.manualNote) {
        if (noteDraft == lastSyncedNote) {
            noteDraft = item.manualNote
        }
        lastSyncedNote = item.manualNote
    }
    var isSavingNote by remember { mutableStateOf(false) }
    var showAiConsent by remember { mutableStateOf(false) }
    val aiConsent by viewModel.aiTransmissionConsent.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    // 頂/底超滑下拉關閉（對齊 MediaViewerDialog 手感）：滑到頂再往下拉、或滑到底再往上推，
    // 超過一段距離出現拉鋸指示（箭頭膠囊），放開超過閾值即關閉，否則彈回。
    val detailScrollState = rememberScrollState()
    val category = categories.firstOrNull { it.id == item.userEdit?.categoryId }
    // 全螢幕媒體檢視器下標（null = 關閉；索引對應「祖先在前、主文在後」的扁平有序，與存檔合併順序一致）
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    // 左上 ... 溢位選單 + 改分類編輯器
    var showOverflow by remember { mutableStateOf(false) }
    var showCategoryEditor by remember { mutableStateOf(false) }
    // 檢視器数据源：按 position 排序並正規化去重，避免已存雙份在全螢幕左右滑出現重複
    val viewerSortedMedia = remember(item.media) {
        com.reater.app.data.remote.MediaDedup.distinctEntities(item.media.sortedBy { it.position })
    }

    if (viewerIndex != null) {
        MediaViewerDialog(
            media = viewerSortedMedia.map { ViewerMedia(kind = it.kind, remoteUrl = it.remoteUrl, localPath = it.localPath) },
            startIndex = (viewerIndex ?: 0).coerceIn(0, maxOf(0, viewerSortedMedia.size - 1)),
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
                // AI 只更新摘要顯示；備註草稿與已存備註一律保留，不清空使用者原始輸入
                // （persistAiAnalysis 在 DB 層同樣只寫 manualSummary、保留 manualNote）
                currentSummary = it
                Toast.makeText(context, "AI 分析完成！（備註已保留）", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, err.message ?: "AI 分析失敗", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun saveNote() {
        if (isSavingNote) return
        val trimmed = noteDraft.trim()
        // 未變更則不寫庫，避免洗掉 editedAt；清空也是合法操作（使用者主動清才清）
        if (trimmed == item.manualNote.trim() && noteDraft == lastSyncedNote) {
            Toast.makeText(context, "備註無變更", Toast.LENGTH_SHORT).show()
            return
        }
        isSavingNote = true
        viewModel.updateManualNote(item.item.id, noteDraft)
        lastSyncedNote = noteDraft
        isSavingNote = false
        Toast.makeText(context, "備註已儲存", Toast.LENGTH_SHORT).show()
    }

    if (showAiConsent) {
        com.reater.app.ui.theme.AppDialog(onDismissRequest = { showAiConsent = false }) {
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

    com.reater.app.ui.theme.AppDialog(onDismissRequest = onDismiss) {
        // 超滑關閉閾值（與 MediaViewerDialog 同級手感：120~160dp）
        val density = androidx.compose.ui.platform.LocalDensity.current
        val dismissThresholdPx = remember(density) { with(density) { 140.dp.toPx() } }
        val hintThresholdPx = remember(density) { with(density) { 36.dp.toPx() } }
        // 橡皮筋位移：拖曳中即時跟手（snapTo），放開後彈簧回位；避免 draggable 搶奪中段捲動，
        // 改用 nestedScroll 只吃「內容已到盡頭剩下的」位移，中段滑動完全不受影響。
        val overscrollAnim = remember {
            androidx.compose.animation.core.Animatable(0f)
        }
        val overscrollConnection = remember(detailScrollState, dismissThresholdPx) {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(
                    available: androidx.compose.ui.geometry.Offset,
                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
                ): androidx.compose.ui.geometry.Offset {
                    // 僅在手指實體拖曳且已有橡皮筋位移時，反向滑動優先收回橡皮筋，避免內文跳動
                    if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                        val cur = overscrollAnim.value
                        if (cur > 0f && available.y < 0f) {
                            val consumedY = available.y.coerceAtLeast(-cur)
                            coroutineScope.launch { overscrollAnim.snapTo(cur + consumedY) }
                            return androidx.compose.ui.geometry.Offset(0f, consumedY)
                        } else if (cur < 0f && available.y > 0f) {
                            val consumedY = available.y.coerceAtMost(-cur)
                            coroutineScope.launch { overscrollAnim.snapTo(cur + consumedY) }
                            return androidx.compose.ui.geometry.Offset(0f, consumedY)
                        }
                    }
                    return androidx.compose.ui.geometry.Offset.Zero
                }

                override fun onPostScroll(
                    consumed: androidx.compose.ui.geometry.Offset,
                    available: androidx.compose.ui.geometry.Offset,
                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
                ): androidx.compose.ui.geometry.Offset {
                    // 嚴格限制：僅「手指按著拖曳（UserInput）」才吃超滑位移；慣性捲動（Fling / SideEffect）到頂/到底絕不吃、不觸發關閉
                    if (source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput || available.y == 0f) {
                        return androidx.compose.ui.geometry.Offset.Zero
                    }
                    val atTop = available.y > 0f && !detailScrollState.canScrollBackward
                    val atBottom = available.y < 0f && !detailScrollState.canScrollForward
                    if (atTop || atBottom) {
                        val cur = overscrollAnim.value
                        val resistance =
                            1f - (kotlin.math.abs(cur) / 600f).coerceIn(0f, 0.75f)
                        val next =
                            (cur + available.y * 0.45f * resistance).coerceIn(-420f, 420f)
                        coroutineScope.launch { overscrollAnim.snapTo(next) }
                        return androidx.compose.ui.geometry.Offset(0f, available.y)
                    }
                    return androidx.compose.ui.geometry.Offset.Zero
                }

                override suspend fun onPostFling(
                    consumed: androidx.compose.ui.unit.Velocity,
                    available: androidx.compose.ui.unit.Velocity
                ): androidx.compose.ui.unit.Velocity {
                    // 慣性甩動絕不直接關閉；放開手指時若位移已超過閾值才關閉，否則一律彈簧回位
                    val cur = overscrollAnim.value
                    if (kotlin.math.abs(cur) > dismissThresholdPx) {
                        onDismiss()
                    } else if (cur != 0f) {
                        overscrollAnim.animateTo(
                            0f,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            )
                        )
                    }
                    return androidx.compose.ui.unit.Velocity.Zero
                }
            }
        }
        // 放開手指（捲動結束）結算：超過閾值關閉，否則彈簧回位保底
        LaunchedEffect(detailScrollState.isScrollInProgress) {
            if (!detailScrollState.isScrollInProgress && overscrollAnim.value != 0f) {
                if (kotlin.math.abs(overscrollAnim.value) > dismissThresholdPx) {
                    onDismiss()
                } else {
                    overscrollAnim.animateTo(
                        0f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                        )
                    )
                }
            }
        }
        val animatedY = overscrollAnim.value
        val overscrollAlpha = (1f - (kotlin.math.abs(animatedY) / 1200f).coerceIn(0f, 0.6f))
        val showTopHint = animatedY > hintThresholdPx
        val showBottomHint = animatedY < -hintThresholdPx
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .graphicsLayer {
                    translationY = animatedY
                    alpha = overscrollAlpha
                }
                // 頂/底超滑拉鋸：只在內容已滑到盡頭時吃掉手勢，中段滑動不受影響
                .nestedScroll(overscrollConnection),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 固定頂欄：左上 ... 溢位選單 + 分類徽章（中間可直接點開改分類）+ 分享 + X 關閉（最右）
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
                                            fontSize = 12.sp,
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
                        modifier = Modifier.weight(1f, fill = false),
                        onClick = { showCategoryEditor = !showCategoryEditor }
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
                        .verticalScroll(detailScrollState)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                // 改分類編輯器（點中間徽章或 ... 選單展開）：與儲存時同款下拉，篩選 + 即時生效
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
                        onRequestCreate = { query ->
                            showCategoryEditor = false
                            onRequestCreateCategory(query)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

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
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // 拆解螺紋鏈：母文 →(中間分享/回覆層)…→ 主文；留言的留言會展開成 N 個區塊
                val threadChain = remember(item.displayBody, item.item.authorHandle) {
                    parseThreadChain(item.displayBody, item.item.authorHandle)
                }
                val mainBlock = threadChain.last()
                val ancestorBlocks = threadChain.dropLast(1)

                // 各區塊媒體歸屬（圖3/圖4 修正：母文圖歸母文，子文圖歸子文；舊資料自動歸位到母文）
                val blockMediaLists = remember(item.media, item.item.rawJsonMin, threadChain) {
                    resolveThreadBlockMedia(item, threadChain)
                }
                val mainMediaList = blockMediaLists.lastOrNull().orEmpty()
                // 全螢幕檢視下標基準統一用 viewerSortedMedia（頂層 Dialog 同源），此處扁平去重供判空
                val viewerFlatMedia = remember(blockMediaLists) {
                    com.reater.app.data.remote.MediaDedup.distinctEntities(blockMediaLists.flatten())
                }
                val hasAnyBlockMedia = viewerFlatMedia.isNotEmpty()
                val legacyMoved = remember(item.item.rawJsonMin, threadChain, item.media) {
                    isLegacyMergedMedia(item, threadChain)
                }

                val hasComments = item.comments.isNotEmpty()

                // 舊資料歸位提示（僅留言鏈 + 有媒體 + 無映射時顯示一次）
                if (legacyMoved) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "此為留言鏈舊存檔，圖片已自動歸位到母文（原誤掛在留言下）。重新儲存可永久修正。",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 1. 祖先區塊（母文 + 中間分享/回覆層）：每塊皆向下連線到下一塊
                ancestorBlocks.forEachIndexed { ai, ancestor ->
                    val aMedia = blockMediaLists.getOrNull(ai).orEmpty()
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // 左側軌道：頭像 + 向下垂直螺紋線連到下一區塊
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(42.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(getAvatarColor(ancestor.author)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ancestor.author.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            // 垂直貫穿連接線（祖先塊下方必有下一塊，一律繪製）
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(if (ancestor.body.length > 50) 60.dp else 40.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // 右側內容：帳號 + 內文 + 互動列
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "@${ancestor.author}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = ancestor.label ?: "分享",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            // 母文/分享內文：短訊息純顯示，文字區塊才套灰框＋一鍵複製
                            if (ancestor.body.isBlank()) {
                                Text(
                                    text = "（無內文）",
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                SelectiveTextBlock(
                                    text = ancestor.body,
                                    label = ancestor.label ?: "分享",
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // 祖先區塊媒體（母文圖歸母文：銀晝戰績/裝備/排行在此顯示，不再掛到子文下）
                            // 圖2：多圖改左右橫滑簡易顯示，省垂直空間
                            if (aMedia.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                MediaGalleryRow(media = aMedia) { am ->
                                    val aKey = com.reater.app.data.remote.MediaDedup.normalizeKey(am.remoteUrl)
                                    val globalIdx = viewerSortedMedia.indexOfFirst {
                                        com.reater.app.data.remote.MediaDedup.normalizeKey(it.remoteUrl) == aKey
                                    }
                                    if (globalIdx >= 0) viewerIndex = globalIdx
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // 互動圖示列（Threads 原生四件套，共用模組）
                            Spacer(modifier = Modifier.height(6.dp))
                            ThreadsStatsRow(
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 2. 主文 / 分享貼文區塊
                Row(modifier = Modifier.fillMaxWidth()) {
                    // 左側軌道：頭像 + 向下螺紋線（若有留言則延伸連到留言，無留言則到底）
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(42.dp)
                    ) {
                        ThreadAvatar(
                            name = mainBlock.author,
                            profileUrl = item.item.authorProfileUrl
                                .takeIf { mainBlock.author.equals(item.item.authorHandle, ignoreCase = true) }
                                .orEmpty()
                        )
                        if (hasComments) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(if (mainMediaList.isNotEmpty()) 120.dp else 40.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // 右側內容：帳號 + 認證 + 時間 + 內文 + 媒體 + 數據列
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "@${mainBlock.author}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            // 單一母文塊（無分享分隔的舊資料）：保留「母文」標籤
                            if (mainBlock.label != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = mainBlock.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            if (item.item.authorVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF1DA1F2),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatSavedTime(item.item.sourceFetchedAt),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 內文渲染
                        val rawBody = mainBlock.body.trim()
                        val isUrlOnly = rawBody.startsWith("http", ignoreCase = true) &&
                            rawBody.lines().size == 1 && rawBody.length < 500 &&
                            (rawBody.contains("threads.com") || rawBody.contains("threads.net"))
                        if (isUrlOnly) {
                            Text(
                                text = "無內文",
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            LinkifiedText(
                                text = rawBody,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                softWrap = true,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // 主文內文：短訊息純顯示，文字區塊才套灰框＋一鍵複製
                            if (rawBody.isBlank()) {
                                Text(
                                    text = "無內文",
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                SelectiveTextBlock(
                                    text = rawBody,
                                    label = mainBlock.label,
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // 貼文媒體預覽（只顯示屬於本區塊的圖；母文圖已在上方母文塊顯示，不再重複）
                        // 圖2：多圖改左右橫滑簡易顯示，單張仍大圖
                        if (mainMediaList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            MediaGalleryRow(media = mainMediaList) { m ->
                                val mKey = com.reater.app.data.remote.MediaDedup.normalizeKey(m.remoteUrl)
                                val globalIdx = viewerSortedMedia.indexOfFirst {
                                    com.reater.app.data.remote.MediaDedup.normalizeKey(it.remoteUrl) == mKey
                                }
                                if (globalIdx >= 0) viewerIndex = globalIdx
                                else viewerIndex = item.media.indexOfFirst { it.remoteUrl == m.remoteUrl }
                                    .takeIf { it >= 0 }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        if (mainMediaList.isEmpty() && !hasAnyBlockMedia &&
                            item.item.lastFetchStatus == "PARTIAL") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "圖片/影片未能自動下載，可點上方按鈕在 Threads 查看原貼文。",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }

                        // 貼文互動數據列（Threads 原生四件套：愛心/留言/轉發/分享，共用模組；0 則只顯示圖示）
                        Spacer(modifier = Modifier.height(6.dp))
                        ThreadsStatsRow(
                            likeCount = item.item.likeCount,
                            replyCount = item.item.replyCount,
                            repostCount = item.item.repostCount,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                // （個人筆記改到底部可編輯區塊，此處不再重複顯示，避免與底部編輯器雙份）

                // 3. 精華留言區塊（以圖3 串文風格：左側頭像軌＋垂直連接線，右側帳號＋內容＋媒體＋讚數列）
                if (item.comments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "精華留言 (${item.comments.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    item.comments.forEachIndexed { index, c ->
                        val isLastComment = (index == item.comments.size - 1)
                        // 留言媒體顯示前先正規化去重，避免同圖多變體存成雙份
                        val commentMedia = remember(c.mediaJson) {
                            com.reater.app.data.remote.MediaDedup.distinctFetched(
                                FetchedMediaJson.decode(c.mediaJson)
                            )
                        }
                        // 已存舊資料可能混入顯示名/時間/數字列（DOM 未清洗版），顯示時再洗一次免重抓
                        val displayCommentText = remember(c.text, c.author) {
                            runCatching {
                                com.reater.app.data.remote.threads.ThreadsWebResolver
                                    .sanitizeDomCommentText(c.text, c.author).text
                                    .ifBlank { c.text.trim() }
                            }.getOrDefault(c.text.trim())
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            // 左側頭像 + 連接線
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(42.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(getAvatarColor(c.author)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = c.author.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                if (!isLastComment) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .weight(1f, fill = false)
                                            .height(if (commentMedia.isNotEmpty()) 140.dp else 46.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // 右側內容
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = if (isLastComment) 8.dp else 14.dp)
                            ) {
                                Text(
                                    text = "@${c.author}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                // 留言內文：一般短訊息純顯示，只有文字區塊才套灰框＋一鍵複製
                                SelectiveTextBlock(
                                    text = displayCommentText,
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp,
                                    collapsedMaxLines = 4,
                                    collapseThresholdChars = 140,
                                    collapseThresholdLines = 5,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // 留言媒體（圖2：多圖橫滑簡易顯示）
                                if (commentMedia.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    CommentMediaGalleryRow(media = commentMedia) { ci ->
                                        commentViewerMedia = commentMedia.map { m ->
                                            ViewerMedia(kind = m.kind, remoteUrl = m.remoteUrl, localPath = m.localPath)
                                        }
                                        commentViewerIndex = ci
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                // 留言互動數值列（Threads 原生四件套，共用模組；小一號圖示）
                                Spacer(modifier = Modifier.height(4.dp))
                                ThreadsStatsRow(
                                    likeCount = c.likeCount,
                                    iconSize = 15.dp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // 備註（可編輯，位於 AI 摘要與底部操作列上方）：AI 摘要只寫摘要欄，絕不清空此處使用者輸入
                Spacer(modifier = Modifier.height(16.dp))
                val noteUnsaved = noteDraft != lastSyncedNote
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "備註", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (noteUnsaved) {
                            Text(
                                text = "● 未儲存",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (item.manualNote.isNotBlank()) {
                            Text(
                                text = "已儲存",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        placeholder = { Text("寫下你的備註…（AI 摘要不會覆蓋這裡）", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 2,
                        maxLines = 6
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (noteDraft.isNotBlank() && noteDraft != item.manualNote) {
                            OutlinedButton(
                                onClick = {
                                    noteDraft = item.manualNote
                                    lastSyncedNote = item.manualNote
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text("還原", fontSize = 12.sp) }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Button(
                            onClick = { saveNote() },
                            enabled = !isSavingNote && noteUnsaved,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            if (isSavingNote) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("儲存備註", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // AI 摘要（精華留言下方、底部操作列上方）
                if (currentSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
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
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
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
            // 頂/底超滑拉鋸指示（對齊圖像/影片檢視器：多滑一段距離出現，放開超過閾值關閉）
            if (showTopHint || showBottomHint) {
                val pullingDown = animatedY > 0f
                val progress = (kotlin.math.abs(animatedY) / dismissThresholdPx).coerceIn(0f, 1f)
                val readyToDismiss = kotlin.math.abs(animatedY) > dismissThresholdPx
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (readyToDismiss) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .align(if (pullingDown) Alignment.TopCenter else Alignment.BottomCenter)
                        .padding(vertical = 10.dp)
                        .alpha(0.45f + 0.55f * progress)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (pullingDown) Icons.Default.KeyboardDoubleArrowDown
                            else Icons.Default.KeyboardDoubleArrowUp,
                            contentDescription = null,
                            tint = if (readyToDismiss) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (readyToDismiss) "放開即關閉" else "繼續滑動可關閉",
                            fontSize = 12.sp,
                            fontWeight = if (readyToDismiss) FontWeight.Bold else FontWeight.Medium,
                            color = if (readyToDismiss) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            }
        }
    }
}
