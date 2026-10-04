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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import kotlin.math.abs
import kotlin.math.min

/**
 * 頭像裁切編輯器：整張圖完整保留（Fit 不預裁），圓形範圍內單指拖曳＋雙指縮放選擇保留位置。
 * 縮放僅雙指手勢，不另設按鈕/滑桿避免干擾畫面；
 * 確認後回傳 512 正方形 Bitmap（未填滿處為透明，由呼叫方存成 PNG），由呼叫方存成新的時間戳內部檔。
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
        // Fit：整張完整保留，初始即顯示全圖（不預裁）
        return min(displayPx / source.width, displayPx / source.height)
    }

    fun clampOffset(scale: Float, raw: Offset): Offset {
        val t = baseScale() * scale
        val dispW = source.width * t
        val dispH = source.height * t
        // cover 時限制邊緣不露出空白；fit（letterbox）時允許在空白範圍內平移但不出框
        val maxX = (abs(dispW - displayPx) / 2).coerceAtLeast(0f)
        val maxY = (abs(dispH - displayPx) / 2).coerceAtLeast(0f)
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
                .wrapContentHeight()
                .sizeIn(maxHeight = 620.dp)
                .clip(RoundedCornerShape(18.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                    text = "拖曳移動・雙指縮放，圓內即頭像",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                // 正方形裁切區：整張 Fit 完整顯示不預裁，手勢疊加縮放位移。
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
                            contentScale = ContentScale.Fit,
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

                Spacer(modifier = Modifier.height(12.dp))
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
 * 依目前縮放/位移輸出正方形（OUTPUT_SIZE），與預覽所見一致：
 * Fit 完整保留全圖，未填滿處保持透明（存 PNG），填滿時等同裁切。
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
        val outSize = AvatarStorage.OUTPUT_SIZE
        val out = Bitmap.createBitmap(
            outSize,
            outSize,
            Bitmap.Config.ARGB_8888
        )
        // 保持透明底（letterbox 區域透明，呼叫方存 PNG 保留）
        out.eraseColor(android.graphics.Color.TRANSPARENT)
        val canvas = Canvas(out)
        val paint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
        val k = outSize / displayPx
        val dst = RectF(
            left * k,
            top * k,
            (left + dispW) * k,
            (top + dispH) * k
        )
        canvas.drawBitmap(source, null, dst, paint)
        out
    } catch (_: Exception) {
        null
    }
}
