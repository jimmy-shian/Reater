package com.reater.app.domain

/**
 * 字幕排版（純 Kotlin，不含 Android 相依，可 JVM 單元測試）。
 *
 * 核心約束：**每一行文字量測寬度必 ≤ maxWidth**（不可超出影片畫布寬度）。
 * - 換行點：空白（含 \t、全形空白 U+3000）優先 → 適合西文詞邊界；
 *   單一不可斷詞（CJK 連字、長網址等）超寬時逐字元斷行 → 適合中文。
 * - 明確 \n 視為強制換行；行首行尾空白壓掉。
 * - [fit] 由大至小嘗試字級，取第一個「換行後行數 ≤ maxLines」的大小；
 *   全部失敗時以 minSize 換行並截斷（尾行以 … 表示刪節）。
 * - [normalizeCues] 把歌詞時間軸整理成可用字幕：
 *   全部無時間軸 → 依影片總長均分；有時間軸 → 排序、修補 end<=start、
 *   夾住與下一行的重疊。
 */
object SubtitleTextLayout {

    /** 一句字幕（毫秒時間軸） */
    data class Cue(val text: String, val startMs: Long, val endMs: Long)

    /**
     * 依量測函式換行；保證回傳每一行 measure(line) ≤ maxWidth。
     * maxWidth ≤ 0 時不換行（防呆：呼叫端應先保證正值）。
     */
    fun wrap(text: String, maxWidth: Double, measure: (String) -> Double): List<String> {
        if (text.isBlank()) return emptyList()
        val w = if (maxWidth > 0) maxWidth else Double.MAX_VALUE
        val out = mutableListOf<String>()
        for (para in text.split('\n')) {
            val words = para.split(' ', '\t', '　').filter { it.isNotEmpty() }
            if (words.isEmpty()) continue
            var cur = StringBuilder()
            fun flush() {
                if (cur.isNotEmpty()) {
                    out.add(cur.toString())
                    cur = StringBuilder()
                }
            }
            for (word in words) {
                val withWord = if (cur.isEmpty()) word else cur.toString() + " " + word
                if (measure(withWord) <= w) {
                    cur = StringBuilder(withWord)
                    continue
                }
                if (measure(word) <= w) {
                    // 詞可單獨成行：在詞邊界換行
                    flush()
                    cur.append(word)
                } else {
                    // 詞本身超寬（CJK / 長網址）：逐字元斷行
                    for (c in word) {
                        if (cur.isEmpty()) { cur.append(c); continue }
                        if (measure(cur.toString() + c) <= w) cur.append(c) else {
                            flush()
                            cur.append(c)
                        }
                    }
                }
            }
            flush()
        }
        return out
    }

    /**
     * 嘗試 startSize → minSize（每次減 step）的字級，回傳第一個
     * 「換行後行數 ≤ maxLines」的 (字級, 行陣列)。全數超行時以 minSize
     * 換行並截斷至 maxLines 行（尾行加 …）。
     */
    fun fit(
        text: String,
        maxWidth: Double,
        maxLines: Int,
        startSize: Int,
        minSize: Int,
        step: Int,
        measureAt: (String, Int) -> Double
    ): Pair<Int, List<String>> {
        if (text.isBlank()) return startSize to emptyList()
        var size = startSize
        while (size > minSize) {
            val lines = wrap(text, maxWidth) { measureAt(it, size) }
            if (lines.isNotEmpty() && lines.size <= maxLines) return size to lines
            size -= step
        }
        val lines = wrap(text, maxWidth) { measureAt(it, minSize) }
        if (lines.isEmpty()) return minSize to emptyList()
        if (lines.size <= maxLines) return minSize to lines
        val kept = lines.take(maxLines).toMutableList()
        var last = kept.removeAt(kept.size - 1)
        while (last.isNotEmpty() && measureAt(last + "…", minSize) > maxWidth) {
            last = last.dropLast(1)
        }
        kept.add(last + "…")
        return minSize to kept
    }

    /**
     * 歌詞時間軸整理：
     * - 過濾空白行。
     * - 全部 startMs/endMs 都 ≤ 0（無時間軸）→ 依 totalMs 均分（totalMs 無效時每句 3 秒）。
     * - 有時間軸 → 依 startMs 排序；end ≤ start 時補成下一句開始（最後一句 +3 秒）；
     *   end 超過下一句開始時夾住（避免兩句重疊）。
     */
    fun normalizeCues(cues: List<Cue>, totalMs: Long): List<Cue> {
        val valid = cues.filter { it.text.isNotBlank() }
        if (valid.isEmpty()) return emptyList()
        val unsynced = valid.all { it.startMs <= 0 && it.endMs <= 0 }
        if (unsynced) {
            val span = if (totalMs > 0) totalMs else valid.size * 3_000L
            val step = (span / valid.size).coerceAtLeast(1L)
            return valid.mapIndexed { i, c -> Cue(c.text, i * step, (i + 1) * step) }
        }
        val sorted = valid.sortedBy { it.startMs }
        val out = mutableListOf<Cue>()
        for (i in sorted.indices) {
            val c = sorted[i]
            val start = c.startMs.coerceAtLeast(0L)
            val nextStart = sorted.getOrNull(i + 1)?.startMs?.coerceAtLeast(0L)
            var end = c.endMs
            if (end <= start) end = nextStart ?: (start + 3_000L)
            if (nextStart != null && end > nextStart) end = nextStart
            if (end <= start) end = start + 1
            out.add(Cue(c.text, start, end))
        }
        return out
    }
}
