package com.reater.app.ui.player

import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.io.File

/**
 * 內嵌式與全螢幕影片播放器（依循 Android 系統規範與 YouTube Mobile 播放器規範設計）
 *
 * 核心規範與設計點：
 * 1. 放大全螢幕時 App 本體鎖直向（見 Manifest），只在播放器內部做「假橫向」：
 *    橫向影片旋轉 90° 填滿高度，直向影片維持填滿，控制層一律維持直式可讀。
 * 2. 全螢幕內部提供直橫向切換按鈕，可自由在「直向全螢幕」與「橫向全螢幕」間切換。
 * 3. 安全邊界採 Compose 狀態化 insets（Dialog attach 後自動重算）＋保底值：
 *    頂部貼緊狀態列，底部控制列保證位於系統導覽列之上。
 * 4. 仿 YouTube 播放體驗：
 *    - 點選畫面叫出控制列，播放中 3.5 秒自動淡出。
 *    - 中央支援：快退 10 秒、大圓形播放/暫停、快進 10 秒；左/右半部雙擊快退/快進 10 秒。
 *    - 底部高對比紅/白進度條，拖曳時顯示精確時間氣泡。
 *    - 支援 0.75x ~ 2.0x 播放倍速切換與靜音一鍵切換。
 */
@Composable
fun InlineVideoPlayer(
    remoteUrl: String,
    localPath: String = "",
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier
) {
    val playerId = remember { java.util.UUID.randomUUID().toString() }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPrepared by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(true) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }

    fun applyInlineMute(mp: MediaPlayer?, muted: Boolean) {
        runCatching {
            if (muted) mp?.setVolume(0f, 0f) else mp?.setVolume(1f, 1f)
        }
    }

    fun toggleInlinePlay() {
        val vv = videoView ?: return
        if (!isPrepared) return
        if (vv.isPlaying) {
            vv.pause()
            position = vv.currentPosition
            isPlaying = false
            VideoPlaybackManager.onPlayStopped(playerId)
        } else {
            isBuffering = false
            VideoPlaybackManager.onPlayStarted(playerId) {
                videoView?.let { if (it.isPlaying) it.pause() }
                isPlaying = false
            }
            vv.start()
            isPlaying = true
        }
    }

    fun toggleInlineMute() {
        isMuted = !isMuted
        applyInlineMute(mediaPlayerRef, isMuted)
    }

    // 播放中定時更新位置
    LaunchedEffect(isPlaying, isPrepared) {
        while (isPlaying && isPrepared) {
            val vv = videoView
            if (vv != null) {
                val d = vv.duration
                val p = vv.currentPosition
                if (d > 0) {
                    duration = d
                    position = p
                }
            }
            delay(400)
        }
    }

    DisposableEffect(playerId) {
        onDispose {
            VideoPlaybackManager.unregister(playerId)
            videoView?.stopPlayback()
            videoView = null
            mediaPlayerRef = null
        }
    }

    // 全螢幕彈窗（放大轉橫向，關閉自動還原直向）
    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            FullscreenVideoViewer(
                remoteUrl = remoteUrl,
                localPath = localPath,
                startPosition = position,
                startPlaying = isPlaying,
                startMuted = isMuted,
                onClose = { finalPos, finalPlaying, finalMuted ->
                    isFullscreen = false
                    position = finalPos
                    isMuted = finalMuted
                    applyInlineMute(mediaPlayerRef, finalMuted)
                    videoView?.let { vv ->
                        runCatching { vv.seekTo(finalPos) }
                        if (finalPlaying) {
                            VideoPlaybackManager.onPlayStarted(playerId) {
                                videoView?.let { if (it.isPlaying) it.pause() }
                                isPlaying = false
                            }
                            vv.start()
                            isPlaying = true
                        } else {
                            if (vv.isPlaying) vv.pause()
                            isPlaying = false
                            VideoPlaybackManager.onPlayStopped(playerId)
                        }
                    }
                }
            )
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable { toggleInlinePlay() },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val src: Any = if (localPath.isNotBlank() && File(localPath).exists()) {
                        File(localPath)
                    } else remoteUrl
                    when (src) {
                        is File -> setVideoPath(src.absolutePath)
                        is String -> if (src.isNotBlank()) setVideoURI(Uri.parse(src))
                    }
                    setOnPreparedListener { mp ->
                        mediaPlayerRef = mp
                        isPrepared = true
                        isBuffering = false
                        duration = mp.duration
                        applyInlineMute(mp, isMuted)
                        if (autoPlay) {
                            VideoPlaybackManager.onPlayStarted(playerId) {
                                videoView?.let { if (it.isPlaying) it.pause() }
                                isPlaying = false
                            }
                            mp.start()
                            isPlaying = true
                        } else {
                            runCatching { seekTo(1) }
                            isPlaying = false
                        }
                    }
                    setOnInfoListener { _, what, _ ->
                        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START && !isPrepared) {
                            isBuffering = true
                        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                            isBuffering = false
                        }
                        false
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        position = 0
                        seekTo(0)
                    }
                    setOnErrorListener { _, _, _ ->
                        isBuffering = false
                        isPlaying = false
                        true
                    }
                    videoView = this
                }
            },
            update = { vv ->
                if (isPrepared && position > 0 && vv.currentPosition == 0 && !vv.isPlaying && isPlaying) {
                    runCatching { vv.seekTo(position) }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && !isPrepared) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(36.dp)
            )
        } else if (!isPlaying) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // 小圖底部控制列：左下播放/暫停、右下靜音
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { toggleInlinePlay() },
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暫停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { toggleInlineMute() },
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (isMuted) "取消靜音" else "靜音",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 放大全螢幕按鈕（右上角，點擊放大轉橫向）
        IconButton(
            onClick = {
                videoView?.let { vv ->
                    position = vv.currentPosition
                    if (vv.isPlaying) {
                        vv.pause()
                    }
                }
                isPlaying = false
                VideoPlaybackManager.pauseAll()
                isFullscreen = true
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(36.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "放大全螢幕播放",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 仿 YouTube 全螢幕播放檢視器（App 本體鎖直向，橫向只在播放器內部呈現）
 * - 放大進入時預設嘗試橫向呈現（橫向影片旋轉填滿），可一鍵切回直向
 * - 底部控制列保證位於系統導覽列之上，完全避開遮擋
 */
@Composable
private fun FullscreenVideoViewer(
    remoteUrl: String,
    localPath: String,
    startPosition: Int,
    startPlaying: Boolean,
    startMuted: Boolean = true,
    onClose: (finalPosition: Int, isPlaying: Boolean, isMuted: Boolean) -> Unit
) {
    val view = LocalView.current

    val fullscreenPlayerId = remember { java.util.UUID.randomUUID().toString() }
    var isPlaying by remember { mutableStateOf(startPlaying) }
    var isMuted by remember { mutableStateOf(startMuted) }
    var isPrepared by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var position by remember { mutableIntStateOf(startPosition) }
    var progress by remember { mutableFloatStateOf(0f) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(true) }

    // 預設放大轉橫向（仿 YouTube 行為；App 本體不轉向，只在播放器內部呈現橫向）
    var isLandscape by remember { mutableStateOf(true) }
    // 影片實際方向（onPrepared 回報後才知道；橫向影片才值得旋轉填滿）
    var videoIsLandscape by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var vvRef by remember { mutableStateOf<VideoView?>(null) }

    // 設定 Dialog Window 透明系統列
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

    // 退出時釋放播放器（App 本體全程鎖直向，不需還原轉向）
    DisposableEffect(fullscreenPlayerId) {
        onDispose {
            VideoPlaybackManager.unregister(fullscreenPlayerId)
            vvRef?.stopPlayback()
            vvRef = null
            mediaPlayerRef = null
        }
    }

    // 自動隱藏控制器（播放中 3.5 秒自動淡出）
    LaunchedEffect(controlsVisible, isPlaying, scrubbing) {
        if (controlsVisible && isPlaying && !scrubbing) {
            delay(3500)
            controlsVisible = false
        }
    }

    // 播放計時器更新
    LaunchedEffect(isPlaying, isPrepared) {
        while (isPlaying && isPrepared) {
            val vv = vvRef
            if (vv != null && !scrubbing) {
                val d = vv.duration
                val p = vv.currentPosition
                if (d > 0) {
                    duration = d
                    position = p
                    progress = p.toFloat() / d
                }
            }
            delay(300)
        }
    }

    fun seekRelative(deltaMs: Int) {
        val currentD = if (duration > 0) duration else 1
        val newPos = (position + deltaMs).coerceIn(0, currentD)
        vvRef?.seekTo(newPos)
        position = newPos
        progress = newPos.toFloat() / currentD
        controlsVisible = true
    }

    fun cycleSpeed() {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
        val nextSpeed = speeds[nextIdx]
        playbackSpeed = nextSpeed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayerRef?.let { mp ->
                runCatching {
                    mp.playbackParams = mp.playbackParams.setSpeed(nextSpeed)
                }
            }
        }
        controlsVisible = true
    }

    fun toggleFullscreenMute() {
        isMuted = !isMuted
        runCatching {
            if (isMuted) mediaPlayerRef?.setVolume(0f, 0f)
            else mediaPlayerRef?.setVolume(1f, 1f)
        }
        controlsVisible = true
    }

    fun handleClose() {
        onClose(position, isPlaying, isMuted)
    }

    // 系統安全邊界：Compose 狀態化 insets（Dialog attach 後自動重算，不是一次性讀取）。
    // 頂部貼緊：狀態列＋4dp（至少 10dp），不額外下移；
    // 底部高於導覽列：導覽列＋16dp（至少 64dp），控制列絕不被遮擋。
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val safeTop = maxOf(statusTop + 4.dp, 10.dp)
    val safeBottom = maxOf(navBottom + 16.dp, 64.dp)

    val safeSidePadding = PaddingValues(start = 16.dp, end = 16.dp)

    // 假橫向：只旋轉影片層（橫向影片才轉），控制層維持直式可讀。
    // App 本體鎖直向（見 Manifest），此處不碰 requestedOrientation。
    val effectiveRotate = isLandscape && videoIsLandscape

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val screenW = maxWidth
        val screenH = maxHeight

        // 影片本體：橫向模式且為橫向影片時，容器尺寸對調並旋轉 90° 填滿高度；
        // 直向影片或直向模式則原樣填滿。控制層一律在外層維持直式。
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val src: Any = if (localPath.isNotBlank() && File(localPath).exists()) {
                        File(localPath)
                    } else remoteUrl
                    when (src) {
                        is File -> setVideoPath(src.absolutePath)
                        is String -> if (src.isNotBlank()) setVideoURI(Uri.parse(src))
                    }
                    setOnPreparedListener { mp ->
                        mediaPlayerRef = mp
                        duration = mp.duration
                        isPrepared = true
                        isBuffering = false
                        val vw = mp.videoWidth
                        val vh = mp.videoHeight
                        if (vw > 0 && vh > 0) {
                            videoIsLandscape = vw > vh
                        }
                        runCatching {
                            if (isMuted) mp.setVolume(0f, 0f) else mp.setVolume(1f, 1f)
                        }
                        if (startPosition > 0) {
                            runCatching { seekTo(startPosition) }
                        }
                        if (startPlaying) {
                            VideoPlaybackManager.onPlayStarted(fullscreenPlayerId) {
                                vvRef?.let { if (it.isPlaying) it.pause() }
                                isPlaying = false
                            }
                            mp.start()
                            isPlaying = true
                        }
                    }
                    setOnInfoListener { _, what, _ ->
                        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) isBuffering = true
                        else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) isBuffering = false
                        false
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        controlsVisible = true
                    }
                    setOnErrorListener { _, _, _ ->
                        isPlaying = false
                        isBuffering = false
                        true
                    }
                    vvRef = this
                }
            },
            modifier = if (effectiveRotate) {
                Modifier
                    .size(screenH, screenW)
                    .graphicsLayer { rotationZ = 90f }
            } else {
                Modifier.fillMaxSize()
            }
        )

        // 觸控手勢感應層（單擊切換控制列，左/右雙擊快退/快進 10 秒）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val halfWidth = size.width / 2f
                            if (offset.x < halfWidth) seekRelative(-10000) else seekRelative(10000)
                        }
                    )
                }
        )

        // 中央緩衝中指示器
        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }

        // 控制器覆蓋層（YouTube 風格平滑淡入淡出）
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
            ) {
                // 1. 頂部列：關閉按鈕、狀態標題、直橫向旋轉切換
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = safeTop)
                        .padding(safeSidePadding)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { handleClose() },
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "關閉全螢幕",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isLandscape) "橫向全螢幕" else "直向全螢幕",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // 頂部旋轉切換膠囊按鈕
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.clickable {
                            isLandscape = !isLandscape
                            controlsVisible = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "切換螢幕方向",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isLandscape) "切換直向" else "切換橫向",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 2. 中央控制組：快退 10s、播放/暫停、快進 10s
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { seekRelative(-10000) },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "倒退 10 秒",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            .clickable {
                                val vv = vvRef ?: return@clickable
                                if (vv.isPlaying) {
                                    vv.pause()
                                    isPlaying = false
                                    VideoPlaybackManager.onPlayStopped(fullscreenPlayerId)
                                } else {
                                    VideoPlaybackManager.onPlayStarted(fullscreenPlayerId) {
                                        vvRef?.let { if (it.isPlaying) it.pause() }
                                        isPlaying = false
                                    }
                                    vv.start()
                                    isPlaying = true
                                }
                                controlsVisible = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暫停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    IconButton(
                        onClick = { seekRelative(10000) },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "快進 10 秒",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // 3. 底部 YouTube 風格進度條與資訊按鈕列
                // 直向與橫向皆精準避開導覽列與側邊邊界
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(safeSidePadding)
                        .padding(bottom = safeBottom)
                ) {
                    // 拖曳中時間預覽懸浮提示
                    if (scrubbing) {
                        val scrubMs = (scrubValue * duration).toInt()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.85f)
                            ) {
                                Text(
                                    text = "${formatMs(scrubMs)} / ${formatMs(duration)}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // YouTube 紅色進度拉條
                    Slider(
                        value = (if (scrubbing) scrubValue else progress).coerceIn(0f, 1f),
                        onValueChange = {
                            scrubbing = true
                            scrubValue = it
                        },
                        onValueChangeFinished = {
                            vvRef?.let { vv ->
                                val target = (scrubValue * duration).toInt()
                                runCatching { vv.seekTo(target) }
                                position = target
                            }
                            progress = scrubValue
                            scrubbing = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF2020),
                            activeTrackColor = Color(0xFFFF2020),
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                        )
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // 時間與功能控制列
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左側：時間顯示 (01:23 / 03:45)
                        Text(
                            text = "${formatMs(if (scrubbing) (scrubValue * duration).toInt() else position)} / ${formatMs(duration)}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // 右側按鈕：靜音、倍速與轉向切換
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 靜音切換按鈕
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.clickable { toggleFullscreenMute() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = if (isMuted) "取消靜音" else "靜音",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // 倍速切換按鈕
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.clickable { cycleSpeed() }
                            ) {
                                Text(
                                    text = "${playbackSpeed}x",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // 旋轉按鈕（直向 ⇄ 橫向）
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.clickable {
                                    isLandscape = !isLandscape
                                    controlsVisible = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ScreenRotation,
                                        contentDescription = "切換螢幕方向",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isLandscape) "直向" else "橫向",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
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

private fun formatMs(ms: Int): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
