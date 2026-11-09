package com.reater.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.reater.app.data.local.AppDatabase
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_1_2
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_2_3
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_3_4
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_4_5
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_5_6
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_6_7
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_7_8
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_8_9
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_9_10
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_10_11
import com.reater.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 每日回顧與閱讀提醒定時廣播接收器：
 * - 鬧鐘觸發時查詢未讀數與選一篇主推文章推播，並自動精準續排隔天同一時間點。
 * - 系統開機（BOOT_COMPLETED）或應用更新時，自動重新登記 Alarm。
 */
class ReviewDigestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settingsRepo = SettingsRepository(appContext)
                val enabled = settingsRepo.reviewDigestEnabled.first()
                val hour = settingsRepo.reviewDigestHour.first()

                if (!enabled) {
                    NotifyCenter.cancelReviewDigestAlarm(appContext)
                    return@launch
                }

                // 重新排定下一次（確保每天固定小時整點觸發，避免時間漂移）
                NotifyCenter.scheduleReviewDigestAlarm(appContext, hour)

                // 若是定時觸發通知
                if (action == NotifyCenter.ACTION_TRIGGER_REVIEW_DIGEST) {
                    val db = Room.databaseBuilder(
                        appContext, AppDatabase::class.java, "reater_database.db"
                    ).setDriver(BundledSQLiteDriver())
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                        MIGRATION_9_10, MIGRATION_10_11
                    ).build()

                    val (unreadCount, latestItem) = try {
                        val count = db.itemDao().countUnreadSince(0L)
                        val item = if (count > 0) db.itemDao().getLatestUnreadItemDetail() else null
                        Pair(count, item)
                    } finally {
                        db.close()
                    }

                    if (unreadCount > 0) {
                        NotifyCenter.postReviewDigest(appContext, unreadCount, latestItem)
                    }
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
