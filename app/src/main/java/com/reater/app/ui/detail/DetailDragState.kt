package com.reater.app.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.sign

/** 只接收內容捲動到邊界後未消耗的垂直手勢，水平 Pager 不參與。 */
@Stable
internal class DetailDragState(
    private val scroll: ScrollState,
    private val dismiss: () -> Unit
) : NestedScrollConnection {
    var offset by mutableFloatStateOf(0f)
        private set
    var containerHeight by mutableFloatStateOf(1f)
    private var settling by mutableStateOf(false)
    var pagerDragging by mutableStateOf(false)

    private fun drag(delta: Float) {
        val resistance = 1f + abs(offset) / (containerHeight * 0.3f).coerceAtLeast(1f)
        offset += delta / resistance
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (settling || pagerDragging || source != NestedScrollSource.UserInput) return Offset.Zero
        if (offset != 0f && available.y.sign != offset.sign) {
            val consumed = available.y.coerceIn(-abs(offset), abs(offset))
            offset += consumed
            return Offset(0f, consumed)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (settling || pagerDragging || source != NestedScrollSource.UserInput) return Offset.Zero
        val atBoundary = (available.y > 0 && !scroll.canScrollBackward) ||
            (available.y < 0 && !scroll.canScrollForward)
        if (!atBoundary || abs(available.y) <= abs(available.x)) return Offset.Zero
        drag(available.y)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (offset == 0f || settling) return Velocity.Zero
        settling = true
        val distance = abs(offset)
        val shouldDismiss = distance > containerHeight * 0.22f ||
            (distance > containerHeight * 0.06f && abs(available.y) > 1600f && available.y.sign == offset.sign)
        try {
            val animation = Animatable(offset)
            if (shouldDismiss) {
                animation.animateTo(offset.sign * containerHeight, tween(200)) { offset = value }
                dismiss()
            } else {
                animation.animateTo(0f, spring(dampingRatio = 0.78f)) { offset = value }
            }
        } finally { settling = false }
        return Velocity(0f, available.y)
    }
}
