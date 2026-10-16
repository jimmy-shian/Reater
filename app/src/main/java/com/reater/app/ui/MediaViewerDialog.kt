package com.reater.app.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.reater.app.ui.player.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.content.pm.ActivityInfo
import java.io.File
import kotlin.math.abs
import kotlin.math.min

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

/** 全螢幕媒體使用同一個 Pager；圖片的 transform 不改變量測尺寸。 */
@Composable
fun MediaViewerDialog(
    media: List<ViewerMedia>,
    startIndex: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    onDismiss: () -> Unit
) {
    if (media.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(media.indices)) { media.size }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    val pageCallback by rememberUpdatedState(onPageChanged)
    LaunchedEffect(pager.settledPage) { pageCallback(pager.settledPage) }
    val activity = context.findPlayerActivity()
    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        VideoPlaybackManager.pauseAll()
        onDispose {
            VideoPlaybackManager.pauseAll()
            activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        val view = LocalView.current
        val window = remember(view) { findDialogWindow(view) }
        SideEffect {
            applyDialogWindowImmersive(window)
            if (!media[pager.currentPage].isVideo) setFullscreenSystemBarsVisible(window, controlsVisible)
        }
        DisposableEffect(window) { onDispose { setFullscreenSystemBarsVisible(window, true) } }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
                val item = media[page]
                if (item.isVideo && pager.currentPage == page) {
                    FullscreenVideoViewer(
                        remoteUrl = item.remoteUrl, localPath = item.localPath,
                        startPosition = 0, startPlaying = true,
                        onClose = { _, _, _ -> onDismiss() }
                    )
                } else if (!item.isVideo) {
                    var edgeDrag by remember { mutableFloatStateOf(0f) }
                    ZoomableViewerImage(item, onTap = { controlsVisible = !controlsVisible },
                        onEdgeDrag = { delta ->
                            edgeDrag += delta
                            pager.dispatchRawDelta(-delta)
                        },
                        onEdgeEnd = {
                            val target = if (abs(edgeDrag) > pager.layoutInfo.pageSize * .15f) {
                                (page + if (edgeDrag < 0) 1 else -1).coerceIn(media.indices)
                            } else page
                            scope.launch { pager.animateScrollToPage(target) }
                            edgeDrag = 0f
                        })
                }
            }
            if (!media[pager.currentPage].isVideo) {
                AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)) {
                    Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .45f))
                        .then(fullscreenTopBarModifier(false)),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${pager.currentPage + 1} / ${media.size}", color = Color.White)
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "關閉", tint = Color.White) }
                    }
                }
                AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)) {
                    Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .45f))
                        .then(fullscreenBottomBarModifier(false)), horizontalArrangement = Arrangement.Center) {
                        OutlinedButton(onClick = { scope.launch {
                            val item = media[pager.currentPage]
                            saveMediaToGalleryWithFeedback(context, false, item.localPath, item.remoteUrl)
                        } }) {
                            Icon(Icons.Default.Download, "下載圖片", tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("下載圖片", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableViewerImage(
    media: ViewerMedia,
    onTap: () -> Unit,
    onEdgeDrag: (Float) -> Unit,
    onEdgeEnd: () -> Unit
) {
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var transformAnimation by remember { mutableStateOf<Job?>(null) }
    fun bounds(zoom: Float): Offset {
        if (imageSize.width == 0 || viewport.width == 0) return Offset.Zero
        val fit = min(viewport.width.toFloat() / imageSize.width, viewport.height.toFloat() / imageSize.height)
        return Offset(((imageSize.width * fit * zoom - viewport.width) / 2).coerceAtLeast(0f),
            ((imageSize.height * fit * zoom - viewport.height) / 2).coerceAtLeast(0f))
    }
    fun settle() {
        val bound = bounds(scale)
        val x = offsetX; val y = offsetY
        val targetX = x.coerceIn(-bound.x, bound.x); val targetY = y.coerceIn(-bound.y, bound.y)
        transformAnimation?.cancel()
        transformAnimation = scope.launch { animate(0f, 1f, animationSpec = spring()) { value, _ ->
            offsetX = x + (targetX - x) * value; offsetY = y + (targetY - y) * value
        } }
    }
    LaunchedEffect(viewport, imageSize) { settle() }
    Box(Modifier.fillMaxSize().onSizeChanged { viewport = it }
        .pointerInput(Unit) {
            detectTapGestures(onTap = { onTap() }, onDoubleTap = { tap ->
                val target = if (scale > 1.05f) 1f else 2.5f
                val old = scale
                val center = Offset(size.width / 2f, size.height / 2f)
                val point = tap - center
                val bound = bounds(target)
                val targetX = (offsetX * target / old + point.x * (1 - target / old)).coerceIn(-bound.x, bound.x)
                val targetY = (offsetY * target / old + point.y * (1 - target / old)).coerceIn(-bound.y, bound.y)
                val x = offsetX; val y = offsetY
                transformAnimation?.cancel()
                transformAnimation = scope.launch { animate(0f, 1f, animationSpec = spring()) { value, _ ->
                    scale = (old + (target - old) * value).coerceIn(1f, 5f)
                    offsetX = x + (targetX - x) * value; offsetY = y + (targetY - y) * value
                } }
            })
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                transformAnimation?.cancel()
                var totalPan = Offset.Zero
                var totalZoom = 1f
                var transforming = false
                var edge = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val pointers = event.changes.count { it.pressed }
                    totalPan += pan
                    totalZoom *= zoom
                    val zoomMotion = abs(1 - totalZoom) * size.width / 2f
                    val moved = totalPan.getDistance() > viewConfiguration.touchSlop || zoomMotion > viewConfiguration.touchSlop
                    if ((pointers > 1 || scale > 1.01f || transforming) && (moved || transforming)) {
                        val old = scale
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        val center = event.calculateCentroid(useCurrent = false) - Offset(size.width / 2f, size.height / 2f)
                        val bound = bounds(scale)
                        val proposedX = offsetX * scale / old + center.x * (1 - scale / old) + pan.x
                        val proposedY = offsetY * scale / old + center.y * (1 - scale / old) + pan.y
                        val outward = pointers == 1 && abs(pan.x) > abs(pan.y) &&
                            ((offsetX >= bound.x - 1 && pan.x > 0) || (offsetX <= -bound.x + 1 && pan.x < 0))
                        if (outward && transforming) {
                            edge = true; onEdgeDrag(pan.x)
                        } else if (!outward || pointers > 1) {
                            offsetX = if (abs(proposedX) > bound.x) proposedX.coerceIn(-bound.x, bound.x) +
                                (proposedX - proposedX.coerceIn(-bound.x, bound.x)) * .2f else proposedX
                            offsetY = if (abs(proposedY) > bound.y) proposedY.coerceIn(-bound.y, bound.y) +
                                (proposedY - proposedY.coerceIn(-bound.y, bound.y)) * .2f else proposedY
                            transforming = true
                        }
                        if (transforming) event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                settle()
                if (edge) onEdgeEnd()
            }
        }, contentAlignment = Alignment.Center) {
        val context = LocalContext.current
        key(retry) {
            AsyncImage(model = ImageRequest.Builder(context).data(media.bestSource()).build(),
                imageLoader = rememberMediaImageLoader(),
                contentDescription = "圖片", contentScale = ContentScale.Fit,
                onLoading = { loading = true; failed = false },
                onSuccess = { result ->
                    loading = false
                    imageSize = IntSize(result.result.drawable.intrinsicWidth.coerceAtLeast(1), result.result.drawable.intrinsicHeight.coerceAtLeast(1))
                }, onError = { loading = false; failed = true },
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY
                })
        }
        if (loading) CircularProgressIndicator()
        if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("載入失敗", color = Color.White)
            Button(onClick = { retry++ }) { Text("重新載入") }
        }
    }
}

/**
 * 把圖片存到系統相簿（Pictures/Reater）。
 * API 29+ 走 MediaStore（免權限）；以下版本存 App 外部 Pictures 並提示路徑。
 */
fun saveImageToGallery(context: Context, media: ViewerMedia): Boolean {
    val src: File = media.localFileOrNull() ?: return false
    return com.reater.app.ui.player.copyFileToGallery(context, src, isVideo = false)
}
