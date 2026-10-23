package com.reater.app.data.remote.threads

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.json.JSONObject
import org.junit.Test

/**
 * 以 2026-10 真實 googlebot SSR 抓回的 SJS block（/share/Boo6zbWC8J/ → DeBy-FEiEf5）
 * 直接驗證新版 "data":{"media"} shape 的解析結果。
 */
class ThreadsSjsParserRealHtmlTest {

    @Test
    fun parsesAllSevenRepliesIncludingSelfThreadTextAttachment() {
        val block = resource("threads_text_attachment_DeUgUXYGcqq.json")
        val result = ThreadsSjsParser.parseBlocks(listOf(block), "DeUgUXYGcqq")!!
        assertEquals(7, result.comments.size)
        val prompt = result.comments.single { it.externalId == "DeUgbQCmWZv" }
        val expected = JSONObject(block).getJSONArray("payloads").getJSONObject(1)
            .getJSONObject("data").getJSONObject("media")
            .getJSONObject("text_post_app_info").getJSONObject("self_thread")
            .getJSONObject("posts").getJSONArray("edges").getJSONObject(0).getJSONObject("node")
            .getJSONObject("text_post_app_info").getJSONObject("snippet_attachment_info")
            .getJSONObject("text_fragments").getJSONArray("fragments").getJSONObject(0)
            .getString("plaintext")
        val parts = prompt.text.split(com.reater.app.domain.TextAttachmentStore.INLINE_MARKER, limit = 2)
        assertEquals("Prompt如下", parts[0])
        assertEquals(expected, parts[1])
        assertTrue(expected.contains("\n\nOVERALL COMPOSITION\n\n"))
        assertTrue(result.comments.all { it.authorProfileUrl.startsWith("https://") })
        assertTrue(result.comments.none { c -> c.media.any {
            it.remoteUrl.substringBefore('?') == c.authorProfileUrl.substringBefore('?')
        } })
        assertTrue(result.comments.single { it.author == "jchuang1977" }.media.isNotEmpty())
        assertEquals(1, result.comments.count { it.text.contains(com.reater.app.domain.TextAttachmentStore.INLINE_MARKER) })
        val payloads = JSONObject(block).getJSONArray("payloads")
        val reversed = JSONObject().put("payloads", org.json.JSONArray()
            .put(payloads.getJSONObject(1)).put(payloads.getJSONObject(0))).toString()
        assertEquals(7, ThreadsSjsParser.parseBlocks(listOf(reversed), "DeUgUXYGcqq")!!.comments.size)
    }

    @Test
    fun textAttachmentKeepsBlankLinesWhitespaceAndEmptyCaption() {
        val attachment = "  first\n\nsecond\n  "
        val stored = com.reater.app.domain.TextAttachmentStore.appendToText("", attachment)
        assertEquals(attachment, stored.split(com.reater.app.domain.TextAttachmentStore.INLINE_MARKER, limit = 2)[1])
        val raw = com.reater.app.domain.TextAttachmentStore.withTextAttachment("{}", attachment)
        assertEquals(attachment, com.reater.app.domain.TextAttachmentStore.read(raw))
        val ordinary = JSONObject("""{"caption":{"text":"${"Long text ".repeat(1000)}"}}""")
        val block = JSONObject().put("data", JSONObject().put("media", ordinary
            .put("code", "ordinary").put("user", JSONObject().put("username", "test")))).toString()
        assertEquals("", ThreadsSjsParser.parseBlocks(listOf(block), "ordinary")!!.textAttachment)
    }

    @Test
    fun actualFetchedHtmlContainsSevenRepliesAndCompleteAttachment() {
        val file = java.io.File(System.getenv("TEMP"), "reater-threads-DeUgUXYGcqq.html")
        org.junit.Assume.assumeTrue("Optional captured live page", file.isFile)
        val result = ThreadsSjsParser.parse(file.readText(), "DeUgUXYGcqq")!!
        assertEquals(7, result.comments.size)
        val prompt = result.comments.single { it.externalId == "DeUgbQCmWZv" }
        assertTrue(prompt.text.contains("\n\nOVERALL COMPOSITION\n\n"))
        val page = ThreadsHtmlParser.parse(file.readText(), "DeUgUXYGcqq")
        assertTrue(page.fromSjs)
        assertEquals(7, page.comments.size)
        assertEquals(prompt.text, page.comments.single { it.externalId == "DeUgbQCmWZv" }.text)
        println("CAPTURED_PAGE comments=${result.comments.size} promptLength=${prompt.text.length} avatars=${result.comments.count { it.authorProfileUrl.isNotBlank() }}")
    }

    @Test
    fun parsesLinkOnlyCommentText() {
        val post = JSONObject(
            """
            {
              "text_post_app_info": {
                "text_fragments": {
                  "fragments": [{
                    "fragment_type": "link",
                    "plaintext": "",
                    "link_fragment": {"url": "https://www.threads.com/share/BAjN7HCsaj/"}
                  }]
                }
              }
            }
            """.trimIndent()
        )
        assertEquals(
            "https://www.threads.com/share/BAjN7HCsaj/",
            ThreadsSjsParser.postText(post)
        )
    }

    @Test
    fun parsesStructuredTextAttachmentWithoutLengthHeuristic() {
        val block = """
            {"data":{"media":{
              "code":"TextAttachmentTest",
              "user":{"username":"tester"},
              "caption":{"text":"Prompt如下"},
              "text_post_app_info":{
                "is_reply":false,
                "snippet_attachment_info":{"text_fragments":{"fragments":[
                  {"plaintext":"完整的文字附件原文"}
                ]}}
              }
            }}}
        """.trimIndent()
        val result = ThreadsSjsParser.parseBlocks(listOf(block), "TextAttachmentTest")
        assertEquals("完整的文字附件原文", result?.textAttachment)
    }

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
            println("LIKE=${res.likeCount} COMMENTS=${res.comments.size}")
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

    @Test
    fun parsesThreadConversationShareLinkPreservingReplyAndParentChain() {
        val html = resource("threads_share_qUDfvMEg.html")
        val parsed = ThreadsHtmlParser.parse(html, "share__qUDfvMEg")
        assertTrue("parsed should succeed from SJS via canonical fallback", parsed.fromSjs)
        assertEquals("ncueaul", parsed.authorHandleFromTitle)
        assertTrue(parsed.bodyText.contains("Claude Code / Codex"))
        assertTrue(parsed.bodyText.contains("taste-skill"))
        assertEquals(1, parsed.media.size)
        assertTrue(parsed.media.first().remoteUrl.startsWith("http"))
        assertEquals(1, parsed.parentChain.size)
        val parent = parsed.parentChain.first()
        assertEquals("auriestudiotw", parent.authorHandle)
        assertTrue(parent.bodyText.contains("我在自學作網站"))
        assertTrue(parent.bodyText.contains("溫馨感"))
        assertEquals(1, parent.media.size)
        assertEquals(2, parsed.comments.size)
        assertTrue(parsed.comments.any { it.author == "mik77550" && it.text.contains("這是") })
        assertTrue(parsed.comments.any { it.author == "ncueaul" && it.text.contains("可以丟連結給你的 Ai") })
    }
}
