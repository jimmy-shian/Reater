package com.reater.app.data.remote.threads

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 以 2026-10 真實 googlebot SSR 抓回的 SJS block（/share/Boo6zbWC8J/ → DeBy-FEiEf5）
 * 直接驗證新版 "data":{"media"} shape 的解析結果。
 */
class ThreadsSjsParserRealHtmlTest {

    private fun readBlock(): String =
        javaClass.classLoader!!.getResourceAsStream("threads_block19.json")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
            .removePrefix("\uFEFF")

    @Test
    fun parsesRealNewShapeBlock() {
        println("ORGJSON_SRC=" + org.json.JSONObject::class.java.protectionDomain?.codeSource?.location)
        val block = readBlock()
        try {
            val o = org.json.JSONObject(block)
            println("JSON_PARSE_OK keys=" + o.keys().asSequence().toList())
        } catch (e: Throwable) {
            println("JSON_PARSE_THROW=" + e)
        }
        val res = ThreadsSjsParser.parseBlocks(listOf(block), "DeBy-FEiEf5")
        println("NULL=${res == null}")
        if (res != null) {
            println("BODY=[${res.bodyText}]")
            println("HANDLE=${res.authorHandle}")
            println("LIKE=${res.likeCount} REPLY=${res.replyCount} REPOST=${res.repostCount}")
            println("MEDIA=${res.media}")
            println("COMMENTS=${res.comments.size}")
            println("PARENT=${res.parent}")
        }
        assertTrue("parser returned null", res != null)
        assertTrue("body empty", res!!.bodyText.isNotBlank())
    }

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
            .removePrefix("\uFEFF")

    @Test
    fun parsesRealFullPageGooglebot() {
        val html = resource("threads_page.html")
        val p = ThreadsHtmlParser.parse(html, "DeBy-FEiEf5")
        println("FP_FROM_SJS=${p.fromSjs}")
        println("FP_HANDLE=${p.authorHandleFromTitle}")
        println("FP_BODY_LEN=${p.bodyText.length}")
        println("FP_BODY=[${p.bodyText}]")
        println("FP_MEDIA=${p.media}")
        println("FP_COMMENTS=${p.comments.size}")
    }
}
