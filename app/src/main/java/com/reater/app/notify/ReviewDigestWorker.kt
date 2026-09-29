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

/**
 * 每日回顧：未讀數 > 0 才推播，避免打擾。
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
            val unread = try {
                db.itemDao().countUnreadSince(0L)
            } finally {
                db.close()
            }
            NotifyCenter.postReviewDigest(appContext, unread)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
