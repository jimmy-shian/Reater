package com.reater.app.notify

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.reater.app.R
import com.reater.app.data.local.AppDatabase
import com.reater.app.data.local.entity.MediaEntity
import com.reater.app.data.remote.MediaDownloader
import com.reater.app.ui.player.VideoThumbnailHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 媒體離線背景下載 Worker：
 * 當使用者點擊「背景下載」或離開儲存畫面時，未下載完畢的媒體將在背景繼續下載，
 * 並在 Android 系統通知欄即時顯示進度條、速度與預估時間。
 *
 * 設計要點：
 * 1. 待下載清單一律以資料庫為準（localPath 空白或本機檔案已不存在），
 *    因此前景抓取中途取消／失敗的媒體都能在這裡接續，不會無聲消失。
 * 2. 一開始就先送出一條進度通知，使用者切到背景馬上看得到（不再等到第一個位元組）。
 * 3. 暫時性失敗交給 WorkManager 自動重試（指數退避），重試次數用盡才通知「下載未完成」。
 * 4. HLS（.m3u8）manifest 無法用 OkHttp 直接存檔，略過且不算失敗。
 */
class MediaDownloadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MediaDownloadWorkerEntryPoint {
        fun appDatabase(): AppDatabase
        fun mediaDownloader(): MediaDownloader
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val itemId = inputData.getLong(KEY_ITEM_ID, -1L)
        val postTitle = inputData.getString(KEY_POST_TITLE).orEmpty()
        val shortcode = inputData.getString(KEY_SHORTCODE).orEmpty().ifBlank { "media" }

        if (itemId < 0) return@withContext Result.success()

        val entryPoint = try {
            EntryPointAccessors.fromApplication(
                appContext,
                MediaDownloadWorkerEntryPoint::class.java
            )
        } catch (_: Exception) {
            return@withContext Result.failure()
        }
        val db = entryPoint.appDatabase()
        val mediaDownloader = entryPoint.mediaDownloader()
        val mediaDao = db.mediaDao()

        val allMedia = mediaDao.getMediaByItemId(itemId)
        if (allMedia.isEmpty()) {
            // 這篇沒有任何媒體：清掉可能殘留的進度通知，靜默結束（不再誤推完成通知）
            NotifyCenter.cancelDownloadNotification(appContext, itemId)
            return@withContext Result.success()
        }

        // 待下載：從未下載（localPath 空白）或本機檔案已消失（被清快取／換機匯入）
        val pendingMedia = allMedia.filter { isPending(it) }
        if (pendingMedia.isEmpty()) {
            // 排程後媒體已全部就緒（前景剛好下載完成）：收起通知，不再重複推送
            NotifyCenter.cancelDownloadNotification(appContext, itemId)
            return@withContext Result.success()
        }

        val totalMediaCount = pendingMedia.size
        var completedCount = 0
        var failedCount = 0
        var skippedCount = 0

        // 先送出一條「不確定進度」通知：切到背景當下就看得見，不會靜靜跑很久才出現
        NotifyCenter.postDownloadProgress(
            context = appContext,
            itemId = itemId,
            title = postTitle,
            currentIndex = 0,
            totalCount = totalMediaCount,
            percent = 0,
            speedBps = 0L,
            etaSec = -1L
        )

        pendingMedia.forEachIndexed { idx, media ->
            if (isStopped) return@forEachIndexed

            // HLS 串流 manifest：OkHttp 直接存只會得到文字檔，跳過（不算失敗）
            if (media.remoteUrl.contains(".m3u8", ignoreCase = true)) {
                skippedCount++
                return@forEachIndexed
            }

            NotifyCenter.postDownloadProgress(
                context = appContext,
                itemId = itemId,
                title = postTitle,
                currentIndex = idx + 1,
                totalCount = totalMediaCount,
                percent = if (totalMediaCount > 0) ((idx * 100) / totalMediaCount) else 0,
                speedBps = 0L,
                etaSec = -1L
            )

            val localPath = try {
                mediaDownloader.downloadMedia(
                    remoteUrl = media.remoteUrl,
                    shortcode = shortcode,
                    index = media.position,
                    onProgress = { progress ->
                        val overallPercent = if (totalMediaCount > 0) {
                            (((idx.toDouble() + (progress.percentage / 100.0)) / totalMediaCount) * 100).toInt()
                        } else progress.percentage

                        NotifyCenter.postDownloadProgress(
                            context = appContext,
                            itemId = itemId,
                            title = postTitle,
                            currentIndex = idx + 1,
                            totalCount = totalMediaCount,
                            percent = overallPercent.coerceIn(0, 100),
                            speedBps = progress.speedBytesPerSec,
                            etaSec = progress.etaSeconds
                        )
                    }
                )
            } catch (ce: CancellationException) {
                // 工作被取消（例如使用者重新儲存同一篇導致 REPLACE）不是下載失敗：
                // 直接拋出，已下載的部分早前已寫入 DB，剩下的下次接手。
                throw ce
            }

            if (localPath.isNotBlank()) {
                mediaDao.updateLocalPath(media.id, localPath)
                if (media.kind.equals("VIDEO", ignoreCase = true) || localPath.endsWith(".mp4", ignoreCase = true)) {
                    VideoThumbnailHelper.getThumbnail(appContext, localPath)
                }
                completedCount++
            } else {
                failedCount++
            }
        }

        if (isStopped) {
            // 工作已被取代/取消（例如使用者重新儲存同一篇）：不更動通知，
            // 由接手的執行個體重新推送，避免留下跑不完的進度通知。
            return@withContext Result.success()
        }

        // 暫時性失敗（網路抖動、CDN 回應慢）：交給 WorkManager 指數退避重試，
        // 讓背景下載真的「會繼續」，而不是直接顯示下載失敗。
        if (failedCount > 0 && runAttemptCount < MAX_AUTO_RETRY) {
            return@withContext Result.retry()
        }

        if (completedCount > 0) {
            NotifyCenter.postDownloadComplete(
                appContext,
                itemId,
                postTitle,
                completedCount,
                failed = failedCount
            )
        } else if (failedCount > 0) {
            NotifyCenter.postDownloadFailed(
                appContext,
                itemId,
                postTitle,
                reason = appContext.getString(R.string.worker_download_failed, failedCount, if (skippedCount > 0) appContext.getString(R.string.worker_download_skipped, skippedCount) else "")
            )
        } else {
            // 全部略過（例如整篇都是 HLS）：收起進度通知，不留一條跑不完的通知在通知欄
            NotifyCenter.cancelDownloadNotification(appContext, itemId)
        }

        Result.success()
    }

    private fun isPending(media: MediaEntity): Boolean {
        if (media.remoteUrl.isBlank()) return false
        if (media.localPath.isBlank()) return true
        val file = File(media.localPath)
        return !file.exists() || file.length() <= 0L
    }

    companion object {
        const val KEY_ITEM_ID = "key_item_id"
        const val KEY_POST_TITLE = "key_post_title"
        const val KEY_SHORTCODE = "key_shortcode"

        /** 自動重試上限（不含首次執行）：超過才通知失敗，避免無限重跑 */
        const val MAX_AUTO_RETRY = 3

        fun schedule(
            context: Context,
            itemId: Long,
            postTitle: String,
            shortcode: String = ""
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val req = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        KEY_ITEM_ID to itemId,
                        KEY_POST_TITLE to postTitle.take(60),
                        KEY_SHORTCODE to shortcode
                    )
                )
                .build()

            // 排程當下就先送出一條「不確定進度」通知：JobScheduler 可能晚幾秒才真正執行，
            // 但使用者切到背景時通知欄已經有進度條，不會以為「按了下去卻沒反應」。
            NotifyCenter.postDownloadProgress(
                context = context,
                itemId = itemId,
                title = postTitle,
                currentIndex = 0,
                totalCount = 0,
                percent = 0,
                speedBps = 0L,
                etaSec = -1L
            )

            WorkManager.getInstance(context).enqueueUniqueWork(
                "media_download_$itemId",
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }
}
