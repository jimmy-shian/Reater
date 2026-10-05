package com.reater.app.ui.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reater.app.ui.MainViewModel
import com.reater.app.ui.components.shortDayLabel

@Composable
fun AnalyticsScreen(
    viewModel: MainViewModel,
    isPro: Boolean,
    onOpenUnlock: () -> Unit
) {
    val state by viewModel.analytics.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAnalytics()
    }

    if (state.loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // 當前在圖表上被選取的日期項目 (用於互動式即時反饋)
    var selectedIndex by remember(state.dailySaved) {
        mutableStateOf(if (state.dailySaved.isNotEmpty()) state.dailySaved.size - 1 else -1)
    }

    val selectedDayData = if (selectedIndex in state.dailySaved.indices) {
        state.dailySaved[selectedIndex]
    } else null

    val totalPeriodSaves = remember(state.dailySaved) {
        state.dailySaved.sumOf { it.second }
    }

    val maxDailySave = remember(state.dailySaved) {
        state.dailySaved.maxOfOrNull { it.second } ?: 0
    }

    val avgDailySave = remember(state.dailySaved) {
        if (state.dailySaved.isNotEmpty()) {
            String.format(java.util.Locale.US, "%.1f", totalPeriodSaves.toFloat() / state.dailySaved.size)
        } else "0.0"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 核心指標卡片
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "今日儲存",
                    value = state.savedToday.toString(),
                    icon = Icons.Default.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "今日打開",
                    value = state.openedToday.toString(),
                    icon = Icons.Default.AccessTime,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "累計回顧",
                    value = state.totalReviews.toString(),
                    icon = Icons.Default.AutoAwesome,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 標題與說明
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPro) "儲存趨勢分析（近 30 天）" else "儲存趨勢分析（本週）",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (isPro) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFB300), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PRO 完整",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "點擊圖表中任一長條或日期點，可查看即時資料次數與佔比",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        if (state.dailySaved.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "期間內尚無儲存記錄",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            // 互動式複合圖表
            item {
                InteractiveAnalyticsChart(
                    data = state.dailySaved,
                    selectedIndex = selectedIndex,
                    onSelectIndex = { selectedIndex = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
            }

            // 互動選取反饋卡片
            if (selectedDayData != null) {
                item {
                    val (day, count) = selectedDayData
                    val percent = if (totalPeriodSaves > 0) {
                        (count.toFloat() / totalPeriodSaves * 100).toInt()
                    } else 0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = day,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "佔期間總儲存 $percent%",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = count.toString(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "則文章",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 統計摘要洞察
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InsightPill(
                        label = "期間總量",
                        value = "${totalPeriodSaves} 則",
                        modifier = Modifier.weight(1f)
                    )
                    InsightPill(
                        label = "單日最高",
                        value = "${maxDailySave} 則",
                        modifier = Modifier.weight(1f)
                    )
                    InsightPill(
                        label = "每日平均",
                        value = "${avgDailySave} 則",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 時段頻率分佈 (Time & Frequency Distribution)
        item {
            TimeFrequencySection(hourlyData = state.hourlySaved)
        }

        if (!isPro) {
            item {
                OutlinedButton(
                    onClick = onOpenUnlock,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("解鎖 Pro・開啟近 30 天與無期限趨勢")
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun InsightPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * 互動式長條 + 折線圖表
 * 具備明確 Y 軸數值、X 軸日期以及點擊選取互動高亮效果
 */
@Composable
private fun InteractiveAnalyticsChart(
    data: List<Pair<String, Int>>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val highlightColor = Color(0xFFFFB300)

    val maxVal = remember(data) {
        (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)
        ) {
            // Y 軸標籤說明
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "次數 (則)", fontSize = 12.sp, color = outline)
                if (selectedIndex in data.indices) {
                    Text(
                        text = "選取：${data[selectedIndex].first} (${data[selectedIndex].second} 則)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Y 軸數值標籤（0, mid, max）
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(text = maxVal.toString(), fontSize = 12.sp, color = outline)
                    Text(text = (maxVal / 2).toString(), fontSize = 12.sp, color = outline)
                    Text(text = "0", fontSize = 12.sp, color = outline)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Canvas 繪製直條與折線，支援點擊計算
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(data) {
                            detectTapGestures { offset ->
                                val n = data.size
                                if (n > 0) {
                                    val stepX = size.width / n.toFloat()
                                    val tappedIndex = (offset.x / stepX).toInt().coerceIn(0, n - 1)
                                    onSelectIndex(tappedIndex)
                                }
                            }
                        }
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        if (data.isEmpty()) return@Canvas
                        val n = data.size
                        val max = maxVal.toFloat()
                        val stepX = size.width / n.coerceAtLeast(1)
                        val barW = (stepX * 0.55f).coerceIn(6f, 24f)

                        // 橫向輔助網格線 (0, 50%, 100%)
                        for (g in 0..2) {
                            val y = size.height * 0.95f - (size.height * 0.85f * g / 2f)
                            drawLine(
                                color = surfaceVariant,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f
                            )
                        }

                        // 繪製直條 Bar
                        data.forEachIndexed { i, (_, cnt) ->
                            val isSelected = i == selectedIndex
                            val barHeight = (size.height * 0.85f * cnt / max).coerceAtLeast(if (cnt > 0) 6f else 0f)
                            val x = stepX * i + (stepX - barW) / 2f
                            val y = size.height * 0.95f - barHeight

                            // 選取高亮背景光暈
                            if (isSelected) {
                                drawRoundRect(
                                    color = highlightColor.copy(alpha = 0.25f),
                                    topLeft = Offset(stepX * i, 0f),
                                    size = Size(stepX, size.height),
                                    cornerRadius = CornerRadius(4f, 4f)
                                )
                            }

                            drawRoundRect(
                                color = if (isSelected) highlightColor else primary.copy(alpha = 0.8f),
                                topLeft = Offset(x, y),
                                size = Size(barW, barHeight),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }

                        // 繪製折線與節點
                        if (n > 1) {
                            val pts = data.mapIndexed { i, (_, cnt) ->
                                Offset(
                                    stepX * i + stepX / 2f,
                                    size.height * 0.95f - (size.height * 0.85f * cnt / max)
                                )
                            }

                            for (i in 0 until pts.size - 1) {
                                drawLine(
                                    color = primary.copy(alpha = 0.9f),
                                    start = pts[i],
                                    end = pts[i + 1],
                                    strokeWidth = 3f
                                )
                            }

                            pts.forEachIndexed { i, p ->
                                val isSelected = i == selectedIndex
                                val r = if (isSelected) 8f else 5.5f
                                drawCircle(
                                    color = if (isSelected) highlightColor else primary,
                                    radius = r,
                                    center = p
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = r * 0.5f,
                                    center = p
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // X 軸日期標籤
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 20.dp)
                    .padding(start = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                val showStep = when {
                    data.size <= 7 -> 1
                    data.size <= 14 -> 2
                    else -> 4
                }
                data.forEachIndexed { i, (day, _) ->
                    if (i % showStep == 0 || i == data.size - 1) {
                        Text(
                            text = shortDayLabel(day),
                            fontSize = 12.sp,
                            fontWeight = if (i == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                            color = if (i == selectedIndex) primary else outline,
                            maxLines = 1,
                            modifier = Modifier.clickable { onSelectIndex(i) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 時段與頻率分佈 (Time & Frequency Distribution)
 * 將 24 小時分為四大時段，展示使用者儲存文章的高峰與習慣
 */
@Composable
private fun TimeFrequencySection(
    hourlyData: List<Pair<Int, Int>>
) {
    // 聚合為四大時段
    val morningCount = hourlyData.filter { it.first in 6..11 }.sumOf { it.second }
    val afternoonCount = hourlyData.filter { it.first in 12..17 }.sumOf { it.second }
    val eveningCount = hourlyData.filter { it.first in 18..23 }.sumOf { it.second }
    val nightCount = hourlyData.filter { it.first in 0..5 }.sumOf { it.second }

    val total = (morningCount + afternoonCount + eveningCount + nightCount).coerceAtLeast(1)

    val slots = listOf(
        TimeSlotInfo("晨間時段", "06:00 - 12:00", morningCount, morningCount.toFloat() / total),
        TimeSlotInfo("午後時段", "12:00 - 18:00", afternoonCount, afternoonCount.toFloat() / total),
        TimeSlotInfo("晚間黃金", "18:00 - 24:00", eveningCount, eveningCount.toFloat() / total),
        TimeSlotInfo("深夜寧靜", "00:00 - 06:00", nightCount, nightCount.toFloat() / total)
    )

    val peakSlot = slots.maxByOrNull { it.count }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "時段與儲存頻率分佈",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "分析您一整天中最常閱讀與收藏內容的時段",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (peakSlot != null && peakSlot.count > 0) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "高峰：${peakSlot.title}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            slots.forEach { slot ->
                val percentInt = (slot.ratio * 100).toInt()
                val isPeak = slot == peakSlot && slot.count > 0

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = slot.title,
                                fontSize = 13.sp,
                                fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = slot.timeRange,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = "${slot.count} 則 ($percentInt%)",
                            fontSize = 12.sp,
                            fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal,
                            color = if (isPeak) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { slot.ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (isPeak) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

private data class TimeSlotInfo(
    val title: String,
    val timeRange: String,
    val count: Int,
    val ratio: Float
)
