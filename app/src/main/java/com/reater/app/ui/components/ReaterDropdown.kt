package com.reater.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * 全站共用下拉選單動效（對應「下拉式選單動畫」規格）。
 *
 * - 觸發器：常態淺陰影，展開時陰影加深（shadow 2dp -> 10dp，300ms）
 * - 箭頭：展開時旋轉 180 度（300ms，與規格 `.arrow` 一致）
 * - 選項面板：由頂部垂直展開 scaleY + 淡入（300ms），收合時反向（250ms），
 *   對應規格 `.select-options` 的 transform-origin: top / opacity / visibility
 * - 選項列：hover/press 以 Material ripple + 選中底色回饋（對應 `.option:hover`）
 *
 * 用法：引用 [ReaterDropdownTrigger] + [ReaterDropdownPanel] + [ReaterDropdownOption] 即可，
 * 無需各自重寫動畫（Popup 式 M3 DropdownMenu 沿用平台內建 fade + scale，不需額外處理）。
 */
object ReaterDropdownMotion {
    const val ExpandMs = 300
    const val CollapseMs = 250

    val panelEnter
        @Composable get() = expandVertically(
            animationSpec = tween(ExpandMs, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top
        ) + fadeIn(animationSpec = tween(ExpandMs))

    val panelExit
        @Composable get() = shrinkVertically(
            animationSpec = tween(CollapseMs, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top
        ) + fadeOut(animationSpec = tween(CollapseMs))
}

/**
 * 規格箭頭：收合朝下，展開旋轉 180 度朝上（300ms）。
 * 所有下拉選單的觸發器箭頭請一律使用此元件，確保動效統一。
 */
@Composable
fun ReaterDropdownArrow(
    expanded: Boolean,
    modifier: Modifier = Modifier
) {
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(ReaterDropdownMotion.ExpandMs),
        label = "dropdownArrow"
    )
    Icon(
        imageVector = Icons.Default.ArrowDropDown,
        contentDescription = if (expanded) "收合選單" else "展開選單",
        tint = MaterialTheme.colorScheme.outline,
        modifier = modifier
            .size(22.dp)
            .rotate(rotation)
    )
}

/**
 * 規格觸發器：[圖示] 標題 ... 徽章 數值 [箭頭]。
 * 展開時陰影加深（對應規格 `.open .select-trigger`）。
 */
@Composable
fun ReaterDropdownTrigger(
    expanded: Boolean,
    onClick: () -> Unit,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    badge: @Composable (() -> Unit)? = null,
    contentExtra: (RowScope.() -> Unit)? = null
) {
    val elevation by animateDpAsState(
        targetValue = if (expanded) 8.dp else 1.dp,
        animationSpec = tween(ReaterDropdownMotion.ExpandMs, easing = FastOutSlowInEasing),
        label = "dropdownTriggerShadow"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .shadow(elevation, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.invoke()
            if (leading != null) Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            badge?.invoke()
            if (badge != null) Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
            contentExtra?.invoke(this)
            Spacer(modifier = Modifier.width(4.dp))
            ReaterDropdownArrow(expanded = expanded)
        }
    }
}

/**
 * 規格選項面板：由頂部展開 + 淡入，收合時反向（含 visibility 語義）。
 */
@Composable
fun ReaterDropdownPanel(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = expanded,
        enter = ReaterDropdownMotion.panelEnter,
        exit = ReaterDropdownMotion.panelExit,
        modifier = modifier.fillMaxWidth()
    ) {
        val scale by transition.animateFloat(transitionSpec = { tween(ReaterDropdownMotion.ExpandMs) }, label = "dropdownScaleY") {
            if (it == EnterExitState.Visible) 1f else 0f
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleY = scale; transformOrigin = TransformOrigin(0.5f, 0f) }
                .shadow(8.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                content = content
            )
        }
    }
}

/**
 * 規格選項列：整列可點，選中顯示底色 + 打勾（對應 `.option` / hover 回饋）。
 */
@Composable
fun ReaterDropdownOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    leading: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.invoke()
            if (leading != null) Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                if (subLabel != null) {
                    Text(
                        text = subLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 下拉面板內嵌的數字鍵盤自訂列：數字輸入 + 套用鈕。
 * 輸入僅保留數字，套用時由呼叫端解析並鉗制範圍。
 */
@Composable
fun ReaterDropdownNumberInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String,
    hint: String,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.filter { ch -> ch.isDigit() }) },
                label = { Text(label, fontSize = 12.sp) },
                suffix = { Text(suffix, fontSize = 12.sp) },
                supportingText = { Text(hint, fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onApply,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text("套用", fontSize = 13.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Keep the popup alive through the reverse animation; native popup handles outside tap and Back. */
@Composable
fun AnimatedDropdownPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    matchAnchorWidth: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val visible = remember { MutableTransitionState(false) }
    SideEffect { visible.targetState = expanded }
    if (visible.currentState || visible.targetState) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        var anchorWidthDp by remember { mutableStateOf<Dp>(0.dp) }
        Popup(
            popupPositionProvider = remember {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        with(density) {
                            anchorWidthDp = anchorBounds.width.toDp()
                        }
                        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                        val y = (anchorBounds.bottom + 4).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
                        return IntOffset(x, y)
                    }
                }
            },
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true)
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides density
            ) {
                AnimatedVisibility(
                    visibleState = visible,
                    enter = ReaterDropdownMotion.panelEnter,
                    exit = ReaterDropdownMotion.panelExit
                ) {
                    val scale by transition.animateFloat(
                        transitionSpec = { tween(ReaterDropdownMotion.ExpandMs) },
                        label = "popupScaleY"
                    ) {
                        if (it == EnterExitState.Visible) 1f else 0f
                    }
                    val surfaceModifier = if (matchAnchorWidth && anchorWidthDp.value > 0f) {
                        Modifier.width(anchorWidthDp)
                    } else {
                        Modifier.widthIn(max = 420.dp).padding(horizontal = 16.dp)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = surfaceModifier
                            .graphicsLayer { scaleY = scale; transformOrigin = TransformOrigin(0.5f, 0f) }
                    ) {
                        Column(content = content)
                    }
                }
            }
        }
    }
}
