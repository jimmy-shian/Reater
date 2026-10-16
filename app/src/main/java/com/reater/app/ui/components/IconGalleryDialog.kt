package com.reater.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reater.app.ui.AvatarIcons
import com.reater.app.ui.AvatarStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 圖示與自訂頭像共用展示廳；原圖、成品與編輯資料沿用 AvatarStorage 配對儲存。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IconGalleryDialog(
    isPro: Boolean,
    currentAvatarId: String,
    onSelectAvatar: (String) -> Unit,
    onOpenUnlock: () -> Unit,
    onDismiss: () -> Unit,
    currentAvatarUri: String? = null,
    onSelectCustomAvatar: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { 2 }
    var filter by rememberSaveable { mutableStateOf("全部") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var history by remember { mutableStateOf(AvatarStorage.listHistoryPaths(context)) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var editOriginal by rememberSaveable { mutableStateOf<String?>(null) }
    var editTs by rememberSaveable { mutableStateOf(0L) }
    var editFromPath by rememberSaveable { mutableStateOf<String?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var inspectingId by rememberSaveable { mutableStateOf<String?>(null) }
    val filters = listOf("全部", "基礎免費", "PRO") + AvatarIcons.ALL.map { it.category }.distinct()

    fun beginEdit(path: String) {
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                val original = AvatarStorage.pairedOriginalFile(context, path) ?: File(path)
                val ts = System.currentTimeMillis()
                AvatarStorage.duplicateOriginalForReEdit(context, original, ts)?.let { it.absolutePath to ts }
            }
            busy = false
            if (result != null) {
                editFromPath = path
                editTs = result.second
                editOriginal = result.first
            } else Toast.makeText(context, "無法讀取頭像原圖", Toast.LENGTH_SHORT).show()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && isPro) scope.launch {
            busy = true
            val ts = System.currentTimeMillis()
            val original = withContext(Dispatchers.IO) { AvatarStorage.saveOriginal(context, uri, ts) }
            busy = false
            if (original != null) {
                editFromPath = null
                editTs = ts
                editOriginal = original
            } else Toast.makeText(context, "圖片匯入失敗，請重新選擇", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(pager.currentPage) { expanded = false }
    LaunchedEffect(editOriginal) {
        bitmap = null
        editOriginal?.let { path ->
            bitmap = withContext(Dispatchers.IO) { AvatarStorage.decodeFile(path) }
            if (bitmap == null) {
                editOriginal = null
                Toast.makeText(context, "無法讀取圖片，請重新選擇", Toast.LENGTH_SHORT).show()
            }
        }
    }
    bitmap?.let { source ->
        val transform = remember(editFromPath) { AvatarStorage.loadCropTransform(context, editFromPath) }
        AvatarCropDialog(
            source = source,
            initialScale = transform?.scale ?: 1f,
            initialNormOffsetX = transform?.normOffsetX ?: 0f,
            initialNormOffsetY = transform?.normOffsetY ?: 0f,
            initialRotation = transform?.rotation ?: 0f,
            onConfirm = { cropped, scale, x, y, rotation ->
                if (!busy && isPro) scope.launch {
                    busy = true
                    val saved = withContext(Dispatchers.IO) {
                        AvatarStorage.saveCroppedWithTs(context, cropped, editTs)?.also {
                            AvatarStorage.saveCropTransform(context, editTs, scale, x, y, rotation)
                        }
                    }
                    busy = false
                    if (saved != null) {
                        onSelectCustomAvatar(saved)
                        history = AvatarStorage.listHistoryPaths(context)
                        editOriginal = null
                    } else Toast.makeText(context, "頭像儲存失敗，請重試", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { if (!busy) editOriginal = null }
        )
    }
    pendingDelete?.let { path ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("刪除歷史頭像？") },
            text = { Text("將刪除此頭像的原圖與編輯紀錄。") },
            confirmButton = { TextButton(onClick = {
                if (AvatarStorage.canDeleteHistory(path, currentAvatarUri)) {
                    AvatarStorage.deleteHistory(context, path, currentAvatarUri)
                    history = AvatarStorage.listHistoryPaths(context)
                }
                pendingDelete = null
            }) { Text("刪除") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
    inspectingId?.let { id ->
        val item = AvatarIcons.getIconItem(id)
        AlertDialog(
            onDismissRequest = { inspectingId = null },
            icon = { Icon(painterResource(item.resId), item.name, Modifier.size(80.dp), tint = androidx.compose.ui.graphics.Color.Unspecified) },
            title = { Text(item.name) },
            text = { Text(item.desc) },
            confirmButton = { TextButton(onClick = {
                inspectingId = null
                if (item.isPro && !isPro) onOpenUnlock() else onSelectAvatar(item.id)
            }) { Text(if (item.isPro && !isPro) "前往 PRO" else "套用為個人頭像") } },
            dismissButton = { TextButton(onClick = { inspectingId = null }) { Text("關閉") } }
        )
    }
    Dialog(onDismissRequest = { if (expanded) expanded = false else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(enabled = expanded) { expanded = false }
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.90f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { expanded = false }) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("圖示與自訂頭像", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "關閉") }
                }
                TabRow(selectedTabIndex = pager.currentPage) {
                    listOf("圖示", "自訂頭像").forEachIndexed { index, title ->
                        Tab(selected = pager.currentPage == index, onClick = {
                            expanded = false
                            scope.launch { pager.animateScrollToPage(index) }
                        }, text = { Text(title) })
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                    if (page == 0) Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text("類型：$filter")
                            val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(275), label = "avatarFilterArrow")
                            Icon(Icons.Default.KeyboardArrowDown, if (expanded) "收合類型" else "展開類型", Modifier.graphicsLayer { rotationZ = arrowRotation })
                        }
                        AnimatedVisibility(expanded, enter = fadeIn(tween(275)) + expandVertically(tween(275)),
                            exit = fadeOut(tween(275)) + shrinkVertically(tween(275))) {
                            Surface(tonalElevation = 4.dp, shape = MaterialTheme.shapes.medium) {
                                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                                    items(filters) { option -> TextButton(onClick = { filter = option; expanded = false }, modifier = Modifier.fillMaxWidth()) { Text(option) } }
                                }
                            }
                        }
                        val icons = AvatarIcons.ALL.filter { item -> when (filter) {
                            "全部" -> true
                            "基礎免費" -> !item.isPro
                            "PRO" -> item.isPro
                            else -> item.category == filter
                        } }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                            icons.groupBy { it.category }.forEach { (category, entries) ->
                                item(key = "section_$category") { Text(category, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp)) }
                                items(entries, key = { it.id }) { item ->
                                    ListItem(
                                        headlineContent = { Text(item.name) },
                                        supportingContent = { Text(item.desc) },
                                        leadingContent = { Icon(painterResource(item.resId), item.name, Modifier.size(40.dp), tint = androidx.compose.ui.graphics.Color.Unspecified) },
                                        trailingContent = { Text(if (item.isPro && !isPro) "PRO" else if (currentAvatarUri == null && item.id == currentAvatarId) "使用中" else "") },
                                        modifier = Modifier.clickable { inspectingId = item.id }
                                    )
                                }
                            }
                        }
                    } else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                        if (!isPro) item {
                            Text("自訂個人頭像", style = MaterialTheme.typography.titleLarge)
                            Text("使用自己的圖片作為 Reater 個人頭像，並保存縮放位置、裁切範圍與歷史頭像。此功能需要 PRO。", modifier = Modifier.padding(vertical = 16.dp))
                            Button(onClick = onOpenUnlock) { Text("前往 PRO") }
                        } else {
                            item { Button(onClick = { picker.launch("image/*") }, enabled = !busy) { Text("從相簿選擇") } }
                            if (currentAvatarUri != null) item {
                                OutlinedButton(onClick = { beginEdit(currentAvatarUri) }, enabled = !busy) { Text("編輯目前頭像") }
                            }
                            item { Text("歷史頭像", style = MaterialTheme.typography.titleMedium) }
                            if (history.isEmpty()) item { Text("尚無歷史頭像，從相簿選擇第一張圖片。") }
                            items(history, key = { it }) { path ->
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        UserAvatarView(avatarId = currentAvatarId, avatarUri = path, modifier = Modifier.size(56.dp))
                                        Text(if (path == currentAvatarUri) "目前使用中" else "歷史頭像", modifier = Modifier.padding(start = 12.dp))
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { onSelectCustomAvatar(path) }, enabled = !busy) { Text("重新套用") }
                                        TextButton(onClick = { beginEdit(path) }, enabled = !busy) { Text("編輯") }
                                        TextButton(onClick = { pendingDelete = path }, enabled = AvatarStorage.canDeleteHistory(path, currentAvatarUri) && !busy) { Text("刪除") }
                                    }
                                }
                            }
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * 使用者頭貼顯示元件：若有自訂照片則載入照片，否則顯示選取的向量 Icon
 */
@Composable
fun UserAvatarView(
    avatarId: String,
    avatarUri: String?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    showBorder: Boolean = true
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFFFB300).copy(alpha = 0.15f))
            .then(
                if (showBorder) Modifier.border(1.5.dp, Color(0xFFFFB300), CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUri.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = com.reater.app.ui.AvatarStorage.resolveModel(androidx.compose.ui.platform.LocalContext.current, avatarUri),
                contentDescription = "個人自訂頭貼",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            val resId = AvatarIcons.getDrawableRes(avatarId)
            Icon(
                painter = painterResource(id = resId),
                contentDescription = "個人頭貼",
                modifier = Modifier.size(size * 0.65f),
                tint = Color.Unspecified
            )
        }
    }
}
