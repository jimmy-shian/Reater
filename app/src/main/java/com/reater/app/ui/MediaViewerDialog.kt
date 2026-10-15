package com.reater.app.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
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
 * 全螢幕媒體檢視器：點擊放大、左右切換、X 關閉、圖片下載到相簿、影片外部開啟。
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
    val coroutineScope = rememberCoroutineScope()
    var index by remember(startIndex) { mutableIntStateOf(startIndex.coerceIn(0, media.size - 1)) }
    val current = media[index]

    DisposableEffect(Unit) {
        com.reater.app.ui.player.VideoPlaybackManager.pauseAll()
        onDispose {
            com.reater.app.ui.player.VideoPlaybackManager.pauseAll()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogWindow = remember(view) { com.reater.app.ui.player.findDialogWindow(view) }
        SideEffect {
            com.reater.app.ui.player.applyDialogWindowImmersive(dialogWindow)
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 媒體本體（置中、延伸至邊緣，沉浸式）：影片內建播放，圖片維持縮放檢視
                androidx.compose.runtime.key(index, current.remoteUrl, current.localPath) {
                    if (current.isVideo) {
                        com.reater.app.ui.player.InlineVideoPlayer(
                            remoteUrl = current.remoteUrl,
                            localPath = current.localPath,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = current.bestSource(),
                            contentDescription = "Media ${index + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                // 頂列：計數 + X 關閉（shared fullscreenTopBarModifier，避開狀態列與瀏海）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .then(com.reater.app.ui.player.fullscreenTopBarModifier(isLandscape = false)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1} / ${media.size}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
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

                // 左右切換（增加水平邊距，避免貼邊）
                if (media.size > 1) {
                    if (index > 0) {
                        IconButton(
                            onClick = { index-- },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "上一張",
                                tint = Color.White
                            )
                        }
                    }
                    if (index < media.size - 1) {
                        IconButton(
                            onClick = { index++ },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 12.dp)
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

                // 底列：下載（圖片/影片）與外部開啟（影片）
                // 使用 shared fullscreenBottomBarModifier，保底 68dp 避開三鍵導覽列 (48dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .then(com.reater.app.ui.player.fullscreenBottomBarModifier(isLandscape = false)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (current.isVideo) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        com.reater.app.ui.player.saveMediaToGalleryWithFeedback(
                                            context = context,
                                            isVideo = true,
                                            localPath = current.localPath,
                                            remoteUrl = current.remoteUrl
                                        )
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("下載影片")
                            }

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
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    com.reater.app.ui.player.saveMediaToGalleryWithFeedback(
                                        context = context,
                                        isVideo = false,
                                        localPath = current.localPath,
                                        remoteUrl = current.remoteUrl
                                    )
                                }
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
 * 把圖片存到系統相簿（Pictures/Reater）。
 * API 29+ 走 MediaStore（免權限）；以下版本存 App 外部 Pictures 並提示路徑。
 */
fun saveImageToGallery(context: Context, media: ViewerMedia): Boolean {
    val src: File = media.localFileOrNull() ?: return false
    return com.reater.app.ui.player.copyFileToGallery(context, src, isVideo = false)
}
