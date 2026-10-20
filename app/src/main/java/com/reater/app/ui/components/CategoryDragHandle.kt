package com.reater.app.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reater.app.R

/**
 * 分類拖曳手柄：支援即時浮動替換手勢與無障礙操作。
 */
@Composable
fun CategoryDragHandle(
    id: Long,
    onDragStart: () -> Unit,
    onDrag: (deltaY: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onAccessibilityMove: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val handleStart by rememberUpdatedState(onDragStart)
    val handleDrag by rememberUpdatedState(onDrag)
    val handleEnd by rememberUpdatedState(onDragEnd)
    val handleCancel by rememberUpdatedState(onDragCancel)
    val handleMove by rememberUpdatedState(onAccessibilityMove)

    Box(
        modifier = modifier
            .size(48.dp)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("向上移動分類") { handleMove(-1); true },
                    CustomAccessibilityAction("向下移動分類") { handleMove(1); true }
                )
            }
            .pointerInput(id) {
                detectDragGestures(
                    onDragStart = { handleStart() },
                    onDrag = { change, delta ->
                        change.consume()
                        handleDrag(delta.y)
                    },
                    onDragEnd = { handleEnd() },
                    onDragCancel = { handleCancel() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_drag_handle),
            contentDescription = "拖曳排序",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 相容舊版簡易接口 */
@Composable
fun CategoryDragHandle(id: Long, onMove: (Int) -> Unit) {
    val move by rememberUpdatedState(onMove)
    val threshold = with(LocalDensity.current) { 58.dp.toPx() }
    Box(
        Modifier
            .size(48.dp)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("向上移動分類") { move(-1); true },
                    CustomAccessibilityAction("向下移動分類") { move(1); true }
                )
            }
            .pointerInput(id, threshold) {
                var distance = 0f
                detectDragGestures(
                    onDragStart = { distance = 0f },
                    onDrag = { change, delta ->
                        change.consume()
                        distance += delta.y
                        if (kotlin.math.abs(distance) >= threshold) {
                            val direction = if (distance > 0) 1 else -1
                            move(direction)
                            distance -= direction * threshold
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_drag_handle),
            contentDescription = "拖曳排序",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
