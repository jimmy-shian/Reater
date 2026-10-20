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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reater.app.ui.AvatarIcons
import com.reater.app.ui.AvatarStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class PendingAvatarCrop(val bitmap: Bitmap, val scale: Float, val x: Float, val y: Float, val rotation: Float)

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
    var pendingCrop by remember { mutableStateOf<PendingAvatarCrop?>(null) }
    var inspectingId by rememberSaveable { mutableStateOf<String?>(null) }
    val filters = listOf("全部", "基礎免費", "PRO")

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
    fun saveAvatar(crop: PendingAvatarCrop, overwrite: Boolean) {
        if (busy || !isPro) return
        scope.launch {
            busy = true
            val saved = withContext(Dispatchers.IO) {
                AvatarStorage.saveCroppedWithTs(context, crop.bitmap, editTs)?.also {
                    AvatarStorage.saveCropTransform(context, editTs, crop.scale, crop.x, crop.y, crop.rotation)
                    // The original was copied when editing began. Commit the replacement before removing its predecessor.
                    if (overwrite) editFromPath?.let { previous -> AvatarStorage.deletePath(previous) }
                }
            }
            busy = false
            if (saved != null) {
                onSelectCustomAvatar(saved)
                history = AvatarStorage.listHistoryPaths(context)
                pendingCrop = null
                editOriginal = null
            } else Toast.makeText(context, "頭像儲存失敗，請重試", Toast.LENGTH_SHORT).show()
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
                val crop = PendingAvatarCrop(cropped, scale, x, y, rotation)
                if (editFromPath != null) pendingCrop = crop else saveAvatar(crop, false)
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
                    Text("圖示展覽室", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "關閉") }
                }
                TabRow(selectedTabIndex = pager.currentPage) {
                    listOf("圖示", "自訂頭像").forEachIndexed { index, title ->
                        Tab(selected = pager.currentPage == index, onClick = {
                            expanded = false
                            scope.launch { pager.animateScrollToPage(index) }
                        }, text = { Text(title, style = MaterialTheme.typography.titleSmall) })
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                    if (page == 0) Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text("類型：$filter", style = MaterialTheme.typography.labelLarge)
                            val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(275), label = "avatarFilterArrow")
                            Icon(Icons.Default.KeyboardArrowDown, if (expanded) "收合類型" else "展開類型", Modifier.graphicsLayer { rotationZ = arrowRotation })
                        }
                        AnimatedVisibility(expanded, enter = fadeIn(tween(275)) + expandVertically(tween(275)),
                            exit = fadeOut(tween(275)) + shrinkVertically(tween(275))) {
                            Surface(tonalElevation = 4.dp, shape = MaterialTheme.shapes.medium) {
                                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                                    items(filters) { option -> ReaterDropdownOption(option, filter == option, { filter = option; expanded = false }) }
                                }
                            }
                        }
                        val icons = AvatarIcons.ALL.filter { item -> when (filter) {
                            "全部" -> true
                            "基礎免費" -> !item.isPro
                            "PRO" -> item.isPro
                            else -> item.category == filter
                        } }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            icons.groupBy { it.category }.forEach { (category, entries) ->
                                item(key = "section_$category") {
                                    Text(
                                        category,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                    )
                                }
                                items(entries, key = { it.id }) { item ->
                                    val isSelected = (currentAvatarUri == null && item.id == currentAvatarId)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { inspectingId = item.id },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        ),
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 大型 App 圖示預覽 (68dp)，依原始 Shape 保留 Rounded Square
                                            Box(
                                                modifier = Modifier
                                                    .size(68.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .border(
                                                        1.5.dp,
                                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                        RoundedCornerShape(16.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(item.resId),
                                                    contentDescription = item.name,
                                                    modifier = Modifier.size(46.dp),
                                                    tint = Color.Unspecified
                                                )
                                            }

                                            Spacer(Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = item.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (item.isPro) {
                                                        Box(
                                                            modifier = Modifier
                                                                .background(Color(0xFFFFB300), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(
                                                                "PRO",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.Black
                                                            )
                                                        }
                                                    }
                                                    if (isSelected) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = MaterialTheme.colorScheme.primary
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Check,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                                Spacer(Modifier.width(2.dp))
                                                                Text(
                                                                    "使用中",
                                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    ),
                                                                    color = MaterialTheme.colorScheme.onPrimary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                Spacer(Modifier.height(3.dp))

                                                Text(
                                                    text = item.desc,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    maxLines = 2,
                                                    lineHeight = 16.sp
                                                )

                                                Spacer(Modifier.height(8.dp))

                                                Row(
                                                    horizontalArrangement = Arrangement.End,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    if (!isSelected) {
                                                        if (item.isPro && !isPro) {
                                                            OutlinedButton(
                                                                onClick = onOpenUnlock,
                                                                modifier = Modifier.height(34.dp),
                                                                shape = RoundedCornerShape(8.dp),
                                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                                            ) {
                                                                Text("解鎖 PRO", style = MaterialTheme.typography.labelMedium)
                                                            }
                                                        } else {
                                                            Button(
                                                                onClick = { onSelectAvatar(item.id) },
                                                                modifier = Modifier.height(34.dp),
                                                                shape = RoundedCornerShape(8.dp),
                                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
                                                            ) {
                                                                Text("設為使用", style = MaterialTheme.typography.labelMedium)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        if (!isPro) item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("自訂個人頭像", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "使用自己的圖片作為 Reater 個人頭像，並保存縮放位置、裁切範圍與歷史頭像。此功能需要 PRO。",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    Button(onClick = onOpenUnlock, shape = RoundedCornerShape(8.dp)) {
                                        Text("前往 PRO")
                                    }
                                }
                            }
                        } else {
                            item {
                                OutlinedButton(
                                    onClick = { picker.launch("image/*") },
                                    enabled = !busy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("從相簿選擇", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            item {
                                Text(
                                    "歷史頭像",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            if (history.isEmpty()) item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        "尚無歷史頭像，從相簿選擇第一張圖片。",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            items(history, key = { it }) { path ->
                                val isSelected = (path == currentAvatarUri)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 大型圓形頭像預覽 (68dp)
                                        UserAvatarView(
                                            avatarId = currentAvatarId,
                                            avatarUri = path,
                                            size = 68.dp
                                        )

                                        Spacer(Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = if (isSelected) "目前頭像" else "歷史頭像",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (isSelected) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.primary
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Spacer(Modifier.width(2.dp))
                                                            Text(
                                                                "使用中",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                ),
                                                                color = MaterialTheme.colorScheme.onPrimary
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(3.dp))

                                            Text(
                                                text = if (isSelected) "目前使用的自訂頭像" else "已儲存的自訂裁切頭像",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )

                                            Spacer(Modifier.height(8.dp))

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (!isSelected) {
                                                    Button(
                                                        onClick = { onSelectCustomAvatar(path) },
                                                        enabled = !busy,
                                                        modifier = Modifier.height(34.dp),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("設為使用", style = MaterialTheme.typography.labelMedium)
                                                    }
                                                }

                                                OutlinedButton(
                                                    onClick = { beginEdit(path) },
                                                    enabled = !busy,
                                                    modifier = Modifier.height(34.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("編輯", style = MaterialTheme.typography.labelMedium)
                                                }

                                                if (!isSelected && AvatarStorage.canDeleteHistory(path, currentAvatarUri)) {
                                                    IconButton(
                                                        onClick = { pendingDelete = path },
                                                        enabled = !busy,
                                                        modifier = Modifier.size(34.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = "刪除頭像",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
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
    pendingCrop?.let { crop ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingCrop = null },
            title = { Text("儲存編輯後的頭像") },
            text = { Text("要保留原頭像另存一份，還是覆蓋更新這張歷史頭像？") },
            confirmButton = { TextButton(onClick = { saveAvatar(crop, true) }, enabled = !busy) { Text("覆蓋更新") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingCrop = null }, enabled = !busy) { Text("取消") }
                    TextButton(onClick = { saveAvatar(crop, false) }, enabled = !busy) { Text("另存新的") }
                }
            }
        )
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
