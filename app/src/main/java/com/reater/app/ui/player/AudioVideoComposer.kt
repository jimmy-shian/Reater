package com.reater.app.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import com.reater.app.domain.SubtitleTextLayout
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 「純音訊 mp4 → 帶封面畫面 + 同步歌詞字幕的 mp4」合成器。
 *
 * - 視訊：封面圖 letterbox 進 720x1280 畫布、2 fps；依歌詞時間軸在畫面下方疊上
 *   當下那句字幕（[SubtitleTextLayout.fit] 自動換行縮字，**每行寬度必 ≤ 畫布寬
 *   減左右邊距，絕不超出**）。畫面只在字幕文字改變時重繪 + I420 轉換（同句共用），
 *   其餘重用 → 檔案小、速度快。YUV420 buffer 直入 MediaCodec 編碼器
 *   （不用 EGL surface，避免 wall-clock 時戳問題）。
 * - 音訊：**解碼成 PCM 後重編碼為 AAC-LC**。舊版把來源位流（Threads 為
 *   HE-AAC/mp4a.40.5）passthrough 進 MediaMuxer，但 Android 相容性只保證
 *   AAC-LC 必解，HE-AAC 為選配 → 不少裝置的相簿/VideoView 播放匯出檔變無聲。
 *   來源先解到暫存 PCM 檔，再邊讀邊以固定取樣數推導時戳（嚴格遞增）寫入。
 * - 收尾後以 MediaExtractor 驗證輸出：必須同時含視訊與音訊軌、音訊時長貼近來源
 *   （±5 秒），否則刪檔回報失敗——寧可失敗也不產出無聲影片。
 *
 * 呼叫端負責放在 Dispatchers.IO（內部為阻塞式管線 + 小睡避免空轉）。
 */
object AudioVideoComposer {

    private const val CANVAS_W = 720
    private const val CANVAS_H = 1280
    private const val FPS = 2
    private const val BITRATE = 1_500_000
    private const val I_FRAME_INTERVAL_S = 200

    // 封面與字幕整體上移，讓垂直視覺重心更平衡（720x1280 畫布）
    private const val COVER_VERTICAL_OFFSET_PX = -90f

    // 字幕版面：左右各留 48px → 文字最寬 624px（< 720 畫布寬）
    private const val SUB_MARGIN_PX = 48
    private const val SUB_BOTTOM_MARGIN_PX = 220
    private const val SUB_MAX_LINES = 3
    private const val SUB_SIZE_START = 46
    private const val SUB_SIZE_MIN = 26
    private const val SUB_SIZE_STEP = 6

    /** 一句字幕（毫秒時間軸；由呼叫端從 AudioMetaStore.LyricLine 轉入） */
    data class Cue(val text: String, val startMs: Long, val endMs: Long)

    private const val TAG = "AudioVideoComposer"

    /** 最近一次失敗原因（匯出 toast 顯示與診斷用；每次 compose 開始時清空） */
    @Volatile
    var lastFailure: String = ""
        private set

    private fun fail(reason: String): Boolean {
        lastFailure = reason
        Log.e(TAG, reason)
        return false
    }

    internal fun clearFailure() {
        lastFailure = ""
    }

    private class PcmMeta(val sampleRate: Int, val channels: Int)

    private class PendingSample(val bytes: ByteArray, val offset: Int, val size: Int, val ptsUs: Long, val flags: Int)

    /** @return true = 合成成功且 outFile 已存在（含音訊軌驗證通過） */
    fun compose(audioFile: File, cover: Bitmap, outFile: File, lyrics: List<Cue> = emptyList()): Boolean {
        lastFailure = ""
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return fail("音訊檔不存在或空檔: ${audioFile.name}")
        }
        // 清理 out（可能殘留）
        runCatching { outFile.delete() }

        var muxer: MediaMuxer? = null
        var videoEncoder: MediaCodec? = null
        var audioEncoder: MediaCodec? = null
        var pcmFile: File? = null
        var pcmChannel: FileChannel? = null
        try {
            val durationMs = readDurationMs(audioFile)
            if (durationMs <= 0) return fail("無法讀取音訊時長（格式不受支援？）")
            val cues = SubtitleTextLayout.normalizeCues(
                lyrics.map { SubtitleTextLayout.Cue(it.text, it.startMs, it.endMs) },
                durationMs
            )
            val frameDurUs = 1_000_000L / FPS
            val frameCount = ((durationMs * FPS + 999L) / 1000L + 2L * FPS).toInt().coerceAtLeast(FPS * 3)

            // ── 音訊 pass 1：解碼成 PCM 暫存檔（HE-AAC/任何 AAC 變體 → 原始 PCM）──
            pcmFile = File(outFile.parentFile, "reater_pcm_${System.currentTimeMillis()}.raw")
            val pcmMeta = decodeAudioToPcm(audioFile, pcmFile)
                ?: return fail("音訊解碼失敗（${lastFailure}）")
            val bytesPerFrame = pcmMeta.channels * 2

            // ── 視訊編碼器（含硬體相容降級，避免 VBR / 長 I-frame 間隔觸發 IllegalArgumentException）──
            videoEncoder = createVideoEncoder()

            // ── 音訊編碼器：AAC-LC（Android 必解格式），取樣率/聲道跟隨解碼輸出 ──
            val aEncFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", pcmMeta.sampleRate, pcmMeta.channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, if (pcmMeta.channels >= 2) 96_000 else 48_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
            }
            audioEncoder = MediaCodec.createEncoderByType("audio/mp4a-latm").also {
                it.configure(aEncFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                it.start()
            }

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            pcmChannel = FileInputStream(pcmFile).channel

            var videoTrack = -1
            var audioTrack = -1
            var started = false
            var videoFmt: MediaFormat? = null
            var audioFmt: MediaFormat? = null
            val pendingVideo = mutableListOf<PendingSample>()
            val pendingAudio = mutableListOf<PendingSample>()

            var inputEosSent = false
            var videoEos = false
            var audioInEos = false
            var audioEos = false
            var nextFrame = 0
            var pcmFramesFed = 0L

            // 字幕畫面快取：同句文字共用同一張 I420
            var cachedTextKey: String? = null
            var cachedI420: I420? = null

            val bufferInfo = MediaCodec.BufferInfo()
            val audioInfo = MediaCodec.BufferInfo()
            // 逾時防呆：音訊長度 + 90 秒（軟體編碼的模擬器/低階機也夠），下限 180 秒
            val deadline = System.currentTimeMillis() +
                (durationMs + 90_000L).coerceAtLeast(180_000L)

            fun startMuxerWhenReady() {
                if (started) return
                val vf = videoFmt ?: return
                val af = audioFmt ?: return
                videoTrack = muxer.addTrack(vf)
                audioTrack = muxer.addTrack(af)
                muxer.start()
                started = true
                // 防禦：start 前收到的輸出樣本補寫（正常情況為空）
                for (p in pendingVideo) {
                    muxer.writeSampleData(
                        videoTrack,
                        ByteBuffer.wrap(p.bytes, p.offset, p.size),
                        MediaCodec.BufferInfo().apply { set(p.offset, p.size, p.ptsUs, p.flags) }
                    )
                }
                pendingVideo.clear()
                for (p in pendingAudio) {
                    muxer.writeSampleData(
                        audioTrack,
                        ByteBuffer.wrap(p.bytes, p.offset, p.size),
                        MediaCodec.BufferInfo().apply { set(p.offset, p.size, p.ptsUs, p.flags) }
                    )
                }
                pendingAudio.clear()
            }

            while (!videoEos || !audioEos) {
                if (System.currentTimeMillis() > deadline) {
                    return fail(
                        "合成逾時（videoEos=$videoEos audioEos=$audioEos frame=$nextFrame/$frameCount " +
                            "pcmFed=$pcmFramesFed started=$started）"
                    )
                }
                var progressed = false

                // 1. 餵入下一視訊影格（2 fps，字幕隨時間軸變化）
                if (!inputEosSent) {
                    val inIdx = videoEncoder.dequeueInputBuffer(0L)
                    if (inIdx >= 0) {
                        var flags = 0
                        // 首影格編碼器必然輸出關鍵影格，無需再帶 SYNC_FRAME／KEY_FRAME 旗標
                        if (nextFrame >= frameCount - 1) {
                            flags = flags or MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            inputEosSent = true
                        }
                        val tMs = nextFrame * frameDurUs / 1000L
                        val text = activeCueText(cues, tMs)
                        // 取得當下字幕畫面（同句重用）
                        val i420 = if (cachedI420 == null || text != cachedTextKey) {
                            val bmp = buildCanvasBitmap(cover, text)
                            bitmapToI420(bmp).also {
                                bmp.recycle()
                                cachedI420 = it
                                cachedTextKey = text
                            }
                        } else {
                            cachedI420
                        }
                        // 先取實際 input buffer 容量，避免 semi-planar 裝置上 U/V rowStride 重複計算超出 capacity 觸發 IllegalArgumentException
                        val inputCap = videoEncoder.getInputBuffer(inIdx)?.capacity()
                            ?: (CANVAS_W * CANVAS_H * 3 / 2)
                        val img = videoEncoder.getInputImage(inIdx)
                            ?: throw IllegalStateException("getInputImage returned null")
                        copyYuvIntoImage(img, i420)
                        videoEncoder.queueInputBuffer(
                            inIdx, 0, inputCap, nextFrame * frameDurUs, flags
                        )
                        nextFrame++
                        progressed = true
                    }
                }

                // 2. 收視訊輸出
                while (true) {
                    val outIdx = videoEncoder.dequeueOutputBuffer(bufferInfo, 0L)
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        videoFmt = videoEncoder.outputFormat
                        startMuxerWhenReady()
                        progressed = true
                        continue
                    }
                    val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (isConfig) {
                        videoEncoder.releaseOutputBuffer(outIdx, false)
                        continue
                    }
                    if (bufferInfo.size > 0) {
                        if (started && videoTrack >= 0) {
                            val buf = videoEncoder.getOutputBuffer(outIdx)!!
                            buf.position(bufferInfo.offset)
                            buf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrack, buf, bufferInfo)
                        } else if (!started) {
                            pendingVideo.add(pendingOf(videoEncoder.getOutputBuffer(outIdx)!!, bufferInfo))
                        }
                    }
                    videoEncoder.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        videoEos = true
                    }
                    progressed = true
                }

                // 3. 餵音訊（PCM 暫存檔 → AAC-LC 編碼器；時戳由取樣數推導）
                if (!audioInEos) {
                    val inIdx = audioEncoder.dequeueInputBuffer(0L)
                    if (inIdx >= 0) {
                        val buf = audioEncoder.getInputBuffer(inIdx)!!
                        val n = pcmChannel.read(buf)
                        if (n < 0) {
                            audioEncoder.queueInputBuffer(
                                inIdx, 0, 0,
                                pcmFramesFed * 1_000_000L / pcmMeta.sampleRate,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            audioInEos = true
                        } else {
                            val bytes = (n / bytesPerFrame) * bytesPerFrame
                            audioEncoder.queueInputBuffer(
                                inIdx, 0, bytes,
                                pcmFramesFed * 1_000_000L / pcmMeta.sampleRate, 0
                            )
                            pcmFramesFed += bytes / bytesPerFrame
                        }
                        progressed = true
                    }
                }

                // 4. 收音訊輸出
                while (true) {
                    val outIdx = audioEncoder.dequeueOutputBuffer(audioInfo, 0L)
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        audioFmt = audioEncoder.outputFormat
                        startMuxerWhenReady()
                        progressed = true
                        continue
                    }
                    val isConfig = (audioInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (isConfig) {
                        audioEncoder.releaseOutputBuffer(outIdx, false)
                        continue
                    }
                    if (audioInfo.size > 0) {
                        if (started && audioTrack >= 0) {
                            val buf = audioEncoder.getOutputBuffer(outIdx)!!
                            buf.position(audioInfo.offset)
                            buf.limit(audioInfo.offset + audioInfo.size)
                            muxer.writeSampleData(audioTrack, buf, audioInfo)
                        } else if (!started) {
                            pendingAudio.add(pendingOf(audioEncoder.getOutputBuffer(outIdx)!!, audioInfo))
                        }
                    }
                    audioEncoder.releaseOutputBuffer(outIdx, false)
                    if ((audioInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        audioEos = true
                    }
                    progressed = true
                }

                // 5. 兩邊都停滯就小睡一擋，避免空轉燒 CPU
                if (!progressed) {
                    try { Thread.sleep(2) } catch (_: InterruptedException) {}
                }
            }

            muxer.stop()
            // 產出驗證：沒有音訊軌或時長對不上 → 判定失敗（不交付無聲影片）
            if (!verifyOutput(outFile, durationMs)) {
                val reason = "輸出驗證失敗（${lastFailure.ifBlank { "無音訊軌或時長異常" }}）"
                runCatching { outFile.delete() }
                return fail(reason)
            }
            if (!outFile.exists() || outFile.length() == 0L) return fail("輸出檔缺失或空檔")
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "compose exception", t)
            lastFailure = "例外: ${t.javaClass.simpleName}: ${t.message}"
            runCatching { if (outFile.exists()) outFile.delete() }
            return false
        } finally {
            runCatching { pcmChannel?.close() }
            runCatching { pcmFile?.delete() }
            runCatching { videoEncoder?.stop() }
            runCatching { videoEncoder?.release() }
            runCatching { audioEncoder?.stop() }
            runCatching { audioEncoder?.release() }
            runCatching { muxer?.release() }
        }
    }

    /** 找出 tMs 當下該顯示的字幕句；句間空檔回 null */
    internal fun activeCueText(cues: List<SubtitleTextLayout.Cue>, tMs: Long): String? {
        var i = 0
        var active: String? = null
        while (i < cues.size) {
            val c = cues[i]
            if (c.startMs > tMs) break
            if (tMs < c.endMs) active = c.text
            i++
        }
        return active
    }

    /**
     * 音訊 pass 1：解碼來源音軌 → PCM16 原生位元組序列寫入 pcmOut。
     * 取樣率/聲道以解碼器輸出格式為準（HE-AAC 會自動套用 SBR → 輸出實際播放率）。
     */
    private fun decodeAudioToPcm(src: File, pcmOut: File): PcmMeta? {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var out: FileOutputStream? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(src.absolutePath)
            var audioIdx = -1
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                    audioIdx = i; break
                }
            }
            if (audioIdx < 0) {
                lastFailure = "來源無音訊軌"
                return null
            }
            extractor.selectTrack(audioIdx)
            val srcFmt = extractor.getTrackFormat(audioIdx)
            val mime = srcFmt.getString(MediaFormat.KEY_MIME)
            if (mime == null) {
                lastFailure = "音訊軌無 mime"
                return null
            }

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(srcFmt, null, null, 0)
            decoder.start()

            out = FileOutputStream(pcmOut)
            val outCh = out.channel
            val info = MediaCodec.BufferInfo()
            var inEos = false
            var outEos = false
            var sampleRate = -1
            var channels = -1

            while (!outEos) {
                if (!inEos) {
                    val inIdx = decoder.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        val sz = extractor.readSampleData(buf, 0)
                        if (sz < 0) {
                            decoder.queueInputBuffer(
                                inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inEos = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, sz, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = decoder.dequeueOutputBuffer(info, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = decoder.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    outIdx >= 0 -> {
                        if (info.size > 0 && sampleRate > 0) {
                            val b = decoder.getOutputBuffer(outIdx)!!
                            b.position(info.offset)
                            b.limit(info.offset + info.size)
                            while (b.hasRemaining()) outCh.write(b)
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outEos = true
                    }
                }
            }
            return if (sampleRate > 0 && channels > 0 && pcmOut.length() > 0) {
                PcmMeta(sampleRate, channels)
            } else {
                lastFailure = "解碼輸出無效（rate=$sampleRate ch=$channels pcmBytes=${pcmOut.length()}）"
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "decodeAudioToPcm exception", t)
            lastFailure = "解碼例外: ${t.javaClass.simpleName}: ${t.message}"
            return null
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor?.release() }
            runCatching { out?.close() }
        }
    }

    /** 產出驗證：需有視訊軌 + 音訊軌，且音訊時長與來源差距 ≤ 5 秒 */
    private fun verifyOutput(out: File, expectedAudioMs: Long): Boolean {
        var ex: MediaExtractor? = null
        try {
            ex = MediaExtractor()
            ex.setDataSource(out.absolutePath)
            var hasVideo = false
            var hasAudio = false
            for (i in 0 until ex.trackCount) {
                val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) hasVideo = true
                else if (mime.startsWith("audio/")) { hasAudio = true; ex.selectTrack(i) }
            }
            if (!hasVideo || !hasAudio) {
                lastFailure = "hasVideo=$hasVideo hasAudio=$hasAudio"
                return false
            }
            var maxUs = 0L
            while (true) {
                val ts = ex.sampleTime
                if (ts < 0) break
                if (ts > maxUs) maxUs = ts
                if (!ex.advance()) break
            }
            val ok = maxUs >= 1_000_000L && abs(maxUs / 1000L - expectedAudioMs) <= 5_000L
            if (!ok) {
                lastFailure = "音訊時長 ${maxUs / 1000L}ms 與來源 ${expectedAudioMs}ms 差異過大"
            }
            return ok
        } catch (t: Throwable) {
            Log.e(TAG, "verifyOutput exception", t)
            lastFailure = "驗證例外: ${t.javaClass.simpleName}: ${t.message}"
            return false
        } finally {
            runCatching { ex?.release() }
        }
    }

    private fun pendingOf(buf: java.nio.ByteBuffer, info: MediaCodec.BufferInfo): PendingSample {
        // buffer 隨後即被 releaseOutputBuffer 回收，讀完不必（也不可）回復 position/limit
        val bytes = ByteArray(info.size)
        buf.position(info.offset)
        buf.limit(info.offset + info.size)
        buf.get(bytes)
        return PendingSample(bytes, 0, info.size, info.presentationTimeUs, info.flags)
    }

    /** 靜態畫面：letterbox 進 720x1280 黑底畫布，下方疊當下字幕（可為 null） */
    private fun buildCanvasBitmap(cover: Bitmap, subtitleText: String?): Bitmap {
        val bmp = Bitmap.createBitmap(CANVAS_W, CANVAS_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)
        val scale = min(CANVAS_W.toFloat() / cover.width, CANVAS_H.toFloat() / cover.height)
        val dw = (cover.width * scale).roundToInt().coerceAtLeast(1)
        val dh = (cover.height * scale).roundToInt().coerceAtLeast(1)
        val left = (CANVAS_W - dw) / 2f
        val minTop = (CANVAS_H - dh).toFloat().coerceIn(0f, 48f)
        val top = ((CANVAS_H - dh) / 2f + COVER_VERTICAL_OFFSET_PX).coerceAtLeast(minTop)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(
            cover, null,
            RectF(left, top, left + dw, top + dh), paint
        )
        if (!subtitleText.isNullOrBlank()) drawSubtitle(canvas, subtitleText)
        return bmp
    }

    /**
     * 字幕繪製：自動換行 + 必要時縮小字級，行數上限 [SUB_MAX_LINES]；
     * 每行量測寬度必 ≤ CANVAS_W - 2×邊距（黑描邊 + 白字，蓋在封面任何底色上都可讀）。
     */
    private fun drawSubtitle(canvas: Canvas, text: String) {
        val maxTextWidth = (CANVAS_W - 2 * SUB_MARGIN_PX).toDouble()
        val boldTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTypeface
        }
        val (size, lines) = SubtitleTextLayout.fit(
            text, maxTextWidth, SUB_MAX_LINES,
            SUB_SIZE_START, SUB_SIZE_MIN, SUB_SIZE_STEP
        ) { s, px ->
            val stroke = (px / 7f).coerceAtLeast(3f)
            measurePaint.textSize = px.toFloat()
            measurePaint.measureText(s).toDouble() + stroke
        }
        if (lines.isEmpty()) return
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size.toFloat()
            typeface = boldTypeface
            textAlign = Paint.Align.CENTER
        }
        val strokePaint = Paint(textPaint).apply {
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = (size / 7f).coerceAtLeast(3f)
            color = Color.BLACK
        }
        val lineH = size * 1.4f
        val blockH = lineH * lines.size
        val bottom = CANVAS_H - SUB_BOTTOM_MARGIN_PX
        val cx = CANVAS_W / 2f
        var y = bottom - blockH + lineH * 0.78f
        for (line in lines) {
            canvas.drawText(line, cx, y, strokePaint)
            y += lineH
        }
        y = bottom - blockH + lineH * 0.78f
        textPaint.color = Color.WHITE
        for (line in lines) {
            canvas.drawText(line, cx, y, textPaint)
            y += lineH
        }
    }

    /**
     * 建立並啟動 H.264 編碼器：優先嘗試 VBR + 長關鍵影格間隔，若硬體編碼器
     * 拒絕參數（IllegalArgumentException），依序降級為安全格式或其他可用編碼器。
     */
    private fun createVideoEncoder(): MediaCodec {
        val primaryFmt = MediaFormat.createVideoFormat("video/avc", CANVAS_W, CANVAS_H).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_S)
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            setInteger(
                MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
            )
        }
        val safeFmt = MediaFormat.createVideoFormat("video/avc", CANVAS_W, CANVAS_H).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
        }
        val defaultCodec = MediaCodec.createEncoderByType("video/avc")
        try {
            defaultCodec.configure(primaryFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            defaultCodec.start()
            return defaultCodec
        } catch (_: Throwable) {
            runCatching { defaultCodec.release() }
        }
        val avcEncoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals("video/avc", true) } }
            .sortedBy { if (it.name.startsWith("c2.android.") || it.name.startsWith("OMX.google.")) 1 else 0 }
        var lastErr: Throwable? = null
        for (info in avcEncoders) {
            for (fmt in listOf(safeFmt, primaryFmt)) {
                val codec = runCatching { MediaCodec.createByCodecName(info.name) }.getOrNull() ?: continue
                try {
                    codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    codec.start()
                    return codec
                } catch (t: Throwable) {
                    lastErr = t
                    runCatching { codec.release() }
                }
            }
        }
        throw lastErr ?: IllegalStateException("找不到可用的 video/avc 編碼器")
    }

    /** ARGB → I420（BT.601，與一般 Encoder 預設一致）；回傳三平面陣列 */
    private fun bitmapToI420(bmp: Bitmap): I420 {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val yArr = ByteArray(w * h)
        val cw = w / 2
        val ch = h / 2
        val uArr = ByteArray(cw * ch)
        val vArr = ByteArray(cw * ch)
        var uIdx = 0
        var vIdx = 0
        var yIdx = 0
        for (row in 0 until h) {
            for (col in 0 until w) {
                val p = pixels[yIdx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yArr[yIdx] = y.coerceIn(0, 255).toByte()
                yIdx++
                if (row % 2 == 0 && col % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    uArr[uIdx++] = u.coerceIn(0, 255).toByte()
                    vArr[vIdx++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return I420(w, h, yArr, uArr, vArr, cw)
    }

    /** 可能是 3 平面（planar）或 2+interleave（semiplanar），都支援 */
    private fun copyYuvIntoImage(img: Image, src: I420) {
        val planes = img.planes
        copyPlane(planes[0], src.y, src.width, src.height, src.width, 1, null)
        when {
            planes.size >= 3 -> {
                copyPlane(planes[1], src.u, src.chromaW, src.height / 2, src.chromaW, 1, null)
                copyPlane(planes[2], src.v, src.chromaW, src.height / 2, src.chromaW, 1, null)
            }
            planes.size == 2 -> {
                // semiplanar：單一 UV 平面，pixelStride=2 交錯
                copyPlane(planes[1], src.u, src.chromaW, src.height / 2, src.chromaW, 1, src.v)
            }
        }
    }

    /** 逐列拷貝，處理 rowStride / pixelStride；interleave 為 UV 平面成對資料 */
    private fun copyPlane(
        plane: Image.Plane,
        srcData: ByteArray,
        srcStride: Int,
        rows: Int,
        rowBytes: Int,
        srcPixelStep: Int,
        interleave: ByteArray?
    ) {
        val stride = plane.rowStride
        val pixStride = plane.pixelStride
        val buf = plane.buffer
        var srcRowStart = 0
        for (r in 0 until rows) {
            val dstRow = r * stride
            if (pixStride == 1) {
                for (c in 0 until rowBytes) {
                    buf.put(dstRow + c, srcData[srcRowStart + c])
                }
            } else {
                for (c in 0 until rowBytes) {
                    val di = dstRow + c * pixStride
                    buf.put(di, srcData[srcRowStart + c])
                    if (interleave != null) buf.put(di + 1, interleave[srcRowStart + c])
                }
            }
            srcRowStart += srcStride
        }
    }

    /** 音訊時長：MediaMetadataRetriever 優先；失敗則採全程 sample 掃描 */
    private fun readDurationMs(file: File): Long {
        var ms = runCatching {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(file.absolutePath)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L
            } finally {
                runCatching { r.release() }
            }
        }.getOrDefault(-1L)
        if (ms <= 0) {
            ms = runCatching {
                val ex = MediaExtractor()
                try {
                    ex.setDataSource(file.absolutePath)
                    var max = 0L
                    while (true) {
                        val ts = ex.sampleTime
                        if (ts < 0) break
                        if (ts > max) max = ts
                        ex.advance()
                    }
                    max / 1000
                } finally {
                    runCatching { ex.release() }
                }
            }.getOrDefault(-1L)
        }
        return ms
    }

    data class I420(
        val width: Int,
        val height: Int,
        val y: ByteArray,
        val u: ByteArray,
        val v: ByteArray,
        val chromaW: Int
    )
}
