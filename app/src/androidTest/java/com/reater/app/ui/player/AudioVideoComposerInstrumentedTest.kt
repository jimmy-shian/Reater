package com.reater.app.ui.player

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * 以動態產生的 2 秒合成 AAC 音檔驗證 AudioVideoComposer 全管線：
 * 解碼 → AAC-LC 重編碼、封面 + 同步歌詞字幕視訊、輸出雙軌驗證。
 */
@RunWith(AndroidJUnit4::class)
class AudioVideoComposerInstrumentedTest {

    @Test
    fun composesSyntheticAacAudioWithSyncedLyrics() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        val audio = File(context.cacheDir, "synthetic_test_audio.m4a")
        val out = File(context.cacheDir, "composed_synthetic_test.mp4")
        val cover = Bitmap.createBitmap(720, 960, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFFCC3333.toInt())
        }

        try {
            createSyntheticM4a(audio, durationMs = 2_000)
            assertTrue("合成音訊產生失敗", audio.exists() && audio.length() > 0L)

            val lyrics = listOf(
                AudioVideoComposer.Cue("First synthetic subtitle line", 100L, 950L),
                AudioVideoComposer.Cue("Second synthetic subtitle line", 1_000L, 1_900L)
            )

            val ok = AudioVideoComposer.compose(audio, cover, out, lyrics)
            assertTrue("合成失敗: ${AudioVideoComposer.lastFailure}", ok)
            assertTrue("輸出空檔", out.length() > 0L)

            val ex = MediaExtractor()
            try {
                ex.setDataSource(out.absolutePath)
                val mimes = (0 until ex.trackCount).mapNotNull { i ->
                    ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                }
                assertTrue("缺視訊軌: $mimes", mimes.any { it.startsWith("video/") })
                val audioMime = mimes.firstOrNull { it.startsWith("audio/") }
                assertTrue("缺音訊軌: $mimes", audioMime != null)
                assertTrue("音訊應為 AAC-LC: $mimes", audioMime == "audio/mp4a-latm")
            } finally {
                ex.release()
            }
        } finally {
            cover.recycle()
            audio.delete()
            out.delete()
        }
    }

    private fun createSyntheticM4a(outFile: File, durationMs: Int) {
        if (outFile.exists()) outFile.delete()
        val sampleRate = 44_100
        val channels = 1
        val totalSamples = (sampleRate.toLong() * durationMs / 1000L).toInt()

        val format = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }

        val encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
        var muxer: MediaMuxer? = null
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()
            var trackIndex = -1
            var muxerStarted = false
            var samplesSent = 0
            var inputEos = false
            var outputEos = false
            val chunkSamples = 1024

            while (!outputEos) {
                if (!inputEos) {
                    val inIdx = encoder.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = encoder.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val remaining = totalSamples - samplesSent
                        if (remaining <= 0) {
                            val eosPtsUs = samplesSent * 1_000_000L / sampleRate
                            encoder.queueInputBuffer(
                                inIdx, 0, 0, eosPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEos = true
                        } else {
                            val maxSamples = inBuf.remaining() / 2
                            val count = minOf(chunkSamples, remaining, maxSamples)
                            inBuf.order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until count) {
                                val sampleIndex = samplesSent + i
                                val angle = 2.0 * PI * 440.0 * sampleIndex / sampleRate
                                val pcm = (sin(angle) * 12000.0).toInt().toShort()
                                inBuf.putShort(pcm)
                            }
                            val ptsUs = samplesSent * 1_000_000L / sampleRate
                            encoder.queueInputBuffer(inIdx, 0, count * 2, ptsUs, 0)
                            samplesSent += count
                        }
                    }
                }

                while (true) {
                    val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 0L)
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                        continue
                    }
                    val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (!isConfig && bufferInfo.size > 0 && muxerStarted) {
                        val outBuf: ByteBuffer = encoder.getOutputBuffer(outIdx)!!
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEos = true
                    }
                }
            }
            if (muxerStarted) {
                muxer.stop()
            }
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { muxer?.release() }
        }
    }
}
