package com.reater.app.data.remote.threads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

/**
 * Threads 音訊（聲音/音樂）貼文解析驗證。
 *
 * 依據 2026-11 實測（share/BAckR3y4Sp → DfTrNv0D9nw，music 貼文）：
 *  - 貼文 image_versions2.candidates 為空、video_versions 為 null
 *  - 聲音在 text_app_music_info.music_consumable_video.audio_metadata.audio_asset.dash_manifest
 *    （MPD XML 內 BaseURL 為可直接播放的 audio-only mp4，URL 參數帶 & 實體）
 *  - 封面在 large_display_image / display_image 的 downloadable_uri
 */
class ThreadsSjsParserAudioTest {

    private fun musicPostJson(
        code: String,
        mpd: String,
        caption: String = "甚至用拉丁文念禱文"
    ): JSONObject {
        val asset = JSONObject()
            .put("dash_manifest", mpd)
            .put("display_title", JSONObject().put("text", "Our Solemn Hour"))
            .put("display_artist", JSONObject().put("text", "Within Temptation"))
            .put(
                "large_display_image",
                JSONObject().put("downloadable_uri", "https://scontent.example/cover_large.jpg?dl=1")
            )
            .put(
                "display_image",
                JSONObject().put("downloadable_uri", "https://scontent.example/cover_small.jpg?dl=1")
            )
        return JSONObject()
            .put("code", code)
            .put("caption", JSONObject().put("text", caption))
            .put("user", JSONObject().put("username", "stormyfeather1011"))
            .put("media_type", 19)
            .put("image_versions2", JSONObject().put("candidates", JSONArray()))
            .put("video_versions", JSONObject.NULL)
            .put("has_audio", JSONObject.NULL)
            .put(
                "text_app_music_info",
                JSONObject()
                    .put("is_available_for_consumption", true)
                    .put(
                        "music_consumable_video",
                        JSONObject().put("audio_metadata", JSONObject().put("audio_asset", asset))
                    )
            )
    }

    private fun blockOf(post: JSONObject): String =
        JSONObject().put("data", JSONObject().put("media", post)).toString()

    @Test
    fun extractsAudioAndCoverFromMusicAttachment() {
        // URL 參數帶 & 實體（MPD XML 內），需還原成 &，否則 CDN 參數被截斷
        val mpd = "<MPD><Period><AdaptationSet contentType=\"audio\">" +
            "<Representation id=\"a\" bandwidth=\"49244\" codecs=\"mp4a.40.5\" mimeType=\"audio/mp4\">" +
            "<BaseURL>https://video.example/o1/v/t2/m69/AQ.mp4?strext=1&_nc_cat=108&oe=6AF0</BaseURL>" +
            "</Representation></AdaptationSet></Period></MPD>"
        val result = ThreadsSjsParser.parseBlocks(listOf(blockOf(musicPostJson("DfTrNv0D9nw", mpd))), "DfTrNv0D9nw")!!

        assertEquals(2, result.media.size)
        val audio = result.media[0]
        assertEquals("AUDIO", audio.kind)
        assertEquals("https://video.example/o1/v/t2/m69/AQ.mp4?strext=1&_nc_cat=108&oe=6AF0", audio.remoteUrl)
        val cover = result.media[1]
        assertEquals("IMAGE", cover.kind)
        assertEquals("https://scontent.example/cover_large.jpg?dl=1", cover.remoteUrl)
        assertEquals("甚至用拉丁文念禱文", result.bodyText)
        assertEquals("stormyfeather1011", result.authorHandle)
    }

    @Test
    fun prefersAudioRepresentationWhenManifestMixesVideoAndAudio() {
        val mpd = "<MPD><Period>" +
            "<AdaptationSet contentType=\"video\">" +
            "<Representation id=\"v\" mimeType=\"video/mp4\">" +
            "<BaseURL>https://video.example/wrong-video.mp4</BaseURL>" +
            "</Representation></AdaptationSet>" +
            "<AdaptationSet contentType=\"audio\">" +
            "<Representation id=\"a\" mimeType=\"audio/mp4\">" +
            "<BaseURL>https://video.example/right-audio.mp4?_nc=1</BaseURL>" +
            "</Representation></AdaptationSet>" +
            "</Period></MPD>"
        val result = ThreadsSjsParser.parseBlocks(listOf(blockOf(musicPostJson("DfTrNv0D9nw", mpd))), "DfTrNv0D9nw")!!
        assertEquals("https://video.example/right-audio.mp4?_nc=1", result.media[0].remoteUrl)
    }

    @Test
    fun videoPostWithMusicInfoStillReturnsVideoOnly() {
        // 有影片的貼文即使帶音樂資訊，也只取影片（影片本身已含聲音）
        val mpd = "<MPD><AdaptationSet contentType=\"audio\"><Representation mimeType=\"audio/mp4\">" +
            "<BaseURL>https://video.example/audio.mp4</BaseURL>" +
            "</Representation></AdaptationSet></MPD>"
        val post = musicPostJson("Dd1Ry_jlKfT", mpd)
            .put(
                "video_versions",
                JSONArray().put(JSONObject().put("url", "https://video.example/real-video.mp4"))
            )
        val result = ThreadsSjsParser.parseBlocks(listOf(blockOf(post)), "Dd1Ry_jlKfT")!!
        assertEquals(1, result.media.size)
        assertEquals("VIDEO", result.media[0].kind)
        assertEquals("https://video.example/real-video.mp4", result.media[0].remoteUrl)
    }

    @Test
    fun genericAudioObjectFallbackProducesAudioMedia() {
        // 未來語音貼文容錯：post.audio{audio_src}
        val post = JSONObject()
            .put("code", "DaBcDeFgHiJ")
            .put("caption", JSONObject().put("text", "voice post"))
            .put("user", JSONObject().put("username", "someone"))
            .put("audio", JSONObject().put("audio_src", "https://cdn.example/voice.mp4"))
        val result = ThreadsSjsParser.parseBlocks(listOf(blockOf(post)), "DaBcDeFgHiJ")!!
        assertEquals(1, result.media.size)
        assertEquals("AUDIO", result.media[0].kind)
        assertEquals("https://cdn.example/voice.mp4", result.media[0].remoteUrl)
    }

    @Test
    fun musicPostWithoutBaseUrlStillKeepsCoverAndText() {
        // dash manifest 無 BaseURL（抓不到聲音）：封面與主文仍完整保留
        val mpd = "<MPD><Period></Period></MPD>"
        val result = ThreadsSjsParser.parseBlocks(listOf(blockOf(musicPostJson("DfTrNv0D9nw", mpd))), "DfTrNv0D9nw")!!
        assertEquals(1, result.media.size)
        assertEquals("IMAGE", result.media[0].kind)
        assertEquals("https://scontent.example/cover_large.jpg?dl=1", result.media[0].remoteUrl)
        assertEquals("甚至用拉丁文念禱文", result.bodyText)
    }
}
