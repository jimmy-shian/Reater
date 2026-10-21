package com.reater.app.data.backup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Streaming container: media never needs to fit into memory. */
object ReaterArchive {
    const val MANIFEST = "manifest.json"
    const val MAX_MANIFEST_BYTES = 50L * 1024 * 1024
    const val MAX_ARCHIVE_BYTES = 10L * 1024 * 1024 * 1024
    private val assetName = Regex("(?:media/[0-9]+\\.[a-z0-9]+|avatars/[a-zA-Z0-9_.]+)")

    fun validName(name: String): Boolean = name == MANIFEST ||
        (assetName.matches(name) && !name.contains(".."))

    fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "備份超過大小上限" }
            output.write(buffer, 0, read)
        }
        return total
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(64 * 1024).use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun write(output: OutputStream, manifest: ByteArray, files: Map<String, File>) {
        require(manifest.size <= MAX_MANIFEST_BYTES) { "manifest 超過大小上限" }
        require(files.size <= 100_000) { "備份檔案數量過多" }
        require(files.values.sumOf { it.length() } + manifest.size <= MAX_ARCHIVE_BYTES) { "備份總量超過大小上限" }

        val bufferedOut = if (output is BufferedOutputStream) output else BufferedOutputStream(output, 64 * 1024)
        val zip = ZipOutputStream(bufferedOut).apply {
            setLevel(Deflater.BEST_SPEED)
        }
        try {
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest)
            zip.closeEntry()
            for ((name, file) in files) {
                require(name != MANIFEST && validName(name)) { "無效的備份檔名: $name" }
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().buffered(64 * 1024).use { copyBounded(it, zip, MAX_ARCHIVE_BYTES) }
                zip.closeEntry()
            }
            zip.finish()
            zip.flush()
        } finally {
            bufferedOut.flush()
        }
    }

    /** Extract into staging directory; salvages manifest even if subsequent media is truncated. */
    fun read(input: InputStream, directory: File): Map<String, File> {
        val files = linkedMapOf<String, File>()
        var total = 0L
        val bufferedInput = if (input is BufferedInputStream) input else BufferedInputStream(input, 64 * 1024)
        ZipInputStream(bufferedInput).use { zip ->
            while (true) {
                val entry: ZipEntry = try {
                    zip.nextEntry ?: break
                } catch (e: Exception) {
                    // 若串流尾端損壞但前面已有 manifest，終止解壓並容錯返回已解出的檔案清單
                    if (MANIFEST in files) break else throw e
                }
                if (entry.isDirectory || !validName(entry.name)) {
                    continue
                }
                if (entry.name in files || files.size >= 100_001) {
                    continue
                }
                val target = File(directory, entry.name)
                if (!target.canonicalPath.startsWith(directory.canonicalPath + File.separator)) {
                    continue
                }
                target.parentFile?.mkdirs()
                val limit = minOf(
                    MAX_ARCHIVE_BYTES - total,
                    if (entry.name == MANIFEST) MAX_MANIFEST_BYTES else MAX_ARCHIVE_BYTES
                )
                try {
                    total += target.outputStream().buffered(64 * 1024).use { copyBounded(zip, it, limit) }
                    zip.closeEntry()
                    files[entry.name] = target
                } catch (e: Exception) {
                    target.delete()
                    if (entry.name == MANIFEST) throw e
                    // 媒體檔案讀取中斷，若 manifest 已提取則容錯結束，拯救核心文章資料
                    break
                }
            }
        }
        require(MANIFEST in files) { "備份缺少 manifest.json，檔案可能已損壞或非有效備份" }
        return files
    }
}
