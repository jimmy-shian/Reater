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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reater.app.ui.AvatarStorage
import kotlin.math.max

/**
 * 頭像裁切編輯器：圓形範圍內單指拖曳＋雙指縮放（或 −/滑桿/＋）選擇保留位置。
 * 圖片以 ContentScale.Crop 鋪底（保證不變形），手勢只疊加縮放與位移；
 * 確認後回傳 512 正方形 Bitmap，由呼叫方存成新的時間戳內部檔。
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

    fun setScale(ns: Float) {
        val clamped = ns.coerceIn(1f, 4f)
        userScale = clamped
        offset = clampOffset(clamped, offset)
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        setScale(userScale * zoomChange)
        offset = clampOffset(userScale, offset + panChange)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(18.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "關閉編輯",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "拖曳移動・雙指或按鈕縮放，圓內即頭像",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                // 正方形裁切區：圖片 Crop 鋪底不變形，手勢疊加縮放位移。
                // sizeIn 限寬（先於 aspectRatio）保證任何螢幕都維持正方形。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(maxWidth = 440.dp)
                        .aspectRatio(1f)
                        .onSizeChanged { displayPx = it.width.toFloat() }
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = remember(source) { source.asImageBitmap() },
                            contentDescription = "待裁切圖片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
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
                            radius = size.minDimension / 2 - with(density) { 2.dp.toPx() },
                            style = Stroke(width = with(density) { 3.dp.toPx() })
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                // 縮放控制：− / 滑桿 / ＋，單行不換行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { setScale(userScale - 0.25f) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "縮小",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Slider(
                        value = userScale,
                        onValueChange = { setScale(it) },
                        valueRange = 1f..4f,
                        steps = 11,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { setScale(userScale + 0.25f) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "放大",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
                        Text(
                            "取消",
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                        Text(
                            "套用",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
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
