package com.reater.app.data.remote.threads

import org.json.JSONArray
import org.json.JSONObject

/**
 * Threads 主題標籤擷取器。
 *
 * 背景：Threads 貼文帶有單一主題（topic pill，如 AI、攝影），在 SSR 內嵌 JSON 的
 * post 物件（或其 text_post_app_info）中以 topic 系欄位出現；同時 Threads 已把
 * hashtag 與主題打通（點 #AI 即進 AI 主題頁），故抓不到 pill 時退而取內文首個
 * hashtag，確保「同主題預選分類」在實務上能命中。
 *
 * 優先順序：pill 欄位 > 內文首個 hashtag；一律回傳乾淨字串（去 #、去空白），無則 ""。
 */
object TopicTags {

    private val TOPIC_KEYS = setOf(
        "topic", "topics", "topic_tag", "topic_name", "topic_title",
        "tag", "tags", "hashtag", "hashtags"
    )

    private val VALUE_KEYS = arrayOf("name", "text", "title", "value", "topic")

    /** 從 SJS post 物件取主題（pill 欄位優先，否則內文首個 hashtag）。 */
    fun resolve(post: JSONObject?, bodyText: String): String {
        val pill = post?.let { fromPostJson(it) }.orEmpty()
        if (pill.isNotBlank()) return pill
        return firstHashtag(bodyText)
    }

    /** 只看 pill 系欄位（post 本體 + text_post_app_info 一層）。 */
    fun fromPostJson(post: JSONObject): String {
        return try {
            extractFrom(post)?.let(::clean).orEmpty()
                .ifBlank {
                    post.optJSONObject("text_post_app_info")?.let { tpa ->
                        extractFrom(tpa)?.let(::clean).orEmpty()
                    }.orEmpty()
                }
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractFrom(obj: JSONObject): String? {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!TOPIC_KEYS.contains(key.lowercase())) continue
            readValue(obj.opt(key))?.let { return it }
        }
        return null
    }

    private fun readValue(v: Any?): String? {
        return when (v) {
            is String -> v.takeIf { it.isNotBlank() }
            is JSONObject -> {
                for (k in VALUE_KEYS) {
                    val s = v.optString(k).takeIf { it.isNotBlank() && it != "null" }
                    if (s != null) return s
                }
                null
            }
            is JSONArray -> {
                for (i in 0 until v.length()) {
                    readValue(v.opt(i))?.let { return it }
                }
                null
            }
            else -> null
        }
    }

    /** 內文首個 hashtag（#AI、#攝影；中英數字底線，1~30 字）。 */
    fun firstHashtag(text: String): String {
        if (text.isBlank()) return ""
        val m = Regex("""#([\p{L}\p{N}_]{1,30})""").find(text) ?: return ""
        return clean(m.groupValues[1])
    }

    private fun clean(raw: String): String {
        val v = raw.trim().trimStart('#').trim()
        if (v.isBlank() || v.length > 30) return ""
        if (v.contains(" ") || v.contains("\n") || v.contains("\t")) return ""
        if (v.equals("null", ignoreCase = true)) return ""
        return v
    }
}
