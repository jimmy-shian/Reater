package com.reater.app.data.remote.threads

import com.reater.app.domain.AudioMetaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 以合成的 Threads SJS block 驗證音訊資產解析：
 * 曲名、歌手、封面、DASH MPD BaseURL XML 實體還原與逐時歌詞時間軸（含空行過濾）。
 */
class ThreadsSjsParserMusicMetaTest {

    @Test
    fun parsesMusicPostWithTimedLyricsAndDashBaseUrl() {
        val block = """
            {"data":{"media":{
              "code": "SynthAudio01",
                  "user": {
                    "username": "synth_tester",
                    "full_name": "Synth Tester"
                  },
                  "caption": {
                    "text": "合成測試音訊貼文"
                  },
                  "text_post_app_info": {
                    "is_reply": false,
                    "text_fragments": {
                      "fragments": [
                        {
                          "fragment_type": "plaintext",
                          "plaintext": "合成測試音訊貼文"
                        }
                      ]
                    }
                  },
                  "image_versions2": {
                    "candidates": []
                  },
                  "text_app_music_info": {
                    "music_consumable_video": {
                      "audio_metadata": {
                        "audio_asset": {
                          "display_title": { "text": "Synthetic Dawn" },
                          "display_artist": { "text": "Test Ensemble" },
                          "large_display_image": {
                            "downloadable_uri": "https://cdn.example.invalid/cover-large.jpg?dl=1"
                          },
                          "display_image": {
                            "downloadable_uri": "https://cdn.example.invalid/cover-small.jpg?dl=1"
                          },
                          "dash_manifest": "<MPD><Period><AdaptationSet mimeType=\"audio/mp4\"><Representation id=\"1\" mimeType=\"audio/mp4\"><BaseURL>https://cdn.example.invalid/audio.mp4?token=abc&amp;expires=123</BaseURL></Representation></AdaptationSet></Period></MPD>",
                          "lyrics": {
                            "available": true,
                            "lines": [
                              {
                                "line": { "text": "First synthetic lyric line" },
                                "start_time_in_ms": 500,
                                "end_time_in_ms": 1500
                              },
                              {
                                "line": { "text": "   " },
                                "start_time_in_ms": 1500,
                                "end_time_in_ms": 1800
                              },
                              {
                                "line": { "text": "Second synthetic lyric line" },
                                "start_time_in_ms": 2000,
                                "end_time_in_ms": 3200
                              }
                            ]
                          }
                        }
                      }
                    }
                  }
            }}}
        """.trimIndent()

        val result = ThreadsSjsParser.parseBlocks(listOf(block), "SynthAudio01")
            ?: throw AssertionError("parser returned null")

        assertTrue(result.bodyText.contains("合成測試音訊貼文"))

        val audio = result.media.filter { it.kind == "AUDIO" }
        assertEquals(1, audio.size)
        assertEquals(
            "https://cdn.example.invalid/audio.mp4?token=abc&expires=123",
            audio[0].remoteUrl
        )

        val covers = result.media.filter { it.kind == "IMAGE" }
        assertEquals(1, covers.size)
        assertEquals("https://cdn.example.invalid/cover-large.jpg?dl=1", covers[0].remoteUrl)

        val meta = AudioMetaStore.decode(audio[0].audioMetaJson)
            ?: throw AssertionError("audioMetaJson decode failed: ${audio[0].audioMetaJson}")
        assertEquals("Synthetic Dawn", meta.title)
        assertEquals("Test Ensemble", meta.artist)
        assertEquals("https://cdn.example.invalid/cover-large.jpg?dl=1", meta.coverUrl)
        assertEquals(2, meta.lyrics.size)
        assertEquals("First synthetic lyric line", meta.lyrics[0].text)
        assertEquals(500L, meta.lyrics[0].startMs)
        assertEquals(1500L, meta.lyrics[0].endMs)
        assertEquals("Second synthetic lyric line", meta.lyrics[1].text)
        assertEquals(2000L, meta.lyrics[1].startMs)
        assertEquals(3200L, meta.lyrics[1].endMs)
    }
}
