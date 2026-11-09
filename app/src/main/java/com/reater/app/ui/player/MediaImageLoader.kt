package com.reater.app.ui.player

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder

/** 共用 Coil 圖片解碼：GIF 使用平台特定 decoder，影片使用 VideoFrameDecoder */
@Composable
fun rememberMediaImageLoader(): ImageLoader {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        ImageLoader.Builder(context).components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
            else add(GifDecoder.Factory())
            add(VideoFrameDecoder.Factory())
        }.build()
    }
}
