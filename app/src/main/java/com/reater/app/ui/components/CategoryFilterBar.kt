package com.reater.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.reater.app.R
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.domain.UrlParser

/**
 * 分類篩選列：緊湊 Dropdown、套用全域字體 Theme Typography、寬度對齊 Header、
 * 限制最大高度並支援內部捲動。
 * 與「全部 / Threads / IG」平台切換（3 合 1 圖示按鈕）並排在同一列，
 * 組成雙重篩選：平台 × 分類。
 */
@Composable
fun CategoryFilterBar(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    counts: Map<Long?, Int> = emptyMap(),
    totalCount: Int = 0,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    /** 平台篩選："ALL" | UrlParser.PLATFORM_THREADS | UrlParser.PLATFORM_INSTAGRAM */
    selectedPlatform: String = "ALL",
    onPlatformSelect: (String) -> Unit = {},
    /** 各平台計數（地圖 key：ALL / THREADS / INSTAGRAM），僅供圖示的無障礙說明 */
    platformCounts: Map<String, Int> = emptyMap()
) {
    var expanded by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(active) { if (!active) expanded = false }

    val label = when (selectedId) {
        null -> "全部分類"
        -1L -> "未分類"
        else -> categories.firstOrNull { it.id == selectedId }?.name ?: "全部分類"
    }

    val configuration = LocalConfiguration.current
    val maxDropdownListHeight = (configuration.screenHeightDp * 0.42f).dp.coerceIn(180.dp, 280.dp)

    // 單列排版：左側「3 合 1」平台切換按鈕 + 右側原本的標籤（分類）選擇器
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlatformSwitchButton(
            selectedPlatform = selectedPlatform,
            onPlatformSelect = onPlatformSelect,
            platformCounts = platformCounts
        )

        Spacer(modifier = Modifier.width(8.dp))

        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val barWidth = maxWidth
            ReaterDropdownTrigger(
                expanded = expanded,
                onClick = { expanded = !expanded },
                title = label,
                value = "${if (selectedId == null) totalCount else counts[selectedId] ?: 0} 筆",
                compact = true,
                leading = {
                    if (selectedId != null && selectedId != -1L) {
                        CategoryIcon(categories.firstOrNull { it.id == selectedId }, modifier = Modifier.size(18.dp))
                    }
                }
            )

            AnimatedDropdownPopup(
                expanded = expanded,
                onDismiss = { expanded = false; query = "" },
                matchAnchorWidth = true,
                anchorWidth = barWidth
            ) {

            // 緊湊搜尋欄 (38dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .height(38.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "搜尋分類",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { query = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清除",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 分類列表（超過可用高度內部捲動）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDropdownListHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                fun select(id: Long?) {
                    onSelect(id)
                    expanded = false
                    query = ""
                }

                // 系統項目：全部分類與未分類
                ReaterDropdownOption(
                    text = "全部分類",
                    selected = selectedId == null,
                    compact = true,
                    subLabel = "$totalCount 筆",
                    onClick = { select(null) }
                )
                if (query.isBlank() || "未分類".contains(query.trim(), ignoreCase = true)) {
                    ReaterDropdownOption(
                        text = "未分類",
                        selected = selectedId == -1L,
                        compact = true,
                        subLabel = "${counts[-1L] ?: 0} 筆",
                        onClick = { select(-1L) }
                    )
                }

                // 分隔線：將系統篩選與自訂實體分類分開
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // 實體自訂分類
                val filtered = categories.filter { it.name.contains(query.trim(), ignoreCase = true) }
                filtered.forEach { cat ->
                    ReaterDropdownOption(
                        text = cat.name,
                        selected = selectedId == cat.id,
                        compact = true,
                        subLabel = "${counts[cat.id] ?: 0} 筆",
                        leading = { CategoryIcon(cat, modifier = Modifier.size(16.dp)) },
                        onClick = { select(cat.id) }
                    )
                }

                if (filtered.isEmpty() && query.isNotBlank() && !"未分類".contains(query.trim(), ignoreCase = true)) {
                    Text(
                        text = "找不到相符分類",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            }
        }
    }
}

/**
 * 平台切換按鈕（3 合 1）：**單一**緊湊按鈕，以 SVG 圖示代表目前的平台篩選，
 * 點擊後依「全部 → Threads → Instagram → 全部」循環切換，圖示同步變化。
 * - 僅顯示圖示，各平台筆數由右側「全部分類」下拉顯示，文字不再擠壓或裁切。
 * - 與分類篩選組成雙重篩選：平台 × 分類。
 */
@Composable
internal fun PlatformSwitchButton(
    selectedPlatform: String,
    onPlatformSelect: (String) -> Unit,
    platformCounts: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        PlatformOption("全部", "ALL", R.drawable.ic_platform_all),
        PlatformOption("Threads", UrlParser.PLATFORM_THREADS, R.drawable.ic_threads),
        PlatformOption("Instagram", UrlParser.PLATFORM_INSTAGRAM, R.drawable.ic_instagram)
    )
    val currentIndex = options.indexOfFirst { it.key == selectedPlatform }.coerceAtLeast(0)
    val current = options[currentIndex]
    val next = options[(currentIndex + 1) % options.size]
    val count = platformCounts[current.key] ?: 0

    Surface(
        onClick = { onPlatformSelect(next.key) },
        modifier = modifier.size(42.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 切換時以淡入淡出 + 縮放做圖示變化動效
            Crossfade(targetState = current.key, label = "platformIcon") { key ->
                val option = options.first { it.key == key }
                Icon(
                    painter = painterResource(option.iconRes),
                    contentDescription = "平台篩選：${option.label}（$count 筆），點擊切換至${next.label}",
                    modifier = Modifier.size(21.dp),
                    tint = if (option.key == "ALL") MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 平台切換項目：顯示名稱、篩選鍵值與圖示資源。 */
private data class PlatformOption(
    val label: String,
    val key: String,
    val iconRes: Int
)
