package com.reater.app.notify

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

/**
 * 每日回顧 Worker：未讀數 > 0 才推播，並選一篇未讀文章為主。
 */
class ReviewDigestWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = Room.databaseBuilder(
                appContext, AppDatabase::class.java, "reater_database.db"
            ).setDriver(BundledSQLiteDriver())
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build()
            val (unread, latestItem) = try {
                val count = db.itemDao().countUnreadSince(0L)
                val item = if (count > 0) db.itemDao().getLatestUnreadItemDetail() else null
                Pair(count, item)
            } finally {
                db.close()
            }
            if (unread > 0) {
                NotifyCenter.postReviewDigest(appContext, unread, latestItem)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
