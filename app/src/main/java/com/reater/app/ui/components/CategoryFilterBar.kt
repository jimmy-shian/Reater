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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reater.app.R
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.domain.UrlParser

/**
 * 分類篩選列：緊湊 Dropdown、套用全域字體 Theme Typography、寬度對齊 Header、
 * 限制最大高度並支援內部捲動。
 * 與「平台切換」（動態來源篩選鈕）並排在同一列，
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
    /** 平台篩選："ALL" | THREADS | INSTAGRAM | X | FACEBOOK | YOUTUBE */
    selectedPlatform: String = "ALL",
    onPlatformSelect: (String) -> Unit = {},
    /** 各平台計數（地圖 key：ALL / THREADS / INSTAGRAM / X / FACEBOOK / YOUTUBE） */
    platformCounts: Map<String, Int> = emptyMap()
) {
    var expanded by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(active) { if (!active) expanded = false }

    val allPlatformLabel = stringResource(R.string.filter_all)
    val allOptions = remember(allPlatformLabel) {
        listOf(
            PlatformOption(allPlatformLabel, "ALL", R.drawable.ic_platform_all),
            PlatformOption("Threads", UrlParser.PLATFORM_THREADS, R.drawable.ic_threads),
            PlatformOption("Instagram", UrlParser.PLATFORM_INSTAGRAM, R.drawable.ic_instagram),
            PlatformOption("X", UrlParser.PLATFORM_X, R.drawable.ic_x),
            PlatformOption("Facebook", UrlParser.PLATFORM_FACEBOOK, R.drawable.ic_facebook),
            PlatformOption("YouTube", UrlParser.PLATFORM_YOUTUBE, R.drawable.ic_youtube)
        )
    }

    // 只保留「有存文章」的平台
    val activePlatforms = remember(platformCounts) {
        allOptions.filter { it.key != "ALL" && (platformCounts[it.key] ?: 0) > 0 }
    }

    // 若使用者只存了 1 種平台（或 0 種），直接隱藏平台篩選按鈕
    val showPlatformButton = activePlatforms.size > 1

    val availableOptions = remember(activePlatforms) {
        listOf(allOptions.first { it.key == "ALL" }) + activePlatforms
    }

    val allCategoriesLabel = stringResource(R.string.category_filter_all_categories)
    val uncategorizedLabel = stringResource(R.string.filter_uncategorized)
    val label = when (selectedId) {
        null -> allCategoriesLabel
        -1L -> uncategorizedLabel
        else -> categories.firstOrNull { it.id == selectedId }?.name ?: allCategoriesLabel
    }

    val configuration = LocalConfiguration.current
    val maxDropdownListHeight = (configuration.screenHeightDp * 0.42f).dp.coerceIn(180.dp, 280.dp)

    // 單列排版：左側平台切換按鈕（僅在有多種平台時出現） + 右側分類選擇器
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showPlatformButton) {
            PlatformSwitchButton(
                selectedPlatform = selectedPlatform,
                onPlatformSelect = onPlatformSelect,
                platformCounts = platformCounts,
                availableOptions = availableOptions
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val barWidth = maxWidth
            ReaterDropdownTrigger(
                expanded = expanded,
                onClick = { expanded = !expanded },
                title = label,
                value = stringResource(R.string.category_filter_item_count, if (selectedId == null) totalCount else counts[selectedId] ?: 0),
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
                                stringResource(R.string.category_filter_search_hint),
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
                                contentDescription = stringResource(R.string.category_filter_clear_desc),
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
                    text = allCategoriesLabel,
                    selected = selectedId == null,
                    compact = true,
                    subLabel = stringResource(R.string.category_filter_item_count, totalCount),
                    onClick = { select(null) }
                )
                if (query.isBlank() || "未分類".contains(query.trim(), ignoreCase = true)) {
                    ReaterDropdownOption(
                        text = uncategorizedLabel,
                        selected = selectedId == -1L,
                        compact = true,
                        subLabel = stringResource(R.string.category_filter_item_count, counts[-1L] ?: 0),
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
                        subLabel = stringResource(R.string.category_filter_item_count, counts[cat.id] ?: 0),
                        leading = { CategoryIcon(cat, modifier = Modifier.size(16.dp)) },
                        onClick = { select(cat.id) }
                    )
                }

                if (filtered.isEmpty() && query.isNotBlank() && !"未分類".contains(query.trim(), ignoreCase = true)) {
                    Text(
                        text = stringResource(R.string.category_dropdown_no_match),
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
 * 平台切換按鈕：
 * - 當選項 <= 3（例如 全部 + Threads + IG = 3）：點擊按順序循環切換。
 * - 當選項 > 3（例如 全部 + Threads + IG + X = 4）：點擊展開下拉選單供直接選擇。
 */
@Composable
internal fun PlatformSwitchButton(
    selectedPlatform: String,
    onPlatformSelect: (String) -> Unit,
    platformCounts: Map<String, Int>,
    availableOptions: List<PlatformOption>,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val currentIndex = availableOptions.indexOfFirst { it.key == selectedPlatform }.let {
        if (it >= 0) it else 0
    }
    val current = availableOptions[currentIndex]
    val count = platformCounts[current.key] ?: 0

    val configuration = LocalConfiguration.current
    val maxListHeight = (configuration.screenHeightDp * 0.42f).dp.coerceIn(180.dp, 280.dp)

    Box(modifier = modifier) {
        Surface(
            onClick = { menuExpanded = !menuExpanded },
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Crossfade(targetState = current.key, label = "platformIcon") { key ->
                    val option = availableOptions.firstOrNull { it.key == key } ?: current
                    Icon(
                        painter = painterResource(option.iconRes),
                        contentDescription = stringResource(R.string.category_filter_platform_desc, option.label, count),
                        modifier = Modifier.size(21.dp),
                        tint = if (option.key == "ALL") MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        AnimatedDropdownPopup(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            matchAnchorWidth = false,
            anchorWidth = 200.dp
        ) {
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .heightIn(max = maxListHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                availableOptions.forEach { opt ->
                    val optCount = platformCounts[opt.key] ?: 0
                    ReaterDropdownOption(
                        text = opt.label,
                        selected = opt.key == selectedPlatform,
                        compact = true,
                        subLabel = "$optCount 筆",
                        leading = {
                            Icon(
                                painter = painterResource(opt.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (opt.key == selectedPlatform) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = {
                            onPlatformSelect(opt.key)
                            menuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

internal data class PlatformOption(
    val label: String,
    val key: String,
    val iconRes: Int
)
