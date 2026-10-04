package com.reater.app.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.reater.app.ui.player.fullscreenBottomBarModifier
import com.reater.app.ui.player.fullscreenTopBarModifier
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

/**
 * 檢視器用的輕量媒體模型（DetailDialog 的 MediaEntity、
 * ShareSave 的 FetchedMedia 都轉成這個再傳入）。
 */
data class ViewerMedia(
    val kind: String,
    val remoteUrl: String,
    val localPath: String = ""
) {
    val isVideo: Boolean get() = kind.equals("VIDEO", ignoreCase = true)

    fun bestSource(): Any =
        if (localPath.isNotBlank() && File(localPath).exists()) File(localPath) else remoteUrl

    fun localFileOrNull(): File? =
        if (localPath.isNotBlank()) File(localPath).takeIf { it.exists() } else null
}

/**
 * 全螢幕媒體檢視器：左右滑動切換、雙指/雙擊放大、X 關閉、圖片下載到相簿、影片外部開啟。
 *
 * 對齊 InlineVideoPlayer.FullscreenVideoViewer 的系統列處理：
 * Dialog Window 透明系統列 + 狀態化 insets（attach 後自動重算）+ 保底值，
 * 底部控制列保證位於系統導覽列之上（三鍵列/手勢列皆不遮擋）。
 */
@Composable
fun MediaViewerDialog(
    media: List<ViewerMedia>,
    startIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (media.isEmpty()) return
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, media.size - 1),
        pageCount = { media.size }
    )
    // 當前頁圖片是否處於放大狀態：放大時停用 Pager 滑動，改由圖片自身平移
    var zoomedOnCurrentPage by remember { mutableStateOf(false) }
    val currentPage = pagerState.currentPage
    val current = media[currentPage.coerceIn(0, media.size - 1)]
    // 向下滑動關閉：跟隨手指的垂直位移（圖片與影片頁共用，放開超過閾值即回到文章）
    var dismissOffsetY by remember { mutableFloatStateOf(0f) }

    // 切頁時重置放大旗標與下滑位移（新頁由其 ZoomableImage 重新回報；影片頁直接歸 false）
    LaunchedEffect(currentPage) {
        dismissOffsetY = 0f
        if (media.getOrNull(currentPage)?.isVideo == true) {
            zoomedOnCurrentPage = false
        }
    }

    DisposableEffect(Unit) {
        com.reater.app.ui.player.VideoPlaybackManager.pauseAll()
        onDispose {
            com.reater.app.ui.player.VideoPlaybackManager.pauseAll()
        }
    }

    // 與影片全螢幕播放器同規格：Dialog Window 透明系統列
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
            win.statusBarColor = android.graphics.Color.TRANSPARENT
            win.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                win.isNavigationBarContrastEnforced = false
                win.isStatusBarContrastEnforced = false
            }
        }
    }

    // 系統安全邊界：直向 / 橫向拆開撰寫，與影片全螢幕同一套
    // fullscreenTopBarModifier / fullscreenBottomBarModifier（見 SafeBarModifiers.kt）。
    // 影片/圖片本體吃滿，頂/底列各自帶保底 inset，避免「下載圖片」貼底被導覽列蓋掉。
    // 直向底列保底 48dp（避開三鍵列/手勢列），橫向底列上抬＋右側留白避開返回鍵。
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val dismissThresholdPx = remember(density) { with(density) { 120.dp.toPx() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = dismissOffsetY
                        alpha = (1f - (dismissOffsetY / 1200f).coerceIn(0f, 0.6f))
                    }
                    // 向下滑動關閉（圖片未放大、影片頁皆可）：只吃向下位移，
                    // 橫向滑動留給 Pager 翻頁；放大時停用，改由圖片自身平移。
                    .pointerInput(zoomedOnCurrentPage, dismissThresholdPx) {
                        if (zoomedOnCurrentPage) return@pointerInput
                        detectVerticalDragGestures(
                            onDragCancel = { dismissOffsetY = 0f },
                            onDragEnd = {
                                if (dismissOffsetY > dismissThresholdPx) {
                                    dismissOffsetY = 0f
                                    onDismiss()
                                } else {
                                    dismissOffsetY = 0f
                                }
                            },
                            onVerticalDrag = { _, dragAmount ->
                                if (dragAmount > 0f || dismissOffsetY > 0f) {
                                    dismissOffsetY = (dismissOffsetY + dragAmount).coerceAtLeast(0f)
                                }
                            }
                        )
                    }
            ) {
                // 媒體本體：HorizontalPager 支援左右滑動；圖片頁內建雙指/雙擊縮放
                HorizontalPager(
                    state = pagerState,
                    // 放大時停用翻頁，單指改為平移圖片；未放大時單指滑動翻頁、雙指縮放
                    userScrollEnabled = !zoomedOnCurrentPage,
                    modifier = Modifier.fillMaxSize(),
                    key = { page -> "${page}_${media[page].remoteUrl}_${media[page].localPath}" }
                ) { page ->
                    val item = media[page]
                    val isCurrent = (page == currentPage)
                    if (item.isVideo) {
                        com.reater.app.ui.player.InlineVideoPlayer(
                            remoteUrl = item.remoteUrl,
                            localPath = item.localPath,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ZoomableImagePage(
                            model = item.bestSource(),
                            contentDescription = "Media ${page + 1}",
                            onZoomChange = { zoomed ->
                                // 只接受當前頁的回報，避免預載入頁互相覆蓋
                                if (isCurrent) zoomedOnCurrentPage = zoomed
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 頂列：直向 / 橫向拆開（fullscreenTopBarModifier），計數 + X 關閉。
                // 背景先鋪滿再接 padding inset，媒體沉浸式延伸到系統列下方。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .then(fullscreenTopBarModifier(isLandscape)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentPage + 1} / ${media.size}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "關閉",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // 左右切換箭頭（滑動為主、按鈕為輔，保留無障礙操作）
                if (media.size > 1 && !zoomedOnCurrentPage) {
                    if (currentPage > 0) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(currentPage - 1)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 8.dp)
                                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "上一張",
                                tint = Color.White
                            )
                        }
                    }
                    if (currentPage < media.size - 1) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(currentPage + 1)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "下一張",
                                tint = Color.White
                            )
                        }
                    }
                }

                // 底列：直向 / 橫向拆開（fullscreenBottomBarModifier），下載/開啟共用。
                // 直向保底 48dp 保證「下載圖片」在導覽列之上，橫向右側留白避開返回鍵。
                // 背景先鋪滿再接 padding inset，沉浸式不留黑邊。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .then(fullscreenBottomBarModifier(isLandscape)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (current.isVideo) {
                        Button(
                            onClick = {
                                val file = current.localFileOrNull()
                                runCatching {
                                    if (file != null) {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW)
                                                .setDataAndType(uri, "video/*")
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        )
                                    } else if (current.remoteUrl.isNotBlank()) {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(current.remoteUrl))
                                        )
                                    }
                                }.onFailure {
                                    Toast.makeText(context, "無法開啟影片", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("開啟影片")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                val ok = saveImageToGallery(context, current)
                                Toast.makeText(
                                    context,
                                    if (ok) "已儲存到相簿" else "儲存失敗（無本機檔案）",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("下載圖片")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 可縮放圖片：雙指捏合局部放大（1x ~ 5x）＋ 單指平移（放大時）＋ 雙擊切換 1x/2.5x（以點擊為焦點）。
 *
 * 手勢分流（避免與 HorizontalPager 搶奪單指滑動）：
 * - 未放大：單指不消費，交給 Pager 翻頁；雙指捏合縮放。
 * - 已放大：停用 Pager（上層 userScrollEnabled=false），單指平移圖片。
 */
@Composable
private fun ZoomableImagePage(
    model: Any,
    contentDescription: String?,
    onZoomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(scale) {
        onZoomChange(scale > 1.02f)
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val maxWpx = with(density) { maxWidth.toPx() }
        val maxHpx = with(density) { maxHeight.toPx() }

        fun clamp(o: Offset, s: Float): Offset {
            if (s <= 1f) return Offset.Zero
            val boundX = (maxWpx * (s - 1f) / 2f).coerceAtLeast(0f)
            val boundY = (maxHpx * (s - 1f) / 2f).coerceAtLeast(0f)
            return Offset(
                o.x.coerceIn(-boundX, boundX),
                o.y.coerceIn(-boundY, boundY)
            )
        }

        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                // 雙擊：1x <-> 2.5x，以點擊位置為焦點
                .pointerInput(model) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            if (scale > 1.2f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val newScale = 2.5f
                                val k = newScale / scale.coerceAtLeast(1f)
                                val tapped = tap - center
                                offset = clamp(offset * k + tapped * (1f - k), newScale)
                                scale = newScale
                            }
                        }
                    )
                }
                // 捏合縮放（雙指）＋ 平移（僅放大時消費單指，避免擋住 Pager 翻頁）
                .pointerInput(model, maxWpx, maxHpx) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            when {
                                pressed.size >= 2 -> {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    if (zoomChange != 1f) {
                                        val old = scale
                                        val new = (old * zoomChange).coerceIn(1f, 5f)
                                        if (new != old) {
                                            // 以手勢重心為焦點，保持重心下內容穩定
                                            val centroid = event.calculateCentroid()
                                            val center = Offset(size.width / 2f, size.height / 2f)
                                            val k = new / old
                                            val focal = centroid - center
                                            offset = clamp(offset * k + focal * (1f - k) + panChange, new)
                                            scale = new
                                        } else if (scale > 1f && panChange != Offset.Zero) {
                                            offset = clamp(offset + panChange, scale)
                                        }
                                        pressed.forEach { it.consume() }
                                    } else if (scale > 1f && panChange != Offset.Zero) {
                                        offset = clamp(offset + panChange, scale)
                                        pressed.forEach { it.consume() }
                                    }
                                }
                                pressed.size == 1 && scale > 1f -> {
                                    val pan = event.calculatePan()
                                    if (pan != Offset.Zero) {
                                        offset = clamp(offset + pan, scale)
                                        pressed.forEach { it.consume() }
                                    }
                                }
                                else -> {
                                    // 未放大單指：不消費，交給 HorizontalPager 翻頁
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * 把圖片存到系統相簿（Pictures/Reater）。
 * API 29+ 走 MediaStore（免權限）；以下版本存 App 外部 Pictures 並提示路徑。
 */
fun saveImageToGallery(context: Context, media: ViewerMedia): Boolean {
    val src: File = media.localFileOrNull()
        ?: return false
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ext = src.extension.ifBlank { "jpg" }
            val mime = when (ext.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "reater_${System.currentTimeMillis()}.$ext")
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Reater")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Reater"
            ).apply { if (!exists()) mkdirs() }
            val dst = File(dir, "reater_${System.currentTimeMillis()}.${src.extension.ifBlank { "jpg" }}")
            src.copyTo(dst, overwrite = true)
            Toast.makeText(context, "已儲存：${dst.absolutePath}", Toast.LENGTH_LONG).show()
            true
        }
    } catch (_: Exception) {
        false
    }
}
