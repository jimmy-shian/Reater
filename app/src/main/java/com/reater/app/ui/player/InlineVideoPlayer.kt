package com.reater.app.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

/**
 * 內建影片播放：系統 VideoView，不需額外依賴。
 * 點一下播放 / 暫停；離開自動停止釋放。
 * 修正：暫停後重播不再轉圈（僅首次載入顯示 buffering）；支援右下放大橫向全螢幕。
 */
@Composable
fun InlineVideoPlayer(
    remoteUrl: String,
    localPath: String = "",
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPrepared by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var position by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            videoView?.stopPlayback()
            videoView = null
        }
    }

    if (isFullscreen) {
        val ctx = LocalContext.current
        val activity = ctx as? Activity
        val prevOrientation = remember { activity?.requestedOrientation }
        LaunchedEffect(Unit) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        DisposableEffect(Unit) {
            onDispose {
                if (prevOrientation != null) {
                    runCatching { activity?.requestedOrientation = prevOrientation }
                } else {
                    runCatching { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
                }
            }
        }
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // 全螢幕沿用同一來源重建播放器，從原位置續播
                FullscreenVideoContent(
                    remoteUrl = remoteUrl,
                    localPath = localPath,
                    startPosition = position,
                    onClose = { isFullscreen = false }
                )
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable {
                val vv = videoView ?: return@clickable
                if (!isPrepared) return@clickable
                if (vv.isPlaying) {
                    vv.pause()
                    position = vv.currentPosition
                    isPlaying = false
                    isBuffering = false
                } else {
                    // 續播：已 prepared 不再轉圈，直接 start
                    isBuffering = false
                    vv.start()
                    isPlaying = true
                }
            },
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
                        isPrepared = true
                        isBuffering = false
                        // 自動開始（與舊行為一致）
                        mp.start()
                        isPlaying = true
                    }
                    setOnInfoListener { _, what, _ ->
                        // 701=緩衝開始，702=緩衝結束；僅未 prepared 時顯示轉圈
                        if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START && !isPrepared) {
                            isBuffering = true
                        } else if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                            isBuffering = false
                        }
                        false
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        isBuffering = false
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
                // 同步續播位置（全螢幕關閉回來時）
                if (isPrepared && position > 0 && vv.currentPosition == 0 && !vv.isPlaying && isPlaying) {
                    runCatching { vv.seekTo(position) }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && !isPrepared) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(40.dp)
            )
        } else if (!isPlaying) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Pause,
                    contentDescription = "暫停",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 放大橫向按鈕（右上，不擋暫停鍵）
        IconButton(
            onClick = {
                videoView?.let { position = it.currentPosition }
                isFullscreen = true
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(32.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
        ) {
            Icon(
                Icons.Default.Fullscreen,
                contentDescription = "放大橫向播放",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FullscreenVideoContent(
    remoteUrl: String,
    localPath: String,
    startPosition: Int,
    onClose: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable {
                // 點擊切換播放/暫停由內層 VideoView 觸發？此處僅關閉轉圈用
            },
        contentAlignment = Alignment.Center
    ) {
        var vvRef by remember { mutableStateOf<VideoView?>(null) }
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
                        if (startPosition > 0) runCatching { seekTo(startPosition) }
                        mp.start()
                        isPlaying = true
                    }
                    setOnCompletionListener { isPlaying = false }
                    setOnErrorListener { _, _, _ -> isPlaying = false; true }
                    setOnClickListener {
                        if (isPlaying) { pause(); isPlaying = false } else { start(); isPlaying = true }
                    }
                    vvRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        DisposableEffect(Unit) {
            onDispose { vvRef?.stopPlayback(); vvRef = null }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "關閉全螢幕", tint = Color.White)
        }
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .clickable {
                        vvRef?.let { if (it.isPlaying) { it.pause(); isPlaying = false } else { it.start(); isPlaying = true } }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "播放", tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
    }
}
