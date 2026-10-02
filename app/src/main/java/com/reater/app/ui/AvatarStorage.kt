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
    private const val ORIG_PREFIX = "custom_avatar_orig_"
    const val MAX_HISTORY = 8
    const val OUTPUT_SIZE = 512

    fun avatarDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** 相容舊版單檔：新版以時間戳檔為主，舊檔若存在視為歷史之一。 */
    fun avatarFile(context: Context): File =
        File(avatarDir(context), LEGACY_FILE_NAME)

    fun isAvatarFile(f: File): Boolean {
        if (f.name == LEGACY_FILE_NAME) return true
        if (f.name.startsWith(ORIG_PREFIX)) return true
        if (!f.name.startsWith(PREFIX)) return false
        return f.name.endsWith(".jpg") || f.name.endsWith(".png")
    }

    /** 是否為原始檔（完整保留、不可直接當頭像顯示，只供裁切器讀取）。 */
    fun isOriginalFile(f: File): Boolean =
        f.name.startsWith(ORIG_PREFIX) &&
            (f.name.endsWith(".jpg") || f.name.endsWith(".png"))

    /** 是否為成品檔（裁切後/直接匯入，可顯示、可回選；原始檔除外）。 */
    fun isDisplayFile(f: File): Boolean {
        if (f.name == LEGACY_FILE_NAME) return true
        if (f.name.startsWith(ORIG_PREFIX)) return false
        if (!f.name.startsWith(PREFIX)) return false
        return f.name.endsWith(".jpg") || f.name.endsWith(".png")
    }

    /** 由新到舊排列的歷史檔案（含舊版單檔遷移對象）。 */
    fun listHistoryFiles(context: Context): List<File> {
        val dir = avatarDir(context)
        val files = dir.listFiles()?.filter { isDisplayFile(it) && it.length() > 0 }
            ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }
    }

    /** 從成品檔名抽出時間戳；失敗回傳 null。 */
    fun extractTs(name: String): Long? {
        return try {
            var core = name
            if (core.startsWith(ORIG_PREFIX)) core = core.removePrefix(ORIG_PREFIX)
            else if (core.startsWith(PREFIX)) core = core.removePrefix(PREFIX)
            else if (core == LEGACY_FILE_NAME) return null
            else return null
            core = core.substringBeforeLast(".")
            // 相容舊的 _orig 後綴寫法（若曾出現）
            core = core.removeSuffix("_orig")
            core.toLong()
        } catch (_: Exception) {
            null
        }
    }

    /** 成品檔對應的原始檔（同 ts 配對）；不存在回傳 null。 */
    fun pairedOriginalFile(context: Context, displayPath: String?): File? {
        if (displayPath.isNullOrBlank()) return null
        return try {
            val ts = extractTs(File(displayPath).name) ?: return null
            val cand = File(avatarDir(context), "$ORIG_PREFIX$ts.jpg")
            // 相容 PNG 原始檔
            val candPng = File(avatarDir(context), "$ORIG_PREFIX$ts.png")
            when {
                cand.exists() && cand.length() > 0 -> cand
                candPng.exists() && candPng.length() > 0 -> candPng
                else -> null
            }
        } catch (_: Exception) {
            null
        }
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

    /** 裁切編輯器產出的正方形 Bitmap 存成新的時間戳檔，回傳絕對路徑（PNG 保留透明 letterbox）。 */
    fun saveBitmap(context: Context, bitmap: Bitmap): String? {
        return try {
            val dir = avatarDir(context)
            val dest = File(dir, "$PREFIX${System.currentTimeMillis()}.png")
            dest.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
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

    /**
     * 新流程第一步：把 picker 的 content Uri 原樣存成原始檔，回傳絕對路徑。
     * 原始檔完整保留全圖（不裁切），只供裁切器讀取；檔名與後續成品共用同一 ts 配對。
     * 呼叫方需先產生 ts（System.currentTimeMillis()）並在存成品時沿用。
     */
    fun saveOriginal(context: Context, sourceUri: Uri, ts: Long): String? {
        return try {
            val dir = avatarDir(context)
            migrateLegacyIfNeeded(dir)
            val dest = File(dir, "$ORIG_PREFIX$ts.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            if (dest.exists() && dest.length() > 0) {
                dest.setLastModified(ts)
                dest.absolutePath
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 新流程第二步：裁切 Bitmap 存成與原始檔同 ts 的成品檔，回傳絕對路徑。
     * 原始檔保留不刪；只裁多餘的成品配對（原始＋成品一起刪）。
     */
    fun saveCroppedWithTs(context: Context, bitmap: Bitmap, ts: Long): String? {
        return try {
            val dir = avatarDir(context)
            val dest = File(dir, "$PREFIX$ts.png")
            dest.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (dest.exists() && dest.length() > 0) {
                dest.setLastModified(ts)
                // 原始檔時間同步，避免被誤判為孤兒
                File(dir, "$ORIG_PREFIX$ts.jpg").takeIf { it.exists() }?.setLastModified(ts)
                File(dir, "$ORIG_PREFIX$ts.png").takeIf { it.exists() }?.setLastModified(ts)
                prunePairsOverflow(dir)
                dest.absolutePath
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 重編流程：沿用舊原始檔複製一份新 ts，再存新成品。
     * 回傳 Pair(新成品路徑, 新原始路徑)；任一失敗回傳 null。
     */
    fun duplicateOriginalForReEdit(context: Context, origFile: File, newTs: Long): File? {
        return try {
            val dir = avatarDir(context)
            val ext = if (origFile.name.endsWith(".png")) ".png" else ".jpg"
            val dest = File(dir, "$ORIG_PREFIX$newTs$ext")
            origFile.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            if (dest.exists() && dest.length() > 0) {
                dest.setLastModified(newTs)
                dest
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
            // 成品連同配對原始檔一起刪，避免孤兒膨脹
            if (isDisplayFile(f)) deletePairFor(f)
            else f.delete()
            true
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
            val files = dir.listFiles()?.filter { isDisplayFile(it) }
                ?.sortedByDescending { it.lastModified() } ?: return
            files.drop(MAX_HISTORY).forEach { runCatching { deletePairFor(it) } }
        } catch (_: Exception) {
        }
    }

    /** 以成品數量為準裁剪：超出的成品連同其配對原始檔一起刪；孤兒原始檔順手清理。 */
    private fun prunePairsOverflow(dir: File) {
        try {
            val displays = dir.listFiles()?.filter { isDisplayFile(it) }
                ?.sortedByDescending { it.lastModified() } ?: return
            displays.drop(MAX_HISTORY).forEach { runCatching { deletePairFor(it) } }
            // 孤兒原始檔：沒有對應成品且超過上限，只保留最新的 MAX_HISTORY 個原始檔
            val liveTs = displays.take(MAX_HISTORY).mapNotNull { extractTs(it.name) }.toSet()
            val orphans = dir.listFiles()?.filter { isOriginalFile(it) && it.length() > 0 }
                ?.filter { extractTs(it.name) !in liveTs }
                ?.sortedByDescending { it.lastModified() } ?: return
            // 保留與現存成品同 ts 的原始檔（重編需要）；其餘孤兒全清避免膨脹
            orphans.forEach { runCatching { it.delete() } }
        } catch (_: Exception) {
        }
    }

    /** 刪除成品及其配對原始檔。 */
    fun deletePairFor(displayFile: File) {
        runCatching { displayFile.delete() }
        val ts = extractTs(displayFile.name) ?: return
        val dir = displayFile.parentFile ?: return
        runCatching { File(dir, "$ORIG_PREFIX$ts.jpg").delete() }
        runCatching { File(dir, "$ORIG_PREFIX$ts.png").delete() }
    }
}
