package com.reater.app.domain

import org.junit.Assert.*
import org.junit.Test

class UrlParserFacebookYouTubeTest {

    @Test
    fun testParseFacebookShareUrl() {
        val raw = "https://www.facebook.com/share/p/1FgbFa9ngp/?mibextid=wwXIfr"
        assertTrue(UrlParser.isFacebookHost(raw))
        val parsed = UrlParser.parseFacebookUrl(raw)
        assertNotNull(parsed)
        assertEquals("1FgbFa9ngp", parsed?.postId)
        assertEquals("https://www.facebook.com/share/p/1FgbFa9ngp", parsed?.canonicalUrl)
        assertEquals("FB 貼文", parsed?.kindLabel)
        assertTrue(parsed?.isShareLink == true)
    }

    @Test
    fun testParseYouTubeStandardAndShorts() {
        val watchUrl = "https://www.youtube.com/watch?v=veY_x0t-_LI"
        assertTrue(UrlParser.isYouTubeHost(watchUrl))
        val parsedWatch = UrlParser.parseYouTubeUrl(watchUrl)
        assertNotNull(parsedWatch)
        assertEquals("veY_x0t-_LI", parsedWatch?.videoId)
        assertEquals("https://www.youtube.com/watch?v=veY_x0t-_LI", parsedWatch?.canonicalUrl)
        assertFalse(parsedWatch?.isShorts == true)

        val shortsUrl = "https://www.youtube.com/shorts/8k4WhKuA3OA"
        assertTrue(UrlParser.isYouTubeHost(shortsUrl))
        val parsedShorts = UrlParser.parseYouTubeUrl(shortsUrl)
        assertNotNull(parsedShorts)
        assertEquals("8k4WhKuA3OA", parsedShorts?.videoId)
        assertEquals("https://www.youtube.com/shorts/8k4WhKuA3OA", parsedShorts?.canonicalUrl)
        assertTrue(parsedShorts?.isShorts == true)
    }
}
