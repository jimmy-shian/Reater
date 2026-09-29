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
 * 儲存後未讀提醒：延遲到期時若該篇仍未讀才推播。
 */
class UnreadNudgeWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val itemId = inputData.getLong(KEY_ITEM_ID, -1L)
        if (itemId < 0) return Result.success()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        return try {
            val db = Room.databaseBuilder(
                appContext, AppDatabase::class.java, "reater_database.db"
            ).setDriver(BundledSQLiteDriver())
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
            val stillUnread = try {
                val detail = db.itemDao().getItemDetailById(itemId)
                detail != null && !detail.isRead && detail.item.isDeleted.not()
            } finally {
                db.close()
            }
            if (stillUnread) {
                NotifyCenter.postUnreadNudge(appContext, itemId, title)
            }
            Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }

    companion object {
        const val KEY_ITEM_ID = "item_id"
        const val KEY_TITLE = "title"
    }
}
