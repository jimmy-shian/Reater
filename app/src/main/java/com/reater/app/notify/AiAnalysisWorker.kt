package com.reater.app.notify

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.reater.app.data.local.AppDatabase
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_10_11
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_1_2
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_2_3
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_3_4
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_4_5
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_5_6
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_6_7
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_7_8
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_8_9
import com.reater.app.data.local.AppDatabase.Companion.MIGRATION_9_10
import com.reater.app.data.local.entity.AiRunEntity
import com.reater.app.data.local.entity.UserEditEntity
import com.reater.app.data.remote.OpenAiApiException
import com.reater.app.data.remote.OpenAiClient
import com.reater.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class AiAnalysisWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val itemId = inputData.getLong(KEY_ITEM_ID, -1L)
        val postTitle = inputData.getString(KEY_POST_TITLE).orEmpty()
        val manualNote = inputData.getString(KEY_MANUAL_NOTE).orEmpty()

        if (itemId < 0) return Result.success()

        val settingsRepo = SettingsRepository(appContext)
        val apiKey = settingsRepo.getOpenAiApiKey().orEmpty()
        val consent = settingsRepo.aiTransmissionConsent.first()
        val customBaseUrl = settingsRepo.customBaseUrl.first()
        val selectedModel = settingsRepo.selectedModel.first()

        if (!consent) {
            NotifyCenter.postAiAnalysisFailed(appContext, itemId, postTitle, "尚未授權 AI 傳輸設定")
            return Result.success()
        }
        if (apiKey.isBlank()) {
            NotifyCenter.postAiAnalysisFailed(appContext, itemId, postTitle, "尚未配置 API Key")
            return Result.success()
        }

        val db = Room.databaseBuilder(
            appContext, AppDatabase::class.java, "reater_database.db"
        ).setDriver(BundledSQLiteDriver())
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10, MIGRATION_10_11
            ).build()

        val itemDetail = try {
            db.itemDao().getItemDetailById(itemId)
        } catch (_: Exception) {
            null
        }

        if (itemDetail == null) {
            db.close()
            return Result.success()
        }

        val commentsJoined = itemDetail.comments.joinToString("\n") { "${it.author}: ${it.text}" }
        val noteToUse = manualNote.ifBlank { itemDetail.manualNote }
        val summaryInput = listOf(
            itemDetail.displayBody,
            noteToUse.takeIf { it.isNotBlank() }?.let { "使用者人工筆記：\n$it" }
        ).filterNotNull().joinToString("\n\n")

        val client = OpenAiClient()
        val result = client.analyzePost(
            apiKey = apiKey,
            model = selectedModel,
            customBaseUrl = customBaseUrl,
            postContent = summaryInput,
            commentsContent = commentsJoined
        )

        return if (result.isSuccess) {
            val (analysis, usage) = result.getOrThrow()
            try {
                val categoryId = db.categoryDao().getCategoryByName(analysis.category)?.id
                val current = db.itemDao().getUserEditByItemId(itemId) ?: UserEditEntity(itemId = itemId)
                db.itemDao().insertUserEdit(
                    current.copy(
                        aiSummary = analysis.summary,
                        categoryId = categoryId ?: current.categoryId,
                        editedAt = System.currentTimeMillis(),
                        editSource = "AI"
                    )
                )

                val encoded = kotlinx.serialization.json.Json.encodeToString(com.reater.app.data.remote.AiAnalysisResult.serializer(), analysis)
                db.aiRunDao().insert(
                    AiRunEntity(
                        itemId = itemId,
                        purpose = "SUMMARIZE",
                        model = selectedModel.ifBlank { "gpt-5-nano" },
                        promptVersion = "v1",
                        inputHash = client.computeInputHash(summaryInput + "\n" + commentsJoined),
                        outputJson = encoded,
                        usageIn = usage.prompt_tokens,
                        usageOut = usage.completion_tokens,
                        createdAt = System.currentTimeMillis()
                    )
                )
            } finally {
                db.close()
            }

            NotifyCenter.postAiAnalysisComplete(appContext, itemId, postTitle, analysis.summary)
            Result.success()
        } else {
            db.close()
            val error = result.exceptionOrNull()
            val isUserAuthOrQuota = error is OpenAiApiException && (error.code == 401 || error.code == 403 || error.code == 404)
            // 自動重試最多 3 次 (runAttemptCount 0, 1, 2)；遇 401/403/404 立即停止重試
            if (!isUserAuthOrQuota && runAttemptCount < 2) {
                Result.retry()
            } else {
                val userFriendlyMessage = OpenAiClient.formatAiError(error)
                NotifyCenter.postAiAnalysisFailed(appContext, itemId, postTitle, userFriendlyMessage)
                Result.success()
            }
        }
    }

    companion object {
        const val KEY_ITEM_ID = "item_id"
        const val KEY_POST_TITLE = "post_title"
        const val KEY_MANUAL_NOTE = "manual_note"
        private const val WORK_NAME_PREFIX = "ai_analysis_work_"

        fun schedule(
            context: Context,
            itemId: Long,
            postTitle: String,
            manualNote: String = ""
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val req = OneTimeWorkRequestBuilder<AiAnalysisWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        KEY_ITEM_ID to itemId,
                        KEY_POST_TITLE to postTitle.take(60),
                        KEY_MANUAL_NOTE to manualNote
                    )
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_NAME_PREFIX$itemId",
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }
}
