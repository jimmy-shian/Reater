package com.reater.app.data.remote.threads

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用 App 真正的抓取程式（ThreadsPageFetcher + ThreadsHtmlParser）對真實 /share/ 連結跑一次，
 * 重現裝置端 HTTP 路徑（不含離線媒體下載）。用來判斷抓取層是否成功。
 */
class ThreadsLiveFetchTest {

    @Test
    fun fetchAndParseShareLink() {
        val target = "https://www.threads.com/share/Boo6zbWC8J"
        val client = ThreadsPageFetcher.newClient()
        val fetched = ThreadsPageFetcher.fetch(client, target)
        println("LIVE_resolvedUrl=${fetched.resolvedUrl}")
        println("LIVE_htmlLen=${fetched.html.length}")
        println("LIVE_gotAnyHtml=${fetched.gotAnyHtml}")
        println("LIVE_hasPostPayload=${ThreadsPageFetcher.hasPostPayload(fetched.html)}")
        println("LIVE_isLoginWall=${ThreadsPageFetcher.isLoginWall(fetched.html)}")

        val ids = ThreadsPageFetcher.extractIds(fetched.resolvedUrl, "")
        println("LIVE_ids_shortcode=${ids.shortcode} LIVE_ids_handle=${ids.handle}")

        val parsed = ThreadsHtmlParser.parse(fetched.html, ids.shortcode)
        println("LIVE_fromSjs=${parsed.fromSjs}")
        println("LIVE_handle=${parsed.authorHandleFromTitle}")
        println("LIVE_bodyLen=${parsed.bodyText.length}")
        println("LIVE_body=[${parsed.bodyText}]")
        println("LIVE_media=${parsed.media}")
        println("LIVE_replyCount=${parsed.replyCount} repostCount=${parsed.repostCount} like=${parsed.likeCount}")
        println("LIVE_comments=${parsed.comments.size}")

        assertTrue("no html fetched", fetched.html.isNotBlank())
        assertTrue("no body parsed", parsed.bodyText.isNotBlank() || parsed.media.isNotEmpty())
    }
}
