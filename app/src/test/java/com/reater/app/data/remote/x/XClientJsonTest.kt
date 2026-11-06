package com.reater.app.data.remote.x

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * X 兩種端點 JSON 解析驗證（fixture 取自 2026-11 實測回應的修剪版）：
 *  - fxtwitter：text 已是乾淨主文；video 的 formats 內含 m3u8 + 多碼率 mp4，須選最高碼率 mp4
 *  - syndication：text 尾端帶 t.co 短連結須清除；mediaDetails.video_info.variants 選 mp4
 */
class XClientJsonTest {

    @Test
    fun parsesFxtwitterTweetWithVideoAndCounts() {
        val tweet = JSONObject(
            """
            {"url":"https://x.com/laobaishare/status/2117547111462158454",
             "id":"2117547111462158454",
             "text":"这是一个 100% 学会 Claude Code 的机会。\n\n做了中文字幕，别犯懒，一定要看。",
             "author":{"screen_name":"laobaishare","name":"老白（每日 AI 干货）",
               "avatar_url":"https://pbs.twimg.com/profile_images/1688863542110507008/RO_200x200.jpg",
               "verification":{"verified":true,"type":"individual"}},
             "replies":107,"retweets":288,"likes":1719,"views":115452,
             "created_timestamp":1793697548,
             "media":{"all":[
                {"id":"1","type":"video",
                 "url":"https://video.twimg.com/amplify_video/2117545619095236611/vid/avc1/2560x1440/yc4bCu1eiqQnAT3R.mp4?tag=29",
                 "thumbnail_url":"https://pbs.twimg.com/amplify_video_thumb/2117545619095236611/img/wPNZkn2Xl78ePwTz.jpg",
                 "duration":1487.1,"width":2560,"height":1440,"format":"video/mp4",
                 "formats":[
                   {"url":"https://video.twimg.com/amplify_video/2117545619095236611/pl/YPELsHrUtoC1STlJ.m3u8?tag=29","container":"m3u8"},
                   {"url":"https://video.twimg.com/amplify_video/2117545619095236611/vid/avc1/480x270/2sVjKyzSQ8Vrla6n.mp4?tag=29","bitrate":256000,"container":"mp4"},
                   {"url":"https://video.twimg.com/amplify_video/2117545619095236611/vid/avc1/1280x720/0HqH2EZVlEyf1xJw.mp4?tag=29","bitrate":2176000,"container":"mp4"},
                   {"url":"https://video.twimg.com/amplify_video/2117545619095236611/vid/avc1/2560x1440/yc4bCu1eiqQnAT3R.mp4?tag=29","bitrate":10368000,"container":"mp4"}
                 ]}
             ]}}
            """.trimIndent()
        )
        val parsed = XJsonParser.fromFxtwitter(tweet, "0")!!
        assertEquals("2117547111462158454", tweet.optString("id"))
        assertEquals("laobaishare", parsed.handle)
        assertEquals("老白（每日 AI 干货）", parsed.displayName)
        assertEquals(true, parsed.verified)
        assertEquals(1719, parsed.likeCount)
        assertEquals(107, parsed.replyCount)
        assertEquals(288, parsed.repostCount)
        assertEquals(1793697548000L, parsed.postedAtMs)
        assertEquals(1, parsed.media.size)
        // 主 url 本身即最高碼率 mp4 → 直接採用（不落入 m3u8）
        assertEquals(
            "https://video.twimg.com/amplify_video/2117545619095236611/vid/avc1/2560x1440/yc4bCu1eiqQnAT3R.mp4?tag=29",
            parsed.media[0].remoteUrl
        )
        assertEquals("VIDEO", parsed.media[0].kind)
        assertTrue(parsed.bodyText.startsWith("这是一个"))
        // 主文不含 t.co 短連結
        assertTrue(!parsed.bodyText.contains("t.co"))
    }

    @Test
    fun fxtwitterPicksHighestBitrateMp4WhenPrimaryIsPlaylist() {
        val tweet = JSONObject(
            """
            {"id":"100","text":"hi",
             "author":{"screen_name":"u1","name":"U"},
             "created_timestamp":1793697548,
             "media":{"all":[
                {"type":"video",
                 "url":"https://video.twimg.com/1/pl/x.m3u8?tag=29",
                 "width":1920,"height":1080,
                 "formats":[
                   {"url":"https://video.twimg.com/1/vid/480x270/a.mp4?tag=29","bitrate":256000,"container":"mp4"},
                   {"url":"https://video.twimg.com/1/vid/1280x720/b.mp4?tag=29","bitrate":2176000,"container":"mp4"}
                 ]}
             ]}}
            """.trimIndent()
        )
        val parsed = XJsonParser.fromFxtwitter(tweet, "0")!!
        assertEquals(
            "https://video.twimg.com/1/vid/1280x720/b.mp4?tag=29",
            parsed.media[0].remoteUrl
        )
    }

    @Test
    fun parsesSyndicationTweetStrippingTrailingTcoLink() {
        val root = JSONObject(
            """
            {"__typename":"Tweet","id_str":"2117547111462158454",
             "text":"这是一个 100% 学会 Claude Code 的机会。 https://t.co/Id69ZE9xc7",
             "created_at":"2026-11-03T09:19:08.000Z",
             "favorite_count":1722,"conversation_count":107,
             "user":{"screen_name":"laobaishare","name":"老白","verified":true,
               "profile_image_url_https":"https://pbs.twimg.com/profile_images/1/RO_200x200.jpg"},
             "mediaDetails":[
                {"type":"video","media_url_https":"https://pbs.twimg.com/amplify_video_thumb/1/img/x.jpg",
                 "video_info":{"variants":[
                   {"bitrate":0,"content_type":"application/x-mpegURL","url":"https://video.twimg.com/1/pl/x.m3u8?tag=29"},
                   {"bitrate":832000,"content_type":"video/mp4","url":"https://video.twimg.com/1/vid/640x360/a.mp4?tag=29"},
                   {"bitrate":2176000,"content_type":"video/mp4","url":"https://video.twimg.com/1/vid/1280x720/b.mp4?tag=29"}
                 ]}},
                {"type":"photo","media_url_https":"https://pbs.twimg.com/media/ABC123.jpg"}
             ]}
            """.trimIndent()
        )
        val parsed = XJsonParser.fromSyndication(root, "0")!!
        assertEquals("laobaishare", parsed.handle)
        assertEquals("老白", parsed.displayName)
        assertEquals(1722, parsed.likeCount)
        assertEquals(107, parsed.replyCount)
        assertEquals(1793697548000L, parsed.postedAtMs)
        // t.co 短連結已從主文清除
        assertEquals("这是一个 100% 学会 Claude Code 的机会。", parsed.bodyText)
        assertEquals(2, parsed.media.size)
        assertEquals("VIDEO", parsed.media[0].kind)
        assertEquals(
            "https://video.twimg.com/1/vid/1280x720/b.mp4?tag=29",
            parsed.media[0].remoteUrl
        )
        assertEquals("IMAGE", parsed.media[1].kind)
        assertEquals("https://pbs.twimg.com/media/ABC123.jpg", parsed.media[1].remoteUrl)
    }

    @Test
    fun emptyTweetReturnsNull() {
        assertNull(XJsonParser.fromFxtwitter(JSONObject("{\"id\":\"1\",\"text\":\"\"}"), "1"))
        assertNull(XJsonParser.fromSyndication(JSONObject("{\"id_str\":\"1\",\"text\":\"\"}"), "1"))
    }
}
