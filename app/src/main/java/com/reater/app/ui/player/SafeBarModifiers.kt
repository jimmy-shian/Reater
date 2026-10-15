package com.reater.app.ui.player

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 尋找 Compose Dialog 所在的真實 Window。
 */
fun findDialogWindow(view: View): Window? {
    if (view is DialogWindowProvider) return view.window
    var parent = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }
    var ctx = view.context
    while (ctx is ContextWrapper) {
        if (ctx is DialogWindowProvider) return ctx.window
        if (ctx is Activity) return ctx.window
        ctx = ctx.baseContext
    }
    return null
}

/**
 * 將 Dialog Window 設置為全螢幕沉浸式（透明系統列、延伸至瀏海區域）。
 */
fun applyDialogWindowImmersive(window: Window?) {
    window?.let { win ->
        WindowCompat.setDecorFitsSystemWindows(win, false)
        win.statusBarColor = android.graphics.Color.TRANSPARENT
        win.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            win.isNavigationBarContrastEnforced = false
            win.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            win.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    }
}

/**
 * 取得影片真實寬高（考慮 EXIF/MP4 轉向角度）。
 * 若轉向為 90 或 270 度，寬高對調。
 */
fun getVideoDimensions(localPath: String): Pair<Int, Int>? {
    if (localPath.isBlank()) return null
    val file = File(localPath)
    if (!file.exists() || file.length() == 0L) return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        val w = wStr?.toIntOrNull() ?: 0
        val h = hStr?.toIntOrNull() ?: 0
        val rot = rotStr?.toIntOrNull() ?: 0
        if (w > 0 && h > 0) {
            if (rot == 90 || rot == 270) Pair(h, w) else Pair(w, h)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * 直向 / 橫向全螢幕頂部列樣式。
 *
 * 直向全螢幕：左右均衡，頂部保證在狀態列之下。
 * - top = max(實際 top inset + 8dp, 44dp)
 * - start/end = max(實際左右 inset + 16dp, 16dp)
 *
 * 橫向全螢幕：右側充分讓出導覽列返回鍵與相機切角。
 * - top = max(實際 top inset + 8dp, 28dp)
 * - start = max(實際 left inset + 12dp, 16dp)
 * - end = max(實際 right inset + 24dp, 52dp)
 */
@Composable
fun fullscreenTopBarModifier(isLandscape: Boolean): Modifier {
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val topInset = safe.calculateTopPadding()
    val leftInset = safe.calculateLeftPadding(layoutDirection)
    val rightInset = safe.calculateRightPadding(layoutDirection)
    return if (isLandscape) {
        val top = maxOf(topInset + 8.dp, 28.dp)
        val left = maxOf(leftInset + 12.dp, 16.dp)
        val right = maxOf(rightInset + 24.dp, 52.dp)
        Modifier.padding(top = top, bottom = 8.dp, start = left, end = right)
    } else {
        val top = maxOf(topInset + 8.dp, 44.dp)
        val left = maxOf(leftInset + 16.dp, 16.dp)
        val right = maxOf(rightInset + 16.dp, 16.dp)
        Modifier.padding(top = top, bottom = 8.dp, start = left, end = right)
    }
}

/**
 * 直向 / 橫向全螢幕底部控制列樣式。
 *
 * 直向全螢幕：保證在三鍵導覽列 (48dp) 或手勢條之上，預留舒適呼吸空間。
 * - bottom = max(實際 bottom inset + 20dp 呼吸, 68dp 保底)
 * - start/end = max(實際左右 inset + 16dp, 16dp)
 *
 * 橫向全螢幕：
 * - bottom = max(實際 bottom inset + 20dp, 40dp) 上抬避免貼邊
 * - start = max(實際 left inset + 12dp, 16dp)
 * - end = max(實際 right inset + 24dp, 52dp) 充分避開三鍵導覽列
 */
@Composable
fun fullscreenBottomBarModifier(isLandscape: Boolean): Modifier {
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val bottomInset = safe.calculateBottomPadding()
    val leftInset = safe.calculateLeftPadding(layoutDirection)
    val rightInset = safe.calculateRightPadding(layoutDirection)
    return if (isLandscape) {
        val bottom = maxOf(bottomInset + 20.dp, 40.dp)
        val left = maxOf(leftInset + 12.dp, 16.dp)
        val right = maxOf(rightInset + 24.dp, 52.dp)
        Modifier.padding(start = left, end = right, top = 4.dp, bottom = bottom)
    } else {
        val bottom = maxOf(bottomInset + 20.dp, 68.dp)
        val left = maxOf(leftInset + 16.dp, 16.dp)
        val right = maxOf(rightInset + 16.dp, 16.dp)
        Modifier.padding(start = left, end = right, top = 4.dp, bottom = bottom)
    }
}

/**
 * 將圖片或影片儲存到系統相簿（Pictures/Reater 或 Movies/Reater）。
 * 若已有本機檔案直接轉存；若僅有遠端網址則於背景下載後轉存，並以 Toast 提示進度。
 */
suspend fun saveMediaToGalleryWithFeedback(
    context: Context,
    isVideo: Boolean,
    localPath: String,
    remoteUrl: String
) {
    val localFile = if (localPath.isNotBlank()) File(localPath).takeIf { it.exists() && it.length() > 0 } else null

    if (localFile == null && remoteUrl.isBlank()) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "無有效媒體檔案可儲存", Toast.LENGTH_SHORT).show()
        }
        return
    }

    if (localFile != null) {
        val ok = withContext(Dispatchers.IO) {
            copyFileToGallery(context, localFile, isVideo)
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, if (ok) "已儲存到相簿" else "儲存失敗", Toast.LENGTH_SHORT).show()
        }
        return
    }

    // 需由遠端網址下載
    withContext(Dispatchers.Main) {
        Toast.makeText(context, if (isVideo) "正在下載影片至相簿..." else "正在下載圖片至相簿...", Toast.LENGTH_SHORT).show()
    }

    val ok = withContext(Dispatchers.IO) {
        val downloaded = downloadToTemp(context, remoteUrl, isVideo) ?: return@withContext false
        try {
            copyFileToGallery(context, downloaded, isVideo)
        } finally {
            runCatching { downloaded.delete() }
        }
    }

    withContext(Dispatchers.Main) {
        Toast.makeText(context, if (ok) "已儲存到相簿" else "下載或儲存失敗，請檢查網路連線", Toast.LENGTH_SHORT).show()
    }
}

fun copyFileToGallery(context: Context, src: File, isVideo: Boolean): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ext = src.extension.ifBlank { if (isVideo) "mp4" else "jpg" }
            val mime = if (isVideo) {
                when (ext.lowercase()) {
                    "webm" -> "video/webm"
                    "mkv" -> "video/x-matroska"
                    "3gp" -> "video/3gpp"
                    else -> "video/mp4"
                }
            } else {
                when (ext.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    else -> "image/jpeg"
                }
            }
            val relativeDir = if (isVideo) "${Environment.DIRECTORY_MOVIES}/Reater" else "${Environment.DIRECTORY_PICTURES}/Reater"
            val contentUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "reater_${System.currentTimeMillis()}.$ext")
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(contentUri, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val pubDir = Environment.getExternalStoragePublicDirectory(
                if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            )
            val dir = File(pubDir, "Reater").apply { if (!exists()) mkdirs() }
            val ext = src.extension.ifBlank { if (isVideo) "mp4" else "jpg" }
            val dst = File(dir, "reater_${System.currentTimeMillis()}.$ext")
            src.copyTo(dst, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(dst.absolutePath), null, null)
            true
        }
    } catch (_: Exception) {
        false
    }
}

private fun downloadToTemp(context: Context, url: String, isVideo: Boolean): File? {
    return try {
        val ext = when {
            url.contains(".mp4", ignoreCase = true) -> "mp4"
            url.contains(".mov", ignoreCase = true) -> "mov"
            url.contains(".webp", ignoreCase = true) -> "webp"
            url.contains(".png", ignoreCase = true) -> "png"
            url.contains(".gif", ignoreCase = true) -> "gif"
            isVideo -> "mp4"
            else -> "jpg"
        }
        val tmp = File(context.cacheDir, "reater_dl_${System.currentTimeMillis()}.$ext")
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            tmp.outputStream().use { body.byteStream().copyTo(it) }
        }
        if (tmp.exists() && tmp.length() > 0) tmp else null
    } catch (_: Exception) {
        null
    }
}

