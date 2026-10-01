package com.reater.app.data.remote

/**
 * 媒體去重工具：同一張圖/同一支影片在 Threads JSON 內常有多個變體 URL
 *（image_versions2 多解析度、video_versions 多碼率、og 與內嵌重複、
 * HTTP 與 WebView 兩路抓到不同清晰度）。
 *
 * 用精確 URL 去重會留下「看起來一模一樣卻存成兩份」的雙份媒體
 *（詳情頁出現兩個相同影片/圖片）。
 * 此處以正規化 key（去 query/fragment、小寫）去重，同一內容只留首個。
 */
object MediaDedup {
    /** 正規化媒體 key：去 query/fragment、小寫、去空白 */
    fun normalizeKey(url: String): String {
        if (url.isBlank()) return ""
        return url.trim()
            .substringBefore("?")
            .substringBefore("#")
            .trim()
            .lowercase()
    }

    /** FetchedMedia 列表依內容去重（保留首次出現順序，最多保留原順序） */
    fun distinctFetched(media: List<FetchedMedia>): List<FetchedMedia> {
        if (media.size <= 1) return media
        val seen = LinkedHashSet<String>()
        val out = ArrayList<FetchedMedia>(media.size)
        for (m in media) {
            val key = normalizeKey(m.remoteUrl).ifBlank { m.remoteUrl.trim() }
            if (key.isBlank()) continue
            if (seen.add(key)) out.add(m)
        }
        return out
    }

    /** MediaEntity 列表依內容去重（保留 position 排序後的首次出現） */
    fun distinctEntities(
        media: List<com.reater.app.data.local.entity.MediaEntity>
    ): List<com.reater.app.data.local.entity.MediaEntity> {
        if (media.size <= 1) return media
        val seen = LinkedHashSet<String>()
        val out = ArrayList<com.reater.app.data.local.entity.MediaEntity>(media.size)
        for (m in media) {
            val key = normalizeKey(m.remoteUrl).ifBlank { m.remoteUrl.trim() }
            if (key.isBlank()) continue
            if (seen.add(key)) out.add(m)
        }
        return out
    }
}
