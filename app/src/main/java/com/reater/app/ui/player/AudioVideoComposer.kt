package com.reater.app.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.Image
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 「純音訊 mp4 → 帶封面畫面的 mp4」合成器。
 *
 * - 視訊：封面圖讀取一次、letterbox 進 720x1280 畫布，轉成 I420 後整段影片
 *   反覆寫入（靜態畫面，1 fps、GOP 開到極長 → 檔案極小）；YUV420 buffer 直入
 *   MediaCodec 編碼器（不用 EGL surface，避免 wall-clock 時戳問題）。
 * - 音訊：MediaExtractor 原流 passthrough（不解碼不轉碼），
 *   HE-AAC 位流 + AudioSpecificConfig(csd-0) 直接交給 MediaMuxer。
 * - 兩軌以時間戳交錯寫入 MediaMuxer → 一般播放器/相冊可直接播放。
 *
 * 呼叫端負責放在 Dispatchers.IO（內部為阻塞式管線 + 小睡避免空轉）。
 */
object AudioVideoComposer {

    private const val CANVAS_W = 720
    private const val CANVAS_H = 1280
    private const val FPS = 1
    private const val BITRATE = 1_500_000
    private const val I_FRAME_INTERVAL_S = 200

    /** @return true = 合成成功且 outFile 已存在 */
    fun compose(audioFile: File, cover: Bitmap, outFile: File): Boolean {
        if (!audioFile.exists() || audioFile.length() == 0L) return false
        // 清理 out（可能殘留）
        runCatching { outFile.delete() }

        var muxer: MediaMuxer? = null
        var encoder: MediaCodec? = null
        var extractor: MediaExtractor? = null
        try {
            val durationMs = readDurationMs(audioFile)
            if (durationMs <= 0) return false
            // 視訊長度 = 音訊時長 + 2 秒緩衝，確保蓋住整段音訊
            val frameCount = ((durationMs / 1000L) + 2L).toInt().coerceAtLeast(3)

            val canvas = buildCanvasBitmap(cover)
            val yuv = bitmapToI420(canvas)

            extractor = MediaExtractor()
            extractor.setDataSource(audioFile.absolutePath)
            var audioIdx = -1
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                    audioIdx = i; break
                }
            }
            if (audioIdx < 0) return false
            extractor.selectTrack(audioIdx)
            val srcAudioFmt = extractor.getTrackFormat(audioIdx)
            val audioMime = srcAudioFmt.getString(MediaFormat.KEY_MIME).orEmpty()
            // 只保留 muxer 必要欄位，避免把不相容參數帶入
            val audioFmt = MediaFormat().apply {
                setString(MediaFormat.KEY_MIME, audioMime)
                setInteger(
                    MediaFormat.KEY_SAMPLE_RATE,
                    srcAudioFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                )
                setInteger(
                    MediaFormat.KEY_CHANNEL_COUNT,
                    srcAudioFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                )
                srcAudioFmt.getByteBuffer("csd-0")?.let { setByteBuffer("csd-0", it) }
            }

            val encFormat = MediaFormat.createVideoFormat("video/avc", CANVAS_W, CANVAS_H).apply {
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

            encoder = MediaCodec.createEncoderByType("video/avc")
            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val audioTrack = muxer.addTrack(audioFmt)
            var videoTrack = -1

            var inputEosSent = false
            var videoEos = false
            var nextFrame = 0
            var audioDone = false
            var pendingAudioTs: Long? = null
            var lastVideoWrittenUs = -1L
            val audioBuf = ByteBuffer.allocateDirect(1 shl 16)
            val audioInfo = MediaCodec.BufferInfo()
            val bufferInfo = MediaCodec.BufferInfo()
            val deadline = System.currentTimeMillis() + (frameCount * 250L).coerceAtLeast(120_000L)

            fun writeAudioUpTo(maxUs: Long) {
                if (videoTrack < 0) return
                while (!audioDone) {
                    if (pendingAudioTs == null) {
                        val ts = extractor.sampleTime
                        if (ts < 0) { audioDone = true; break }
                        audioBuf.clear()
                        val sz = extractor.readSampleData(audioBuf, 0)
                        if (sz < 0) { audioDone = true; break }
                        audioBuf.position(0)
                        audioBuf.limit(sz)
                        pendingAudioTs = ts
                    }
                    val ts = pendingAudioTs!!
                    if (ts > maxUs) break
                    audioInfo.set(0, audioBuf.limit(), ts, 0)
                    muxer.writeSampleData(audioTrack, audioBuf, audioInfo)
                    extractor.advance()
                    pendingAudioTs = null
                }
            }

            while (!videoEos || !audioDone) {
                if (System.currentTimeMillis() > deadline) return false
                var progressed = false

                // 1. 餵入下一視訊影格（1 fps）
                if (!inputEosSent) {
                    val inIdx = encoder.dequeueInputBuffer(5_000L)
                    if (inIdx >= 0) {
                        var flags = 0
                        // 首影格編碼器必然輸出關鍵影格，無需再帶 SYNC_FRAME／KEY_FRAME 旗標
                        if (nextFrame >= frameCount - 1) {
                            flags = flags or MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            inputEosSent = true
                        }
                        val img = encoder.getInputImage(inIdx)
                            ?: throw IllegalStateException("getInputImage returned null")
                        copyYuvIntoImage(img, yuv)
                        encoder.queueInputBuffer(
                            inIdx, 0, yuv.y.size, nextFrame * 1_000_000L, flags
                        )
                        nextFrame++
                        progressed = true
                    }
                }

                // 2. 收視訊輸出並按時戳寫入
                val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 5_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // 兩軌都 addTrack 完才能 start()：視訊輸出格式在此時才確定。
                        // 缺了 start() 會讓 writeSampleData 全數拋出 IllegalStateException → 合成失敗。
                        videoTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                    }
                    outIdx >= 0 -> {
                        val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!isConfig && videoTrack >= 0 && bufferInfo.size > 0) {
                            val buf = encoder.getOutputBuffer(outIdx)
                            if (buf != null) {
                                buf.position(bufferInfo.offset)
                                buf.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(videoTrack, buf, bufferInfo)
                                lastVideoWrittenUs = bufferInfo.presentationTimeUs
                                writeAudioUpTo(lastVideoWrittenUs + 1_000_000L)
                            }
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            videoEos = true
                        }
                        progressed = true
                    }
                }

                // 3. 兩軌都停滯就小睡一擋交錯，避免空轉燒 CPU
                if (!progressed) {
                    try { Thread.sleep(2) } catch (_: InterruptedException) {}
                }
            }

            // 尾聲：把剩餘音訊全部寫入（影片 EOS 後長度已蓋過音訊）
            writeAudioUpTo(Long.MAX_VALUE)
            if (!audioDone) return false

            muxer.stop()
            return outFile.exists() && outFile.length() > 0
        } catch (_: Throwable) {
            runCatching { if (outFile.exists()) outFile.delete() }
            return false
        } finally {
            runCatching { extractor?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { muxer?.release() }
        }
    }

    /** 靜態畫面整段重用：letterbox 進 720x1280 黑底畫布 */
    private fun buildCanvasBitmap(cover: Bitmap): Bitmap {
        val bmp = Bitmap.createBitmap(CANVAS_W, CANVAS_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)
        val scale = min(CANVAS_W.toFloat() / cover.width, CANVAS_H.toFloat() / cover.height)
        val dw = (cover.width * scale).roundToInt().coerceAtLeast(1)
        val dh = (cover.height * scale).roundToInt().coerceAtLeast(1)
        val left = (CANVAS_W - dw) / 2f
        val top = (CANVAS_H - dh) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(
            cover, null,
            RectF(left, top, left + dw, top + dh), paint
        )
        return bmp
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
