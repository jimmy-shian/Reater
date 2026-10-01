package com.reater.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * 自訂頭像持久化：相簿 picker 回傳的 content:// URI 只是暫時授權，
 * 關閉重開 App 就會失效（圖片不見）。一律拷貝到 App 內部 filesDir，
 * DataStore 只存內部檔案絕對路徑，重開依然可讀。
 *
 * 歷史版：
 * - 每次匯入（含裁切後存檔）都產生唯一檔名 custom_avatar_<millis>.jpg，
 *   DataStore 寫入不同字串 -> Flow 一定發出新值，Compose 內外同步重組；
 *   Coil 也不會命中同一路徑的舊快取（根治「換圖不刷新」）。
 * - 保留最近 MAX_HISTORY 張，可在「自訂相片頭貼」頁簽回選過往圖片。
 * - 切回內建圖示只清除 current，不刪檔（歷史保留）。
 */
object AvatarStorage {
    private const val DIR_NAME = "avatar"
    private const val LEGACY_FILE_NAME = "custom_avatar.jpg"
    private const val PREFIX = "custom_avatar_"
    const val MAX_HISTORY = 8
    const val OUTPUT_SIZE = 512

    fun avatarDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** 相容舊版單檔：新版以時間戳檔為主，舊檔若存在視為歷史之一。 */
    fun avatarFile(context: Context): File =
        File(avatarDir(context), LEGACY_FILE_NAME)

    fun isAvatarFile(f: File): Boolean {
        if (f.name == LEGACY_FILE_NAME) return true
        return f.name.startsWith(PREFIX) && f.name.endsWith(".jpg")
    }

    /** 由新到舊排列的歷史檔案（含舊版單檔遷移對象）。 */
    fun listHistoryFiles(context: Context): List<File> {
        val dir = avatarDir(context)
        val files = dir.listFiles()?.filter { isAvatarFile(it) && it.length() > 0 }
            ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }
    }

    fun listHistoryPaths(context: Context): List<String> =
        listHistoryFiles(context).map { it.absolutePath }

    /**
     * 把 picker 的 content Uri 拷貝成新的時間戳內部檔，回傳絕對路徑；失敗回傳 null。
     * 不再刪除其他歷史檔，只做上限裁剪。
     */
    fun saveFromPicker(context: Context, sourceUri: Uri): String? {
        return try {
            val dir = avatarDir(context)
            migrateLegacyIfNeeded(dir)
            val dest = File(dir, "$PREFIX${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            if (dest.exists() && dest.length() > 0) {
                dest.setLastModified(System.currentTimeMillis())
                pruneOverflow(dir)
                dest.absolutePath
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /** 裁切編輯器產出的正方形 Bitmap 存成新的時間戳檔，回傳絕對路徑。 */
    fun saveBitmap(context: Context, bitmap: Bitmap): String? {
        return try {
            val dir = avatarDir(context)
            val dest = File(dir, "$PREFIX${System.currentTimeMillis()}.jpg")
            dest.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (dest.exists() && dest.length() > 0) {
                dest.setLastModified(System.currentTimeMillis())
                pruneOverflow(dir)
                dest.absolutePath
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /** 對單張裁切圖做取樣解碼，避免超大原圖 OOM；回傳可用於編輯的 Bitmap。 */
    fun decodeForEdit(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            while ((w / sample) > maxSide || (h / sample) > maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun decodeFile(path: String?, maxSide: Int = 1600): Bitmap? {
        if (path.isNullOrBlank()) return null
        return try {
            val f = File(path)
            if (!f.exists() || f.length() <= 0) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while ((bounds.outWidth / sample) > maxSide || (bounds.outHeight / sample) > maxSide) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) {
            null
        }
    }

    fun deletePath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return try {
            val f = File(path)
            if (!f.isAbsolute) return false
            f.delete()
        } catch (_: Exception) {
            false
        }
    }

    fun clear(context: Context) {
        runCatching {
            avatarDir(context).listFiles()
                ?.filter { isAvatarFile(it) }
                ?.forEach { runCatching { it.delete() } }
        }
    }

    /**
     * 把 DataStore 存的字串解析成 Coil 可載入的 model：
     * - 內部檔案絕對路徑且存在 -> File
     * - 舊版 content://（未遷移、重開已失效）-> 照樣回傳讓 Coil 試讀，失敗由 error fallback 接手
     * - 檔案遺失 -> null（呼叫方顯示內建圖示）
     */
    fun resolveModel(context: Context, stored: String?): Any? {
        if (stored.isNullOrBlank()) return null
        // content:// 舊資料：直接回傳，讀不到時 AsyncImage error fallback 會顯示圖示
        if (stored.startsWith("content://") || stored.startsWith("file://")) return stored
        val f = File(stored)
        if (f.isAbsolute) {
            return if (f.exists() && f.length() > 0) f else null
        }
        return stored
    }

    /** Coil 快取鍵：路徑 + 修改時間，避免同路徑覆寫時命中舊快取。 */
    fun cacheKey(stored: String?): String? {
        if (stored.isNullOrBlank()) return null
        return try {
            if (stored.startsWith("content://") || stored.startsWith("file://")) stored
            else {
                val f = File(stored)
                if (f.exists()) "${f.absolutePath}@${f.lastModified()}" else stored
            }
        } catch (_: Exception) {
            stored
        }
    }

    /** 開機自檢：內部檔遺失卻還存著路徑 -> 回傳 false，呼叫方可清除該筆避免空白圖 */
    fun isStoredValid(context: Context, stored: String?): Boolean {
        if (stored.isNullOrBlank()) return false
        if (stored.startsWith("content://")) {
            // 舊格式：試讀權限，讀不到即視為失效
            return try {
                context.contentResolver.openInputStream(Uri.parse(stored))?.close()
                true
            } catch (_: Exception) {
                false
            }
        }
        if (stored.startsWith("file://")) return true
        val f = File(stored)
        return f.exists() && f.length() > 0
    }

    /** 舊版 custom_avatar.jpg 更名為時間戳檔，避免與新檔混淆並納入歷史。 */
    private fun migrateLegacyIfNeeded(dir: File) {
        try {
            val legacy = File(dir, LEGACY_FILE_NAME)
            if (legacy.exists() && legacy.length() > 0) {
                val renamed = File(dir, "$PREFIX${legacy.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()}.jpg")
                if (!renamed.exists()) runCatching { legacy.renameTo(renamed) }
            }
        } catch (_: Exception) {
        }
    }

    private fun pruneOverflow(dir: File) {
        try {
            val files = dir.listFiles()?.filter { isAvatarFile(it) }
                ?.sortedByDescending { it.lastModified() } ?: return
            files.drop(MAX_HISTORY).forEach { runCatching { it.delete() } }
        } catch (_: Exception) {
        }
    }
}
