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
     * 影音抓取規則（配合 Threads CDN 特性）：
     *  - .m3u8 是串流 manifest（需切片下載），不直接存檔 → 跳過，避免存壞檔；
     *  - 帶 Referer + 桌面 UA，提高 CDN 直連成功率；
     *  - 副檔名從 path 判斷（忽略 query），未知圖片預設 jpg。
     */
    suspend fun downloadMedia(remoteUrl: String, shortcode: String, index: Int): String {
        if (remoteUrl.isBlank()) return ""
        // HLS 串流清單：OkHttp 直接存只會存到文字檔，跳過（UI 會提示去 Threads 看）
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

                val request = Request.Builder()
                    .url(remoteUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                    .header("Referer", "https://www.threads.com/")
                    .header("Accept", "image/avif,image/webp,image/*,video/*,*/*;q=0.8")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext ""
                    val body = response.body ?: return@withContext ""
                    // 內容太小（<1KB）多半是擋掉後的錯誤頁，不存
                    if (body.contentLength() in 1..1023) return@withContext ""
                    FileOutputStream(targetFile).use { outStream ->
                        body.byteStream().copyTo(outStream)
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
