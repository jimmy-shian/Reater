package com.reater.app.domain

import com.reater.app.data.remote.MediaDedup
import org.json.JSONArray
import org.json.JSONObject

/**
 * Threads 音訊（音樂貼文）metadata 的存取層：
 *
 * - 解析端（ThreadsSjsParser.audioFromItem）把 audio_asset 的
 *   曲名/歌手/封面圖/跑動歌詞 編成 JSON 附在 AUDIO FetchedMedia.audioMetaJson。
 * - 存檔（ThreadPostRepository.savePost）彙整成
 *   {"audio_meta":[{"url":<normalizeKey>,"title":...,"artist":...,"cover":...,"lyrics":[{...}]}]}
 *   注入 items.rawJsonMin（免 DB 遷移，同 chainMedia/textAttachment 模式）。
 * - UI（詳情頁歌詞卡 / 全螢幕匯出）以 MediaDedup.normalizeKey(remoteUrl) 反查。
 */
object AudioMetaStore {
    private const val KEY = "audio_meta"

    /** 一行歌詞（Threads audio_asset.lyrics.lines[].line） */
    data class LyricLine(val text: String, val startMs: Long = 0, val endMs: Long = 0)

    data class AudioMeta(
        val title: String = "",
        val artist: String = "",
        val coverUrl: String = "",
        val lyrics: List<LyricLine> = emptyList()
    )

    fun encode(meta: AudioMeta): String = runCatching {
        val root = JSONObject()
        root.put("title", meta.title)
        root.put("artist", meta.artist)
        root.put("cover", meta.coverUrl)
        if (meta.lyrics.isNotEmpty()) {
            val lines = JSONArray()
            for (l in meta.lyrics) {
                lines.put(JSONObject().put("text", l.text).put("s", l.startMs).put("e", l.endMs))
            }
            root.put("lyrics", lines)
        }
        root.toString()
    }.getOrDefault("")

    fun decode(json: String?): AudioMeta? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(json)
            val lines = mutableListOf<LyricLine>()
            val arr = o.optJSONArray("lyrics")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val l = arr.optJSONObject(i) ?: continue
                    val text = l.optString("text")
                    if (text.isNotBlank()) {
                        lines.add(LyricLine(text, l.optLong("s", 0L), l.optLong("e", 0L)))
                    }
                }
            }
            AudioMeta(
                title = o.optString("title"),
                artist = o.optString("artist"),
                coverUrl = o.optString("cover"),
                lyrics = lines
            )
        }.getOrNull()
    }

    /** 存檔注入：把 url(normalizeKey)→meta 的映射合進 rawJsonMin 根物件 */
    fun withAudioMeta(rawJson: String, metas: Map<String, AudioMeta>): String {
        if (metas.isEmpty()) return rawJson
        val root = runCatching { JSONObject(rawJson) }.getOrElse { JSONObject() }
        val arr = JSONArray()
        for ((url, meta) in metas) {
            val encoded = runCatching { JSONObject(encode(meta)) }.getOrElse { continue }
            encoded.put("url", url)
            arr.put(encoded)
        }
        root.put(KEY, arr)
        return root.toString()
    }

    /** 讀取 rawJsonMin 內的 audio_meta 映射（key = MediaDedup.normalizeKey(remoteUrl)） */
    fun parseMap(rawJson: String?): Map<String, AudioMeta> {
        if (rawJson.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(rawJson)
            val arr = root.optJSONArray(KEY) ?: return@runCatching emptyMap<String, AudioMeta>()
            val out = LinkedHashMap<String, AudioMeta>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url")
                // 移掉 url 鍵後再 decode，避免 url 混進 meta 欄位
                o.remove("url")
                val meta = decode(o.toString()) ?: continue
                if (url.isNotBlank()) out.putIfAbsent(url, meta)
            }
            out
        }.getOrDefault(emptyMap())
    }

    /** 以媒體網址反查 meta（normalizeKey 相容精確/正規化命中） */
    fun metaFor(rawJson: String?, mediaUrl: String): AudioMeta? =
        parseMap(rawJson)[MediaDedup.normalizeKey(mediaUrl)]

    /** url→metaJson 字串映射（UI 逐媒體挑選用） */
    fun toJsonUrlMap(rawJson: String?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((url, meta) in parseMap(rawJson)) out[url] = encode(meta)
        return out
    }

    /** 由 url→metaJson 映射挑出該媒體的 metaJson（normalizeKey 命中） */
    fun pickJson(urlMap: Map<String, String>, mediaUrl: String): String =
        urlMap[MediaDedup.normalizeKey(mediaUrl)].orEmpty()

    /** 歌詞全文 [mm:ss] 前綴格式（匯出 ©lyr / 詳情頁顯示用） */
    fun lyricsText(meta: AudioMeta): String {
        if (meta.lyrics.isEmpty()) return ""
        return meta.lyrics.joinToString("\n") { l ->
            val sec = (l.startMs.coerceAtLeast(0)) / 1000
            "[${sec / 60}:${(sec % 60).toString().padStart(2, '0')}] ${l.text}"
        }
    }
}
