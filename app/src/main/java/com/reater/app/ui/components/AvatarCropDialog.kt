package com.reater.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reater.app.ui.AvatarStorage
import kotlin.math.max

/**
 * 頭像裁切編輯器：圓形範圍內雙指縮放＋單指拖曳選擇要保留的位置，
 * 取代過去「直接正中間 Crop」的行為。確認後回傳 512 正方形 Bitmap，
 * 由呼叫方存成新的時間戳內部檔（保證內外即時同步刷新）。
 */
@Composable
fun AvatarCropDialog(
    source: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    var userScale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var displayPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    fun baseScale(): Float {
        if (source.width <= 0 || source.height <= 0 || displayPx <= 0) return 1f
        return max(displayPx / source.width, displayPx / source.height)
    }

    fun clampOffset(scale: Float, raw: Offset): Offset {
        val t = baseScale() * scale
        val dispW = source.width * t
        val dispH = source.height * t
        val maxX = ((dispW - displayPx) / 2).coerceAtLeast(0f)
        val maxY = ((dispH - displayPx) / 2).coerceAtLeast(0f)
        return Offset(
            raw.x.coerceIn(-maxX, maxX),
            raw.y.coerceIn(-maxY, maxY)
        )
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (userScale * zoomChange).coerceIn(1f, 4f)
        userScale = newScale
        offset = clampOffset(newScale, offset + panChange)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(18.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "編輯頭像位置",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "關閉編輯",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "單指拖曳移動・雙指縮放，下方滑桿也可微調",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .onSizeChanged { displayPx = it.width.toFloat() }
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    val bs = baseScale()
                    val imgWdp = with(density) { (source.width * bs).toDp() }
                    val imgHdp = with(density) { (source.height * bs).toDp() }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = remember(source) { source.asImageBitmap() },
                            contentDescription = "待裁切圖片",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .size(imgWdp, imgHdp)
                                .graphicsLayer {
                                    scaleX = userScale
                                    scaleY = userScale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                                .transformable(transformState)
                        )
                    }

                    // 圓形裁切遮罩：圓外壓暗 + 金色描邊，圓內即最終頭像範圍
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = Path().apply {
                            addRect(Rect(Offset.Zero, Size(size.width, size.height)))
                            addOval(Rect(Offset.Zero, Size(size.width, size.height)))
                            fillType = PathFillType.EvenOdd
                        }
                        drawPath(path, Color(0xFF111827).copy(alpha = 0.55f))
                        drawCircle(
                            color = Color(0xFFFFB300),
                            radius = size.minDimension / 2 - 2.dp.toPx(),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = userScale,
                        onValueChange = { v ->
                            val ns = v.coerceIn(1f, 4f)
                            userScale = ns
                            offset = clampOffset(ns, offset)
                        },
                        valueRange = 1f..4f,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消", fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            cropSquare(source, userScale, offset, displayPx, baseScale())?.let {
                                onConfirm(it)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        enabled = displayPx > 0
                    ) {
                        Text("套用此範圍", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * 依目前縮放/位移把來源圖裁出正方形（輸出 OUTPUT_SIZE），對應圓形遮罩內範圍。
 */
fun cropSquare(
    source: Bitmap,
    userScale: Float,
    offsetPx: Offset,
    displayPx: Float,
    baseScale: Float
): Bitmap? {
    return try {
        val t = baseScale * userScale.coerceIn(1f, 4f)
        if (t <= 0f || displayPx <= 0) return null
        val dispW = source.width * t
        val dispH = source.height * t
        val left = (displayPx / 2 + offsetPx.x) - dispW / 2
        val top = (displayPx / 2 + offsetPx.y) - dispH / 2
        var srcLeft = -left / t
        var srcTop = -top / t
        var srcSize = displayPx / t
        srcLeft = srcLeft.coerceIn(0f, source.width.toFloat())
        srcTop = srcTop.coerceIn(0f, source.height.toFloat())
        if (srcLeft + srcSize > source.width) srcSize = source.width - srcLeft
        if (srcTop + srcSize > source.height) srcSize = source.height - srcTop
        if (srcSize <= 0) return null
        val srcRect = android.graphics.Rect(
            srcLeft.toInt().coerceIn(0, source.width - 1),
            srcTop.toInt().coerceIn(0, source.height - 1),
            (srcLeft + srcSize).toInt().coerceIn(1, source.width),
            (srcTop + srcSize).toInt().coerceIn(1, source.height)
        )
        if (srcRect.width() <= 0 || srcRect.height() <= 0) return null
        val out = Bitmap.createBitmap(
            AvatarStorage.OUTPUT_SIZE,
            AvatarStorage.OUTPUT_SIZE,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(out)
        val paint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
        canvas.drawBitmap(
            source,
            srcRect,
            RectF(0f, 0f, out.width.toFloat(), out.height.toFloat()),
            paint
        )
        out
    } catch (_: Exception) {
        null
    }
}
