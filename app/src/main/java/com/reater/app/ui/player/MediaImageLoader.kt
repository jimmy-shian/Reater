package com.reater.app.ui.player

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

/** 共用 Coil 圖片解碼；GIF 使用平台動態 decoder，舊版 Android 使用 GifDecoder。 */
@Composable
fun rememberMediaImageLoader(): ImageLoader {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        ImageLoader.Builder(context).components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
            else add(GifDecoder.Factory())
        }.build()
    }
}
