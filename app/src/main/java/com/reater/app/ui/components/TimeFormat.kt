package com.reater.app.ui.components

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val savedFmt = SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault())
private val dayFmt = SimpleDateFormat("M/d", Locale.getDefault())

/** 儲存時間：sourceFetchedAt 即首次存入 Reater 的時間 */
fun formatSavedTime(millis: Long): String {
    if (millis <= 0L) return "時間未知"
    return "儲存於 " + savedFmt.format(Date(millis))
}

/** 今日 00:00（local）millis */
fun startOfToday(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** 本週一 00:00（local）millis：免費版分析僅看當週 */
fun startOfWeek(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    }.timeInMillis
}

/** 趨勢圖日標籤 yyyy-MM-dd → M/d */
fun shortDayLabel(day: String): String {
    return runCatching {
        val p = day.split("-")
        "${p[1].trimStart('0')}/${p[2].trimStart('0')}"
    }.getOrDefault(day)
}
