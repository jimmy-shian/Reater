package com.reater.app.data.backup

import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
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
        file.inputStream().use { stream ->
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
        require(manifest.size <= MAX_MANIFEST_BYTES)
        require(files.size <= 100_000)
        require(files.values.sumOf { it.length() } + manifest.size <= MAX_ARCHIVE_BYTES)
        // The caller owns the destination stream.
        ZipOutputStream(object : FilterOutputStream(output) {
            override fun close() { flush() }
        }).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest)
            zip.closeEntry()
            for ((name, file) in files) {
                require(name != MANIFEST && validName(name))
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { copyBounded(it, zip, MAX_ARCHIVE_BYTES) }
                zip.closeEntry()
            }
            zip.finish()
            zip.flush()
        }
    }

    /** Extract only into a caller-created empty staging directory; never trust entry paths. */
    fun read(input: InputStream, directory: File): Map<String, File> {
        val files = linkedMapOf<String, File>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && validName(entry.name)) { "備份含無效路徑" }
                require(entry.name !in files && files.size < 100_001) { "備份檔案重複或過多" }
                val target = File(directory, entry.name)
                require(target.canonicalPath.startsWith(directory.canonicalPath + File.separator))
                target.parentFile?.mkdirs()
                val limit = minOf(MAX_ARCHIVE_BYTES - total,
                    if (entry.name == MANIFEST) MAX_MANIFEST_BYTES else MAX_ARCHIVE_BYTES)
                total += target.outputStream().use { copyBounded(zip, it, limit) }
                zip.closeEntry() // Also verifies the ZIP CRC.
                files[entry.name] = target
            }
        }
        require(MANIFEST in files) { "備份缺少 manifest.json" }
        return files
    }
}
