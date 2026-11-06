package com.reater.app.data.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 下載即時進度資訊 */
data class DownloadProgress(
    val bytesRead: Long,
    val totalBytes: Long,
    val percentage: Int, // 0..100
    val speedBytesPerSec: Long,
    val etaSeconds: Long
)

@Singleton
class MediaDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaDir: File by lazy {
        File(context.filesDir, "threads_media").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Downloads remote media (image/video) and saves it locally.
     * Returns the absolute path of the downloaded file, or "" on failure/skip.
     *
     * 支援即時下載進度回傳（百分比與剩餘時間倒數）。
     */
    suspend fun downloadMedia(
        remoteUrl: String,
        shortcode: String,
        index: Int,
        referer: String? = null,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): String {
        if (remoteUrl.isBlank()) return ""
        // HLS 串流清單：OkHttp 直接存只會存到文字檔，跳過
        if (remoteUrl.contains(".m3u8", ignoreCase = true)) return ""
        return withContext(Dispatchers.IO) {
            try {
                // Determine file extension from path (ignore query string)
                val pathOnly = remoteUrl.substringBefore("?").lowercase()
                val ext = when {
                    pathOnly.contains(".mp4") || remoteUrl.contains(".mp4", ignoreCase = true) -> "mp4"
                    pathOnly.contains(".mov") -> "mov"
                    pathOnly.contains(".webp") -> "webp"
                    pathOnly.contains(".png") -> "png"
                    pathOnly.contains(".gif") -> "gif"
                    else -> "jpg"
                }
                val safeCode = shortcode.replace(Regex("[^A-Za-z0-9_-]"), "_")
                    .ifBlank { "media" }.take(32)
                val targetFile = File(mediaDir, "${safeCode}_${index}_${System.currentTimeMillis() % 10000}.$ext")

                val reqBuilder = Request.Builder()
                    .url(remoteUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                    .header("Accept", "image/avif,image/webp,image/*,video/*,*/*;q=0.8")
                if (!referer.isNullOrBlank()) {
                    reqBuilder.header("Referer", referer)
                }

                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext ""
                    val body = response.body ?: return@withContext ""
                    val totalBytes = body.contentLength()
                    // 內容太小（<1KB）多半是擋掉後的錯誤頁，不存
                    if (totalBytes in 1..1023) return@withContext ""

                    val buffer = ByteArray(8192)
                    var bytesReadTotal = 0L
                    val startTime = System.currentTimeMillis()
                    var lastProgressTime = 0L

                    val source = body.byteStream()
                    FileOutputStream(targetFile).use { outStream ->
                        var read: Int
                        while (source.read(buffer).also { read = it } != -1) {
                            outStream.write(buffer, 0, read)
                            bytesReadTotal += read
                            val now = System.currentTimeMillis()
                            if (now - lastProgressTime >= 100 || (totalBytes > 0 && bytesReadTotal == totalBytes)) {
                                lastProgressTime = now
                                val elapsedSec = ((now - startTime).coerceAtLeast(1L)) / 1000.0
                                val speed = (bytesReadTotal / elapsedSec).toLong()
                                val percent = if (totalBytes > 0) {
                                    ((bytesReadTotal * 100) / totalBytes).toInt().coerceIn(0, 100)
                                } else 0
                                val remainingBytes = (totalBytes - bytesReadTotal).coerceAtLeast(0)
                                val eta = if (speed > 0) remainingBytes / speed else 0L
                                onProgress?.invoke(
                                    DownloadProgress(
                                        bytesRead = bytesReadTotal,
                                        totalBytes = totalBytes,
                                        percentage = percent,
                                        speedBytesPerSec = speed,
                                        etaSeconds = eta
                                    )
                                )
                            }
                        }
                    }
                    if (targetFile.length() < 1024) {
                        targetFile.delete()
                        return@withContext ""
                    }
                    targetFile.absolutePath
                }
            } catch (e: Exception) {
                // Fallback to empty or remoteUrl on failure
                ""
            }
        }
    }
}
