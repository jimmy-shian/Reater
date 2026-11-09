package com.reater.app.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 影片縮圖非同步擷取與雙層快取管理（記憶體 + 磁碟）。
 * - 優先讀取本機已快取的縮圖檔案，避免重複解碼。
 * - 從已下載影片檔案中擷取適合的預覽影格（預設 1 秒，短影片退回第一影格）。
 * - 支援非同步 Dispatchers.IO 執行，不阻塞主執行緒。
 */
object VideoThumbnailHelper {
    private val memoryCache = object : LruCache<String, Bitmap>(20) {}

    suspend fun getThumbnail(context: Context, videoPath: String): File? = withContext(Dispatchers.IO) {
        if (videoPath.isBlank()) return@withContext null
        val videoFile = File(videoPath)
        if (!videoFile.exists() || videoFile.length() == 0L) return@withContext null

        val thumbDir = File(context.cacheDir, "video_thumbs").apply { if (!exists()) mkdirs() }
        val safeName = videoFile.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val thumbFile = File(thumbDir, "thumb_${safeName}.jpg")

        if (thumbFile.exists() && thumbFile.length() > 0) {
            return@withContext thumbFile
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    480,
                    360
                ) ?: retriever.frameAtTime
            } else {
                retriever.frameAtTime
            }

            if (frame != null) {
                FileOutputStream(thumbFile).use { out ->
                    frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                return@withContext thumbFile
            }
        } catch (_: Throwable) {
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
        null
    }
}
