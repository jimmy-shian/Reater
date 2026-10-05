package com.reater.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reater.app.ui.AvatarIconItem
import com.reater.app.ui.AvatarIcons
import com.reater.app.ui.AvatarStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 圖示總覽展示廳 (Icon Gallery)
 * 展示 5 款基礎免費圖示與 10 款 Pro 專屬圖示（共 15 款）。
 * 支援：
 * 1. 點擊大圖查看（高解析向量細節檢視與設計理念說明）。
 * 2. 未解鎖 Pro 款採用灰色鎖定遮罩檔住，清晰標示並提供解鎖路徑。
 * 3. 固定頂欄與始終可見的「X」關閉按鈕，版面不跑版。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconGalleryDialog(
    isPro: Boolean,
    currentAvatarId: String,
    currentAvatarUri: String? = null,
    currentAvatarOriginal: String? = null,
    onSelectAvatar: (String) -> Unit,
    onOpenUnlock: () -> Unit,
    onDismiss: () -> Unit,
    onImportPhoto: (android.net.Uri) -> Unit = {},
    onClearPhoto: () -> Unit = {},
    avatarHistory: List<String> = emptyList(),
    onSelectHistory: (String) -> Unit = {},
    onDeleteHistory: (String) -> Unit = {},
    onSaveCropped: (android.graphics.Bitmap, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    onSaveCroppedWithSource: (android.graphics.Bitmap, android.net.Uri, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    onSaveReEdit: (android.graphics.Bitmap, String?, Float, Float, Float) -> Unit = { _, _, _, _, _ -> }
) {
    // 標籤僅保留 2 個：0: 全部（含下拉篩選 全部/免費/PRO），1: 自訂相片 (PRO)
    // 「全部」頁內的範圍篩選改用下拉選單（共用 ReaterDropdown 動畫），不再把免費/PRO 獨立成頁籤
    val customActiveKey = !currentAvatarUri.isNullOrBlank()
    var selectedTab by remember(customActiveKey, isPro) {
        mutableStateOf(if (customActiveKey) 1 else 0)
    }
    // 全部頁內的下拉篩選：0=全部(15)，1=基礎免費(5)，2=PRO 專屬(10)
    var allScope by remember { mutableStateOf(0) }
    var scopeExpanded by remember { mutableStateOf(false) }
    var inspectingItem by remember { mutableStateOf<AvatarIconItem?>(null) } // 大圖查看選中項目
    // 自訂照片優先：有 Uri 時 15 款圖示一律取消勾選，避免「照片已套用卻還顯示某圖示套用中」
    val hasCustomPhoto = !currentAvatarUri.isNullOrBlank()
    fun isEffectivelySelected(id: String): Boolean = !hasCustomPhoto && currentAvatarId == id

    // 取消勾選回退記憶（開窗當下快照＋每次套用前更新）：再點實心勾時回到相片或上一個圖示，不關頁籤
    val entryAvatarId = remember { currentAvatarId }
    val entryPhotoPath = remember { currentAvatarUri }
    var previousIconId by remember { mutableStateOf(entryAvatarId) }
    var lastPhotoPath by remember { mutableStateOf(entryPhotoPath) }
    fun rememberBeforeApply() {
        previousIconId = currentAvatarId
        lastPhotoPath = currentAvatarUri
    }
    // 再點實心勾＝取消勾選：有相片記憶（且仍在歷史裡）就回到相片，否則回到上一個圖示；全程不關頁籤
    fun deselectToPrevious() {
        val photo = lastPhotoPath?.takeIf { p -> avatarHistory.any { it == p } }
        if (photo != null) onSelectHistory(photo)
        else onSelectAvatar(previousIconId)
    }

    // 自訂相片頁簽內的相簿選擇器：原圖先保留（存原始檔），再進裁切編輯器選位置/縮放，
    // 確認後成品與原始檔同 ts 配對存放；重編一律從原始檔讀取，不裁成品避免畫質遞減。
    // 解碼失敗才走舊的直接匯入。
    val context = LocalContext.current
    val cropScope = rememberCoroutineScope()
    var cropSource by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var cropSourceUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var cropIsReEdit by remember { mutableStateOf(false) }
    var cropInitialScale by remember { mutableFloatStateOf(1f) }
    var cropInitialNormOffsetX by remember { mutableFloatStateOf(0f) }
    var cropInitialNormOffsetY by remember { mutableFloatStateOf(0f) }
    fun closeCrop() {
        cropSource = null
        cropSourceUri = null
        cropIsReEdit = false
        cropInitialScale = 1f
        cropInitialNormOffsetX = 0f
        cropInitialNormOffsetY = 0f
    }
    val customPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            cropScope.launch(Dispatchers.IO) {
                val bmp = AvatarStorage.decodeForEdit(context, uri)
                withContext(Dispatchers.Main) {
                    if (bmp != null) {
                        cropInitialScale = 1f
                        cropInitialNormOffsetX = 0f
                        cropInitialNormOffsetY = 0f
                        cropSource = bmp
                        cropSourceUri = uri
                        cropIsReEdit = false
                    } else onImportPhoto(uri)
                }
            }
        }
    }
    if (cropSource != null) {
        AvatarCropDialog(
            source = cropSource!!,
            initialScale = cropInitialScale,
            initialNormOffsetX = cropInitialNormOffsetX,
            initialNormOffsetY = cropInitialNormOffsetY,
            onConfirm = { cropped, scale, normX, normY ->
                val srcUri = cropSourceUri
                val reEdit = cropIsReEdit
                val origForReEdit = currentAvatarOriginal
                closeCrop()
                if (reEdit) onSaveReEdit(cropped, origForReEdit, scale, normX, normY)
                else if (srcUri != null) onSaveCroppedWithSource(cropped, srcUri, scale, normX, normY)
                else onSaveCropped(cropped, scale, normX, normY)
            },
            onDismiss = { closeCrop() }
        )
    }
    // 非 Pro 不顯示自訂頁簽；但已有舊照片時仍顯示以便管理/刪除
    val showCustomTab = isPro || hasCustomPhoto

    // 大圖查看彈窗
    if (inspectingItem != null) {
        val item = inspectingItem!!
        val isLocked = item.isPro && !isPro
        val isSelected = isEffectivelySelected(item.id)

        com.reater.app.ui.theme.AppDialog(
            onDismissRequest = { inspectingItem = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(20.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 大圖頂部列（標籤 + X 關閉）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (item.isPro) Color(0xFFFFB300).copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (item.isPro) "PRO 專屬典藏" else "基礎免費款",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isPro) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { inspectingItem = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "關閉大圖",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 大圖預覽本體（108dp 大尺寸）
                    Box(
                        modifier = Modifier
                            .size(108.dp)
                            .clip(CircleShape)
                            .background(
                                if (item.isPro) Color(0xFFFFB300).copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .border(
                                width = 2.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else if (item.isPro) Color(0xFFFFB300).copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = item.resId),
                            contentDescription = item.name,
                            modifier = Modifier
                                .size(60.dp)
                                .alpha(if (isLocked) 0.35f else 1f),
                            tint = Color.Unspecified
                        )

                        // 未解鎖：灰色鎖定罩住大圖本體
                        if (isLocked) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF374151).copy(alpha = 0.72f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Pro 鎖定",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "PRO 專屬",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 名稱與描述（含總覽編號）
                    val inspectNumber = AvatarIcons.ALL.indexOfFirst { it.id == item.id } + 1
                    Text(
                        text = if (inspectNumber > 0) {
                            "%02d・%s".format(inspectNumber, item.name)
                        } else item.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (inspectNumber > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "圖示編號 %02d / %d".format(inspectNumber, AvatarIcons.ALL.size),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.desc,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 操作按鈕（使用中可再點一下取消勾選，回到相片或上一個圖示）
                    if (isSelected) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    deselectToPrevious()
                                    inspectingItem = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "使用中・再點一下取消勾選",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else if (isLocked) {
                        Button(
                            onClick = {
                                inspectingItem = null
                                onOpenUnlock()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("解鎖 Pro 專屬圖示", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                rememberBeforeApply()
                                onSelectAvatar(item.id)
                                inspectingItem = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("套用為個人頭像", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // 主展示廳 Dialog
    com.reater.app.ui.theme.AppDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(18.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 固定頂欄：右側「X」關閉按鈕絕對可見，左側使用 weight(1f) 防文字擠壓
                // PRO 徽章縮小並與 X 保持 8dp 間距，避免重疊/裁切
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "圖示總覽展示廳",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isPro) Color(0xFFFFB300) else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isPro) "PRO 典藏" else "共 15 款",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPro) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // 副標已依設計移除，保留標題單行避免擠壓關閉鈕
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "關閉展示廳",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // 篩選工具列已合併進各頁內容（單列：圖示範圍下拉＋自訂相片入口），不再使用獨立雙列頁籤，避免「全部 (15)」重複出現
                // pager 先建（雙向連動）：點按鈕 -> animateScrollToPage；左右滑 -> snapshotFlow 回寫頁籤
                val tabScope = rememberCoroutineScope()
                val pageCount = if (showCustomTab) 2 else 1
                val pagerState = rememberPagerState(
                    initialPage = if (showCustomTab && customActiveKey) 1 else 0
                ) { pageCount }
                LaunchedEffect(pagerState, pageCount) {
                    snapshotFlow { pagerState.currentPage }.collect { p ->
                        val clamped = p.coerceIn(0, pageCount - 1)
                        if (clamped != selectedTab) selectedTab = clamped
                    }
                }
                // 舊獨立頁籤列已移除（與下方「圖示範圍」下拉合併），導覽改由各頁單一工具列負責

                // 「全部」頁內容：範圍下拉選單（共用 ReaterDropdown 動畫）＋ 圖示列表
                // 下拉選項：全部(15) / 基礎免費(5) / PRO 專屬(10)，編號仍依總覽順序 01~15
                val scopeLabel = when (allScope) {
                    1 -> "基礎免費 (5)"
                    2 -> "PRO 專屬 (10)"
                    else -> "全部 (${AvatarIcons.ALL.size})"
                }
                val visibleIcons = when (allScope) {
                    1 -> AvatarIcons.FREE
                    2 -> AvatarIcons.PRO
                    else -> AvatarIcons.ALL
                }
                val allPageContent: @Composable (Modifier) -> Unit = { pageModifier ->
                    Column(modifier = pageModifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            // 合併式單一工具列：左為圖示範圍下拉（全部/免費/PRO），右為自訂相片入口
                            // 取代舊的「全部 (15) 頁籤＋圖示範圍下拉」雙列，避免重複顯示「全部 (15)」
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ReaterDropdownTrigger(
                                        expanded = scopeExpanded,
                                        onClick = { scopeExpanded = !scopeExpanded },
                                        title = "圖示範圍",
                                        value = scopeLabel
                                    )
                                }
                                if (showCustomTab) {
                                    GalleryToolbarButton(
                                        text = "自訂相片",
                                        onClick = {
                                            selectedTab = 1
                                            tabScope.launch {
                                                if (pagerState.currentPage != 1) pagerState.animateScrollToPage(1)
                                            }
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // 展開/收合：expandVertically(top) + fadeIn 300ms / 反向 250ms（見 ReaterDropdownMotion）
                            ReaterDropdownPanel(
                                expanded = scopeExpanded
                            ) {
                                ReaterDropdownOption(
                                    text = "全部",
                                    subLabel = "共 ${AvatarIcons.ALL.size} 款",
                                    selected = allScope == 0,
                                    onClick = { allScope = 0; scopeExpanded = false }
                                )
                                ReaterDropdownOption(
                                    text = "基礎免費",
                                    subLabel = "${AvatarIcons.FREE.size} 款免費",
                                    selected = allScope == 1,
                                    onClick = { allScope = 1; scopeExpanded = false }
                                )
                                ReaterDropdownOption(
                                    text = "PRO 專屬",
                                    subLabel = "${AvatarIcons.PRO.size} 款典藏",
                                    selected = allScope == 2,
                                    onClick = { allScope = 2; scopeExpanded = false }
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(visibleIcons, key = { it.id }) { item ->
                                val number = AvatarIcons.ALL.indexOfFirst { it.id == item.id } + 1
                                IconGalleryCard(
                                    item = item,
                                    number = number,
                                    isSelected = isEffectivelySelected(item.id),
                                    isProUser = isPro,
                                    onInspect = { inspectingItem = item },
                                    onApply = {
                                        if (item.isPro && !isPro) {
                                            onOpenUnlock()
                                        } else {
                                            rememberBeforeApply()
                                            onSelectAvatar(item.id)
                                        }
                                    },
                                    onDeselect = { deselectToPrevious() }
                                )
                            }
                        }
                    }
                }
                val customPageContent: @Composable (Modifier) -> Unit = { pageModifier ->
                    // 自訂相片頁（PRO）：頂部單一工具列提供返回圖示一覽入口，與全部頁的合併工具列對應
                    Column(modifier = pageModifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GalleryBackButton(
                                text = "圖示一覽 (${AvatarIcons.ALL.size})",
                                onClick = {
                                    selectedTab = 0
                                    tabScope.launch {
                                        if (pagerState.currentPage != 0) pagerState.animateScrollToPage(0)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "自訂相片",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    // 自訂相片頭貼內容：原圖保留＋重編從原始檔讀取；按鈕僅保留 更換/刪除 左右並排
                    CustomPhotoSection(
                        avatarId = currentAvatarId,
                        avatarUri = currentAvatarUri,
                        hasCustomPhoto = hasCustomPhoto,
                        onPick = { customPhotoPicker.launch("image/*") },
                        onClear = onClearPhoto,
                        avatarHistory = avatarHistory,
                        onSelectHistory = onSelectHistory,
                        onDeleteHistory = onDeleteHistory,
                        onEditCurrent = {
                            // 重編一律從原始檔讀取（全圖保留），並載入上次放大與位移位置
                            cropScope.launch(Dispatchers.IO) {
                                val origPath = currentAvatarOriginal
                                    ?: AvatarStorage.pairedOriginalFile(context, currentAvatarUri)?.absolutePath
                                val origBmp = AvatarStorage.decodeFile(origPath) ?: AvatarStorage.decodeFile(currentAvatarUri)
                                val transform = AvatarStorage.loadCropTransform(context, origPath ?: currentAvatarUri)
                                withContext(Dispatchers.Main) {
                                    if (origBmp != null) {
                                        cropInitialScale = transform?.scale ?: 1f
                                        cropInitialNormOffsetX = transform?.normOffsetX ?: 0f
                                        cropInitialNormOffsetY = transform?.normOffsetY ?: 0f
                                        cropSource = origBmp
                                        cropSourceUri = null
                                        cropIsReEdit = true
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    }
                }

                // 內容與頁籤雙向連動：無自訂頁籤時只有「全部」，直接顯示避免連動錯亂
                if (showCustomTab) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f)
                    ) { page ->
                        if (page == 1) {
                            customPageContent(Modifier.fillMaxSize())
                        } else {
                            allPageContent(Modifier.fillMaxSize())
                        }
                    }
                } else {
                    if (selectedTab != 0) selectedTab = 0
                    allPageContent(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GallerySmallTab(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    leading: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            leading?.invoke()
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * 合併式工具列按鈕：高度與 ReaterDropdownTrigger 對齊（vertical 12dp），用於「自訂相片」入口。
 * 取代舊的雙列小頁籤，避免與下拉內的「全部 (15)」重複。
 */
@Composable
private fun GalleryToolbarButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * 返回圖示一覽按鈕：與下拉同高，左箭頭＋文字，點擊回到全部頁。
 */
@Composable
private fun GalleryBackButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "‹",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 自訂相片說明列：勾選圖示＋單句文字，左對齊易讀。
 */
@Composable
private fun PhotoInfoRow(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun IconGalleryCard(
    item: AvatarIconItem,
    number: Int = 0,
    isSelected: Boolean,
    isProUser: Boolean,
    onInspect: () -> Unit,
    onApply: () -> Unit,
    onDeselect: () -> Unit = {}
) {
    val isLocked = item.isPro && !isProUser

    // 編號徽章改放卡片左上角（不再疊在圖示上），避免擋住圖示本體
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onInspect() }
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 圖示本體預覽（點擊可大圖查看）
                Box(
                    modifier = Modifier.size(54.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                if (item.isPro) Color(0xFFFFB300).copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = 1.dp,
                                color = if (item.isPro) Color(0xFFFFB300).copy(alpha = 0.4f)
                                else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = item.resId),
                            contentDescription = item.name,
                            modifier = Modifier
                                .size(30.dp)
                                .alpha(if (isLocked) 0.35f else 1f),
                            tint = Color.Unspecified
                        )
                        // 未解鎖：灰色鎖定罩住圖示本體
                        if (isLocked) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF374151).copy(alpha = 0.72f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Pro 鎖定",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (item.isPro) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFB300), RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "PRO",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "免費",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.desc,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 右側操作：使用中＝實心 primary 圓＋白色勾（再點取消）；未選用＝空心圓（無勾，避免與使用中混淆）；鎖定款維持解鎖鈕
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            shape = CircleShape
                        )
                        .clickable { onDeselect() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "使用中，再點一下取消勾選",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else if (isLocked) {
                OutlinedButton(
                    onClick = onApply,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("解鎖", fontSize = 12.sp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                            shape = CircleShape
                        )
                        .clickable(onClick = onApply),
                    contentAlignment = Alignment.Center
                ) {
                    // 未選用刻意留空：空心圓表示可點選套用，不再顯示淺色勾
                }
            }
        }
    }
    // 卡片左上角編號：純文字、無底色無邊框，避免重疊與版面過擠
    if (number > 0) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 12.dp, y = 6.dp)
                .padding(horizontal = 2.dp, vertical = 0.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "%02d".format(number),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
    }
    }
}

/**
 * 自訂相片頭貼頁簽內容：原圖保留＋重編從原始檔讀取。
 * 按鈕僅保留「更換 / 刪除」左右並排；調整位置改點頭像（右下鉛筆徽章）進入，
 * 重編一律讀原始全圖，不裁成品避免越編越小。
 * 每次匯入（含重編）都存成新的時間戳配對檔，DataStore 值必變即時同步。
 */
@Composable
private fun CustomPhotoSection(
    avatarId: String,
    avatarUri: String?,
    hasCustomPhoto: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
    avatarHistory: List<String> = emptyList(),
    onSelectHistory: (String) -> Unit = {},
    onDeleteHistory: (String) -> Unit = {},
    onEditCurrent: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 歷史以磁碟實際存在為準過濾，避免已刪檔還顯示空白格；原始檔不列入歷史
    val context = LocalContext.current
    val validHistory = remember(avatarHistory) {
        avatarHistory.filter { p ->
            try {
                if (p.contains("custom_avatar_orig_")) false
                else {
                    val f = java.io.File(p)
                    f.isAbsolute && f.exists() && f.length() > 0
                }
            } catch (_: Exception) {
                false
            }
        }
    }
    // 刪除確認：過往單張 X 與刪除照片都不再直接刪除
    var pendingDeletePath by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    if (pendingDeletePath != null) {
        com.reater.app.ui.theme.AppAlertDialog(
            onDismissRequest = { pendingDeletePath = null },
            title = { Text("刪除這張過往圖片？") },
            text = { Text("將從 App 內部儲存永久刪除（含原始檔），使用中頭像若是此張會一併退回圖示。此動作無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeletePath?.let { onDeleteHistory(it) }
                        pendingDeletePath = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDeletePath = null }) {
                    Text("取消")
                }
            }
        )
    }
    if (showClearConfirm) {
        com.reater.app.ui.theme.AppAlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("刪除自訂照片？") },
            text = { Text("將清除目前使用中的自訂照片並自動退回圖示頭貼，過往圖片仍保留可回選。確定要刪除嗎？") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClear()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            if (hasCustomPhoto) {
                // 頭像可點重編：右下鉛筆徽章；重編讀原始全圖
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clickable { onEditCurrent() },
                    contentAlignment = Alignment.Center
                ) {
                    UserAvatarView(
                        avatarId = avatarId,
                        avatarUri = avatarUri,
                        size = 96.dp
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "調整位置（從原圖重編）",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "點頭像調整位置・原圖保留可重編",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            } else {
                UserAvatarView(
                    avatarId = avatarId,
                    avatarUri = avatarUri,
                    size = 96.dp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (hasCustomPhoto) "自訂相片頭貼・使用中" else "尚未設定自訂照片",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 說明改為三列式資訊卡：每列一句、左對齊，避免長句分號擠成一團
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PhotoInfoRow(text = "原圖存在 App 內，隨時重調位置、不失真")
                    PhotoInfoRow(text = "相簿原檔刪除或移動，不影響顯示")
                    PhotoInfoRow(text = "重開 App 仍保留，不會遺失")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (hasCustomPhoto) {
            item {
                // 更新＋刪除左右並排（各半寬），跑版時文字縮成一行省略
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPick,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("更換照片", fontSize = 13.sp, maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("刪除照片", fontSize = 13.sp, color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "刪除後自動退回圖示頭貼\n也可在圖示頁點空心圓切換",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
            if (validHistory.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "過往圖片（點選切換使用中頭像）",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        validHistory.forEach { path ->
                            val selected = path == avatarUri
                            Box(modifier = Modifier.size(76.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) Color(0xFFFFB300)
                                            else MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape
                                        )
                                        .clickable { if (!selected) onSelectHistory(path) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val f = remember(path) {
                                        try {
                                            java.io.File(path).takeIf {
                                                it.isAbsolute && it.exists() && it.length() > 0
                                            }
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                    if (f != null) {
                                        val req = remember(path) {
                                            coil.request.ImageRequest.Builder(context)
                                                .data(f)
                                                .memoryCacheKey(AvatarStorage.cacheKey(path))
                                                .diskCacheKey(AvatarStorage.cacheKey(path))
                                                .crossfade(false)
                                                .build()
                                        }
                                        coil.compose.AsyncImage(
                                            model = req,
                                            contentDescription = "過往頭像",
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                    if (selected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(20.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "使用中",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                                // 單張刪除：先彈確認框，不直接刪除
                                IconButton(
                                    onClick = { pendingDeletePath = path },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            CircleShape
                                        )
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "刪除這張過往圖片",
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        } else {
            item {
                Button(
                    onClick = onPick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("選擇照片上傳", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                if (validHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "過往圖片（點選直接套用）",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        validHistory.forEach { path ->
                            Box(modifier = Modifier.size(76.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                        .clickable { onSelectHistory(path) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val f = remember(path) {
                                        try {
                                            java.io.File(path).takeIf {
                                                it.isAbsolute && it.exists() && it.length() > 0
                                            }
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                    if (f != null) {
                                        val req = remember(path) {
                                            coil.request.ImageRequest.Builder(context)
                                                .data(f)
                                                .memoryCacheKey(AvatarStorage.cacheKey(path))
                                                .diskCacheKey(AvatarStorage.cacheKey(path))
                                                .crossfade(false)
                                                .build()
                                        }
                                        coil.compose.AsyncImage(
                                            model = req,
                                            contentDescription = "過往頭像",
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                }
                                // 無使用中時歷史也可刪除：同樣先確認
                                IconButton(
                                    onClick = { pendingDeletePath = path },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            CircleShape
                                        )
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "刪除這張過往圖片",
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(13.dp)
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

/**
 * 使用者頭貼顯示元件：若有自訂照片則載入照片，否則顯示選取的向量 Icon。
 * 以路徑＋修改時間當 Coil 快取鍵，同路徑覆寫也不會顯示舊圖；
 * 配合時間戳新檔，展示介面與主畫面必同步刷新。
 */
@Composable
fun UserAvatarView(
    avatarId: String,
    avatarUri: String?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    showBorder: Boolean = true
) {
    val context = LocalContext.current
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
            val resId = AvatarIcons.getDrawableRes(avatarId)
            // 內部檔案絕對路徑 -> 轉 File 餵給 Coil；遺失則直接退回圖示避免空白
            val fileModel = remember(avatarUri) {
                if (!avatarUri.startsWith("content://") && !avatarUri.startsWith("file://")) {
                    val f = java.io.File(avatarUri)
                    if (f.isAbsolute && f.exists() && f.length() > 0) f else null
                } else {
                    avatarUri
                }
            }
            if (fileModel == null) {
                Icon(
                    painter = painterResource(id = resId),
                    contentDescription = "個人頭貼",
                    modifier = Modifier.size(size * 0.65f),
                    tint = Color.Unspecified
                )
            } else {
                val request = remember(avatarUri) {
                    coil.request.ImageRequest.Builder(context)
                        .data(fileModel)
                        .memoryCacheKey(AvatarStorage.cacheKey(avatarUri))
                        .diskCacheKey(AvatarStorage.cacheKey(avatarUri))
                        .crossfade(false)
                        .build()
                }
                coil.compose.AsyncImage(
                    model = request,
                    contentDescription = "個人自訂頭貼",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    error = painterResource(id = resId),
                    fallback = painterResource(id = resId)
                )
            }
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
