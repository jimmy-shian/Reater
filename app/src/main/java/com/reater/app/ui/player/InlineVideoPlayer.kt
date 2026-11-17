package com.reater.app.ui.player

import androidx.compose.material3.MaterialTheme
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.abs
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.reater.app.R
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
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
 * 1. 放大全螢幕時以真實轉向切換（Activity.requestedOrientation），直/橫向影片皆依比例完整顯示；
 *    Manifest 有 configChanges，不會重建 Activity。關閉還原進入前方向。
 * 2. 全螢幕內部提供直橫向切換按鈕，可自由在「直向全螢幕」與「橫向全螢幕」間切換。
 * 3. 安全邊界採 Compose 狀態化 insets（Dialog attach 後自動重算）：
 *    頂部貼緊狀態列，底部控制列保證位於系統導覽列之上。
 * 4. 仿 YouTube 播放體驗：
 *    - 點選畫面叫出控制列，播放中 3.5 秒自動淡出。
 *    - 中央支援：快退 10 秒、大圓形播放/暫停、快進 10 秒；左/右半部雙擊快退/快進 10 秒。
 *    - 底部高對比紫/白進度條，拖曳時顯示精確時間氣泡。
 *    - 支援 0.75x ~ 2.0x 播放倍速切換與靜音一鍵切換。
 */
@Composable
fun InlineVideoPlayer(
    remoteUrl: String,
    localPath: String = "",
    autoPlay: Boolean = false,
    /** 音訊（無畫面）來源的海報圖：緩衝中與整段播放期間顯示，看起來像影片封面 */
    posterUrl: String = "",
    /** AUDIO 媒體附帶的 metadata JSON（AudioMetaStore.encode 格式；匯出時嵌入曲名/歌手/歌詞） */
    audioMetaJson: String = "",
    modifier: Modifier = Modifier
) {
    val playerId = remember { java.util.UUID.randomUUID().toString() }
    var isPlaying by rememberSaveable(remoteUrl, localPath) { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPrepared by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf(false) }
    var retryAttempt by remember { mutableIntStateOf(0) }
    var isFullscreen by rememberSaveable(remoteUrl, localPath) { mutableStateOf(false) }
    var fullscreenWasPlaying by rememberSaveable(remoteUrl, localPath) { mutableStateOf(false) }
    var isMuted by rememberSaveable(remoteUrl, localPath) { mutableStateOf(true) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    // audio-only mp4（Threads 音訊貼文等）：videoWidth 永遠為 0 → 顯示海報代替黑畫面
    var isAudioOnly by remember { mutableStateOf(false) }
    val showPoster = posterUrl.isNotBlank() && (isAudioOnly || !isPrepared)
    var position by rememberSaveable(remoteUrl, localPath) { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    // 是否實際播放過：未播放過的播放器不回報暫停狀態，
    // 避免同 URL 的另一個播放中實例（清單卡↔詳情頁）被快照互相覆蓋造成歌詞卡閃爍
    var hasPlayed by remember { mutableStateOf(false) }

    // 預先取得本機影片真實尺寸（含旋轉校正）
    val fileDims = remember(localPath) { getVideoDimensions(localPath) }
    var videoWidth by remember(localPath) { mutableIntStateOf(fileDims?.first ?: 0) }
    var videoHeight by remember(localPath) { mutableIntStateOf(fileDims?.second ?: 0) }

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
            hasPlayed = true
            isPlaying = true
        }
    }

    fun toggleInlineMute() {
        isMuted = !isMuted
        applyInlineMute(mediaPlayerRef, isMuted)
    }

    // 播放中定時更新位置（同時回報 PlaybackStateBus：詳情頁動態歌詞卡同步用）
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
                PlaybackStateBus.update(remoteUrl, p.toLong(), d.toLong(), true)
            }
            delay(400)
        }
    }

    // 暫停／停止時回報一次最後進度（歌詞卡凍結在當前行；從未播放過的實例不回報，
    // 以免蓋掉同一媒體正在播放中實例的快照）
    LaunchedEffect(isPlaying, isPrepared, hasPlayed) {
        if (isPrepared && !isPlaying && hasPlayed) {
            val vv = videoView
            PlaybackStateBus.update(
                remoteUrl,
                (vv?.currentPosition ?: position).toLong(),
                (vv?.duration ?: duration).toLong(),
                false
            )
        }
    }

    DisposableEffect(playerId) {
        onDispose {
            VideoPlaybackManager.unregister(playerId)
            videoView?.stopPlayback()
            videoView = null
            mediaPlayerRef = null
            PlaybackStateBus.clear(remoteUrl)
        }
    }

    // 全螢幕彈窗（自動依影片比例決定預設直或橫向，關閉自動還原直向）
    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            var dismissOffsetY by remember { mutableFloatStateOf(0f) }
            var dismissHeightPx by remember { mutableIntStateOf(1) }
            var dismissSettling by remember { mutableStateOf(false) }
            val fullscreenScope = rememberCoroutineScope()

            fun closeFullscreen(finalPos: Int = position, finalPlaying: Boolean = isPlaying, finalMuted: Boolean = isMuted) {
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
                        hasPlayed = true
                        isPlaying = true
                    } else {
                        if (vv.isPlaying) vv.pause()
                        isPlaying = false
                        VideoPlaybackManager.onPlayStopped(playerId)
                    }
                }
            }

            fun updateDismissDrag(delta: Float) {
                if (dismissSettling) return
                val resistance = 1f + abs(dismissOffsetY) / (dismissHeightPx * 0.5f).coerceAtLeast(1f)
                dismissOffsetY += delta / resistance
            }

            fun endDismissDrag(velocityY: Float = 0f) {
                if (dismissSettling) return
                val shouldDismissDown = dismissOffsetY > dismissHeightPx * 0.18f ||
                    (dismissOffsetY > dismissHeightPx * 0.06f && velocityY > 1200f)
                val shouldDismissUp = dismissOffsetY < -dismissHeightPx * 0.18f ||
                    (dismissOffsetY < -dismissHeightPx * 0.06f && velocityY < -1200f)
                val shouldDismiss = shouldDismissDown || shouldDismissUp

                if (!shouldDismiss && abs(dismissOffsetY) <= 0.5f) {
                    dismissOffsetY = 0f
                    return
                }
                dismissSettling = true
                fullscreenScope.launch {
                    try {
                        val anim = Animatable(dismissOffsetY)
                        if (shouldDismiss) {
                            val target = if (shouldDismissDown) dismissHeightPx.toFloat() else -dismissHeightPx.toFloat()
                            anim.animateTo(target, tween(220)) { dismissOffsetY = value }
                            closeFullscreen()
                        } else {
                            anim.animateTo(0f, spring(dampingRatio = 0.82f)) { dismissOffsetY = value }
                        }
                    } finally {
                        dismissSettling = false
                    }
                }
            }

            val dismissAlpha = (1f - abs(dismissOffsetY) / dismissHeightPx.toFloat() * 1.6f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { dismissHeightPx = it.height.coerceAtLeast(1) }
                    .background(Color.Black.copy(alpha = dismissAlpha))
                    .graphicsLayer {
                        translationY = dismissOffsetY
                        val s = (1f - abs(dismissOffsetY) / dismissHeightPx.toFloat() * 0.12f).coerceIn(0.88f, 1f)
                        scaleX = s; scaleY = s
                        alpha = dismissAlpha
                    }
            ) {
                FullscreenVideoViewer(
                    remoteUrl = remoteUrl,
                    localPath = localPath,
                    startPosition = position,
                    startPlaying = fullscreenWasPlaying,
                    startMuted = isMuted,
                    initialVideoWidth = videoWidth,
                    initialVideoHeight = videoHeight,
                    // 音訊（無畫面）必須把海報帶進全螢幕，否則開全螢幕只剩黑畫面
                    posterUrl = posterUrl,
                    // 音訊 metadata（曲名/歌手/歌詞）：匯出「音樂版/影片版」時嵌入用
                    audioMetaJson = audioMetaJson,
                    onDismissDrag = { updateDismissDrag(it) },
                    onDismissEnd = { endDismissDrag(it) },
                    onClose = { finalPos, finalPlaying, finalMuted ->
                        closeFullscreen(finalPos, finalPlaying, finalMuted)
                    }
                )
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable { toggleInlinePlay() },
        contentAlignment = Alignment.Center
    ) {
        key(retryAttempt) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val src: Any = if (localPath.isNotBlank() && File(localPath).exists()) {
                        File(localPath)
                    } else remoteUrl
                    when (src) {
                        is File -> setVideoPath(src.absolutePath)
                        is String -> if (src.isNotBlank()) setVideoURI(Uri.parse(src)) else {
                            playbackError = true
                            isBuffering = false
                        }
                    }
                    setOnPreparedListener { mp ->
                        mediaPlayerRef = mp
                        isPrepared = true
                        playbackError = false
                        isBuffering = false
                        duration = mp.duration
                        val w = mp.videoWidth
                        val h = mp.videoHeight
                        // 無視軌 → 純音訊（audio-only mp4 也能正常播放/seek）
                        isAudioOnly = w == 0 || h == 0
                        if (fileDims == null && w > 0 && h > 0) {
                            videoWidth = w
                            videoHeight = h
                        }
                        runCatching {
                            mp.setOnVideoSizeChangedListener { _, newW, newH ->
                                if (fileDims == null && newW > 0 && newH > 0) {
                                    videoWidth = newW
                                    videoHeight = newH
                                }
                            }
                        }
                        applyInlineMute(mp, isMuted)
                        if (position > 0) runCatching { seekTo(position) }
                        if (autoPlay || isPlaying) {
                            VideoPlaybackManager.onPlayStarted(playerId) {
                                videoView?.let { if (it.isPlaying) it.pause() }
                                isPlaying = false
                            }
                            mp.start()
                            hasPlayed = true
                            isPlaying = true
                        } else {
                            runCatching { seekTo(position.coerceAtLeast(1)) }
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
                        // 播完：回報歸零暫停態，歌詞卡回到靜態顯示
                        PlaybackStateBus.update(remoteUrl, 0L, duration.toLong(), false)
                    }
                    setOnErrorListener { _, _, _ ->
                        playbackError = true
                        isPrepared = false
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
            modifier = Modifier.fillMaxSize(),
            onRelease = { released ->
                runCatching { position = released.currentPosition }
                released.stopPlayback()
            }
        )
        }
        // 海報層：音訊（無畫面）或尚未 prepare 時顯示封面，避免純黑畫面
        if (showPoster) {
            AsyncImage(
                model = posterUrl,
                contentDescription = stringResource(R.string.video_poster_desc),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        if (playbackError) {
            Column(Modifier.background(Color.Black.copy(alpha = .85f)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.video_play_failed), color = Color.White)
                Button(onClick = {
                    isPrepared = false; isBuffering = true; playbackError = false; isPlaying = true
                    retryAttempt++
                }) { Text(stringResource(R.string.video_retry)) }
            }
        }

        if (isBuffering && !isPrepared) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
        // 中央大播放鍵已移除：底部控制列左下已有播放／暫停鍵，
        // 同時顯示兩顆播放鍵屬重複（點擊畫面本身也可切換播放）。

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
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.video_pause_desc) else stringResource(R.string.video_play_desc),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { toggleInlineMute() },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) stringResource(R.string.video_unmute_desc) else stringResource(R.string.video_mute_desc),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 放大全螢幕按鈕（右上角，點擊放大轉橫向）
        IconButton(
            onClick = {
                fullscreenWasPlaying = isPlaying
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
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = stringResource(R.string.video_fullscreen_desc),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 仿 YouTube 全螢幕播放檢視器（真實轉向版）
 * - 放大進入時依影片比例選擇方向（改 Activity.requestedOrientation），關閉還原進入前方向
 * - Manifest 已宣告 configChanges，不會重建 Activity，只會重算版面
 * - 直向 / 橫向影片皆依比例完整呈現，不再用假旋轉（graphicsLayer）避免裁切與觸控錯位
 * - 底部控制列保證位於系統導覽列之上，完全避開遮擋
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullscreenVideoViewer(
    remoteUrl: String,
    localPath: String,
    startPosition: Int,
    startPlaying: Boolean,
    startMuted: Boolean = true,
    initialVideoWidth: Int = 0,
    initialVideoHeight: Int = 0,
    /** 音訊（無畫面）來源的海報圖：顯示封面代替黑畫面 */
    posterUrl: String = "",
    /** AUDIO 媒體 metadata JSON（AudioMetaStore.encode 格式；空字串=無，匯出用） */
    audioMetaJson: String = "",
    onDismissDrag: (Float) -> Unit = {},
    onDismissEnd: (Float) -> Unit = {},
    onClose: (finalPosition: Int, isPlaying: Boolean, isMuted: Boolean) -> Unit
) {
    val view = LocalView.current

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val fullscreenPlayerId = remember { java.util.UUID.randomUUID().toString() }
    var isPlaying by rememberSaveable(remoteUrl, localPath) { mutableStateOf(startPlaying) }
    var isMuted by rememberSaveable(remoteUrl, localPath) { mutableStateOf(startMuted) }
    var isPrepared by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf(false) }
    var retryAttempt by remember { mutableIntStateOf(0) }
    var isBuffering by remember { mutableStateOf(true) }
    var duration by remember { mutableIntStateOf(0) }
    var position by rememberSaveable(remoteUrl, localPath) { mutableIntStateOf(startPosition) }
    var progress by remember { mutableFloatStateOf(0f) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }
    var pendingSingleTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // 是否實際播放過：從未播放過的實例不回報暫停狀態，避免蓋掉播放中實例的快照
    var hasPlayed by remember { mutableStateOf(startPlaying) }

    // 預設方向：依影片實際寬高比自動判斷（寬 >= 高 → 橫向，寬 < 高 → 直向）。
    // 若初進入時尺寸未知，預設為 false (直向全螢幕)，避免手機縱向瀏覽時突然強行橫置。
    val initialLandscape = remember {
        if (initialVideoWidth > 0 && initialVideoHeight > 0) {
            initialVideoWidth > initialVideoHeight
        } else {
            getVideoDimensions(localPath)?.let { (w, h) -> w > h }
        }
    }
    var orientationDecided by rememberSaveable(remoteUrl, localPath) { mutableStateOf(initialLandscape != null) }
    var isLandscape by rememberSaveable(remoteUrl, localPath) { mutableStateOf<Boolean?>(initialLandscape ?: false) }
    var playbackSpeed by rememberSaveable(remoteUrl, localPath) { mutableFloatStateOf(1.0f) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var vvRef by remember { mutableStateOf<VideoView?>(null) }
    // audio-only mp4（Threads 音訊貼文等）：videoWidth 永遠為 0 → 顯示海報代替黑畫面
    var isAudioOnly by remember { mutableStateOf(false) }
    val showPoster = posterUrl.isNotBlank() && (isAudioOnly || !isPrepared)

    // 真實轉向：切換 Activity.requestedOrientation（Manifest 有 configChanges，不會重建）
    val activity = context.findPlayerActivity()
    val previousOrientation = remember(activity) { activity?.requestedOrientation }
    LaunchedEffect(isLandscape) {
        if (isLandscape == null) return@LaunchedEffect
        runCatching {
            activity?.requestedOrientation = if (isLandscape == true) {
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
                    val wasPlaying = isPlaying
                    mp.playbackParams = mp.playbackParams.setSpeed(speed)
                    if (!wasPlaying) mp.pause()
                }
            }
        }
    }

    // 設定 Dialog Window 透明系統列
    val dialogWindow = remember(view) { findDialogWindow(view) }
    val safeInsets = rememberMediaViewerSafeInsets(dialogWindow, view, controlsVisible)
    SideEffect {
        applyDialogWindowImmersive(dialogWindow)
        setFullscreenSystemBarsVisible(dialogWindow, controlsVisible || playbackError, activity)
    }

    // 退出時釋放播放器並還原直向
    DisposableEffect(fullscreenPlayerId) {
        onDispose {
            setFullscreenSystemBarsVisible(dialogWindow, true, activity)
            VideoPlaybackManager.unregister(fullscreenPlayerId)
            // 回報最後進度（暫停態），讓詳情頁歌詞卡與內嵌播放器位置一致
            vvRef?.let { last ->
                runCatching {
                    PlaybackStateBus.update(remoteUrl, last.currentPosition.toLong(), last.duration.toLong(), false)
                }
            }
            vvRef?.stopPlayback()
            vvRef = null
            mediaPlayerRef = null
            runCatching {
                activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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

    // 播放計時器更新（同時回報 PlaybackStateBus：詳情頁動態歌詞卡同步用）
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
                PlaybackStateBus.update(remoteUrl, p.toLong(), d.toLong(), true)
            }
            delay(300)
        }
    }

    // 暫停／停止時回報一次最後進度（歌詞卡凍結在當前行；從未播放過的實例不回報）
    LaunchedEffect(isPlaying, isPrepared, hasPlayed) {
        if (isPrepared && !isPlaying && hasPlayed) {
            val vv = vvRef
            PlaybackStateBus.update(
                remoteUrl,
                (vv?.currentPosition ?: position).toLong(),
                (vv?.duration ?: duration).toLong(),
                false
            )
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
        // 暫停中快轉／倒轉：回報新位置讓動態歌詞卡跟著跳行
        if (!isPlaying && hasPlayed) {
            PlaybackStateBus.update(remoteUrl, newPos.toLong(), currentD.toLong(), false)
        }
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
        val finalPosition = vvRef?.let { runCatching { it.currentPosition }.getOrNull() } ?: position
        onClose(finalPosition, isPlaying, isMuted)
    }
    BackHandler { handleClose() }

    // 音訊（無畫面）匯出：先問「影片版還是音樂版」，匯出中顯示進度視窗
    var showAudioExportChooser by remember { mutableStateOf(false) }
    var audioExportLabel by remember { mutableStateOf<String?>(null) }
    if (showAudioExportChooser) {
        AlertDialog(
            onDismissRequest = { showAudioExportChooser = false },
            title = { Text(stringResource(R.string.video_export_format_title)) },
            text = {
                // 可捲動：橫向螢幕內容超過對話框高度時不被截斷
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.video_export_format_message), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            painter = painterResource(R.drawable.ic_music_note),
                            contentDescription = stringResource(R.string.video_music_desc),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.video_export_music_title), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.video_export_music_desc),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            painter = painterResource(R.drawable.ic_movie),
                            contentDescription = stringResource(R.string.video_movie_desc),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.video_export_video_title), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.video_export_video_desc),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        showAudioExportChooser = false
                        audioExportLabel = context.getString(R.string.video_export_music_progress)
                        coroutineScope.launch {
                            exportAudioAsTaggedMusicWithFeedback(
                                context = context,
                                localPath = localPath,
                                remoteUrl = remoteUrl,
                                posterUrl = posterUrl,
                                audioMetaJson = audioMetaJson
                            )
                            audioExportLabel = null
                        }
                    }) { Text(stringResource(R.string.video_export_music)) }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = {
                        showAudioExportChooser = false
                        audioExportLabel = context.getString(R.string.video_export_video_progress)
                        coroutineScope.launch {
                            exportAudioAsCoverVideoWithFeedback(
                                context = context,
                                localPath = localPath,
                                remoteUrl = remoteUrl,
                                posterUrl = posterUrl
                            )
                            audioExportLabel = null
                        }
                    }) { Text(stringResource(R.string.video_export_video)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAudioExportChooser = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (audioExportLabel != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.video_exporting_title)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(audioExportLabel.orEmpty(), fontSize = 14.sp)
                }
            },
            confirmButton = {}
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // 影片本體 B方案動態：縮到安全區，導覽列出現/隱藏時自動變化，橫向左右不再被蓋
        key(retryAttempt) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val src: Any = if (localPath.isNotBlank() && File(localPath).exists()) {
                        File(localPath)
                    } else remoteUrl
                    when (src) {
                        is File -> setVideoPath(src.absolutePath)
                        is String -> if (src.isNotBlank()) setVideoURI(Uri.parse(src)) else {
                            playbackError = true
                            isBuffering = false
                        }
                    }
                    setOnPreparedListener { mp ->
                        mediaPlayerRef = mp
                        duration = mp.duration
                        isPrepared = true
                        playbackError = false
                        isBuffering = false
                        // 無視軌 → 純音訊（audio-only mp4 也能正常播放/seek）
                        val w0 = mp.videoWidth
                        val h0 = mp.videoHeight
                        isAudioOnly = w0 == 0 || h0 == 0
                        // 依影片實際寬高比自動決定直/橫向（僅進場時判斷一次）
                        if (!orientationDecided) {
                            val w = mp.videoWidth
                            val h = mp.videoHeight
                            if (w > 0 && h > 0) {
                                orientationDecided = true
                                isLandscape = (w > h)
                            }
                        }
                        runCatching {
                            mp.setOnVideoSizeChangedListener { _, w, h ->
                                if (!orientationDecided && w > 0 && h > 0) {
                                    orientationDecided = true
                                    isLandscape = (w > h)
                                }
                            }
                        }
                        runCatching {
                            if (isMuted) mp.setVolume(0f, 0f) else mp.setVolume(1f, 1f)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            runCatching {
                                mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed)
                            }
                        }
                        if (position > 0) {
                            runCatching { seekTo(position) }
                        }
                        if (isPlaying) {
                            VideoPlaybackManager.onPlayStarted(fullscreenPlayerId) {
                                vvRef?.let { if (it.isPlaying) it.pause() }
                                isPlaying = false
                            }
                            mp.start()
                            hasPlayed = true
                            isPlaying = true
                        } else {
                            runCatching { mp.pause() }
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
                        playbackError = true
                        isPrepared = false
                        isPlaying = false
                        isBuffering = false
                        true
                    }
                    vvRef = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { released ->
                runCatching { position = released.currentPosition }
                released.stopPlayback()
            }
        )
        }

        // 海報層：音訊（無畫面）顯示封面代替黑畫面（控制列/手勢層在其上）
        if (showPoster) {
            AsyncImage(
                model = posterUrl,
                contentDescription = stringResource(R.string.video_poster_desc),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // 觸控手勢感應層：垂直主導手勢觸發上下滑動關閉檢視器；無拖曳時觸發單擊切換控制列與左右雙擊快退/快進
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onDismissDrag, onDismissEnd) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalPan = Offset.Zero
                        var isDragging = false
                        var velocityY = 0f
                        var lastTime = System.currentTimeMillis()
                        val downPoint = down.position

                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pan = event.calculatePan()
                            val pointers = event.changes.count { it.pressed }
                            totalPan += pan

                            if (!isDragging && pointers == 1) {
                                if (abs(totalPan.y) > viewConfiguration.touchSlop &&
                                    abs(totalPan.y) > abs(totalPan.x) * 1.2f) {
                                    isDragging = true
                                    lastTime = System.currentTimeMillis()
                                    velocityY = 0f
                                    pendingSingleTapJob?.cancel()
                                    pendingSingleTapJob = null
                                }
                            }

                            if (isDragging && pointers == 1) {
                                val now = System.currentTimeMillis()
                                val dt = (now - lastTime).coerceAtLeast(1)
                                velocityY = (pan.y / dt * 1000f * 0.6f + velocityY * 0.4f).coerceIn(-6000f, 6000f)
                                lastTime = now
                                onDismissDrag(pan.y)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        if (isDragging) {
                            onDismissEnd(velocityY)
                        } else if (totalPan.getDistance() < viewConfiguration.touchSlop) {
                            val now = System.currentTimeMillis()
                            val isDoubleTap = (now - lastTapTime < 300) &&
                                (downPoint - lastTapPosition).getDistance() < viewConfiguration.touchSlop * 2.5f

                            if (isDoubleTap) {
                                pendingSingleTapJob?.cancel()
                                pendingSingleTapJob = null
                                lastTapTime = 0L
                                val halfWidth = size.width / 2f
                                if (downPoint.x < halfWidth) seekRelative(-10000) else seekRelative(10000)
                            } else {
                                lastTapTime = now
                                lastTapPosition = downPoint
                                pendingSingleTapJob?.cancel()
                                pendingSingleTapJob = coroutineScope.launch {
                                    delay(260)
                                    controlsVisible = !controlsVisible
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                        }
                    }
                }
        )

        // 中央緩衝中指示器
        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
        if (playbackError) {
            Column(Modifier.background(Color.Black.copy(alpha = .85f)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.video_play_failed), color = Color.White)
                Button(onClick = {
                    isPrepared = false; isBuffering = true; playbackError = false; isPlaying = true
                    retryAttempt++
                }) { Text(stringResource(R.string.video_retry)) }
                Button(onClick = { handleClose() }) { Text(stringResource(R.string.close)) }
            }
        }

        // 控制器覆蓋層（YouTube 風格平滑淡入淡出）
        AnimatedVisibility(
            visible = controlsVisible && !playbackError,
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
                        .then(fullscreenTopBarModifier(safeInsets)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { handleClose() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.video_fullscreen_close_desc),
                                tint = Color.White
                            )
                        }

                    }

                    // 頂部旋轉切換膠囊按鈕
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.clickable {
                            orientationDecided = true
                            isLandscape = !(isLandscape ?: true)
                            controlsVisible = true
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    ) {
                        Row(
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = stringResource(R.string.video_rotate_desc),
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.video_rotate_button),
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
                            contentDescription = stringResource(R.string.video_rewind_10_desc),
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
                                    hasPlayed = true
                                    isPlaying = true
                                }
                                controlsVisible = true
                                lastInteractionTime = System.currentTimeMillis()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.video_pause_desc) else stringResource(R.string.video_play_desc),
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
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
                            contentDescription = stringResource(R.string.video_forward_10_desc),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // 3. 底部 YouTube 風格進度條與資訊按鈕列
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .then(fullscreenBottomBarModifier(safeInsets))
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
                            // 暫停中拖曳進度：回報新位置讓動態歌詞卡跟著跳行
                            if (!isPlaying && hasPlayed) {
                                PlaybackStateBus.update(remoteUrl, position.toLong(), duration.toLong(), false)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 一般字級同列；小螢幕或大字級自動換行，避免擠壓控制按鈕。
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 左側：時間顯示 (01:23 / 03:45)
                        Text(
                            text = "${formatMs(if (scrubbing) (scrubValue * duration).toInt() else position)} / ${formatMs(duration)}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // 右側按鈕：靜音、下載、倍速與轉向切換
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 靜音切換按鈕
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .height(48.dp)
                                    .clickable {
                                    toggleFullscreenMute()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.height(48.dp).padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = if (isMuted) stringResource(R.string.video_unmute_desc) else stringResource(R.string.video_mute_desc),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // 下載按鈕（將影片儲存到系統相簿，仿圖片檢視器）
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .height(48.dp)
                                    .clickable {
                                    controlsVisible = true
                                    lastInteractionTime = System.currentTimeMillis()
                                    if (isAudioOnly) {
                                        // 音訊（音樂）貼文：先選「影片版/音樂版」再匯出
                                        showAudioExportChooser = true
                                    } else {
                                        coroutineScope.launch {
                                            saveMediaToGalleryWithFeedback(
                                                context = context,
                                                isVideo = true,
                                                localPath = localPath,
                                                remoteUrl = remoteUrl,
                                                isAudio = false
                                            )
                                        }
                                    }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.height(48.dp).padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = stringResource(R.string.video_download_desc),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.video_download_button),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // 倍速切換按鈕（點擊彈出倍速選單卡片）
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (showSpeedMenu) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .height(48.dp)
                                    .clickable {
                                    showSpeedMenu = !showSpeedMenu
                                    controlsVisible = true
                                    lastInteractionTime = System.currentTimeMillis()
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(48.dp)
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
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
                                    text = stringResource(R.string.video_speed_title),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp)
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
                                            text = if (speed == 1.0f) stringResource(R.string.video_speed_normal) else "${speed}x",
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = stringResource(R.string.video_speed_selected_desc),
                                                tint = MaterialTheme.colorScheme.primary,
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
