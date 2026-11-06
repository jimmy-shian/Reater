package com.reater.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * X (Twitter) 連結解析驗證：官方 App 分享帶追蹤參數、舊網域、
 * /i/web/status/ 無 handle 形、/photo|video/N 媒體頁尾綴。
 */
class UrlParserXTest {

    @Test
    fun parsesOfficialShareLinkWithTrackingParams() {
        val parsed = UrlParser.parseXUrl(
            "https://x.com/laobaishare/status/2117547111462158454?a=fUGOyLcGRGxu-WUXkSw&s=09"
        )!!
        assertEquals("laobaishare", parsed.handle)
        assertEquals("2117547111462158454", parsed.statusId)
        assertEquals("https://x.com/laobaishare/status/2117547111462158454", parsed.canonicalUrl)
        assertEquals("https://x.com/laobaishare/status/2117547111462158454", parsed.fetchUrl)
        assertEquals("X 貼文", parsed.kindLabel)
    }

    @Test
    fun parsesLegacyTwitterDomains() {
        assertEquals(
            "1234567890",
            UrlParser.parseXUrl("https://twitter.com/someone/status/1234567890/")!!.statusId
        )
        val mobile = UrlParser.parseXUrl("https://mobile.twitter.com/someone/status/1234567890")!!
        assertEquals("someone", mobile.handle)
        assertEquals("https://x.com/someone/status/1234567890", mobile.canonicalUrl)
    }

    @Test
    fun parsesWebStatusPathWithoutHandle() {
        val parsed = UrlParser.parseXUrl("https://x.com/i/web/status/1999999999999999999")!!
        assertEquals("", parsed.handle)
        assertEquals("1999999999999999999", parsed.statusId)
        assertEquals("https://x.com/i/web/status/1999999999999999999", parsed.canonicalUrl)
    }

    @Test
    fun normalizesMediaAndAnalyticsSuffixes() {
        val photo = UrlParser.parseXUrl("https://x.com/u1/status/1001/photo/1")!!
        assertEquals("1001", photo.statusId)
        assertTrue(photo.hadMediaSuffix)
        assertEquals("https://x.com/u1/status/1001", photo.canonicalUrl)
        val video = UrlParser.parseXUrl("https://x.com/u1/status/1002/video/1")!!
        assertTrue(video.hadMediaSuffix)
        assertEquals("https://x.com/u1/status/1002", video.canonicalUrl)
        val analytics = UrlParser.parseXUrl("https://x.com/u1/status/1003/analytics")!!
        assertEquals("https://x.com/u1/status/1003", analytics.canonicalUrl)
    }

    @Test
    fun rejectsNonXLinks() {
        assertNull(UrlParser.parseXUrl("https://www.threads.com/@user/post/DeBy-FEiEf5"))
        assertNull(UrlParser.parseXUrl("https://www.instagram.com/p/De_mAFQH-sr/"))
        assertNull(UrlParser.parseXUrl("https://x.com/laobaishare"))
        assertNull(UrlParser.parseXUrl("https://x.com/not-a-status/page"))
        assertNull(UrlParser.parseXUrl(""))
    }

    @Test
    fun extractsFirstXUrlFromSharedText() {
        val text = "看看這則 https://x.com/laobaishare/status/2117547111462158454?a=fUGOyLcGRGxu-WUXkSw&s=09 不錯。"
        assertEquals(
            "https://x.com/laobaishare/status/2117547111462158454?a=fUGOyLcGRGxu-WUXkSw&s=09",
            UrlParser.extractFirstXUrl(text)
        )
        val noUrl = UrlParser.extractFirstXUrl("https://www.threads.com/@user/post/ABC123 只有 Threads 連結")
        assertNull(noUrl)
    }

    @Test
    fun hostChecksAndParamStripping() {
        assertTrue(UrlParser.isXHost("https://x.com/u/status/1"))
        assertTrue(UrlParser.isXHost("https://twitter.com/u/status/1"))
        assertFalse(UrlParser.isXHost("https://www.threads.com/@u/post/ABC"))
        assertEquals(
            "https://x.com/u/status/1",
            UrlParser.stripXParams("https://x.com/u/status/1?s=09")
        )
    }
}
