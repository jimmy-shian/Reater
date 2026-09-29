package com.reater.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
    const val UNREAD_WORK = "reater_unread_nudge"
    const val REVIEW_WORK = "reater_review_digest"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "閱讀提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "未讀回顧與每日回顧通知" }
            )
        }
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun postUnreadNudge(context: Context, itemId: Long, title: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val note = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_avatar_news)
            .setContentTitle("還有未讀文章")
            .setContentText(if (title.isBlank()) "你儲存的文章還沒看，點我回顧" else "「$title」還沒看，點我回顧")
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1000 + (itemId % 100000).toInt(), note)
    }

    fun postReviewDigest(context: Context, unreadCount: Int) {
        if (!canNotify(context) || unreadCount <= 0) return
        ensureChannel(context)
        val note = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_avatar_news)
            .setContentTitle("今日回顧")
            .setContentText("還有 $unreadCount 篇未讀，睡前清空它們吧")
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(9999, note)
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

    /** 每日回顧：24h 週期，首次延遲對齊到設定小時 */
    fun rescheduleReviewDigest(context: Context, enabled: Boolean, hour: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(REVIEW_WORK)
            return
        }
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delayMin = ((target.timeInMillis - now.timeInMillis) / 60000).coerceAtLeast(15)
        val req = PeriodicWorkRequestBuilder<ReviewDigestWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMin, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(REVIEW_WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}
