package com.reater.app.ui.player

import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 純 Kotlin 的 MP4/M4A metadata 寫入器：把 iTunes 標準
 * moov.udta.meta.ilst（©nam 曲名 / ©ART 歌手 / ©lyr 歌詞 / covr 封面圖）
 * 插入既有檔案 moov 的末端，Android MediaMetadataRetriever / 音樂 App /
 * 系統媒體掃描會自動讀出曲名、歌手、專輯封面與歌詞。
 *
 * 為何能安全插入（共用不搬移任何 mdat 樣本位址）：
 * - 插入位置在 moov 結尾之前，moov 後所有 box 整體平移，彼此相對位置不變。
 * - Threads DASH 音訊（2026-11 實測 129 個 fragment）全部 moof/traf/tfhd
 *   帶 default-base-is-moof（0x020000）、無絕對 base_data_offset
 *   → trun sample 相對自身 moof，平移後仍正確。
 * - sidx 的 first_offset / referenced_size 相對 box 端點（相對位址），同樣不受影響。
 * - 無 mfra/mfro（實測 top-level = ftyp/moov/sidx/moof…/mdat…）。
 */
object Mp4TagWriter {

    /** 要嵌入的 tags；coverJpeg 為 JPEG 原始位元組（covr data flags=13） */
    data class Tags(
        val title: String,
        val artist: String,
        val lyrics: String,
        val coverJpeg: ByteArray? = null
    )

    /**
     * 在 src 的 moov 末端插入 udta/meta/ilst，寫出成 out 檔。
     * @return true = 成功；false = 不支援的結構（找不到 moov / 已有 udta）。
     */
    fun embed(src: File, out: File, tags: Tags): Boolean {
        return try {
            val bytes = src.readBytes()
            val udta = buildUdtaBox(tags)
            val moov = findMoov(bytes) ?: return false
            val (moovStart, moovSize) = moov
            if (hasDirectChild(bytes, moovStart, moovSize, "udta")) return false

            val newMoovSize = moovSize + udta.size
            if (newMoovSize > Int.MAX_VALUE) return false
            // moov size 欄位改為擴大後的值（4 bytes big-endian）
            System.arraycopy(
                writeU32(newMoovSize), 0, bytes, moovStart, 4
            )

            val outFile = out
            outFile.outputStream().use { os ->
                os.write(bytes, 0, moovStart + moovSize) // 原檔頭 + moov（尺寸已改）
                os.write(udta)                            // 插入的 metadata box
                os.write(bytes, moovStart + moovSize, bytes.size - (moovStart + moovSize))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    // ---------- 結構走訪 ----------

    /** 頂層找 moov box，回傳 (offset, size)；找不到回 null */
    internal fun findMoov(bytes: ByteArray): Pair<Int, Int>? {
        var pos = 0
        while (pos + 8 <= bytes.size) {
            val size = readU32(bytes, pos).toInt()
            val type = readType(bytes, pos + 4)
            if (size < 8) return null // 非法尺寸，放棄
            if (type == "moov") return pos to size
            pos += size
        }
        return null
    }

    /** moov 是否已有頂層 udta 子 box（有則不重複插入，避免雙 udta） */
    internal fun hasDirectChild(bytes: ByteArray, moovStart: Int, moovSize: Int, childType: String): Boolean {
        var pos = moovStart + 8
        val end = moovStart + moovSize
        while (pos + 8 <= end) {
            val size = readU32(bytes, pos).toInt()
            if (size < 8) return false
            if (readType(bytes, pos + 4) == childType) return true
            pos += size
        }
        return false
    }

    // ---------- box 組裝 ----------

    /** udta.meta.ilst（©nam/©ART/©lyr/covr 非空者入列） */
    internal fun buildUdtaBox(tags: Tags): ByteArray {
        val items = mutableListOf<ByteArray>()
        if (tags.title.isNotBlank()) items.add(ilstEntry(nameBox("©nam"), textData(tags.title)))
        if (tags.artist.isNotBlank()) items.add(ilstEntry(nameBox("©ART"), textData(tags.artist)))
        if (tags.lyrics.isNotBlank()) items.add(ilstEntry(nameBox("©lyr"), textData(tags.lyrics)))
        if (tags.coverJpeg != null && tags.coverJpeg.isNotEmpty()) {
            items.add(ilstEntry(nameBox("covr"), dataBox(flags = 13, payload = tags.coverJpeg)))
        }
        if (items.isEmpty()) throw IllegalStateException("no tags to embed")

        // hdlr：version/flags 0 + pre_defined 0 + 'mdir' + 'appl' + reserved 12 + NUL
        val hdlrPayload = ByteArray(0) + writeU32(0) + writeU32(0) + "mdir".ascii() + "appl".ascii() +
            ByteArray(12) + byteArrayOf(0)
        val hdlr = box("hdlr", hdlrPayload)
        val ilstPayload = items.fold(ByteArray(0)) { acc, entry -> acc + entry }
        val ilst = box("ilst", ilstPayload)
        // meta 是 full box：version/flags 4 bytes 緊接 type 之後
        val metaPayload = ByteArray(4) + hdlr + ilst
        val meta = box("meta", metaPayload)
        return box("udta", meta)
    }

    /** ilst 子項目包裝：[size][type 4B][data box] */
    private fun ilstEntry(typeBytes: ByteArray, data: ByteArray): ByteArray {
        val size = 8 + data.size
        return writeU32(size) + typeBytes + data
    }

    /** data box：[size]['data'][0][flags=1 UTF-8 / 13 JPEG][locale 4B 0][payload] */
    private fun textData(value: String): ByteArray =
        dataBox(flags = 1, payload = value.toByteArray(StandardCharsets.UTF_8))

    private fun dataBox(flags: Int, payload: ByteArray): ByteArray {
        val size = 16 + payload.size
        return writeU32(size) + "data".ascii() + byteArrayOf(0, 0, 0, flags.toByte()) + ByteArray(4) + payload
    }

    /** box 名稱（© 系列等非 ASCII 第一次節為 0xA9） */
    private fun nameBox(keyword: String): ByteArray {
        require(keyword.length == 4)
        val b = ByteArray(4)
        for (i in 0 until 4) b[i] = keyword[i].code.toByte()
        return b
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        return writeU32(size) + type.ascii() + payload
    }

    // ---------- 位元組工具 ----------

    internal fun readU32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun readType(b: ByteArray, o: Int): String =
        String(b, o, 4, StandardCharsets.ISO_8859_1)

    internal fun writeU32(v: Int): ByteArray = byteArrayOf(
        ((v ushr 24) and 0xFF).toByte(),
        ((v ushr 16) and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte()
    )

    private fun String.ascii(): ByteArray = toByteArray(StandardCharsets.ISO_8859_1)

    /** 拼接 byte arrays（+ 運算子） */
    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val out = ByteArray(size + other.size)
        System.arraycopy(this, 0, out, 0, size)
        System.arraycopy(other, 0, out, size, other.size)
        return out
    }
}
