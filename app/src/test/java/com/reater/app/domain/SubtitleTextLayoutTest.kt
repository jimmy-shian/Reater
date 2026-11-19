package com.reater.app.domain

import com.reater.app.domain.SubtitleTextLayout.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 字幕排版契約測試：每一行量測寬度必 ≤ maxWidth（不可超出影片畫布寬度）。
 * 量測用確定性假函式：CJK 全形字元 10px、其他 5px。
 */
class SubtitleTextLayoutTest {

    private fun measure(s: String): Double =
        s.sumOf { c -> if (c.code > 0x2E7F) 10.0 else 5.0 }

    @Test
    fun cjkTextWrapsByCharacterAndNeverOverflows() {
        val lines = SubtitleTextLayout.wrap("一二三四五六七八九十", 30.0, ::measure)
        assertEquals(listOf("一二三", "四五六", "七八九", "十"), lines)
        assertTrue(lines.all { measure(it) <= 30.0 })
    }

    @Test
    fun latinTextBreaksAtWordBoundaries() {
        val lines = SubtitleTextLayout.wrap("aaa bbb ccc ddd", 35.0, ::measure)
        assertEquals(listOf("aaa bbb", "ccc ddd"), lines)
        assertTrue(lines.all { measure(it) <= 35.0 })
    }

    @Test
    fun overlongUnbreakableWordIsBrokenByCharacter() {
        val lines = SubtitleTextLayout.wrap("ABCDEFGH", 15.0, ::measure)
        assertEquals(listOf("ABC", "DEF", "GH"), lines)
        assertTrue(lines.all { measure(it) <= 15.0 })
    }

    @Test
    fun explicitNewlineForcesLineBreak() {
        assertEquals(listOf("aa", "bb"), SubtitleTextLayout.wrap("aa\nbb", 100.0, ::measure))
    }

    @Test
    fun mixedLongTextEveryLineFits() {
        val text = "我的耳機兩個都有 因為我聽的是AC/DC Highway to Hell 超長的一句歌詞 mixed with English words too"
        val lines = SubtitleTextLayout.wrap(text, 200.0, ::measure)
        assertTrue("應有換行", lines.size > 1)
        assertTrue(lines.all { measure(it) <= 200.0 })
        assertEquals(
            "換行不應遺失任何文字（行界空白被當換行點壓掉）",
            text.split(Regex("[\\s　]+")).filter { it.isNotEmpty() }.joinToString(""),
            lines.joinToString("").replace(" ", "").replace("\t", "").replace("　", "")
        )
    }

    @Test
    fun fitShrinksFontSizeUntilWithinMaxLines() {
        var measuredSize = 0
        val measureAt = { s: String, size: Int ->
            measuredSize = size
            s.sumOf { 1.0 * size } // CJK：每字寬 = 字級
        }
        val (size, lines) = SubtitleTextLayout.fit(
            "一二三四五六七八九十", 200.0, 2, 46, 26, 6, measureAt
        )
        // size=40 時每行 5 字、共 2 行 → 符合 maxLines
        assertEquals(40, size)
        assertEquals(2, lines.size)
        assertTrue(lines.all { measureAt(it, size) <= 200.0 })
    }

    @Test
    fun fitTruncatesWithEllipsisWhenStillTooLong() {
        val text = "字".repeat(60)
        val measureAt = { s: String, size: Int -> s.length * (size / 10.0) }
        val (size, lines) = SubtitleTextLayout.fit(
            text, 30.0, 3, 46, 26, 6, measureAt
        )
        // size=26 時每行 11 字、60 字 → 6 行 > 3 → 縮到最小仍超行 → 截斷
        assertEquals(26, size)
        assertEquals(3, lines.size)
        assertTrue("尾行以 … 截斷", lines[2].endsWith("…"))
        assertTrue(lines.all { measureAt(it, size) <= 30.0 })
    }

    @Test
    fun normalizeCuesDistributesUnsyncedLyricsEvenly() {
        val cues = listOf(Cue("a", 0, 0), Cue("b", 0, 0), Cue("c", 0, 0))
        val out = SubtitleTextLayout.normalizeCues(cues, 9_000L)
        assertEquals(listOf(
            Cue("a", 0, 3_000),
            Cue("b", 3_000, 6_000),
            Cue("c", 6_000, 9_000)
        ), out)
    }

    @Test
    fun normalizeCuesSortsRepairsMissingEndsAndRemovesOverlaps() {
        val cues = listOf(
            Cue("late", 10_000, 12_000),
            Cue("first-no-end", 0, 0),
            Cue("overlap", 11_000, 20_000)
        )
        val out = SubtitleTextLayout.normalizeCues(cues, 30_000L)
        // 排序後：first-no-end(0) → late(10000) → overlap(11000)
        assertEquals("first-no-end", out[0].text)
        assertEquals(0L, out[0].startMs)
        assertEquals(10_000L, out[0].endMs) // end<=start → 補成下一句開始
        assertEquals("late", out[1].text)
        assertEquals(11_000L, out[1].endMs) // 與下一句重疊 → 夾住
        assertEquals("overlap", out[2].text)
        assertEquals(20_000L, out[2].endMs) // 無下一句 → 保留原 end
        assertTrue(out.all { it.endMs > it.startMs })
    }

    @Test
    fun blankTextProducesNoLines() {
        assertEquals(emptyList<String>(), SubtitleTextLayout.wrap("  \n\t ", 100.0, ::measure))
        assertEquals(46, SubtitleTextLayout.fit(" ", 100.0, 3, 46, 26, 6) { _, _ -> 0.0 }.first)
    }
}
