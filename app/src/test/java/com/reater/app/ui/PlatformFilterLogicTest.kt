package com.reater.app.ui

import com.reater.app.domain.UrlParser
import org.junit.Assert.*
import org.junit.Test

class PlatformFilterLogicTest {

    private data class PlatformOptionTest(
        val label: String,
        val key: String
    )

    private val allOptions = listOf(
        PlatformOptionTest("全部", "ALL"),
        PlatformOptionTest("Threads", UrlParser.PLATFORM_THREADS),
        PlatformOptionTest("Instagram", UrlParser.PLATFORM_INSTAGRAM),
        PlatformOptionTest("X", UrlParser.PLATFORM_X),
        PlatformOptionTest("Facebook", UrlParser.PLATFORM_FACEBOOK),
        PlatformOptionTest("YouTube", UrlParser.PLATFORM_YOUTUBE)
    )

    private fun resolveOptions(platformCounts: Map<String, Int>): Pair<Boolean, List<PlatformOptionTest>> {
        val activePlatforms = allOptions.filter { it.key != "ALL" && (platformCounts[it.key] ?: 0) > 0 }
        val showButton = activePlatforms.size > 1
        val available = listOf(allOptions.first { it.key == "ALL" }) + activePlatforms
        return Pair(showButton, available)
    }

    @Test
    fun testOnlySinglePlatform_HidesButton() {
        val counts = mapOf(
            "ALL" to 5,
            UrlParser.PLATFORM_THREADS to 5,
            UrlParser.PLATFORM_INSTAGRAM to 0,
            UrlParser.PLATFORM_X to 0,
            UrlParser.PLATFORM_FACEBOOK to 0,
            UrlParser.PLATFORM_YOUTUBE to 0
        )
        val (showButton, available) = resolveOptions(counts)
        assertFalse("只有一種平台時應隱藏按鈕", showButton)
        assertEquals(2, available.size) // ALL + Threads
    }

    @Test
    fun testTwoPlatforms_ShowsButton_CyclesSequential() {
        val counts = mapOf(
            "ALL" to 8,
            UrlParser.PLATFORM_THREADS to 5,
            UrlParser.PLATFORM_INSTAGRAM to 3,
            UrlParser.PLATFORM_X to 0,
            UrlParser.PLATFORM_FACEBOOK to 0,
            UrlParser.PLATFORM_YOUTUBE to 0
        )
        val (showButton, available) = resolveOptions(counts)
        assertTrue("兩種平台時應顯示按鈕", showButton)
        assertEquals("總選項為 全部 + 2 平台 = 3 個", 3, available.size)
        val useDropdown = available.size > 3
        assertFalse("3 個以內（含全部）應順序循環切換，不使用下拉選單", useDropdown)
    }

    @Test
    fun testMoreThanThreeOptions_UsesDropdown() {
        val counts = mapOf(
            "ALL" to 15,
            UrlParser.PLATFORM_THREADS to 5,
            UrlParser.PLATFORM_INSTAGRAM to 3,
            UrlParser.PLATFORM_X to 2,
            UrlParser.PLATFORM_FACEBOOK to 3,
            UrlParser.PLATFORM_YOUTUBE to 2
        )
        val (showButton, available) = resolveOptions(counts)
        assertTrue(showButton)
        assertEquals(6, available.size) // ALL + 5 platforms
        val useDropdown = available.size > 3
        assertTrue("超過 3 個選項時應使用下拉選單", useDropdown)
    }
}
