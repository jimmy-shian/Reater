package com.reater.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.app.AlarmManager
import androidx.core.content.ContextCompat
import com.reater.app.data.local.entity.ItemDetail
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.reater.app.R
import com.reater.app.ui.MainActivity
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 通知中樞：頻道、權限、未讀提醒排程、每日回顧排程。
 */
object NotifyCenter {
    const val CHANNEL_ID = "reater_reminder"
    const val CHANNEL_DOWNLOAD = "reater_download"
    const val UNREAD_WORK = "reater_unread_nudge"
    const val REVIEW_WORK = "reater_review_digest"
    const val DOWNLOAD_NOTIF_BASE = 30000
    const val EXPORT_NOTIF_BASE = 40000
    const val ACTION_TRIGGER_REVIEW_DIGEST = "com.reater.app.action.TRIGGER_REVIEW_DIGEST"
    const val REQUEST_CODE_REVIEW_DIGEST_ALARM = 1001

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notify_channel_reading_reminder),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.notify_channel_reading_reminder_desc) }
            )
        }
        ensureDownloadChannel(context)
    }

    fun ensureDownloadChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_DOWNLOAD) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DOWNLOAD,
                    context.getString(R.string.notify_channel_offline_download),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = context.getString(R.string.notify_channel_offline_download_desc) }
            )
        }
    }



    fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    const val EXTRA_OPEN_ITEM_ID = "open_item_id"

    private fun openAppIntent(context: Context, itemId: Long? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (itemId != null && itemId > 0) {
                putExtra(EXTRA_OPEN_ITEM_ID, itemId)
            }
        }
        val requestCode = if (itemId != null && itemId > 0) (itemId % 100000).toInt() else 0
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun postUnreadNudge(context: Context, itemId: Long, title: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val note = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reater)
            .setContentTitle(context.getString(R.string.notify_content_unread_title))
            .setContentText(if (title.isBlank()) context.getString(R.string.notify_content_unread_text_default) else context.getString(R.string.notify_content_unread_text_with_title, title))
            .setContentIntent(openAppIntent(context, itemId))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1000 + (itemId % 100000).toInt(), note)
        } catch (_: SecurityException) {
            // 權限可能在檢查後被撤回；略過通知，讓背景工作正常結束。
        }
    }

    fun postReviewDigest(context: Context, unreadCount: Int, mainItem: ItemDetail? = null) {
        if (!canNotify(context) || unreadCount <= 0) return
        ensureChannel(context)

        val titleText = "未讀 ${unreadCount} 篇"
        val snippet = mainItem?.displayBody?.trim()?.replace("\n", " ")?.take(60)?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.notify_content_unread_text_default)
        val author = mainItem?.item?.authorDisplayName?.takeIf { it.isNotBlank() }
            ?: mainItem?.item?.authorHandle?.takeIf { it.isNotBlank() }
        val contentText = if (author != null) "$author：$snippet" else snippet

        val bigText = if (mainItem != null) {
            if (author != null) context.getString(R.string.notify_content_unread_title_with_author, author, snippet) else context.getString(R.string.notify_content_unread_text_no_author, snippet)
        } else {
            context.getString(R.string.notify_content_unread_summary, unreadCount)
        }

        val note = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reater)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(titleText)
                    .setSummaryText(context.getString(R.string.notify_content_unread_summary_total, unreadCount))
                    .bigText(bigText)
            )
            .setContentIntent(openAppIntent(context, mainItem?.item?.id))
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(9999, note)
        } catch (_: SecurityException) {
            // 權限可能在檢查後被撤回；略過通知，讓背景工作正常結束。
        }
    }

    fun postAiAnalysisComplete(context: Context, itemId: Long, title: String, summary: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val note = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reater)
            .setContentTitle(if (title.isBlank()) context.getString(R.string.notify_ai_done_title) else context.getString(R.string.notify_ai_done_title_with_name, title))
            .setContentText(summary.ifBlank { context.getString(R.string.notify_content_analysis_done) })
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.ifBlank { context.getString(R.string.notify_content_analysis_done) }))
            .setContentIntent(openAppIntent(context, itemId))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(20000 + (itemId % 10000).toInt(), note)
        } catch (_: SecurityException) {
        }
    }

    fun postAiAnalysisFailed(context: Context, itemId: Long, title: String, errorMessage: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val note = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reater)
            .setContentTitle(if (title.isBlank()) context.getString(R.string.notify_ai_failed_title) else context.getString(R.string.notify_ai_failed_title_with_name, title))
            .setContentText(errorMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(errorMessage))
            .setContentIntent(openAppIntent(context, itemId))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(20000 + (itemId % 10000).toInt(), note)
        } catch (_: SecurityException) {
        }
    }

    /** 儲存後延遲提醒：唯一工作，重复儲存會取代 */
    fun scheduleUnreadNudge(context: Context, itemId: Long, title: String, delayMin: Int) {
        val req = OneTimeWorkRequestBuilder<UnreadNudgeWorker>()
            .setInitialDelay(delayMin.coerceIn(1, 120).toLong(), TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    UnreadNudgeWorker.KEY_ITEM_ID to itemId,
                    UnreadNudgeWorker.KEY_TITLE to title.take(60)
                )
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$UNREAD_WORK-$itemId", ExistingWorkPolicy.REPLACE, req
        )
    }

    fun cancelUnreadNudge(context: Context, itemId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("$UNREAD_WORK-$itemId")
    }

    fun scheduleReviewDigestAlarm(context: Context, hour: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReviewDigestReceiver::class.java).apply {
            action = ACTION_TRIGGER_REVIEW_DIGEST
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_REVIEW_DIGEST_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
            }
        } catch (_: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
            } catch (_: Exception) {}
        }
    }

    fun cancelReviewDigestAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReviewDigestReceiver::class.java).apply {
            action = ACTION_TRIGGER_REVIEW_DIGEST
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_REVIEW_DIGEST_ALARM,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }

    /** 每日回顧：以 AlarmManager 準時喚醒為準，輔以 WorkManager 備援 */
    fun rescheduleReviewDigest(context: Context, enabled: Boolean, hour: Int) {
        if (!enabled) {
            cancelReviewDigestAlarm(context)
            WorkManager.getInstance(context).cancelUniqueWork(REVIEW_WORK)
            return
        }

        // 1. AlarmManager 精準定時
        scheduleReviewDigestAlarm(context, hour)

        // 2. WorkManager 週期性備援
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delayMin = ((target.timeInMillis - now.timeInMillis) / 60000).coerceAtLeast(1)
        val req = PeriodicWorkRequestBuilder<ReviewDigestWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMin, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            REVIEW_WORK, ExistingPeriodicWorkPolicy.UPDATE, req
        )
    }

    fun postDownloadProgress(
        context: Context,
        itemId: Long,
        title: String,
        currentIndex: Int,
        totalCount: Int,
        percent: Int,
        speedBps: Long,
        etaSec: Long
    ) {
        if (!canNotify(context)) return
        ensureDownloadChannel(context)

        val speedText = when {
            speedBps > 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", speedBps / (1024.0 * 1024.0))
            speedBps > 1024 -> String.format(java.util.Locale.US, "%.0f KB/s", speedBps / 1024.0)
            speedBps > 0 -> "$speedBps B/s"
            else -> ""
        }

        val etaText = if (etaSec > 0) {
            val m = etaSec / 60
            val s = etaSec % 60
            String.format(java.util.Locale.US, " · 剩餘 %02d:%02d", m, s)
        } else ""

        val speedEtaPart = if (speedText.isNotBlank()) " · $speedText$etaText" else etaText

        val contentText = when {
            totalCount <= 0 -> context.getString(R.string.notify_content_downloading_preparing)
            totalCount > 1 -> context.getString(R.string.notify_content_downloading_multi, currentIndex, totalCount, percent, speedEtaPart)
            else -> context.getString(R.string.notify_content_downloading_single, percent, speedEtaPart)
        }

        val note = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_reater)
            .setContentTitle(if (title.isBlank()) context.getString(R.string.notify_content_downloading_title) else context.getString(R.string.notify_content_downloading_title_with_name, title.take(30)))
            .setContentText(contentText)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context, itemId))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(DOWNLOAD_NOTIF_BASE + (itemId % 10000).toInt(), note)
        } catch (_: SecurityException) {
        }
    }

    fun postDownloadComplete(context: Context, itemId: Long, title: String, count: Int, failed: Int = 0) {
        if (!canNotify(context)) return
        ensureDownloadChannel(context)
        val baseText = if (title.isBlank()) context.getString(R.string.notify_content_download_done_default, count) else context.getString(R.string.notify_content_download_done_with_title, title.take(30), count)
        val note = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_reater)
            .setContentTitle(context.getString(R.string.notify_content_download_done_title))
            .setContentText(if (failed > 0) context.getString(R.string.notify_download_done_with_failures, baseText, failed) else baseText)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, itemId))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(DOWNLOAD_NOTIF_BASE + (itemId % 10000).toInt(), note)
        } catch (_: SecurityException) {
        }
    }

    fun postDownloadFailed(context: Context, itemId: Long, title: String, reason: String = context.getString(R.string.notify_content_download_failed_reason)) {
        if (!canNotify(context)) return
        ensureDownloadChannel(context)
        val note = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_reater)
            .setContentTitle(context.getString(R.string.notify_content_download_failed_title))
            .setContentText(if (title.isBlank()) reason else "「${title.take(30)}」$reason")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, itemId))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(DOWNLOAD_NOTIF_BASE + (itemId % 10000).toInt(), note)
        } catch (_: SecurityException) {
        }
    }

    fun cancelDownloadNotification(context: Context, itemId: Long) {
        try {
            NotificationManagerCompat.from(context).cancel(DOWNLOAD_NOTIF_BASE + (itemId % 10000).toInt())
        } catch (_: Exception) {}
    }

    fun postMediaExportRunning(context: Context, notifId: Int, isVideo: Boolean) {
        if (!canNotify(context)) return
        ensureDownloadChannel(context)
        val title = context.getString(
            if (isVideo) R.string.notify_export_video_running_title
            else R.string.notify_export_music_running_title
        )
        val note = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_reater)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notify_export_running_desc))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notifId, note)
        } catch (_: SecurityException) {
        }
    }

    fun postMediaExportComplete(
        context: Context,
        notifId: Int,
        isVideo: Boolean,
        success: Boolean,
        message: String
    ) {
        if (!canNotify(context)) return
        ensureDownloadChannel(context)
        val title = context.getString(
            when {
                !success -> R.string.notify_export_failed_title
                isVideo -> R.string.notify_export_video_done_title
                else -> R.string.notify_export_music_done_title
            }
        )
        val note = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_reater)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notifId, note)
        } catch (_: SecurityException) {
        }
    }
}
