package com.reater.app.ui.player

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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

private val Context.videoPrefsDataStore by preferencesDataStore(name = "video_playback_prefs")

private object VideoSpeedPrefs {
    private val KEY_SPEED = floatPreferencesKey("video_playback_speed")

    suspend fun getSpeed(context: Context): Float {
        return runCatching {
            context.videoPrefsDataStore.data.first()[KEY_SPEED] ?: 1.0f
        }.getOrDefault(1.0f)
    }

    suspend fun setSpeed(context: Context, speed: Float) {
        runCatching {
            context.videoPrefsDataStore.edit { prefs ->
                prefs[KEY_SPEED] = speed
            }
        }
    }
}

/**
 * 內嵌式與全螢幕影片播放器（依循 Android 系統規範與 YouTube Mobile 播放器規範設計）
 *
 * 核心規範與設計點：
 * 1. 放大全螢幕時以真實轉向切換（Activity.requestedOrientation），直/橫向影片皆自然填滿；
 *    Manifest 有 configChanges，不會重建 Activity。關閉還原直向。
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
 * 仿 YouTube 全螢幕播放檢視器（真實轉向版）
 * - 放大進入時預設切橫向（改 Activity.requestedOrientation），關閉還原直向
 * - Manifest 已宣告 configChanges，不會重建 Activity，只會重算版面
 * - 直向 / 橫向影片皆可正常填滿，不再用假旋轉（graphicsLayer）避免裁切與觸控錯位
 * - 底部控制列保證位於系統導覽列之上，完全避開遮擋
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

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
    var showSpeedMenu by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // 預設放大轉橫向（仿 YouTube 行為；真實轉向 Activity）
    var isLandscape by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var vvRef by remember { mutableStateOf<VideoView?>(null) }
    // 向下滑動關閉：跟隨手指的垂直位移（放開超過閾值即關閉回到文章）
    var dismissOffsetY by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = remember(density) { with(density) { 120.dp.toPx() } }

    // Dialog 內的 LocalContext 是 ContextWrapper 包裝，必須逐層拆包才能拿到 Activity；
    // 先前直接 as? Activity 永遠為 null，導致 requestedOrientation 從未生效（轉向失敗主因）。
    val activity = remember(context) { findFullscreenActivity(context) }
    LaunchedEffect(isLandscape) {
        runCatching {
            activity?.requestedOrientation = if (isLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    // 載入記憶中的倍速設定
    LaunchedEffect(Unit) {
        val savedSpeed = VideoSpeedPrefs.getSpeed(context)
        playbackSpeed = savedSpeed
    }

    fun applySpeed(speed: Float) {
        playbackSpeed = speed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayerRef?.let { mp ->
                runCatching {
                    mp.playbackParams = mp.playbackParams.setSpeed(speed)
                }
            }
        }
    }

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

    // 退出時釋放播放器並還原直向
    DisposableEffect(fullscreenPlayerId) {
        onDispose {
            VideoPlaybackManager.unregister(fullscreenPlayerId)
            vvRef?.stopPlayback()
            vvRef = null
            mediaPlayerRef = null
            runCatching {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    // 自動隱藏控制器（播放中 3.5 秒自動淡出；拖動進度條或開啟倍速選單時不自動隱藏）
    LaunchedEffect(controlsVisible, isPlaying, scrubbing, showSpeedMenu, lastInteractionTime) {
        if (controlsVisible && isPlaying && !scrubbing && !showSpeedMenu) {
            delay(3500)
            controlsVisible = false
        }
    }

    // 播放計時器更新：只要已 prepared 就持續輪詢（與 isPlaying 脫鉤），
    // 避免「全螢幕開啟時 inline 已先暫停 → startPlaying 永遠 false」、
    // 「使用者在 onPrepared 前先按播放 → 狀態與 VideoView 脫鉤」導致首次時間不跟隨，
    // 必須再暫停/播放一次才恢復的老問題。暫停時也更新 duration，避免 0:00 閃爍。
    LaunchedEffect(isPrepared) {
        while (isPrepared) {
            val vv = vvRef
            if (vv != null && !scrubbing) {
                val d = runCatching { vv.duration }.getOrDefault(0)
                val p = runCatching { vv.currentPosition }.getOrDefault(0)
                if (d > 0) {
                    duration = d
                    position = p.coerceIn(0, d)
                    progress = (p.toFloat() / d).coerceIn(0f, 1f)
                }
            }
            delay(250)
        }
    }

    fun seekRelative(deltaMs: Int) {
        val currentD = if (duration > 0) duration else 1
        val newPos = (position + deltaMs).coerceIn(0, currentD)
        vvRef?.seekTo(newPos)
        position = newPos
        progress = newPos.toFloat() / currentD
        controlsVisible = true
        lastInteractionTime = System.currentTimeMillis()
    }

    fun toggleFullscreenMute() {
        isMuted = !isMuted
        runCatching {
            if (isMuted) mediaPlayerRef?.setVolume(0f, 0f)
            else mediaPlayerRef?.setVolume(1f, 1f)
        }
        controlsVisible = true
        lastInteractionTime = System.currentTimeMillis()
    }

    fun handleClose() {
        onClose(position, isPlaying, isMuted)
    }

    // 系統安全邊界：直向 / 橫向拆開撰寫（見 SafeBarModifiers.kt）。
    // 影片本體 fillMaxSize 直接畫到系統列下方（沉浸式），只有「控制列」吃安全邊界：
    // - 直向頂列：左右均衡，頂部保證在狀態列之下；
    //   直向底列：bottom = max(實際 inset + 12dp, 48dp)，保證在三鍵列/手勢列之上，
    //   修復進度條掉進手機導覽列返回區塊無法使用的問題。
    // - 橫向頂列：左側可貼邊、右側空出導覽列返回鍵；
    //   橫向底列：bottom 上抬、右側空出返回鍵，避免進度條右端被裁、底部純粹過低。
    // 中央控制組：不吃 insets，永遠置中於影片。

    // 向下滑動關閉手勢（含放開回彈/超過閾值關閉）；Slider 是橫向拖曳，不會誤觸。
    val dismissDragModifier = Modifier.pointerInput(dismissThresholdPx) {
        detectVerticalDragGestures(
            onDragCancel = { dismissOffsetY = 0f },
            onDragEnd = {
                if (dismissOffsetY > dismissThresholdPx) {
                    dismissOffsetY = 0f
                    handleClose()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer {
                translationY = dismissOffsetY
                alpha = (1f - (dismissOffsetY / 1200f).coerceIn(0f, 0.6f))
            },
        contentAlignment = Alignment.Center
    ) {
        // 影片本體：真實轉向後直接填滿即可，直/橫向影片皆由 VideoView 依比例呈現
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
                        val d = runCatching { mp.duration }.getOrDefault(0)
                        if (d > 0) {
                            duration = d
                            // 第一次就給正確時間/進度：onPrepared 當下立刻同步，
                            // 不再等 250ms 輪詢，避免開場 0:00 閃一下才跳對。
                            val sp = startPosition.coerceIn(0, d)
                            position = sp
                            progress = (sp.toFloat() / d).coerceIn(0f, 1f)
                        } else {
                            duration = d
                        }
                        isPrepared = true
                        isBuffering = false
                        runCatching {
                            if (isMuted) mp.setVolume(0f, 0f) else mp.setVolume(1f, 1f)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            runCatching {
                                mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed)
                            }
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
            update = { vv ->
                // 補上「先按播放、後才 prepared」的競爭缺口：
                // factory 閉包只記得舊 startPlaying，若使用者在緩衝完成前已按播放，
                // isPlaying=true 但 VideoView 仍停著 → 時間永遠不走，需再暫停/播放。
                // 此處每次重組都對齊一次，保證第一次播放就能跟隨。
                if (isPrepared) {
                    runCatching {
                        if (isPlaying && !vv.isPlaying) vv.start()
                        else if (!isPlaying && vv.isPlaying) vv.pause()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 觸控手勢感應層（單擊切換控制列，左/右雙擊快退/快進 10 秒；向下拖曳關閉回到文章）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(dismissDragModifier)
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
                    .then(dismissDragModifier)
                    // 空白處單擊隱藏控制列（修復：先前此處只設 true，導致點空白關不掉）；
                    // 雙擊維持快退/快進；按鈕本身會消費事件，不會冒泡到此處
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                controlsVisible = false
                            },
                            onDoubleTap = { offset ->
                                val halfWidth = size.width / 2f
                                if (offset.x < halfWidth) seekRelative(-10000) else seekRelative(10000)
                            }
                        )
                    }
            ) {
                // 1. 頂部列：直向 / 橫向拆開（fullscreenTopBarModifier），
                // 關閉按鈕、狀態標題、直橫向旋轉切換共用同一容器。
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .then(fullscreenTopBarModifier(isLandscape)),
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
                            lastInteractionTime = System.currentTimeMillis()
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
                                lastInteractionTime = System.currentTimeMillis()
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
                // 直向 / 橫向拆開（fullscreenBottomBarModifier）：
                // 直向上抬 48dp 保底避開導覽列返回區塊，橫向上抬並右側留白避開返回鍵。
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .then(fullscreenBottomBarModifier(isLandscape))
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

                    // YouTube 紅色進度拉條：thumbTrackGapSize=0 才能連成一條，
                    // 預設 8.dp 會在 thumb 左右各留缺口，直向截圖看起來就是
                    //「紅條—缺口—把手—缺口—灰條」斷成三截。觸控高度維持 26.dp。
                    Slider(
                        value = (if (scrubbing) scrubValue else progress).coerceIn(0f, 1f),
                        onValueChange = {
                            scrubbing = true
                            scrubValue = it
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onValueChangeFinished = {
                            vvRef?.let { vv ->
                                val target = (scrubValue * duration).toInt()
                                runCatching { vv.seekTo(target) }
                                position = target
                            }
                            progress = scrubValue
                            scrubbing = false
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF2020),
                            activeTrackColor = Color(0xFFFF2020),
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                        ),
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color(0xFFFF2020),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                                ),
                                thumbTrackGapSize = 0.dp
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

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
                                modifier = Modifier.clickable {
                                    toggleFullscreenMute()
                                }
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

                            // 倍速切換按鈕（點擊彈出倍速選單卡片）
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (showSpeedMenu) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.clickable {
                                    showSpeedMenu = !showSpeedMenu
                                    controlsVisible = true
                                    lastInteractionTime = System.currentTimeMillis()
                                }
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
                                    lastInteractionTime = System.currentTimeMillis()
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

                // 4. 倍速選單（置中彈窗卡片，避免 Popup 在旋轉容器中位置偏位）
                if (showSpeedMenu) {
                    val availableSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable {
                                showSpeedMenu = false
                                lastInteractionTime = System.currentTimeMillis()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF202020),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .width(220.dp)
                                .clickable(enabled = false) {}
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    text = "播放速度",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                availableSpeeds.forEach { speed ->
                                    val isSelected = (playbackSpeed == speed)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) Color.White.copy(alpha = 0.15f)
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                applySpeed(speed)
                                                coroutineScope.launch {
                                                    VideoSpeedPrefs.setSpeed(context, speed)
                                                }
                                                showSpeedMenu = false
                                                lastInteractionTime = System.currentTimeMillis()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (speed == 1.0f) "1.0x (正常)" else "${speed}x",
                                            color = if (isSelected) Color(0xFFFF4D4D) else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "已選擇",
                                                tint = Color(0xFFFF4D4D),
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
}

private fun formatMs(ms: Int): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

/**
 * 從 Dialog 包裝過的 Context 逐層拆包找出 Activity。
 * Compose Dialog 的 LocalContext 通常是 ContextThemeWrapper，直接 as? Activity 必為 null，
 * 這就是先前橫/直向切換無效的主因。
 */
private fun findFullscreenActivity(context: Context): android.app.Activity? {
    var c: Context? = context
    var depth = 0
    while (c != null && depth < 20) {
        if (c is android.app.Activity) return c
        c = (c as? ContextWrapper)?.baseContext
        depth++
    }
    return null
}
